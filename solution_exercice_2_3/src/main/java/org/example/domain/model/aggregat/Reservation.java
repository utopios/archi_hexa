package org.example.domain.model.aggregat;


import org.example.domain.exception.RoomCapacityExceededException;
import org.example.domain.model.entity.ReservationStatus;
import org.example.domain.model.entity.Room;
import org.example.domain.model.valueObject.MemberEmail;
import org.example.domain.model.valueObject.Money;
import org.example.domain.model.valueObject.ReservationId;
import org.example.domain.model.valueObject.TimeSlot;

import java.time.LocalDateTime;
import java.util.Objects;

public class Reservation {

    private final ReservationId id;
    private final MemberEmail memberEmail;
    private final Room room;
    private final TimeSlot timeSlot;
    private final int attendeeCount;
    private final Money price;
    private ReservationStatus status;

    private Reservation(ReservationId id, MemberEmail memberEmail, Room room,
                        TimeSlot timeSlot, int attendeeCount, Money price) {
        this.id = id;
        this.memberEmail = memberEmail;
        this.room = room;
        this.timeSlot = timeSlot;
        this.attendeeCount = attendeeCount;
        this.price = price;
        this.status = ReservationStatus.CONFIRMED;
    }

    /**
     * Factory method — seul point d'entrée pour créer une réservation.
     * Vérifie la capacité de la salle et calcule le prix.
     *
     * @throws RoomCapacityExceededException si la salle ne peut pas accueillir les participants
     */
    public static Reservation create(MemberEmail memberEmail, Room room,
                                     TimeSlot timeSlot, int attendeeCount) {
        if (!room.canAccommodate(attendeeCount)) {
            throw new RoomCapacityExceededException(
                    "La salle '" + room.getName() + "' (capacité " + room.getCapacity()
                            + ") ne peut pas accueillir " + attendeeCount + " participants");
        }
        Money price = room.calculatePrice(timeSlot);
        return new Reservation(ReservationId.generate(), memberEmail, room, timeSlot, attendeeCount, price);
    }

    /**
     * Annule la réservation.
     * Impossible si le créneau a déjà commencé.
     */
    public void cancel() {
        if (status == ReservationStatus.CANCELLED) {
            throw new IllegalStateException("La réservation est déjà annulée");
        }
        if (LocalDateTime.now().isAfter(timeSlot.start())) {
            throw new IllegalStateException("Impossible d'annuler une réservation dont le créneau a démarré");
        }
        this.status = ReservationStatus.CANCELLED;
    }

    public ReservationId getId() { return id; }
    public MemberEmail getMemberEmail() { return memberEmail; }
    public Room getRoom() { return room; }
    public TimeSlot getTimeSlot() { return timeSlot; }
    public int getAttendeeCount() { return attendeeCount; }
    public Money getPrice() { return price; }
    public ReservationStatus getStatus() { return status; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Reservation r)) return false;
        return Objects.equals(id, r.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "Reservation{id=" + id + ", member=" + memberEmail
                + ", room=" + room.getName() + ", slot=" + timeSlot
                + ", price=" + price + ", status=" + status + "}";
    }
}
