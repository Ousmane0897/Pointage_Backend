package com.example.Pointage_Cleanic.services.rh;

import com.example.Pointage_Cleanic.Dto.rh.CreerDemandeValidationRequest;
import com.example.Pointage_Cleanic.Dto.rh.DemandeValidationDto;
import com.example.Pointage_Cleanic.Dto.rh.ValiderDemandeRequest;
import com.example.Pointage_Cleanic.Enum.rh.ActionValidation;
import com.example.Pointage_Cleanic.Enum.rh.StatutDossierEmploye;
import com.example.Pointage_Cleanic.Enum.rh.StatutPeriodeEssai;
import com.example.Pointage_Cleanic.Enum.rh.StatutValidation;
import com.example.Pointage_Cleanic.Mapper.rh.DemandeValidationMapper;
import com.example.Pointage_Cleanic.entities.rh.DemandeValidationPeriodeEssai;
import com.example.Pointage_Cleanic.entities.rh.DossierEmploye;
import com.example.Pointage_Cleanic.entities.rh.PeriodeEssai;
import com.example.Pointage_Cleanic.exception.DemandeValidationConflictException;
import com.example.Pointage_Cleanic.exception.ResourceNotFoundException;
import com.example.Pointage_Cleanic.repositories.rh.DemandeValidationPeriodeEssaiRepository;
import com.example.Pointage_Cleanic.repositories.rh.DossierEmployeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class DemandeValidationPeriodeEssaiService {

    private static final List<StatutValidation> STATUTS_FINAUX =
            List.of(StatutValidation.CONFIRMEE, StatutValidation.REFUSEE);

    private final DemandeValidationPeriodeEssaiRepository repository;
    private final DemandeValidationMapper mapper;
    private final PeriodeEssaiService periodeEssaiService;
    private final DossierEmployeRepository dossierEmployeRepository;

    public List<DemandeValidationDto> list(String statutStr) {
        List<DemandeValidationPeriodeEssai> result;
        if (statutStr != null && !statutStr.isBlank()) {
            StatutValidation statut;
            try {
                statut = StatutValidation.valueOf(statutStr);
            } catch (IllegalArgumentException ex) {
                throw new IllegalArgumentException("Statut validation invalide : " + statutStr);
            }
            result = repository.findByStatut(statut);
        } else {
            result = repository.findAll();
        }
        return mapper.toDtoList(result);
    }

    public DemandeValidationDto creer(String periodeEssaiId, CreerDemandeValidationRequest request) {
        PeriodeEssai periode = periodeEssaiService.requireById(periodeEssaiId);

        if (periode.getStatut() != StatutPeriodeEssai.EN_COURS
                && periode.getStatut() != StatutPeriodeEssai.PROLONGE) {
            throw new IllegalArgumentException(
                    "Impossible de créer une demande de validation : période en statut " + periode.getStatut());
        }

        List<DemandeValidationPeriodeEssai> demandesActives =
                repository.findByPeriodeEssaiIdAndStatutNotIn(periodeEssaiId, STATUTS_FINAUX);
        if (!demandesActives.isEmpty()) {
            throw new DemandeValidationConflictException(
                    "Une demande de validation est déjà active pour cette période d'essai");
        }

        String commentaireInitial = request != null ? request.commentaire() : null;

        DemandeValidationPeriodeEssai demande = DemandeValidationPeriodeEssai.builder()
                .periodeEssaiId(periodeEssaiId)
                .employeId(periode.getEmployeId())
                .employeNom(periode.getEmployeNom())
                .employePrenom(periode.getEmployePrenom())
                .statut(StatutValidation.EN_ATTENTE_MANAGER)
                .dateCreation(LocalDateTime.now())
                .commentaireManager(commentaireInitial)
                .build();

        return mapper.toDto(repository.save(demande));
    }

    public DemandeValidationDto traiter(String demandeId, ValiderDemandeRequest request, String acteurNom) {
        DemandeValidationPeriodeEssai demande = repository.findById(demandeId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Demande de validation introuvable : " + demandeId));

        if (STATUTS_FINAUX.contains(demande.getStatut())) {
            throw new IllegalArgumentException(
                    "Demande déjà finalisée (statut " + demande.getStatut() + ")");
        }
        if (request == null || request.decision() == null) {
            throw new IllegalArgumentException("decision est obligatoire");
        }

        ActionValidation action = request.decision();
        String commentaire = request.commentaire();
        StatutValidation courant = demande.getStatut();

        switch (action) {
            case VALIDER -> {
                if (courant == StatutValidation.EN_ATTENTE_MANAGER) {
                    demande.setCommentaireManager(commentaire);
                    demande.setStatut(StatutValidation.VALIDEE_MANAGER);
                } else if (courant == StatutValidation.VALIDEE_MANAGER) {
                    demande.setCommentaireRh(commentaire);
                    demande.setStatut(StatutValidation.VALIDEE_RH);
                } else {
                    throw new IllegalArgumentException(
                            "Transition VALIDER illégale depuis " + courant);
                }
            }
            case CONFIRMER -> {
                if (courant != StatutValidation.VALIDEE_RH) {
                    throw new IllegalArgumentException(
                            "Transition CONFIRMER illégale depuis " + courant
                                    + " (attendu VALIDEE_RH)");
                }
                if (commentaire != null && !commentaire.isBlank()) {
                    demande.setCommentaireRh(commentaire);
                }
                demande.setStatut(StatutValidation.CONFIRMEE);
                demande.setDateFinalisation(LocalDateTime.now());
                appliquerTitularisation(demande, acteurNom, commentaire);
            }
            case REFUSER -> {
                if (courant == StatutValidation.EN_ATTENTE_MANAGER) {
                    demande.setCommentaireManager(commentaire);
                } else {
                    demande.setCommentaireRh(commentaire);
                }
                demande.setStatut(StatutValidation.REFUSEE);
                demande.setDateFinalisation(LocalDateTime.now());
            }
        }

        return mapper.toDto(repository.save(demande));
    }

    private void appliquerTitularisation(DemandeValidationPeriodeEssai demande,
                                          String acteurNom, String commentaire) {
        String commentaireDecision = commentaire != null && !commentaire.isBlank()
                ? commentaire
                : demande.getCommentaireRh();

        periodeEssaiService.applyTitularisation(
                demande.getPeriodeEssaiId(), acteurNom, commentaireDecision);

        if (demande.getEmployeId() != null) {
            dossierEmployeRepository.findById(demande.getEmployeId()).ifPresent(employe -> {
                employe.setStatut(StatutDossierEmploye.ACTIF);
                employe.setDureeEssaiMois(null);
                dossierEmployeRepository.save(employe);
            });
        }
    }
}