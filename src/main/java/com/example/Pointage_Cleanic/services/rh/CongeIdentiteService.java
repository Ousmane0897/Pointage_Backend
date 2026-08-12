package com.example.Pointage_Cleanic.services.rh;

import com.example.Pointage_Cleanic.Enum.rh.NiveauValidationConge;
import com.example.Pointage_Cleanic.entities.rh.DossierEmploye;
import com.example.Pointage_Cleanic.repositories.rh.DossierEmployeRepository;
import com.example.Pointage_Cleanic.services.terrain.CurrentUserProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Résout l'identité métier de l'appelant pour le circuit de validation des congés.
 *
 * <p>Le JWT ne porte <b>ni id ni employeId</b> : « qui suis-je » et « de qui suis-je le
 * supérieur » ne peuvent se déduire que de l'e-mail (subject du token) rapproché du dossier
 * employé. C'est la brique dont dépendent {@code /moi}, {@code /mes-demandes} et
 * {@code /a-valider}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CongeIdentiteService {

    /** Rôle validateur du niveau 2. */
    public static final String ROLE_RH = "RH";

    /**
     * Rôle validateur du niveau 3 : la Direction générale <b>est</b> le super-admin.
     * Aucun rôle {@code DIRECTION_GENERALE} n'existe ni ne doit être créé.
     */
    public static final String ROLE_SUPERADMIN = "SUPERADMIN";

    private final CurrentUserProvider currentUserProvider;
    private final DossierEmployeRepository dossierEmployeRepository;

    // ─── Identité ─────────────────────────────────────────────────────────────

    public String emailCourant() {
        return currentUserProvider.currentEmail();
    }

    public String nomCourant() {
        return currentUserProvider.currentUserNom();
    }

    public String idUtilisateurCourant() {
        return currentUserProvider.currentUserId();
    }

    /**
     * Dossier employé de l'appelant, vide si son compte n'est rattaché à aucun dossier
     * (comptes techniques, super-admin non salarié…).
     */
    public Optional<DossierEmploye> employeCourant() {
        String email = emailCourant();
        if (email == null || email.isBlank()) {
            return Optional.empty();
        }
        List<DossierEmploye> trouves = dossierEmployeRepository.findByEmailIgnoreCase(email.trim());
        if (trouves.isEmpty()) {
            return Optional.empty();
        }
        if (trouves.size() > 1) {
            // `email` n'est pas contraint unique en base : on tranche de façon déterministe
            // (le plus petit id) pour ne pas dépendre de l'ordre de la collection.
            log.warn("{} dossiers employés partagent l'email {} — résolution sur le premier id.",
                    trouves.size(), email);
            return trouves.stream().min(Comparator.comparing(DossierEmploye::getId));
        }
        return Optional.of(trouves.get(0));
    }

    /** Id du dossier employé de l'appelant, ou {@code null} s'il n'est pas rattaché. */
    public String employeIdCourant() {
        return employeCourant().map(DossierEmploye::getId).orElse(null);
    }

    // ─── Rôles ────────────────────────────────────────────────────────────────

    public boolean estRh() {
        return ROLE_RH.equals(currentUserProvider.currentRole());
    }

    /** Super-admin = Direction générale (niveau 3). */
    public boolean estSuperAdmin() {
        return ROLE_SUPERADMIN.equals(currentUserProvider.currentRole());
    }

    /** Peut déposer une demande au nom d'un tiers. */
    public boolean peutCreerPourAutrui() {
        return estRh() || estSuperAdmin();
    }

    /** L'appelant encadre-t-il au moins un employé ? (validateur de niveau 1) */
    public boolean estSuperieurHierarchique() {
        String employeId = employeIdCourant();
        return employeId != null
                && dossierEmployeRepository.existsBySuperieurHierarchiqueId(employeId);
    }

    /**
     * Niveaux que l'appelant peut trancher. Le super-admin les couvre tous — il valide le
     * niveau 3 et peut débloquer les précédents.
     */
    public List<NiveauValidationConge> niveauxValidables() {
        List<NiveauValidationConge> niveaux = new ArrayList<>();
        if (estSuperAdmin()) {
            niveaux.add(NiveauValidationConge.SUPERIEUR);
            niveaux.add(NiveauValidationConge.RH);
            niveaux.add(NiveauValidationConge.DIRECTION_GENERALE);
            return niveaux;
        }
        if (estSuperieurHierarchique()) {
            niveaux.add(NiveauValidationConge.SUPERIEUR);
        }
        if (estRh()) {
            niveaux.add(NiveauValidationConge.RH);
        }
        return niveaux;
    }

    // ─── Périmètre de lecture ─────────────────────────────────────────────────

    /**
     * Périmètre de lecture des congés de l'appelant, résolu en <b>une seule passe</b>.
     *
     * <p>⚠ Perf : {@code currentRole()} et {@code employeCourant()} coûtent chacun une à
     * deux lectures Mongo. On les appelle ici une fois et on fait circuler le résultat —
     * ne jamais rappeler {@code estRh()} / {@code estSuperAdmin()} depuis un stream de
     * filtrage. Règle : <b>un {@code perimetreLecture()} par méthode publique</b>.
     */
    public PerimetreConges perimetreLecture() {
        String role = currentUserProvider.currentRole();
        if (ROLE_RH.equals(role) || ROLE_SUPERADMIN.equals(role)) {
            return PerimetreConges.tout();
        }

        String moi = employeIdCourant();
        if (moi == null) {
            return PerimetreConges.vide();
        }

        Set<String> visibles = new HashSet<>();
        visibles.add(moi);
        dossierEmployeRepository.findBySuperieurHierarchiqueId(moi)
                .forEach(subordonne -> visibles.add(subordonne.getId()));
        return new PerimetreConges(false, moi, Set.copyOf(visibles));
    }
}
