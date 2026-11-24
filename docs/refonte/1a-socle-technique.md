# Lot 1 — Sous-branche 1a : socle technique

Branche `refonte/v3-1a-socle-technique` (backend et frontend), créée depuis `refonte/v3` le 24/11/2026. Objet : décisions **D1** (transactions), **D2** (migrations versionnées) et **D3** (index) de `docs/refonte/lot-1-conception.md`. Rien n'a été poussé.

Ce document est complété à chaque étape ; il est livré complet avec le dernier commit de 1a.

---

## 1. Spike Mongock (D2, Q-C)

### 1.1 Question posée

Mongock 5 fonctionne-t-il avec la pile actuelle :
- Spring Boot 3.3.6 ;
- Spring Data MongoDB 4.3.6 et pilote 5.0.1 ;
- Java 21 ;
- notre `MongoMappingContext` personnalisé ;
- les transactions sur replica set ?

Si oui, avec quelles limites ? Sinon, repli sur l'exécuteur maison prévu par D2.

### 1.2 Montage

- **Dépendances** : BOM `io.mongock:mongock-bom:5.5.1` (dernière version publiée), `mongock-springboot-v3`, `mongodb-springdata-v4-driver`. Aucun conflit de version : Mongock reprend le Spring Data et le pilote de la BOM Spring Boot. Licence : **Apache 2.0**, en édition communautaire.
- **Test `spikemongock/MongockSpikeIT`**. Il démarre une vraie application `PointageCleanicApplication` par scénario, sur une base dédiée du conteneur de test (`mongo:7.0`, replica set).
- **ChangeUnits du spike** : dans le paquet de test `spikemongock`, placé hors de `com.example.Pointage_Cleanic` pour qu'aucun autre test ne le scanne.
- **Réglages** :
  - `mongock.runner-type=initializingbean` ;
  - `mongock.transactional=true`, qui remplace `transaction-enabled`, déclaré obsolète par Mongock 5.5 ;
  - `@EnableMongock` est obligatoire : le lanceur n'a pas d'autoconfiguration, seul le pilote en a une.

### 1.3 Résultats

| # | Point vérifié | Résultat | Preuve |
|---|---|---|---|
| 1 | Démarrage avec Boot 3.3.6 / Spring Data 4.3.6 / Java 21 et notre `MongoMappingContext` | ✅ | tous les scénarios |
| 2 | Exécution **avant le trafic** | ✅ La ChangeUnit tourne pendant l'initialisation des beans, avant `ApplicationReadyEvent`. Un échec fait échouer `SpringApplication.run` : le serveur n'écoute jamais. | `execute_avant_le_trafic_avec_registre_et_verrou` |
| 3 | Registre `mongockChangeLog` et verrou `mongockLock` | ✅ Une entrée `EXECUTED` par ChangeUnit, plus deux entrées système de Mongock. | idem |
| 4 | Pas de rejeu d'une ChangeUnit déjà appliquée | ✅ Second démarrage : 0 exécution. | `une_change_unit_appliquee_n_est_pas_rejouee` |
| 5 | Transaction : échec à mi-parcours ⇒ rien d'écrit | ✅ Les 2 insertions avant l'exception sont annulées. | `echec_a_mi_parcours_n_ecrit_rien_et_refuse_le_demarrage` |
| 6 | Échec ⇒ démarrage refusé | ✅ `BeanCreationException` portant le message de la ChangeUnit. | idem |
| 7 | Deux instances qui démarrent en même temps | ✅ La seconde démarre pendant que la première exécute sa ChangeUnit (5 s, verrou tenu). Elle attend le verrou, constate que tout est appliqué, puis démarre : 1 seule exécution. | `deux_demarrages_simultanes_n_executent_qu_une_fois` |
| 8 | Index créé par une ChangeUnit `transactional = false` | ✅ | point 2 (ChangeUnit `S002`) |
| 9 | « Baseline » qui marque d'autres ChangeUnits comme appliquées | ❌ Mongock lit le registre **une seule fois, avant la première ChangeUnit**. Une entrée `EXECUTED` écrite par une ChangeUnit `0000` n'empêche pas la suivante de s'exécuter. | `une_baseline_ecrite_dans_le_registre_n_empeche_pas_l_execution` |

