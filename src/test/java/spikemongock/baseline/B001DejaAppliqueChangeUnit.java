package spikemongock.baseline;

import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;

/** Ne doit pas s'exécuter si la baseline a pu la marquer comme appliquée. */
@ChangeUnit(id = "spike-b001-deja-applique", order = "001", author = "spike")
public class B001DejaAppliqueChangeUnit {

    private final MongoTemplate mongoTemplate;

    public B001DejaAppliqueChangeUnit(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Execution
    public void executer() {
        mongoTemplate.insert(new Document("execute", true), "spike_baseline");
    }

    @RollbackExecution
    public void annuler() {
        // transactionnelle
    }
}
