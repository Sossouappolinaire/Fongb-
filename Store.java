package bj.fongbe.academie;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Tout est enregistré sur le téléphone, dans le dossier « FongbeKouam2 » :
 *  - users.json              : comptes (PIN stocké sous forme de hash)
 *  - progress_<id>.json      : progression de chaque utilisateur
 *  - words_extra.json        : mots importés depuis un fichier .mkou
 *  - lessons_extra.json      : leçons importées depuis un fichier .mkou
 *  - audio/<clé>.mp3|ogg|wav : prononciations importées
 */
public class Store {

    public static final String ADMIN_EMAIL = "sossoukouam@gmail.com";
    public static final String FOLDER = "FongbeKouam2";

    // ------------------------------------------------------------------
    // Fichiers
    // ------------------------------------------------------------------

    public static File dir(Context c) {
        File base = c.getExternalFilesDir(null);
        if (base == null) {
            base = c.getFilesDir();
        }
        File d = new File(base, FOLDER);
        if (!d.exists()) {
            d.mkdirs();
        }
        return d;
    }

    public static File file(Context c, String name) {
        return new File(dir(c), name);
    }

    public static File audioDir(Context c) {
        File d = new File(dir(c), "audio");
        if (!d.exists()) {
            d.mkdirs();
        }
        return d;
    }

    public static byte[] readAll(InputStream in, long cap) throws IOException {
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        long total = 0;
        int n;
        while ((n = in.read(buf)) != -1) {
            total += n;
            if (total > cap) {
                throw new IOException("Fichier trop volumineux.");
            }
            o.write(buf, 0, n);
        }
        return o.toByteArray();
    }

    public static String readText(File f) {
        if (!f.exists()) {
            return null;
        }
        try {
            FileInputStream in = new FileInputStream(f);
            try {
                return new String(readAll(in, 30L * 1024 * 1024), "UTF-8");
            } finally {
                in.close();
            }
        } catch (Exception e) {
            return null;
        }
    }

    public static void writeText(File f, String s) {
        try {
            File tmp = new File(f.getParentFile(), f.getName() + ".tmp");
            FileOutputStream o = new FileOutputStream(tmp);
            o.write(s.getBytes("UTF-8"));
            o.close();
            if (!tmp.renameTo(f)) {
                f.delete();
                tmp.renameTo(f);
            }
        } catch (Exception ignored) {
        }
    }

    public static JSONArray readArray(Context c, String name) {
        String t = readText(file(c, name));
        if (t == null) {
            return new JSONArray();
        }
        try {
            return new JSONArray(t);
        } catch (Exception e) {
            return new JSONArray();
        }
    }

    public static void writeArray(Context c, String name, JSONArray a) {
        writeText(file(c, name), a.toString());
    }

    // ------------------------------------------------------------------
    // Comptes et session
    // ------------------------------------------------------------------

    private static SharedPreferences session(Context c) {
        return c.getSharedPreferences("fongbe_session", Context.MODE_PRIVATE);
    }

    public static boolean isLoggedIn(Context c) {
        return session(c).getString("uid", null) != null;
    }

    public static String currentUserId(Context c) {
        return session(c).getString("uid", "guest");
    }

    public static void logout(Context c) {
        session(c).edit().remove("uid").apply();
    }

    private static void setUser(Context c, String id) {
        session(c).edit().putString("uid", id).apply();
    }

    public static JSONObject currentUser(Context c) {
        String id = currentUserId(c);
        JSONArray arr = readArray(c, "users.json");
        for (int i = 0; i < arr.length(); i++) {
            JSONObject u = arr.optJSONObject(i);
            if (u != null && id.equals(u.optString("id"))) {
                return u;
            }
        }
        return null;
    }

    public static boolean isAdminEmail(String s) {
        return s != null && s.trim().equalsIgnoreCase(ADMIN_EMAIL);
    }

    private static String digits(String s) {
        return s == null ? "" : s.replaceAll("[^0-9]", "");
    }

