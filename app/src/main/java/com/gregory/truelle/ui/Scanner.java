package com.gregory.truelle.ui;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.pdf.PdfRenderer;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.IntentSenderRequest;
import androidx.core.content.FileProvider;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions;
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning;
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;
import com.gregory.truelle.data.ReceiptParser;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Photo de facture : le scanner de Google (recadrage, redressement, plusieurs pages, ou import depuis la galerie)
 * puis lecture du texte sur le téléphone, sans connexion et sans envoyer la facture ailleurs.
 */
public final class Scanner {

    private Scanner() {}

    public interface Callback {
        void onFacture(Facture facture);

        void onAnnule();

        void onErreur(String message);
    }

    /** Une facture scannée, pas encore rangée dans le chantier. */
    public static final class Facture {
        public final File pdf;
        public final ReceiptParser.Resultat lu;

        Facture(File pdf, ReceiptParser.Resultat lu) {
            this.pdf = pdf;
            this.lu = lu;
        }
    }

    public static void lancer(Activity activity, ActivityResultLauncher<IntentSenderRequest> launcher, Callback cb) {
        GmsDocumentScannerOptions options = new GmsDocumentScannerOptions.Builder()
                .setGalleryImportAllowed(true) // pour les factures déjà prises en photo
                .setPageLimit(10)
                .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG, GmsDocumentScannerOptions.RESULT_FORMAT_PDF)
                .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
                .build();
        GmsDocumentScanning.getClient(options)
                .getStartScanIntent(activity)
                .addOnSuccessListener(sender -> launcher.launch(new IntentSenderRequest.Builder(sender).build()))
                .addOnFailureListener(e -> cb.onErreur("Le scanner n'est pas disponible sur ce téléphone (" + e.getMessage() + ")"));
    }

    /** Appelé avec le résultat du scanner : copie le PDF, lit la première page. */
    public static void traiter(Context ctx, GmsDocumentScanningResult resultat, Callback cb) {
        File pdf = null;
        try {
            GmsDocumentScanningResult.Pdf p = resultat.getPdf();
            if (p != null) {
                pdf = new File(ctx.getCacheDir(), "scan-" + System.currentTimeMillis() + ".pdf");
                copier(ctx, p.getUri(), pdf);
            }
        } catch (Exception e) {
            cb.onErreur("Impossible de récupérer le scan : " + e.getMessage());
            return;
        }
        if (pdf == null) {
            cb.onErreur("Le scan n'a rien renvoyé");
            return;
        }
        final File fichier = pdf;
        List<GmsDocumentScanningResult.Page> pages = resultat.getPages();
        if (pages == null || pages.isEmpty()) {
            cb.onFacture(new Facture(fichier, new ReceiptParser.Resultat()));
            return;
        }
        InputImage image;
        try {
            image = InputImage.fromFilePath(ctx, pages.get(0).getImageUri());
        } catch (Exception e) {
            cb.onFacture(new Facture(fichier, new ReceiptParser.Resultat()));
            return;
        }
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                .process(image)
                .addOnSuccessListener(text -> cb.onFacture(new Facture(fichier, ReceiptParser.analyser(lignes(text)))))
                // Lecture ratée : on garde quand même la facture, l'utilisateur saisit le montant.
                .addOnFailureListener(e -> cb.onFacture(new Facture(fichier, new ReceiptParser.Resultat())));
    }

    private static List<ReceiptParser.Ligne> lignes(Text text) {
        List<ReceiptParser.Ligne> res = new ArrayList<>();
        for (Text.TextBlock bloc : text.getTextBlocks()) {
            for (Text.Line ligne : bloc.getLines()) {
                Rect r = ligne.getBoundingBox();
                if (r == null) r = new Rect(0, res.size() * 40, 0, res.size() * 40 + 30);
                res.add(new ReceiptParser.Ligne(ligne.getText(), r.top, r.bottom, r.left));
            }
        }
        return res;
    }

    private static void copier(Context ctx, Uri source, File dest) throws Exception {
        try (InputStream in = ctx.getContentResolver().openInputStream(source);
             OutputStream out = new FileOutputStream(dest)) {
            if (in == null) throw new Exception("fichier introuvable");
            byte[] buf = new byte[64 * 1024];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        }
    }

    /** Aperçu de la première page d'un PDF, ou null si illisible. */
    public static Bitmap apercu(File pdf, int largeurPx) {
        try (ParcelFileDescriptor fd = ParcelFileDescriptor.open(pdf, ParcelFileDescriptor.MODE_READ_ONLY);
             PdfRenderer renderer = new PdfRenderer(fd)) {
            if (renderer.getPageCount() == 0) return null;
            try (PdfRenderer.Page page = renderer.openPage(0)) {
                int hauteur = Math.max(1, Math.round(largeurPx * (float) page.getHeight() / page.getWidth()));
                Bitmap bmp = Bitmap.createBitmap(largeurPx, hauteur, Bitmap.Config.ARGB_8888);
                bmp.eraseColor(Color.WHITE);
                page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
                return bmp;
            }
        } catch (Exception e) {
            return null;
        }
    }

    /** Ouvre la facture dans la visionneuse PDF du téléphone. */
    public static void ouvrir(Context ctx, File fichier) {
        try {
            Uri uri = FileProvider.getUriForFile(ctx, ctx.getPackageName() + ".fichiers", fichier);
            String type = fichier.getName().toLowerCase().endsWith(".pdf") ? "application/pdf" : "image/*";
            Intent i = new Intent(Intent.ACTION_VIEW).setDataAndType(uri, type).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            ctx.startActivity(Intent.createChooser(i, "Ouvrir la facture"));
        } catch (ActivityNotFoundException | IllegalArgumentException e) {
            Toast.makeText(ctx, "Aucune appli pour ouvrir ce fichier", Toast.LENGTH_SHORT).show();
        }
    }
}
