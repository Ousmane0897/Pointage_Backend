package com.example.Pointage_Cleanic.services;

import com.example.Pointage_Cleanic.entities.Superviseur;
import com.example.Pointage_Cleanic.entities.User;
import com.example.Pointage_Cleanic.exception.EmailAlreadyExistsException;
import com.example.Pointage_Cleanic.repositories.LoginRepository;
import com.example.Pointage_Cleanic.repositories.SuperviseursRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SuperviseursServices {

    private final SuperviseursRepository repository;

    private final PasswordEncoder passwordEncoder;

    private final LoginRepository loginRepository;


    public Superviseur save( Superviseur superviseur) {


        Superviseur superviseur1  = new Superviseur();
        User user = new User();

        if (repository.findByEmail(superviseur.getEmail()).isEmpty()) {

             superviseur1.setPrenom(superviseur.getPrenom());
             superviseur1.setNom(superviseur.getNom());
             superviseur1.setEmail(superviseur.getEmail());
             superviseur1.setPassword(passwordEncoder.encode(superviseur.getPassword()));
             superviseur1.setPoste(superviseur.getPoste());
             superviseur1.setRole(superviseur.getRole());
             superviseur1.setMotifDesactivation(superviseur.getMotifDesactivation());
             superviseur1.setActive(true);
            user.setEmail(superviseur.getEmail());
            user.setPassword(passwordEncoder.encode(superviseur.getPassword()));

            loginRepository.save(user);
            repository.save(superviseur1);
        } else {
            throw new EmailAlreadyExistsException("cet Email existe déja:" + superviseur.getEmail());
        }

        return superviseur1;

    }

    public List<Superviseur> getAll() {

        return repository.findAll();
    }

    public void delete(String id) {
        repository.deleteById(id);
    }

    public Superviseur getById(String id) {

        return repository.findById(id).orElseThrow();
    }

    public Superviseur update( String id, Superviseur superviseur) {

        Superviseur superviseur1 = repository.findById(id).orElseThrow();

        superviseur1.setPrenom(superviseur.getPrenom());
        superviseur1.setNom(superviseur.getNom());
        superviseur1.setEmail(superviseur.getEmail());
        superviseur1.setPassword(superviseur.getPassword());
        superviseur1.setPoste(superviseur.getPoste());
        superviseur1.setRole(superviseur.getRole());
        superviseur1.setMotifDesactivation(superviseur.getMotifDesactivation());
        superviseur1.setActive(superviseur.isActive());

        return superviseur1;

    }
}
