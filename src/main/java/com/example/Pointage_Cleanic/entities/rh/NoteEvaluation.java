package com.example.Pointage_Cleanic.entities.rh;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NoteEvaluation {

    private String critereCode;
    private String critereLibelle;
    private Integer note;
    private String commentaire;
}