package com.example.Pointage_Cleanic.configurations;

import com.example.Pointage_Cleanic.exception.ConflitConcurrentException;
import com.mongodb.MongoException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

/**
 * Exécute un traitement dans une transaction MongoDB et le <b>rejoue</b> quand la base le
 * signale comme transitoire (lot 1 / 1a, décision D1).
 *
 * <p>Pourquoi : deux transactions qui écrivent le même document ne s'attendent pas ; la seconde
 * reçoit aussitôt un {@code WriteConflict} étiqueté {@code TransientTransactionError}, et
 * {@code @Transactional} ne rejoue rien. Le traitement entier est donc rejoué (il relit l'état
 * à jour : un bon déjà validé par l'autre transaction donne alors un 409 métier), jusqu'à
 * {@value #ESSAIS_MAX} essais avec une courte attente aléatoire croissante. Au-delà :
 * {@link ConflitConcurrentException} (409 {@code CONFLIT_CONCURRENT}), sans rien d'écrit.
 *
 * <p>Conventions :
 * <ul>
 *   <li>le traitement ne fait <b>aucun appel externe</b> (SMTP, SMS, WebSocket, Google) : il est
 *       rejouable et peut être annulé ; ces appels partent des consommateurs d'événements de
 *       l'outbox ({@code services.evenements}, lot 1 / 1b) ;</li>
 *   <li>appelé alors qu'une transaction est déjà ouverte, il s'y joint sans rejouer : c'est la
 *       transaction englobante qui décide ;</li>
 *   <li>l'étiquette {@code UnknownTransactionCommitResult} n'est <b>pas</b> rejouée : le commit
 *       a pu réussir, et rejouer le traitement risquerait de l'appliquer deux fois.</li>
 * </ul>
 */
@Slf4j
@Component
public class ExecuteurTransactionnel {

    static final int ESSAIS_MAX = 5;

    private final TransactionTemplate transactionTemplate;

    public ExecuteurTransactionnel(TransactionTemplate transactionTemplate) {
        this.transactionTemplate = transactionTemplate;
    }

    public <T> T executer(Supplier<T> traitement) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            return traitement.get();
        }
        for (int essai = 1; ; essai++) {
            try {
                return transactionTemplate.execute(statut -> traitement.get());
            } catch (RuntimeException e) {
                if (!estTransitoire(e)) {
                    throw e;
                }
                if (essai >= ESSAIS_MAX) {
                    throw new ConflitConcurrentException(
                            "Opération en concurrence avec d'autres modifications des mêmes données : "
                                    + "rien n'a été enregistré, réessayez.", e);
                }
                log.debug("Conflit transactionnel transitoire, essai {}/{}", essai, ESSAIS_MAX);
                attendre(essai);
            }
        }
    }

    public void executer(Runnable traitement) {
        executer(() -> {
            traitement.run();
            return null;
        });
    }

    static boolean estTransitoire(Throwable erreur) {
        for (Throwable t = erreur; t != null; t = t.getCause()) {
            if (t instanceof MongoException me
                    && me.hasErrorLabel(MongoException.TRANSIENT_TRANSACTION_ERROR_LABEL)) {
                return true;
            }
            if (t.getCause() == t) {
                break;
            }
        }
        return false;
    }

    private static void attendre(int essai) {
        long ms = ThreadLocalRandom.current().nextLong(5, 25) * essai;
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ConflitConcurrentException("Interrompu pendant l'attente d'un nouvel essai.", e);
        }
    }
}
