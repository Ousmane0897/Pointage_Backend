package com.example.Pointage_Cleanic.entities.audit;

import com.example.Pointage_Cleanic.Enum.audit.ActionAudit;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;

/**
 * Ligne du journal d'audit (lot 1 / 1b, décision D6 ; CDC règle 5 : qui, quand, quoi, pourquoi).
 * Écrite dans la transaction métier : elle n'existe que si l'action a eu lieu. Jamais modifiée.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "journal_audit")
public class EntreeAudit {

    @Id
    private String id;

    private Instant le;
    private String compteId;
    private String nomInstantane;

    private ActionAudit action;
    private String objetType;
    private String objetId;
    /** Numéro de la pièce (ou libellé {@code BROUILLON-…}) au moment de l'action. */
    private String numero;

    private List<Changement> changements;
    private String motif;

    private String ip;
    private String correlationId;

    /** Différence d'un champ ; chemin pointé pour les champs imbriqués ({@code piece.numero}). */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Changement {
        private String champ;
        private Object avant;
        private Object apres;
    }
}
