package com.example.Pointage_Cleanic.entities.rh;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.*;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Renouvellement {

    private String id;
    private String contratId;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate ancienneDateFin;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate nouvelleDateFin;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate dateRenouvellement;

    private String motif;
}