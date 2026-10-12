package com.example.Pointage_Cleanic.Dto.pointage;

import com.example.Pointage_Cleanic.Enum.pointage.ModeHorsAffectation;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** Lecture et patch (champ {@code null} = inchangé) des paramètres de pointage. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ParametresPointageDto {
    private ModeHorsAffectation horsAffectation;
    private Boolean qrObligatoire;
    private LocalDateTime dateModification;
    private String modifieParNom;
}
