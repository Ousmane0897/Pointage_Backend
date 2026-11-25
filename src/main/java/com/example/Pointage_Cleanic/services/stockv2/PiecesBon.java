package com.example.Pointage_Cleanic.services.stockv2;

import com.example.Pointage_Cleanic.Enum.pieces.TypePiece;
import com.example.Pointage_Cleanic.entities.pieces.AuteurInstantane;
import com.example.Pointage_Cleanic.entities.pieces.PieceBase;
import com.example.Pointage_Cleanic.entities.stockv2.BonEntree;
import com.example.Pointage_Cleanic.entities.stockv2.BonSortie;
import com.example.Pointage_Cleanic.services.terrain.CurrentUserProvider;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Les bons de stock comme pièces (lot 1 / 1b, D4) : bloc
 * {@link PieceBase} (créé à la volée pour un bon antérieur à 1b), auteur courant, instantané de
 * l'état avant une transition (pour le différentiel d'audit).
 */
@Component
@RequiredArgsConstructor
public class PiecesBon {

    private final CurrentUserProvider currentUser;
    private final ObjectMapper objectMapper;

    public AuteurInstantane auteurCourant() {
        return AuteurInstantane.builder()
                .compteId(currentUser.currentUserId())
                .nom(currentUser.currentUserNom())
                .build();
    }

    /** Bloc pièce d'un nouveau bon : auteur et date de création. */
    public PieceBase nouvelle(TypePiece type, LocalDateTime creeLe) {
        return PieceBase.builder().type(type).creePar(auteurCourant()).creeLe(creeLe).build();
    }

    /**
     * Bloc pièce du bon, créé s'il manque (bon antérieur à 1b, avant la ChangeUnit 0012 ou
     * pendant elle) : la référence existante devient le numéro, sans exercice (série historique).
     */
    public PieceBase assurer(BonEntree bon) {
        if (bon.getPiece() == null) {
            bon.setPiece(historique(TypePiece.BE, bon.getReference(), bon.getCreeParId(), bon.getCreeParNom(),
                    bon.getCreatedAt()));
        }
        return bon.getPiece();
    }

    public PieceBase assurer(BonSortie bon) {
        if (bon.getPiece() == null) {
            bon.setPiece(historique(TypePiece.BS, bon.getReference(), bon.getCreeParId(), bon.getCreeParNom(),
                    bon.getCreatedAt()));
        }
        return bon.getPiece();
    }

    static PieceBase historique(TypePiece type, String reference, String creeParId, String creeParNom,
                                LocalDateTime creeLe) {
        return PieceBase.builder()
                .type(type)
                .numero(reference)
                .creePar(creeParId == null && creeParNom == null ? null
                        : AuteurInstantane.builder().compteId(creeParId).nom(creeParNom).build())
                .creeLe(creeLe)
                .build();
    }

    /** Instantané de l'état courant, à prendre avant de modifier le bon (différentiel d'audit). */
    public Map<String, Object> instantane(Object bon) {
        return objectMapper.convertValue(bon, new TypeReference<Map<String, Object>>() {
        });
    }
}
