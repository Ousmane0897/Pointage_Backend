package spikemongock;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;

/**
 * Configuration du spike, passée explicitement comme source de l'application (pas de
 * {@code @Configuration} : elle ne doit être prise par aucun scan). Le gestionnaire de
 * transactions vient de l'application ({@code MongoConfigurations}) : Mongock exige sa
 * présence pour {@code mongock.transactional=true}. {@code @EnableMongock} vient aussi de
 * l'application ({@code MigrationsConfig}) ; le spike ne fait que changer le paquet scanné.
 */
public class SpikeConfiguration {

    @Bean
    ApplicationListener<ApplicationReadyEvent> spikeEcouteurPret() {
        return evenement -> SpikeEtat.applicationPrete = true;
    }
}
