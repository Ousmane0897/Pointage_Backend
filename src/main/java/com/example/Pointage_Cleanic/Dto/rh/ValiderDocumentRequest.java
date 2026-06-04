package com.example.Pointage_Cleanic.Dto.rh;

import com.example.Pointage_Cleanic.Enum.rh.DecisionDocument;

public record ValiderDocumentRequest(DecisionDocument statut, String commentaire) {
}
