package com.gregory.truelle.ui;

import android.content.Context;
import android.widget.TextView;

import com.gregory.truelle.data.Chantier;

/**
 * Libellés et couleurs des statuts, au même endroit pour rester cohérent partout.
 * Gris = pas encore engagé, bleu = en cours / en attente, vert = acquis.
 */
public final class Libelles {

    private Libelles() {}

    public static String devis(String sorte, String statut) {
        boolean nous = Chantier.DEVIS_NOUS.equals(sorte);
        if (Chantier.DEVIS_SIGNE.equals(statut)) return nous ? "Signé" : "Accepté";
        if (Chantier.DEVIS_REFUSE.equals(statut)) return "Refusé";
        return nous ? "Envoyé" : "Reçu";
    }

    public static TextView chipDevis(Context c, String sorte, String statut) {
        int[] col = Chantier.DEVIS_SIGNE.equals(statut) ? new int[]{UiKit.VERT_FOND, UiKit.VERT}
                : Chantier.DEVIS_REFUSE.equals(statut) ? new int[]{UiKit.GRIS_FOND, UiKit.GRIS}
                : new int[]{UiKit.BLEU_FOND, UiKit.BLEU};
        return UiKit.chip(c, devis(sorte, statut), col[0], col[1]);
    }

    public static String travaux(String statut) {
        if (Chantier.TRAVAUX_FAIT.equals(statut)) return "Fait";
        if (Chantier.TRAVAUX_ACCEPTE.equals(statut)) return "Accepté";
        return "Proposé";
    }

    public static TextView chipTravaux(Context c, String statut) {
        int[] col = Chantier.TRAVAUX_FAIT.equals(statut) ? new int[]{UiKit.VERT_FOND, UiKit.VERT}
                : Chantier.TRAVAUX_ACCEPTE.equals(statut) ? new int[]{UiKit.BLEU_FOND, UiKit.BLEU}
                : new int[]{UiKit.GRIS_FOND, UiKit.GRIS};
        return UiKit.chip(c, travaux(statut), col[0], col[1]);
    }

    public static String facturation(String statut) {
        if (Chantier.FACT_ENCAISSE.equals(statut)) return "Encaissé";
        if (Chantier.FACT_FACTURE.equals(statut)) return "Facturé";
        return "À facturer";
    }

    public static TextView chipFacturation(Context c, String statut) {
        int[] col = Chantier.FACT_ENCAISSE.equals(statut) ? new int[]{UiKit.VERT_FOND, UiKit.VERT}
                : Chantier.FACT_FACTURE.equals(statut) ? new int[]{UiKit.BLEU_FOND, UiKit.BLEU}
                : new int[]{UiKit.GRIS_FOND, UiKit.GRIS};
        return UiKit.chip(c, facturation(statut), col[0], col[1]);
    }
}
