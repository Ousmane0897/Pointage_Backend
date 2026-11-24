package com.example.Pointage_Cleanic.migrations.index;

import org.bson.Document;

import java.util.Objects;

/**
 * Un index attendu en base (lot 1 / 1a, décision D3).
 *
 * @param module        module propriétaire (une ChangeUnit d'index par module)
 * @param collection    collection MongoDB
 * @param nom           nom de l'index, stable : c'est lui qu'on compare en base
 * @param cles          clés, dans l'ordre ({@code 1}, {@code -1} ou {@code "text"})
 * @param unique        index unique : précédé d'une recherche de doublons
 * @param filtrePartiel filtre d'index partiel, ou {@code null}. Pour un unique sur un champ
 *                      facultatif, {@code {champ: {$type: "string"}}} : sans lui, les documents
 *                      sans valeur compteraient comme doublons (null = null)
 */
public record IndexAttendu(String module, String collection, String nom, Document cles,
                           boolean unique, Document filtrePartiel) {

    public IndexAttendu {
        Objects.requireNonNull(module);
        Objects.requireNonNull(collection);
        Objects.requireNonNull(nom);
        Objects.requireNonNull(cles);
    }

    public boolean estTexte() {
        return cles.values().stream().anyMatch("text"::equals);
    }
}
