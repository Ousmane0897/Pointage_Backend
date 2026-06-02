package com.example.Pointage_Cleanic.services.terrain;

import com.example.Pointage_Cleanic.entities.terrain.ApplicationPhyto;
import com.lowagie.text.Chunk;
import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.util.List;

/** Génération du registre réglementaire des applications phytosanitaires (PDF, OpenPDF). */
@Service
public class RegistrePhytoPdfService {

    public byte[] generer(List<ApplicationPhyto> applications, LocalDate dateDebut, LocalDate dateFin) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Document doc = new Document(PageSize.A4.rotate());
            PdfWriter.getInstance(doc, out);
            doc.open();

            Paragraph titre = new Paragraph("REGISTRE PHYTOSANITAIRE",
                    FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14));
            titre.setAlignment(Element.ALIGN_CENTER);
            doc.add(titre);
            Paragraph periode = new Paragraph("Période : " + dateDebut + " → " + dateFin,
                    FontFactory.getFont(FontFactory.HELVETICA, 9));
            periode.setAlignment(Element.ALIGN_CENTER);
            doc.add(periode);
            doc.add(Chunk.NEWLINE);

            PdfPTable table = new PdfPTable(8);
            table.setWidthPercentage(100);
            table.setWidths(new float[]{2.2f, 3f, 3f, 2.5f, 1.8f, 2.5f, 3f, 1.8f});
            for (String h : new String[]{"Date", "Site", "Produit", "N° homolog.", "Dose", "Zone", "Agent", "Statut"}) {
                PdfPCell c = new PdfPCell(new Phrase(h, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8)));
                c.setBackgroundColor(Color.LIGHT_GRAY);
                table.addCell(c);
            }

            for (ApplicationPhyto a : applications) {
                table.addCell(petit(a.getDateApplication() == null ? "" : a.getDateApplication().toString()));
                table.addCell(petit(ns(a.getSiteNom())));
                table.addCell(petit(ns(a.getProduitNomCommercial())));
                table.addCell(petit(ns(a.getProduitNumeroHomologation())));
                table.addCell(petit((a.getDoseAppliquee() == null ? "" : a.getDoseAppliquee())
                        + " " + ns(a.getDoseUnite())));
                table.addCell(petit(a.getZoneTraitee() == null ? "" : ns(a.getZoneTraitee().getLibelle())));
                table.addCell(petit(ns(a.getEmployeNom())));
                table.addCell(petit(a.getStatut() == null ? "" : a.getStatut().name()));
            }
            doc.add(table);

            doc.close();
            return out.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Erreur génération registre phytosanitaire : " + e.getMessage(), e);
        }
    }

    private Phrase petit(Object texte) {
        return new Phrase(texte == null ? "" : texte.toString(), FontFactory.getFont(FontFactory.HELVETICA, 8));
    }

    private String ns(Object v) {
        return v == null ? "" : v.toString();
    }
}