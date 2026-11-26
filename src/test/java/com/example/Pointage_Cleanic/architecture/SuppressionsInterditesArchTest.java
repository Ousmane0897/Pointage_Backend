package com.example.Pointage_Cleanic.architecture;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.repository.Repository;

import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Pattern;

import static java.util.Map.entry;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Garde-fous d'architecture du lot 1 / 1b.
 *
 * <ol>
 *   <li><b>Suppressions interdites</b> (décision D4) : aucun appel {@code delete*},
 *       {@code remove*}, {@code findAndRemove}, {@code findAllAndRemove} ou {@code dropCollection}
 *       sur un repository Spring Data ou sur {@code MongoTemplate} hors liste blanche. Une pièce
 *       s'annule ou s'abandonne, un référentiel se désactive. Liste blanche en deux parties :
 *       exceptions permanentes, et classes <b>en sursis</b> nommées avec la sous-branche qui les
 *       mettra en conformité. La liste ne peut que diminuer : une classe qui n'appelle plus de
 *       suppression doit en sortir.</li>
 *   <li><b>Règle 3</b> (décision D5) : un module n'appelle jamais le service d'écriture d'un autre
 *       module pour produire un effet ; il publie un événement. Liste explicite de services
 *       d'écriture par module ; les lectures de référentiels restent permises.</li>
 * </ol>
 */
class SuppressionsInterditesArchTest {

    private static final String RACINE = "com.example.Pointage_Cleanic";
    private static final Pattern SUPPRESSION =
            Pattern.compile("delete.*|remove.*|findAndRemove|findAllAndRemove|dropCollection");

    /** Suppressions légitimes, sans échéance. */
    private static final Set<String> PERMANENTES = Set.of(
            RACINE + ".services.ResetPasswordService"   // jetons de réinitialisation consommés ou expirés
    );
    /** Paquets entiers autorisés : migrations (D2 : elles transforment l'existant, avec rapport). */
    private static final Set<String> PAQUETS_PERMANENTS = Set.of(RACINE + ".migrations");

    /** Classe → sous-branche qui la mettra en conformité. */
    static final Map<String, String> EN_SURSIS = Map.ofEntries(
            // 1d — référentiels employé et site
            entry(RACINE + ".services.EmployeCompletService", "1d"),
            entry(RACINE + ".services.rh.DossierEmployeService", "1d"),
            entry(RACINE + ".services.terrain.SiteClientService", "1d"),
            // 1e — référentiel article unique (dont la compensation du /bulk produits)
            entry(RACINE + ".services.stockv2.ProduitStockService", "1e"),
            entry(RACINE + ".services.stockv2.CategorieStockService", "1e"),
            entry(RACINE + ".services.productionchimie.MatierePremiereService", "1e"),
            entry(RACINE + ".services.productionchimie.FormatConditionnementService", "1e"),
            entry(RACINE + ".services.terrain.MaterielService", "1e"),
            // 1f — grille des salaires
            entry(RACINE + ".services.rh.CategorieProfessionnelleService", "1f"),
            // 1g — comptes (suppression faite directement par le contrôleur)
            entry(RACINE + ".controllers.UtilisateurController", "1g"),
            // 1c — paramètres datés
            entry(RACINE + ".services.rh.JourFerieService", "1c"),
            // Modules métier, dans le lot de leur module (lot-1-conception.md §4, sous 1b) :
            // RH lot 6, Terrain lot 2, Stock lot 3, Production chimie lot 8
            entry(RACINE + ".services.rh.BesoinFormationService", "lot 6"),
            entry(RACINE + ".services.rh.BulletinPaieService", "lot 6"),
            entry(RACINE + ".services.rh.ContratService", "lot 6"),
            entry(RACINE + ".services.rh.DeclarationSocialeService", "lot 6"),
            entry(RACINE + ".services.rh.DocumentEmployeService", "lot 6"),
            entry(RACINE + ".services.rh.EvaluationPeriodiqueService", "lot 6"),
            entry(RACINE + ".services.rh.FormationService", "lot 6"),
            entry(RACINE + ".services.rh.HeureSupplementaireService", "lot 6"),
            entry(RACINE + ".services.rh.RhAbsenceService", "lot 6"),
            entry(RACINE + ".services.rh.SanctionService", "lot 6"),
            entry(RACINE + ".services.productionchimie.FormulationService", "lot 8"),
            entry(RACINE + ".services.productionchimie.GrilleControleService", "lot 8"),
            entry(RACINE + ".services.stockv2.ChantierService", "lot 3"),
            entry(RACINE + ".services.stockv2.PlafondService", "lot 3"),
            entry(RACINE + ".services.terrain.ControleQualiteService", "lot 2"),
            entry(RACINE + ".services.terrain.InterventionService", "lot 2"),
            entry(RACINE + ".services.terrain.PhytosanitaireService", "lot 2")
    );

