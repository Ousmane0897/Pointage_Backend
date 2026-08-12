package com.example.Pointage_Cleanic.Dto.terrain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Corps de {@code POST /api/terrain/planning/affectations/{id}/annuler}.
 *
 * <p>Le motif est validé dans {@code PlanningService.annuler} (obligatoire, 5 caractères minimum
 * après {@code trim}) plutôt que par Bean Validation : {@code @Size} compte la chaîne brute.
 * L'auteur de l'annulation est déduit du JWT, jamais transmis ici.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AnnulationAffectationRequest(String motif) {
}
