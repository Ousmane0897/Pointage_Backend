package com.example.Pointage_Cleanic.services.terrain;

import com.example.Pointage_Cleanic.entities.terrain.FicheIntervention;
import com.example.Pointage_Cleanic.entities.terrain.ProduitUtilise;
import com.example.Pointage_Cleanic.entities.terrain.SignatureClient;
import com.example.Pointage_Cleanic.entities.terrain.TacheChecklist;
import com.lowagie.text.Chunk;
import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.FontFactory;
import com.lowagie.text.Image;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.util.Base64;

/** Génération serveur du PDF d'une fiche d'intervention (OpenPDF). */
@Service
public class InterventionPdfService {

    public byte[] generer(FicheIntervention f) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Document doc = new Document();
            PdfWriter.getInstance(doc, out);
            doc.open();

            Paragraph titre = new Paragraph("FICHE D'INTERVENTION — " + ns(f.getNumero()),
                    FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14));
            titre.setAlignment(Element.ALIGN_CENTER);
            doc.add(titre);
            doc.add(Chunk.NEWLINE);

            PdfPTable entete = new PdfPTable(2);
            entete.setWidthPercentage(100);
            cell(entete, "Site", ns(f.getSiteNom()) + " (" + ns(f.getSiteCode()) + ")");
            cell(entete, "Agent", ns(f.getEmployeNom()) + " — " + ns(f.getEmployeMatricule()));
            cell(entete, "Début", f.getDateDebut() == null ? "-" : f.getDateDebut().toString());
            cell(entete, "Fin", f.getDateFin() == null ? "-" : f.getDateFin().toString());
            cell(entete, "Durée (min)", f.getDuree() == null ? "-" : f.getDuree().toString());
            cell(entete, "Statut", f.getStatut() == null ? "-" : f.getStatut().name());
            doc.add(entete);
            doc.add(Chunk.NEWLINE);

            // Tâches
            doc.add(sousTitre("Tâches"));
            if (f.getTaches() != null && !f.getTaches().isEmpty()) {
                PdfPTable t = new PdfPTable(3);
                t.setWidthPercentage(100);
                t.setWidths(new float[]{5f, 1.2f, 4f});
                enteteCellule(t, "Libellé");
                enteteCellule(t, "Fait");
                enteteCellule(t, "Observation");
                for (TacheChecklist tache : f.getTaches()) {
                    t.addCell(petit(ns(tache.getLibelle())));
                    t.addCell(petit(tache.isFait() ? "Oui" : "Non"));
                    t.addCell(petit(ns(tache.getObservation())));
                }
                doc.add(t);
            } else {
                doc.add(petitParagraphe("Aucune tâche."));
            }
            doc.add(Chunk.NEWLINE);

            // Produits
            doc.add(sousTitre("Produits utilisés"));
            if (f.getProduits() != null && !f.getProduits().isEmpty()) {
                PdfPTable t = new PdfPTable(4);
                t.setWidthPercentage(100);
                enteteCellule(t, "Nom");
                enteteCellule(t, "Quantité");
                enteteCellule(t, "Unité");
                enteteCellule(t, "Référence");
                for (ProduitUtilise p : f.getProduits()) {
                    t.addCell(petit(ns(p.getNom())));
                    t.addCell(petit(p.getQuantite() == null ? "-" : p.getQuantite().toString()));
                    t.addCell(petit(ns(p.getUnite())));
                    t.addCell(petit(ns(p.getReference())));
                }
                doc.add(t);
            } else {
                doc.add(petitParagraphe("Aucun produit."));
            }
            doc.add(Chunk.NEWLINE);

            // Observations
            doc.add(sousTitre("Observations"));
            doc.add(petitParagraphe(ns(f.getObservations())));
            doc.add(Chunk.NEWLINE);

            // Signature client
            doc.add(sousTitre("Signature client"));
            SignatureClient sig = f.getSignatureClient();
            if (sig != null) {
                doc.add(petitParagraphe(ns(sig.getNom()) + " — " + ns(sig.getFonction())
                        + (sig.getDate() == null ? "" : " (" + sig.getDate() + ")")));
                Image img = imageDepuisDataUrl(sig.getDataUrl());
                if (img != null) {
                    img.scaleToFit(200, 100);
                    doc.add(img);
                }
            } else {
                doc.add(petitParagraphe("Non signée."));
            }

            doc.close();
            return out.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Erreur génération PDF intervention : " + e.getMessage(), e);
        }
    }

    private Image imageDepuisDataUrl(String dataUrl) {
        if (dataUrl == null || dataUrl.isBlank()) return null;
        try {
            String base64 = dataUrl.contains(",") ? dataUrl.substring(dataUrl.indexOf(',') + 1) : dataUrl;
            byte[] bytes = Base64.getDecoder().decode(base64);
            return Image.getInstance(bytes);
        } catch (Exception e) {
            return null;
        }
    }

    private String ns(Object v) {
        return v == null ? "" : v.toString();
    }

    private Paragraph sousTitre(String texte) {
        return new Paragraph(texte, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11));
    }

    private Paragraph petitParagraphe(String texte) {
        return new Paragraph(texte, FontFactory.getFont(FontFactory.HELVETICA, 9));
    }

    private Phrase petit(String texte) {
        return new Phrase(texte, FontFactory.getFont(FontFactory.HELVETICA, 8));
    }

    private void cell(PdfPTable table, String label, String value) {
        table.addCell(new Phrase(label, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9)));
        table.addCell(new Phrase(value, FontFactory.getFont(FontFactory.HELVETICA, 9)));
    }

    private void enteteCellule(PdfPTable table, String texte) {
        PdfPCell c = new PdfPCell(new Phrase(texte, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9)));
        c.setBackgroundColor(Color.LIGHT_GRAY);
        table.addCell(c);
    }
}