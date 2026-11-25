package com.example.Pointage_Cleanic.services.evenements;

import com.example.Pointage_Cleanic.entities.evenements.EvenementDomaine;

import java.util.Set;

/**
 * Consommateur d'événements de domaine (lot 1 / 1b, décision D5). Tout bean qui implémente cette
 * interface est appelé par {@link DispatcherEvenements} pour les types qu'il déclare.
 *
 * <p>Idempotence : le dispatcher enregistre {@code (nom, evenementId)} dans
 * {@code evenements_consommes} ; un événement déjà consommé n'est pas remis. Un consommateur
 * <b>transactionnel</b> (cas par défaut) fait ses écritures dans la même transaction que cette
 * trace : tout ou rien. Un consommateur à {@link #effetsExternes() effets externes} (e-mail,
 * WebSocket) est appelé hors transaction, la trace étant posée après son succès : en cas de
 * panne entre les deux, l'effet peut être rejoué une fois (au moins une fois, jamais perdu).
 */
public interface ConsommateurEvenement {

    /** Nom stable : il sert de clé d'idempotence ; ne pas le renommer. */
    String nom();

    /** Types d'événements traités. */
    Set<String> types();

    void traiter(EvenementDomaine evenement);

    /** Vrai si le traitement appelle l'extérieur (SMTP, WebSocket) : il ne tourne pas en transaction. */
    default boolean effetsExternes() {
        return false;
    }
}
