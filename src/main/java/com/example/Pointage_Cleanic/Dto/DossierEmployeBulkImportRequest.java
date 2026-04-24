package com.example.Pointage_Cleanic.Dto;

import com.example.Pointage_Cleanic.Enum.StrategieErreursImport;

import java.util.List;

public record DossierEmployeBulkImportRequest(
        List<DossierEmployeBulkLigneDto> employes,
        StrategieErreursImport strategieErreurs
) {}