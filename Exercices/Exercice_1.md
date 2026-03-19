## Exercice 1 : Identifier le domaine dans un monolithe

**Difficulté :** Facile
**Durée estimée :** 15-20 minutes
**Prérequis :** Module 1 - Introduction à l'architecture hexagonale

### Contexte

Vous rejoignez une équipe qui maintient un monolithe Spring Boot classique pour une plateforme de **réservation de salles de réunion** dans un réseau de co-working. Le code suit une architecture en couches traditionnelle (Controller -> Service -> Repository). L'équipe souhaite migrer vers une architecture hexagonale, mais avant de toucher au code, il faut **comprendre ce qui relève du domaine, de l'application et de l'infrastructure**.

Voici le code existant :

```java
@RestController
@RequestMapping("/api/reservations")
public class ReservationController {

    @Autowired
    private ReservationService reservationService;

    @PostMapping
    public ResponseEntity<ReservationDTO> book(@RequestBody BookRoomRequest request) {
        ReservationDTO reservation = reservationService.bookRoom(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(reservation);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ReservationDTO> getReservation(@PathVariable Long id) {
        return ResponseEntity.ok(reservationService.getReservation(id));
    }
}
```

```java
@Service
@Transactional
public class ReservationService {

    @Autowired
    private ReservationRepository reservationRepository;

    @Autowired
    private RoomRepository roomRepository;

    @Autowired
    private JavaMailSender mailSender;

    public ReservationDTO bookRoom(BookRoomRequest request) {
        // Vérifier que l'email du demandeur est renseigné
        if (request.getMemberEmail() == null || request.getMemberEmail().isEmpty()) {
            throw new IllegalArgumentException("Email du membre requis");
        }

        // Vérifier que la durée est valide (minimum 30 min, maximum 8h)
        long durationMinutes = ChronoUnit.MINUTES.between(request.getStartTime(), request.getEndTime());
        if (durationMinutes < 30) {
            throw new IllegalArgumentException("La durée minimale d'une réservation est 30 minutes");
        }
        if (durationMinutes > 480) {
            throw new IllegalArgumentException("La durée maximale d'une réservation est 8 heures");
        }

        // Vérifier que la salle existe et est disponible
        RoomEntity room = roomRepository.findById(request.getRoomId())
            .orElseThrow(() -> new RuntimeException("Salle introuvable"));

        if (room.getCapacity() < request.getAttendeeCount()) {
            throw new RuntimeException("La salle " + room.getName()
                + " ne peut pas accueillir " + request.getAttendeeCount() + " personnes");
        }

        boolean isOverlapping = reservationRepository.existsOverlappingReservation(
            request.getRoomId(), request.getStartTime(), request.getEndTime());
        if (isOverlapping) {
            throw new RuntimeException("La salle est déjà réservée sur ce créneau");
        }

        // Calculer le tarif (tarif horaire * durée, majoration 20% le week-end)
        double hours = durationMinutes / 60.0;
        double price = room.getHourlyRate() * hours;
        if (request.getStartTime().getDayOfWeek().getValue() >= 6) {
            price = price * 1.20; // majoration week-end
        }

        // Sauvegarder la réservation
        ReservationEntity reservation = new ReservationEntity();
        reservation.setMemberEmail(request.getMemberEmail());
        reservation.setRoom(room);
        reservation.setStartTime(request.getStartTime());
        reservation.setEndTime(request.getEndTime());
        reservation.setAttendeeCount(request.getAttendeeCount());
        reservation.setPrice(price);
        reservation.setStatus("CONFIRMED");
        reservation.setCreatedAt(LocalDateTime.now());
        ReservationEntity saved = reservationRepository.save(reservation);

        // Envoyer un email de confirmation
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(request.getMemberEmail());
        message.setSubject("Réservation #" + saved.getId() + " confirmée - " + room.getName());
        message.setText("Votre réservation du " + request.getStartTime()
            + " au " + request.getEndTime() + " a été enregistrée pour " + price + "€.");
        mailSender.send(message);

        return toDTO(saved);
    }

    // ...
}
```

```java
@Entity
@Table(name = "reservations")
public class ReservationEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String memberEmail;
    private double price;
    private String status;
    private int attendeeCount;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private LocalDateTime createdAt;

    @ManyToOne
    private RoomEntity room;

    // getters, setters...
}
```

### Énoncé

1. **Lisez attentivement** le code ci-dessus. Sur une feuille ou un tableau blanc, créez trois colonnes : **Domaine**, **Application**, **Infrastructure**.

2. **Classez chaque élément** du code dans la bonne colonne. Posez-vous la question : "Si je change de base de données ou de framework web, est-ce que cette logique change ?" Si non, c'est du domaine.

3. **Identifiez précisément les règles métier** cachées dans le `ReservationService`. Listez-les sous forme de phrases simples (ex : "Une réservation nécessite un email de membre valide").

4. **Dessinez le diagramme de dépendances actuel** : qui dépend de qui ? Les flèches pointent dans quelle direction ?

5. **Dessinez le diagramme cible** en architecture hexagonale : où placeriez-vous chaque élément ? Comment les dépendances sont-elles inversées ?

### Indices

- Les annotations `@Entity`, `@Autowired`, `@Transactional`, `@RestController` sont des indices forts d'appartenance à l'infrastructure.
- La logique de durée minimale/maximale, la vérification de capacité, la détection de chevauchement et la majoration week-end sont des **règles métier pures** : elles existeraient même sans Spring, sans JPA, sans email.
- En architecture hexagonale, le domaine ne dépend de **rien**. C'est l'infrastructure qui dépend du domaine (inversion de dépendance).
- Pensez aux **ports** : quelles interfaces le domaine devrait-il exposer pour que l'infrastructure puisse s'y connecter ?

### Livrables

- Un tableau à 3 colonnes (Domaine / Application / Infrastructure) avec chaque élément classé
- La liste des règles métier identifiées (minimum 4)
- Deux diagrammes : l'architecture actuelle vs l'architecture cible hexagonale
- Une explication en 2-3 phrases de pourquoi l'architecture actuelle pose problème pour les tests