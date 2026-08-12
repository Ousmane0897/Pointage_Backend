package com.example.Pointage_Cleanic.DataLoader;

import com.example.Pointage_Cleanic.Enum.rh.ActionValidationConge;
import com.example.Pointage_Cleanic.Enum.rh.NiveauValidationConge;
import com.example.Pointage_Cleanic.Enum.rh.StatutDemande;
import com.example.Pointage_Cleanic.entities.rh.DemandeConge;
import com.example.Pointage_Cleanic.entities.rh.DossierEmploye;
import com.example.Pointage_Cleanic.entities.rh.HistoriqueValidationConge;
import com.example.Pointage_Cleanic.repositories.rh.DemandeCongeRepository;
import com.example.Pointage_Cleanic.repositories.rh.DossierEmployeRepository;
import com.example.Pointage_Cleanic.services.rh.CongeCalendrier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Migration des demandes de congé vers le circuit de validation à 3 niveaux.
 *
 * <p>Deux traitements, tous deux <b>idempotents</b> :
 * <ol>
 *   <li><b>Statuts</b> — les demandes encore en {@code EN_ATTENTE} (workflow mono-niveau)
 *       passent en {@code EN_ATTENTE_SUPERIEUR}, ou {@code EN_ATTENTE_RH} si l'employé n'a
 *       pas de supérieur hiérarchique. Le validateur N1 est figé sur la demande et les
 *       anciens champs plats de décision sont convertis en entrée d'historique.</li>
 *   <li><b>Nombre de jours</b> — {@code nombreJours} est recalculé en <b>jours ouvrés</b>
 *       sur <i>toutes</i> les demandes, quel que soit leur statut : il était compté en jours
 *       calendaires (week-ends inclus), et les congés déjà pris continueraient sinon de
 *       fausser les soldes de l'année en cours.</li>
 * </ol>
 */
@Slf4j
@Component
@Order(902)
@RequiredArgsConstructor
public class CongeStatutMigrationRunner implements CommandLineRunner {

    private final DemandeCongeRepository demandeCongeRepository;
    private final DossierEmployeRepository dossierEmployeRepository;

    @Override
    public void run(String... args) {
        try {
            List<DemandeConge> demandes = demandeCongeRepository.findAll();
            if (demandes.isEmpty()) {
                return;
            }

            List<DemandeConge> aSauver = new ArrayList<>();
            int statutsMigres = 0;
            int joursRecalcules = 0;

            for (DemandeConge demande : demandes) {
                boolean modifiee = false;

                if (demande.getStatut() == StatutDemande.EN_ATTENTE) {
                    migrerStatut(demande);
                    statutsMigres++;
                    modifiee = true;
                }

                int attendu = CongeCalendrier.joursOuvres(demande.getDateDebut(), demande.getDateFin());
                if (demande.getNombreJours() == null || demande.getNombreJours() != attendu) {
                    demande.setNombreJours(attendu);
                    joursRecalcules++;
                    modifiee = true;
                }

                if (modifiee) {
                    aSauver.add(demande);
                }
            }

            if (!aSauver.isEmpty()) {
                demandeCongeRepository.saveAll(aSauver);
            }
            if (statutsMigres > 0 || joursRecalcules > 0) {
                log.info("Migration congés : {} statut(s) EN_ATTENTE convertis vers le circuit "
                        + "à 3 niveaux, {} nombreJours recalculés en jours ouvrés.",
                        statutsMigres, joursRecalcules);
            }
        } catch (Exception e) {
            // Une migration ratée ne doit pas empêcher l'application de démarrer.
            log.warn("Migration des demandes de congé échouée : {}", e.getMessage());
        }
    }

    private void migrerStatut(DemandeConge demande) {
        Optional<DossierEmploye> employe = demande.getEmployeId() == null
                ? Optional.empty()
                : dossierEmployeRepository.findById(demande.getEmployeId());

        String superieurId = employe.map(DossierEmploye::getSuperieurHierarchiqueId).orElse(null);
        String superieurNom = employe.map(DossierEmploye::getSuperieurHierarchiqueNom).orElse(null);
        boolean sansSuperieur = superieurId == null || superieurId.isBlank();

        demande.setSuperieurHierarchiqueId(superieurId);
        demande.setSuperieurHierarchiqueNom(superieurNom);
        demande.setNiveauSuperieurIgnore(sansSuperieur);
        demande.setStatut(sansSuperieur
                ? StatutDemande.EN_ATTENTE_RH
                : StatutDemande.EN_ATTENTE_SUPERIEUR);

        if (demande.getHistorique() == null || demande.getHistorique().isEmpty()) {
            demande.tracer(HistoriqueValidationConge.builder()
                    .action(ActionValidationConge.CREATION)
                    .date(demande.getDateDemande() == null
                            ? LocalDateTime.now()
                            : demande.getDateDemande().atStartOfDay())
                    .commentaire("Demande antérieure au circuit de validation à 3 niveaux")
                    .build());

            // Conversion de l'ancienne décision plate, si elle existait.
            if (demande.getDecideurNom() != null || demande.getCommentaireDecision() != null) {
                demande.tracer(HistoriqueValidationConge.builder()
                        .action(ActionValidationConge.VALIDATION)
                        .niveau(NiveauValidationConge.SUPERIEUR)
                        .auteurId(demande.getDecideurId())
                        .auteurNom(demande.getDecideurNom())
                        .date(demande.getDateDecision() == null
                                ? LocalDateTime.now()
                                : demande.getDateDecision().atStartOfDay())
                        .commentaire(demande.getCommentaireDecision())
                        .build());
            }
        }

        if (sansSuperieur) {
            demande.tracer(HistoriqueValidationConge.builder()
                    .action(ActionValidationConge.VALIDATION)
                    .niveau(NiveauValidationConge.SUPERIEUR)
                    .date(LocalDateTime.now())
                    .commentaire("Niveau ignoré — aucun supérieur hiérarchique renseigné")
                    .build());
        }
    }
}
