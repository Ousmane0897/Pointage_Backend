package com.example.Pointage_Cleanic.services.rh;

import com.example.Pointage_Cleanic.Enum.rh.JoursTravail;
import com.example.Pointage_Cleanic.entities.rh.AffectationSite;
import com.example.Pointage_Cleanic.entities.rh.DossierEmploye;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Répond à « sur quels sites cet employé est-il attendu ce jour-là, et de quand à
 * quand ? ». Composant <b>pur</b> : aucun accès base, aucune horloge — le jour est
 * toujours passé en paramètre, ce qui le rend testable sans fixer le temps.
 *
 * <p>C'est le socle des lignes du pointage centralisé : une ligne par affectation
 * renvoyée ici, et non plus une ligne par pointage enregistré.
 */
@Component
public class PlanningAffectationResolver {

    /**
     * Affectations sur lesquelles l'employé est attendu le {@code jour} donné :
     * celles dont la période de présence couvre la date <b>et</b> dont la semaine
     * ouvrée contient le jour de la semaine.
     *
     * <p>L'ordre du résultat est celui de l'affichage <b>et</b> celui des
     * identifiants de ligne : horaire de début croissant (les affectations sans
     * horaire en dernier), puis nom de site. Il doit donc être déterministe.
     */
    public List<AffectationSite> prevuesPourJour(DossierEmploye employe, LocalDate jour) {
        if (employe == null || jour == null || employe.getAffectations() == null) {
            return List.of();
        }
        List<AffectationSite> prevues = new ArrayList<>();
        for (AffectationSite affectation : employe.getAffectations()) {
            if (affectation == null || affectation.getSite() == null
                    || affectation.getSite().isBlank()) {
                continue;
            }
            if (!periodeCouvre(affectation, jour)) continue;
            if (!jourOuvre(affectation, employe, jour.getDayOfWeek())) continue;
            prevues.add(affectation);
        }
        prevues.sort(Comparator
                .comparing((AffectationSite a) -> parseHeure(a.getHoraireDebut()),
                        Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(a -> a.getSite().toLowerCase()));
        return prevues;
    }

    /**
     * La période de présence sur le site couvre-t-elle ce jour ? Une borne nulle est
     * une borne infinie : {@code dateEntree} nulle ⇒ présent depuis toujours,
     * {@code dateSortie} nulle ⇒ toujours en poste (contrat documenté côté front).
     */
    public boolean periodeCouvre(AffectationSite affectation, LocalDate jour) {
        LocalDate entree = affectation.getDateEntree();
        LocalDate sortie = affectation.getDateSortie();
        if (entree != null && jour.isBefore(entree)) return false;
        return sortie == null || !jour.isAfter(sortie);
    }

    /**
     * Le site est-il travaillé ce jour de la semaine ? Échelle de replis :
     * <ol>
     *   <li>la semaine ouvrée <b>du site</b> ({@code AffectationSite.joursTravail}) ;</li>
     *   <li>à défaut celle <b>de l'employé</b> ({@code DossierEmploye.joursTravail},
     *       antérieure au rattachement par site) ;</li>
     *   <li>à défaut <b>aucun filtrage</b> — le site est réputé travaillé tous les jours.</li>
     * </ol>
     *
     * <p><b>Le dernier échelon est délibérément permissif.</b> Replier sur
     * {@code LUN_VEN} ferait disparaître toute ligne du samedi et du dimanche pour un
     * dossier qui n'a pas encore de rythme renseigné — donc masquerait une absence
     * réelle sur un écran dont c'est précisément la raison d'être. Dans une société de
     * nettoyage où {@code LUN_SAM} et {@code LUN_DIM} sont courants, le faux négatif
     * est ici le pire mode de défaillance : une ligne en trop se voit et se corrige,
     * une absence manquante ne se voit pas. {@code LUN_VEN} reste le défaut
     * d'<i>affichage</i> du front, ce qui n'en fait pas une règle de filtrage serveur.
     *
     * <p>Une valeur non reconnue (donnée corrompue) suit la même règle prudente.
     */
    public boolean jourOuvre(AffectationSite affectation, DossierEmploye employe, DayOfWeek jour) {
        String rythme = affectation.getJoursTravail();
        if (rythme == null || rythme.isBlank()) {
            rythme = employe != null ? employe.getJoursTravail() : null;
        }
        if (rythme == null || rythme.isBlank()) return true;

        JoursTravail valeur;
        try {
            valeur = JoursTravail.valueOf(rythme.trim());
        } catch (IllegalArgumentException ignored) {
            return true;
        }
        return switch (valeur) {
            case LUN_VEN -> jour.getValue() <= DayOfWeek.FRIDAY.getValue();
            case LUN_SAM -> jour.getValue() <= DayOfWeek.SATURDAY.getValue();
            case LUN_DIM -> true;
        };
    }

    /**
     * L'heure tombe-t-elle dans la tranche horaire du site (bornes incluses) ?
     * {@code false} dès qu'une des deux bornes manque — sans tranche connue, on ne
     * peut rien affirmer, et un horaire inventé produirait un rattachement faux.
     *
     * <p>Une tranche dont la fin précède le début est traitée comme <b>à cheval sur
     * minuit</b> (22:00–06:00). Le service refuse aujourd'hui d'enregistrer une telle
     * tranche, mais une écriture directe en base ne doit pas faire dérailler la lecture.
     */
    public boolean contient(AffectationSite affectation, LocalTime heure) {
        LocalTime debut = parseHeure(affectation.getHoraireDebut());
        LocalTime fin = parseHeure(affectation.getHoraireFin());
        if (debut == null || fin == null || heure == null) return false;
        if (debut.isAfter(fin)) {
            return !heure.isBefore(debut) || !heure.isAfter(fin);
        }
        return !heure.isBefore(debut) && !heure.isAfter(fin);
    }

    /** Parse une heure {@code "HH:mm"}, {@code null} si absente ou illisible. */
    public LocalTime parseHeure(String hhmm) {
        if (hhmm == null || hhmm.isBlank()) return null;
        try {
            return LocalTime.parse(hhmm.trim());
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }
}
