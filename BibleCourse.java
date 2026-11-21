package bj.fongbe.academie;

import android.app.Activity;
import android.content.Context;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Tout le contenu enseigné (leçons, mots, exercices, examen) vient de la Bible en fon (Fon13).
 *
 * Chaque unité du parcours = un passage biblique. Le texte fon est téléchargé une fois depuis
 * Bible.com (comme dans l'écran Bible), gardé sur le téléphone, puis utilisé hors-ligne.
 * Le texte français (Louis Segond 1910, domaine public) sert seulement d'aide à la compréhension.
 * Aucun mot de fon n'est écrit à la main dans l'appli.
 */
public class BibleCourse {

    public static final String FR_VERSION = "93";
    public static final String FR_ABBR = "LSG";

    /** Nombre maximum de mots à apprendre par unité. */
    private static final int WORDS_PER_UNIT = 20;

    private static final Pattern TOKEN = Pattern.compile("[\\p{L}\\p{M}]+");

    public static class Unit {
        public final String title;
        public final String intro;
        public final String book;     // code USFM du livre, ex. « JHN »
        public final int chapter;
        public final int from;
        public final int to;

        Unit(String title, String intro, String book, int chapter, int from, int to) {
            this.title = title;
            this.intro = intro;
            this.book = book;
            this.chapter = chapter;
            this.from = from;
            this.to = to;
        }

        public String usfm() {
            return book + "." + chapter;
        }

        public String bookName() {
            for (int i = 0; i < FonBible.CODES.length; i++) {
                if (FonBible.CODES[i].equals(book)) {
                    return FonBible.NAMES[i];
                }
            }
            return book;
        }

        public String passage() {
            return bookName() + " " + chapter + ":" + from + (to > from ? "-" + to : "");
        }
    }

    /** Un verset du parcours : texte fon (Fon13) et texte français (LSG, peut être vide). */
    public static class Verse {
        public final int unit;
        public final String ref;
        public final String fon;
        public final String fr;
        public final String key;

        Verse(int unit, String ref, String fon, String fr, String key) {
            this.unit = unit;
            this.ref = ref;
            this.fon = fon;
            this.fr = fr;
            this.key = key;
        }
    }

    /** Un mot fon pris dans un verset, avec le verset comme contexte. */
    public static class Word extends FongbeData.Entry {
        public final String ref;
        public final String verseFon;
        public final String verseFr;

        Word(String fon, String ref, String verseFon, String verseFr, String key) {
            super(ref + (verseFr.isEmpty() ? "" : " · " + cut(verseFr, 70)), fon, key);
            this.ref = ref;
            this.verseFon = verseFon;
            this.verseFr = verseFr;
        }
    }

    public static final Unit[] UNITS = {
        new Unit("Au commencement",
            "Les premiers mots de la Bible : Dieu crée. Lis chaque verset en fongbé, écoute-le, "
                + "puis apprends ses mots.", "GEN", 1, 1, 5),
        new Unit("La Parole",
            "L'ouverture de l'Évangile de Jean : la Parole était avec Dieu.", "JHN", 1, 1, 5),
        new Unit("Dieu a tant aimé le monde",
            "Le message central de l'Évangile, verset après verset.", "JHN", 3, 16, 21),
        new Unit("Le Notre Père",
            "La prière que Jésus a enseignée à ses disciples.", "MAT", 6, 9, 13),
        new Unit("Les Béatitudes",
            "Jésus dit qui est heureux : un passage court, très répété.", "MAT", 5, 3, 10),
        new Unit("Demandez, cherchez, frappez",
            "Jésus parle de la prière et de la bonté de Dieu.", "MAT", 7, 7, 11),
        new Unit("Demeurez dans mon amour",
            "Jésus parle de l'amour entre ses disciples.", "JHN", 15, 9, 13),
        new Unit("L'amour",
            "Ce que l'amour est, et ce qu'il n'est pas.", "1CO", 13, 4, 8),
    };

    private static volatile Verse[] verses = new Verse[0];
    public static volatile boolean syncing = false;
    public static volatile String error = "";

    public static Verse[] verses() {
        return verses;
    }

    public static List<Verse> versesOf(int unit) {
        List<Verse> out = new ArrayList<Verse>();
        for (Verse v : verses) {
            if (v.unit == unit) {
                out.add(v);
            }
        }
        return out;
    }

    /** Mots (sans doublon) d'un verset, tels qu'écrits dans la Bible en fon. */
    public static List<String> tokens(String text) {
        List<String> out = new ArrayList<String>();
        Matcher m = TOKEN.matcher(text);
        while (m.find()) {
            out.add(m.group());
        }
        return out;
    }

    static String cut(String s, int max) {
        s = s.replace("\n", " ").trim();
        return s.length() > max ? s.substring(0, max - 1) + "…" : s;
    }

    private static int num(String s) {
        try {
            return Integer.parseInt(s.trim());
        } catch (Exception e) {
            return -1;
        }
    }

    private static String verseText(FonBible.Chapter c, int n) {
        if (c == null) {
            return "";
        }
        for (String[] v : c.verses) {
            if (num(v[0]) == n) {
                return v[1];
            }
        }
        return "";
    }

    /**
     * Reconstruit leçons, mots et versets à partir des chapitres déjà sur le téléphone
     * (aucun accès réseau). Les unités sont prises dans l'ordre, jusqu'à la première absente.
     */
    public static synchronized void rebuild(Context ctx) {
        List<Verse> vs = new ArrayList<Verse>();
        List<FongbeData.Lesson> lessons = new ArrayList<FongbeData.Lesson>();
        Map<String, FongbeData.Entry> words = new LinkedHashMap<String, FongbeData.Entry>();

        for (int u = 0; u < UNITS.length; u++) {
            Unit un = UNITS[u];

            FonBible.Chapter fon = null;
            FonBible.Chapter fr = null;
            try {
                fon = FonBible.load(ctx, FonBible.VERSION, "FON13", un.usfm(), false);
            } catch (Exception ignored) {
            }
            try {
                fr = FonBible.load(ctx, FR_VERSION, FR_ABBR, un.usfm(), false);
            } catch (Exception ignored) {
            }
            if (fon == null) {
                break;
            }

            List<FongbeData.Entry> phrases = new ArrayList<FongbeData.Entry>();
            int added = 0;

            for (String[] v : fon.verses) {
                int n = num(v[0]);
                if (n < un.from || n > un.to) {
                    continue;
                }
                String frText = verseText(fr, n);
                String ref = un.bookName() + " " + un.chapter + ":" + n;
                String key = "bv_" + un.book.toLowerCase(Locale.ROOT) + "_" + un.chapter + "_" + n;

                vs.add(new Verse(u, ref, v[1], frText, key));
                phrases.add(new FongbeData.Entry(
                    frText.isEmpty() ? ref : ref + " · " + frText, v[1], key));

                for (String t : tokens(v[1])) {
                    String low = t.toLowerCase(Locale.ROOT);
                    if (added >= WORDS_PER_UNIT || low.length() < 3 || words.containsKey(low)) {
                        continue;
                    }
                    String wkey = "bw_" + un.book.toLowerCase(Locale.ROOT) + "_" + un.chapter
                        + "_" + n + "_" + added;
                    words.put(low, new Word(t, ref, v[1], frText, wkey));
                    added++;
                }
            }

            if (phrases.isEmpty()) {
                break;
            }
            lessons.add(new FongbeData.Lesson(un.title,
                "Passage : " + un.passage() + ". " + un.intro,
                phrases.toArray(new FongbeData.Entry[0])));
        }

        verses = vs.toArray(new Verse[0]);
        FongbeData.LESSONS = lessons.toArray(new FongbeData.Lesson[0]);
        FongbeData.WORDS = words.values().toArray(new FongbeData.Entry[0]);
    }

    /**
     * Télécharge (si besoin) les chapitres du parcours, un par un, en prévenant l'écran
     * à chaque nouvelle unité disponible. Les chapitres déjà présents ne sont pas retéléchargés.
     */
    public static void sync(final Activity a, final Runnable onChange) {
        if (syncing) {
            return;
        }
        syncing = true;
        error = "";
        new Thread(() -> {
            for (Unit un : UNITS) {
                try {
                    FonBible.load(a, FonBible.VERSION, "FON13", un.usfm(), true);
                } catch (Exception e) {
                    error = un.passage() + " : " + e.getMessage();
                    break;
                }
                try {
                    FonBible.load(a, FR_VERSION, FR_ABBR, un.usfm(), true);
                } catch (Exception ignored) {
                    // le français est une aide : on continue sans lui
                }
                rebuild(a);
                if (onChange != null) {
                    a.runOnUiThread(onChange);
                }
            }
            syncing = false;
            if (onChange != null) {
                a.runOnUiThread(onChange);
            }
        }).start();
    }
}
