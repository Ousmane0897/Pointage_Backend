package com.example.Pointage_Cleanic.entities.terrain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Tâche d'une checklist d'intervention. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TacheChecklist {
    private String libelle;
    private boolean fait;
    private String observation;
}