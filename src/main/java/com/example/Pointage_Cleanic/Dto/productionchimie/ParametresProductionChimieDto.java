package com.example.Pointage_Cleanic.Dto.productionchimie;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ParametresProductionChimieDto {

    private String id;

    /** Tolérance de contrôle du total (± %). */
    @PositiveOrZero
    private Double toleranceTotalPct;

    private LocalDateTime updatedAt;
    private String updatedBy;
}
