package com.example.Pointage_Cleanic.Enum.pieces;

/**
 * Types de pièces numérotées (lot 1 / 1b, décision D4).
 *
 * <p>Codes imposés par le CDC §3 « Services communs » : {@code DEV}, {@code FAC}, {@code BS},
 * {@code BE}, {@code OF}, {@code LOT}, {@code DD}, {@code BC}, {@code BCA}, {@code CHQ}. Codes
 * proposés pour les pièces que le CDC ne code pas : {@code CNG}, {@code INV}, {@code MVT},
 * {@code BUL}, {@code DSO}, {@code SAN}, {@code IMP}.
 *
 * <p>Le nom de la constante est le code imprimé dans le numéro ({@code BE-2026-0001}). Portés en
 * 1b : {@link #BE}, {@link #BS}, {@link #INV}. Les autres attendent leur sous-branche.
 */
public enum TypePiece {
    DEV("Devis"),
    FAC("Facture"),
    BS("Bon de sortie"),
    BE("Bon d'entrée"),
    OF("Ordre de fabrication"),
    LOT("Lot"),
    DD("Demande de dépense"),
    BC("Bon de commande"),
    BCA("Bon de caisse"),
    CHQ("Chèque"),
    CNG("Congé"),
    INV("Inventaire"),
    MVT("Mouvement de stock"),
    BUL("Bulletin de paie"),
    DSO("Déclaration sociale"),
    SAN("Sanction"),
    IMP("Import");

    private final String libelle;

    TypePiece(String libelle) {
        this.libelle = libelle;
    }

    public String libelle() {
        return libelle;
    }
}
