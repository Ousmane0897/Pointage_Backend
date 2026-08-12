package com.example.Pointage_Cleanic.entities.GestionModules.SousModules;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;

import java.io.IOException;

/**
 * Désérialisation tolérante du champ {@code modulesAutorises.rh}.
 *
 * <p>Depuis le passage de {@code rh} booléen → objet de 17 sous-flags, un payload ou un
 * token historique peut encore présenter {@code rh} sous forme de booléen. Plutôt que de
 * planter, on le réinterprète :
 * <ul>
 *   <li>{@code true}  ⇒ accès RH complet : les 17 sous-flags à {@code true} ;</li>
 *   <li>{@code false} ⇒ aucun accès : objet vide (17 sous-flags à {@code false}) ;</li>
 *   <li>objet JSON ⇒ mapping normal des 17 clés (champs absents = {@code false}).</li>
 * </ul>
 *
 * <p>Le mapping objet est fait à la main (via {@link JsonNode}) pour éviter la récursion
 * infinie qu'entraînerait un {@code readValueAs(Rh.class)} alors que {@link Rh} est
 * justement annoté {@code @JsonDeserialize(using = RhDeserializer.class)}.
 */
public class RhDeserializer extends JsonDeserializer<Rh> {

    @Override
    public Rh deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        JsonToken token = p.currentToken();

        // Ancien format booléen : true ⇒ tous vrais, false ⇒ tous faux.
        if (token == JsonToken.VALUE_TRUE || token == JsonToken.VALUE_FALSE) {
            return allFlags(p.getBooleanValue());
        }

        // Format objet (nominal).
        if (token == JsonToken.START_OBJECT) {
            JsonNode node = p.readValueAsTree();
            return fromNode(node);
        }

        // null ou autre : aucun accès.
        return new Rh();
    }

    private static Rh allFlags(boolean value) {
        Rh rh = new Rh();
        rh.setDossierEmploye(value);
        rh.setContrats(value);
        rh.setOrganigramme(value);
        rh.setDocuments(value);
        rh.setPointageCentralise(value);
        rh.setAbsences(value);
        rh.setConges(value);
        rh.setCongesValidation(value);
        rh.setCongesMesDemandes(value);
        rh.setHeuresSupplementaires(value);
        rh.setRecapitulatif(value);
        rh.setGrilleSalariale(value);
        rh.setCalculBulletin(value);
        rh.setHistoriquePaies(value);
        rh.setDeclarations(value);
        rh.setFormations(value);
        rh.setEvaluations(value);
        rh.setSanctions(value);
        rh.setTableauBordRh(value);
        return rh;
    }

    private static Rh fromNode(JsonNode node) {
        Rh rh = new Rh();
        rh.setDossierEmploye(node.path("dossierEmploye").asBoolean(false));
        rh.setContrats(node.path("contrats").asBoolean(false));
        rh.setOrganigramme(node.path("organigramme").asBoolean(false));
        rh.setDocuments(node.path("documents").asBoolean(false));
        rh.setPointageCentralise(node.path("pointageCentralise").asBoolean(false));
        rh.setAbsences(node.path("absences").asBoolean(false));
        rh.setConges(node.path("conges").asBoolean(false));
        rh.setCongesValidation(node.path("congesValidation").asBoolean(false));
        rh.setCongesMesDemandes(node.path("congesMesDemandes").asBoolean(false));
        rh.setHeuresSupplementaires(node.path("heuresSupplementaires").asBoolean(false));
        rh.setRecapitulatif(node.path("recapitulatif").asBoolean(false));
        rh.setGrilleSalariale(node.path("grilleSalariale").asBoolean(false));
        rh.setCalculBulletin(node.path("calculBulletin").asBoolean(false));
        rh.setHistoriquePaies(node.path("historiquePaies").asBoolean(false));
        rh.setDeclarations(node.path("declarations").asBoolean(false));
        rh.setFormations(node.path("formations").asBoolean(false));
        rh.setEvaluations(node.path("evaluations").asBoolean(false));
        rh.setSanctions(node.path("sanctions").asBoolean(false));
        rh.setTableauBordRh(node.path("tableauBordRh").asBoolean(false));
        return rh;
    }
}