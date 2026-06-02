package com.example.Pointage_Cleanic.entities.terrain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** Position GPS horodatée (pointage, début/fin d'intervention). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PositionGps {
    private Double latitude;
    private Double longitude;
    private Double precisionM;
    private LocalDateTime timestamp;
}