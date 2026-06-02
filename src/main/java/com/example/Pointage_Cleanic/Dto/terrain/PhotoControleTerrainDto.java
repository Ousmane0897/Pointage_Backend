package com.example.Pointage_Cleanic.Dto.terrain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class PhotoControleTerrainDto {
    private String nomFichier;
    private String url;
    private String mimeType;
    private String legende;
}