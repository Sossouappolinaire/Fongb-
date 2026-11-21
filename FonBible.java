package bj.fongbe.academie;

import android.content.Context;
import android.os.Build;
import android.text.Html;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Bible en fongbé (Fon13) : le texte et le lien mp3 d'un chapitre sont lus dans la page
 * Bible.com du chapitre (comme le fait un navigateur), puis gardés sur le téléphone
 * pour la lecture hors-ligne. Rien n'est stocké dans l'appli elle-même.
 */
public class FonBible {

    public static final String VERSION = "817";

    public static final String[] NAMES = {
        "Genèse", "Exode", "Lévitique", "Nombres", "Deutéronome", "Josué", "Juges", "Ruth",
        "1 Samuel", "2 Samuel", "1 Rois", "2 Rois", "1 Chroniques", "2 Chroniques", "Esdras",
        "Néhémie", "Esther", "Job", "Psaumes", "Proverbes", "Ecclésiaste", "Cantique des cantiques",
        "Ésaïe", "Jérémie", "Lamentations", "Ézéchiel", "Daniel", "Osée", "Joël", "Amos", "Abdias",
        "Jonas", "Michée", "Nahum", "Habacuc", "Sophonie", "Aggée", "Zacharie", "Malachie",
        "Matthieu", "Marc", "Luc", "Jean", "Actes", "Romains", "1 Corinthiens", "2 Corinthiens",
        "Galates", "Éphésiens", "Philippiens", "Colossiens", "1 Thessaloniciens", "2 Thessaloniciens",
        "1 Timothée", "2 Timothée", "Tite", "Philémon", "Hébreux", "Jacques", "1 Pierre", "2 Pierre",
        "1 Jean", "2 Jean", "3 Jean", "Jude", "Apocalypse"
    };

    public static final String[] CODES = {
        "GEN", "EXO", "LEV", "NUM", "DEU", "JOS", "JDG", "RUT", "1SA", "2SA", "1KI", "2KI", "1CH",
        "2CH", "EZR", "NEH", "EST", "JOB", "PSA", "PRO", "ECC", "SNG", "ISA", "JER", "LAM", "EZK",
        "DAN", "HOS", "JOL", "AMO", "OBA", "JON", "MIC", "NAM", "HAB", "ZEP", "HAG", "ZEC", "MAL",
        "MAT", "MRK", "LUK", "JHN", "ACT", "ROM", "1CO", "2CO", "GAL", "EPH", "PHP", "COL", "1TH",
        "2TH", "1TI", "2TI", "TIT", "PHM", "HEB", "JAS", "1PE", "2PE", "1JN", "2JN", "3JN", "JUD",
        "REV"
    };

    public static final int[] CHAPTERS = {
        50, 40, 27, 36, 34, 24, 21, 4, 31, 24, 22, 25, 29, 36, 10, 13, 10, 42, 150, 31, 12, 8, 66,
        52, 5, 48, 12, 14, 3, 9, 1, 4, 7, 3, 3, 3, 2, 14, 4, 28, 16, 24, 21, 28, 16, 16, 13, 6, 6,
        4, 4, 5, 3, 6, 4, 3, 1, 13, 5, 5, 3, 5, 1, 1, 1, 22
    };

    /** Un chapitre : lien mp3 (vide s'il n'y a pas d'audio) et versets {numéro, texte}. */
    public static class Chapter {
        public String audioUrl = "";
        public final List<String[]> verses = new ArrayList<>();
    }

    /** Charge un chapitre (« GEN.1 ») en fon : cache du téléphone d'abord, sinon page Bible.com. */
    public static Chapter load(Context ctx, String usfm) throws Exception {
        return load(ctx, VERSION, "FON13", usfm, true);
    }