    /** Services d'écriture par module (règle 3) : non appelables depuis un autre module. */
    private static final Map<String, Set<String>> SERVICES_ECRITURE = Map.of(
            "stockv2", Set.of("BonEntreeService", "BonSortieService", "InventaireService",
                    "MouvementStockService", "ProduitStockService", "ChantierService"),
            "rh", Set.of("DossierEmployeService", "ContratService", "BulletinPaieService",
                    "DemandeCongeService", "CongeWorkflowService", "RhAbsenceService"),
            "terrain", Set.of("PlanningService", "InterventionService", "PointageTerrainService"),
            "productionchimie", Set.of("OrdreFabricationService", "LotService", "MatierePremiereService",
                    "MouvementStockChimieService")
    );

    /** Violations existantes de la règle 3, en sursis (aucune aujourd'hui). */
    private static final Set<String> REGLE_3_EN_SURSIS = Set.of();

    private static JavaClasses classes;

    @BeforeAll
    static void importer() {
        classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages(RACINE);
    }

    @Test
    void aucune_suppression_hors_liste_blanche() {
        Map<String, Set<String>> appelants = new TreeMap<>();
        for (JavaClass c : classes) {
            for (JavaMethodCall appel : c.getMethodCallsFromSelf()) {
                JavaClass cible = appel.getTargetOwner();
                boolean stockage = cible.isAssignableTo(Repository.class) || cible.isAssignableTo(MongoOperations.class);
                if (stockage && SUPPRESSION.matcher(appel.getName()).matches()) {
                    appelants.computeIfAbsent(classeEnglobante(c), k -> new TreeSet<>())
                            .add(cible.getSimpleName() + "." + appel.getName());
                }
            }
        }
        appelants.keySet().removeIf(n -> PERMANENTES.contains(n)
                || PAQUETS_PERMANENTS.stream().anyMatch(p -> n.startsWith(p + ".")));

        Map<String, Set<String>> nonPrevus = new TreeMap<>(appelants);
        nonPrevus.keySet().removeAll(EN_SURSIS.keySet());
        assertThat(nonPrevus).as("suppressions hors liste blanche : annuler, abandonner ou désactiver").isEmpty();

        Set<String> obsoletes = new TreeSet<>(EN_SURSIS.keySet());
        obsoletes.removeAll(appelants.keySet());
        assertThat(obsoletes).as("classes en sursis qui ne suppriment plus : les enlever de la liste").isEmpty();
    }

    @Test
    void regle_3_pas_d_appel_aux_services_d_ecriture_d_un_autre_module() {
        Set<String> violations = new TreeSet<>();
        for (JavaClass c : classes) {
            String module = module(c.getPackageName());
            if (module == null) {
                continue;
            }
            c.getDirectDependenciesFromSelf().forEach(d -> {
                JavaClass cible = d.getTargetClass();
                String moduleCible = module(cible.getPackageName());
                if (moduleCible != null && !moduleCible.equals(module)
                        && SERVICES_ECRITURE.getOrDefault(moduleCible, Set.of()).contains(cible.getSimpleName())) {
                    violations.add(classeEnglobante(c) + " -> " + cible.getName());
                }
            });
        }
        violations.removeAll(REGLE_3_EN_SURSIS);
        assertThat(violations).as("règle 3 : publier un événement au lieu d'appeler un autre module").isEmpty();
    }

    /** Module d'une classe de services (services.<module>.…), sinon null. */
    private static String module(String paquet) {
        String prefixe = RACINE + ".services.";
        if (!paquet.startsWith(prefixe)) {
            return null;
        }
        String reste = paquet.substring(prefixe.length());
        int point = reste.indexOf('.');
        return point < 0 ? reste : reste.substring(0, point);
    }

    private static String classeEnglobante(JavaClass c) {
        String nom = c.getName();
        int dollar = nom.indexOf('$');
        return dollar < 0 ? nom : nom.substring(0, dollar);
    }
}
