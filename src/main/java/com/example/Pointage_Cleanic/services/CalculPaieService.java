package com.example.Pointage_Cleanic.services;

import com.example.Pointage_Cleanic.Enum.NatureLigne;
import com.example.Pointage_Cleanic.Enum.RegimeIpres;
import com.example.Pointage_Cleanic.Enum.StatutBulletin;
import com.example.Pointage_Cleanic.Enum.StatutValidationHS;
import com.example.Pointage_Cleanic.entities.*;
import com.example.Pointage_Cleanic.exception.ResourceNotFoundException;
import com.example.Pointage_Cleanic.repositories.CategorieProfessionnelleRepository;
import com.example.Pointage_Cleanic.repositories.EmployeCompletRepository;
import com.example.Pointage_Cleanic.repositories.HeureSupplementaireRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

/**
 * Moteur de calcul de la paie — méthodes unitaires pures et testables.
 * Les paramètres (taux, plafonds, barème IR) proviennent de la collection
 * parametres_paie via ParametresPaieService.
 */
@Service
@RequiredArgsConstructor
public class CalculPaieService {

    public static final String CODE_SAL_BASE = "SAL_BASE";
    public static final String CODE_PRIME_PREFIX = "PRIME_";
    public static final String CODE_INDEM_PREFIX = "INDEM_";
    public static final String CODE_HS = "HS_TOTAL";
    public static final String CODE_IPRES_GEN_SAL = "IPRES_GEN_SAL";
    public static final String CODE_IPRES_GEN_EMP = "IPRES_GEN_EMP";
    public static final String CODE_IPRES_COMP_SAL = "IPRES_COMP_SAL";
    public static final String CODE_IPRES_COMP_EMP = "IPRES_COMP_EMP";
    public static final String CODE_CSS_PF_EMP = "CSS_PF_EMP";
    public static final String CODE_CSS_ATMP_EMP = "CSS_ATMP_EMP";
    public static final String CODE_IR = "IR";
    public static final String CODE_TRIMF = "TRIMF";

    private static final double HEURES_PAR_JOUR_STANDARD = 8.0;

    private final EmployeCompletRepository employeCompletRepository;
    private final CategorieProfessionnelleRepository categorieProfessionnelleRepository;
    private final ParametresPaieService parametresPaieService;
    private final HeureSupplementaireRepository heureSupplementaireRepository;
    private final RecapitulatifMensuelService recapitulatifMensuelService;

    // ─── Composantes du brut ────────────────────────────────────────────

    public long calculSalaireBaseProrata(long salaireBase, int joursTravailles, int joursOuvrables) {
        if (joursOuvrables <= 0) return 0L;
        if (joursTravailles >= joursOuvrables) return salaireBase;
        return Math.round((double) salaireBase * joursTravailles / joursOuvrables);
    }

    public long calculMontantHeuresSup(long salaireBase, int joursOuvrablesStandard,
                                        double heuresSupTotal, double heuresSupMajoreesEquivalent) {
        if (joursOuvrablesStandard <= 0 || salaireBase <= 0) return 0L;
        double tauxHoraire = (double) salaireBase / (joursOuvrablesStandard * HEURES_PAR_JOUR_STANDARD);
        double heuresAPayer = heuresSupTotal + heuresSupMajoreesEquivalent;
        return Math.round(heuresAPayer * tauxHoraire);
    }

    public long somme(List<Long> montants) {
        return montants.stream().filter(m -> m != null).mapToLong(Long::longValue).sum();
    }

    // ─── IPRES ───────────────────────────────────────────────────────────

    public long calculAssietteIpresGeneral(long baseSoumise, ParametresPaie p) {
        return Math.min(baseSoumise, p.getPlafondIpresGeneral());
    }

    public long calculAssietteIpresComplementaire(long baseSoumise, ParametresPaie p) {
        if (baseSoumise <= p.getPlafondIpresGeneral()) return 0L;
        long excedent = baseSoumise - p.getPlafondIpresGeneral();
        long plafondComp = p.getPlafondIpresComplementaire() - p.getPlafondIpresGeneral();
        return Math.min(excedent, plafondComp);
    }

    public long calculIpresGeneralSalarie(long baseSoumise, ParametresPaie p) {
        return Math.round(calculAssietteIpresGeneral(baseSoumise, p) * p.getTauxIpresGeneralSalarie());
    }

    public long calculIpresGeneralEmployeur(long baseSoumise, ParametresPaie p) {
        return Math.round(calculAssietteIpresGeneral(baseSoumise, p) * p.getTauxIpresGeneralEmployeur());
    }

