package com.example.Pointage_Cleanic.services.evenements;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Déclenche le dispatcher de l'outbox toutes les {@code outbox.dispatcher.delai-ms} ms (2 s par
 * défaut). Désactivable par {@code outbox.dispatcher.actif=false} : c'est le cas dans les tests,
 * qui appellent {@link DispatcherEvenements#traiterLot()} eux-mêmes pour rester déterministes.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "outbox.dispatcher.actif", havingValue = "true", matchIfMissing = true)
public class PlanificationDispatcher {

    private final DispatcherEvenements dispatcher;

    @Scheduled(fixedDelayString = "${outbox.dispatcher.delai-ms:2000}")
    public void declencher() {
        try {
            dispatcher.traiterLot();
        } catch (RuntimeException e) {
            log.warn("Dispatcher de l'outbox en échec, nouvel essai au prochain passage : {}", e.getMessage());
        }
    }
}
