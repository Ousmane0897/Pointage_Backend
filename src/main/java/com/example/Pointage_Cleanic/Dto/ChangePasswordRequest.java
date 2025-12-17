package com.example.Pointage_Cleanic.Dto;


import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ChangePasswordRequest {
    private String email; // utile car tu utilises email comme identifiant
    private String oldPassword;
    private String newPassword;
    private String confirmPassword;
    private String role;
}
