package com.example.Pointage_Cleanic.services.stockv2;

import com.example.Pointage_Cleanic.Dto.stockv2.BonSortieDto;
import com.example.Pointage_Cleanic.Dto.stockv2.BonSortiePayload;
import com.example.Pointage_Cleanic.Dto.stockv2.DecisionPayload;
import com.example.Pointage_Cleanic.Dto.stockv2.DestinatairePayload;
import com.example.Pointage_Cleanic.Dto.stockv2.NotificationStockDto;
import com.example.Pointage_Cleanic.Enum.stockv2.ActionWorkflow;
import com.example.Pointage_Cleanic.Enum.stockv2.StatutBon;
import com.example.Pointage_Cleanic.Enum.stockv2.StatutChantier;
import com.example.Pointage_Cleanic.Enum.stockv2.TypeSortie;
import com.example.Pointage_Cleanic.Mapper.stockv2.BonSortieMapper;
import com.example.Pointage_Cleanic.entities.stockv2.BonSortie;
import com.example.Pointage_Cleanic.entities.stockv2.Chantier;
import com.example.Pointage_Cleanic.entities.stockv2.DestinataireBon;
import com.example.Pointage_Cleanic.entities.stockv2.LigneBon;
import com.example.Pointage_Cleanic.exception.ResourceNotFoundException;
import com.example.Pointage_Cleanic.exception.StockAccesRefuseException;
import com.example.Pointage_Cleanic.exception.StockConflitException;
import com.example.Pointage_Cleanic.repositories.stockv2.BonSortieRepository;
import com.example.Pointage_Cleanic.repositories.stockv2.ChantierRepository;
import com.example.Pointage_Cleanic.services.stockv2.BonSupportService.DemandeurRef;

