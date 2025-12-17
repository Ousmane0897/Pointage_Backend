package com.example.Pointage_Cleanic.services;

import com.example.Pointage_Cleanic.Enum.StatutCommande;
import com.example.Pointage_Cleanic.entities.besoins.BesoinProduit;
import com.example.Pointage_Cleanic.entities.besoins.CollecteBesoins;
import com.example.Pointage_Cleanic.repositories.CollecteBesoinRepository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.InjectMocks;
import org.mockito.Mock;

import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

@ExtendWith(MockitoExtension.class)
class CollecteBesoinServiceTest {

    @Mock
    private CollecteBesoinRepository repository;

    @InjectMocks
    private CollecteBesoinService service;



    // -------------------------------------------------------------
    // TEST creerDemande()
    // -------------------------------------------------------------
    @Test
    void testCreerDemande() {

        CollecteBesoins demande = new CollecteBesoins();
        demande.setProduitsDemandes(List.of(new BesoinProduit("P1", "Prod1", 5)));

        when(repository.save(any(CollecteBesoins.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CollecteBesoins result = service.creerDemande(demande, "OUSMANE");

        assertEquals(StatutCommande.EN_ATTENTE, result.getStatut());
        assertNotNull(result.getDateDemande());
        assertNotNull(result.getHistoriqueModifications());
        assertEquals(1, result.getHistoriqueModifications().size());
        verify(repository).save(any(CollecteBesoins.class));
    }



    // -------------------------------------------------------------
    // TEST getAll()
    // -------------------------------------------------------------
    @Test
    void testGetAll() {
        when(repository.findAll()).thenReturn(List.of(new CollecteBesoins(), new CollecteBesoins()));

        List<CollecteBesoins> result = service.getAll();

        assertEquals(2, result.size());
        verify(repository).findAll();
    }



    // -------------------------------------------------------------
    // TEST getDemandesDuMois()
    // -------------------------------------------------------------
    @Test
    void testGetDemandesDuMois() {

        String mois = LocalDate.now().getMonth().name().toLowerCase();

        when(repository.findByMoisActuel(anyString())).thenReturn(List.of(new CollecteBesoins()));

        List<CollecteBesoins> result = service.getDemandesDuMois();

        assertEquals(1, result.size());
        verify(repository).findByMoisActuel(anyString());
    }




    // -------------------------------------------------------------
    // TEST getById()
    // -------------------------------------------------------------
    @Test
    void testGetById_found() {
        CollecteBesoins d = new CollecteBesoins();
        when(repository.findById("123")).thenReturn(Optional.of(d));

        CollecteBesoins result = service.getById("123");

        assertNotNull(result);
    }

    @Test
    void testGetById_notFound() {
        when(repository.findById("123")).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> service.getById("123"));
    }



    // -------------------------------------------------------------
    // TEST getByDestination()
    // -------------------------------------------------------------
    @Test
    void testGetByDestination() {
        when(repository.findByDestination("Dakar")).thenReturn(List.of(new CollecteBesoins()));

        List<CollecteBesoins> result = service.getByDestination("Dakar");

        assertEquals(1, result.size());
        verify(repository).findByDestination("Dakar");
    }



    // -------------------------------------------------------------
    // TEST modifierDemande()
    // -------------------------------------------------------------
    @Test
    void testModifierDemande_success() {

        CollecteBesoins ancien = new CollecteBesoins();
        ancien.setStatut(StatutCommande.EN_ATTENTE);
        ancien.setHistoriqueModifications(new ArrayList<>());
        ancien.setNombreModifications(0);

        CollecteBesoins nouvelleVersion = new CollecteBesoins();
        nouvelleVersion.setDestination("Nouvelle Dest");
        nouvelleVersion.setResponsable("Resp X");
        nouvelleVersion.setProduitsDemandes(List.of(new BesoinProduit("P2", "Prod2", 3)));

        when(repository.findById("id123")).thenReturn(Optional.of(ancien));
        when(repository.save(any(CollecteBesoins.class))).thenAnswer(inv -> inv.getArgument(0));

        CollecteBesoins result = service.modifierDemande("id123", nouvelleVersion, "Superviseur X");

        assertEquals("Nouvelle Dest", result.getDestination());
        assertEquals(1, result.getNombreModifications());
        assertTrue(result.getHistoriqueModifications().get(0).contains("validée par Superviseur X"));
        verify(repository).save(any());
    }


    @Test
    void testModifierDemande_impossibleSiLivree() {

        CollecteBesoins ancien = new CollecteBesoins();
        ancien.setStatut(StatutCommande.LIVREE);

        when(repository.findById("idX")).thenReturn(Optional.of(ancien));

        assertThrows(RuntimeException.class,
                () -> service.modifierDemande("idX", new CollecteBesoins(), "X"));
    }



    // -------------------------------------------------------------
    // TEST updateStatut()
    // -------------------------------------------------------------
    @Test
    void testUpdateStatut_success() {

        CollecteBesoins d = new CollecteBesoins();
        d.setStatut(StatutCommande.EN_ATTENTE);
        d.setHistoriqueModifications(new ArrayList<>());

        when(repository.findById("777")).thenReturn(Optional.of(d));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CollecteBesoins result = service.updateStatut("777", StatutCommande.EN_COURS, "Ousmane");

        assertEquals(StatutCommande.EN_COURS, result.getStatut());
        assertEquals(1, result.getHistoriqueModifications().size());
        verify(repository).save(any());
    }


    @Test
    void testUpdateStatut_blockIfAlreadyLivree() {

        CollecteBesoins d = new CollecteBesoins();
        d.setStatut(StatutCommande.LIVREE);

        when(repository.findById("99")).thenReturn(Optional.of(d));

        assertThrows(RuntimeException.class,
                () -> service.updateStatut("99", StatutCommande.EN_COURS, "X"));
    }



    // -------------------------------------------------------------
    // TEST getHistorique()
    // -------------------------------------------------------------
    @Test
    void testGetHistorique() {

        CollecteBesoins d = new CollecteBesoins();
        d.setHistoriqueModifications(List.of("log1", "log2"));

        when(repository.findById("H1")).thenReturn(Optional.of(d));

        List<String> logs = service.getHistorique("H1");

        assertEquals(2, logs.size());
        verify(repository).findById("H1");
    }



    // -------------------------------------------------------------
    // TEST getHistoriques()
    // -------------------------------------------------------------
    @Test
    void testGetHistoriques() {
        when(repository.findByStatut(StatutCommande.LIVREE))
                .thenReturn(List.of(new CollecteBesoins()));

        List<CollecteBesoins> result = service.getHistoriques();

        assertEquals(1, result.size());
        verify(repository).findByStatut(StatutCommande.LIVREE);
    }

}