### 1.4 Constats à connaître

1. **Mongock crée son propre `MongoTransactionManager`.** Le bean `PlatformTransactionManager` de l'application n'est qu'un **interrupteur** : sans lui, `transactional=true` refuse de démarrer (« transactionManager not provided »). Le gestionnaire réellement utilisé est construit par le pilote sur la même `MongoDatabaseFactory`.
2. **Mongock passe le `MongoTemplate` partagé en `SessionSynchronization.ALWAYS`**, durablement. Conséquence attendue : le template rejoint toute transaction ouverte sur le fil. C'est sans effet tant que le seul gestionnaire de transactions est Mongo (cas de l'application).
3. **Une ChangeUnit en échec ne laisse aucune trace dans le registre.** L'entrée `FAILED` est écrite dans la transaction annulée. L'échec n'est visible que dans les journaux, et la ChangeUnit sera retentée au démarrage suivant. C'est le comportement voulu par D2 (refus de démarrer, message explicite).
4. **Les transactions exigent un replica set.** Sur un `mongod` standalone, le commit échoue (« Transaction numbers are only allowed on a replica set member or mongos »). Mongock masque alors l'erreur d'origine derrière un `IllegalTransactionStateException: Transaction is already completed`, dû à un second `rollback` dans son bloc `catch`. **Ce message signifie donc presque toujours « pas de replica set ».** Il apparaîtra au premier démarrage d'un poste de développement resté en standalone : voir §2 (`docker-compose.dev.yml`).
5. **Artefact de test, pas de Mongock.** Deux `SpringApplication` lancées au même instant dans une même JVM se disputent l'initialisation de logback (`ConcurrentModificationException`). Le test décale donc la seconde jusqu'au début de la ChangeUnit de la première. En production, les instances sont des processus distincts : la question ne se pose pas.
6. **Incident du spike.** Les premiers lancements passaient l'URI de test en « propriétés par défaut ». Or cette source a la plus basse priorité : `application.yml` (`localhost:27017`) l'emportait, et les lancements ont visé la base locale de développement `cleanic`, en standalone. Ils y ont créé `mongockChangeLog` (8 entrées `ROLLED_BACK`) et `mongockLock` (vide). Ces deux collections ont été supprimées aussitôt (vérifié). Aucune ChangeUnit du spike n'y a tourné ; les 12 runners actuels y ont tourné comme lors d'un `bootRun`, ce qui est sans effet puisqu'ils sont idempotents. **Correctif** : le test passe ses réglages en arguments de ligne de commande, la source la plus prioritaire, et vérifie le nom de la base effective.

### 1.5 Décision

**Mongock 5.5.1 est retenu.** Le repli sur l'exécuteur maison n'est pas nécessaire.

Conséquences pour la suite de 1a :
- **Baseline (D2)** : la ChangeUnit `0000-baseline` prévue par la conception n'est **pas réalisable** comme décrit (point 9). Chaque ChangeUnit issue d'un runner garde sa garde d'idempotence actuelle. Sur une base existante, elle s'exécute une fois, ne modifie rien et s'inscrit au registre. L'effet recherché est le même, « ne pas rejouer les 12 runners », et `MigrationsIT` le vérifie sur un jeu « base existante ». **Écart de forme avec D2, pas de fond.**
- **ChangeUnits d'index** : `transactional = false`. MongoDB n'autorise pas la création d'index dans une transaction sur une collection existante.
- **ChangeUnits de données** : transactionnelles. Un échec n'écrit rien et refuse le démarrage.
- **Développement** : le poste doit tourner en replica set (constat 4).