import static com.example.Pointage_Cleanic.services.stockv2.BonSupportService.ROLE_CONTROLEUR_STOCK;
import static com.example.Pointage_Cleanic.services.stockv2.BonSupportService.ROLE_SUPERADMIN;
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
import java.util.List;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class BonSortieService {

    private final BonSortieRepository repository;
    private final BonSortieMapper mapper;
    private final BonSupportService support;
    private final ReferentielSiteService referentielSite;
    private final ReferentielEmployeStockService referentielEmploye;
    private final MouvementBonGenerator mouvementGenerator;
    private final StockNotificationService notificationService;
    private final BonMailNotificationService mailNotificationService;
    private final CompteurStockService compteurService;
    private final ChantierRepository chantierRepository;
    private final MongoTemplate mongoTemplate;

    public PageResponse<BonSortieDto> list(int page, int size, String q, StatutBon statut, TypeSortie type,
                                           String siteId, LocalDate dateDebut, LocalDate dateFin) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("date").descending());
        Query query = new Query().with(pageable);
        if (q != null && !q.isBlank()) {
            String regex = ".*" + Pattern.quote(q) + ".*";
            query.addCriteria(new Criteria().orOperator(
                    Criteria.where("reference").regex(regex, "i"),
                    Criteria.where("motif").regex(regex, "i"),
                    Criteria.where("destinataire.clientNom").regex(regex, "i")
            ));
        }
        if (statut != null) {
            query.addCriteria(Criteria.where("statut").is(statut));
        }
        if (type != null) {
            query.addCriteria(Criteria.where("type").is(type));
        }
        if (siteId != null && !siteId.isBlank()) {
            query.addCriteria(Criteria.where("siteSourceId").is(siteId));
        }
        appliquerDates(query, dateDebut, dateFin);

        List<BonSortie> results = mongoTemplate.find(query, BonSortie.class);
        long total = mongoTemplate.count(Query.of(query).limit(-1).skip(-1), BonSortie.class);
        return new PageResponse<>(results.stream().map(mapper::toDto).toList(), total);
    }

    public BonSortieDto getById(String id) {
        return mapper.toDto(loadOrThrow(id));
    }

    public BonSortieDto creer(BonSortiePayload payload) {
        if (payload.getType() == null) {
            throw new IllegalArgumentException("Le type de sortie est obligatoire");
        }
        String siteNom = referentielSite.valideEtCharge(payload.getSiteSourceId()).getNom();
        DestinataireBon destinataire = resoudreDestinataire(payload.getDestinataire());
        List<LigneBon> lignes = support.denormaliserLignes(payload.getLignes());
        DemandeurRef demandeur = support.resoudreDemandeur(payload.getDemandeurId());
        SpecificiteSortie spec = resoudreSpecificite(payload);

        LocalDateTime now = LocalDateTime.now();
        BonSortie bon = BonSortie.builder()
                .reference(compteurService.genererReference("BS"))
                .type(payload.getType())
                .date(payload.getDate() != null ? payload.getDate() : LocalDate.now())
                .siteSourceId(payload.getSiteSourceId())
                .siteSourceNom(siteNom)
                .destinataire(destinataire)
                .motif(payload.getMotif())
                .natureDon(spec.natureDon())
                .beneficiaireDon(spec.beneficiaireDon())
                .chantierId(spec.chantierId())
                .chantierReference(spec.chantierReference())
                .lignes(lignes)
                .statut(StatutBon.BROUILLON)
                .demandeurId(demandeur.id())
                .demandeurNom(demandeur.nom())
                // Auteur déduit du JWT, jamais accepté du client : c'est lui qui gouverne les
                // habilitations, contrairement au demandeur choisi dans le formulaire.
                .creeParId(support.currentUserId())
                .creeParEmail(support.currentUserEmail())
                .creeParNom(support.currentUserNom())
                .commentaire(payload.getCommentaire())
                .montantTotal(support.montantTotal(lignes))
                .createdAt(now)
                .updatedAt(now)
                .build();
        bon.getHistorique().add(support.historique(ActionWorkflow.CREATION, null));
        BonSortie saved = repository.save(bon);
        mailNotificationService.notifierCreationSortie(saved);
        return mapper.toDto(saved);
    }

    public BonSortieDto modifier(String id, BonSortiePayload payload) {
        BonSortie bon = loadOrThrow(id);
        exigerBrouillon(bon, "modifié");
        exigerCreateurOuControleur(bon);
        if (payload.getType() == null) {
            throw new IllegalArgumentException("Le type de sortie est obligatoire");
        }
        String siteNom = referentielSite.valideEtCharge(payload.getSiteSourceId()).getNom();
        DestinataireBon destinataire = resoudreDestinataire(payload.getDestinataire());
        List<LigneBon> lignes = support.denormaliserLignes(payload.getLignes());
        DemandeurRef demandeur = support.resoudreDemandeur(payload.getDemandeurId());
        SpecificiteSortie spec = resoudreSpecificite(payload);

        bon.setType(payload.getType());
        bon.setDate(payload.getDate() != null ? payload.getDate() : bon.getDate());
        bon.setSiteSourceId(payload.getSiteSourceId());
        bon.setSiteSourceNom(siteNom);
        bon.setDestinataire(destinataire);
        bon.setMotif(payload.getMotif());
        bon.setNatureDon(spec.natureDon());
        bon.setBeneficiaireDon(spec.beneficiaireDon());
        bon.setChantierId(spec.chantierId());
        bon.setChantierReference(spec.chantierReference());
        bon.setLignes(lignes);
        bon.setDemandeurId(demandeur.id());
        bon.setDemandeurNom(demandeur.nom());
        bon.setCommentaire(payload.getCommentaire());
        bon.setMontantTotal(support.montantTotal(lignes));
        bon.setUpdatedAt(LocalDateTime.now());
        bon.getHistorique().add(support.historique(ActionWorkflow.MODIFICATION, null));
        return mapper.toDto(repository.save(bon));
    }

    public void supprimer(String id) {
        BonSortie bon = loadOrThrow(id);
        exigerBrouillon(bon, "supprimé");
        exigerCreateurOuControleur(bon);
        repository.deleteById(id);
    }

    public BonSortieDto soumettre(String id) {
        BonSortie bon = loadOrThrow(id);
        if (bon.getStatut() != StatutBon.BROUILLON) {
            throw new StockConflitException("Seul un bon en BROUILLON peut être soumis (statut actuel : " + bon.getStatut() + ")");
        }
        // Le créateur soumet ses propres bons : sans cela, il corrigerait un bon repris après refus
        // sans pouvoir le renvoyer dans le circuit.
        exigerCreateurOuControleur(bon);
        bon.setStatut(StatutBon.SOUMIS);
        bon.setUpdatedAt(LocalDateTime.now());
        bon.getHistorique().add(support.historique(ActionWorkflow.SOUMISSION, null));
        BonSortie saved = repository.save(bon);
        notificationService.diffuserEtCiblerResponsableAchats(
                notification("BON_SOUMIS", saved, "Bon de sortie soumis", "Le bon " + saved.getReference() + " attend validation."));
        return mapper.toDto(saved);
    }

    public BonSortieDto valider(String id, DecisionPayload decision) {
        BonSortie bon = loadOrThrow(id);
        if (bon.getStatut() != StatutBon.SOUMIS) {
            throw new StockConflitException("Seul un bon SOUMIS peut être validé (statut actuel : " + bon.getStatut() + ")");
        }
        // Décision qui engage le stock : super-administrateur seul.
        exigerSuperAdmin("Validation");
        String commentaire = decision == null ? null : decision.getCommentaire();

        // 7.5 : un bon DISTRIBUTION_CHANTIER ne peut être validé vers un chantier clôturé.
        if (bon.getType() == TypeSortie.DISTRIBUTION_CHANTIER && bon.getChantierId() != null) {
            Chantier chantier = chantierRepository.findById(bon.getChantierId())
                    .orElseThrow(() -> new ResourceNotFoundException("Chantier introuvable : " + bon.getChantierId()));
            if (chantier.getStatut() == StatutChantier.CLOTURE) {
                throw new StockConflitException(
                        "Impossible de valider : le chantier " + chantier.getReference() + " est clôturé");
            }
        }

        // Génère les mouvements SORTIE AVANT mutation : lève 422 (stock insuffisant) sans rien valider.
        mouvementGenerator.genererPourSortie(bon);

        bon.setValidateurId(support.currentUserId());
        bon.setValidateurNom(support.currentUserNom());
        bon.setStatut(StatutBon.EFFECTIF);
        bon.setUpdatedAt(LocalDateTime.now());
        bon.getHistorique().add(support.historique(ActionWorkflow.VALIDATION, commentaire));
        bon.getHistorique().add(support.historique(ActionWorkflow.EFFECTIF, null));
        BonSortie saved = repository.save(bon);

        notificationService.diffuser(notification("BON_VALIDE", saved, "Bon de sortie validé",
                "Le bon " + saved.getReference() + " a été validé."));
        notificationService.diffuser(notification("BON_EFFECTIF", saved, "Stock mis à jour",
                "Le bon " + saved.getReference() + " est effectif : stock mouvementé."));
        return mapper.toDto(saved);
    }

    public BonSortieDto refuser(String id, DecisionPayload decision) {
        BonSortie bon = loadOrThrow(id);
        if (bon.getStatut() != StatutBon.SOUMIS) {
            throw new StockConflitException("Seul un bon SOUMIS peut être refusé (statut actuel : " + bon.getStatut() + ")");
        }
        exigerSuperAdmin("Refus");
        if (decision == null || decision.getCommentaire() == null || decision.getCommentaire().isBlank()) {
            throw new IllegalArgumentException("Le commentaire de refus est obligatoire");
        }
        String motif = decision.getCommentaire().trim();
        bon.setStatut(StatutBon.REFUSE);
        bon.setMotifRefus(motif);
        bon.setValidateurId(support.currentUserId());
        bon.setValidateurNom(support.currentUserNom());
        bon.setUpdatedAt(LocalDateTime.now());
        bon.getHistorique().add(support.historique(ActionWorkflow.REFUS, motif));
        BonSortie saved = repository.save(bon);
        notificationService.diffuser(notification("BON_REFUSE", saved, "Bon de sortie refusé",
                "Le bon " + saved.getReference() + " a été refusé : " + motif));
        return mapper.toDto(saved);
    }

    /**
     * Reprise d'un bon refusé : il repasse en BROUILLON pour que son créateur le corrige, puis le
     * renvoie dans le circuit. {@code REFUSE} cesse ainsi d'être un cul-de-sac.
     *
     * <p>Action explicite plutôt qu'un bon refusé rendu modifiable en place : sans elle, la colonne
     * <i>Refusé</i> du Kanban mélangerait refus définitifs et corrections en cours.
     *
     * <p>⚠ L'historique est <b>augmenté</b> d'une entrée {@code REPRISE}, jamais réinitialisé, et
     * {@code motifRefus} est <b>conservé</b> — écrasé seulement au refus suivant : c'est au moment de
     * corriger que le créateur en a le plus besoin. Le front l'affiche alors en bandeau orange.
     */
    public BonSortieDto reprendre(String id) {
        BonSortie bon = loadOrThrow(id);
        if (bon.getStatut() != StatutBon.REFUSE) {
            throw new StockConflitException(
                    "Seul un bon REFUSE peut être repris (statut actuel : " + bon.getStatut() + ")");
        }
        exigerCreateurOuControleur(bon);

        bon.setStatut(StatutBon.BROUILLON);
        bon.setUpdatedAt(LocalDateTime.now());
        bon.getHistorique().add(support.historique(ActionWorkflow.REPRISE, null));
        BonSortie saved = repository.save(bon);
        notificationService.diffuser(notification("BON_REPRIS", saved, "Bon de sortie repris",
                "Le bon " + saved.getReference() + " est repassé en brouillon pour correction."));
        return mapper.toDto(saved);
    }

    /**
     * Le créateur du bon, le contrôleur de stock et le super-admin, personne d'autre.
     *
     * <p>⚠ Repli transitoire : sur les bons créés <b>avant</b> l'ajout de {@code creeParEmail}, le
     * champ est nul et la propriété est considérée comme acquise — sinon d'anciens bons refusés
     * seraient définitivement bloqués. À retirer quand le parc sera renouvelé.
     */
    /**
     * Décisions du circuit (valider / refuser) : super-administrateur seul.
     *
     * <p>Aucun repli ici, contrairement à la propriété : le rôle est porté par le JWT, il est
     * toujours connu.
     */
    private void exigerSuperAdmin(String action) {
        if (!ROLE_SUPERADMIN.equals(support.currentRole())) {
            throw new StockAccesRefuseException(action + " réservé au super-administrateur");
        }
    }

    private void exigerCreateurOuControleur(BonSortie bon) {
        String role = support.currentRole();
        if (ROLE_SUPERADMIN.equals(role) || ROLE_CONTROLEUR_STOCK.equals(role)) {
            return;
        }
        String auteur = bon.getCreeParEmail();
        if (auteur == null || auteur.isBlank()) {
            return;
        }
        String courant = support.currentUserEmail();
        if (courant == null || !auteur.trim().equalsIgnoreCase(courant.trim())) {
            throw new StockAccesRefuseException(
                    "Reprise réservée au créateur du bon, au contrôleur de stock et au super-administrateur");
        }
    }

    /** Valide et résout les champs propres au type de sortie (don / chantier). */
    private SpecificiteSortie resoudreSpecificite(BonSortiePayload payload) {
        TypeSortie type = payload.getType();
        if (type == TypeSortie.DON) {
            if (payload.getChantierId() != null && !payload.getChantierId().isBlank()) {
                throw new IllegalArgumentException("Un bon DON ne peut pas être rattaché à un chantier");
            }
            if (payload.getNatureDon() == null) {
                throw new IllegalArgumentException("Bon DON : la nature du don est obligatoire");
            }
            if (payload.getBeneficiaireDon() == null || payload.getBeneficiaireDon().isBlank()) {
                throw new IllegalArgumentException("Bon DON : le bénéficiaire du don est obligatoire");
            }
            return new SpecificiteSortie(payload.getNatureDon(), payload.getBeneficiaireDon().trim(), null, null);
        }
        if (type == TypeSortie.DISTRIBUTION_CHANTIER) {
            if (payload.getNatureDon() != null) {
                throw new IllegalArgumentException("Un bon DISTRIBUTION_CHANTIER ne peut pas porter de nature de don");
            }
            if (payload.getChantierId() == null || payload.getChantierId().isBlank()) {
                throw new IllegalArgumentException("Bon DISTRIBUTION_CHANTIER : le chantier est obligatoire");
            }
            Chantier chantier = chantierRepository.findById(payload.getChantierId())
                    .orElseThrow(() -> new ResourceNotFoundException("Chantier introuvable : " + payload.getChantierId()));
            return new SpecificiteSortie(null, null, chantier.getId(), chantier.getReference());
        }
        return new SpecificiteSortie(null, null, null, null);
    }

    private record SpecificiteSortie(com.example.Pointage_Cleanic.Enum.stockv2.NatureDon natureDon,
                                     String beneficiaireDon, String chantierId, String chantierReference) {
    }

    private DestinataireBon resoudreDestinataire(DestinatairePayload payload) {
        if (payload == null || payload.getType() == null) {
            throw new IllegalArgumentException("Le destinataire (type) est obligatoire");
        }
        DestinataireBon.DestinataireBonBuilder builder = DestinataireBon.builder().type(payload.getType());
        switch (payload.getType()) {
            case SITE -> {
                if (payload.getSiteId() == null || payload.getSiteId().isBlank()) {
                    throw new IllegalArgumentException("Destinataire SITE : siteId obligatoire");
                }
                builder.siteId(payload.getSiteId())
                        .siteNom(referentielSite.valideEtCharge(payload.getSiteId()).getNom());
            }
            case AGENT -> {
                if (payload.getAgentId() == null || payload.getAgentId().isBlank()) {
                    throw new IllegalArgumentException("Destinataire AGENT : agentId obligatoire");
                }
                builder.agentId(payload.getAgentId())
                        .agentNom(ReferentielEmployeStockService.nomComplet(
                                referentielEmploye.valideEtCharge(payload.getAgentId())));
            }
            case CLIENT -> {
                if (payload.getClientNom() == null || payload.getClientNom().isBlank()) {
                    throw new IllegalArgumentException("Destinataire CLIENT : clientNom obligatoire");
                }
                builder.clientNom(payload.getClientNom().trim());
            }
        }
        return builder.build();
    }

    private BonSortie loadOrThrow(String id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Bon de sortie introuvable : " + id));
    }

    private void exigerBrouillon(BonSortie bon, String action) {
        if (bon.getStatut() != StatutBon.BROUILLON) {
            throw new StockConflitException("Un bon ne peut être " + action + " qu'en statut BROUILLON (statut actuel : " + bon.getStatut() + ")");
        }
    }

    private void appliquerDates(Query query, LocalDate dateDebut, LocalDate dateFin) {
        if (dateDebut != null && dateFin != null) {
            query.addCriteria(Criteria.where("date").gte(dateDebut).lte(dateFin));
        } else if (dateDebut != null) {
            query.addCriteria(Criteria.where("date").gte(dateDebut));
        } else if (dateFin != null) {
            query.addCriteria(Criteria.where("date").lte(dateFin));
        }
    }

    private NotificationStockDto notification(String type, BonSortie bon, String titre, String message) {
        return NotificationStockDto.builder()
                .type(type)
                .sens("SORTIE")
                .bonId(bon.getId())
                .reference(bon.getReference())
                .titre(titre)
                .message(message)
                .dateEmission(LocalDateTime.now().toString())
                .build();
    }
}
