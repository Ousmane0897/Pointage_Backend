package com.example.Pointage_Cleanic.migrations.index;

import com.example.Pointage_Cleanic.entities.EmployeComplet;
import com.example.Pointage_Cleanic.entities.evenements.EvenementConsomme;
import com.example.Pointage_Cleanic.entities.evenements.EvenementDomaine;
import com.example.Pointage_Cleanic.entities.Pointage;
import com.example.Pointage_Cleanic.entities.productionchimie.ControleQualite;
import com.example.Pointage_Cleanic.entities.productionchimie.FicheFormulation;
import com.example.Pointage_Cleanic.entities.productionchimie.FormatConditionnement;
import com.example.Pointage_Cleanic.entities.productionchimie.GrilleControle;
import com.example.Pointage_Cleanic.entities.productionchimie.Lot;
import com.example.Pointage_Cleanic.entities.productionchimie.MatierePremiere;
import com.example.Pointage_Cleanic.entities.productionchimie.MouvementStockChimie;
import com.example.Pointage_Cleanic.entities.productionchimie.OrdreFabrication;
import com.example.Pointage_Cleanic.entities.rh.BesoinFormation;
import com.example.Pointage_Cleanic.entities.rh.BulletinPaie;
import com.example.Pointage_Cleanic.entities.rh.CategorieProfessionnelle;
import com.example.Pointage_Cleanic.entities.rh.Contrat;
import com.example.Pointage_Cleanic.entities.rh.DemandeConge;
import com.example.Pointage_Cleanic.entities.rh.DocumentEmploye;
import com.example.Pointage_Cleanic.entities.rh.DossierEmploye;
import com.example.Pointage_Cleanic.entities.rh.EvaluationFormation;
import com.example.Pointage_Cleanic.entities.rh.EvaluationPeriodique;
import com.example.Pointage_Cleanic.entities.rh.HeureSupplementaire;
import com.example.Pointage_Cleanic.entities.rh.JourFerie;
import com.example.Pointage_Cleanic.entities.rh.ParticipationFormation;
import com.example.Pointage_Cleanic.entities.rh.RhAbsence;
import com.example.Pointage_Cleanic.entities.rh.Sanction;
import com.example.Pointage_Cleanic.entities.rh.SessionFormation;
import com.example.Pointage_Cleanic.entities.stock.Produit;
import com.example.Pointage_Cleanic.entities.stockv2.BonEntree;
import com.example.Pointage_Cleanic.entities.stockv2.BonSortie;
import com.example.Pointage_Cleanic.entities.stockv2.CategorieStock;
import com.example.Pointage_Cleanic.entities.stockv2.Chantier;
import com.example.Pointage_Cleanic.entities.stockv2.HistoriquePointCout;
import com.example.Pointage_Cleanic.entities.stockv2.Inventaire;
import com.example.Pointage_Cleanic.entities.stockv2.MouvementStock;
import com.example.Pointage_Cleanic.entities.stockv2.Plafond;
import com.example.Pointage_Cleanic.entities.stockv2.ProduitStock;
import com.example.Pointage_Cleanic.entities.stockv2.StockParSite;
import com.example.Pointage_Cleanic.entities.stockv2.SuppressionStockLog;
import com.example.Pointage_Cleanic.entities.terrain.AffectationAgent;
import com.example.Pointage_Cleanic.entities.terrain.Materiel;
import com.example.Pointage_Cleanic.entities.terrain.ProduitPhytosanitaire;
import com.example.Pointage_Cleanic.entities.terrain.SiteClient;
import org.bson.Document;

import java.beans.Introspector;
import java.util.ArrayList;
import java.util.List;

/**
 * LA liste des index attendus en base (lot 1 / 1a, décision D3). Seule source : les annotations
 * {@code @Indexed} / {@code @CompoundIndex} ont été retirées des entités (elles n'étaient jamais
 * appliquées, {@code auto-index-creation} étant désactivé) et les classes {@code *IndexesConfig}
 * supprimées. Les index sont créés par les ChangeUnits {@code 0101} à {@code 0105} et
 * {@code IndexesAttendusIT} vérifie que la base contient exactement cette liste.
 *
 * <p>Ajouter un index : une ligne ici, et une <b>nouvelle</b> ChangeUnit (une ChangeUnit
 * appliquée ne repasse pas). Avant un index unique, {@link CreateurIndex} cherche les doublons et
 * échoue s'il en trouve, sans rien dédoublonner.
 *
 * <p>Choix notables (détail : docs/refonte/1a-socle-technique.md §4) :
 * <ul>
 *   <li>Production Chimie et {@code terrain_affectations} : repris <b>à l'identique</b> des
 *       anciennes {@code *IndexesConfig} (noms, clés, sans filtre), déjà présents en production ;</li>
 *   <li>uniques sur un champ facultatif : filtre partiel {@code $type: "string"} au lieu de
 *       {@code sparse} (un champ présent mais {@code null} est indexé par un index sparse) ;</li>
 *   <li>{@code employes_complet.nomComplet} unique : <b>non créé</b> (homonymes légitimes ;
 *       collection legacy à archiver en 1d).</li>
 * </ul>
 */
