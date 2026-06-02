package com.example.Pointage_Cleanic.Dto.terrain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Notification WebSocket ciblée (destination {@code /user/queue/notifications-terrain}). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationTerrain {

    public enum Type {
        ALERTE_OUVERTE, ALERTE_ESCALADEE, ALERTE_TRAITEE, INFO
    }

    private Type type;
    private String titre;
    private String message;
    private String alerteId;
    private String niveau;
    private String dateEmission;
}