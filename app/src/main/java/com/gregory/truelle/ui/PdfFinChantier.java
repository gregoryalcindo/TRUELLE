package com.gregory.truelle.ui;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.pdf.PdfDocument;
import android.graphics.pdf.PdfRenderer;
import android.os.ParcelFileDescriptor;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;

import com.gregory.truelle.data.Chantier;
import com.gregory.truelle.data.DossierDepenses;
import com.gregory.truelle.data.LocalStore;
import com.gregory.truelle.data.Money;
import com.gregory.truelle.data.RecapFin;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Map;

/**
 * Dessine le PDF de fin de chantier : récapitulatif pour le client (devis signés, travaux en plus
 * avec leurs accords signés, matériel refacturé), puis les factures fournisseurs en annexe.
 * Ce document ne remplace pas la facture : c'est écrit en pied de page.
 */
public final class PdfFinChantier {

    private static final int L = 595, H = 842;       // A4 en points
    private static final int M = 40;                 // marges
    private static final int BAS = H - 50;           // limite avant le pied de page
    private static final int ENCRE = Color.parseColor("#1B1F23");
    private static final int GRIS = Color.parseColor("#5A6067");
    private static final int TRAIT = Color.parseColor("#D6D3CB");
    private static final int FOND = Color.parseColor("#F2F1EC");

    private final PdfDocument doc = new PdfDocument();
    private PdfDocument.Page page;
    private Canvas c;
    private float y;
    private int numero = 0;
    private String pied = "Document récapitulatif — ne remplace pas la facture.";

    private PdfFinChantier() {}

    /** Crée le PDF dans un fichier temporaire et le renvoie. */
    public static File generer(File dossierTemp, LocalStore store, Chantier chantier, RecapFin recap, Map<String, String> entreprise) throws IOException {
        PdfFinChantier p = new PdfFinChantier();
        try {
            p.recapitulatif(store, chantier, recap, entreprise);
            for (RecapFin.Annexe an : recap.annexes) {
                File f = store.fichier(chantier.id, an.fichier);
                if (f != null) p.annexe(an.titre, f);
            }
            if (p.page != null) p.doc.finishPage(p.page);
            File out = new File(dossierTemp, "fin-de-chantier-" + System.currentTimeMillis() + ".pdf");
            try (FileOutputStream fos = new FileOutputStream(out)) {
                p.doc.writeTo(fos);
            }
            return out;
        } finally {
            p.doc.close();
        }
    }

    // ------------------------------------------------------------ récapitulatif

    private void recapitulatif(LocalStore store, Chantier ch, RecapFin r, Map<String, String> ent) {
        nouvellePage();
        enteteEtChantier(ch, ent, "RÉCAPITULATIF", "DE FIN DE CHANTIER");

        sections(r.sections, "Aucun montant à récapituler : ajoutez les devis signés, travaux acceptés ou matériel refacturé.");

        // Totaux.
        besoin(80);
        float xBox = L - M - 250;
        Paint p = new Paint();
        p.setColor(FOND);
        c.drawRect(xBox, y, L - M, y + 72, p);
        ligneTotal("Total TTC", Money.format(r.totalTtc), xBox + 12, y + 10, false);
        ligneTotal("Déjà réglé", Money.format(r.dejaRegle), xBox + 12, y + 30, false);
        ligneTotal("Reste à payer", Money.format(r.resteAPayer()), xBox + 12, y + 50, true);
        y += 92;

        // Accords signés.
        if (!r.signatures.isEmpty()) {
            besoin(40);
            y = texte("Accords signés pour les travaux supplémentaires", M, y, 12, true, ENCRE, L - 2 * M);
            y += 6;
            for (String[] sig : r.signatures) {
                besoin(90);
                float y0 = y;
                float yt = texte(sig[0], M, y0, 10, true, ENCRE, 280);
                texte("Bon pour accord, signé le " + sig[1], M, yt + 2, 9, false, GRIS, 280);
                File img = store.fichier(ch.id, sig[2]);
                Bitmap b = img == null ? null : Scanner.apercuFichier(img, 600);
                if (b != null) {
                    float w = 200, hImg = w * b.getHeight() / b.getWidth();
                    hImg = Math.min(hImg, 70);
                    c.drawBitmap(b, null, new RectF(L - M - w, y0, L - M, y0 + hImg), null);
                    b.recycle();
                }
                y = y0 + 80;
            }
        }

        besoin(30);
        texte("Montants TTC. Ce récapitulatif accompagne la facture et ne la remplace pas.", M, y + 6, 8.5f, false, GRIS, L - 2 * M);
    }

