package com.example.Pointage_Cleanic.services;

import com.example.Pointage_Cleanic.Dto.EmployeCompletDto;
import com.example.Pointage_Cleanic.Dto.ImportEmployeResponse;
import com.example.Pointage_Cleanic.Mapper.EmployeCompletMapper;
import com.example.Pointage_Cleanic.Mapper.EmployeMapper;
import com.example.Pointage_Cleanic.entities.Employe;
import com.example.Pointage_Cleanic.entities.EmployeComplet;
import com.example.Pointage_Cleanic.exception.EmployeAlreadyExistsException;
import com.example.Pointage_Cleanic.repositories.EmployeCompletRepository;
import com.example.Pointage_Cleanic.repositories.EmployeRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(org.mockito.junit.jupiter.MockitoExtension.class)
class EmployeCompletServiceTest {

    @InjectMocks
    private EmployeCompletService service;

    @Mock
    private EmployeCompletRepository employeCompletRepository;

    @Mock
    private EmployeRepository employeRepository;

    @Mock
    private EmployeCompletMapper employeCompletMapper;

    @Mock
    private EmployeMapper employeMapper;

    // ==========================================================
    // 🧪 CREATE
    // ==========================================================

    @Test
    void should_create_employe_successfully() throws Exception {

        EmployeCompletDto dto = new EmployeCompletDto();
        dto.setAgentId("AG-0001");
        dto.setPrenom("Mamadou");
        dto.setNom("Diop");
        dto.setMatricule("MAT001");

        EmployeComplet complet = new EmployeComplet();
        Employe employe = new Employe();

        when(employeCompletRepository.existsByAgentId(any())).thenReturn(false);
        when(employeCompletRepository.existsByMatricule(any())).thenReturn(false);
        when(employeCompletRepository.existsByNomComplet(any())).thenReturn(false);

        when(employeCompletMapper.toEntity(dto)).thenReturn(complet);
        when(employeMapper.toEmploye(dto)).thenReturn(employe);
        when(employeCompletRepository.save(any())).thenReturn(complet);

        EmployeComplet result = service.create(dto, null, null);

        assertNotNull(result);
        verify(employeCompletRepository).save(any());
        verify(employeRepository).save(any());
    }

    @Test
    void should_throw_exception_when_agentId_exists() {

        EmployeCompletDto dto = new EmployeCompletDto();
        dto.setAgentId("AG-0001");

        when(employeCompletRepository.existsByAgentId("AG-0001")).thenReturn(true);

        assertThrows(EmployeAlreadyExistsException.class,
                () -> service.create(dto, null, null));
    }

    // ==========================================================
    // 🧪 IMPORT MANY
    // ==========================================================

    @Test
    void should_import_many_with_success_and_errors() {

        EmployeCompletDto ok = new EmployeCompletDto();
        ok.setAgentId("AG-001");
        ok.setMatricule("MAT-1");
        ok.setPrenom("Mamadou");
        ok.setNom("Diop");

        EmployeCompletDto fail = new EmployeCompletDto();
        fail.setAgentId("AG-002");
        fail.setMatricule("MAT-2");
        fail.setPrenom("Fatou");
        fail.setNom("Sarr");

        // Cas succès
        when(employeCompletRepository.existsByAgentId("AG-001")).thenReturn(false);
        when(employeCompletRepository.existsByMatricule("MAT-1")).thenReturn(false);
        when(employeCompletRepository.existsByNomComplet("MAMADOU DIOP")).thenReturn(false);

        // Cas erreur
        when(employeCompletRepository.existsByAgentId("AG-002")).thenReturn(true);

        EmployeComplet complet = new EmployeComplet();
        Employe employe = new Employe();

        when(employeCompletMapper.toEntity(ok)).thenReturn(complet);
        when(employeMapper.toEmploye(ok)).thenReturn(employe);
        when(employeCompletRepository.save(any())).thenReturn(complet);
        when(employeRepository.save(any())).thenReturn(employe);

        ImportEmployeResponse response = service.importMany(List.of(ok, fail));

        assertEquals(1, response.success().size());
        assertEquals(1, response.errors().size());
    }




    // ==========================================================
    // 🧪 DELETE
    // ==========================================================

    @Test
    void should_delete_employe_complet_and_simple() {

        EmployeComplet complet = new EmployeComplet();
        Employe employe = new Employe();

        when(employeCompletRepository.findByAgentId("AG-1")).thenReturn(Optional.of(complet));
        when(employeRepository.findByAgentId("AG-1")).thenReturn(Optional.of(employe));

        service.delete("AG-1");

        verify(employeCompletRepository).delete(complet);
        verify(employeRepository).delete(employe);
    }

    // ==========================================================
    // 🧪 UPDATE
    // ==========================================================

    @Test
    void should_update_employe_successfully() throws Exception {

        EmployeComplet existing = new EmployeComplet();
        Employe employe = new Employe();
        EmployeCompletDto dto = new EmployeCompletDto();

        when(employeCompletRepository.findByAgentId("AG-1")).thenReturn(Optional.of(existing));
        when(employeRepository.findByAgentId("AG-1")).thenReturn(Optional.of(employe));
        when(employeCompletRepository.save(any())).thenReturn(existing);

        EmployeComplet updated = service.update("AG-1", dto, null);

        assertNotNull(updated);
        verify(employeCompletMapper).updateEntityFromDto(dto, existing);
        verify(employeMapper).updateEmployeFromDto(dto, employe);
    }
}
