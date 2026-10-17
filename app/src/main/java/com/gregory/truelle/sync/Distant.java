package com.gregory.truelle.sync;

import java.io.File;
import java.io.IOException;
import java.util.List;

/** Le stockage distant (Google Drive en vrai, une fausse version en mémoire pour les tests). */
public interface Distant {

    final class Element {
        public final String id;
        public final String nom;
        public final boolean dossier;

        public Element(String id, String nom, boolean dossier) {
            this.id = id;
            this.nom = nom;
            this.dossier = dossier;
        }
    }

    /** Identifiant du dossier racine « Chantiers » (créé s'il n'existe pas). */
    String racine() throws IOException;

    List<Element> lister(String dossierId) throws IOException;

    String creerDossier(String parentId, String nom) throws IOException;

    void envoyer(String parentId, File fichier) throws IOException;

    void telecharger(String fichierId, File destination) throws IOException;
}
