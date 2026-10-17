package com.gregory.truelle.ui.screens;

import android.app.AlertDialog;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;

import com.gregory.truelle.MainActivity;
import com.gregory.truelle.data.Chantier;
import com.gregory.truelle.data.Event;
import com.gregory.truelle.ui.Screen;
import com.gregory.truelle.ui.UiKit;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Une note libre sur le chantier (livraison, remarque du client, à ne pas oublier…). */
public final class NoteScreen extends Screen {

    private final String chantierId;
    private final String noteId;

    public NoteScreen(MainActivity a, String chantierId, String noteId) {
        super(a);
        this.chantierId = chantierId;
        this.noteId = noteId;
    }

    @Override
    public boolean rafraichirAuRetour() {
        return false;
    }

    @Override
    public View build() {
        Chantier c = store().charger(chantierId);
        Chantier.Note existante = (c != null && noteId != null) ? c.note(noteId) : null;
        boolean edition = existante != null;

        Page p = new Page(a);
        p.entete.addView(UiKit.retour(a, "Annuler", v -> a.retour()));
        p.entete.addView(UiKit.titre(a, edition ? "Modifier la note" : "Nouvelle note", 30));
        if (c == null) return p.racine;

        EditText f = UiKit.champ(a, edition ? existante.texte : "",
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        f.setMinLines(6);
        f.setGravity(Gravity.TOP | Gravity.START);
        f.setHint("Ex. : livraison du carrelage jeudi matin");
        p.contenu.addView(UiKit.libelle(a, "Note · " + c.titre, f), UiKit.pleineLargeur());

        p.ajouterAuPied(UiKit.boutonAction(a, "Enregistrer", v -> {
            String texte = f.getText().toString().trim();
            if (texte.isEmpty()) {
                f.setError("La note est vide");
                return;
            }
            boolean ok;
            if (!edition) {
                ok = enregistrer(chantierId, Event.NOTE, Collections.singletonMap("texte", texte));
            } else if (!texte.equals(existante.texte)) {
                Map<String, String> data = new LinkedHashMap<>();
                data.put("cible", existante.id);
                data.put("texte", texte);
                ok = enregistrer(chantierId, Event.CORRECTION, data);
            } else {
                ok = true;
            }
            if (ok) {
                toast("Note enregistrée ✓");
                a.retour();
            }
        }));

        if (edition) {
            p.ajouterAuPied(UiKit.boutonContour(a, "Supprimer cette note", v -> new AlertDialog.Builder(a)
                    .setTitle("Supprimer cette note ?")
                    .setNegativeButton("Non", null)
                    .setPositiveButton("Supprimer", (d, w) -> {
                        if (enregistrer(chantierId, Event.SUPPRESSION, Collections.singletonMap("cible", existante.id))) {
                            toast("Note supprimée");
                            a.retour();
                        }
                    })
                    .show()));
        } else {
            f.requestFocus();
        }
        return p.racine;
    }
}
