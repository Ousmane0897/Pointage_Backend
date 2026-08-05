package com.example.Pointage_Cleanic.Dto.rh;

import com.example.Pointage_Cleanic.Enum.rh.NiveauValidationConge;
import com.example.Pointage_Cleanic.Enum.rh.StatutDemande;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Charge utile des notifications temps réel du circuit de validation des congés
 * ({@code /topic/conges-validations} en broadcast,
 * {@code /user/queue/notifications-conges} ciblée sur le validateur attendu).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class NotificationCongeDto {

    /**
     * {@code DEMANDE_SOUMISE | DEMANDE_VALIDEE_NIVEAU | DEMANDE_APPROUVEE |
     * DEMANDE_REFUSEE | DEMANDE_ANNULEE | INFO}
     */
    private String type;

    private String demandeId;
    private String employeId;
    private String employeNom;

    /** Niveau désormais attendu (ou celui qui vient d'agir). */
    private NiveauValidationConge niveau;

    private StatutDemande statut;
    private String titre;
    private String message;
    private LocalDateTime dateEmission;
}
