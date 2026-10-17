package com.gregory.truelle.ui.screens;

import android.graphics.Bitmap;
import android.view.View;
import android.widget.LinearLayout;

import com.gregory.truelle.MainActivity;
import com.gregory.truelle.data.Chantier;
import com.gregory.truelle.data.Event;
import com.gregory.truelle.data.Money;
import com.gregory.truelle.ui.Screen;
import com.gregory.truelle.ui.SignaturePad;
import com.gregory.truelle.ui.UiKit;

import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** Le client signe au doigt pour accepter des travaux en plus et leur prix. */
public final class SignatureScreen extends Screen {

    private final String chantierId;
    private final String travauxId;

    public SignatureScreen(MainActivity a, String chantierId, String travauxId) {
        super(a);
        this.chantierId = chantierId;
        this.travauxId = travauxId;
    }

    @Override
    public boolean rafraichirAuRetour() {
        return false;
    }

    @Override
    public View build() {
        Chantier c = store().charger(chantierId);
        Chantier.Element t = c == null ? null : c.element(travauxId);
        Page p = new Page(a);
        p.entete.addView(UiKit.retour(a, "Annuler", v -> a.retour()));
        p.entete.addView(UiKit.titre(a, "Accord du client", 30));
        if (t == null) return p.racine;

        LinearLayout recap = UiKit.carte(a, 8);
        recap.setBackground(UiKit.rounded(UiKit.SURFACE, dp(16), 0, 0));
        recap.addView(UiKit.text(a, c.titre + (c.client.isEmpty() ? "" : " · " + c.client), 16, UiKit.ENCRE_2, false));
        recap.addView(UiKit.text(a, t.get("description"), 19, UiKit.ENCRE, true));
        recap.addView(UiKit.ligneChiffre(a, "Montant TTC", Money.format(t.centimes("montant_centimes")), true), UiKit.pleineLargeur());
        p.contenu.addView(recap, UiKit.pleineLargeur());

        p.contenu.addView(UiKit.text(a, "Bon pour accord : signez ci-dessous avec le doigt.", 18, UiKit.ENCRE, true));
        SignaturePad pad = new SignaturePad(a);
        p.contenu.addView(pad, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(280)));
        p.contenu.addView(UiKit.boutonContour(a, "Effacer", v -> pad.effacer()), UiKit.pleineLargeur());

        p.ajouterAuPied(UiKit.boutonAction(a, "Valider la signature", v -> {
            if (pad.estVide()) {
                toast("Le client n'a pas encore signé");
                return;
            }
            try {
                File tmp = new File(a.getCacheDir(), "signature-" + System.currentTimeMillis() + ".png");
                Bitmap bmp = pad.image();
                try (FileOutputStream out = new FileOutputStream(tmp)) {
                    bmp.compress(Bitmap.CompressFormat.PNG, 100, out);
                }
                String chemin = store().importerFichier(chantierId, "Travaux en plus", tmp, "signature-" + t.get("description"), "png");
                //noinspection ResultOfMethodCallIgnored
                tmp.delete();
                Map<String, String> data = new LinkedHashMap<>();
                data.put("cible", t.id);
                data.put("signature", chemin);
                data.put("signe_le", new SimpleDateFormat("dd/MM/yyyy", Locale.FRANCE).format(new Date()));
                if (Chantier.TRAVAUX_PROPOSE.equals(t.get("statut"))) data.put("statut", Chantier.TRAVAUX_ACCEPTE);
                if (enregistrer(chantierId, Event.CORRECTION, data)) {
                    toast("Signature enregistrée ✓ Travaux acceptés");
                    a.retour();
                }
            } catch (Exception e) {
                toast("Signature non enregistrée : " + e.getMessage());
            }
        }));
        return p.racine;
    }
}
