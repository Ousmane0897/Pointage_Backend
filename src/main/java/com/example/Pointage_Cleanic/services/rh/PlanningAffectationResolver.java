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
     * <ol start="0">
     *   <li>les <b>jours explicites du site</b> ({@code AffectationSite.joursSemaine}),
     *       quand la liste est renseignée ;</li>
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
     *
     * <p>Le <b>jour de repos hebdomadaire</b> du site ({@code AffectationSite.jourRepos})
     * est retiré <i>après</i> la semaine ouvrée — voir {@link #estJourDeRepos}.
     */
    public boolean jourOuvre(AffectationSite affectation, DossierEmploye employe, DayOfWeek jour) {
        // ── Échelon 0 : les jours explicites du site, quand ils sont renseignés.
        // Ils font SEULS autorité — ni le rythme préréglé, ni le jour de repos ne s'y
        // ajoutent : la liste EST la semaine ouvrée. C'est ce qui permet d'exprimer
        // « lundi, mercredi, vendredi », rythme que JoursTravail ne sait pas décrire et
        // qui faisait compter l'agent ABSENT les quatre autres jours.
        if (aDesJoursExplicites(affectation)) {
            return contientJour(affectation.getJoursSemaine(), jour);
        }

        String rythme = affectation.getJoursTravail();
        if (rythme == null || rythme.isBlank()) {
            rythme = employe != null ? employe.getJoursTravail() : null;
        }
        // ⚠ Le repos est évalué même sans rythme connu (échelon permissif) : un site qui
        // porte un jour de repos explicite l'a fait saisir par la RH, c'est une information
        // sûre, contrairement à un rythme absent.
        if (rythme == null || rythme.isBlank()) return !estJourDeRepos(affectation, rythme, jour);

        JoursTravail valeur;
        try {
            valeur = JoursTravail.valueOf(rythme.trim());
        } catch (IllegalArgumentException ignored) {
            return !estJourDeRepos(affectation, null, jour);
        }
        boolean dansLaSemaineOuvree = switch (valeur) {
            case LUN_VEN -> jour.getValue() <= DayOfWeek.FRIDAY.getValue();
            case LUN_SAM -> jour.getValue() <= DayOfWeek.SATURDAY.getValue();
            case LUN_DIM -> true;
            // ⚠ PERSONNALISE sans jours exploitables : AUCUN filtrage, et non « aucun
            // jour ». Le marqueur promet une liste dans joursSemaine ; si elle est absente,
            // on ne sait rien du rythme, et déclarer la semaine fermée mettrait les jours
            // ouvrables — donc les absences — à zéro. Le faux négatif reste ici le pire
            // mode de défaillance. Le service refuse cette combinaison à l'écriture : cette
            // branche ne couvre qu'une écriture directe en base.
            case PERSONNALISE -> true;
        };
        return dansLaSemaineOuvree && !estJourDeRepos(affectation, rythme, jour);
    }

    /**
     * La semaine ouvrée du site est-elle donnée en clair, sous une forme exploitable ?
     *
     * <p>⚠ Une liste ne contenant <b>que</b> des valeurs inutilisables (nulles ou hors
     * intervalle, cas d'une corruption) est réputée <b>absente</b> : on retombe alors sur le
     * rythme, plutôt que de fermer la semaine entière. Même prudence que le rythme corrompu
     * ci-dessus — une donnée illisible ne doit pas faire disparaître des créneaux.
     */
    private boolean aDesJoursExplicites(AffectationSite affectation) {
        List<Integer> jours = affectation.getJoursSemaine();
        if (jours == null) return false;
        for (Integer j : jours) {
            if (j != null && j >= 0 && j <= 7) return true;
        }
        return false;
    }

    /**
     * Ce jour figure-t-il dans la liste explicite ?
     *
     * <p>Convention {@code Date.getDay()} du front (0 = dimanche), 7 ISO toléré — mêmes
     * règles que {@link #estJourDeRepos}. Les entrées nulles ou hors intervalle sont
     * ignorées plutôt que refusées : le résolveur est un lecteur, la validation vit dans
     * {@code DossierEmployeService}.
     */
    private boolean contientJour(List<Integer> jours, DayOfWeek jour) {
        int jourFront = jour == DayOfWeek.SUNDAY ? 0 : jour.getValue();
        for (Integer j : jours) {
            if (j == null) continue;
            int normalise = j == 7 ? 0 : j;
            if (normalise == jourFront) return true;
        }
        return false;
    }

    /**
     * Ce jour est-il le repos hebdomadaire du site ?
     *
     * <p>Le champ n'existe que pour les sites qui dérogent au repos dominical implicite —
     * un restaurant ouvert le dimanche, dont les agents se reposent un autre jour.
     * <b>Null ⇒ rien n'est retiré</b>, ce qui laisse tout le parc existant inchangé.
     *
     * <p>⚠ <b>Sans effet sur {@code LUN_VEN}</b> : la semaine y porte déjà ses deux jours de
     * repos, en retirer un troisième donnerait une semaine de quatre jours. La garde est ici
     * plutôt que dans le formulaire seul, pour qu'une valeur restée en base sur un dossier
     * dont le rythme a changé après coup reste sans effet.
     *
     * <p>⚠ La convention est celle de {@code Date.getDay()} côté front (0 = dimanche), qui
     * coïncide avec l'ISO de lundi à samedi. Le 7 ISO est accepté pour dimanche : une
     * écriture directe en base à ce format ne doit pas passer inaperçue.
     */
    private boolean estJourDeRepos(AffectationSite affectation, String rythme, DayOfWeek jour) {
        Integer repos = affectation.getJourRepos();
        if (repos == null) return false;
        if (rythme != null && JoursTravail.LUN_VEN.name().equals(rythme.trim())) return false;
        int normalise = repos == 7 ? 0 : repos;
        int jourFront = jour == DayOfWeek.SUNDAY ? 0 : jour.getValue();
        return normalise == jourFront;
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
