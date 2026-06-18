package com.example.Pointage_Cleanic.services.stockv2;

import com.example.Pointage_Cleanic.Dto.stockv2.ChantierDto;
import com.example.Pointage_Cleanic.Dto.stockv2.ChantierPayload;
import com.example.Pointage_Cleanic.Dto.stockv2.DetailChantierDto;
import com.example.Pointage_Cleanic.Dto.stockv2.LigneConsommationChantierDto;
import com.example.Pointage_Cleanic.Enum.stockv2.StatutChantier;
import com.example.Pointage_Cleanic.Mapper.stockv2.ChantierMapper;
import com.example.Pointage_Cleanic.entities.stockv2.Chantier;
import com.example.Pointage_Cleanic.entities.stockv2.MouvementStock;
import com.example.Pointage_Cleanic.entities.stockv2.ProduitStock;
import com.example.Pointage_Cleanic.exception.ResourceNotFoundException;
import com.example.Pointage_Cleanic.exception.StockConflitException;
import com.example.Pointage_Cleanic.repositories.stockv2.ChantierRepository;
import com.example.Pointage_Cleanic.repositories.stockv2.MouvementStockRepository;
import com.example.Pointage_Cleanic.util.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/** CRUD + workflow chantier (7.5) ; coût/nb de mouvements recalculés à la lecture. */
@Service
@RequiredArgsConstructor
public class ChantierService {

    private final ChantierRepository repository;
    private final ChantierMapper mapper;
    private final MouvementStockRepository mouvementRepository;
    private final ReferentielSiteService referentielSite;
    private final AnalyseSupport support;
    private final MongoTemplate mongoTemplate;

