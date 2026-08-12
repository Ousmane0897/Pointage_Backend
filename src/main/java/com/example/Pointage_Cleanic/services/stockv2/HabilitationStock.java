package com.example.Pointage_Cleanic.services.stockv2;

import com.example.Pointage_Cleanic.exception.StockAccesRefuseException;
import com.example.Pointage_Cleanic.services.terrain.CurrentUserProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Habilitations du module Stock v2 — point unique des contrôles par rôle.
 *
 * <p>⚠ Ce backend n'a <b>aucune</b> autorisation déclarative : {@code SecurityConfig} se limite à
 * {@code .authenticated()} et il n'existe pas un seul {@code @PreAuthorize}. Les règles vivent donc
 * dans les services, et ce composant évite qu'elles se dispersent en copies divergentes.
 *
 * <p><b>Ce qui est protégé, et pourquoi.</b> Les opérations <b>irréversibles ou qui mutent le
 * stock</b> : décider d'un bon (valider / refuser), clôturer un inventaire, supprimer une donnée de
 * référence. Les écritures ordinaires (créer un bon, saisir un comptage, créer un produit) restent
 * ouvertes à tout compte authentifié.
 *
 * <p>⚠ <b>Ce n'est pas un oubli</b> : le modèle d'habilitation de l'application repose sur les
 * <b>flags de module</b> du JWT ({@code modules.stock.catalogue}, {@code .inventaires}…), que le
 * front gate écran par écran. Restreindre ces écritures à deux rôles verrouillerait des comptes
 * légitimes porteurs du flag. Le serveur, lui, <b>ignore complètement ce claim</b> : le faire
 * respecter côté serveur est le prolongement naturel de ce lot — en traitant au passage le cas de
 * {@code JwtUtil.generateToken2}, qui n'émet pas de {@code modules}.
 */
@Service
@RequiredArgsConstructor
public class HabilitationStock {

    /**
     * ⚠ Le super-administrateur est la chaîne {@code SUPERADMIN}, sans underscore : c'est la seule
     * valeur réellement émise (collection {@code login}).
     */
    public static final String ROLE_SUPERADMIN = "SUPERADMIN";
    public static final String ROLE_CONTROLEUR_STOCK = "CONTROLEUR_STOCK";

    private final CurrentUserProvider currentUser;

    public boolean estSuperAdmin() {
        return ROLE_SUPERADMIN.equals(currentUser.currentRole());
    }

    public boolean estControleurStock() {
        return ROLE_CONTROLEUR_STOCK.equals(currentUser.currentRole());
    }

    /**
     * Décisions qui engagent le stock (valider / refuser un bon, suppression définitive).
     *
     * <p>Aucun repli : le rôle est porté par le JWT, il est toujours connu.
     */
    public void exigerSuperAdmin(String action) {
        if (!estSuperAdmin()) {
            throw new StockAccesRefuseException(action + " réservé au super-administrateur");
        }
    }

    /** Opérations irréversibles de gestion : clôture d'inventaire, suppression d'un référentiel. */
    public void exigerControleurOuSuperAdmin(String action) {
        if (!estSuperAdmin() && !estControleurStock()) {
            throw new StockAccesRefuseException(
                    action + " réservé au contrôleur de stock et au super-administrateur");
        }
    }

    /**
     * Actions réservées à l'auteur d'un document (modifier, supprimer, soumettre, reprendre), le
     * contrôleur de stock et le super-administrateur gardant la main sur tous les documents.
     *
     * <p>⚠ Repli transitoire : sur les documents créés <b>avant</b> l'ajout de {@code creeParEmail},
     * le champ est nul et la propriété est considérée comme acquise — sinon d'anciens bons refusés
     * seraient définitivement bloqués. À retirer quand le parc sera renouvelé.
     */
    public void exigerCreateurOuControleur(String creeParEmail, String action) {
        if (estSuperAdmin() || estControleurStock()) {
            return;
        }
        if (creeParEmail == null || creeParEmail.isBlank()) {
            return;
        }
        String courant = currentUser.currentEmail();
        if (courant == null || !creeParEmail.trim().equalsIgnoreCase(courant.trim())) {
            throw new StockAccesRefuseException(action
                    + " réservé au créateur du document, au contrôleur de stock et au super-administrateur");
        }
    }
}
