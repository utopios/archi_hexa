package org.example.domain.model.valueObject;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

public record TimeSlot(LocalDateTime start, LocalDateTime end) {

    private static final long MIN_DURATION_MINUTES = 30;
    private static final long MAX_DURATION_MINUTES = 480; // 8h

    public TimeSlot {
        if (start == null || end == null) {
            throw new IllegalArgumentException("Le début et la fin ne peuvent pas être null");
        }
        if (!end.isAfter(start)) {
            throw new IllegalArgumentException("La fin du créneau doit être après le début");
        }
        long duration = ChronoUnit.MINUTES.between(start, end);
        if (duration < MIN_DURATION_MINUTES) {
            throw new IllegalArgumentException(
                    "La durée minimale d'un créneau est 30 minutes (actuelle : " + duration + " min)");
        }
        if (duration > MAX_DURATION_MINUTES) {
            throw new IllegalArgumentException(
                    "La durée maximale d'un créneau est 8 heures (actuelle : " + duration + " min)");
        }
    }

    public long durationInMinutes() {
        return ChronoUnit.MINUTES.between(start, end);
    }

    /**
     * Retourne true si le créneau commence un samedi ou un dimanche.
     */
    public boolean isWeekend() {
        DayOfWeek day = start.getDayOfWeek();
        return day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY;
    }

    /**
     * Retourne true si ce créneau chevauche un autre créneau.
     * Deux créneaux se chevauchent si l'un commence avant que l'autre ne finisse,
     * et vice-versa.
     */
    public boolean overlapsWith(TimeSlot other) {
        return this.start.isBefore(other.end) && other.start.isBefore(this.end);
    }

    @Override
    public String toString() {
        return start + " → " + end + " (" + durationInMinutes() + " min)";
    }
}