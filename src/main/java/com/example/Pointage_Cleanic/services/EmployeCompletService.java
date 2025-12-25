package com.example.Pointage_Cleanic.services;


import com.example.Pointage_Cleanic.Dto.EmployeCompletDto;
import com.example.Pointage_Cleanic.Dto.ImportEmployeResponse;
import com.example.Pointage_Cleanic.Dto.ImportError;
import com.example.Pointage_Cleanic.Mapper.EmployeCompletMapper;
import com.example.Pointage_Cleanic.Mapper.EmployeMapper;
import com.example.Pointage_Cleanic.entities.Employe;
import com.example.Pointage_Cleanic.entities.EmployeComplet;
import com.example.Pointage_Cleanic.exception.EmployeAlreadyExistsException;
import com.example.Pointage_Cleanic.repositories.EmployeCompletRepository;
import com.example.Pointage_Cleanic.repositories.EmployeRepository;
import jdk.jfr.Registered;
import lombok.RequiredArgsConstructor;
import org.apache.coyote.BadRequestException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
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

        if (dto.getAgentId() == null || dto.getAgentId().length() < 4) {
            throw new BadRequestException("agentId invalide ou trop court");
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


    public ImportEmployeResponse importMany(List<EmployeCompletDto> dtos) {

        System.out.println("🧩 importMany() - Nb employés: " + dtos.size());

        List<EmployeCompletDto> success = new ArrayList<>();
        List<ImportError> errors = new ArrayList<>();

        int line = 1;
        for (EmployeCompletDto dto : dtos) {
            System.out.println("➡️ Traitement ligne " + line + " | " + dto.getAgentId());
            try {
                create(dto, null); // réutilise la méthode existante sans photo
                success.add(dto);
            } catch (EmployeAlreadyExistsException e) {
                errors.add(new ImportError(line, dto.getAgentId(), e.getMessage()));
            } catch (Exception e) {
                System.out.println("❌ Erreur ligne " + line + ": " + e.getMessage());
                errors.add(new ImportError(line, dto.getAgentId(), "Erreur interne : " + e.getMessage()));
            }
            line++;
        }

        return new ImportEmployeResponse(success, errors);
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
        Optional<Employe> employe = employeRepository.findByAgentId(agentId); // agentId dans EmployeComplet représente le code secret dand Employe

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

        Employe employe = employeRepository.findByAgentId(agentId) // le code secret de l'employe qui lui permet de se pointer est son agentId
                .orElseThrow(() -> new RuntimeException("Employé simple introuvable"));

        // 3️⃣ Mise à jour SÉCURISÉE (champs obligatoires seulement)
        employeMapper.updateEmployeFromDto(dto, employe);

        // Aucun champ optionnel n'est modifié ici.
        // Toutes les propriétés opérationnelles sont conservées.

        employeRepository.save(employe);

        return updated;
    }




}
