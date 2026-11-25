package com.example.Pointage_Cleanic.entities.evenements;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * Trace « tel consommateur a traité tel événement » (lot 1 / 1b, décision D5). L'index unique
 * {@code (consommateur, evenementId)} rend chaque consommateur idempotent : un événement
 * redistribué (bail expiré, nouvel essai après l'échec d'un autre consommateur) n'a d'effet
 * qu'une fois.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "evenements_consommes")
public class EvenementConsomme {

    @Id
    private String id;
    private String consommateur;
    private String evenementId;
    private Instant le;
}
