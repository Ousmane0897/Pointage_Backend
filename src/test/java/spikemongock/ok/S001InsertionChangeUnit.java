package spikemongock.ok;

import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import spikemongock.SpikeEtat;

/** ChangeUnit transactionnelle nominale : deux écritures. */
@ChangeUnit(id = "spike-s001-insertion", order = "001", author = "spike")
public class S001InsertionChangeUnit {

    private final MongoTemplate mongoTemplate;

    public S001InsertionChangeUnit(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Execution
    public void executer() {
        SpikeEtat.pretePendantMigration = SpikeEtat.applicationPrete;
        SpikeEtat.executions.incrementAndGet();
        SpikeEtat.migrationCommencee.countDown();
        if (SpikeEtat.pauseMs > 0) {
            try {
                Thread.sleep(SpikeEtat.pauseMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        mongoTemplate.insert(new Document("cle", "a"), "spike_cible");
        mongoTemplate.insert(new Document("cle", "b"), "spike_cible");
    }

    @RollbackExecution
    public void annuler() {
        // transactionnelle : l'annulation est faite par la base
    }
}
