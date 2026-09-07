package com.example.Pointage_Cleanic.Dto.rh;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;

import java.time.LocalDate;

/**
 * Enfant à charge — cf. {@link com.example.Pointage_Cleanic.entities.rh.EnfantEmploye}.
 *
 * <p>{@code id} est posé serveur ; un client qui le renvoie tel quel conserve
 * l'identité de la ligne, un client qui l'omet en reçoit un nouveau.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class EnfantEmployeDto {

    private String id;
    private String prenom;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate dateNaissance;
}
