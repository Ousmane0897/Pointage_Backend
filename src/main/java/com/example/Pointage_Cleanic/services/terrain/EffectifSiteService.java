package com.example.Pointage_Cleanic.services.terrain;

import com.example.Pointage_Cleanic.Dto.terrain.EffectifSiteDto;
import com.example.Pointage_Cleanic.Enum.rh.StatutDossierEmploye;
import com.example.Pointage_Cleanic.Enum.terrain.PerimetreEffectif;
import com.example.Pointage_Cleanic.Enum.terrain.StatutAffectation;
import com.example.Pointage_Cleanic.entities.rh.AffectationSite;
import com.example.Pointage_Cleanic.entities.rh.DossierEmploye;
import com.example.Pointage_Cleanic.entities.terrain.AffectationAgent;
import com.example.Pointage_Cleanic.entities.terrain.SiteClient;
import com.example.Pointage_Cleanic.exception.ResourceNotFoundException;
import com.example.Pointage_Cleanic.repositories.terrain.AffectationAgentRepository;
import com.example.Pointage_Cleanic.repositories.terrain.SiteClientRepository;
import com.example.Pointage_Cleanic.util.AffectationSiteUtils;
import com.example.Pointage_Cleanic.util.SiteAffecteUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Calcule l'effectif actuel d'un site client selon deux périmètres distincts
 * partageant le même plafond {@code nombreMaxEmployes} :
 * <ul>
 *   <li>RH : employés (dossiers) rattachés au site par NOM <b>et occupant un poste</b> ;</li>
 *   <li>TERRAIN : affectations de planning terrain sur le {@code siteId} non annulées.</li>
 * </ul>
 * Voir {@link PerimetreEffectif}. Aucun blocage métier : le comptage est consultatif,
 * le blocage est réalisé côté frontend.
 */
@Service
@RequiredArgsConstructor
public class EffectifSiteService {

    /**
     * Statuts dont le titulaire occupe effectivement un poste au sens du plafond du site.
     * <p>
     * {@code SORTI} et {@code SUSPENDU} en sont exclus : l'agent a quitté l'entreprise ou
     * en est temporairement écarté (congé long, suspension), et sa place doit pouvoir être
     * confiée à un remplaçant. Jusqu'ici le décompte ignorait complètement le statut, si
     * bien qu'un site restait réputé plein d'agents absents et refusait toute nouvelle
     * affectation — la seule échappatoire étant de clore la ligne d'affectation par une
     * {@code dateSortie}, ce qui aurait faussement acté un départ définitif du site dans
     * son historique.
     * <p>
     * Même liste que {@code OrganigrammeService.STATUTS_ACTIFS}, et volontairement plus
     * étroite que celle de {@code DemandeCongeService} (qui garde {@code SUSPENDU} : un
     * agent suspendu conserve ses droits à congés, mais pas son poste).
     */
    private static final Set<StatutDossierEmploye> STATUTS_OCCUPANTS =
            EnumSet.of(StatutDossierEmploye.ACTIF, StatutDossierEmploye.EN_PERIODE_ESSAI);

    private final SiteClientRepository siteClientRepository;
    private final AffectationAgentRepository affectationAgentRepository;
    private final MongoTemplate mongoTemplate;
    /** Horloge d'{@code Africa/Dakar} : sert à écarter les affectations closes. */
    private final Clock clock;

    public EffectifSiteDto calculer(String siteId, PerimetreEffectif perimetre,
                                    String excludeEmployeId, String excludeAffectationId) {
        SiteClient site = siteClientRepository.findById(siteId)
                .orElseThrow(() -> new ResourceNotFoundException("Site introuvable : " + siteId));

        long nombreActuel = switch (perimetre) {
            case RH -> compterEffectifRh(site.getNom(), excludeEmployeId);
            case TERRAIN -> compterEffectifTerrain(siteId, excludeAffectationId);
        };

        return new EffectifSiteDto(nombreActuel, site.getNombreMaxEmployes());
    }

