package com.example.Pointage_Cleanic.DataLoader;

import com.example.Pointage_Cleanic.entities.rh.DossierEmploye;
import com.example.Pointage_Cleanic.repositories.rh.DossierEmployeRepository;
import com.example.Pointage_Cleanic.util.RattachementEmailUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * Normalise au démarrage l'email des {@link DossierEmploye} existants
 * ({@code trim} + minuscules, vide ⇒ {@code null}), comme le fait désormais
 * {@code DossierEmployeService} à chaque écriture.
 *
 * <p>L'email est la seule clé qui rattache un compte de connexion à son dossier :
 * un espace saisi en fin de champ suffisait à empêcher le titulaire de déposer une
 * demande de congé, et à masquer le dossier dans le contrôle de rattachement des
 * comptes (qui recherche les emails normalisés à l'identique).
 *
 * <p><b>Idempotent</b> : seuls les dossiers dont l'email change sont écrits, et
 * seul le champ {@code email} est mis à jour — le reste du document n'est jamais
 * réécrit.
 */
@Slf4j
@Component
@Order(1210)
@RequiredArgsConstructor
public class DossierEmailNormalisationRunner implements CommandLineRunner {

    private final DossierEmployeRepository dossierEmployeRepository;
    private final MongoTemplate mongoTemplate;

    @Override
    public void run(String... args) {
        int normalises = 0;
        for (DossierEmploye dossier : dossierEmployeRepository.findAll()) {
            String actuel = dossier.getEmail();
            String normalise = RattachementEmailUtils.normaliser(actuel);
            if (actuel == null || Objects.equals(actuel, normalise)) {
                continue;
            }
            Update update = normalise == null
                    ? new Update().unset("email")
                    : new Update().set("email", normalise);
            mongoTemplate.updateFirst(
                    Query.query(Criteria.where("_id").is(dossier.getId())),
                    update, DossierEmploye.class);
            normalises++;
        }
        if (normalises > 0) {
            log.info("Normalisation des emails : {} dossier(s) employé(s) corrigé(s)", normalises);
        }
    }
}
