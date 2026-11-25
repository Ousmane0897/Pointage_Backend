package com.example.Pointage_Cleanic.migrations;

import com.example.Pointage_Cleanic.PointageCleanicApplication;
import com.example.Pointage_Cleanic.config.MongoTestContainer;
import com.example.Pointage_Cleanic.entities.rh.AffectationSite;
import com.example.Pointage_Cleanic.entities.rh.DossierEmploye;
import com.example.Pointage_Cleanic.entities.stockv2.StockParSite;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;
import org.bson.Document;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Critère 1a « MigrationsIT » (décision D2). Chaque scénario démarre la vraie application sur
 * une base dédiée du conteneur de test :
 * <ul>
 *   <li>base vide : toutes les ChangeUnits s'appliquent, puis un redémarrage ne rejoue pas
 *       les ponctuelles ;</li>
 *   <li>base existante (la production d'aujourd'hui : runners déjà passés, registre Mongock
 *       absent) : les ChangeUnits s'inscrivent sans modifier un seul document — c'est
 *       l'équivalent de la « baseline » de D2 (voir le compte rendu, §1.5) ;</li>
 *   <li>base aux formats anciens : les migrations s'appliquent ;</li>
 *   <li>profil {@code demo} : seules ses ChangeUnits créent les données de démonstration.</li>
 * </ul>
 */
class MigrationsIT {

    private static final List<String> PONCTUELLES = List.of(
            "0001-modules-autorises-nettoyage", "0002-rh-modules-booleen-vers-objet",
            "0003-conges-circuit-trois-niveaux", "0004-dossiers-agentid-renommage",
            "0005-dossiers-date-code-agent", "0006-contrats-alternance-vers-prestation",
            "0009-periode-essai-suppression", "0010-parametres-paie-par-defaut",
            "0011-version-initiale-stock", "0012-pieces-bons-existants",
            "0106-compteurs-pieces");
    private static final List<String> TOUJOURS = List.of(
            "0007-stock-entrepot-unique", "0008-affectations-sites-complement");

    private static MongoClient client;

    @BeforeAll
    static void connecter() {
        client = MongoClients.create(MongoTestContainer.conteneur().getReplicaSetUrl());
    }

    @AfterAll
    static void fermer() {
        client.close();
    }

    private static String nouvelleBase() {
        return "migr_" + UUID.randomUUID().toString().substring(0, 8);
    }

    /** Réglages en arguments de ligne de commande : priorité sur application.yml (voir le spike). */
    private static void demarrer(String base, Consumer<ConfigurableApplicationContext> pendant, String... extra) {
        List<String> args = new ArrayList<>(List.of(
                "--spring.data.mongodb.uri=" + MongoTestContainer.conteneur().getReplicaSetUrl(base),
                "--server.port=0",
                "--spring.mail.host=localhost"));
        args.addAll(List.of(extra));
        try (ConfigurableApplicationContext contexte =
                     new SpringApplicationBuilder(PointageCleanicApplication.class).run(args.toArray(String[]::new))) {
            assertThat(contexte.getBean(MongoTemplate.class).getDb().getName()).isEqualTo(base);
            pendant.accept(contexte);
        }
    }

    private static void demarrer(String base, String... extra) {
        demarrer(base, contexte -> { }, extra);
    }

    private static long executions(MongoDatabase db, String changeId) {
        return db.getCollection("mongockChangeLog")
                .countDocuments(new Document("changeId", changeId).append("state", "EXECUTED"));
    }

    /** Tous les documents de toutes les collections métier, triés : l'état complet de la base. */
    private static Map<String, List<String>> photographier(MongoDatabase db) {
        Map<String, List<String>> photo = new TreeMap<>();
        for (String nom : db.listCollectionNames()) {
            if (nom.startsWith("mongock")) {
                continue;
            }
            List<String> documents = new ArrayList<>();
            db.getCollection(nom).find().forEach(d -> documents.add(d.toJson()));
            documents.sort(String::compareTo);
            photo.put(nom, documents);
        }
        return photo;
    }

