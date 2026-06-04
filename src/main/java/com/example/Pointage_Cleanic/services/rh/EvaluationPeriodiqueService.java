package com.example.Pointage_Cleanic.services.rh;

import com.example.Pointage_Cleanic.Dto.rh.AutoEvaluationRequest;
import com.example.Pointage_Cleanic.Dto.rh.BesoinFormationDto;
import com.example.Pointage_Cleanic.Dto.rh.EvaluationManagerRequest;
import com.example.Pointage_Cleanic.Dto.rh.EvaluationPeriodiqueDto;
import com.example.Pointage_Cleanic.Dto.rh.ValidationEvaluationRequest;
import com.example.Pointage_Cleanic.Enum.rh.NotationAlphabetique;
import com.example.Pointage_Cleanic.Enum.rh.SourceBesoin;
import com.example.Pointage_Cleanic.Enum.rh.StatutBesoin;
import com.example.Pointage_Cleanic.Enum.rh.StatutEvaluation;
import com.example.Pointage_Cleanic.Mapper.rh.EvaluationPeriodiqueMapper;
import com.example.Pointage_Cleanic.entities.*;
import com.example.Pointage_Cleanic.entities.rh.*;
import com.example.Pointage_Cleanic.exception.ResourceNotFoundException;
import com.example.Pointage_Cleanic.repositories.rh.BesoinFormationRepository;
import com.example.Pointage_Cleanic.repositories.rh.EvaluationPeriodiqueRepository;
import com.example.Pointage_Cleanic.repositories.rh.GrilleEvaluationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class EvaluationPeriodiqueService {

    private static final double SEUIL_A = 4.5;
    private static final double SEUIL_B = 3.5;
    private static final double SEUIL_C = 2.5;

    private final EvaluationPeriodiqueRepository evaluationRepository;
    private final GrilleEvaluationRepository grilleRepository;
    private final BesoinFormationRepository besoinFormationRepository;

    private final EvaluationPeriodiqueMapper mapper;

    public EvaluationPeriodiqueDto create(EvaluationPeriodiqueDto dto) {
        evaluationRepository.findByEmployeIdAndPeriode(dto.getEmployeId(), dto.getPeriode())
                .ifPresent(e -> {
                    throw new IllegalStateException(
                            "Évaluation déjà existante pour employé=" + dto.getEmployeId()
                                    + " et période=" + dto.getPeriode());
                });

        GrilleEvaluation grille = resolveGrille(dto.getGrilleId());

        EvaluationPeriodique entity = mapper.toEntity(dto);
        entity.setGrilleId(grille.getId());
        entity.setGrilleTitre(grille.getTitre());
        entity.setStatut(StatutEvaluation.BROUILLON);
        entity.setDateCreation(LocalDate.now());
        return mapper.toDto(evaluationRepository.save(entity));
    }

    public EvaluationPeriodiqueDto getById(String id) {
        return mapper.toDto(findEntity(id));
    }

    public List<EvaluationPeriodiqueDto> search(String employeId, String departement,
                                                String periode, StatutEvaluation statut) {
        List<EvaluationPeriodique> list;
        if (employeId != null && periode != null) {
            list = evaluationRepository.findByEmployeIdAndPeriode(employeId, periode)
                    .map(List::of).orElse(List.of());
        } else if (employeId != null) {
            list = evaluationRepository.findByEmployeId(employeId);
        } else if (departement != null && periode != null) {
            list = evaluationRepository.findByDepartementAndPeriode(departement, periode);
        } else if (departement != null) {
            list = evaluationRepository.findByDepartement(departement);
        } else if (periode != null) {
            list = evaluationRepository.findByPeriode(periode);
        } else if (statut != null) {
            list = evaluationRepository.findByStatut(statut);
        } else {
            list = evaluationRepository.findAll();
        }
        return list.stream()
                .filter(e -> statut == null || e.getStatut() == statut)
                .map(mapper::toDto)
                .collect(Collectors.toList());
    }

    public EvaluationPeriodiqueDto update(String id, EvaluationPeriodiqueDto dto) {
        EvaluationPeriodique existing = findEntity(id);
        if (existing.getStatut() != StatutEvaluation.BROUILLON) {
            throw new IllegalStateException(
                    "Impossible de modifier une évaluation au statut " + existing.getStatut());
        }
        mapper.updateEntityFromDto(dto, existing);
        return mapper.toDto(evaluationRepository.save(existing));
    }

    public void delete(String id) {
        EvaluationPeriodique existing = findEntity(id);
        if (existing.getStatut() == StatutEvaluation.VALIDE) {
            throw new IllegalStateException("Impossible de supprimer une évaluation validée");
        }
        evaluationRepository.delete(existing);
    }

    // ====================== Workflow ======================

    public EvaluationPeriodiqueDto autoEvaluer(String id, AutoEvaluationRequest req) {
        EvaluationPeriodique e = findEntity(id);
        requireStatut(e, StatutEvaluation.BROUILLON, StatutEvaluation.AUTO_EVALUATION);
        e.setNotesAutoEvaluation(req.getNotesAutoEvaluation() != null
                ? req.getNotesAutoEvaluation() : new ArrayList<>());
        e.setCommentaireEmploye(req.getCommentaireEmploye());
        e.setStatut(StatutEvaluation.AUTO_EVALUATION);
        e.setDateAutoEvaluation(LocalDate.now());
        return mapper.toDto(evaluationRepository.save(e));
    }

    public EvaluationPeriodiqueDto evaluerManager(String id, EvaluationManagerRequest req) {
        EvaluationPeriodique e = findEntity(id);
        requireStatut(e, StatutEvaluation.AUTO_EVALUATION, StatutEvaluation.EVALUATION_MANAGER);
        e.setNotesManager(req.getNotesManager() != null ? req.getNotesManager() : new ArrayList<>());
        e.setCommentaireManager(req.getCommentaireManager());
        e.setObjectifsPeriodeSuivante(req.getObjectifsPeriodeSuivante());
        e.setEvaluateurId(req.getEvaluateurId());
        e.setEvaluateurNom(req.getEvaluateurNom());

        GrilleEvaluation grille = resolveGrille(e.getGrilleId());
        double noteGlobale = calculerNoteGlobale(e.getNotesManager(), grille.getCriteres());
        e.setNoteGlobale(noteGlobale);
        e.setNoteAlphabetique(mapAlphabetique(noteGlobale));

        e.setStatut(StatutEvaluation.EVALUATION_MANAGER);
        e.setDateEvaluationManager(LocalDate.now());
        return mapper.toDto(evaluationRepository.save(e));
    }

    public EvaluationPeriodiqueDto valider(String id, ValidationEvaluationRequest req) {
        EvaluationPeriodique e = findEntity(id);
        requireStatut(e, StatutEvaluation.EVALUATION_MANAGER);

        List<String> besoinsCrees = new ArrayList<>();
        if (req.getBesoinsFormation() != null) {
            for (BesoinFormationDto b : req.getBesoinsFormation()) {
                BesoinFormation besoin = BesoinFormation.builder()
                        .employeId(e.getEmployeId())
                        .matricule(e.getMatricule())
                        .nom(e.getNom())
                        .prenom(e.getPrenom())
                        .departement(e.getDepartement())
                        .competenceLacune(b.getCompetenceLacune())
                        .priorite(b.getPriorite())
                        .formationSuggereId(b.getFormationSuggereId())
                        .source(SourceBesoin.EVALUATION)
                        .statut(StatutBesoin.IDENTIFIE)
                        .dateIdentification(LocalDate.now())
                        .build();
                besoinsCrees.add(besoinFormationRepository.save(besoin).getId());
            }
        }
        e.setBesoinsFormationIdentifies(besoinsCrees);
        e.setStatut(StatutEvaluation.VALIDE);
        e.setDateValidation(LocalDate.now());
        if (req.getValidateurId() != null) e.setEvaluateurId(req.getValidateurId());
        if (req.getValidateurNom() != null) e.setEvaluateurNom(req.getValidateurNom());
        return mapper.toDto(evaluationRepository.save(e));
    }

    // ====================== Calculs ======================

    /**
     * Moyenne pondérée des notes manager selon les poids de la grille.
     * Σ (note × poids) / Σ poids, en ne considérant que les critères notés.
     */
    double calculerNoteGlobale(List<NoteEvaluation> notes, List<CritereEvaluation> criteres) {
        if (notes == null || notes.isEmpty() || criteres == null || criteres.isEmpty()) return 0.0;
        Map<String, Integer> poidsParCode = criteres.stream()
                .collect(Collectors.toMap(CritereEvaluation::getCode,
                        c -> c.getPoids() == null ? 0 : c.getPoids()));

        double sommeNotesPonderees = 0;
        double sommePoids = 0;
        for (NoteEvaluation n : notes) {
            Integer poids = poidsParCode.get(n.getCritereCode());
            if (poids == null || poids == 0 || n.getNote() == null) continue;
            sommeNotesPonderees += n.getNote().doubleValue() * poids;
            sommePoids += poids;
        }
        if (sommePoids == 0) return 0.0;
        return Math.round((sommeNotesPonderees / sommePoids) * 100.0) / 100.0;
    }

    NotationAlphabetique mapAlphabetique(double note) {
        if (note >= SEUIL_A) return NotationAlphabetique.A;
        if (note >= SEUIL_B) return NotationAlphabetique.B;
        if (note >= SEUIL_C) return NotationAlphabetique.C;
        return NotationAlphabetique.D;
    }

    // ====================== Helpers ======================

    private GrilleEvaluation resolveGrille(String grilleId) {
        if (grilleId != null) {
            return grilleRepository.findById(grilleId)
                    .orElseThrow(() -> new ResourceNotFoundException("Grille introuvable : " + grilleId));
        }
        return grilleRepository.findFirstByActifOrderByDateCreationDesc(true)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Aucune grille d'évaluation active — créer une grille avant d'évaluer"));
    }

    private void requireStatut(EvaluationPeriodique e, StatutEvaluation... attendus) {
        for (StatutEvaluation s : attendus) {
            if (e.getStatut() == s) return;
        }
        throw new IllegalStateException("Transition interdite depuis le statut " + e.getStatut());
    }

    private EvaluationPeriodique findEntity(String id) {
        return evaluationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Évaluation introuvable : " + id));
    }
}