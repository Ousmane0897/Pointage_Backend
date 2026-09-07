package com.example.Pointage_Cleanic.services.rh;

import com.example.Pointage_Cleanic.Enum.rh.GenreEmploye;
import com.example.Pointage_Cleanic.entities.rh.DossierEmploye;
import com.example.Pointage_Cleanic.entities.rh.EnfantEmploye;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * Les seules données du dossier employé dont dépendent les droits à congés.
 *
 * <p>Ce record existe pour que {@link CongeAcquisCalculator} ne dépende pas d'une entité
 * Mongo : il reste pur, et ses tests s'écrivent avec trois valeurs plutôt qu'un dossier
 * complet.
 *
 * <p>⚠ Il ne porte que des <b>dates immuables</b> (embauche, naissances) — jamais un
 * compteur d'enfants ni une ancienneté déjà calculée. C'est ce qui garantit qu'un exercice
 * clos sera recalculé à l'identique dans cinq ans : l'âge et l'ancienneté sont réévalués
 * à la date de référence de <i>cet</i> exercice, et non à celle d'aujourd'hui. Un scalaire
 * ferait bouger le passé, donc le reliquat reporté.
 *
 * @param dateEntree        entrée dans l'entreprise ; {@code null} toléré (dossiers antérieurs)
 * @param genre             gouverne la réserve « mères de famille »
 * @param naissancesEnfants dates de naissance des enfants à charge ; vide si non renseignées
 */
public record DroitsEmployeSnapshot(
        LocalDate dateEntree,
        GenreEmploye genre,
        List<LocalDate> naissancesEnfants
) {

    public DroitsEmployeSnapshot {
        naissancesEnfants = naissancesEnfants == null ? List.of() : List.copyOf(naissancesEnfants);
    }

    /**
     * Extrait le snapshot d'un dossier.
     *
     * <p>⚠ <b>Aucun repli sur {@code nombreEnfants}</b> : sans date de naissance, on ne peut
     * ni vérifier la condition d'âge, ni garantir la stabilité d'un exercice clos. Le
     * supplément vaut alors 0 — même arbitrage prudent que le {@code type} nul du décompte
     * de solde : sous-estimer un droit plutôt qu'en créditer un à tort. Le supplément
     * apparaîtra à la saisie des dates dans la fiche.
     */
    public static DroitsEmployeSnapshot from(DossierEmploye employe) {
        if (employe == null) {
            return new DroitsEmployeSnapshot(null, null, List.of());
        }
        List<LocalDate> naissances = employe.getEnfants() == null
                ? List.of()
                : employe.getEnfants().stream()
                        .filter(Objects::nonNull)
                        .map(EnfantEmploye::getDateNaissance)
                        .filter(Objects::nonNull)
                        .toList();
        return new DroitsEmployeSnapshot(employe.getDateEmbauche(), employe.getGenre(), naissances);
    }
}
