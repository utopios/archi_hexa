package org.example.domain.model.valueObject;

import java.util.regex.Pattern;

public record MemberEmail(String value) {

    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[a-zA-Z0-9._%+\\-]+@[a-zA-Z0-9.\\-]+\\.[a-zA-Z]{2,}$");

    public MemberEmail {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("L'email ne peut pas être vide");
        }
        value = value.toLowerCase().strip();
        if (!EMAIL_PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException("Format d'email invalide : " + value);
        }
    }

    public static MemberEmail of(String value) {
        return new MemberEmail(value);
    }

    @Override
    public String toString() {
        return value;
    }
}