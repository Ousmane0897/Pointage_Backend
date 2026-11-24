package spikemongock.ok;

import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;

/** Création d'index : hors transaction (transactional = false), comme le feront les ChangeUnits d'index. */
@ChangeUnit(id = "spike-s002-index", order = "002", author = "spike", transactional = false)
public class S002IndexHorsTransactionChangeUnit {

    private final MongoTemplate mongoTemplate;

    public S002IndexHorsTransactionChangeUnit(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Execution
    public void executer() {
        mongoTemplate.indexOps("spike_cible")
                .ensureIndex(new Index("cle", Sort.Direction.ASC).unique().named("cle_unique"));
    }

    @RollbackExecution
    public void annuler() {
        mongoTemplate.indexOps("spike_cible").dropIndex("cle_unique");
    }
}