    public long calculIpresComplementaireSalarie(long baseSoumise, ParametresPaie p) {
        return Math.round(calculAssietteIpresComplementaire(baseSoumise, p) * p.getTauxIpresComplementaireSalarie());
    }

    public long calculIpresComplementaireEmployeur(long baseSoumise, ParametresPaie p) {
        return Math.round(calculAssietteIpresComplementaire(baseSoumise, p) * p.getTauxIpresComplementaireEmployeur());
    }

    // ─── CSS ─────────────────────────────────────────────────────────────

    public long calculAssietteCss(long baseSoumise, ParametresPaie p) {
        return Math.min(baseSoumise, p.getPlafondCss());
    }

    public long calculCssPrestationsFamiliales(long baseSoumise, ParametresPaie p) {
        return Math.round(calculAssietteCss(baseSoumise, p) * p.getTauxCssPrestationsFamilialesEmployeur());
    }

    public long calculCssAtMp(long baseSoumise, Double tauxOverride, ParametresPaie p) {
        double taux = tauxOverride != null ? tauxOverride : p.getTauxAtMpDefaut();
        return Math.round(calculAssietteCss(baseSoumise, p) * taux);
    }

    // ─── Impôt sur le revenu (barème progressif) ────────────────────────

    public long calculIrSurBaseAnnuelle(long baseAnnuelle, ParametresPaie p) {
        if (baseAnnuelle <= 0 || p.getBaremeIr() == null || p.getBaremeIr().isEmpty()) return 0L;

        long total = 0L;
        for (TrancheIr tranche : p.getBaremeIr()) {
            long borneMin = tranche.getBorneMin() != null ? tranche.getBorneMin() : 0L;
            Long borneMax = tranche.getBorneMax();
            double taux = tranche.getTaux() != null ? tranche.getTaux() : 0.0;

            if (baseAnnuelle < borneMin) break;
            long plafondTranche = borneMax != null ? Math.min(baseAnnuelle, borneMax) : baseAnnuelle;
            long montantTranche = plafondTranche - borneMin + 1;
            if (montantTranche <= 0) continue;
            total += Math.round(montantTranche * taux);
        }
        return total;
    }

    public long calculIrMensuel(long brutImposableMensuel, long cotisationsSalarialesDeductibles, ParametresPaie p) {
        long baseMensuelle = Math.max(0L, brutImposableMensuel - cotisationsSalarialesDeductibles);
        long baseAnnuelle = baseMensuelle * 12;
        long irAnnuel = calculIrSurBaseAnnuelle(baseAnnuelle, p);
        return Math.round(irAnnuel / 12.0);
    }

    public long calculTrimfMensuel(ParametresPaie p) {
        return p.getMontantTrimfMensuel() != null ? p.getMontantTrimfMensuel() : 0L;
    }

    // ─── Orchestration : compose un BulletinPaie complet ─────────────────

