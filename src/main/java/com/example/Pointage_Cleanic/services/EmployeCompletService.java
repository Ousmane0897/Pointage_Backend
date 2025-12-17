package com.example.Pointage_Cleanic.services;


import com.example.Pointage_Cleanic.Dto.EmployeCompletDto;
import com.example.Pointage_Cleanic.Mapper.EmployeCompletMapper;
import com.example.Pointage_Cleanic.Mapper.EmployeMapper;
import com.example.Pointage_Cleanic.entities.Employe;
import com.example.Pointage_Cleanic.entities.EmployeComplet;
import com.example.Pointage_Cleanic.exception.EmployeAlreadyExistsException;
import com.example.Pointage_Cleanic.repositories.EmployeCompletRepository;
import com.example.Pointage_Cleanic.repositories.EmployeRepository;
import jdk.jfr.Registered;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Instant;
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

        // ===========================================================
        //        1️⃣ Construction du nom complet (unique)
        // ===========================================================

        String nomComplet = (dto.getPrenom() + " " + dto.getNom()).trim().toUpperCase();



        // ===========================================================
        //        2️⃣ Vérifications des champs uniques
        // ===========================================================

        if (employeCompletRepository.existsByAgentId(dto.getAgentId())) {
            throw new EmployeAlreadyExistsException("Un employé avec cet agentId existe déjà.");
        }

        if (employeCompletRepository.existsByMatricule(dto.getMatricule())) {
            throw new EmployeAlreadyExistsException("Un employé avec ce matricule existe déjà.");
        }

        if (employeCompletRepository.existsByNomComplet(nomComplet)) {
            throw new EmployeAlreadyExistsException("Un employé avec ce prénom et nom existe déjà.");
        }



        // ===========================================================
        //        3️⃣ Création EmployeComplet via MapStruct
        // ===========================================================

        EmployeComplet employeComplet = employeCompletMapper.toEntity(dto);

        // Ajouter le champ nomComplet
        employeComplet.setNomComplet(nomComplet);

        // Ajouter la photo si disponible
        if (photo != null && !photo.isEmpty()) {
            employeComplet.setPhoto(photo.getBytes());
        }

        // Sauvegarde EmployeComplet
        EmployeComplet savedComplet = employeCompletRepository.save(employeComplet);


        // ===========================================================
        //        4️⃣ Création Employe (simple) via MapStruct
        // ===========================================================

        Employe employe = employeMapper.toEmploye(dto);

        // Champs backend non mappés
        employe.setEmployeCreePar("SYSTEM");
        employe.setSiteAvantDeplacement(
                dto.getAgence() != null && dto.getAgence().length > 0 ? dto.getAgence()[0] : null
        );
        employe.setMatin(false);
        employe.setApresMidi(false);
        employe.setDeplacement(false);
        employe.setRemplacement(false);
        employe.setHorairesDeRemplacement(null);
        employe.setPersonneRemplacee(null);
        employe.setDateEtHeureCreation(Instant.now().toString());
        employe.setHeuresSupplementaires(null);

        employeRepository.save(employe);


        // ===========================================================
        //        5️⃣ Retour de l'employé complet créé
        // ===========================================================

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

    public void delete(String matricule) {

        // 🔍 Vérifier si l'employé complet existe
        EmployeComplet employeComplet = employeCompletRepository.findByMatricule(matricule)
                .orElseThrow(() -> new RuntimeException("Employé complet introuvable : " + matricule));

        // 🗑 Supprimer EmployeComplet
        employeCompletRepository.delete(employeComplet);

        // 🔍 Vérifier s'il existe aussi dans la collection employes
        Optional<Employe> employe = employeRepository.findByCodeSecret(matricule); // agentId dans EmployeComplet représente le code secret dand Employe

        employe.ifPresent(e -> {
            employeRepository.delete(e);
            System.out.println("Employe supprimé : " + matricule);
        });

        System.out.println("EmployeComplet supprimé : " + matricule);
    }


    public EmployeComplet update(String matricule, EmployeCompletDto dto, MultipartFile photo) throws IOException {

        // 1️⃣ Récupération EmployeComplet
        EmployeComplet existing = employeCompletRepository.findByMatricule(matricule)
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

        Employe employe = employeRepository.findByCodeSecret(matricule) // le code secret de l'employe qui lui permet de se pointer est son agentId
                .orElseThrow(() -> new RuntimeException("Employé simple introuvable"));

        // 3️⃣ Mise à jour SÉCURISÉE (champs obligatoires seulement)
        employeMapper.updateEmployeFromDto(dto, employe);

        // Aucun champ optionnel n'est modifié ici.
        // Toutes les propriétés opérationnelles sont conservées.

        employeRepository.save(employe);

        return updated;
    }




}