    /**
     * Charge un chapitre d'une version donnée (817/FON13 = fon, 93/LSG = Louis Segond 1910).
     * Sans réseau autorisé, renvoie null si le chapitre n'est pas encore sur le téléphone.
     */
    public static Chapter load(Context ctx, String version, String abbr, String usfm,
                               boolean network) throws Exception {
        File dir = new File(ctx.getFilesDir(), "fonbible");
        dir.mkdirs();
        String name = version.equals(VERSION) ? usfm + ".txt" : usfm + "." + abbr + ".txt";
        File cache = new File(dir, name);
        if (cache.exists() && cache.length() > 0) {
            Chapter c = readCache(cache);
            if (!c.verses.isEmpty()) {
                return c;
            }
        }
        if (!network) {
            return null;
        }
        String html = download("https://www.bible.com/bible/" + version + "/" + usfm + "." + abbr);
        Chapter c = parse(html);
        if (c.verses.isEmpty()) {
            throw new Exception("texte introuvable dans la page");
        }
        writeCache(cache, c);
        return c;
    }

    // ----- Téléchargement -----

    private static String download(String page) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(page).openConnection();
        c.setConnectTimeout(20000);
        c.setReadTimeout(30000);
        c.setRequestProperty("User-Agent",
            "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) "
            + "Chrome/120.0 Mobile Safari/537.36");
        c.setRequestProperty("Accept-Language", "fr");
        if (c.getResponseCode() != 200) {
            throw new Exception("HTTP " + c.getResponseCode());
        }
        InputStream in = c.getInputStream();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[16384];
        int n;
        while ((n = in.read(buf)) > 0) {
            out.write(buf, 0, n);
        }
        in.close();
        return out.toString("UTF-8");
    }

    // ----- Lecture de la page -----

    static Chapter parse(String html) {
        Chapter c = new Chapter();
        c.audioUrl = findAudio(html);

        Map<Integer, StringBuilder> map = new LinkedHashMap<>();
        Matcher m = Pattern.compile("<span\\b[^>]*data-usfm=\"([^\"]+)\"[^>]*>").matcher(html);
        while (m.find()) {
            String tag = m.group();
            if (!tag.contains("verse")) {
                continue;
            }
            String u = m.group(1).split("\\+")[0];
            int dot = u.lastIndexOf('.');
            int num;
            try {
                num = Integer.parseInt(u.substring(dot + 1).replaceAll("\\D.*$", ""));
            } catch (Exception e) {
                continue;
            }
            int end = skipSpan(html, m.end());
            String inner = html.substring(m.end(), Math.max(m.end(), end - 7));
            inner = removeSpans(inner, "label", "heading", "note");
            String text = toText(inner);
            if (text.isEmpty()) {
                continue;
            }
            StringBuilder sb = map.get(num);
            if (sb == null) {
                sb = new StringBuilder();
                map.put(num, sb);
            } else {
                sb.append(' ');
            }
            sb.append(text);
        }
        for (Map.Entry<Integer, StringBuilder> e : map.entrySet()) {
            c.verses.add(new String[] {String.valueOf(e.getKey()), e.getValue().toString()});
        }
        return c;
    }

    /** Lien mp3 du chapitre : celui de la version Fon (version_id=817) de préférence. */
    private static String findAudio(String html) {
        String h = html.replace("\\/", "/").replace("\\u0026", "&").replace("&amp;", "&");
        Matcher m = Pattern.compile(
            "(?:https:)?//audio-bible-cdn\\.youversionapi\\.com/[^\"'\\\\\\s<>]+?\\.mp3"
            + "(?:\\?[^\"'\\\\\\s<>]*)?").matcher(h);
        String first = "";
        while (m.find()) {
            String u = m.group();
            if (u.startsWith("//")) {
                u = "https:" + u;
            }
            if (u.contains("version_id=" + VERSION)) {
                return u;
            }
            if (first.isEmpty()) {
                first = u;
            }
        }
        return first;
    }

    /** Index juste après le </span> qui ferme le span dont la balise ouvrante se termine à « from ». */
    private static int skipSpan(String s, int from) {
        Matcher t = Pattern.compile("<span\\b|</span>").matcher(s);
        t.region(from, s.length());
        int depth = 1;
        while (t.find()) {
            if (t.group().equals("</span>")) {
                depth--;
                if (depth == 0) {
                    return t.end();
                }
            } else {
                depth++;
            }
        }
        return s.length();
    }

    /** Retire les spans (avec tout leur contenu) dont la classe contient l'un des mots donnés. */
    private static String removeSpans(String s, String... parts) {
        Matcher m = Pattern.compile("<span\\b[^>]*class=\"([^\"]*)\"[^>]*>").matcher(s);
        StringBuilder out = new StringBuilder();
        int i = 0;
        while (i < s.length() && m.find(i)) {
            boolean kill = false;
            for (String p : parts) {
                if (m.group(1).contains(p)) {
                    kill = true;
                }
            }
            if (!kill) {
                out.append(s, i, m.end());
                i = m.end();
            } else {
                out.append(s, i, m.start());
                i = skipSpan(s, m.end());
            }
        }
        if (i < s.length()) {
            out.append(s.substring(i));
        }
        return out.toString();
    }

    @SuppressWarnings("deprecation")
    private static String toText(String html) {
        CharSequence cs = Build.VERSION.SDK_INT >= 24
            ? Html.fromHtml(html, Html.FROM_HTML_MODE_LEGACY) : Html.fromHtml(html);
        return cs.toString().replace('\u00a0', ' ').replaceAll("\\s+", " ").trim();
    }

    /** Débuts de versets mémorisés : mode 1 = détectés automatiquement, 2 = calés à la main. */
    public static class Times {
        public int[] starts;
        public int mode;
    }

    public static Times loadTimes(Context ctx, String usfm, int verseCount) {
        File f = new File(new File(ctx.getFilesDir(), "fonbible"), usfm + ".times");
        if (!f.exists()) {
            return null;
        }
        try {
            BufferedReader r = new BufferedReader(new InputStreamReader(new FileInputStream(f), "UTF-8"));
            try {
                Times t = new Times();
                t.mode = Integer.parseInt(r.readLine().trim());
                String[] p = r.readLine().trim().split(",");
                if (p.length != verseCount) {
                    return null;
                }
                t.starts = new int[p.length];
                for (int i = 0; i < p.length; i++) {
                    t.starts[i] = Integer.parseInt(p[i].trim());
                }
                return t;
            } finally {
                r.close();
            }
        } catch (Exception e) {
            return null;
        }
    }

    public static void saveTimes(Context ctx, String usfm, int[] starts, int mode) {
        try {
            File dir = new File(ctx.getFilesDir(), "fonbible");
            dir.mkdirs();
            StringBuilder sb = new StringBuilder();
            sb.append(mode).append('\n');
            for (int i = 0; i < starts.length; i++) {
                if (i > 0) {
                    sb.append(',');
                }
                sb.append(starts[i]);
            }
            sb.append('\n');
            Writer w = new OutputStreamWriter(new FileOutputStream(new File(dir, usfm + ".times")), "UTF-8");
            try {
                w.write(sb.toString());
            } finally {
                w.close();
            }
        } catch (Exception e) {
            // pas grave : les positions seront recalculées
        }
    }

    // ----- Cache sur le téléphone : 1re ligne = lien mp3, puis « numéro<TAB>texte » -----

    private static Chapter readCache(File f) throws Exception {
        Chapter c = new Chapter();
        BufferedReader r = new BufferedReader(new InputStreamReader(new FileInputStream(f), "UTF-8"));
        try {
            String first = r.readLine();
            c.audioUrl = first == null ? "" : first.trim();
            String line;
            while ((line = r.readLine()) != null) {
                int tab = line.indexOf('\t');
                if (tab > 0) {
                    c.verses.add(new String[] {line.substring(0, tab), line.substring(tab + 1)});
                }
            }
        } finally {
            r.close();
        }
        return c;
    }

    private static void writeCache(File f, Chapter c) throws Exception {
        Writer w = new OutputStreamWriter(new FileOutputStream(f), "UTF-8");
        try {
            w.write(c.audioUrl + "\n");
            for (String[] v : c.verses) {
                w.write(v[0] + "\t" + v[1] + "\n");
            }
        } finally {
            w.close();
        }
    }
}
