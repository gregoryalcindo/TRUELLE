package com.gregory.truelle.sync;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.gregory.truelle.data.Chantier;
import com.gregory.truelle.data.Event;
import com.gregory.truelle.data.LocalStore;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;

public class SynchroTest {

    /** Un faux Google Drive en mémoire. */
    static final class FauxDrive implements Distant {
        final Map<String, String[]> elements = new LinkedHashMap<>(); // id -> {parent, nom, "d"|"f"}
        final Map<String, byte[]> contenus = new LinkedHashMap<>();
        int n = 0;

        @Override
        public String racine() {
            for (Map.Entry<String, String[]> e : elements.entrySet()) {
                if ("root".equals(e.getValue()[0]) && "Chantiers".equals(e.getValue()[1])) return e.getKey();
            }
            return creerDossier("root", "Chantiers");
        }

        @Override
        public List<Element> lister(String dossierId) {
            List<Element> l = new ArrayList<>();
            for (Map.Entry<String, String[]> e : elements.entrySet()) {
                if (dossierId.equals(e.getValue()[0])) l.add(new Element(e.getKey(), e.getValue()[1], "d".equals(e.getValue()[2])));
            }
            return l;
        }

        @Override
        public String creerDossier(String parentId, String nom) {
            String id = "id" + (++n);
            elements.put(id, new String[]{parentId, nom, "d"});
            return id;
        }

        @Override
        public void envoyer(String parentId, File fichier) throws IOException {
            String id = "id" + (++n);
            elements.put(id, new String[]{parentId, fichier.getName(), "f"});
            contenus.put(id, Files.readAllBytes(fichier.toPath()));
        }

        @Override
        public void telecharger(String fichierId, File destination) throws IOException {
            Files.write(destination.toPath(), contenus.get(fichierId));
        }
    }

    private static Map<String, String> map(String... kv) {
        Map<String, String> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put(kv[i], kv[i + 1]);
        return m;
    }

    @Test
    public void deuxTelephones_partagentChantiersEtSaisies_sansRienEcraser() throws Exception {
        FauxDrive drive = new FauxDrive();
        File tel1 = Files.createTempDirectory("tel1").toFile();
        File tel2 = Files.createTempDirectory("tel2").toFile();
        LocalStore gregory = new LocalStore(tel1);
        LocalStore papa = new LocalStore(tel2);

        // Grégory crée un chantier et une dépense, puis synchronise.
        Chantier c = gregory.creerChantier("Salle de bain", "Dupont", "", Chantier.MODE_REEL, "Grégory");
        gregory.ajouter(c.id, Event.create(Event.DEPENSE, c.id, "Grégory", map("montant_centimes", "4590")));
        Synchro.Bilan b1 = Synchro.synchroniser(tel1, drive);
        assertTrue(b1.envoyes >= 3);

        // Le téléphone de papa récupère tout.
        Synchro.Bilan b2 = Synchro.synchroniser(tel2, drive);
        assertTrue(b2.recus >= 3);
        assertEquals(4590, papa.charger(c.id).totalDepensesCentimes());

        // Hors réseau, chacun ajoute quelque chose de son côté.
        papa.ajouter(c.id, Event.create(Event.STATUT, c.id, "Papa", map("valeur", Chantier.EN_COURS)));
        gregory.ajouter(c.id, Event.create(Event.DEPENSE, c.id, "Grégory", map("montant_centimes", "1000")));

        Synchro.synchroniser(tel2, drive);
        Synchro.synchroniser(tel1, drive);
        Synchro.synchroniser(tel2, drive);

        // Les deux téléphones voient exactement la même chose.
        Chantier vu1 = new LocalStore(tel1).charger(c.id);
        Chantier vu2 = new LocalStore(tel2).charger(c.id);
        assertEquals(Chantier.EN_COURS, vu1.statut);
        assertEquals(Chantier.EN_COURS, vu2.statut);
        assertEquals(5590, vu1.totalDepensesCentimes());
        assertEquals(5590, vu2.totalDepensesCentimes());

        // Une synchro de plus n'envoie ni ne reçoit rien.
        Synchro.Bilan rien = Synchro.synchroniser(tel1, drive);
        assertEquals(0, rien.envoyes);
        assertEquals(0, rien.recus);
    }

    @Test
    public void libererDeLaPlace_puisRecupererAlaDemande() throws Exception {
        FauxDrive drive = new FauxDrive();
        File tel = Files.createTempDirectory("tel").toFile();
        LocalStore store = new LocalStore(tel);
        Chantier c = store.creerChantier("Terrasse", "Lucas", "", Chantier.MODE_REEL, "Grégory");
        File photo = Files.createTempFile("photo", ".jpg").toFile();
        Files.write(photo.toPath(), new byte[300_000]);
        String chemin = store.importerFichier(c.id, "Photos", photo, "Photo 1", "jpg");
        Synchro.synchroniser(tel, drive);

        // Place libérée : le fichier quitte le téléphone, il est noté « sur le Drive ».
        assertEquals(300_000, store.liberer(c.id, chemin));
        assertEquals(null, store.fichier(c.id, chemin));
        assertTrue(store.surLeDriveSeulement(c.id, chemin));

        // La synchro ne le retélécharge pas et ne supprime rien sur le Drive.
        Synchro.Bilan b = Synchro.synchroniser(tel, drive);
        assertEquals(0, b.recus);
        assertEquals(0, b.envoyes);
        assertEquals(null, store.fichier(c.id, chemin));

        // À la demande, il revient.
        File dest = store.emplacement(c.id, chemin);
        assertTrue(Synchro.recuperer(drive, store.cheminDepuisRacine(c.id, chemin), dest));
        store.recupere(c.id, chemin);
        assertEquals(300_000, store.fichier(c.id, chemin).length());
        assertEquals(false, store.surLeDriveSeulement(c.id, chemin));
        assertEquals(false, Synchro.recuperer(drive, store.cheminDepuisRacine(c.id, "Photos/absente.jpg"), new File(tel, "x.jpg")));
    }

    @Test
    public void fichiersTemporairesEtCachesIgnores() {
        assertTrue(Synchro.ignorer(".abc.json.tmp"));
        assertTrue(Synchro.ignorer(".cache"));
        assertEquals(false, Synchro.ignorer("20261017-101010_depense_ab12cd34.json"));
        assertEquals(Collections.emptyList(), new ArrayList<String>());
    }
}
