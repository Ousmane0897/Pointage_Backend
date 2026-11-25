package com.example.Pointage_Cleanic.Enum.stockv2;

/** Action tracée dans l'historique d'un bon. */
public enum ActionWorkflow {
    CREATION,
    MODIFICATION,
    SOUMISSION,
    VALIDATION,
    REFUS,
    /**
     * Reprise d'un bon de sortie refusé : il repasse en BROUILLON pour correction.
     * <p>
     * Cette entrée est <b>ajoutée</b> à l'historique, qui n'est jamais réinitialisé : le cycle
     * refusé doit rester lisible après la reprise.
     */
    REPRISE,
    EFFECTIF,
    /** Annulation d'un bon EFFECTIF (lot 1 / 1b) : motif obligatoire, mouvements inverses. */
    ANNULATION,
    /** Abandon d'un brouillon (lot 1 / 1b) : il est conservé, jamais supprimé. */
    ABANDON
}
