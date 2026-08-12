package com.example.Pointage_Cleanic.entities.stockv2;

import com.example.Pointage_Cleanic.Enum.stockv2.NatureDon;
import com.example.Pointage_Cleanic.Enum.stockv2.StatutBon;
import com.example.Pointage_Cleanic.Enum.stockv2.TypeSortie;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** Bon de sortie (7.4). Génère des mouvements SORTIE depuis le site source à la validation. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "stockv2_bons_sortie")
public class BonSortie {

    @Id
    private String id;

    @Indexed(unique = true, sparse = true)
    private String reference;

    private TypeSortie type;
    private LocalDate date;

    private String siteSourceId;
    private String siteSourceNom;

    private DestinataireBon destinataire;
    private String motif;

    // --- 7.5 : don (requis si type = DON) ---
    private NatureDon natureDon;
    private String beneficiaireDon;

    // --- 7.5 : rattachement chantier (requis si type = DISTRIBUTION_CHANTIER) ---
    private String chantierId;
    private String chantierReference;

    @Builder.Default
    private List<LigneBon> lignes = new ArrayList<>();

    @Builder.Default
    private StatutBon statut = StatutBon.BROUILLON;

    private String demandeurId;
    private String demandeurNom;
    private String validateurId;
    private String validateurNom;

    /**
     * Auteur du bon, renseigné <b>serveur</b> depuis le JWT à la création et jamais accepté du
     * client (absent de {@code BonSortiePayload}). Distinct du {@code demandeurId}, qui est choisi
     * dans le formulaire : c'est l'auteur qui gouverne les habilitations (reprise après refus,
     * édition, suppression d'un brouillon), pas le demandeur.
     */
    private String creeParId;
    private String creeParEmail;
    private String creeParNom;

    private String commentaire;
    private String motifRefus;

    @Builder.Default
    private List<EntreeHistorique> historique = new ArrayList<>();

    private long montantTotal;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
