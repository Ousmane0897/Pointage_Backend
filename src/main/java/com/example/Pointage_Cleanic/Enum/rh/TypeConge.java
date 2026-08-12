package com.example.Pointage_Cleanic.Enum.rh;

/**
 * Nature d'une demande de congé.
 *
 * <p>Chaque valeur déclare explicitement si elle <b>ampute les jours de congé annuel
 * acquis</b> : la règle vit ici, à côté de la donnée, pour qu'ajouter un type oblige à
 * trancher la question au lieu de la laisser hériter d'un défaut silencieux.
 *
 * <p>⚠ Mongo désérialise l'enum <b>par son nom</b> : ajouter une valeur est sans effet sur
 * les documents existants, mais en renommer ou en supprimer une casserait leur relecture.
 */
public enum TypeConge {

    ANNUEL("Annuel", true),
    MATERNITE("Maternité", false),
    PATERNITE("Paternité", false),
    REPOS_MEDICAL("Repos médical", false),
    SANS_SOLDE("Sans solde", false),
    EXCEPTIONNEL("Exceptionnel", false),
    ABSENCE_NON_JUSTIFIEE("Absence non justifiée", false);

    private final String libelle;
    private final boolean decompteSoldeAnnuel;

    TypeConge(String libelle, boolean decompteSoldeAnnuel) {
        this.libelle = libelle;
        this.decompteSoldeAnnuel = decompteSoldeAnnuel;
    }

    /** Libellé fr-FR accentué, pour les rendus serveur (e-mails, pointage centralisé). */
    public String getLibelle() {
        return libelle;
    }

    /**
     * Ce type ampute-t-il les jours de congé annuel acquis ?
     *
     * <p>Seul le congé annuel le fait. Un repos médical, un congé maternité, un congé sans
     * solde ou une absence non justifiée ne se retranchent pas du compteur de congés payés.
     */
    public boolean decompteSoldeAnnuel() {
        return decompteSoldeAnnuel;
    }
}