    /**
     * Compte les dossiers employés qui <b>occupent un poste</b> sur ce site : statut
     * occupant (voir {@link #STATUTS_OCCUPANTS}) <b>et</b> affectation active visant le nom
     * du site (comparaison exacte, insensible à la casse), avec repli sur la chaîne
     * {@code siteAffecte}.
     * <p>
     * Le pré-filtre Mongo réduit le scan ; le filtrage exact en mémoire évite le sur-match
     * du regex de sous-chaîne (ex. « Point » ⊂ « Point E »).
     */
    private long compterEffectifRh(String nom, String excludeEmployeId) {
        if (nom == null || nom.isBlank()) {
            return 0;
        }
        Query query = new Query(new Criteria().orOperator(
                Criteria.where("affectations.site").is(nom),
                Criteria.where("siteAffecte").regex(Pattern.quote(nom), "i")));
        List<DossierEmploye> candidats = mongoTemplate.find(query, DossierEmploye.class);

        return candidats.stream()
                .filter(d -> excludeEmployeId == null || !excludeEmployeId.equals(d.getId()))
                .filter(this::occupeUnPoste)
                .filter(d -> estRattacheAuSite(d, nom))
                .count();
    }

    /**
     * Vrai si le dossier occupe un poste, indépendamment du site.
     * <p>
     * ⚠ Un {@code statut} <b>nul</b> est compté. Le champ n'a jamais été {@code @NotNull}
     * et les dossiers antérieurs peuvent en être dépourvus : libérer une place sur la foi
     * d'une donnée absente ferait dépasser le plafond en silence, alors que le sur-compter
     * ne fait qu'appeler une correction du dossier. Même arbitrage prudent que le
     * {@code type} nul de {@code TypeConge.decompteSoldeAnnuel}.
     */
    private boolean occupeUnPoste(DossierEmploye dossier) {
        StatutDossierEmploye statut = dossier.getStatut();
        return statut == null || STATUTS_OCCUPANTS.contains(statut);
    }

    /**
     * Vrai si le dossier est <b>actuellement rattaché</b> à ce site. Ne dit rien du statut
     * de l'employé : « est-il sur ce site ? » et « occupe-t-il un poste ? » sont deux
     * questions distinctes, la seconde étant tranchée par {@link #occupeUnPoste}.
     * <p>
     * ⚠ Deux règles, dans cet ordre, et l'ordre compte :
     * <ol>
     *   <li>dossier doté d'affectations structurées ⇒ <b>elles seules font foi</b>,
     *       les closes exclues ;</li>
     *   <li>sinon seulement, repli sur {@code siteAffecte} (dossiers antérieurs, sans
     *       aucune date).</li>
     * </ol>
     * Sans la borne de l'étape 1, un agent parti resterait compté — il l'était jusqu'ici,
     * ce qui ne se voyait pas tant que les affectations closes étaient supprimées à la
     * main. Sans la subordination de l'étape 2, il le resterait tout autant : son
     * affectation close ne matchant plus, le code retomberait sur {@code siteAffecte}.
     */
    private boolean estRattacheAuSite(DossierEmploye dossier, String nom) {
        List<AffectationSite> affectations = dossier.getAffectations();
        if (affectations != null && !affectations.isEmpty()) {
            return AffectationSiteUtils.actives(affectations, LocalDate.now(clock)).stream()
                    .anyMatch(a -> memeSite(a.getSite(), nom));
        }
        return SiteAffecteUtils.decouper(dossier.getSiteAffecte()).stream()
                .anyMatch(s -> memeSite(s, nom));
    }

    private boolean memeSite(String candidat, String nom) {
        return candidat != null && candidat.trim().equalsIgnoreCase(nom.trim());
    }

    /**
     * Compte les affectations de planning terrain sur ce {@code siteId} dont le statut
     * n'est pas {@code ANNULEE} (aucun filtre de dates). {@code excludeAffectationId}
     * retire l'affectation en cours d'édition.
     */
    private long compterEffectifTerrain(String siteId, String excludeAffectationId) {
        if (excludeAffectationId == null || excludeAffectationId.isBlank()) {
            return affectationAgentRepository.countBySiteIdAndStatutNot(siteId, StatutAffectation.ANNULEE);
        }
        Query query = new Query(Criteria.where("siteId").is(siteId)
                .and("statut").ne(StatutAffectation.ANNULEE)
                .and("_id").ne(excludeAffectationId));
        return mongoTemplate.count(query, AffectationAgent.class);
    }
}
