package com.example.Pointage_Cleanic.Dto.terrain;

/**
 * Effectif actuel d'un site client comparé à son plafond.
 *
 * @param nombreActuel effectif calculé selon le périmètre demandé (RH ou TERRAIN)
 * @param nombreMax    plafond {@code nombreMaxEmployes} du site, ou {@code null} si non configuré
 */
public record EffectifSiteDto(long nombreActuel, Integer nombreMax) {
}
