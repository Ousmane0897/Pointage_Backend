package com.example.Pointage_Cleanic.entities.pointage;

import com.example.Pointage_Cleanic.Enum.pointage.ModeHorsAffectation;
import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

/**
 * Paramètres du pointage kiosque — document <b>singleton</b>, get-or-create paresseux
 * (patron de {@code ParametresConges}). Lu par le flux de pointage <b>public</b> : sa
 * lecture ne dépend jamais de l'identité de l'appelant.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Document(collection = "parametres_pointage")
public class ParametresPointage {

    @Id
    private String id;

    /** {@code POINTAGE_HORS_AFFECTATION} — défaut {@link ModeHorsAffectation#AVERTIR}. */
    private ModeHorsAffectation horsAffectation;

    /**
     * Interrupteur de transition. Tant qu'il est faux, un pointage <b>sans</b> QR d'agence
     * suit le flux historique (« Agence non renseignée ») ; une fois vrai, il est refusé.
     * Un pointage <b>avec</b> QR est toujours contrôlé, quelle que soit sa valeur.
     */
    private Boolean qrObligatoire;

    private LocalDateTime dateModification;
    private String modifieParId;
    private String modifieParNom;
}
