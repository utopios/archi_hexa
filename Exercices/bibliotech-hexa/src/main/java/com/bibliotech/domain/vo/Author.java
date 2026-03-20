package com.bibliotech.domain.vo;

public record Author(String value) {

    public Author {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("L'auteur ne peut pas etre vide");
        }
        if (value.length() > 100) {
            throw new IllegalArgumentException("Le nom de l'auteur ne peut pas depasser 100 caracteres");
        }
        value = value.trim();
    }
}
