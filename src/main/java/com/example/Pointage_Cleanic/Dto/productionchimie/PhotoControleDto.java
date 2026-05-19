package com.example.Pointage_Cleanic.Dto.productionchimie;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PhotoControleDto {
    private String url;
    private String nomFichier;
    private String mimeType;
}
