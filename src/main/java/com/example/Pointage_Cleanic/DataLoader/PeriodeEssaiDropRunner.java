package com.example.Pointage_Cleanic.DataLoader;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Migration de suppression du module RH « Période d'essai / Titularisation » (RH 6.1).
 *
 * <p>Le module dédié (écrans front + entités {@code PeriodeEssai} /
 * {@code DemandeValidationPeriodeEssai}) a été retiré. Ce runner supprime les
 * collections Mongo qui lui étaient propres. Sur MongoDB, {@code drop()} d'une
 * collection retire aussi tous ses index — il n'y a pas de contrainte/index externe
 * à gérer séparément.
 *
 * <p><b>Périmètre :</b> ne touche QUE ces deux collections. La collection
 * {@code dossiers_employes} n'est pas concernée : le statut {@code EN_PERIODE_ESSAI}
 * et le champ {@code dureeEssaiMois} de l'employé restent intacts.
 *
 * <p>Sûr et idempotent : le drop n'est tenté que si la collection existe encore ;
 * les exécutions suivantes sont des no-op.
 */
@Slf4j
@Component
@Order(1200)
@RequiredArgsConstructor
public class PeriodeEssaiDropRunner implements CommandLineRunner {

    private static final List<String> COLLECTIONS_A_SUPPRIMER = List.of(
            "periodes_essai",
            "demandes_validation_periode_essai");

    private final MongoTemplate mongoTemplate;

    @Override
    public void run(String... args) {
        COLLECTIONS_A_SUPPRIMER.forEach(collection -> {
            if (mongoTemplate.collectionExists(collection)) {
                mongoTemplate.getCollection(collection).drop();
                log.info("Suppression module Période d'essai : collection '{}' droppée", collection);
            }
        });
    }
}
