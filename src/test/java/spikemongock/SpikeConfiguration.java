package spikemongock;

import io.mongock.runner.springboot.EnableMongock;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.MongoTransactionManager;

/**
 * Configuration du spike, passée explicitement comme source de l'application (pas de
 * {@code @Configuration} : elle ne doit être prise par aucun scan).
 */
@EnableMongock
public class SpikeConfiguration {

    @Bean
    MongoTransactionManager spikeTransactionManager(MongoDatabaseFactory factory) {
        return new MongoTransactionManager(factory);
    }

    @Bean
    ApplicationListener<ApplicationReadyEvent> spikeEcouteurPret() {
        return evenement -> SpikeEtat.applicationPrete = true;
    }
}
