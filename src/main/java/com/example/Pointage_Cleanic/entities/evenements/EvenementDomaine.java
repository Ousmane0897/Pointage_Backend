package com.example.Pointage_Cleanic.entities.evenements;

import com.example.Pointage_Cleanic.Enum.evenements.StatutEvenement;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.Map;

/**
 * Événement de domaine (lot 1 / 1b, décision D5), écrit dans la <b>même transaction</b> que la
 * pièce qui le produit ({@code services.evenements.EvenementPublisher}), puis remis aux
 * consommateurs par {@code DispatcherEvenements}.
 *
 * <p>Les événements traités ne sont pas supprimés : ils forment un journal rejouable (base des
 * futures écritures comptables, lots 4 et 7).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "evenements_domaine")
public class EvenementDomaine {

    @Id
    private String id;

    /** Type d'événement, ex. {@code PIECE_VALIDEE} (constantes dans {@code TypesEvenement}). */
    private String type;
    /** Version du schéma du {@link #payload} (et non verrou optimiste). */
    private int version;

    private String agregatType;
    private String agregatId;
    private String siteId;

    private Instant survenuLe;
    private String auteurCompteId;
    private String auteurNom;
    private String correlationId;

    private Map<String, Object> payload;

    private StatutEvenement statut;
    private int tentatives;
    private Instant prochainEssai;
    /** Fin du bail du dispatcher qui le traite (statut {@code EN_COURS}). */
    private Instant bailJusquA;
    private String derniereErreur;
    private Instant traiteLe;
}
