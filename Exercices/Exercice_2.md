## Exercice 2 : Modéliser des Value Objects et Entités

**Difficulté :** Moyen
**Prérequis :** Exercice 1, Module 2 - Le domaine au centre

### Contexte

Suite à l'analyse de l'exercice 1, vous commencez la migration vers l'architecture hexagonale. La première étape consiste à créer un **modèle de domaine riche** pour la plateforme de réservation de salles. Fini les `String`, `double` et `LocalDateTime` bruts partout : vous allez créer des types métier expressifs qui portent leurs propres règles de validation.

Votre Product Owner vous donne les règles métier suivantes :
- Un montant (`Money`) est toujours positif ou nul, exprimé en euros avec 2 décimales maximum
- Un email de membre (`MemberEmail`) doit respecter un format valide et être stocké en minuscules
- Un créneau horaire (`TimeSlot`) est composé d'une heure de début et d'une heure de fin ; la fin doit être strictement après le début
- La durée d'un créneau doit être comprise entre 30 minutes et 8 heures
- Un identifiant de réservation (`ReservationId`) est un UUID
- Une salle (`Room`) a une identité, un nom, une capacité (entier > 0) et un tarif horaire (`Money`)
- Une réservation (`Reservation`) est un agrégat liant un membre, une salle et un créneau
- Le tarif d'une réservation est majoré de 20% si le créneau tombe un week-end

### Énoncé

1. **Créez les Value Objects** suivants en utilisant les **Java Records** (Java 17+). Chaque Value Object doit valider ses invariants dans le constructeur compact :



   - `Money` : montant positif ou nul, arrondi à 2 décimales, opérations `add(Money)`, `multiply(BigDecimal)`, `isGreaterThan(Money)`
   - `MemberEmail` : validation par regex, stockage en minuscules
   - `TimeSlot` : fin > début, durée entre 30 min et 8h, méthodes `durationInMinutes()`, `isWeekend()`, `overlapsWith(TimeSlot)`
   - `ReservationId` : encapsule un UUID, factory method `generate()`

2. **Créez l'entité `Room`** avec :
   - Un identifiant (`RoomId`, Value Object encapsulant un UUID)
   - Un nom, une capacité (entier strictement positif), un tarif horaire (`Money`)
   - Une méthode `canAccommodate(int attendeeCount)` qui retourne un booléen
   - Une méthode `calculatePrice(TimeSlot slot)` qui applique la majoration week-end de 20%
   - Égalité basée sur l'identité (pas sur les attributs)

3. **Créez l'agrégat `Reservation`** avec :
   - Un `ReservationId`, un `MemberEmail`, un statut (enum : `PENDING`, `CONFIRMED`, `CANCELLED`)
   - La `Room` réservée, le `TimeSlot`, le nombre de participants, le prix calculé (`Money`)
   - Une factory method statique `create(MemberEmail, Room, TimeSlot, int attendeeCount)` qui vérifie la capacité et calcule le prix
   - Une méthode `cancel()` qui passe le statut à `CANCELLED` (impossible si déjà `CONFIRMED` et que le créneau a démarré)
   - La réservation doit être **immuable** après confirmation : aucun setter public


### Indices

- Utilisez `BigDecimal` pour `Money`, jamais `double` (problèmes de précision). La majoration de 20% s'écrit `multiply(new BigDecimal("1.20"))`.
- Les Java Records fournissent automatiquement `equals()`, `hashCode()` et `toString()` basés sur **tous les composants** : parfait pour les Value Objects.
- Pour l'entité `Room`, vous devrez surcharger `equals()` et `hashCode()` pour n'utiliser que le `RoomId`.
- `DayOfWeek.getValue()` retourne 6 pour samedi et 7 pour dimanche — utilisez cette logique dans `TimeSlot.isWeekend()`.
- La factory method `Reservation.create()` est le seul point d'entrée pour créer une réservation : elle centralise toutes les validations et garantit qu'on ne peut pas créer une réservation dans un état incohérent.

### Livrables

- Les classes `Money.java`, `MemberEmail.java`, `TimeSlot.java`, `ReservationId.java` (package `domain.model`)
- Les classes `Room.java`, `RoomId.java` (package `domain.model`)
- La classe `Reservation.java` avec `ReservationStatus.java` (package `domain.model`)
