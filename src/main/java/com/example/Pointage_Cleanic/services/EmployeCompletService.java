package com.example.Pointage_Cleanic.services;


import com.example.Pointage_Cleanic.entities.EmployeComplet;
import com.example.Pointage_Cleanic.repositories.EmployeCompletRepository;
import jdk.jfr.Registered;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

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

    public EmployeComplet update(String agentId, EmployeComplet employeComplet) {

        EmployeComplet employeComplet1 = employeCompletRepository.findByAgentId(agentId).orElseThrow(() -> new RuntimeException("Agent non trouvé"));

        if (employeComplet1 != null) {

            employeComplet1.setPrenom(employeComplet.getPrenom());
            employeComplet1.setNom(employeComplet.getNom());
            employeComplet1.setSexe(employeComplet.getSexe());
            employeComplet1.setDateNaissance(employeComplet.getDateNaissance());
            employeComplet1.setLieuNaissance(employeComplet.getLieuNaissance());
            employeComplet1.setNationalite(employeComplet.getNationalite());
            employeComplet1.setEtatCivil(employeComplet.getEtatCivil());
            employeComplet1.setAdresse(employeComplet.getAdresse());
            employeComplet1.setVille(employeComplet.getVille());
            employeComplet1.setTelephone1(employeComplet.getTelephone1());
            employeComplet1.setTelephone2(employeComplet.getTelephone2());
            employeComplet1.setEmail(employeComplet.getEmail());
            employeComplet1.setContactUrgence(employeComplet.getContactUrgence());
            employeComplet1.setLienDeParenteAvecContactUrgence(employeComplet.getLienDeParenteAvecContactUrgence());
            employeComplet1.setTelephoneUrgent(employeComplet.getTelephoneUrgent());
            employeComplet1.setAgence(employeComplet.getAgence());
            employeComplet1.setCodeSite(employeComplet.getCodeSite());
            employeComplet1.setVilleSite(employeComplet.getVilleSite());
            employeComplet1.setChefEquipe(employeComplet.getChefEquipe());
            employeComplet1.setManagerOps(employeComplet.getManagerOps());
            employeComplet1.setPoste(employeComplet.getPoste());
            employeComplet1.setTypeContrat(employeComplet.getTypeContrat());
            employeComplet1.setDateEmbauche(employeComplet.getDateEmbauche());
            employeComplet1.setDateFinContrat(employeComplet.getDateFinContrat());
            employeComplet1.setTempsDeTravail(employeComplet.getTempsDeTravail());
            employeComplet1.setHoraire(employeComplet.getHoraire());
            employeComplet1.setSalaireDeBase(employeComplet.getSalaireDeBase());
            employeComplet1.setPrimeTransport(employeComplet.getPrimeTransport());
            employeComplet1.setPrimeAssiduite(employeComplet.getPrimeAssiduite());
            employeComplet1.setPrimeRisque(employeComplet.getPrimeRisque());
            employeComplet1.setRibCompteBancaire(employeComplet.getRibCompteBancaire());
            employeComplet1.setBanque(employeComplet.getBanque());
            employeComplet1.setCnssOuIpres(employeComplet.getCnssOuIpres());
            employeComplet1.setIpmNumero(employeComplet.getIpmNumero());
            employeComplet1.setPermisConduire(employeComplet.getPermisConduire());
            employeComplet1.setCategoriePermis(employeComplet.getCategoriePermis());
            employeComplet1.setStatut(employeComplet.getStatut());
            employeComplet1.setMotifSortie(employeComplet.getMotifSortie());
            employeComplet1.setDateSortie(employeComplet.getDateSortie());
            employeComplet1.setObservations(employeComplet.getObservations());



        }

        return employeComplet1;

    }


}
