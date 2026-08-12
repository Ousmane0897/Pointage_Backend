package com.example.Pointage_Cleanic.services.rh;

import com.example.Pointage_Cleanic.Dto.rh.DecisionNiveauDto;
import com.example.Pointage_Cleanic.Dto.rh.DemandeCongeDto;
import com.example.Pointage_Cleanic.Dto.rh.HistoriqueValidationCongeDto;
import com.example.Pointage_Cleanic.Enum.rh.NiveauValidationConge;
import com.example.Pointage_Cleanic.entities.rh.DecisionNiveau;
import com.example.Pointage_Cleanic.entities.rh.DemandeConge;
import com.example.Pointage_Cleanic.entities.rh.HistoriqueValidationConge;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Conversion entité ↔ DTO des demandes de congé, partagée par {@link DemandeCongeService}
 * et {@link CongeWorkflowService}.
 *
 * <p>{@code peutValiderParMoi} n'est <b>pas</b> renseigné ici : il dépend de l'appelant et
 * relève du workflow service.
 */
@Component
public class CongeMapper {

    public DemandeCongeDto toDto(DemandeConge e) {
        if (e == null) {
            return null;
        }
        return DemandeCongeDto.builder()
                .id(e.getId()).employeId(e.getEmployeId()).matricule(e.getMatricule())
                .nom(e.getNom()).prenom(e.getPrenom()).departement(e.getDepartement())
                .type(e.getType()).dateDebut(e.getDateDebut()).dateFin(e.getDateFin())
                .nombreJours(e.getNombreJours()).motif(e.getMotif()).statut(e.getStatut())
                .dateDemande(e.getDateDemande())
                // Circuit
                .niveauCourant(NiveauValidationConge.depuisStatut(e.getStatut()).orElse(null))
                .superieurHierarchiqueId(e.getSuperieurHierarchiqueId())
                .superieurHierarchiqueNom(e.getSuperieurHierarchiqueNom())
                .niveauSuperieurIgnore(e.getNiveauSuperieurIgnore())
                .decisionSuperieur(toDto(e.getDecisionSuperieur()))
                .decisionRh(toDto(e.getDecisionRh()))
                .decisionDg(toDto(e.getDecisionDg()))
                .niveauRefus(e.getNiveauRefus())
                .motifRefus(e.getMotifRefus())
                .historique(toHistoriqueDto(e.getHistorique()))
                // Décision finale, format historique
                .dateDecision(e.getDateDecision())
                .decideurId(e.getDecideurId()).decideurNom(e.getDecideurNom())
                .commentaireDecision(e.getCommentaireDecision())
                .build();
    }

    /**
     * Champs acceptés du client à la création. Volontairement partiel : le statut, le circuit,
     * les décisions et l'historique sont posés par le serveur, jamais par le client.
     */
    public DemandeConge toEntity(DemandeCongeDto dto) {
        return DemandeConge.builder()
                .employeId(dto.getEmployeId())
                .type(dto.getType())
                .dateDebut(dto.getDateDebut())
                .dateFin(dto.getDateFin())
                .motif(dto.getMotif())
                .build();
    }

    private DecisionNiveauDto toDto(DecisionNiveau d) {
        if (d == null) {
            return null;
        }
        return DecisionNiveauDto.builder()
                .decideurId(d.getDecideurId())
                .decideurNom(d.getDecideurNom())
                .date(d.getDate())
                .commentaire(d.getCommentaire())
                .build();
    }

    private List<HistoriqueValidationCongeDto> toHistoriqueDto(List<HistoriqueValidationConge> historique) {
        if (historique == null) {
            return List.of();
        }
        return historique.stream()
                .map(h -> HistoriqueValidationCongeDto.builder()
                        .action(h.getAction())
                        .niveau(h.getNiveau())
                        .auteurId(h.getAuteurId())
                        .auteurNom(h.getAuteurNom())
                        .date(h.getDate())
                        .commentaire(h.getCommentaire())
                        .build())
                .collect(Collectors.toList());
    }
}
