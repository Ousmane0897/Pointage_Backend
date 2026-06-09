package com.example.Pointage_Cleanic.services.terrain;

import com.example.Pointage_Cleanic.Dto.terrain.PointageTerrainDto;
import com.example.Pointage_Cleanic.Enum.terrain.StatutPointage;
import com.example.Pointage_Cleanic.Enum.terrain.TypePointage;
import com.example.Pointage_Cleanic.Mapper.terrain.PointageTerrainMapper;
import com.example.Pointage_Cleanic.entities.rh.DossierEmploye;
import com.example.Pointage_Cleanic.entities.terrain.CoordonneesGps;
import com.example.Pointage_Cleanic.entities.terrain.PointageTerrain;
import com.example.Pointage_Cleanic.entities.terrain.PositionGps;
import com.example.Pointage_Cleanic.entities.terrain.SiteClient;
import com.example.Pointage_Cleanic.repositories.terrain.PointageTerrainRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Robustesse de la vérification GPS du pointage pour les sites SANS coordonnées
 * (cas nominal depuis que le front ne saisit plus le GPS).
 */
@ExtendWith(MockitoExtension.class)
class PointageTerrainServiceTest {

    @Mock private PointageTerrainRepository repository;
    @Mock private PointageTerrainMapper mapper;
    @Mock private MongoTemplate mongoTemplate;
    @Mock private ReferentielRhService referentielRh;
    @Mock private SiteClientService siteService;
    @Mock private HaversineService haversine;
    @Mock private AlerteService alerteService;
    @Mock private TerrainNotificationService notification;

    @InjectMocks
    private PointageTerrainService service;

    private PointageTerrain entityAvecAgent(PositionGps pos) {
        return PointageTerrain.builder()
                .employeId("e1")
                .siteId("s1")
                .type(TypePointage.ENTREE)
                .positionAgent(pos)
                .build();
    }

    private DossierEmploye agentExploitation() {
        return DossierEmploye.builder()
                .id("e1").matricule("M001").prenom("Awa").nom("Ndiaye")
                .departement("Opération")
                .build();
    }

    @Test
    void pointage_site_sans_coordonnees_renvoie_gps_indisponible() {
        PositionGps pos = PositionGps.builder().latitude(14.7).longitude(-17.4).precisionM(5.0).build();
        PointageTerrain entity = entityAvecAgent(pos);

        PointageTerrainDto dto = new PointageTerrainDto();
        when(mapper.toEntity(dto)).thenReturn(entity);
        when(referentielRh.valideEtCharge("e1")).thenReturn(agentExploitation());
        // Site sans GPS : coordonnees == null, rayonToleranceM == null
        when(siteService.loadOrThrow("s1")).thenReturn(
                SiteClient.builder().code("S1").nom("Site Sans GPS").build());
        when(repository.save(any(PointageTerrain.class))).thenAnswer(inv -> inv.getArgument(0));
        when(mapper.toDto(any(PointageTerrain.class))).thenReturn(new PointageTerrainDto());

        service.create(dto);

        // Statut neutre, pas de HORS_ZONE, pas de NPE
        assertThat(entity.getStatut()).isEqualTo(StatutPointage.GPS_INDISPONIBLE);
        assertThat(entity.getDistanceM()).isNull();
        // Aucun calcul de distance ni alerte hors-zone pour un site sans coordonnées
        verifyNoInteractions(haversine);
        verify(alerteService, never())
                .creerAlerte(any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void pointage_site_avec_coordonnees_dans_rayon_renvoie_sur_site() {
        PositionGps pos = PositionGps.builder().latitude(14.6928).longitude(-17.4467).precisionM(5.0).build();
        PointageTerrain entity = entityAvecAgent(pos);

        PointageTerrainDto dto = new PointageTerrainDto();
        when(mapper.toEntity(dto)).thenReturn(entity);
        when(referentielRh.valideEtCharge("e1")).thenReturn(agentExploitation());
        when(siteService.loadOrThrow("s1")).thenReturn(SiteClient.builder()
                .code("S1").nom("Site Geofence")
                .coordonnees(CoordonneesGps.builder().latitude(14.6928).longitude(-17.4467).build())
                .rayonToleranceM(100)
                .build());
        when(haversine.distanceM(any(Double.class), any(Double.class), any(Double.class), any(Double.class)))
                .thenReturn(10.0);
        when(repository.save(any(PointageTerrain.class))).thenAnswer(inv -> inv.getArgument(0));
        when(mapper.toDto(any(PointageTerrain.class))).thenReturn(new PointageTerrainDto());

        service.create(dto);

        assertThat(entity.getStatut()).isEqualTo(StatutPointage.SUR_SITE);
        verify(alerteService, never())
                .creerAlerte(any(), any(), any(), any(), any(), any(), any(), any(), any());
    }
}