    public BulletinPaie orchestrer(String employeId, int mois, int annee) {
        EmployeComplet emp = employeCompletRepository.findById(employeId)
                .orElseThrow(() -> new ResourceNotFoundException("Employé introuvable : " + employeId));

        if (emp.getCategorieCode() == null || emp.getCategorieCode().isBlank()) {
            throw new IllegalStateException(
                    "L'employé " + emp.getMatricule() + " n'est rattaché à aucune catégorie professionnelle.");
        }

        CategorieProfessionnelle cat = categorieProfessionnelleRepository.findByCode(emp.getCategorieCode())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Catégorie introuvable : " + emp.getCategorieCode()));

        ParametresPaie params = parametresPaieService.loadEntity();

        RecapitulatifMensuelService.LigneRecapDto recap = recapitulatifMensuelService
                .getRecapitulatif(mois, annee, "").stream()
                .filter(l -> l.getEmployeId().equals(emp.getId()))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Récapitulatif mensuel introuvable pour " + emp.getMatricule()));

        YearMonth ym = YearMonth.of(annee, mois);
        List<HeureSupplementaire> hsValidees = heureSupplementaireRepository
                .findByEmployeIdAndDateBetween(emp.getId(), ym.atDay(1), ym.atEndOfMonth())
                .stream().filter(hs -> hs.getStatut() == StatutValidationHS.VALIDEE).toList();

        double heuresSupTotal = hsValidees.stream()
                .mapToDouble(hs -> hs.getNombreHeures() != null ? hs.getNombreHeures() : 0).sum();
        double heuresSupMajoreesEquivalent = hsValidees.stream()
                .mapToDouble(hs -> hs.getHeuresMajoreesEquivalent() != null ? hs.getHeuresMajoreesEquivalent() : 0).sum();

        int joursOuvrablesStandard = params.getJoursOuvrablesStandardMois() != null
                ? params.getJoursOuvrablesStandardMois() : 22;
        long salaireBase = cat.getSalaireBase() != null ? cat.getSalaireBase() : 0L;

        List<LigneBulletin> lignes = new ArrayList<>();

        // ── 1. Lignes de gains ──
        long salaireBaseProrata = calculSalaireBaseProrata(
                salaireBase, recap.getJoursPresents(), recap.getJoursOuvrables());
        lignes.add(LigneBulletin.builder()
                .code(CODE_SAL_BASE).libelle("Salaire de base").nature(NatureLigne.GAIN)
                .base(salaireBase).montantSalarial(salaireBaseProrata).build());

        long totalPrimes = 0L, primesImposables = 0L, primesSoumisesIpres = 0L, primesSoumisesCss = 0L;
        if (cat.getPrimes() != null) {
            for (PrimeCategorie pr : cat.getPrimes()) {
                long montant = pr.getMontant() != null ? pr.getMontant() : 0L;
                totalPrimes += montant;
                if (pr.isImposable()) primesImposables += montant;
                if (pr.isSoumiseIpres()) primesSoumisesIpres += montant;
                if (pr.isSoumiseCss()) primesSoumisesCss += montant;
                lignes.add(LigneBulletin.builder()
                        .code(CODE_PRIME_PREFIX + normaliseCode(pr.getLibelle()))
                        .libelle(pr.getLibelle()).nature(NatureLigne.GAIN)
                        .montantSalarial(montant).build());
            }
        }

        long totalIndemnites = 0L, indemnitesImposables = 0L;
        if (cat.getIndemnites() != null) {
            for (IndemniteCategorie in : cat.getIndemnites()) {
                long montant = in.getMontant() != null ? in.getMontant() : 0L;
                totalIndemnites += montant;
                if (in.isImposable()) indemnitesImposables += montant;
                lignes.add(LigneBulletin.builder()
                        .code(CODE_INDEM_PREFIX + normaliseCode(in.getLibelle()))
                        .libelle(in.getLibelle()).nature(NatureLigne.GAIN)
                        .montantSalarial(montant).build());
            }
        }

        long montantHs = calculMontantHeuresSup(
                salaireBase, joursOuvrablesStandard, heuresSupTotal, heuresSupMajoreesEquivalent);
        if (montantHs > 0) {
            lignes.add(LigneBulletin.builder()
                    .code(CODE_HS).libelle("Heures supplémentaires").nature(NatureLigne.GAIN)
                    .base(Math.round(heuresSupTotal * 100)).montantSalarial(montantHs).build());
        }

        long salaireBrut = salaireBaseProrata + totalPrimes + totalIndemnites + montantHs;
        long baseImposable = salaireBaseProrata + primesImposables + indemnitesImposables + montantHs;
        long baseSoumiseIpres = salaireBaseProrata + primesSoumisesIpres + montantHs;
        long baseSoumiseCss = salaireBaseProrata + primesSoumisesCss + montantHs;

        // ── 2. IPRES salarié & employeur ──
        long ipresGenSal = calculIpresGeneralSalarie(baseSoumiseIpres, params);
        long ipresGenEmp = calculIpresGeneralEmployeur(baseSoumiseIpres, params);
        long assietteIpresGen = calculAssietteIpresGeneral(baseSoumiseIpres, params);
        lignes.add(ligneCotisation(CODE_IPRES_GEN_SAL, "IPRES Régime Général (part salarié)",
                NatureLigne.RETENUE_SALARIALE, assietteIpresGen, params.getTauxIpresGeneralSalarie(),
                ipresGenSal, 0L));
        lignes.add(ligneCotisation(CODE_IPRES_GEN_EMP, "IPRES Régime Général (part employeur)",
                NatureLigne.COTISATION_PATRONALE, assietteIpresGen, params.getTauxIpresGeneralEmployeur(),
                0L, ipresGenEmp));

        long ipresCompSal = 0L, ipresCompEmp = 0L;
        if (cat.getRegimeIpres() == RegimeIpres.REGIME_COMPLEMENTAIRE
                && baseSoumiseIpres > params.getPlafondIpresGeneral()) {
            ipresCompSal = calculIpresComplementaireSalarie(baseSoumiseIpres, params);
            ipresCompEmp = calculIpresComplementaireEmployeur(baseSoumiseIpres, params);
            long assietteComp = calculAssietteIpresComplementaire(baseSoumiseIpres, params);
            lignes.add(ligneCotisation(CODE_IPRES_COMP_SAL, "IPRES Complémentaire (part salarié)",
                    NatureLigne.RETENUE_SALARIALE, assietteComp, params.getTauxIpresComplementaireSalarie(),
                    ipresCompSal, 0L));
            lignes.add(ligneCotisation(CODE_IPRES_COMP_EMP, "IPRES Complémentaire (part employeur)",
                    NatureLigne.COTISATION_PATRONALE, assietteComp, params.getTauxIpresComplementaireEmployeur(),
                    0L, ipresCompEmp));
        }

        // ── 3. CSS employeur (prestations familiales + AT/MP) ──
        long assietteCss = calculAssietteCss(baseSoumiseCss, params);
        long cssPfEmp = calculCssPrestationsFamiliales(baseSoumiseCss, params);
        lignes.add(ligneCotisation(CODE_CSS_PF_EMP, "CSS Prestations Familiales (employeur)",
                NatureLigne.COTISATION_PATRONALE, assietteCss, params.getTauxCssPrestationsFamilialesEmployeur(),
                0L, cssPfEmp));

        long cssAtMpEmp = calculCssAtMp(baseSoumiseCss, cat.getTauxAtMp(), params);
        double tauxAtMpApplique = cat.getTauxAtMp() != null ? cat.getTauxAtMp() : params.getTauxAtMpDefaut();
        lignes.add(ligneCotisation(CODE_CSS_ATMP_EMP, "CSS Accident du Travail / Maladie Pro (employeur)",
                NatureLigne.COTISATION_PATRONALE, assietteCss, tauxAtMpApplique, 0L, cssAtMpEmp));

        // ── 4. Impôt sur le revenu + TRIMF ──
        long totalCotisSalariales = ipresGenSal + ipresCompSal;
        long irMensuel = calculIrMensuel(baseImposable, totalCotisSalariales, params);
        lignes.add(ligneCotisation(CODE_IR, "Impôt sur le revenu", NatureLigne.RETENUE_SALARIALE,
                baseImposable - totalCotisSalariales, null, irMensuel, 0L));

        long trimf = calculTrimfMensuel(params);
        lignes.add(ligneCotisation(CODE_TRIMF, "TRIMF", NatureLigne.RETENUE_SALARIALE,
                null, null, trimf, 0L));

        // ── 5. Totaux & net ──
        long totalCotisPatronales = ipresGenEmp + ipresCompEmp + cssPfEmp + cssAtMpEmp;
        long netAPayer = salaireBrut - totalCotisSalariales - irMensuel - trimf;
        long coutTotalEmployeur = salaireBrut + totalCotisPatronales;

        return BulletinPaie.builder()
                .employeId(emp.getId())
                .matricule(emp.getMatricule())
                .nom(emp.getNom())
                .prenom(emp.getPrenom())
                .poste(emp.getPoste())
                .departement(emp.getAgence() != null && emp.getAgence().length > 0 ? emp.getAgence()[0] : null)
                .categorieCode(cat.getCode())
                .numeroIpres(emp.getCnssOuIpres())
                .numeroCss(emp.getNumeroCss())
                .rib(emp.getRibCompteBancaire())
                .banque(emp.getBanque())
                .periode(PeriodePaie.builder().mois(mois).annee(annee).build())
                .joursTravailles(recap.getJoursPresents())
                .joursAbsence(recap.getJoursAbsents())
                .joursConge(recap.getJoursConge())
                .heuresSupTotal(heuresSupTotal)
                .heuresSupMajoreesEquivalent(heuresSupMajoreesEquivalent)
                .lignes(lignes)
                .salaireBrut(salaireBrut)
                .totalCotisationsSalariales(totalCotisSalariales)
                .totalCotisationsPatronales(totalCotisPatronales)
                .impotRevenu(irMensuel)
                .trimf(trimf)
                .netAPayer(netAPayer)
                .coutTotalEmployeur(coutTotalEmployeur)
                .statut(StatutBulletin.BROUILLON)
                .dateCalcul(LocalDate.now())
                .build();
    }

    private LigneBulletin ligneCotisation(String code, String libelle, NatureLigne nature,
                                          Long base, Double taux, long montantSal, long montantPat) {
        return LigneBulletin.builder()
                .code(code).libelle(libelle).nature(nature)
                .base(base).taux(taux)
                .montantSalarial(montantSal).montantPatronal(montantPat)
                .build();
    }

    private String normaliseCode(String libelle) {
        if (libelle == null) return "AUTRE";
        return libelle.toUpperCase().replaceAll("[^A-Z0-9]+", "_").replaceAll("^_|_$", "");
    }
}