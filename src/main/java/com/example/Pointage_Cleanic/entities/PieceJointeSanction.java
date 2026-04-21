package com.example.Pointage_Cleanic.entities;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.*;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PieceJointeSanction {

    private String id;
    private String nom;
    private String url;
    private String mimeType;
    private Long taille;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate dateUpload;

    @JsonIgnore
    private byte[] contenu;
}