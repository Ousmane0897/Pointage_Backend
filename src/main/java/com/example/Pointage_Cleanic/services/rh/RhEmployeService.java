package com.example.Pointage_Cleanic.services.rh;
import com.example.Pointage_Cleanic.services.EmployeCompletService;

import com.example.Pointage_Cleanic.Dto.EmployeCompletDto;
import com.example.Pointage_Cleanic.Dto.rh.RhEmployeStatutRequest;
import com.example.Pointage_Cleanic.Mapper.EmployeCompletMapper;
import com.example.Pointage_Cleanic.Mapper.EmployeMapper;
import com.example.Pointage_Cleanic.entities.Employe;
import com.example.Pointage_Cleanic.entities.EmployeComplet;
import com.example.Pointage_Cleanic.exception.ResourceNotFoundException;
import com.example.Pointage_Cleanic.repositories.EmployeCompletRepository;
import com.example.Pointage_Cleanic.repositories.EmployeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.support.PageableExecutionUtils;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@Service
@RequiredArgsConstructor
public class RhEmployeService {

    private final EmployeCompletRepository employeCompletRepository;
    private final EmployeRepository employeRepository;
    private final EmployeCompletMapper employeCompletMapper;
    private final EmployeMapper employeMapper;
    private final EmployeCompletService employeCompletService;
    private final MongoTemplate mongoTemplate;

    public Page<EmployeCompletDto> list(int page, int size, String q, String statut) {
        Pageable pageable = PageRequest.of(page, size);

        boolean hasQ = q != null && !q.isBlank();
        boolean hasStatut = statut != null && !statut.isBlank();

        if (hasQ && hasStatut) {
            EmployeComplet.StatutEmploye statutEnum = EmployeComplet.StatutEmploye.valueOf(statut);
            Criteria textCriteria = new Criteria().orOperator(
                    Criteria.where("prenom").regex(q, "i"),
                    Criteria.where("nom").regex(q, "i"),
                    Criteria.where("matricule").regex(q, "i"),
                    Criteria.where("email").regex(q, "i")
            );
            Criteria combined = new Criteria().andOperator(
                    Criteria.where("statut").is(statutEnum),
                    textCriteria
            );
            Query query = new Query(combined).with(pageable);
            List<EmployeComplet> results = mongoTemplate.find(query, EmployeComplet.class);
            long count = mongoTemplate.count(new Query(combined), EmployeComplet.class);
            return PageableExecutionUtils.getPage(results, pageable, () -> count)
                    .map(employeCompletMapper::toDto);
        }

        if (hasStatut) {
            EmployeComplet.StatutEmploye statutEnum = EmployeComplet.StatutEmploye.valueOf(statut);
            return employeCompletRepository.findByStatut(statutEnum, pageable)
                    .map(employeCompletMapper::toDto);
        }

        if (hasQ) {
            return employeCompletRepository
                    .findByPrenomContainingIgnoreCaseOrNomContainingIgnoreCaseOrMatriculeContainingIgnoreCaseOrEmailContainingIgnoreCase(
                            q, q, q, q, pageable)
                    .map(employeCompletMapper::toDto);
        }

        return employeCompletRepository.findAll(pageable).map(employeCompletMapper::toDto);
    }

    public EmployeCompletDto getById(String id) {
        EmployeComplet emp = employeCompletRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Employé introuvable : " + id));
        return employeCompletMapper.toDto(emp);
    }

    public EmployeCompletDto create(EmployeCompletDto dto, MultipartFile photo, MultipartFile contrat) throws IOException {
        EmployeComplet created = employeCompletService.create(dto, photo, contrat);
        return employeCompletMapper.toDto(created);
    }

    public EmployeCompletDto update(String id, EmployeCompletDto dto, MultipartFile photo) throws IOException {
        EmployeComplet existing = employeCompletRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Employé introuvable : " + id));

        employeCompletMapper.updateEntityFromDto(dto, existing);

        if (photo != null && !photo.isEmpty()) {
            existing.setPhoto(photo.getBytes());
        }

        EmployeComplet updated = employeCompletRepository.save(existing);

        employeRepository.findByAgentId(existing.getAgentId()).ifPresent(employe -> {
            employeMapper.updateEmployeFromDto(dto, employe);
            employeRepository.save(employe);
        });

        return employeCompletMapper.toDto(updated);
    }

    public void delete(String id) {
        EmployeComplet emp = employeCompletRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Employé introuvable : " + id));
        employeCompletService.delete(emp.getAgentId());
    }

    public EmployeCompletDto updateStatut(String id, RhEmployeStatutRequest request) {
        EmployeComplet emp = employeCompletRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Employé introuvable : " + id));

        emp.setStatut(request.getStatut());

        if (request.getStatut() == EmployeComplet.StatutEmploye.SORTIE) {
            emp.setMotifSortie(request.getMotifSortie());
            emp.setDateSortie(request.getDateSortie());
        }

        return employeCompletMapper.toDto(employeCompletRepository.save(emp));
    }
}