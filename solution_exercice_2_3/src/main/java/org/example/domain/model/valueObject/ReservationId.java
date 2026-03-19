package org.example.domain.model.valueObject;

import java.util.UUID;

public record ReservationId(UUID value) {

    public ReservationId {
        if (value == null) {
            throw new IllegalArgumentException("L'identifiant de réservation ne peut pas être null");
        }
    }

    public static ReservationId generate() {
        return new ReservationId(UUID.randomUUID());
    }

    public static ReservationId of(String uuid) {
        return new ReservationId(UUID.fromString(uuid));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
