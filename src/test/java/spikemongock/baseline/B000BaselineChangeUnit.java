package spikemongock.baseline;

import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.Date;

/**
 * Tentative de baseline « dans Mongock » : écrire dans le registre une entrée EXECUTED pour
 * la ChangeUnit suivante, afin qu'elle soit sautée. Le spike vérifie si Mongock relit le
 * registre entre deux ChangeUnits.
 */
@ChangeUnit(id = "spike-b000-baseline", order = "000", author = "spike", transactional = false)
public class B000BaselineChangeUnit {

    private final MongoTemplate mongoTemplate;

    public B000BaselineChangeUnit(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Execution
    public void executer() {
        mongoTemplate.insert(new Document()
                .append("executionId", "baseline")
                .append("changeId", "spike-b001-deja-applique")
                .append("author", "spike")
                .append("timestamp", new Date())
                .append("state", "EXECUTED")
                .append("type", "EXECUTION")
                .append("changeLogClass", B001DejaAppliqueChangeUnit.class.getName())
                .append("changeSetMethod", "executer")
                .append("executionMillis", 0L)
                .append("executionHostname", "baseline")
                .append("systemChange", false), "mongockChangeLog");
    }

    @RollbackExecution
    public void annuler() {
        // rien
    }
}
