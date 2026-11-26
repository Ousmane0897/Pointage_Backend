package com.example.Pointage_Cleanic.entities.pieces;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** Abandon d'un brouillon : il est conservé, jamais supprimé (lot 1 / 1b, D4). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Abandon {
    private AuteurInstantane par;
    private LocalDateTime le;
}