public final class IndexesAttendus {

    public static final String SOCLE = "socle";
    public static final String RH = "rh";
    public static final String STOCK = "stock";
    public static final String TERRAIN = "terrain";
    public static final String PRODUCTION_CHIMIE = "production-chimie";
    public static final String OUTBOX = "outbox";

    private static final List<IndexAttendu> TOUS = construire();

    private IndexesAttendus() {
    }

    public static List<IndexAttendu> tous() {
        return TOUS;
    }

    public static List<IndexAttendu> duModule(String module) {
        return TOUS.stream().filter(i -> i.module().equals(module)).toList();
    }

    private static List<IndexAttendu> construire() {
        List<IndexAttendu> l = new ArrayList<>();

        // ---- Socle : entités partagées et legacy ------------------------------------------
        l.add(uniqueTexte(SOCLE, EmployeComplet.class, "agentId_unique", "agentId"));
        l.add(uniqueTexte(SOCLE, EmployeComplet.class, "matricule_unique", "matricule"));
        l.add(simple(SOCLE, Pointage.class, "date_timestamp_idx", cles("date", 1, "timestamp", -1)));
        l.add(simple(SOCLE, Pointage.class, "search_idx", cles("nom", 1, "prenom", 1, "codeSecret", 1)));
        l.add(simple(SOCLE, Pointage.class, "pointage_flux_idx", cles("codeSecret", 1, "date", 1, "heureDepart", 1)));
        l.add(simple(SOCLE, Pointage.class, "site", cles("site", 1)));
        l.add(simple(SOCLE, Pointage.class, "deviceId", cles("deviceId", 1)));
        l.add(simple(SOCLE, Produit.class, "search_idx",
                cles("nomProduit", "text", "codeProduit", "text", "categorie", 1, "destination", 1)));

        // ---- RH ------------------------------------------------------------------------------
        l.add(simple(RH, BesoinFormation.class, "employeId", cles("employeId", 1)));
        l.add(simple(RH, BulletinPaie.class, "employe_periode_idx",
                cles("employeId", 1, "periode.annee", 1, "periode.mois", 1)));
        l.add(simple(RH, BulletinPaie.class, "employeId", cles("employeId", 1)));
        l.add(uniqueTexte(RH, CategorieProfessionnelle.class, "code_unique", "code"));
        l.add(simple(RH, Contrat.class, "employeId", cles("employeId", 1)));
        l.add(simple(RH, DemandeConge.class, "employeId", cles("employeId", 1)));
        l.add(simple(RH, DemandeConge.class, "superieurHierarchiqueId", cles("superieurHierarchiqueId", 1)));
        l.add(simple(RH, DocumentEmploye.class, "employeId", cles("employeId", 1)));
        l.add(uniqueTexte(RH, DossierEmploye.class, "matricule_unique", "matricule"));
        l.add(uniqueTexte(RH, DossierEmploye.class, "agentId_unique", "agentId"));
        l.add(simple(RH, DossierEmploye.class, "email", cles("email", 1)));
        l.add(simple(RH, EvaluationFormation.class, "participationId", cles("participationId", 1)));
        l.add(simple(RH, EvaluationFormation.class, "sessionId", cles("sessionId", 1)));
        l.add(simple(RH, EvaluationPeriodique.class, "employe_periode_idx", cles("employeId", 1, "periode", 1)));
        l.add(simple(RH, EvaluationPeriodique.class, "employeId", cles("employeId", 1)));
        l.add(simple(RH, HeureSupplementaire.class, "employeId", cles("employeId", 1)));
        l.add(unique(RH, JourFerie.class, "date_unique", cles("date", 1), null));
        l.add(unique(RH, ParticipationFormation.class, "session_employe_idx", cles("sessionId", 1, "employeId", 1), null));
        l.add(simple(RH, ParticipationFormation.class, "sessionId", cles("sessionId", 1)));
        l.add(simple(RH, ParticipationFormation.class, "employeId", cles("employeId", 1)));
        l.add(simple(RH, RhAbsence.class, "employeId", cles("employeId", 1)));
        l.add(simple(RH, Sanction.class, "employe_type_dateSanction_idx",
                cles("employeId", 1, "type", 1, "dateSanction", -1)));
        l.add(simple(RH, Sanction.class, "employeId", cles("employeId", 1)));
        l.add(simple(RH, SessionFormation.class, "formationId", cles("formationId", 1)));

        // ---- Stock v2 ------------------------------------------------------------------------
        l.add(uniqueTexte(STOCK, BonEntree.class, "reference_unique", "reference"));
        l.add(uniqueTexte(STOCK, BonSortie.class, "reference_unique", "reference"));
        l.add(simple(STOCK, CategorieStock.class, "parentId", cles("parentId", 1)));
        l.add(uniqueTexte(STOCK, Chantier.class, "reference_unique", "reference"));
        l.add(simple(STOCK, HistoriquePointCout.class, "produitId", cles("produitId", 1)));
        l.add(uniqueTexte(STOCK, Inventaire.class, "reference_unique", "reference"));
        l.add(simple(STOCK, Inventaire.class, "siteId", cles("siteId", 1)));
        l.add(simple(STOCK, Inventaire.class, "statut", cles("statut", 1)));
        l.add(uniqueTexte(STOCK, MouvementStock.class, "reference_unique", "reference"));
        l.add(simple(STOCK, MouvementStock.class, "produitId", cles("produitId", 1)));
        l.add(simple(STOCK, MouvementStock.class, "siteSourceId", cles("siteSourceId", 1)));
        l.add(simple(STOCK, MouvementStock.class, "siteDestinationId", cles("siteDestinationId", 1)));
        l.add(simple(STOCK, MouvementStock.class, "date", cles("date", 1)));
        l.add(simple(STOCK, MouvementStock.class, "bonId", cles("bonId", 1)));
        l.add(simple(STOCK, MouvementStock.class, "inventaireId", cles("inventaireId", 1)));
        l.add(simple(STOCK, MouvementStock.class, "chantierId", cles("chantierId", 1)));
        l.add(simple(STOCK, Plafond.class, "siteId", cles("siteId", 1)));
        l.add(uniqueTexte(STOCK, ProduitStock.class, "code_unique", "code"));
        l.add(simple(STOCK, ProduitStock.class, "categorieId", cles("categorieId", 1)));
        l.add(simple(STOCK, ProduitStock.class, "actif", cles("actif", 1)));
        // Pas de filtre : (produit, entrepôt = null) doit lui aussi être unique.
        l.add(unique(STOCK, StockParSite.class, "produit_site_unique", cles("produitId", 1, "siteId", 1), null));
        l.add(simple(STOCK, SuppressionStockLog.class, "documentId", cles("documentId", 1)));

        // ---- Terrain -------------------------------------------------------------------------
        // Repris à l'identique de l'ancienne TerrainIndexesConfig (déjà en production).
        l.add(simple(TERRAIN, AffectationAgent.class, "statut_debut_fin", cles("statut", 1, "dateDebut", 1, "dateFin", 1)));
        l.add(simple(TERRAIN, AffectationAgent.class, "employe_id", cles("employeId", 1)));
        l.add(simple(TERRAIN, AffectationAgent.class, "site_statut", cles("siteId", 1, "statut", 1)));
        l.add(uniqueTexte(TERRAIN, Materiel.class, "code_unique", "code"));
        l.add(uniqueTexte(TERRAIN, ProduitPhytosanitaire.class, "numeroHomologation_unique", "numeroHomologation"));
        l.add(uniqueTexte(TERRAIN, SiteClient.class, "code_unique", "code"));

        // ---- Production Chimie : à l'identique de l'ancienne ProductionChimieIndexesConfig ----
        l.add(unique(PRODUCTION_CHIMIE, MatierePremiere.class, "code_unique", cles("code", 1), null));
        l.add(simple(PRODUCTION_CHIMIE, MatierePremiere.class, "actif", cles("actif", 1)));
        l.add(simple(PRODUCTION_CHIMIE, MouvementStockChimie.class, "matiere_date", cles("matierePremiereId", 1, "date", -1)));
        l.add(simple(PRODUCTION_CHIMIE, MouvementStockChimie.class, "of_id", cles("ordreFabricationId", 1)));
        l.add(simple(PRODUCTION_CHIMIE, MouvementStockChimie.class, "type_date", cles("type", 1, "date", -1)));
        l.add(unique(PRODUCTION_CHIMIE, FicheFormulation.class, "code_unique", cles("code", 1), null));
        l.add(simple(PRODUCTION_CHIMIE, FicheFormulation.class, "statut", cles("statut", 1)));
        l.add(unique(PRODUCTION_CHIMIE, OrdreFabrication.class, "numero_unique", cles("numero", 1), null));
        l.add(simple(PRODUCTION_CHIMIE, OrdreFabrication.class, "statut", cles("statut", 1)));
        l.add(simple(PRODUCTION_CHIMIE, OrdreFabrication.class, "date_lancement", cles("dateLancementPrevue", -1)));
        l.add(simple(PRODUCTION_CHIMIE, OrdreFabrication.class, "operateur", cles("operateurResponsableId", 1)));
        l.add(unique(PRODUCTION_CHIMIE, Lot.class, "numero_unique", cles("numero", 1), null));
        l.add(simple(PRODUCTION_CHIMIE, Lot.class, "produit_date", cles("produitNom", 1, "dateFabrication", -1)));
        l.add(simple(PRODUCTION_CHIMIE, Lot.class, "statut_controle", cles("statutControle", 1)));
        l.add(simple(PRODUCTION_CHIMIE, Lot.class, "statut_stock", cles("statutStock", 1)));
        l.add(simple(PRODUCTION_CHIMIE, Lot.class, "formulation_id", cles("formulationId", 1)));
        l.add(simple(PRODUCTION_CHIMIE, GrilleControle.class, "produit_nom", cles("produitNom", 1)));
        l.add(simple(PRODUCTION_CHIMIE, GrilleControle.class, "formulation_id", cles("formulationId", 1)));
        l.add(simple(PRODUCTION_CHIMIE, ControleQualite.class, "lot_id", cles("lotId", 1)));
        l.add(simple(PRODUCTION_CHIMIE, ControleQualite.class, "produit_date", cles("produitNom", 1, "dateControle", -1)));
        l.add(simple(PRODUCTION_CHIMIE, ControleQualite.class, "decision", cles("decision", 1)));
        l.add(unique(PRODUCTION_CHIMIE, FormatConditionnement.class, "code_unique", cles("code", 1), null));
        l.add(simple(PRODUCTION_CHIMIE, FormatConditionnement.class, "actif", cles("actif", 1)));

        // ---- Outbox des événements de domaine (lot 1 / 1b, D5) : ChangeUnit 0107 --------------
        // Réclamation par le dispatcher : (statut, prochainEssai) et bail expiré (statut, bailJusquA).
        l.add(simple(OUTBOX, EvenementDomaine.class, "statut_prochainEssai", cles("statut", 1, "prochainEssai", 1)));
        l.add(simple(OUTBOX, EvenementDomaine.class, "statut_bail", cles("statut", 1, "bailJusquA", 1)));
        l.add(simple(OUTBOX, EvenementDomaine.class, "agregat", cles("agregatType", 1, "agregatId", 1)));
        l.add(unique(OUTBOX, EvenementConsomme.class, "consommateur_evenement_unique",
                cles("consommateur", 1, "evenementId", 1), null));

        return List.copyOf(l);
    }

