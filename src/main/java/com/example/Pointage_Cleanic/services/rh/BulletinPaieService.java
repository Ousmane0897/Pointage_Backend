package com.example.Pointage_Cleanic.services.rh;

import com.example.Pointage_Cleanic.Dto.rh.BulletinPaieDto;
import com.example.Pointage_Cleanic.Dto.rh.ValiderBulletinRequest;
import com.example.Pointage_Cleanic.Enum.rh.NatureLigne;
import com.example.Pointage_Cleanic.Enum.rh.StatutBulletin;
import com.example.Pointage_Cleanic.Mapper.rh.BulletinPaieMapper;
import com.example.Pointage_Cleanic.entities.rh.AvanceCategorie;
import com.example.Pointage_Cleanic.entities.rh.BulletinPaie;
import com.example.Pointage_Cleanic.entities.rh.LigneBulletin;
import com.example.Pointage_Cleanic.entities.rh.PretCategorie;
import com.example.Pointage_Cleanic.entities.rh.RetenueCategorie;
import com.example.Pointage_Cleanic.exception.ResourceNotFoundException;
import com.example.Pointage_Cleanic.repositories.rh.BulletinPaieRepository;
import com.lowagie.text.*;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BulletinPaieService {

    private final BulletinPaieRepository bulletinPaieRepository;
    private final BulletinPaieMapper bulletinPaieMapper;
    private final CalculPaieService calculPaieService;

    public BulletinPaieDto calculerEtSauvegarder(String employeId, String categorieCode, int mois, int annee) {
        bulletinPaieRepository.findByEmployeIdAndPeriodeMoisAndPeriodeAnnee(employeId, mois, annee)
                .ifPresent(existing -> {
                    if (existing.getStatut() != StatutBulletin.ANNULE) {
                        throw new IllegalStateException(
                                "Un bulletin existe déjà pour cet employé sur cette période (statut "
                                        + existing.getStatut() + ").");
                    }
                });

        BulletinPaie bulletin = calculPaieService.orchestrer(employeId, categorieCode, mois, annee);
        return bulletinPaieMapper.toDto(bulletinPaieRepository.save(bulletin));
    }

    public BulletinPaieDto getById(String id) {
        return bulletinPaieMapper.toDto(bulletinPaieRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Bulletin introuvable : " + id)));
    }

    public List<BulletinPaieDto> getAll(String employeId, String departement, Integer mois,
                                        Integer annee, StatutBulletin statut, String q) {
        return bulletinPaieRepository.findAll().stream()
                .filter(b -> employeId == null || employeId.isBlank() || employeId.equals(b.getEmployeId()))
                .filter(b -> departement == null || departement.isBlank() || departement.equalsIgnoreCase(b.getDepartement()))
                .filter(b -> mois == null || b.getPeriode() != null && b.getPeriode().getMois() == mois)
                .filter(b -> annee == null || b.getPeriode() != null && b.getPeriode().getAnnee() == annee)
                .filter(b -> statut == null || b.getStatut() == statut)
                .filter(b -> {
                    if (q == null || q.isBlank()) return true;
                    String needle = q.toLowerCase();
                    return (b.getNom() != null && b.getNom().toLowerCase().contains(needle))
                            || (b.getPrenom() != null && b.getPrenom().toLowerCase().contains(needle))
                            || (b.getMatricule() != null && b.getMatricule().toLowerCase().contains(needle));
                })
                .map(bulletinPaieMapper::toDto)
                .collect(Collectors.toList());
    }

    public List<BulletinPaieDto> getHistorique(String employeId, int annee) {
        return bulletinPaieRepository
                .findByPeriodeAnneeAndEmployeIdOrderByPeriodeMoisAsc(annee, employeId)
                .stream().map(bulletinPaieMapper::toDto)
                .collect(Collectors.toList());
    }

    public BulletinPaieDto update(String id, BulletinPaieDto dto) {
        BulletinPaie existing = bulletinPaieRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Bulletin introuvable : " + id));
        if (existing.getStatut() != StatutBulletin.BROUILLON) {
            throw new IllegalStateException(
                    "Un bulletin ne peut être modifié qu'à l'état BROUILLON (actuel : " + existing.getStatut() + ").");
        }
        bulletinPaieMapper.updateEntityFromDto(dto, existing);
        return bulletinPaieMapper.toDto(bulletinPaieRepository.save(existing));
    }

    public void delete(String id) {
        BulletinPaie existing = bulletinPaieRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Bulletin introuvable : " + id));
        if (existing.getStatut() != StatutBulletin.BROUILLON) {
            throw new IllegalStateException(
                    "Seuls les bulletins BROUILLON peuvent être supprimés (actuel : " + existing.getStatut() + ").");
        }
        bulletinPaieRepository.delete(existing);
    }

    public BulletinPaieDto valider(String id, ValiderBulletinRequest request) {
        BulletinPaie bulletin = bulletinPaieRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Bulletin introuvable : " + id));
        if (bulletin.getStatut() != StatutBulletin.BROUILLON) {
            throw new IllegalStateException(
                    "Seul un bulletin BROUILLON peut être validé (actuel : " + bulletin.getStatut() + ").");
        }

        bulletin.setStatut(StatutBulletin.VALIDE);
        bulletin.setDateValidation(LocalDate.now());
        if (request != null) {
            bulletin.setValidateurId(request.getValidateurId());
            bulletin.setValidateurNom(request.getValidateurNom());
            if (request.getCommentaire() != null) bulletin.setCommentaire(request.getCommentaire());
        }

        calculerCumulsAnnuels(bulletin);
        return bulletinPaieMapper.toDto(bulletinPaieRepository.save(bulletin));
    }

    public BulletinPaieDto marquerPaye(String id) {
        BulletinPaie bulletin = bulletinPaieRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Bulletin introuvable : " + id));
        if (bulletin.getStatut() != StatutBulletin.VALIDE) {
            throw new IllegalStateException(
                    "Seul un bulletin VALIDE peut être marqué comme payé (actuel : " + bulletin.getStatut() + ").");
        }
        bulletin.setStatut(StatutBulletin.PAYE);
        bulletin.setDatePaiement(LocalDate.now());
        return bulletinPaieMapper.toDto(bulletinPaieRepository.save(bulletin));
    }

    public BulletinPaieDto annuler(String id, String commentaire) {
        BulletinPaie bulletin = bulletinPaieRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Bulletin introuvable : " + id));
        if (bulletin.getStatut() == StatutBulletin.ANNULE) {
            throw new IllegalStateException("Bulletin déjà annulé.");
        }
        bulletin.setStatut(StatutBulletin.ANNULE);
        if (commentaire != null) bulletin.setCommentaire(commentaire);
        return bulletinPaieMapper.toDto(bulletinPaieRepository.save(bulletin));
    }

    private void calculerCumulsAnnuels(BulletinPaie bulletin) {
        if (bulletin.getPeriode() == null) return;
        int annee = bulletin.getPeriode().getAnnee();

        List<BulletinPaie> mois_antérieurs = bulletinPaieRepository
                .findByPeriodeAnneeAndEmployeIdOrderByPeriodeMoisAsc(annee, bulletin.getEmployeId())
                .stream().filter(b -> !b.getId().equals(bulletin.getId()))
                .filter(b -> b.getStatut() == StatutBulletin.VALIDE || b.getStatut() == StatutBulletin.PAYE)
                .toList();

        long cumulBrut = mois_antérieurs.stream().mapToLong(b -> nv(b.getSalaireBrut())).sum()
                + nv(bulletin.getSalaireBrut());
        long cumulNet = mois_antérieurs.stream().mapToLong(b -> nv(b.getNetAPayer())).sum()
                + nv(bulletin.getNetAPayer());
        long cumulIr = mois_antérieurs.stream().mapToLong(b -> nv(b.getImpotRevenu())).sum()
                + nv(bulletin.getImpotRevenu());

        bulletin.setCumulBrutAnnuel(cumulBrut);
        bulletin.setCumulNetAnnuel(cumulNet);
        bulletin.setCumulIrAnnuel(cumulIr);
    }

    private long nv(Long v) {
        return v != null ? v : 0L;
    }

    // ─── Export PDF (OpenPDF) ───────────────────────────────────────────

    public byte[] exportPdf(String id) {
        BulletinPaie b = bulletinPaieRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Bulletin introuvable : " + id));

        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Document doc = new Document();
            PdfWriter.getInstance(doc, out);
            doc.open();

            String moisLabel = YearMonth.of(b.getPeriode().getAnnee(), b.getPeriode().getMois())
                    .getMonth().getDisplayName(TextStyle.FULL, Locale.FRENCH);
            String periode = moisLabel + " " + b.getPeriode().getAnnee();

            Paragraph titre = new Paragraph("BULLETIN DE PAIE — " + periode.toUpperCase(),
                    FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14));
            titre.setAlignment(Element.ALIGN_CENTER);
            doc.add(titre);
            doc.add(Chunk.NEWLINE);

            PdfPTable entete = new PdfPTable(2);
            entete.setWidthPercentage(100);
            ajouterEnteteCell(entete, "Matricule", ns(b.getMatricule()));
            ajouterEnteteCell(entete, "Nom complet", ns(b.getNom()) + " " + ns(b.getPrenom()));
            ajouterEnteteCell(entete, "Poste", ns(b.getPoste()));
            ajouterEnteteCell(entete, "Département", ns(b.getDepartement()));
            ajouterEnteteCell(entete, "Catégorie", ns(b.getCategorieCode()));
            ajouterEnteteCell(entete, "N° IPRES", ns(b.getNumeroIpres()));
            ajouterEnteteCell(entete, "N° CSS", ns(b.getNumeroCss()));
            ajouterEnteteCell(entete, "Banque / RIB", ns(b.getBanque()) + " / " + ns(b.getRib()));
            ajouterEnteteCell(entete, "Jours travaillés", String.valueOf(b.getJoursTravailles()));
            ajouterEnteteCell(entete, "Heures sup. (total)", String.valueOf(b.getHeuresSupTotal()));
            doc.add(entete);
            doc.add(Chunk.NEWLINE);

            PdfPTable table = new PdfPTable(6);
            table.setWidthPercentage(100);
            table.setWidths(new float[]{3.5f, 1.3f, 1.2f, 1.5f, 1.5f, 1.5f});
            String[] headers = {"Libellé", "Base", "Taux", "Gain", "Retenue sal.", "Cotis. pat."};
            for (String h : headers) {
                PdfPCell hc = new PdfPCell(new Phrase(h,
                        FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9)));
                hc.setBackgroundColor(java.awt.Color.LIGHT_GRAY);
                table.addCell(hc);
            }

            for (LigneBulletin l : b.getLignes()) {
                table.addCell(new Phrase(ns(l.getLibelle()), FontFactory.getFont(FontFactory.HELVETICA, 8)));
                table.addCell(fmt(l.getBase()));
                table.addCell(l.getTaux() != null ? String.format(Locale.FRENCH, "%.2f%%", l.getTaux() * 100) : "");
                table.addCell(l.getNature() == NatureLigne.GAIN ? fmt(l.getMontantSalarial()) : "");
                table.addCell(l.getNature() == NatureLigne.RETENUE_SALARIALE ? fmt(l.getMontantSalarial()) : "");
                table.addCell(l.getNature() == NatureLigne.COTISATION_PATRONALE ? fmt(l.getMontantPatronal()) : "");
            }
            doc.add(table);
            doc.add(Chunk.NEWLINE);

            PdfPTable totaux = new PdfPTable(2);
            totaux.setWidthPercentage(60);
            totaux.setHorizontalAlignment(Element.ALIGN_RIGHT);
            ajouterTotalCell(totaux, "Salaire brut", fmt(b.getSalaireBrut()));
            ajouterTotalCell(totaux, "Total cotisations salariales", fmt(b.getTotalCotisationsSalariales()));
            ajouterTotalCell(totaux, "Impôt sur le revenu", fmt(b.getImpotRevenu()));
            ajouterTotalCell(totaux, "TRIMF", fmt(b.getTrimf()));
            if (nv(b.getTotalPrets()) + nv(b.getTotalAvances()) + nv(b.getTotalRetenues()) > 0) {
                ajouterTotalCell(totaux, "Total retenues personnelles",
                        fmt(nv(b.getTotalPrets()) + nv(b.getTotalAvances()) + nv(b.getTotalRetenues())));
            }
            ajouterTotalCell(totaux, "NET À PAYER", fmt(b.getNetAPayer()));
            ajouterTotalCell(totaux, "Coût total employeur", fmt(b.getCoutTotalEmployeur()));
            doc.add(totaux);

            // ── Section conditionnelle : détail des retenues personnelles ──
            boolean aRetenues = (b.getPretsAppliques() != null && !b.getPretsAppliques().isEmpty())
                    || (b.getAvancesAppliquees() != null && !b.getAvancesAppliquees().isEmpty())
                    || (b.getRetenuesAppliquees() != null && !b.getRetenuesAppliquees().isEmpty());
            if (aRetenues) {
                doc.add(Chunk.NEWLINE);
                Paragraph sousTitre = new Paragraph("Retenues personnelles",
                        FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10));
                doc.add(sousTitre);

                PdfPTable detail = new PdfPTable(3);
                detail.setWidthPercentage(80);
                detail.setWidths(new float[]{3f, 2f, 2f});
                String[] hs = {"Libellé", "Montant", "Durée"};
                for (String h : hs) {
                    PdfPCell c = new PdfPCell(new Phrase(h,
                            FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9)));
                    c.setBackgroundColor(java.awt.Color.LIGHT_GRAY);
                    detail.addCell(c);
                }
                if (b.getPretsAppliques() != null) {
                    for (PretCategorie pr : b.getPretsAppliques()) {
                        detail.addCell(new Phrase("Prêt : " + ns(pr.getLibelle()),
                                FontFactory.getFont(FontFactory.HELVETICA, 8)));
                        detail.addCell(fmt(pr.getMontant()));
                        detail.addCell(pr.getDureeMois() != null ? pr.getDureeMois() + " mois" : "");
                    }
                }
                if (b.getAvancesAppliquees() != null) {
                    for (AvanceCategorie av : b.getAvancesAppliquees()) {
                        detail.addCell(new Phrase("Avance : " + ns(av.getLibelle()),
                                FontFactory.getFont(FontFactory.HELVETICA, 8)));
                        detail.addCell(fmt(av.getMontant()));
                        detail.addCell(av.getDureeMois() != null ? av.getDureeMois() + " mois" : "");
                    }
                }
                if (b.getRetenuesAppliquees() != null) {
                    for (RetenueCategorie re : b.getRetenuesAppliquees()) {
                        detail.addCell(new Phrase("Retenue : " + ns(re.getLibelle()),
                                FontFactory.getFont(FontFactory.HELVETICA, 8)));
                        detail.addCell(fmt(re.getMontant()));
                        detail.addCell("");
                    }
                }
                doc.add(detail);
            }

            doc.add(Chunk.NEWLINE);
            doc.add(new Paragraph("Date de calcul : "
                    + (b.getDateCalcul() != null ? b.getDateCalcul().toString() : "-"),
                    FontFactory.getFont(FontFactory.HELVETICA_OBLIQUE, 8)));

            doc.close();
            return out.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Erreur génération PDF bulletin : " + e.getMessage(), e);
        }
    }

    private void ajouterEnteteCell(PdfPTable table, String label, String value) {
        PdfPCell c1 = new PdfPCell(new Phrase(label,
                FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9)));
        PdfPCell c2 = new PdfPCell(new Phrase(value,
                FontFactory.getFont(FontFactory.HELVETICA, 9)));
        table.addCell(c1);
        table.addCell(c2);
    }

    private void ajouterTotalCell(PdfPTable table, String label, String value) {
        PdfPCell c1 = new PdfPCell(new Phrase(label,
                FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9)));
        PdfPCell c2 = new PdfPCell(new Phrase(value,
                FontFactory.getFont(FontFactory.HELVETICA, 9)));
        c2.setHorizontalAlignment(Element.ALIGN_RIGHT);
        table.addCell(c1);
        table.addCell(c2);
    }

    private String ns(String s) {
        return s != null ? s : "";
    }

    private String fmt(Long v) {
        return v != null ? String.format(Locale.FRENCH, "%,d", v) : "";
    }
}