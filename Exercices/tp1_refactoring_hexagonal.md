# TP 1 : Refactoring d'un Monolithe vers l'Architecture Hexagonale

**Prerequis :** Java 17+, Spring Boot, JPA, Maven, notions d'architecture logicielle

**Objectifs :**
- Extraire le domaine metier d'un monolithe CRUD classique
- Creer des ports et adapteurs
- Inverser les dependances
- Ecrire des tests par couche
- Comparer avant/apres en termes de testabilite et maintenabilite

---

> **Organisation du TP :**
> - **Partie 1:** Refactoring — analyser le monolithe et migrer vers l'architecture hexagonale
> - **Partie 2:** Tests — ecrire les tests unitaires, d'integration et architecturaux

---

## Contexte metier

Vous travaillez sur **BiblioTech**, un systeme de gestion de bibliotheque. L'application actuelle est un monolithe Spring Boot classique avec une architecture en couches (Controller -> Service -> Repository) et souffre de nombreux problemes de couplage.

**Entites metier :**

| Entite | Attributs |
|--------|-----------|
| **Book** | ISBN, titre, auteur, nombre de copies disponibles |
| **Member** | memberId, nom, email, nombre de livres empruntes (max 3) |
| **BorrowingRecord** | membre, livre, date d'emprunt, date de retour, penalite |

**Regles metier :**

1. Un membre peut emprunter **maximum 3 livres** simultanement
2. Un livre ne peut etre emprunte que si le **nombre de copies disponibles > 0**
3. Un retour en retard (**> 14 jours**) genere une **penalite de 1 euro par jour de retard**
4. Un membre avec des **penalites impayees ne peut pas emprunter**

---

## Point de depart : le projet fourni

Le projet de base **`bibliotech-monolithe`** vous est fourni. Il s'agit d'un monolithe Spring Boot fonctionnel que vous allez refactorer.

### Demarrer l'application

```bash
cd bibliotech-monolithe
mvn spring-boot:run
```

L'application demarre sur `http://localhost:8080`.

**Endpoints disponibles :**

| Methode | URL | Description |
|---------|-----|-------------|
| `POST` | `/api/books` | Ajouter un livre |
| `GET` | `/api/books` | Lister tous les livres |
| `GET` | `/api/books/{isbn}` | Recuperer un livre par ISBN |
| `GET` | `/api/books/search?keyword=` | Rechercher par titre |
| `DELETE` | `/api/books/{isbn}` | Supprimer un livre |
| `POST` | `/api/members` | Enregistrer un membre |
| `GET` | `/api/members` | Lister tous les membres |
| `GET` | `/api/members/{memberId}` | Recuperer un membre |
| `POST` | `/api/borrow?memberId=&isbn=` | Emprunter un livre |
| `POST` | `/api/return/{borrowingId}` | Retourner un livre |
| `POST` | `/api/members/{memberId}/pay?amount=` | Payer une penalite |

**Console H2** (base de donnees en memoire) : `http://localhost:8080/h2-console`
- JDBC URL : `jdbc:h2:mem:bibliotech`
- User : `sa` / Password : *(vide)*

---

# Partie 1 : Refactoring

---

## Etape 1 : Analyser le monolithe existant

Ouvrez le projet `bibliotech-monolithe` dans votre IDE et explorez le code. L'architecture actuelle est la suivante :

```
bibliotech-monolithe/
└── src/main/java/com/bibliotech/
    ├── model/
    │   ├── Book.java              -- Entite JPA
    │   ├── Member.java            -- Entite JPA
    │   └── BorrowingRecord.java   -- Entite JPA
    ├── repository/
    │   ├── BookRepository.java
    │   ├── MemberRepository.java
    │   └── BorrowingRecordRepository.java
    ├── service/
    │   └── BookService.java       -- Le "God Service"
    └── controller/
        └── BookController.java
```

### 1.1 Exercice d'analyse (10 min)

Lisez attentivement le code de chaque classe, puis identifiez les problemes en remplissant le tableau suivant :

| Probleme | Ou dans le code ? | Consequence |
|----------|-------------------|-------------|
| Entités JPA = entités métier | @Entity et continent de la logique métier | Hibernate pour modifier l'état via des proxy. Sans hibernate les entités ont des setters public sans logique |
| Couplage fort à JPA | BookService dépend de BookRepository extends JpaRepository | Changer BDD force à modifier le service métier |
| Couplage fort à JavaMail | @Autowired JavaMailSender dans BookService | Changer de canal de notification force à modifier le service métier |
| Logique métier dans le service, pas dans les entités | member.borrow() dans le service | Modèle anémique |
| **God Service** | BookService (200 lignes) | Impossible à maintenir |

> **Questions cles a se poser :**
> - Peut-on tester la logique metier sans demarrer Spring ?
> - Que se passe-t-il si on veut remplacer l'email par des SMS ?
> - Que se passe-t-il si on veut changer de base de donnees ?
> - Combien de raisons de changer a `BookService` ?

---

## Etape 2 : Extraire le domaine (30min)

