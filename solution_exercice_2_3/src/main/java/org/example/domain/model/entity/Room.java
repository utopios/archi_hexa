package org.example.domain.model.entity;

import org.example.domain.model.valueObject.Money;
import org.example.domain.model.valueObject.RoomId;
import org.example.domain.model.valueObject.TimeSlot;

import java.math.BigDecimal;
import java.util.Objects;

public class Room {

    private static final BigDecimal WEEKEND_SURCHARGE = new BigDecimal("1.20");

    private final RoomId id;
    private final String name;
    private final int capacity;
    private final Money hourlyRate;

    private Room(RoomId id, String name, int capacity, Money hourlyRate) {
        this.id = id;
        this.name = name;
        this.capacity = capacity;
        this.hourlyRate = hourlyRate;
    }

    public static Room of(RoomId id, String name, int capacity, Money hourlyRate) {
        if (id == null) throw new IllegalArgumentException("L'identifiant de salle est requis");
        if (name == null || name.isBlank()) throw new IllegalArgumentException("Le nom de la salle est requis");
        if (capacity <= 0) throw new IllegalArgumentException("La capacité doit être strictement positive");
        if (hourlyRate == null) throw new IllegalArgumentException("Le tarif horaire est requis");
        return new Room(id, name, capacity, hourlyRate);
    }

    /**
     * Vérifie si la salle peut accueillir le nombre de participants demandé.
     */
    public boolean canAccommodate(int attendeeCount) {
        return attendeeCount <= this.capacity;
    }

    /**
     * Calcule le prix de la réservation pour un créneau donné.
     * Applique une majoration de 20% si le créneau est un week-end.
     */
    public Money calculatePrice(TimeSlot slot) {
        double hours = slot.durationInMinutes() / 60.0;
        Money basePrice = hourlyRate.multiply(BigDecimal.valueOf(hours));
        if (slot.isWeekend()) {
            return basePrice.multiply(WEEKEND_SURCHARGE);
        }
        return basePrice;
    }

    public RoomId getId() { return id; }
    public String getName() { return name; }
    public int getCapacity() { return capacity; }
    public Money getHourlyRate() { return hourlyRate; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Room room)) return false;
        return Objects.equals(id, room.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "Room{id=" + id + ", name='" + name + "', capacity=" + capacity + ", hourlyRate=" + hourlyRate + "}";
    }
}
