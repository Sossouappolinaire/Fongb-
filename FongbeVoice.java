package bj.fongbe.academie;

import android.app.Activity;
import android.content.Context;
import android.media.MediaPlayer;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.Normalizer;
import java.util.Locale;

public class FongbeVoice {

    private static MediaPlayer player;

    /** Nom de fichier audio attendu pour un texte français. */
    public static String slug(String s) {
        String t = Normalizer.normalize(s, Normalizer.Form.NFD)
            .replaceAll("\\p{M}", "")
            .toLowerCase(Locale.ROOT);
        t = t.replaceAll("[^a-z0-9]+", "_").replaceAll("^_+|_+$", "");
        if (t.length() > 60) {
            t = t.substring(0, 60).replaceAll("_+$", "");
        }
        if (t.isEmpty()) {
            t = "x";
        }
        if (Character.isDigit(t.charAt(0))) {
            t = "a_" + t;
        }
        return t;
    }

    public static void speak(final Activity a, final FongbeData.Entry e) {

        // 0) Prononciation importée (.mkou), prioritaire
        File imported = Store.audioFile(a, e.key);
        if (imported != null) {
            playFile(a, imported);
            return;
        }

        // 1) Enregistrement natif du projet
        int id = a.getResources().getIdentifier(e.key, "raw", a.getPackageName());
        if (id != 0) {
            stopPlayer();
            player = MediaPlayer.create(a, id);
            startPlayer();
            return;
        }

        // 2) Audio déjà généré (cache) ou génération en ligne
        final File cached = findCached(a, e.fon);
        if (cached != null) {
            playFile(a, cached);
            return;
        }

        if (Config.ONLINE_ENABLED) {
            Toast.makeText(a, "Chargement de la voix fongbé…", Toast.LENGTH_SHORT).show();
            new Thread(() -> {
                try {
                    final File f = synthesize(a, e.fon);
                    a.runOnUiThread(() -> playFile(a, f));
                } catch (Exception ex) {
                    a.runOnUiThread(() -> Toast.makeText(
                        a,
                        "Voix fongbé indisponible pour « " + e.fr + " ». "
                        + "Ajoute l'enregistrement " + e.key + ".mp3 (voir LISTE_AUDIOS.txt).",
                        Toast.LENGTH_LONG
                    ).show());
                }
            }).start();
            return;
        }

        Toast.makeText(
            a,
            "Pas encore d'audio fongbé pour « " + e.fr + " » (fichier attendu : " + e.key + ".mp3).",
            Toast.LENGTH_LONG
        ).show();
    }

    // ----- Génération de la voix (bloquant : hors thread principal) -----

    /** Renvoie le fichier audio du texte fongbé : cache, sinon API Hugging Face, puis Space. */
    public static File synthesize(Context ctx, String fon) throws Exception {

        File cached = findCached(ctx, fon);
        if (cached != null) {
            return cached;
        }

        Exception last = null;

        // a) API Hugging Face (modèle Meta MMS) avec le jeton de l'utilisateur
        String token = ctx.getSharedPreferences("fongbe", Context.MODE_PRIVATE)
            .getString("hf_token", "");
        if (token != null && token.trim().length() > 0) {
            try {
                JSONObject body = new JSONObject().put("inputs", fon);
                File f = postForAudio(ctx, fon, Config.HF_MODEL_URL, body.toString(),
                    token.trim(), true);
                if (f != null) {
                    return f;
                }
            } catch (Exception ex) {
                last = ex;
            }
        }

        // b) Space public
        if (Config.SPACE_URL.length() > 0) {
            try {
                File f = viaSpace(ctx, fon);
                if (f != null) {
                    return f;
                }
            } catch (Exception ex) {
                last = ex;
            }
        }

        throw last != null ? last : new Exception("pas d'audio");
    }

    /**
     * POST JSON, réponse attendue = audio brut. {@code secret} : jeton Bearer
     * Hugging Face ({@code hf} = true).
     */
    private static File postForAudio(Context ctx, String fon, String url, String json,
                                     String secret, boolean hf) throws Exception {

        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setRequestMethod("POST");
        c.setDoOutput(true);
        c.setConnectTimeout(20000);
        c.setReadTimeout(90000);
        c.setRequestProperty("Content-Type", "application/json");
        if (secret != null) {
            if (hf) {
                c.setRequestProperty("Authorization", "Bearer " + secret);
                c.setRequestProperty("x-wait-for-model", "true");
            } else {
                c.setRequestProperty("X-API-Key", secret);
            }
        }

        OutputStream os = c.getOutputStream();
        os.write(json.getBytes("UTF-8"));
        os.close();

        if (c.getResponseCode() != 200) {
            throw new Exception("HTTP " + c.getResponseCode());
        }
        String type = c.getContentType() == null ? "" : c.getContentType().toLowerCase(Locale.ROOT);
        if (!type.startsWith("audio") && !type.contains("octet-stream")) {
            throw new Exception("réponse non audio");
        }

        File out = cacheFile(ctx, fon, extFor(type));
        saveStream(c.getInputStream(), out);
        return out;
    }

