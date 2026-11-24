package com.example.Pointage_Cleanic.configurations;

import io.mongock.runner.springboot.EnableMongock;
import org.springframework.context.annotation.Configuration;

/**
 * Migrations de données versionnées par Mongock (lot 1 / 1a, décision D2 ; spike :
 * docs/refonte/1a-socle-technique.md §1).
 *
 * <ul>
 *   <li>ChangeUnits dans {@code migrations.changeunits} : une enveloppe mince par migration,
 *       la logique vit dans un bean de {@code migrations} (testable directement) ;</li>
 *   <li>registre {@code mongockChangeLog} (une ChangeUnit appliquée n'est pas rejouée, sauf
 *       {@code runAlways}), verrou {@code mongockLock} (une seule instance migre) ;</li>
 *   <li>exécution pendant l'initialisation du contexte ({@code runner-type: initializingbean}) :
 *       avant que l'application n'accepte du trafic ; un échec refuse le démarrage ;</li>
 *   <li>transactionnelles par défaut ({@code mongock.transactional}) : exige le replica set.</li>
 * </ul>
 * Réglages : {@code mongock.*} dans {@code application.yml}.
 */
@Configuration
@EnableMongock
public class MigrationsConfig {
}