    @Test
    void base_vide_toutes_les_change_units_puis_aucun_rejeu() {
        String nom = nouvelleBase();
        MongoDatabase db = client.getDatabase(nom);

        demarrer(nom);
        PONCTUELLES.forEach(id -> assertThat(executions(db, id)).as(id).isEqualTo(1));
        TOUJOURS.forEach(id -> assertThat(executions(db, id)).as(id).isEqualTo(1));
        assertThat(db.getCollection("parametres_paie").countDocuments()).isEqualTo(1);
        assertThat(db.getCollection("sites_clients").countDocuments())
                .as("aucun site fictif hors profil demo").isZero();
        assertThat(db.getCollection("formations").countDocuments()).isZero();

        Map<String, List<String>> avant = photographier(db);
        demarrer(nom);
        PONCTUELLES.forEach(id -> assertThat(executions(db, id)).as(id + " non rejouée").isEqualTo(1));
        assertThat(photographier(db)).isEqualTo(avant);
    }

    /**
     * Les deux ChangeUnits {@code runAlways} gardent le comportement des anciens runners : elles
     * repassent à chaque démarrage. Preuve par l'effet : un solde de site créé entre deux
     * démarrages (comme le fait un inventaire par site) est refusionné dans l'entrepôt.
     */
    @Test
    void les_change_units_run_always_repassent_a_chaque_demarrage() {
        String nom = nouvelleBase();
        MongoDatabase db = client.getDatabase(nom);
        demarrer(nom);

        db.getCollection("stockv2_etats_stock").insertMany(List.of(
                new Document("produitId", "p-2").append("siteId", null).append("quantite", 1.0),
                new Document("produitId", "p-2").append("siteId", "site-B").append("quantite", 2.0)));
        demarrer(nom);

        List<Document> soldes = db.getCollection("stockv2_etats_stock").find().into(new ArrayList<>());
        assertThat(soldes).hasSize(1);
        assertThat(soldes.get(0).getDouble("quantite")).isEqualTo(3.0);
    }

    @Test
    void base_existante_deja_migree_aucune_ecriture() {
        String nom = nouvelleBase();
        MongoDatabase db = client.getDatabase(nom);

        // Premier démarrage, puis jeu représentatif de données déjà au format courant.
        demarrer(nom, contexte -> {
            MongoTemplate mongo = contexte.getBean(MongoTemplate.class);
            mongo.insert(new Document("email", "rh@exemple.invalid")
                    .append("modulesAutorises", new Document("rh", new Document("dossierEmploye", true)
                            .append("conges", false))), "utilisateur");
            mongo.insert(DossierEmploye.builder()
                    .matricule("M-001").nom("Diop").prenom("Awa")
                    .agentId("1234").dateCreationAgentId(LocalDateTime.of(2026, 1, 5, 8, 0))
                    .dateCreationAgentIdEstimee(false)
                    .dateEmbauche(LocalDate.of(2025, 3, 1)).joursTravail("LUN_VEN")
                    .siteAffecte("Plateau")
                    .affectations(List.of(AffectationSite.builder().id("aff-1").site("Plateau")
                            .joursTravail("LUN_VEN").dateEntree(LocalDate.of(2025, 3, 1)).build()))
                    .build());
            mongo.insert(new Document("statut", "EN_ATTENTE_RH")
                    .append("dateDebut", LocalDate.of(2026, 11, 2)).append("dateFin", LocalDate.of(2026, 11, 6))
                    .append("nombreJours", 5), "conges");
            mongo.insert(StockParSite.builder().produitId("p-1").siteId(null).quantite(10.0).build());
            mongo.insert(new Document("typeContrat", "PRESTATION"), "contrats");
        });
        // La production d'aujourd'hui n'a jamais connu Mongock : registre absent.
        db.getCollection("mongockChangeLog").drop();
        Map<String, List<String>> avant = photographier(db);

        demarrer(nom);

        assertThat(photographier(db)).as("aucun document modifié").isEqualTo(avant);
        PONCTUELLES.forEach(id -> assertThat(executions(db, id)).as(id + " inscrite").isEqualTo(1));
    }