Creez un **nouveau projet Maven** `bibliotech-hexagonal` (ou renommez le projet existant). L'objectif est de creer un package `domain` **totalement independant** de tout framework.

### 2.1 Structure de packages cible

```
com.bibliotech/
  domain/
    model/
      Book.java
      Member.java
      BorrowingRecord.java
    vo/
      ISBN.java
      MemberId.java
      BookTitle.java
      Email.java
      Penalty.java
    event/
      DomainEvent.java
      BookBorrowed.java
      BookReturned.java
      PenaltyGenerated.java
    exception/
      BookNotAvailableException.java
      BorrowingLimitReachedException.java
      UnpaidPenaltiesException.java
      BookAlreadyReturnedException.java
    port/
      in/
        BookLendingService.java
        BookCatalogService.java
      out/
        BookRepository.java
        MemberRepository.java
        BorrowingRepository.java
        NotificationSender.java
  application/
    usecase/
      BookLendingUseCase.java
      BookCatalogUseCase.java
  infrastructure/
    adapter/
      in/
        rest/
          BookRestController.java
          dto/
            BorrowBookRequest.java
            BookResponse.java
      out/
        persistence/
          JpaBookRepository.java
          JpaMemberRepository.java
          JpaBorrowingRepository.java
          entity/
            BookJpaEntity.java
            MemberJpaEntity.java
            BorrowingRecordJpaEntity.java
          mapper/
            BookMapper.java
            MemberMapper.java
        notification/
          EmailNotificationSender.java
          InMemoryNotificationSender.java
    config/
      BeanConfiguration.java
```

### 2.2 Value Objects

Creez les Value Objects dans `domain/vo/`. Ils sont **immutables** et encapsulent la validation.

> **Regles :**
> - Utilisez des `record` Java
> - La validation se fait dans le constructeur compact (`public VO { ... }`)
> - Aucune annotation framework

**Value Objects a creer :**

| Value Object | Validation attendue |
|---|---|
| `ISBN` | Format ISBN-10 ou ISBN-13, non null |
| `MemberId` | Non null, non vide |
| `BookTitle` | Non null, non vide, max 500 caracteres |
| `Email` | Format email valide (regex) |
| `Penalty` | Montant >= 0 ; methodes `zero()`, `fromLateDays(long)`, `add()`, `subtract()`, `isUnpaid()` |

### 2.3 Exceptions du domaine

Creez des exceptions metier expressives dans `domain/exception/` :

- `BookNotAvailableException` : aucune copie disponible
- `BorrowingLimitReachedException` : limite de 3 livres atteinte
- `UnpaidPenaltiesException` : penalites impayees (conserve le montant)
- `BookAlreadyReturnedException` : livre deja retourne

### 2.4 Evenements du domaine

Creez dans `domain/event/` une interface scellee `DomainEvent` et trois records :

- `BookBorrowed` : memberId, isbn, borrowDate, expectedReturnDate
- `BookReturned` : memberId, isbn, returnDate, isLate
- `PenaltyGenerated` : memberId, penalty, daysLate

### 2.5 Entites du domaine (avec logique metier)

> **Point cle :** les entites du domaine ne sont PAS des entites JPA. Elles contiennent la logique metier et produisent des evenements.

