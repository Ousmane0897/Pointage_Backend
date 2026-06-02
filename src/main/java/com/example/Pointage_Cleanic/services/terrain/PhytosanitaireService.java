package com.example.Pointage_Cleanic.services.terrain;

import com.example.Pointage_Cleanic.Dto.terrain.AlerteDelaiPhyto;
import com.example.Pointage_Cleanic.Enum.terrain.CategoriePhyto;
import com.example.Pointage_Cleanic.Enum.terrain.StatutApplicationPhyto;
import com.example.Pointage_Cleanic.Enum.terrain.TypeAlerteDelai;
import com.example.Pointage_Cleanic.entities.DossierEmploye;
import com.example.Pointage_Cleanic.entities.terrain.ApplicationPhyto;
import com.example.Pointage_Cleanic.entities.terrain.ProduitPhytosanitaire;
import com.example.Pointage_Cleanic.entities.terrain.SiteClient;
import com.example.Pointage_Cleanic.exception.ResourceNotFoundException;
import com.example.Pointage_Cleanic.exception.TerrainConflitException;
import com.example.Pointage_Cleanic.repositories.terrain.ApplicationPhytoRepository;
import com.example.Pointage_Cleanic.repositories.terrain.ProduitPhytoRepository;
import com.example.Pointage_Cleanic.util.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PhytosanitaireService {

    private final ProduitPhytoRepository produitRepository;
    private final ApplicationPhytoRepository applicationRepository;
    private final MongoTemplate mongoTemplate;
    private final ReferentielRhService referentielRh;
    private final SiteClientService siteService;
    private final CompteurTerrainService compteur;
    private final RegistrePhytoPdfService pdfService;

    // ───────────────────────── Produits ─────────────────────────

    public List<ProduitPhytosanitaire> listProduits() {
        return produitRepository.findAll();
    }

    public ProduitPhytosanitaire getProduit(String id) {
        return loadProduitOrThrow(id);
    }

    public ProduitPhytosanitaire createProduit(ProduitPhytosanitaire produit) {
        produit.setId(null);
        if (produit.getNumeroHomologation() == null || produit.getNumeroHomologation().isBlank()) {
            throw new IllegalArgumentException("numeroHomologation obligatoire");
        }
        if (produitRepository.existsByNumeroHomologation(produit.getNumeroHomologation())) {
            throw new TerrainConflitException("Numéro d'homologation déjà utilisé : " + produit.getNumeroHomologation());
        }
        produit.setCreatedAt(LocalDateTime.now());
        return produitRepository.save(produit);
    }

    public ProduitPhytosanitaire updateProduit(String id, ProduitPhytosanitaire patch) {
        ProduitPhytosanitaire entity = loadProduitOrThrow(id);
        if (patch.getNumeroHomologation() != null
                && !patch.getNumeroHomologation().equals(entity.getNumeroHomologation())
                && produitRepository.existsByNumeroHomologation(patch.getNumeroHomologation())) {
            throw new TerrainConflitException("Numéro d'homologation déjà utilisé : " + patch.getNumeroHomologation());
        }
        patch.setId(entity.getId());
        patch.setCreatedAt(entity.getCreatedAt());
        return produitRepository.save(patch);
    }

    public void deleteProduit(String id) {
        produitRepository.delete(loadProduitOrThrow(id));
    }

    private ProduitPhytosanitaire loadProduitOrThrow(String id) {
        return produitRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Produit phytosanitaire introuvable : " + id));
    }

    // ───────────────────────── Applications ─────────────────────────

    public PageResponse<ApplicationPhyto> listApplications(int page, int size, LocalDate dateDebut, LocalDate dateFin,
                                                           String siteId, String employeId, String produitId,
                                                           CategoriePhyto categorie, StatutApplicationPhyto statut) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("dateApplication").descending());
        Query query = new Query().with(pageable);
        if (dateDebut != null) query.addCriteria(Criteria.where("dateApplication").gte(dateDebut.atStartOfDay()));
        if (dateFin != null) query.addCriteria(Criteria.where("dateApplication").lte(dateFin.atTime(LocalTime.MAX)));
        if (siteId != null && !siteId.isBlank()) query.addCriteria(Criteria.where("siteId").is(siteId));
        if (employeId != null && !employeId.isBlank()) query.addCriteria(Criteria.where("employeId").is(employeId));
        if (produitId != null && !produitId.isBlank()) query.addCriteria(Criteria.where("produitId").is(produitId));
        if (statut != null) query.addCriteria(Criteria.where("statut").is(statut));
        if (categorie != null) {
            List<String> ids = produitRepository.findByCategorie(categorie).stream()
                    .map(ProduitPhytosanitaire::getId).toList();
            query.addCriteria(Criteria.where("produitId").in(ids));
        }

        List<ApplicationPhyto> results = mongoTemplate.find(query, ApplicationPhyto.class);
        long total = mongoTemplate.count(Query.of(query).limit(-1).skip(-1), ApplicationPhyto.class);
        return new PageResponse<>(results, total);
    }

    public List<ApplicationPhyto> applicationsPeriode(LocalDate dateDebut, LocalDate dateFin) {
        return applicationRepository.findByDateApplicationBetween(
                dateDebut.atStartOfDay(), dateFin.atTime(LocalTime.MAX));
    }

    public ApplicationPhyto getApplication(String id) {
        return loadApplicationOrThrow(id);
    }

    public ApplicationPhyto createApplication(ApplicationPhyto application) {
        application.setId(null);
        denormaliserApplication(application);
        application.setNumero(compteur.genererNumeroApplication(LocalDate.now()));
        if (application.getDateApplication() == null) {
            application.setDateApplication(LocalDateTime.now());
        }
        if (application.getStatut() == null) {
            application.setStatut(StatutApplicationPhyto.PLANIFIEE);
        }
        LocalDateTime now = LocalDateTime.now();
        application.setCreatedAt(now);
        application.setUpdatedAt(now);
        return applicationRepository.save(application);
    }

    public ApplicationPhyto updateApplication(String id, ApplicationPhyto patch) {
        ApplicationPhyto entity = loadApplicationOrThrow(id);
        patch.setId(entity.getId());
        patch.setNumero(entity.getNumero());
        patch.setCreatedAt(entity.getCreatedAt());
        denormaliserApplication(patch);
        patch.setUpdatedAt(LocalDateTime.now());
        return applicationRepository.save(patch);
    }

    public void deleteApplication(String id) {
        applicationRepository.delete(loadApplicationOrThrow(id));
    }

    private void denormaliserApplication(ApplicationPhyto application) {
        DossierEmploye employe = referentielRh.valideEtCharge(application.getEmployeId());
        application.setEmployeMatricule(employe.getMatricule());
        application.setEmployeNom(ReferentielRhService.nomComplet(employe));

        SiteClient site = siteService.loadOrThrow(application.getSiteId());
        application.setSiteCode(site.getCode());
        application.setSiteNom(site.getNom());

        ProduitPhytosanitaire produit = loadProduitOrThrow(application.getProduitId());
        application.setProduitNomCommercial(produit.getNomCommercial());
        application.setProduitNumeroHomologation(produit.getNumeroHomologation());

        // Délais réglementaires calculés depuis la date d'application
        LocalDateTime base = application.getDateApplication() == null
                ? LocalDateTime.now() : application.getDateApplication();
        if (produit.getDelaiReentreeHeures() != null) {
            application.setDateFinReentree(base.plusHours(produit.getDelaiReentreeHeures()));
        }
        if (produit.getDelaiAvantNouvelleApplicationJours() != null) {
            application.setDateProchaineApplicationAutorisee(
                    base.plusDays(produit.getDelaiAvantNouvelleApplicationJours()));
        }
    }

    private ApplicationPhyto loadApplicationOrThrow(String id) {
        return applicationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Application phytosanitaire introuvable : " + id));
    }

    // ───────────────────────── Alertes délais ─────────────────────────

    public List<AlerteDelaiPhyto> alertesDelais() {
        LocalDateTime now = LocalDateTime.now();
        List<AlerteDelaiPhyto> alertes = new ArrayList<>();
        for (ApplicationPhyto a : applicationRepository.findByStatut(StatutApplicationPhyto.EFFECTUEE)) {
            if (a.getDateFinReentree() != null && now.isBefore(a.getDateFinReentree())) {
                alertes.add(construireAlerte(a, TypeAlerteDelai.REENTREE_ACTIVE, a.getDateFinReentree(), now));
            }
            if (a.getDateProchaineApplicationAutorisee() != null
                    && now.isBefore(a.getDateProchaineApplicationAutorisee())) {
                alertes.add(construireAlerte(a, TypeAlerteDelai.NOUVELLE_APPLICATION_INTERDITE,
                        a.getDateProchaineApplicationAutorisee(), now));
            }
        }
        return alertes;
    }

    private AlerteDelaiPhyto construireAlerte(ApplicationPhyto a, TypeAlerteDelai type,
                                              LocalDateTime contrainte, LocalDateTime now) {
        return AlerteDelaiPhyto.builder()
                .applicationId(a.getId())
                .type(type)
                .siteId(a.getSiteId())
                .siteNom(a.getSiteNom())
                .zoneTraitee(a.getZoneTraitee() == null ? null : a.getZoneTraitee().getLibelle())
                .produitNom(a.getProduitNomCommercial())
                .dateFinContrainte(contrainte)
                .heuresRestantes(Math.max(0, Duration.between(now, contrainte).toHours()))
                .build();
    }

    // ───────────────────────── Registre PDF ─────────────────────────

    public byte[] registrePdf(LocalDate dateDebut, LocalDate dateFin) {
        return pdfService.generer(applicationsPeriode(dateDebut, dateFin), dateDebut, dateFin);
    }
}