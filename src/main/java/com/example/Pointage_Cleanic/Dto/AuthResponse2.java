package com.example.Pointage_Cleanic.Dto;

import com.example.Pointage_Cleanic.entities.GestionModules.ModulesAutorises;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class AuthResponse2 {
    private String token;
    private ModulesAutorises modulesAutorises;
}