    /** Sections de lignes « libellé / détail / montant » avec sous-totaux. */
    private void sections(java.util.List<RecapFin.Section> liste, String siVide) {
        for (RecapFin.Section s : liste) {
            besoin(50);
            y = texte(s.titre, M, y, 12, true, ENCRE, L - 2 * M);
            y += 4;
            trait();
            for (RecapFin.Ligne l : s.lignes) {
                float h = hauteur(l.libelle, 10, false, 360) + (l.detail.isEmpty() ? 0 : hauteur(l.detail, 8.5f, false, 360) + 1) + 8;
                besoin(h);
                float y0 = y + 4;
                float yb = texte(l.libelle, M, y0, 10, false, ENCRE, 360);
                if (!l.detail.isEmpty()) yb = texte(l.detail, M, yb + 1, 8.5f, false, GRIS, 360);
                texteDroite(Money.format(l.montantCentimes), L - M, y0, 10, false, ENCRE);
                y = yb + 4;
                trait();
            }
            besoin(20);
            texteDroite("Sous-total : " + Money.format(s.total), L - M, y + 4, 10, true, ENCRE);
            y += 30;
        }
        if (liste.isEmpty()) {
            y = texte(siVide, M, y, 10, false, GRIS, L - 2 * M);
            y += 16;
        }
    }

    private void ligneTotal(String lib, String val, float x, float yy, boolean gras) {
        texte(lib, x, yy, gras ? 12 : 10, gras, ENCRE, 140);
        texteDroite(val, L - M - 12, yy, gras ? 12 : 10, gras, ENCRE);
    }

    /** En-tête (entreprise à gauche, titre du document à droite) puis le bloc chantier. */
    private void enteteEtChantier(Chantier ch, Map<String, String> ent, String titre1, String titre2) {
        String date = new SimpleDateFormat("dd/MM/yyyy", Locale.FRANCE).format(new Date());
        float yG = y;
        String nom = val(ent, "nom");
        if (!nom.isEmpty()) yG = texte(nom, M, yG, 15, true, ENCRE, 300);
        for (String cle : new String[]{"adresse", "telephone", "email"}) {
            if (!val(ent, cle).isEmpty()) yG = texte(val(ent, cle), M, yG + 1, 9, false, GRIS, 300);
        }
        if (!val(ent, "siret").isEmpty()) yG = texte("SIRET " + val(ent, "siret"), M, yG + 1, 9, false, GRIS, 300);
        float yD = texteDroite(titre1, L - M, y, 13, true, ENCRE);
        yD = texteDroite(titre2, L - M, yD, 13, true, ENCRE);
        yD = texteDroite("Édité le " + date, L - M, yD + 4, 9, false, GRIS);
        y = Math.max(yG, yD) + 18;

        // Bloc chantier sur fond clair : on mesure d'abord, on dessine le fond, puis le texte.
        int largeurBloc = L - 2 * M - 24;
        String l1 = "Chantier : " + ch.titre;
        String l2 = ch.client.isEmpty() ? null : "Client : " + ch.client;
        String l3 = ch.adresse.isEmpty() ? null : "Adresse : " + ch.adresse;
        float hBloc = 20 + hauteur(l1, 11, true, largeurBloc)
                + (l2 == null ? 0 : 2 + hauteur(l2, 10, false, largeurBloc))
                + (l3 == null ? 0 : 2 + hauteur(l3, 10, false, largeurBloc));
        Paint fond = new Paint();
        fond.setColor(FOND);
        c.drawRect(M, y, L - M, y + hBloc, fond);
        float yc = texte(l1, M + 12, y + 10, 11, true, ENCRE, largeurBloc);
        if (l2 != null) yc = texte(l2, M + 12, yc + 2, 10, false, ENCRE, largeurBloc);
        if (l3 != null) texte(l3, M + 12, yc + 2, 10, false, ENCRE, largeurBloc);
        y += hBloc + 22;
    }

    // ------------------------------------------------------------ dossier des factures de dépenses

    /** Toutes les factures de dépenses, rangées par catégorie : document interne (toi, le comptable). */
    public static File genererDossierDepenses(File dossierTemp, LocalStore store, Chantier chantier, DossierDepenses d, Map<String, String> entreprise) throws IOException {
        PdfFinChantier p = new PdfFinChantier();
        p.pied = "Dossier interne des factures de dépenses du chantier.";
        try {
            p.nouvellePage();
            p.enteteEtChantier(chantier, entreprise, "DOSSIER DES FACTURES", "DE DÉPENSES");
            p.sections(d.sections, "Aucune dépense enregistrée sur ce chantier.");
            p.besoin(60);
            float xBox = L - M - 250;
            Paint fond = new Paint();
            fond.setColor(FOND);
            p.c.drawRect(xBox, p.y, L - M, p.y + 32, fond);
            p.ligneTotal("Total des dépenses", Money.format(d.total), xBox + 12, p.y + 10, true);
            p.y += 48;
            String note = d.annexes.size() + " facture(s) jointe(s) ci-après, dans l'ordre du sommaire."
                    + (d.sansFacture > 0 ? " " + d.sansFacture + " dépense(s) sans facture." : "");
            p.texte(note, M, p.y, 9, false, GRIS, L - 2 * M);
            for (RecapFin.Annexe an : d.annexes) {
                File f = store.fichier(chantier.id, an.fichier);
                if (f != null) p.annexe(an.titre, f);
            }
            if (p.page != null) p.doc.finishPage(p.page);
            File out = new File(dossierTemp, "dossier-factures-" + System.currentTimeMillis() + ".pdf");
            try (FileOutputStream fos = new FileOutputStream(out)) {
                p.doc.writeTo(fos);
            }
            return out;
        } finally {
            p.doc.close();
        }
    }

