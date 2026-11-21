package bj.fongbe.academie;

/**
 * Modèles de données du cours. Le contenu (leçons, mots, exercices) n'est plus écrit ici :
 * il vient entièrement de la Bible en fon, voir BibleCourse.
 */
public class FongbeData {

    public static class Entry {
        public final String fr;
        public final String fon;
        public final String key;

        public Entry(String fr, String fon, String key) {
            this.fr = fr;
            this.fon = fon;
            this.key = key;
        }
    }

    public static class Lesson {
        public final String title;
        public final String intro;
        public final Entry[] phrases;

        public Lesson(String title, String intro, Entry[] phrases) {
            this.title = title;
            this.intro = intro;
            this.phrases = phrases;
        }
    }

    public static final String CREDIT =
        "Texte : Bible en fon (Fon13, Bible.com). Français : Louis Segond 1910 (domaine public).";

    // Contenu actuel : construit par BibleCourse à partir des chapitres de la Bible en fon.
    public static Entry[] WORDS = new Entry[0];
    public static Lesson[] LESSONS = new Lesson[0];

    public static synchronized void reload(android.content.Context c) {
        BibleCourse.rebuild(c);
    }
}