    private static IndexAttendu simple(String module, Class<?> entite, String nom, Document cles) {
        return new IndexAttendu(module, collection(entite), nom, cles, false, null);
    }

    private static IndexAttendu unique(String module, Class<?> entite, String nom, Document cles, Document filtre) {
        return new IndexAttendu(module, collection(entite), nom, cles, true, filtre);
    }

    /** Unique sur un champ texte facultatif : seuls les documents où il est une chaîne comptent. */
    private static IndexAttendu uniqueTexte(String module, Class<?> entite, String nom, String champ) {
        return unique(module, entite, nom, cles(champ, 1),
                new Document(champ, new Document("$type", "string")));
    }

    private static Document cles(Object... paires) {
        Document d = new Document();
        for (int i = 0; i < paires.length; i += 2) {
            d.append((String) paires[i], paires[i + 1]);
        }
        return d;
    }

    /** Nom de collection tel que Spring Data le calcule (annotation @Document, sinon nom de classe). */
    static String collection(Class<?> entite) {
        org.springframework.data.mongodb.core.mapping.Document doc =
                entite.getAnnotation(org.springframework.data.mongodb.core.mapping.Document.class);
        if (doc != null && !doc.collection().isBlank()) {
            return doc.collection();
        }
        if (doc != null && !doc.value().isBlank()) {
            return doc.value();
        }
        return Introspector.decapitalize(entite.getSimpleName());
    }
}
