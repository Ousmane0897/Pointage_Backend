package com.example.Pointage_Cleanic.controllers.terrain;

import com.example.Pointage_Cleanic.Dto.pointage.AgenceQrDto;
import com.example.Pointage_Cleanic.services.pointage.AgenceQrAdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * QR codes de pointage des agences ({@code SiteClient}). Même préfixe que
 * {@link SitesClientsController} (donc authentifié), contrôleur séparé pour ne pas
 * alourdir celui du référentiel. Habilitation RH / SUPERADMIN vérifiée dans le service.
 */
@RestController
@RequestMapping("/api/terrain/sites-clients")
@RequiredArgsConstructor
public class AgencesQrController {

    private final AgenceQrAdminService service;

    @GetMapping("/qr-codes")
    public List<AgenceQrDto> lister() {
        return service.lister();
    }

    @PostMapping("/{id}/qr/regenerer")
    public AgenceQrDto regenerer(@PathVariable String id) {
        return service.regenerer(id);
    }
}
