package com.example.Pointage_Cleanic.Dto.rh;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class PieceJustificativeDto {

    private String id;
    private String nom;
    private String mimeType;
    private Long taille;
    private String url;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate dateUpload;
}