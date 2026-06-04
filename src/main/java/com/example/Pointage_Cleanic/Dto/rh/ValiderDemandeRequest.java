package com.example.Pointage_Cleanic.Dto.rh;

import com.example.Pointage_Cleanic.Enum.rh.ActionValidation;

public record ValiderDemandeRequest(ActionValidation decision, String commentaire) {
}