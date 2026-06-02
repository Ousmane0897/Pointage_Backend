package com.example.Pointage_Cleanic.Dto.terrain;

import com.example.Pointage_Cleanic.Enum.terrain.MomentPhoto;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Métadonnée d'une nouvelle photo, alignée sur l'ordre des fichiers uploadés. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class PhotoMetaDto {
    private MomentPhoto moment;
    private String legende;
}