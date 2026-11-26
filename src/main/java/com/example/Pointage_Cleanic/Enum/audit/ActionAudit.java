package com.example.Pointage_Cleanic.Enum.audit;

/**
 * Actions tracées par le journal d'audit (lot 1 / 1b, décision D6). {@code ABANDON} et
 * {@code REPRISE} (brouillon abandonné, pièce refusée remise en brouillon) s'ajoutent à la liste
 * de D6 : écart mineur consigné dans docs/refonte/1b-pieces.md.
 */
public enum ActionAudit {
    CREATION,
    MODIFICATION,
    SOUMISSION,
    VALIDATION,
    REFUS,
    REPRISE,
    ANNULATION,
    ABANDON,
    DESACTIVATION,
    IMPORT,
    EXPORT,
    PARAMETRE
}
