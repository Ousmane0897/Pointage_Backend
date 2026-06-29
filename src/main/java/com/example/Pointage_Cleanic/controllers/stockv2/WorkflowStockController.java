package com.example.Pointage_Cleanic.controllers.stockv2;

import com.example.Pointage_Cleanic.Dto.stockv2.BonWorkflowDto;
import com.example.Pointage_Cleanic.Enum.stockv2.SensBon;
import com.example.Pointage_Cleanic.Enum.stockv2.StatutBon;
import com.example.Pointage_Cleanic.services.stockv2.WorkflowStockService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/stock/workflow")
@RequiredArgsConstructor
public class WorkflowStockController {

    private final WorkflowStockService service;

    @GetMapping("/bons")
    public ResponseEntity<List<BonWorkflowDto>> bons(
            @RequestParam(required = false) StatutBon statut,
            @RequestParam(required = false) SensBon sens,
            @RequestParam(required = false) String q) {
        return ResponseEntity.ok(service.bons(statut, sens, q));
    }
}