    private static File viaSpace(Context ctx, String fon) throws Exception {
        String url = null;
        String[] prefixes = {"/gradio_api/call/", "/call/"};
        for (String p : prefixes) {
            try {
                url = tryGradio(Config.SPACE_URL, p, fon);
            } catch (Exception ex) {
                url = null;
            }
            if (url != null) {
                break;
            }
        }
        if (url == null) {
            return null;
        }
        File out = cacheFile(ctx, fon, "wav");
        download(url, out);
        return out;
    }

    private static String extFor(String contentType) {
        if (contentType.contains("flac")) {
            return "flac";
        }
        if (contentType.contains("mpeg") || contentType.contains("mp3")) {
            return "mp3";
        }
        if (contentType.contains("ogg")) {
            return "ogg";
        }
        return "wav";
    }

    /** Type MIME d'un fichier audio du cache (pour l'enregistrement dans Téléchargements). */
    public static String mimeFor(File f) {
        String n = f.getName().toLowerCase(Locale.ROOT);
        if (n.endsWith(".flac")) {
            return "audio/flac";
        }
        if (n.endsWith(".mp3")) {
            return "audio/mpeg";
        }
        if (n.endsWith(".ogg")) {
            return "audio/ogg";
        }
        return "audio/wav";
    }

    // ----- Gradio (Space Hugging Face) -----

    private static String tryGradio(String base, String prefix, String fon) throws Exception {

        String api = Config.API_NAME;

        HttpURLConnection c = (HttpURLConnection) new URL(base + prefix + api).openConnection();
        c.setRequestMethod("POST");
        c.setDoOutput(true);
        c.setConnectTimeout(20000);
        c.setReadTimeout(60000);
        c.setRequestProperty("Content-Type", "application/json");

        JSONObject body = new JSONObject();
        body.put("data", new JSONArray().put(fon));

        OutputStream os = c.getOutputStream();
        os.write(body.toString().getBytes("UTF-8"));
        os.close();

        if (c.getResponseCode() != 200) {
            return null;
        }

        String eventId = new JSONObject(readAll(c.getInputStream())).getString("event_id");

        HttpURLConnection g = (HttpURLConnection)
            new URL(base + prefix + api + "/" + eventId).openConnection();
        g.setConnectTimeout(20000);
        g.setReadTimeout(90000);

        String sse = readAll(g.getInputStream());
        boolean complete = false;

        for (String line : sse.split("\n")) {

            if (line.startsWith("event: complete")) {
                complete = true;

            } else if (complete && line.startsWith("data: ")) {

                JSONArray arr = new JSONArray(line.substring(6));
                Object first = arr.get(0);

                if (first instanceof JSONObject) {
                    JSONObject o = (JSONObject) first;
                    String url = o.optString("url", "");
                    if (url.length() == 0 && o.optString("path", "").length() > 0) {
                        String filePrefix = prefix.substring(0, prefix.length() - 5) + "file=";
                        url = base + filePrefix + o.getString("path");
                    }
                    if (url.length() > 0) {
                        return url;
                    }
                } else if (first instanceof String) {
                    String s = (String) first;
                    if (s.startsWith("http")) {
                        return s;
                    }
                }
                return null;
            }
        }
        return null;
    }

    private static String readAll(InputStream in) throws Exception {
        BufferedReader r = new BufferedReader(new InputStreamReader(in, "UTF-8"));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = r.readLine()) != null) {
            sb.append(line).append('\n');
        }
        r.close();
        return sb.toString();
    }

    private static void download(String url, File out) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(20000);
        c.setReadTimeout(60000);
        saveStream(c.getInputStream(), out);
    }

    private static void saveStream(InputStream in, File out) throws Exception {
        File tmp = new File(out.getPath() + ".part");
        FileOutputStream fo = new FileOutputStream(tmp);
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) {
            fo.write(buf, 0, n);
        }
        fo.close();
        in.close();
        if (tmp.length() == 0 || !tmp.renameTo(out)) {
            tmp.delete();
            throw new Exception("audio vide");
        }
    }

    private static File cacheFile(Context ctx, String fon, String ext) {
        return new File(ctx.getCacheDir(), "fon_" + Integer.toHexString(fon.hashCode()) + "." + ext);
    }

    /** Audio déjà généré pour ce texte, quel que soit son format. */
    public static File findCached(Context ctx, String fon) {
        String[] exts = {"wav", "flac", "mp3", "ogg"};
        for (String ext : exts) {
            File f = cacheFile(ctx, fon, ext);
            if (f.exists() && f.length() > 0) {
                return f;
            }
        }
        return null;
    }

    // ----- Lecture -----

    /** Joue un fichier audio déjà généré (utilisé par l'écran « Voix fongbé »). */
    public static void playCached(Activity a, File f) {
        playFile(a, f);
    }

    private static void playFile(Activity a, File f) {
        try {
            stopPlayer();
            player = new MediaPlayer();
            player.setDataSource(f.getAbsolutePath());
            player.prepare();
            startPlayer();
        } catch (Exception ex) {
            f.delete();
            Toast.makeText(a, "Lecture audio impossible.", Toast.LENGTH_SHORT).show();
        }
    }

    private static void startPlayer() {
        if (player == null) {
            return;
        }
        player.setOnCompletionListener(mp -> {
            mp.release();
            if (player == mp) {
                player = null;
            }
        });
        player.start();
    }

    public static void stopPlayer() {
        if (player != null) {
            try {
                player.stop();
            } catch (Exception ignored) {
            }
            player.release();
            player = null;
        }
    }
}