    public PageResponse<ChantierDto> list(int page, int size, String q, StatutChantier statut,
                                          String siteId, LocalDate dateDebut, LocalDate dateFin) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("dateDebut").descending());
        Query query = new Query().with(pageable);
        if (AnalyseSupport.notBlank(q)) {
            String regex = ".*" + Pattern.quote(q) + ".*";
            query.addCriteria(new Criteria().orOperator(
                    Criteria.where("reference").regex(regex, "i"),
                    Criteria.where("nom").regex(regex, "i"),
                    Criteria.where("client").regex(regex, "i")));
        }
        if (statut != null) {
            query.addCriteria(Criteria.where("statut").is(statut));
        }
        if (AnalyseSupport.notBlank(siteId)) {
            query.addCriteria(Criteria.where("siteId").is(siteId));
        }
        AnalyseSupport.appliquerDates(query, dateDebut, dateFin);

        List<Chantier> results = mongoTemplate.find(query, Chantier.class);
        long total = mongoTemplate.count(Query.of(query).limit(-1).skip(-1), Chantier.class);
        List<ChantierDto> content = results.stream().map(this::toDtoEnrichi).toList();
        return new PageResponse<>(content, total);
    }

    public List<ChantierDto> actifs() {
        return repository.findByStatut(StatutChantier.EN_COURS).stream().map(this::toDtoEnrichi).toList();
    }

    public DetailChantierDto detail(String id) {
        Chantier chantier = loadOrThrow(id);
        List<MouvementStock> mouvements = mouvementRepository.findByChantierId(id);
        Map<String, ProduitStock> produits = support.produitsByIds(mouvements);

        Map<String, LigneConsommationChantierDto> lignes = new LinkedHashMap<>();
        long coutTotal = 0;
        for (MouvementStock m : mouvements) {
            long montant = support.montant(m, produits);
            coutTotal += montant;
            LigneConsommationChantierDto ligne = lignes.computeIfAbsent(m.getProduitId(), pid -> {
                ProduitStock p = produits.get(pid);
                return LigneConsommationChantierDto.builder()
                        .produitId(pid)
                        .produitCode(m.getProduitCode())
                        .produitLibelle(m.getProduitLibelle())
                        .unite(m.getUnite())
                        .prixUnitaire(p == null ? null : p.getPrixUnitaire())
                        .quantite(0)
                        .montant(0)
                        .build();
            });
            ligne.setQuantite(ligne.getQuantite() + m.getQuantite());
            ligne.setMontant(ligne.getMontant() + montant);
            if (m.getDate() != null) {
                if (ligne.getPremiereDate() == null || m.getDate().isBefore(ligne.getPremiereDate())) {
                    ligne.setPremiereDate(m.getDate());
                }
                if (ligne.getDerniereDate() == null || m.getDate().isAfter(ligne.getDerniereDate())) {
                    ligne.setDerniereDate(m.getDate());
                }
            }
        }

        return DetailChantierDto.builder()
                .chantier(mapper.toDto(applyDenormalisation(chantier, coutTotal, mouvements.size())))
                .lignes(new ArrayList<>(lignes.values()))
                .coutTotal(coutTotal)
                .nbProduits(lignes.size())
                .dureeJours(dureeJours(chantier))
                .build();
    }

    public ChantierDto creer(ChantierPayload payload) {
        validerPayload(payload);
        if (repository.existsByReference(payload.getReference().trim())) {
            throw new StockConflitException("Référence de chantier déjà utilisée : " + payload.getReference());
        }
        LocalDateTime now = LocalDateTime.now();
        Chantier chantier = Chantier.builder()
                .reference(payload.getReference().trim())
                .nom(payload.getNom().trim())
                .siteId(siteId(payload))
                .siteNom(siteNom(payload))
                .client(payload.getClient())
                .description(payload.getDescription())
                .dateDebut(payload.getDateDebut())
                .statut(StatutChantier.EN_COURS)
                .createdAt(now)
                .updatedAt(now)
                .build();
        return toDtoEnrichi(repository.save(chantier));
    }

    public ChantierDto modifier(String id, ChantierPayload payload) {
        Chantier chantier = loadOrThrow(id);
        exigerEnCours(chantier, "modifié");
        validerPayload(payload);
        String nouvelleRef = payload.getReference().trim();
        if (!nouvelleRef.equals(chantier.getReference()) && repository.existsByReference(nouvelleRef)) {
            throw new StockConflitException("Référence de chantier déjà utilisée : " + nouvelleRef);
        }
        chantier.setReference(nouvelleRef);
        chantier.setNom(payload.getNom().trim());
        chantier.setSiteId(siteId(payload));
        chantier.setSiteNom(siteNom(payload));
        chantier.setClient(payload.getClient());
        chantier.setDescription(payload.getDescription());
        chantier.setDateDebut(payload.getDateDebut());
        chantier.setUpdatedAt(LocalDateTime.now());
        return toDtoEnrichi(repository.save(chantier));
    }

    public void supprimer(String id) {
        Chantier chantier = loadOrThrow(id);
        exigerEnCours(chantier, "supprimé");
        repository.deleteById(id);
    }

    public ChantierDto cloturer(String id) {
        Chantier chantier = loadOrThrow(id);
        if (chantier.getStatut() == StatutChantier.CLOTURE) {
            throw new StockConflitException("Le chantier " + chantier.getReference() + " est déjà clôturé");
        }
        chantier.setStatut(StatutChantier.CLOTURE);
        chantier.setDateFin(LocalDate.now());
        chantier.setUpdatedAt(LocalDateTime.now());
        return toDtoEnrichi(repository.save(chantier));
    }

    // ---------- internes ----------

    private void validerPayload(ChantierPayload payload) {
        if (payload == null || !AnalyseSupport.notBlank(payload.getReference())) {
            throw new IllegalArgumentException("La référence du chantier est obligatoire");
        }
        if (!AnalyseSupport.notBlank(payload.getNom())) {
            throw new IllegalArgumentException("Le nom du chantier est obligatoire");
        }
        if (payload.getDateDebut() == null) {
            throw new IllegalArgumentException("La date de début du chantier est obligatoire");
        }
    }

    private String siteId(ChantierPayload payload) {
        return AnalyseSupport.notBlank(payload.getSiteId()) ? payload.getSiteId() : null;
    }

    private String siteNom(ChantierPayload payload) {
        return AnalyseSupport.notBlank(payload.getSiteId())
                ? referentielSite.valideEtCharge(payload.getSiteId()).getNom() : null;
    }

    private ChantierDto toDtoEnrichi(Chantier chantier) {
        List<MouvementStock> mouvements = mouvementRepository.findByChantierId(chantier.getId());
        Map<String, ProduitStock> produits = support.produitsByIds(mouvements);
        long coutTotal = mouvements.stream().mapToLong(m -> support.montant(m, produits)).sum();
        return mapper.toDto(applyDenormalisation(chantier, coutTotal, mouvements.size()));
    }

    private Chantier applyDenormalisation(Chantier chantier, long coutTotal, int nbMouvements) {
        chantier.setCoutTotal(coutTotal);
        chantier.setNbMouvements(nbMouvements);
        return chantier;
    }

    private Integer dureeJours(Chantier chantier) {
        if (chantier.getDateDebut() == null) {
            return null;
        }
        LocalDate fin = chantier.getDateFin() != null ? chantier.getDateFin() : LocalDate.now();
        return (int) (ChronoUnit.DAYS.between(chantier.getDateDebut(), fin) + 1);
    }

    private Chantier loadOrThrow(String id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Chantier introuvable : " + id));
    }

    private void exigerEnCours(Chantier chantier, String action) {
        if (chantier.getStatut() == StatutChantier.CLOTURE) {
            throw new StockConflitException("Un chantier clôturé ne peut pas être " + action);
        }
    }
}
