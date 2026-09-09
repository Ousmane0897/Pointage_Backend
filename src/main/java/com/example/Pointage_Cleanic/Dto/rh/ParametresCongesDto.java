package com.example.Pointage_Cleanic.Dto.rh;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Barème des droits à congés — cf. {@link com.example.Pointage_Cleanic.entities.rh.ParametresConges}.
 *
 * <p>⚠ Le {@code PUT} est un <b>patch</b> : tout champ {@code null} laisse la valeur en
 * base inchangée. C'est ce qui permet à un client partiel de modifier un seul réglage
 * sans effacer les paliers d'ancienneté.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class ParametresCongesDto {

    private String id;

    @Min(value = 0, message = "joursAcquisParMois doit être >= 0")
    private Integer joursAcquisParMois;

    private Boolean supplementEnfantsActif;

    @Min(value = 0, message = "joursParEnfant doit être >= 0")
    private Integer joursParEnfant;

    @Min(value = 0, message = "ageMaxEnfant doit être >= 0")
    private Integer ageMaxEnfant;

    private Boolean reserverAuxMeres;

    @Min(value = 0, message = "plafondJoursEnfants doit être >= 0")
    private Integer plafondJoursEnfants;

    private Boolean supplementAncienneteActif;

    @Valid
    private List<PalierAncienneteCongeDto> paliersAnciennete;

    private Boolean proratiserSupplements;

    private LocalDateTime dateModification;
    private String modifieParId;
    private String modifieParNom;
}
