package com.example.Pointage_Cleanic.util;

import com.example.Pointage_Cleanic.entities.rh.AffectationSite;
import com.example.Pointage_Cleanic.entities.rh.DossierEmploye;

import java.time.LocalDate;
import java.util.List;

/**
 * « Cet employé est-il actuellement rattaché à ce site ? » — règle unique, partagée par
 * le décompte d'effectif d'un site ({@code EffectifSiteService}) et le contrôle
 * « pointage hors affectation » du scan QR d'agence.
 * <p>
 * ⚠ Les affectations désignent le site par son <b>nom</b> (aucun {@code siteId}) : la
 * comparaison est exacte, insensible à la casse et aux espaces de bord. Renommer un site
 * rompt donc le lien avec ses agents — limite connue, héritée par les deux appelants.
 * <p>
 * Classe statique pure ; « aujourd'hui » est un paramètre, jamais {@code LocalDate.now()}.
 */
public final class RattachementSiteUtils {

    private RattachementSiteUtils() {
    }

    /**
     * Deux règles, dans cet ordre, et l'ordre compte :
     * <ol>
     *   <li>dossier doté d'affectations structurées ⇒ <b>elles seules font foi</b>,
     *       les closes exclues ;</li>
     *   <li>sinon seulement, repli sur {@code siteAffecte} (dossiers antérieurs).</li>
     * </ol>
     * Sans la subordination de l'étape 2, un agent parti dont l'affectation close ne
     * matche plus retomberait sur {@code siteAffecte} et serait réputé rattaché.
     */
    public static boolean estAffecteAuSite(DossierEmploye dossier, String nomSite, LocalDate aujourdHui) {
        if (dossier == null || nomSite == null || nomSite.isBlank()) {
            return false;
        }
        List<AffectationSite> affectations = dossier.getAffectations();
        if (affectations != null && !affectations.isEmpty()) {
            return AffectationSiteUtils.actives(affectations, aujourdHui).stream()
                    .anyMatch(a -> memeSite(a.getSite(), nomSite));
        }
        return SiteAffecteUtils.decouper(dossier.getSiteAffecte()).stream()
                .anyMatch(s -> memeSite(s, nomSite));
    }

    public static boolean memeSite(String candidat, String nom) {
        return candidat != null && nom != null && candidat.trim().equalsIgnoreCase(nom.trim());
    }
}
