package spikemongock.echec;

import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;

/** Écrit deux documents puis échoue : la transaction doit tout annuler. */
@ChangeUnit(id = "spike-e001-echec", order = "001", author = "spike")
public class E001EchecMiParcoursChangeUnit {

    private final MongoTemplate mongoTemplate;

    public E001EchecMiParcoursChangeUnit(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Execution
    public void executer() {
        mongoTemplate.insert(new Document("cle", "a"), "spike_echec");
        mongoTemplate.insert(new Document("cle", "b"), "spike_echec");
        throw new IllegalStateException("échec volontaire à mi-parcours (spike)");
    }

    @RollbackExecution
    public void annuler() {
        // transactionnelle : l'annulation est faite par la base
    }
}
