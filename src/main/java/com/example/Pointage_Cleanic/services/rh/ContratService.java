package com.example.Pointage_Cleanic.services.rh;

import com.example.Pointage_Cleanic.Dto.rh.AjouterAvenantRequest;
import com.example.Pointage_Cleanic.Dto.rh.AlerteContratDto;
import com.example.Pointage_Cleanic.Dto.rh.ContratDto;
import com.example.Pointage_Cleanic.Dto.rh.RenouvellerContratRequest;
import com.example.Pointage_Cleanic.Enum.rh.StatutContrat;
import com.example.Pointage_Cleanic.Enum.rh.TypeContratRh;
import com.example.Pointage_Cleanic.Mapper.rh.ContratMapper;
import com.example.Pointage_Cleanic.entities.rh.Avenant;
import com.example.Pointage_Cleanic.entities.rh.Contrat;
import com.example.Pointage_Cleanic.entities.rh.DossierEmploye;
import com.example.Pointage_Cleanic.entities.rh.Renouvellement;
import com.example.Pointage_Cleanic.exception.ResourceNotFoundException;
import com.example.Pointage_Cleanic.repositories.rh.ContratRepository;
import com.example.Pointage_Cleanic.repositories.rh.DossierEmployeRepository;
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
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ContratService {

    private final ContratRepository contratRepository;
    private final DossierEmployeRepository dossierEmployeRepository;
    private final ContratMapper contratMapper;
    private final MongoTemplate mongoTemplate;
    private final PeriodeEssaiService periodeEssaiService;

    public ContratDto create(ContratDto dto, MultipartFile fichier) throws IOException {
        DossierEmploye employe = dossierEmployeRepository.findById(dto.getEmployeId())
                .orElseThrow(() -> new ResourceNotFoundException("Dossier employé introuvable : " + dto.getEmployeId()));

        Contrat contrat = contratMapper.toEntity(dto);
        contrat.setEmployeNom(employe.getNom());
        contrat.setEmployePrenom(employe.getPrenom());

        if (contrat.getStatut() == null) {
            contrat.setStatut(StatutContrat.ACTIF);
        }
        if (contrat.getJoursAvantAlerte() == null) {
            contrat.setJoursAvantAlerte(30);
        }

        applyFichier(contrat, fichier);

        Contrat saved = contratRepository.save(contrat);

        Integer dureeEssaiMois = resoudreDureeEssaiMois(saved, employe);
        if (dureeEssaiMois != null && dureeEssaiMois > 0) {
            periodeEssaiService.seedFromContrat(saved, dureeEssaiMois);
        }

        return toDto(saved);
    }

    /**
     * Détermine la durée d'essai (en mois) à appliquer à la création d'un contrat.
     * Priorité au champ explicite {@code Contrat.dureeEssaiMois} (si l'API
     * cliente le fournit en override) ; sinon, dérivation depuis
     * {@code DossierEmploye.dureeEssaiMois} quand l'employé est en
     * {@code EN_PERIODE_ESSAI}. La conversion en jours calendaires
     * (via {@code plusMonths}) est faite dans
     * {@link PeriodeEssaiService#seedFromContrat}.
     * Le frontend Angular {@code Contrat} n'expose pas {@code dureeEssaiMois}
     * pour l'instant : c'est donc la branche dérivation qui est empruntée par
     * défaut.
     */
    private Integer resoudreDureeEssaiMois(Contrat contrat, DossierEmploye employe) {
        if (contrat.getDureeEssaiMois() != null && contrat.getDureeEssaiMois() > 0) {
            return contrat.getDureeEssaiMois();
        }
        if (employe.getStatut() != com.example.Pointage_Cleanic.Enum.rh.StatutDossierEmploye.EN_PERIODE_ESSAI) {
            return null;
        }
        if (employe.getDureeEssaiMois() == null || employe.getDureeEssaiMois() <= 0) {
            return null;
        }
        return employe.getDureeEssaiMois();
    }

    public ContratDto getById(String id) {
        return toDto(requireById(id));
    }

    public Page<ContratDto> list(int page, int size, String q, String typeContrat) {
        Pageable pageable = PageRequest.of(page, size);
        List<Criteria> criterias = new ArrayList<>();

        if (q != null && !q.isBlank()) {
            String escaped = Pattern.quote(q);
            criterias.add(new Criteria().orOperator(
                    Criteria.where("employeNom").regex(escaped, "i"),
                    Criteria.where("employePrenom").regex(escaped, "i"),
                    Criteria.where("employeId").regex(escaped, "i")
            ));
        }
        if (typeContrat != null && !typeContrat.isBlank()) {
            criterias.add(Criteria.where("typeContrat").is(TypeContratRh.valueOf(typeContrat)));
        }

        Query query = new Query();
        if (!criterias.isEmpty()) {
            query.addCriteria(new Criteria().andOperator(criterias.toArray(new Criteria[0])));
        }
        long total = mongoTemplate.count(Query.of(query).limit(-1).skip(-1), Contrat.class);
        query.with(pageable);
        List<Contrat> results = mongoTemplate.find(query, Contrat.class);

        return PageableExecutionUtils.getPage(results, pageable, () -> total)
                .map(this::toDto);
    }

    public List<ContratDto> getByEmployeId(String employeId) {
        return contratRepository.findByEmployeId(employeId).stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    public ContratDto update(String id, ContratDto dto, MultipartFile fichier) throws IOException {
        Contrat existing = requireById(id);
        contratMapper.updateEntityFromDto(dto, existing);
        applyFichier(existing, fichier);
        return toDto(contratRepository.save(existing));
    }

    public void delete(String id) {
        Contrat contrat = requireById(id);
        contratRepository.delete(contrat);
    }

    public ContratDto renouveler(String id, RenouvellerContratRequest request) {
        Contrat contrat = requireById(id);

        Renouvellement renouvellement = Renouvellement.builder()
                .id(UUID.randomUUID().toString())
                .contratId(contrat.getId())
                .ancienneDateFin(contrat.getDateFin())
                .nouvelleDateFin(request.getNouvelleDateFin())
                .dateRenouvellement(LocalDate.now())
                .motif(request.getMotif())
                .build();

        contrat.getRenouvellements().add(renouvellement);
        contrat.setDateFin(request.getNouvelleDateFin());
        contrat.setStatut(StatutContrat.RENOUVELE);

        return toDto(contratRepository.save(contrat));
    }

    public ContratDto ajouterAvenant(String id, AjouterAvenantRequest request) {
        Contrat contrat = requireById(id);

        Avenant avenant = Avenant.builder()
                .id(UUID.randomUUID().toString())
                .contratId(contrat.getId())
                .dateCreation(LocalDate.now())
                .objet(request.getObjet())
                .description(request.getDescription())
                .dateEffet(request.getDateEffet())
                .build();

        contrat.getAvenants().add(avenant);

        return toDto(contratRepository.save(contrat));
    }

    public ContratDto resilier(String id) {
        Contrat contrat = requireById(id);
        contrat.setStatut(StatutContrat.RESILIE);
        return toDto(contratRepository.save(contrat));
    }

    public List<AlerteContratDto> getAlertesEcheance() {
        LocalDate today = LocalDate.now();

        return contratRepository.findByStatutAndDateFinIsNotNull(StatutContrat.ACTIF).stream()
                .filter(c -> c.getDateFin().isAfter(today.minusDays(1)))
                .filter(c -> {
                    long joursRestants = ChronoUnit.DAYS.between(today, c.getDateFin());
                    int seuil = c.getJoursAvantAlerte() != null ? c.getJoursAvantAlerte() : 30;
                    return joursRestants <= seuil;
                })
                .map(c -> {
                    long joursRestants = ChronoUnit.DAYS.between(today, c.getDateFin());
                    return AlerteContratDto.builder()
                            .contratId(c.getId())
                            .employeId(c.getEmployeId())
                            .employeNom(c.getEmployeNom())
                            .employePrenom(c.getEmployePrenom())
                            .typeContrat(c.getTypeContrat())
                            .dateFin(c.getDateFin())
                            .joursRestants(joursRestants)
                            .build();
                })
                .sorted(Comparator.comparingLong(AlerteContratDto::getJoursRestants))
                .collect(Collectors.toList());
    }

    public Contrat getFichier(String id) {
        return requireById(id);
    }

    public void deleteFichier(String id) {
        Contrat contrat = requireById(id);
        contrat.setFichierContrat(null);
        contrat.setFichierContratNom(null);
        contrat.setFichierContratMimeType(null);
        contratRepository.save(contrat);
    }

    private Contrat requireById(String id) {
        return contratRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Contrat introuvable : " + id));
    }

    private void applyFichier(Contrat contrat, MultipartFile fichier) throws IOException {
        if (fichier != null && !fichier.isEmpty()) {
            contrat.setFichierContrat(fichier.getBytes());
            contrat.setFichierContratNom(fichier.getOriginalFilename());
            contrat.setFichierContratMimeType(
                    fichier.getContentType() != null ? fichier.getContentType() : "application/pdf");
        }
    }

    private ContratDto toDto(Contrat contrat) {
        ContratDto dto = contratMapper.toDto(contrat);
        if (contrat.getFichierContrat() != null && contrat.getFichierContrat().length > 0) {
            dto.setFichierContratUrl("/api/gestion-personnel/contrats/" + contrat.getId() + "/fichier");
        }
        return dto;
    }
}