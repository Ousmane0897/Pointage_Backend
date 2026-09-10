package com.example.Pointage_Cleanic.services.rh;

import com.example.Pointage_Cleanic.entities.rh.AffectationSite;
import com.example.Pointage_Cleanic.entities.rh.DossierEmploye;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Répond à « ce jour est-il ouvrable <b>pour cet employé</b> ? ».
 *
 * <p>C'est le point de vérité unique du calcul des jours ouvrables : il compose le rythme de
 * travail et le jour de repos ({@link PlanningAffectationResolver}) avec le calendrier des
 * jours fériés ({@link JourFerieService}), sans réimplémenter ni l'un ni l'autre. Sans lui,
 * la règle serait recopiée dans le récapitulatif mensuel, le pointage centralisé, la paie et
 * les congés, et divergerait au premier ajustement.
 *
 * <p><b>Composant pur vis-à-vis de l'horloge</b> : « aujourd'hui » n'intervient jamais, les
 * bornes sont toujours passées en paramètre — comme {@link PlanningAffectationResolver} et
 * {@link CongeAcquisCalculator}. C'est ce qui le rend testable sur dates figées.
 *
 * <p>⚠ <b>Les fériés sont passés en argument, jamais relus ici.</b> L'appelant les charge
 * <b>une fois</b> par méthode publique via {@link JourFerieService#datesFeriees} et fait
 * circuler le {@code Set}. Dans un récapitulatif de N employés, une lecture par employé
 * serait N requêtes Mongo pour une valeur identique — et deux employés du même tableau
 * pourraient être calculés sur des calendriers différents si l'exécution chevauchait une
 * saisie RH. Même discipline que {@code PerimetreConges} et {@code BaremeConges}.
 */
@Service
@RequiredArgsConstructor
public class CalendrierTravailService {

    private final PlanningAffectationResolver planning;

    /**
     * Ce jour est-il ouvrable pour cet employé ?
     *
     * <p>Deux conditions : <b>au moins une</b> affectation active le couvre, <b>et</b> le
     * jour n'est pas férié.
     *
     * <p>⚠ <b>Multi-sites : la règle est le OU, pas le ET.</b> Un agent affecté à un site en
     * {@code LUN_VEN} et à un autre en {@code LUN_SAM} travaille bien le samedi. Le
     * récapitulatif compte des <b>jours</b>, pas des créneaux — ne pas confondre les deux
     * unités, comme le rappelle déjà {@code ResumeJourneeDto}.
     *
     * <p>⚠ <b>Un férié n'est jamais ouvrable, qu'il soit chômé ou non.</b> C'est la décision
     * métier : le jour sort des jours ouvrables, donc un agent qui ne vient pas n'est pas en
     * absence. Le travail réellement effectué reste compté par ailleurs, à partir des
     * pointages — c'est ce qui permet à un férié d'être travaillé sans être dû.
     */
    public boolean jourOuvrable(DossierEmploye employe, LocalDate jour, Set<LocalDate> feries) {
        if (employe == null || jour == null) return false;
        if (estFerie(jour, feries)) return false;
        return couvertParUneAffectation(employe, jour);
    }

    /**
     * Le jour est-il férié ? {@code null} et ensemble vide se comportent comme « aucun férié
     * saisi », c'est-à-dire comme avant l'existence du référentiel.
     */
    public boolean estFerie(LocalDate jour, Set<LocalDate> feries) {
        return jour != null && feries != null && feries.contains(jour);
    }

    /**
     * Jours ouvrables de l'employé sur la période, <b>bornes incluses</b>.
     *
     * <p>Renvoie les dates elles-mêmes et non un compte : les appelants en ont besoin pour
     * rapprocher les pointages jour par jour, et recompter derrière ferait diverger les deux
     * lectures.
     */
    public List<LocalDate> joursOuvrables(DossierEmploye employe, LocalDate debut, LocalDate fin,
                                          Set<LocalDate> feries) {
        List<LocalDate> jours = new ArrayList<>();
        if (employe == null || debut == null || fin == null || fin.isBefore(debut)) {
            return jours;
        }
        for (LocalDate d = debut; !d.isAfter(fin); d = d.plusDays(1)) {
            if (jourOuvrable(employe, d, feries)) jours.add(d);
        }
        return jours;
    }

    /**
     * Fériés de la période qui seraient tombés sur un jour travaillé par l'employé.
     *
     * <p>C'est ce chiffre que le récapitulatif affiche, et non le nombre total de fériés du
     * mois : un férié tombant le jour de repos d'un agent ne lui fait rien gagner et n'a
     * donc pas à figurer dans sa ligne.
     */
    public List<LocalDate> feriesTravailles(DossierEmploye employe, LocalDate debut, LocalDate fin,
                                            Set<LocalDate> feries) {
        List<LocalDate> jours = new ArrayList<>();
        if (employe == null || debut == null || fin == null || fin.isBefore(debut)
                || feries == null || feries.isEmpty()) {
            return jours;
        }
        for (LocalDate d = debut; !d.isAfter(fin); d = d.plusDays(1)) {
            if (feries.contains(d) && couvertParUneAffectation(employe, d)) jours.add(d);
        }
        return jours;
    }

    /**
     * Au moins une affectation attend l'employé ce jour-là — période de présence et semaine
     * ouvrée comprises. Délègue intégralement au résolveur, <b>échelle de replis incluse</b>
     * (site → employé → aucun filtrage) : un dossier sans rythme renseigné reste réputé
     * travaillé, ce qui évite de masquer une absence réelle.
     */
    private boolean couvertParUneAffectation(DossierEmploye employe, LocalDate jour) {
        List<AffectationSite> affectations = employe.getAffectations();
        if (affectations == null || affectations.isEmpty()) {
            // ⚠ Dossier sans affectation structurée : on ne peut rien affirmer sur son
            // rythme. Le déclarer non ouvrable mettrait ses jours ouvrables à zéro, donc
            // ses absences à zéro — exactement le faux négatif que le résolveur refuse.
            return true;
        }
        return !planning.prevuesPourJour(employe, jour).isEmpty();
    }
}
