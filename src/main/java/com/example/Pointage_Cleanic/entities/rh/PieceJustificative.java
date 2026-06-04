package com.example.Pointage_Cleanic.entities.rh;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.*;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PieceJustificative {

    private String id;
    private String nom;
    private String mimeType;
    private Long taille;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate dateUpload;

    private byte[] data;
}