**`Book`** doit avoir :
- `canBeBorrowed()` : retourne true si `availableCopies > 0`
- `decrementStock()` : decremente ou lance `BookNotAvailableException`
- `incrementStock()` : incremente (retour d'un livre)
- Pas de setters publics

**`Member`** doit avoir :
- `canBorrow()` : retourne true si < 3 livres ET pas de penalites
- `borrow()` : verifie limite + penalites, lance les exceptions appropriees, incremente le compteur
- `returnBook()` : decremente le compteur
- `addPenalty(Penalty)` : cumule une penalite
- `payPenalty(double)` : deduit un montant des penalites

**`BorrowingRecord`** doit avoir :
- Methode statique `create(memberId, isbn, borrowDate)` : cree un enregistrement et publie `BookBorrowed`
- `markAsReturned(returnDate)` : marque comme retourne, calcule la penalite, publie `BookReturned` et eventuellement `PenaltyGenerated`
- `pullDomainEvents()` : retourne et vide la liste des evenements

---

## Etape 3 : Definir les ports

Les ports sont des **interfaces** qui definissent les contrats entre les couches. Ils vivent dans le package `domain/port/`.

> **Pourquoi dans `domain/` ?** Les ports font partie integrante du domaine : ce sont les contrats que le domaine *exige* de son environnement. Les ports d'entree exposent ce que le domaine offre, les ports de sortie expriment ce dont le domaine a besoin — sans savoir qui l'implementera.

### 3.1 Ports primaires (entrants -- driving)

Ce sont les use cases exposes a l'exterieur.

**`BookLendingService`** (`domain/port/in/` — port d'entree) :
```
borrowBook(String memberId, String isbn) : BorrowingRecord
returnBook(Long borrowingId) : BorrowingRecord
payPenalty(String memberId, double amount) : void
```

**`BookCatalogService`** (`domain/port/in/` — port d'entree) :
```
addBook(String isbn, String title, String author, int copies) : Book
findByIsbn(String isbn) : Optional<Book>
searchByTitle(String keyword) : List<Book>
findAll() : List<Book>
removeBook(String isbn) : void
```

### 3.2 Ports secondaires (sortants -- driven)

Ce sont les interfaces que l'infrastructure doit implementer. Elles sont dans `domain/port/out/`.

**`BookRepository`** :
```
findByIsbn(ISBN) : Optional<Book>
findByTitleContaining(String) : List<Book>
findAll() : List<Book>
save(Book) : Book
deleteByIsbn(ISBN) : void
```

**`MemberRepository`** :
```
findById(MemberId) : Optional<Member>
save(Member) : Member
```

**`BorrowingRepository`** :
```
findById(Long) : Optional<BorrowingRecord>
findActiveByMember(MemberId) : List<BorrowingRecord>
findActiveByBook(ISBN) : List<BorrowingRecord>
save(BorrowingRecord) : BorrowingRecord
```

**`NotificationSender`** :
```
send(String to, String subject, String body) : void
notifyEvent(DomainEvent event) : void
```

---

## Etape 4 : Implementer la couche application 

La couche application **orchestre** les objets du domaine via les ports. Elle ne contient aucune logique metier.

> **Contrainte importante :** les use cases n'ont AUCUNE annotation Spring (`@Service`, `@Transactional`). Ce sont de simples classes Java.

### 4.1 `BookLendingUseCase`

Implementez `BookLendingService`. Pour `borrowBook` :
1. Creer les Value Objects (validation automatique)
2. Charger le membre et le livre via les ports secondaires
3. Deleguer la logique metier aux entites (`member.borrow()`, `book.decrementStock()`)
4. Creer l'enregistrement d'emprunt (`BorrowingRecord.create(...)`)
5. Persister les changements
6. Publier les evenements du domaine via `notificationSender::notifyEvent`

Implementez de meme `returnBook` et `payPenalty`.

### 4.2 `BookCatalogUseCase`

Implementez `BookCatalogService`. Chaque methode delegue simplement au port `BookRepository`.

---

## Etape 5 : Creer les adapteurs 

### 5.1 Adapteurs secondaires -- Persistance

#### Entites JPA (separees du domaine)

Creez dans `infrastructure/adapter/out/persistence/entity/` des entites JPA distinctes des entites du domaine :
- `BookJpaEntity` : `@Entity`, `@Table(name = "books")`
- `MemberJpaEntity` : `@Entity`, `@Table(name = "members")`
- `BorrowingRecordJpaEntity` : `@Entity`, `@Table(name = "borrowing_records")`, avec `memberId` et `isbn` comme simples `String` (pas de `@ManyToOne`)

#### Mappers (JPA Entity <-> Domain Entity)

Creez dans `persistence/mapper/` des classes utilitaires (constructeur prive) avec des methodes statiques :
- `BookMapper.toDomain(BookJpaEntity)` et `BookMapper.toJpa(Book)`
- `MemberMapper.toDomain(MemberJpaEntity)` et `MemberMapper.toJpa(Member)`

#### Adapteurs JPA

Creez `JpaBookRepository implements BookRepository` (port secondaire) qui :
- Depend d'un `SpringDataBookRepository extends JpaRepository<BookJpaEntity, String>`
- Delegue les appels JPA et mappe avec `BookMapper`

**Exercice :** Creez sur le meme modele `JpaMemberRepository` et `JpaBorrowingRepository`.

### 5.2 Adapteur secondaire -- Notification

**`EmailNotificationSender`** : implementez `NotificationSender` en utilisant `JavaMailSender`. La methode `notifyEvent` fait un switch sur les types d'evenements (`BookBorrowed`, `BookReturned`, `PenaltyGenerated`).

**`InMemoryNotificationSender`** : implementez `NotificationSender` pour les tests, en stockant les notifications dans des listes en memoire. Ajoutez des methodes `getSentNotifications()`, `getReceivedEvents()`, `clear()` pour les assertions.

### 5.3 Adapteur primaire -- REST Controller

Creez `BookRestController` qui :
- Depend des ports d'entree `BookCatalogService` et `BookLendingService` (injection par constructeur)
- Expose les memes endpoints que le monolithe d'origine
- Utilise des DTOs (`BookResponse`, `BorrowBookRequest`) pour ne pas exposer les entites du domaine

### 5.4 Configuration Spring

Creez `BeanConfiguration` dans `infrastructure/config/` :

```java
@Configuration
public class BeanConfiguration {

    @Bean
    public BookCatalogService bookCatalogService(BookRepository bookRepository) {
        return new BookCatalogUseCase(bookRepository);
    }

    @Bean
    public BookLendingService bookLendingService(
            BookRepository bookRepository,
            MemberRepository memberRepository,
            BorrowingRepository borrowingRepository,
            NotificationSender notificationSender) {
        return new BookLendingUseCase(...);
    }
}
```

> **Points importants :**
> - Les use cases sont instancies comme des beans Spring, MAIS ils n'ont aucune annotation Spring
> - Spring ne fait que le "cablage" (wiring)
> - Le domaine reste pur et testable independamment

---

