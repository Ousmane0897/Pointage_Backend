package com.example.Pointage_Cleanic.services;

import com.example.Pointage_Cleanic.Dto.EvaluationFormationDto;
import com.example.Pointage_Cleanic.Dto.FormationDto;
import com.example.Pointage_Cleanic.Dto.ParticipationFormationDto;
import com.example.Pointage_Cleanic.Dto.SessionFormationDto;
import com.example.Pointage_Cleanic.Enum.StatutSession;
import com.example.Pointage_Cleanic.Mapper.EvaluationFormationMapper;
import com.example.Pointage_Cleanic.Mapper.FormationMapper;
import com.example.Pointage_Cleanic.Mapper.ParticipationFormationMapper;
import com.example.Pointage_Cleanic.Mapper.SessionFormationMapper;
import com.example.Pointage_Cleanic.entities.*;
import com.example.Pointage_Cleanic.exception.ResourceNotFoundException;
import com.example.Pointage_Cleanic.repositories.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FormationService {

    private final FormationRepository formationRepository;
    private final SessionFormationRepository sessionFormationRepository;
    private final ParticipationFormationRepository participationFormationRepository;
    private final EvaluationFormationRepository evaluationFormationRepository;
    private final EmployeCompletRepository employeCompletRepository;

    private final FormationMapper formationMapper;
    private final SessionFormationMapper sessionFormationMapper;
    private final ParticipationFormationMapper participationFormationMapper;
    private final EvaluationFormationMapper evaluationFormationMapper;

    // ====================== Formations ======================

    public FormationDto create(FormationDto dto) {
        Formation entity = formationMapper.toEntity(dto);
        entity.setDateCreation(LocalDate.now());
        entity.setDateModification(Instant.now());
        return formationMapper.toDto(formationRepository.save(entity));
    }

    public FormationDto getById(String id) {
        return formationMapper.toDto(findFormation(id));
    }

    public List<FormationDto> search(String q, Boolean actif) {
        List<Formation> list;
        if (actif != null && q != null && !q.isBlank()) {
            list = formationRepository.findByActifAndTitreContainingIgnoreCase(actif, q);
        } else if (actif != null) {
            list = formationRepository.findByActif(actif);
        } else if (q != null && !q.isBlank()) {
            list = formationRepository.findByTitreContainingIgnoreCase(q);
        } else {
            list = formationRepository.findAll();
        }
        return list.stream().map(formationMapper::toDto).collect(Collectors.toList());
    }

    public FormationDto update(String id, FormationDto dto) {
        Formation existing = findFormation(id);
        formationMapper.updateEntityFromDto(dto, existing);
        existing.setDateModification(Instant.now());
        return formationMapper.toDto(formationRepository.save(existing));
    }

    public void delete(String id) {
        formationRepository.delete(findFormation(id));
    }

    // ====================== Sessions ======================

    public SessionFormationDto addSession(String formationId, SessionFormationDto dto) {
        Formation formation = findFormation(formationId);
        SessionFormation session = sessionFormationMapper.toEntity(dto);
        session.setFormationId(formation.getId());
        session.setFormationTitre(formation.getTitre());
        if (session.getStatut() == null) session.setStatut(StatutSession.PLANIFIEE);
        if (session.getParticipantsInscrits() == null) session.setParticipantsInscrits(0);
        return sessionFormationMapper.toDto(sessionFormationRepository.save(session));
    }

    public List<SessionFormationDto> listSessions(String formationId) {
        return sessionFormationRepository.findByFormationId(formationId).stream()
                .map(sessionFormationMapper::toDto).collect(Collectors.toList());
    }

    public SessionFormationDto updateSession(String sessionId, SessionFormationDto dto) {
        SessionFormation existing = findSession(sessionId);
        sessionFormationMapper.updateEntityFromDto(dto, existing);
        return sessionFormationMapper.toDto(sessionFormationRepository.save(existing));
    }

    public void deleteSession(String sessionId) {
        sessionFormationRepository.delete(findSession(sessionId));
    }

    // ====================== Participants ======================

    public ParticipationFormationDto addParticipant(String sessionId, ParticipationFormationDto dto) {
        SessionFormation session = findSession(sessionId);
        if (session.getParticipantsInscrits() != null
                && session.getCapaciteMax() != null
                && session.getParticipantsInscrits() >= session.getCapaciteMax()) {
            throw new IllegalStateException("Session pleine (capacité max atteinte : "
                    + session.getCapaciteMax() + ")");
        }
        if (participationFormationRepository.existsBySessionIdAndEmployeId(sessionId, dto.getEmployeId())) {
            throw new IllegalStateException("Employé déjà inscrit à cette session");
        }
        EmployeComplet employe = employeCompletRepository.findById(dto.getEmployeId())
                .orElseThrow(() -> new ResourceNotFoundException("Employé introuvable : " + dto.getEmployeId()));

        ParticipationFormation participation = participationFormationMapper.toEntity(dto);
        participation.setSessionId(sessionId);
        participation.setEmployeId(employe.getId());
        participation.setMatricule(employe.getMatricule());
        participation.setNom(employe.getNom());
        participation.setPrenom(employe.getPrenom());
        // Pas de champ "departement" sur EmployeComplet — on utilise le poste comme proxy.
        participation.setDepartement(employe.getPoste());

        ParticipationFormation saved = participationFormationRepository.save(participation);

        session.setParticipantsInscrits(session.getParticipantsInscrits() + 1);
        sessionFormationRepository.save(session);

        return participationFormationMapper.toDto(saved);
    }

    public List<ParticipationFormationDto> listParticipants(String sessionId) {
        return participationFormationRepository.findBySessionId(sessionId).stream()
                .map(participationFormationMapper::toDto).collect(Collectors.toList());
    }

    public ParticipationFormationDto marquerPresence(String participationId, boolean present) {
        ParticipationFormation p = findParticipation(participationId);
        p.setPresent(present);
        return participationFormationMapper.toDto(participationFormationRepository.save(p));
    }

    public ParticipationFormationDto marquerCompletee(String participationId, boolean completee) {
        ParticipationFormation p = findParticipation(participationId);
        p.setCompletee(completee);
        if (completee && !p.isAttestationGeneree()) {
            p.setAttestationGeneree(true);
            p.setDateAttestation(LocalDate.now());
        }
        return participationFormationMapper.toDto(participationFormationRepository.save(p));
    }

    // ====================== Évaluations à chaud ======================

    public EvaluationFormationDto addEvaluation(String sessionId, EvaluationFormationDto dto) {
        ParticipationFormation participation = participationFormationRepository
                .findBySessionIdAndEmployeId(sessionId, dto.getEmployeId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Participation introuvable pour session=" + sessionId + " employe=" + dto.getEmployeId()));

        EvaluationFormation evaluation = evaluationFormationMapper.toEntity(dto);
        evaluation.setSessionId(sessionId);
        evaluation.setParticipationId(participation.getId());
        evaluation.setEmployeId(participation.getEmployeId());
        if (evaluation.getDateEvaluation() == null) evaluation.setDateEvaluation(LocalDate.now());

        return evaluationFormationMapper.toDto(evaluationFormationRepository.save(evaluation));
    }

    public List<EvaluationFormationDto> listEvaluations(String sessionId) {
        return evaluationFormationRepository.findBySessionId(sessionId).stream()
                .map(evaluationFormationMapper::toDto).collect(Collectors.toList());
    }

    // ====================== Helpers ======================

    private Formation findFormation(String id) {
        return formationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Formation introuvable : " + id));
    }

    private SessionFormation findSession(String id) {
        return sessionFormationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Session de formation introuvable : " + id));
    }

    private ParticipationFormation findParticipation(String id) {
        return participationFormationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Participation introuvable : " + id));
    }
}