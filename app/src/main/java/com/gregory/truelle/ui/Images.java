package com.gregory.truelle.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;

import com.gregory.truelle.data.LocalStore;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Photos et images importées : réduites avant d'être enregistrées, pour ne pas saturer
 * le téléphone ni le Drive. 1 600 px de côté en qualité 80 (environ 300 Ko), lisible et
 * suffisant pour le PDF client ; miniature de 360 px (environ 30 Ko) pour les listes.
 */
public final class Images {

    private Images() {}

    public static final int COTE_PHOTO = 1600;
    public static final int QUALITE_PHOTO = 80;
    public static final int COTE_MINIATURE = 360;
    public static final int QUALITE_MINIATURE = 75;
    public static final String DOSSIER_PHOTOS = "Photos";
    public static final String DOSSIER_MINIATURES = "Photos/Miniatures";

    /** Chemins relatifs (dans le dossier du chantier) d'une photo enregistrée. */
    public static final class Enregistree {
        public final String fichier;
        public final String miniature;

        Enregistree(String fichier, String miniature) {
            this.fichier = fichier;
            this.miniature = miniature;
        }
    }

    /** Réduit et enregistre une photo du chantier, avec sa miniature. */
    public static Enregistree enregistrerPhoto(Context ctx, LocalStore store, String chantierId, File source) throws IOException {
        String nom = "Photo " + new SimpleDateFormat("yyyy-MM-dd HH'h'mm ss", Locale.FRANCE).format(new Date());
        File grande = reduire(source, new File(ctx.getCacheDir(), "photo-" + System.nanoTime() + ".jpg"), COTE_PHOTO, QUALITE_PHOTO);
        File petite = reduire(source, new File(ctx.getCacheDir(), "mini-" + System.nanoTime() + ".jpg"), COTE_MINIATURE, QUALITE_MINIATURE);
        try {
            String f = store.importerFichier(chantierId, DOSSIER_PHOTOS, grande, nom, "jpg");
            String m = store.importerFichier(chantierId, DOSSIER_MINIATURES, petite, nom, "jpg");
            return new Enregistree(f, m);
        } finally {
            //noinspection ResultOfMethodCallIgnored
            grande.delete();
            //noinspection ResultOfMethodCallIgnored
            petite.delete();
        }
    }

    /** Image importée (devis photographié, capture…) : réduite comme une photo, PDF laissés tels quels. */
    public static File reduireSiImage(Context ctx, File source, String extension) {
        if ("pdf".equalsIgnoreCase(extension)) return source;
        try {
            File dest = new File(ctx.getCacheDir(), "image-" + System.nanoTime() + ".jpg");
            reduire(source, dest, COTE_PHOTO, QUALITE_PHOTO);
            return dest;
        } catch (Exception e) {
            return source; // illisible comme image : on garde l'original plutôt que de le perdre
        }
    }

    /** Réduit une image (en respectant son orientation) et l'écrit en JPEG. */
    public static File reduire(File source, File dest, int coteMax, int qualite) throws IOException {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(source.getAbsolutePath(), o);
        if (o.outWidth <= 0 || o.outHeight <= 0) throw new IOException("Image illisible");
        int echantillon = 1;
        while (Math.max(o.outWidth, o.outHeight) / (echantillon * 2) >= coteMax) echantillon *= 2;
        o = new BitmapFactory.Options();
        o.inSampleSize = echantillon;
        Bitmap bmp = BitmapFactory.decodeFile(source.getAbsolutePath(), o);
        if (bmp == null) throw new IOException("Image illisible");

        Matrix m = new Matrix();
        int rotation = rotation(source);
        if (rotation != 0) m.postRotate(rotation);
        float echelle = Math.min(1f, coteMax / (float) Math.max(bmp.getWidth(), bmp.getHeight()));
        if (echelle < 1f) m.postScale(echelle, echelle);
        Bitmap finale = m.isIdentity() ? bmp : Bitmap.createBitmap(bmp, 0, 0, bmp.getWidth(), bmp.getHeight(), m, true);
        try (FileOutputStream out = new FileOutputStream(dest)) {
            if (!finale.compress(Bitmap.CompressFormat.JPEG, qualite, out)) throw new IOException("Compression impossible");
        } finally {
            if (finale != bmp) finale.recycle();
            bmp.recycle();
        }
        return dest;
    }

    private static int rotation(File f) {
        try {
            int o = new ExifInterface(f.getAbsolutePath()).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
            if (o == ExifInterface.ORIENTATION_ROTATE_90) return 90;
            if (o == ExifInterface.ORIENTATION_ROTATE_180) return 180;
            if (o == ExifInterface.ORIENTATION_ROTATE_270) return 270;
        } catch (Exception ignored) {
            // pas d'EXIF : image déjà droite
        }
        return 0;
    }

    /** Charge une image pour l'affichage (réduite à la largeur demandée), ou null. */
    public static Bitmap charger(File f, int largeurPx) {
        if (f == null || !f.exists()) return null;
        return Scanner.apercuFichier(f, largeurPx);
    }
}
