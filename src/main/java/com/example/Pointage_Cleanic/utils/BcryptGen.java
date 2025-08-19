package com.example.Pointage_Cleanic.utils;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

public class BcryptGen {

    public static void main(String[] args) {
        String password = "babacar2025"; // à modifier si besoin
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        String encoded = encoder.encode(password);
        System.out.println("Mot de passe encodé : " + encoded);
    }
}
