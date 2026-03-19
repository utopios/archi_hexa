package org.example.domain.model.valueObject;

import java.util.UUID;

public record RoomId(UUID value) {

    public RoomId {
        if (value == null) {
            throw new IllegalArgumentException("L'identifiant de salle ne peut pas être null");
        }
    }

    public static RoomId generate() {
        return new RoomId(UUID.randomUUID());
    }

    public static RoomId of(String uuid) {
        return new RoomId(UUID.fromString(uuid));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}