package com.example.Pointage_Cleanic.services;


import com.example.Pointage_Cleanic.Dto.EmployeCompletDto;
import com.example.Pointage_Cleanic.Mapper.EmployeCompletMapper;
import com.example.Pointage_Cleanic.Mapper.EmployeMapper;
import com.example.Pointage_Cleanic.entities.Employe;
import com.example.Pointage_Cleanic.entities.EmployeComplet;
import com.example.Pointage_Cleanic.repositories.EmployeCompletRepository;
import com.example.Pointage_Cleanic.repositories.EmployeRepository;
import jdk.jfr.Registered;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class EmployeCompletService {

    private final EmployeCompletRepository employeCompletRepository;
    private final EmployeCompletMapper employeCompletMapper;
    private final EmployeMapper employeMapper;
    private final EmployeRepository employeRepository;

    public EmployeComplet save(EmployeComplet employeComplet) {

        return employeCompletRepository.save(employeComplet);
    }

    // On utilise MapStruct pour créer l'entité. Puis on ajoute la photo si fournie. Enfin on sauvegarde.
    public EmployeComplet create(EmployeCompletDto dto, MultipartFile photo) throws IOException {

        // 1️⃣ Création de l'employé complet
        EmployeComplet employeComplet = employeCompletMapper.toEntity(dto);

        if (photo != null && !photo.isEmpty()) {
            employeComplet.setPhoto(photo.getBytes());
        }

        EmployeComplet savedComplet = employeCompletRepository.save(employeComplet);

        // 2️⃣ Création Employe (simple)
        Employe employe = employeMapper.toEmploye(dto);

        // Champs laissés volontairement à initialiser manuellement
        employe.setEmployeCreePar("SYSTEM");
        employe.setSiteAvantDeplacement(dto.getAgence() != null && dto.getAgence().length > 0 ? dto.getAgence()[0] : null);
        employe.setMatin(false);
        employe.setApresMidi(false);
        employe.setDeplacement(false);
        employe.setRemplacement(false);
        employe.setHorairesDeRemplacement(null);
        employe.setPersonneRemplacee(null);
        employe.setDateEtHeureCreation(java.time.Instant.now().toString());
        employe.setHeuresSupplementaires(null);

        employeRepository.save(employe);

        return savedComplet;
    }

    public List<EmployeComplet> getAll() {

        return employeCompletRepository.findAll();
    }

    public EmployeComplet getEmploye(String prenom, String nom) {

        Optional<EmployeComplet> employeComplet  = employeCompletRepository.findByPrenomAndNom(prenom,nom);
        return employeComplet.orElse(null);
    }

    public EmployeComplet getByAgentId(String AgentId) {

       Optional<EmployeComplet> employeComplet =  employeCompletRepository.findByAgentId(AgentId);

        return employeComplet.orElse(null);

    }

    public void delete(String agentId) {

        // 🔍 Vérifier si l'employé complet existe
        EmployeComplet employeComplet = employeCompletRepository.findByAgentId(agentId)
                .orElseThrow(() -> new RuntimeException("Employé complet introuvable : " + agentId));

        // 🗑 Supprimer EmployeComplet
        employeCompletRepository.delete(employeComplet);

        // 🔍 Vérifier s'il existe aussi dans la collection employes
        Optional<Employe> employe = employeRepository.findByCodeSecret(agentId); // agentId dans EmployeComplet représente le code secret dand Employe

        employe.ifPresent(e -> {
            employeRepository.delete(e);
            System.out.println("Employe supprimé : " + agentId);
        });

        System.out.println("EmployeComplet supprimé : " + agentId);
    }


    public EmployeComplet update(String agentId, EmployeCompletDto dto, MultipartFile photo) throws IOException {

        // 1️⃣ Récupération EmployeComplet
        EmployeComplet existing = employeCompletRepository.findByAgentId(agentId)
                .orElseThrow(() -> new RuntimeException("Employé complet introuvable"));

        // 2️⃣ Mise à jour EmployeComplet (tous champs sauf photo gérés à part)
        employeCompletMapper.updateEntityFromDto(dto, existing);

        if (photo != null && !photo.isEmpty()) {
            existing.setPhoto(photo.getBytes());
        }

        EmployeComplet updated = employeCompletRepository.save(existing);


        // ===============================
        //  SYNCHRONISATION AVEC Employe
        // ===============================

        Employe employe = employeRepository.findByCodeSecret(agentId) // le code secret de l'employe qui lui permet de se pointer est son agentId
                .orElseThrow(() -> new RuntimeException("Employé simple introuvable"));

        // 3️⃣ Mise à jour SÉCURISÉE (champs obligatoires seulement)
        employeMapper.updateEmployeFromDto(dto, employe);

        // Aucun champ optionnel n'est modifié ici.
        // Toutes les propriétés opérationnelles sont conservées.

        employeRepository.save(employe);

        return updated;
    }




}
