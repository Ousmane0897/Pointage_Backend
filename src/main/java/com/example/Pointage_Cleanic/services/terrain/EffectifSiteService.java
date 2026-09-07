package com.example.Pointage_Cleanic.services.terrain;

import com.example.Pointage_Cleanic.Dto.terrain.EffectifSiteDto;
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
import java.util.List;
import java.util.regex.Pattern;

/**
 * Calcule l'effectif actuel d'un site client selon deux périmètres distincts
 * partageant le même plafond {@code nombreMaxEmployes} :
 * <ul>
 *   <li>RH : employés (dossiers) rattachés au site par NOM ;</li>
 *   <li>TERRAIN : affectations de planning terrain sur le {@code siteId} non annulées.</li>
 * </ul>
 * Voir {@link PerimetreEffectif}. Aucun blocage métier : le comptage est consultatif,
 * le blocage est réalisé côté frontend.
 */
@Service
@RequiredArgsConstructor
public class EffectifSiteService {

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
     * Compte les dossiers employés dont une affectation vise le nom du site (comparaison
     * exacte, insensible à la casse), avec fallback sur la chaîne {@code siteAffecte}.
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
                .filter(d -> estRattacheAuSite(d, nom))
                .count();
    }

    /**
     * Vrai si le dossier occupe <b>actuellement</b> un poste sur ce site.
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
