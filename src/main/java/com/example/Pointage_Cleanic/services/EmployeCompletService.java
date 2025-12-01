package com.example.Pointage_Cleanic.services;


import com.example.Pointage_Cleanic.entities.EmployeComplet;
import com.example.Pointage_Cleanic.repositories.EmployeCompletRepository;
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

    public EmployeComplet save(EmployeComplet employeComplet) {

        return employeCompletRepository.save(employeComplet);
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

        EmployeComplet employeComplet1 = employeCompletRepository.findByMatricule(matricule).orElseThrow(() -> new RuntimeException("Agent non trouvé"));

        if (employeComplet1 != null) {
            employeCompletRepository.delete(employeComplet1);
        }
    }

    public EmployeComplet update(String id, EmployeComplet newData, MultipartFile photo) throws IOException {

        // 🔍 1. Vérification de l'existence de l'employé
        EmployeComplet existing = employeCompletRepository.findByAgentId(id)
                .orElseThrow(() -> new RuntimeException("Employé introuvable"));

        // 🟦 2. Copie des champs simples (merge manuel)
        existing.setAgentId(newData.getAgentId());
        existing.setMatricule(newData.getMatricule());
        existing.setPrenom(newData.getPrenom());
        existing.setNom(newData.getNom());
        existing.setSexe(newData.getSexe());
        existing.setDateNaissance(newData.getDateNaissance());
        existing.setLieuNaissance(newData.getLieuNaissance());
        existing.setNationalite(newData.getNationalite());
        existing.setEtatCivil(newData.getEtatCivil());
        existing.setAdresse(newData.getAdresse());
        existing.setVille(newData.getVille());
        existing.setTelephone1(newData.getTelephone1());
        existing.setTelephone2(newData.getTelephone2());
        existing.setEmail(newData.getEmail());
        existing.setContactUrgence(newData.getContactUrgence());
        existing.setLienDeParenteAvecContactUrgence(newData.getLienDeParenteAvecContactUrgence());
        existing.setTelephoneUrgent(newData.getTelephoneUrgent());
        existing.setAgence(newData.getAgence());
        existing.setCodeSite(newData.getCodeSite());
        existing.setVilleSite(newData.getVilleSite());
        existing.setChefEquipe(newData.getChefEquipe());
        existing.setManagerOps(newData.getManagerOps());
        existing.setPoste(newData.getPoste());
        existing.setTypeContrat(newData.getTypeContrat());
        existing.setDateEmbauche(newData.getDateEmbauche());
        existing.setDateFinContrat(newData.getDateFinContrat());
        existing.setTempsDeTravail(newData.getTempsDeTravail());
        existing.setHoraire(newData.getHoraire());
        existing.setSalaireDeBase(newData.getSalaireDeBase());
        existing.setPrimeTransport(newData.getPrimeTransport());
        existing.setPrimeAssiduite(newData.getPrimeAssiduite());
        existing.setPrimeRisque(newData.getPrimeRisque());
        existing.setRibCompteBancaire(newData.getRibCompteBancaire());
        existing.setBanque(newData.getBanque());
        existing.setCnssOuIpres(newData.getCnssOuIpres());
        existing.setIpmNumero(newData.getIpmNumero());
        existing.setPermisConduire(newData.getPermisConduire());
        existing.setCategoriePermis(newData.getCategoriePermis());
        existing.setStatut(newData.getStatut());
        existing.setMotifSortie(newData.getMotifSortie());
        existing.setDateSortie(newData.getDateSortie());
        existing.setObservations(newData.getObservations());

        // 🟩 3. Gestion de la photo (si envoyée)
        if (photo != null && !photo.isEmpty()) {
            existing.setPhoto(photo.getBytes());
        }

        // 🟧 4. Sauvegarde en base
        return employeCompletRepository.save(existing);
    }


}
