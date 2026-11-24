package spikemongock;

import io.mongock.runner.springboot.EnableMongock;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;

/**
 * Configuration du spike, passée explicitement comme source de l'application (pas de
 * {@code @Configuration} : elle ne doit être prise par aucun scan). Le gestionnaire de
 * transactions vient de l'application ({@code MongoConfigurations}) : Mongock exige sa
 * présence pour {@code mongock.transactional=true}.
 */
@EnableMongock
public class SpikeConfiguration {

    @Bean
    ApplicationListener<ApplicationReadyEvent> spikeEcouteurPret() {
        return evenement -> SpikeEtat.applicationPrete = true;
    }
}
