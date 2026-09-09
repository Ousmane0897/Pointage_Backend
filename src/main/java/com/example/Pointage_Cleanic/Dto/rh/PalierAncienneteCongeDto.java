package com.example.Pointage_Cleanic.Dto.rh;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.Min;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class PalierAncienneteCongeDto {

    @Min(value = 1, message = "anneesAnciennete doit être >= 1")
    private Integer anneesAnciennete;

    @Min(value = 0, message = "joursSupplementaires doit être >= 0")
    private Integer joursSupplementaires;
}
