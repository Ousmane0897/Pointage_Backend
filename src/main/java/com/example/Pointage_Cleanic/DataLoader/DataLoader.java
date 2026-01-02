package com.example.Pointage_Cleanic.DataLoader;

import com.example.Pointage_Cleanic.entities.User;
import com.example.Pointage_Cleanic.repositories.LoginRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;


@Component
@RequiredArgsConstructor
public class DataLoader implements CommandLineRunner {

    private final LoginRepository loginRepository;

    private final PasswordEncoder passwordEncoder;


    @Override
    public void run(String... args) {
        // Vérifie si l'utilisateur existe déjà
        if (loginRepository.findByEmail("ousmane.diouf@cleanicsenegal.com").isEmpty()) {
            User user = new User();
            user.setEmail("ousmane.diouf@cleanicsenegal.com");
            user.setPassword(passwordEncoder.encode("admin2025"));
            user.setRole("SUPERADMIN");
            user.setMustChangePassword(true);
            loginRepository.save(user);
            System.out.println("Utilisateur admin créé");
        }
    }
}