    private static String hash(String salt, String pin) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] d = md.digest((salt + pin).getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder();
            for (byte b : d) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    private static String newSalt() {
        byte[] b = new byte[8];
        new SecureRandom().nextBytes(b);
        StringBuilder sb = new StringBuilder();
        for (byte x : b) {
            sb.append(String.format("%02x", x));
        }
        return sb.toString();
    }

    private static void ensureAdmin(Context c) {
        JSONArray arr = readArray(c, "users.json");
        for (int i = 0; i < arr.length(); i++) {
            JSONObject u = arr.optJSONObject(i);
            if (u != null && "admin".equals(u.optString("id"))) {
                return;
            }
        }
        try {
            JSONObject u = new JSONObject();
            u.put("id", "admin");
            u.put("nom", "Administrateur");
            u.put("prenom", "");
            u.put("email", ADMIN_EMAIL);
            u.put("numero", "");
            u.put("admin", true);
            u.put("created", System.currentTimeMillis());
            arr.put(u);
            writeArray(c, "users.json", arr);
        } catch (Exception ignored) {
        }
    }

    /** Retourne null si OK, sinon le message d'erreur. */
    public static String register(Context c, String nom, String prenom, String email,
                                  String numero, String pin) {
        nom = nom.trim();
        prenom = prenom.trim();
        email = email.trim().toLowerCase(Locale.ROOT);

        // L'administrateur entre directement, sans inscription ni PIN.
        if (isAdminEmail(email)) {
            ensureAdmin(c);
            setUser(c, "admin");
            return null;
        }

        if (nom.isEmpty() || prenom.isEmpty()) {
            return "Entre ton nom et ton prénom.";
        }
        String num = digits(numero);
        if (num.length() < 8) {
            return "Numéro de téléphone invalide.";
        }
        if (!pin.matches("[0-9]{4,6}")) {
            return "Le code PIN doit avoir 4 à 6 chiffres.";
        }
        if (!email.isEmpty() && !email.matches(".+@.+\\..+")) {
            return "Adresse e-mail invalide.";
        }

        JSONArray arr = readArray(c, "users.json");
        for (int i = 0; i < arr.length(); i++) {
            JSONObject u = arr.optJSONObject(i);
            if (u != null && num.equals(u.optString("id"))) {
                return "Ce numéro est déjà inscrit. Connecte-toi.";
            }
        }

        try {
            String salt = newSalt();
            JSONObject u = new JSONObject();
            u.put("id", num);
            u.put("nom", nom);
            u.put("prenom", prenom);
            u.put("email", email);
            u.put("numero", num);
            u.put("salt", salt);
            u.put("hash", hash(salt, pin));
            u.put("created", System.currentTimeMillis());
            arr.put(u);
            writeArray(c, "users.json", arr);
        } catch (Exception e) {
            return "Impossible d'enregistrer le compte.";
        }
        setUser(c, num);
        return null;
    }

    /** Retourne null si OK, sinon le message d'erreur. */
    public static String login(Context c, String ident, String pin) {
        ident = ident.trim();

        if (isAdminEmail(ident)) {
            ensureAdmin(c);
            setUser(c, "admin");
            return null;
        }
        if (ident.isEmpty()) {
            return "Entre ton numéro ou ton e-mail.";
        }

        boolean byEmail = ident.contains("@");
        String num = digits(ident);

        JSONArray arr = readArray(c, "users.json");
        for (int i = 0; i < arr.length(); i++) {
            JSONObject u = arr.optJSONObject(i);
            if (u == null) {
                continue;
            }
            boolean match = byEmail
                ? ident.equalsIgnoreCase(u.optString("email"))
                : (!num.isEmpty() && num.equals(u.optString("id")));
            if (!match) {
                continue;
            }
            String h = u.optString("hash", "");
            if (!h.isEmpty() && h.equals(hash(u.optString("salt"), pin))) {
                setUser(c, u.optString("id"));
                return null;
            }
            return "Code PIN incorrect.";
        }
        return "Compte introuvable. Inscris-toi d'abord.";
    }

    // ------------------------------------------------------------------
    // Mots et leçons (JSON <-> objets)
    // ------------------------------------------------------------------

    private static String cleanKey(String k, String fr) {
        String s = (k == null ? "" : k.toLowerCase(Locale.ROOT)).replaceAll("[^a-z0-9_]", "");
        if (s.isEmpty()) {
            s = FongbeVoice.slug(fr);
        }
        return s;
    }

    public static FongbeData.Entry entry(JSONObject o) {
        if (o == null) {
            return null;
        }
        String fr = o.optString("fr", "").trim();
        String fon = o.optString("fon", "").trim();
        if (fr.isEmpty() || fon.isEmpty()) {
            return null;
        }
        return new FongbeData.Entry(fr, fon, cleanKey(o.optString("key", ""), fr));
    }

    public static JSONObject entryJson(FongbeData.Entry e) {
        JSONObject o = new JSONObject();
        try {
            o.put("fr", e.fr);
            o.put("fon", e.fon);
            o.put("key", e.key);
        } catch (Exception ignored) {
        }
        return o;
    }

    public static FongbeData.Lesson lesson(JSONObject o) {
        if (o == null) {
            return null;
        }
        String title = o.optString("title", "").trim();
        if (title.isEmpty()) {
            return null;
        }
        String intro = o.optString("intro", "").trim();
        List<FongbeData.Entry> list = new ArrayList<FongbeData.Entry>();
        JSONArray ph = o.optJSONArray("phrases");
        if (ph != null) {
            for (int i = 0; i < ph.length(); i++) {
                FongbeData.Entry e = entry(ph.optJSONObject(i));
                if (e != null) {
                    list.add(e);
                }
            }
        }
        return new FongbeData.Lesson(title, intro, list.toArray(new FongbeData.Entry[0]));
    }

    public static JSONObject lessonJson(FongbeData.Lesson l) {
        JSONObject o = new JSONObject();
        try {
            o.put("title", l.title);
            o.put("intro", l.intro);
            JSONArray a = new JSONArray();
            for (FongbeData.Entry e : l.phrases) {
                a.put(entryJson(e));
            }
            o.put("phrases", a);
        } catch (Exception ignored) {
        }
        return o;
    }

    private static FongbeData.Lesson mergeLesson(FongbeData.Lesson a, FongbeData.Lesson b) {
        String intro = b.intro.isEmpty() ? a.intro : b.intro;
        LinkedHashMap<String, FongbeData.Entry> m = new LinkedHashMap<String, FongbeData.Entry>();
        for (FongbeData.Entry e : a.phrases) {
            m.put(e.key, e);
        }
        for (FongbeData.Entry e : b.phrases) {
            m.put(e.key, e);
        }
        return new FongbeData.Lesson(a.title, intro, m.values().toArray(new FongbeData.Entry[0]));
    }

    private static void mergeInto(List<FongbeData.Lesson> list, FongbeData.Lesson n) {
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).title.equalsIgnoreCase(n.title)) {
                list.set(i, mergeLesson(list.get(i), n));
                return;
            }
        }
        list.add(n);
    }

    /** Mots de base + mots importés (les mots importés remplacent ceux qui ont la même clé). */
    public static FongbeData.Entry[] mergedWords(Context c, FongbeData.Entry[] base) {
        List<FongbeData.Entry> list = new ArrayList<FongbeData.Entry>(Arrays.asList(base));
        Map<String, Integer> index = new HashMap<String, Integer>();
        for (int i = 0; i < list.size(); i++) {
            if (!index.containsKey(list.get(i).key)) {
                index.put(list.get(i).key, i);
            }
        }
        JSONArray a = readArray(c, "words_extra.json");
        for (int i = 0; i < a.length(); i++) {
            FongbeData.Entry e = entry(a.optJSONObject(i));
            if (e == null) {
                continue;
            }
            Integer at = index.get(e.key);
            if (at != null) {
                list.set(at, e);
            } else {
                index.put(e.key, list.size());
                list.add(e);
            }
        }
        return list.toArray(new FongbeData.Entry[0]);
    }

    public static FongbeData.Lesson[] mergedLessons(Context c, FongbeData.Lesson[] base) {
        List<FongbeData.Lesson> list = new ArrayList<FongbeData.Lesson>(Arrays.asList(base));
        JSONArray a = readArray(c, "lessons_extra.json");
        for (int i = 0; i < a.length(); i++) {
            FongbeData.Lesson l = lesson(a.optJSONObject(i));
            if (l != null) {
                mergeInto(list, l);
            }
        }
        return list.toArray(new FongbeData.Lesson[0]);
    }

    public static File audioFile(Context c, String key) {
        if (key == null || key.isEmpty()) {
            return null;
        }
        File d = new File(dir(c), "audio");
        String[] exts = {"mp3", "ogg", "wav"};
        for (String ext : exts) {
            File f = new File(d, key + "." + ext);
            if (f.exists()) {
                return f;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------
    // Import d'un fichier .mkou
    // ------------------------------------------------------------------

    private static JSONArray arrayFrom(byte[] b, String field) throws Exception {
        String t = new String(b, "UTF-8").trim();
        if (t.startsWith("\uFEFF")) {
            t = t.substring(1);
        }
        if (t.startsWith("[")) {
            return new JSONArray(t);
        }
        JSONArray a = new JSONObject(t).optJSONArray(field);
        return a == null ? new JSONArray() : a;
    }

    /**
     * Fichier .mkou = archive ZIP contenant :
     *   words.json    [{"fr":"...","fon":"...","key":"..."}]
     *   lessons.json  [{"title":"...","intro":"...","phrases":[{fr,fon,key}]}]
     *   audio/<clé>.mp3 (ou .ogg / .wav)
     * Un simple fichier JSON {"words":[...],"lessons":[...]} est aussi accepté.
     * Le contenu est FUSIONNÉ avec l'existant.
     */
    public static String importMkou(Context c, Uri uri) throws Exception {
        InputStream in = c.getContentResolver().openInputStream(uri);
        if (in == null) {
            throw new IOException("Fichier illisible.");
        }
        byte[] data;
        try {
            data = readAll(in, 80L * 1024 * 1024);
        } finally {
            in.close();
        }

        JSONArray words = new JSONArray();
        JSONArray lessons = new JSONArray();
        int audio = 0;

        if (data.length > 4 && data[0] == 'P' && data[1] == 'K') {
            ZipInputStream z = new ZipInputStream(new ByteArrayInputStream(data));
            ZipEntry ze;
            while ((ze = z.getNextEntry()) != null) {
                if (ze.isDirectory()) {
                    continue;
                }
                String n = ze.getName();
                String base = n.substring(n.lastIndexOf('/') + 1).toLowerCase(Locale.ROOT);
                if (base.equals("words.json")) {
                    words = arrayFrom(readAll(z, 20L * 1024 * 1024), "words");
                } else if (base.equals("lessons.json")) {
                    lessons = arrayFrom(readAll(z, 20L * 1024 * 1024), "lessons");
                } else if (base.matches("[a-z0-9_]{1,80}\\.(mp3|ogg|wav)")) {
                    byte[] b = readAll(z, 20L * 1024 * 1024);
                    FileOutputStream o = new FileOutputStream(new File(audioDir(c), base));
                    o.write(b);
                    o.close();
                    audio++;
                }
            }
            z.close();
        } else {
            String t = new String(data, "UTF-8").trim();
            if (t.startsWith("\uFEFF")) {
                t = t.substring(1);
            }
            if (t.startsWith("[")) {
                words = new JSONArray(t);
            } else {
                JSONObject o = new JSONObject(t);
                JSONArray w = o.optJSONArray("words");
                JSONArray l = o.optJSONArray("lessons");
                words = w == null ? new JSONArray() : w;
                lessons = l == null ? new JSONArray() : l;
            }
        }

        if (words.length() == 0 && lessons.length() == 0 && audio == 0) {
            throw new IOException("Ce fichier .mkou ne contient rien d'utilisable.");
        }

        // --- Fusion des mots ---
        Set<String> known = new HashSet<String>();
        for (FongbeData.Entry e : FongbeData.WORDS) {
            known.add(e.key);
        }
        LinkedHashMap<String, FongbeData.Entry> extra = new LinkedHashMap<String, FongbeData.Entry>();
        JSONArray oldWords = readArray(c, "words_extra.json");
        for (int i = 0; i < oldWords.length(); i++) {
            FongbeData.Entry e = entry(oldWords.optJSONObject(i));
            if (e != null) {
                extra.put(e.key, e);
            }
        }
        int added = 0;
        int updated = 0;
        for (int i = 0; i < words.length(); i++) {
            FongbeData.Entry e = entry(words.optJSONObject(i));
            if (e == null) {
                continue;
            }
            if (known.contains(e.key)) {
                updated++;
            } else {
                added++;
                known.add(e.key);
            }
            extra.put(e.key, e);
        }
        JSONArray outWords = new JSONArray();
        for (FongbeData.Entry e : extra.values()) {
            outWords.put(entryJson(e));
        }
        writeArray(c, "words_extra.json", outWords);

        // --- Fusion des leçons ---
        List<FongbeData.Lesson> exLessons = new ArrayList<FongbeData.Lesson>();
        JSONArray oldLessons = readArray(c, "lessons_extra.json");
        for (int i = 0; i < oldLessons.length(); i++) {
            FongbeData.Lesson l = lesson(oldLessons.optJSONObject(i));
            if (l != null) {
                exLessons.add(l);
            }
        }
        int lessonCount = 0;
        for (int i = 0; i < lessons.length(); i++) {
            FongbeData.Lesson l = lesson(lessons.optJSONObject(i));
            if (l != null) {
                mergeInto(exLessons, l);
                lessonCount++;
            }
        }
        JSONArray outLessons = new JSONArray();
        for (FongbeData.Lesson l : exLessons) {
            outLessons.put(lessonJson(l));
        }
        writeArray(c, "lessons_extra.json", outLessons);

        FongbeData.reload(c);

        return "Import terminé ✅\n"
            + added + " nouveau(x) mot(s), " + updated + " mis à jour\n"
            + lessonCount + " leçon(s)\n"
            + audio + " prononciation(s) audio";
    }
}