    // ------------------------------------------------------------ annexes

    private void annexe(String titre, File f) {
        if (f.getName().toLowerCase(Locale.ROOT).endsWith(".pdf")) {
            try (ParcelFileDescriptor fd = ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY);
                 PdfRenderer renderer = new PdfRenderer(fd)) {
                int n = renderer.getPageCount();
                for (int i = 0; i < n; i++) {
                    try (PdfRenderer.Page src = renderer.openPage(i)) {
                        int largeur = 1100;
                        int hauteur = Math.max(1, Math.round(largeur * (float) src.getHeight() / src.getWidth()));
                        Bitmap bmp = Bitmap.createBitmap(largeur, hauteur, Bitmap.Config.ARGB_8888);
                        bmp.eraseColor(Color.WHITE);
                        src.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT);
                        pageImage(titre + (n > 1 ? " (page " + (i + 1) + "/" + n + ")" : ""), bmp);
                        bmp.recycle();
                    }
                }
            } catch (Exception e) {
                pageTexte(titre, "Document illisible : " + f.getName());
            }
        } else {
            Bitmap bmp = Scanner.apercuFichier(f, 1100);
            if (bmp == null) pageTexte(titre, "Image illisible : " + f.getName());
            else {
                pageImage(titre, bmp);
                bmp.recycle();
            }
        }
    }

    private void pageImage(String titre, Bitmap bmp) {
        if (page != null) doc.finishPage(page);
        page = null;
        nouvellePage();
        y = texte(titre, M, y, 11, true, ENCRE, L - 2 * M) + 8;
        float zoneL = L - 2 * M, zoneH = BAS - y;
        float echelle = Math.min(zoneL / bmp.getWidth(), zoneH / bmp.getHeight());
        float w = bmp.getWidth() * echelle, h = bmp.getHeight() * echelle;
        float x = M + (zoneL - w) / 2;
        Paint bord = new Paint();
        bord.setStyle(Paint.Style.STROKE);
        bord.setColor(TRAIT);
        c.drawBitmap(bmp, null, new RectF(x, y, x + w, y + h), new Paint(Paint.FILTER_BITMAP_FLAG));
        c.drawRect(x, y, x + w, y + h, bord);
    }

    private void pageTexte(String titre, String message) {
        if (page != null) doc.finishPage(page);
        page = null;
        nouvellePage();
        y = texte(titre, M, y, 11, true, ENCRE, L - 2 * M) + 8;
        texte(message, M, y, 10, false, GRIS, L - 2 * M);
    }

    // ------------------------------------------------------------ mise en page

    private void nouvellePage() {
        numero++;
        page = doc.startPage(new PdfDocument.PageInfo.Builder(L, H, numero).create());
        c = page.getCanvas();
        y = M;
        texte(pied, M, H - 30, 7.5f, false, GRIS, 400);
        texteDroite("Page " + numero, L - M, H - 30, 7.5f, false, GRIS);
    }

    /** Passe à la page suivante s'il ne reste pas assez de place. */
    private void besoin(float h) {
        if (y + h > BAS) {
            doc.finishPage(page);
            nouvellePage();
        }
    }

    private void trait() {
        Paint p = new Paint();
        p.setColor(TRAIT);
        p.setStrokeWidth(0.8f);
        c.drawLine(M, y, L - M, y, p);
    }

    private static TextPaint peinture(float taille, boolean gras, int couleur) {
        TextPaint tp = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        tp.setTextSize(taille);
        tp.setColor(couleur);
        tp.setTypeface(gras ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        return tp;
    }

    private static StaticLayout mise(String s, float taille, boolean gras, int couleur, int largeur) {
        return StaticLayout.Builder.obtain(s, 0, s.length(), peinture(taille, gras, couleur), Math.max(10, largeur))
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(0, 1.1f)
                .build();
    }

    private static float hauteur(String s, float taille, boolean gras, int largeur) {
        return mise(s, taille, gras, ENCRE, largeur).getHeight();
    }

    /** Dessine un texte qui passe à la ligne ; renvoie la position sous le texte. */
    private float texte(String s, float x, float yy, float taille, boolean gras, int couleur, int largeur) {
        StaticLayout sl = mise(s, taille, gras, couleur, largeur);
        c.save();
        c.translate(x, yy);
        sl.draw(c);
        c.restore();
        return yy + sl.getHeight();
    }

    /** Texte sur une ligne aligné à droite sur xDroite ; renvoie la position sous le texte. */
    private float texteDroite(String s, float xDroite, float yy, float taille, boolean gras, int couleur) {
        TextPaint tp = peinture(taille, gras, couleur);
        Paint.FontMetrics fm = tp.getFontMetrics();
        float w = tp.measureText(s);
        c.drawText(s, xDroite - w, yy - fm.ascent, tp);
        return yy + (fm.descent - fm.ascent) * 1.1f;
    }

    private static String val(Map<String, String> m, String k) {
        String v = m.get(k);
        return v == null ? "" : v.trim();
    }
}