    @Test
    void base_aux_formats_anciens_est_migree() {
        String nom = nouvelleBase();
        MongoDatabase db = client.getDatabase(nom);
        db.getCollection("utilisateur").insertOne(new Document("email", "x@exemple.invalid")
                .append("modulesAutorises", new Document("rh", true).append("Stock", true)));
        db.getCollection("dossiers_employes").insertOne(new Document("matricule", "M-9").append("AgentId", "5678"));
        db.getCollection("contrats").insertOne(new Document("typeContrat", "ALTERNANCE"));
        db.getCollection("periodes_essai").insertOne(new Document("ancien", true));
        db.getCollection("stockv2_etats_stock").insertMany(List.of(
                new Document("produitId", "p-1").append("siteId", null).append("quantite", 4.0),
                new Document("produitId", "p-1").append("siteId", "site-A").append("quantite", 6.0)));
        db.getCollection("stockv2_bons_sortie").insertOne(new Document("reference", "BS-20260101-001")
                .append("statut", "SOUMIS"));
        db.getCollection("stockv2_bons_sortie").insertOne(new Document("reference", "BS-20260101-002")
                .append("statut", "SOUMIS").append("version", 4L));
        db.getCollection("conges").insertOne(new Document("statut", "EN_ATTENTE")
                .append("dateDebut", LocalDate.of(2026, 11, 2)).append("dateFin", LocalDate.of(2026, 11, 8)));

        demarrer(nom);

        Document modules = db.getCollection("utilisateur").find().first().get("modulesAutorises", Document.class);
        assertThat(modules.get("rh")).isInstanceOf(Document.class);
        assertThat(modules.get("rh", Document.class).getBoolean("dossierEmploye")).isTrue();
        assertThat(modules).doesNotContainKey("Stock");
        Document dossier = db.getCollection("dossiers_employes").find().first();
        assertThat(dossier).doesNotContainKey("AgentId").containsEntry("agentId", "5678")
                .containsKey("dateCreationAgentId").containsEntry("dateCreationAgentIdEstimee", true);
        assertThat(db.getCollection("contrats").find().first().getString("typeContrat")).isEqualTo("PRESTATION");
        assertThat(db.listCollectionNames().into(new ArrayList<>())).doesNotContain("periodes_essai");
        List<Document> soldes = db.getCollection("stockv2_etats_stock").find().into(new ArrayList<>());
        assertThat(soldes).hasSize(1);
        assertThat(soldes.get(0).get("siteId")).isNull();
        assertThat(soldes.get(0).getDouble("quantite")).isEqualTo(10.0);
        assertThat(db.getCollection("stockv2_bons_sortie").find(new Document("reference", "BS-20260101-001"))
                .first().getLong("version")).as("version initiale posée").isZero();
        assertThat(db.getCollection("stockv2_bons_sortie").find(new Document("reference", "BS-20260101-002"))
                .first().getLong("version")).as("version existante conservée").isEqualTo(4L);
        // 1b (D4) : bloc pièce posé, référence historique conservée comme numéro, registre rempli.
        Document piece = db.getCollection("stockv2_bons_sortie").find(new Document("reference", "BS-20260101-001"))
                .first().get("piece", Document.class);
        assertThat(piece.getString("type")).isEqualTo("BS");
        assertThat(piece.getString("numero")).isEqualTo("BS-20260101-001");
        assertThat(piece.get("exercice")).isNull();
        assertThat(db.getCollection("registre_pieces").countDocuments(new Document("type", "BS")
                .append("statut", "SOUMISE"))).isEqualTo(2);
        assertThat(db.getCollection("migration_rapports").countDocuments(
                new Document("migrationId", "0012-pieces-bons-existants"))).isEqualTo(1);
        Document conge = db.getCollection("conges").find().first();
        assertThat(conge.getString("statut")).isEqualTo("EN_ATTENTE_RH");
        assertThat(conge.getInteger("nombreJours")).as("lundi → dimanche = 5 jours ouvrés").isEqualTo(5);
    }

    @Test
    void profil_demo_cree_les_donnees_de_demonstration() {
        String nom = nouvelleBase();
        MongoDatabase db = client.getDatabase(nom);

        demarrer(nom, "--spring.profiles.active=demo");

        assertThat(executions(db, "9001-demo-rh")).isEqualTo(1);
        assertThat(executions(db, "9002-demo-terrain")).isEqualTo(1);
        assertThat(db.getCollection("sites_clients").countDocuments()).isEqualTo(2);
        assertThat(db.getCollection("formations").countDocuments()).isEqualTo(3);
    }
}
