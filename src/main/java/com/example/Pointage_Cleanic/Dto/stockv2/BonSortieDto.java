package com.example.Pointage_Cleanic.Dto.stockv2;

import com.example.Pointage_Cleanic.Enum.stockv2.NatureDon;
import com.example.Pointage_Cleanic.Enum.stockv2.StatutBon;
import com.example.Pointage_Cleanic.Enum.stockv2.TypeSortie;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BonSortieDto {
    private String id;
    /** Nul en brouillon depuis le lot 1 / 1b (D4) : le numéro est attribué à la validation. */
    private String reference;
    /** Numéro à afficher : la référence, sinon {@code BROUILLON-<6 derniers caractères de l'id>}. */
    private String libelleNumero;
    /** Annulation d'un bon EFFECTIF (qui, quand, motif) ; nul sinon. */
    private com.example.Pointage_Cleanic.entities.pieces.Annulation annulation;
    /** Abandon d'un brouillon (qui, quand) ; nul sinon. */
    private com.example.Pointage_Cleanic.entities.pieces.Abandon abandon;
    private TypeSortie type;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate date;

    private String siteSourceId;
    private String siteSourceNom;
    private String motif;

    private NatureDon natureDon;
    private String beneficiaireDon;
    private String chantierId;
    private String chantierReference;

    private DestinataireDto destinataire;
    private List<LigneBonDto> lignes;
    private StatutBon statut;
    private String demandeurId;
    private String demandeurNom;
    private String validateurId;
    private String validateurNom;

    /**
     * Auteur du bon, déduit du JWT à la création. Le front compare {@code creeParEmail} à l'adresse
     * de connexion pour décider des actions réservées au créateur — le JWT ne portant ni id ni
     * username, l'e-mail est le seul point de comparaison disponible.
     */
    private String creeParId;
    private String creeParEmail;
    private String creeParNom;

    private String commentaire;
    private String motifRefus;
    private List<HistoriqueDto> historique;
    private long montantTotal;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
