package com.bibliotech.domain.vo;

public record MemberName(String value) {

    public MemberName {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Le nom du membre ne peut pas etre vide");
        }
        if (value.length() > 100) {
            throw new IllegalArgumentException("Le nom du membre ne peut pas depasser 100 caracteres");
        }
        value = value.trim();
    }
}
