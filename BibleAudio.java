package bj.fongbe.academie;

import android.app.Activity;

import android.widget.Toast;

import java.io.File;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Lecture des versets et des mots avec l'enregistrement réel de la Bible en fon (mp3 du chapitre,
 * comme dans l'écran Bible). Un verset se joue seul, de son début à son début suivant.
 * Un mot joue le verset où il se trouve : le mp3 ne contient pas de mots séparés.
 * Les positions des versets sont partagées avec l'écran Bible (fichier <usfm>.times),
 * donc un verset calé à la main là-bas est aussi bien joué ici.
 */
public class BibleAudio {

    private static ChapterPlayer player;
    private static String loadedUsfm = "";

    private static final Set<String> preparing = new HashSet<String>();

    /** Joue le verset (ou le mot) ; un mot pas encore découpé est d'abord préparé, puis joué. */
    public static void play(final Activity a, final FongbeData.Entry e) {
        final File wordClip = WordSlicer.clipFor(a, e);
        if (wordClip != null) {
            if (wordClip.exists() && wordClip.length() > 44) {
                if (player != null) {
                    player.pause();
                }
                FongbeVoice.playCached(a, wordClip);
                return;
            }
            // Mot pas encore découpé : téléchargement du chapitre + découpe, puis lecture.
            final String[] wk = e.key.split("_");
            final String book = wk[1].toUpperCase(Locale.ROOT);
            int ch0 = 0;
            int v0 = 0;
            try {
                ch0 = Integer.parseInt(wk[2]);
                v0 = Integer.parseInt(wk[3]);
            } catch (NumberFormatException ex) {
                playVerse(a, e);
                return;
            }
            final int chap = ch0;
            final int ver = v0;
            final String id = book + "." + chap + "." + ver;
            synchronized (preparing) {
                if (!preparing.add(id)) {
                    return;
                }
            }
            Toast.makeText(a, "Préparation du mot (téléchargement + découpe)…",
                Toast.LENGTH_SHORT).show();
            new Thread(() -> {
                final boolean ok = WordSlicer.sliceVerseNow(a, book, chap, ver);
                synchronized (preparing) {
                    preparing.remove(id);
                }
                a.runOnUiThread(() -> {
                    if (ok && wordClip.exists() && wordClip.length() > 44) {
                        if (player != null) {
                            player.pause();
                        }
                        FongbeVoice.playCached(a, wordClip);
                    } else {
                        playVerse(a, e); // repli : on joue le verset
                    }
                });
            }).start();
            return;
        }
        playVerse(a, e);
    }

    private static void playVerse(final Activity a, final FongbeData.Entry e) {
        String[] k = e.key.split("_");
        if (k.length < 4 || !(k[0].equals("bv") || k[0].equals("bw"))) {
            FongbeVoice.speak(a, e);
            return;
        }
        final String usfm = k[1].toUpperCase(Locale.ROOT) + "." + k[2];
        final int verse;
        try {
            verse = Integer.parseInt(k[3]);
        } catch (NumberFormatException ex) {
            FongbeVoice.speak(a, e);
            return;
        }

        FonBible.Chapter ch = null;
        try {
            ch = FonBible.load(a, FonBible.VERSION, "FON13", usfm, false);
        } catch (Exception ignored) {
        }
        if (ch == null || ch.audioUrl.isEmpty()) {
            FongbeVoice.speak(a, e);
            return;
        }
        final FonBible.Chapter chapter = ch;

        int found = -1;
        for (int i = 0; i < chapter.verses.size(); i++) {
            if (chapter.verses.get(i)[0].trim().equals(String.valueOf(verse))) {
                found = i;
                break;
            }
        }
        if (found < 0) {
            FongbeVoice.speak(a, e);
            return;
        }
        final int index = found;

        FongbeVoice.stopPlayer();
        if (player != null) {
            player.pause();
        }

        FongbeVoice.ensureAudio(a, chapter.audioUrl, f -> {
            if (!prepare(a, usfm, chapter, f)) {
                FongbeVoice.speak(a, e);
                return;
            }
            player.playVerse(index);
        });
    }

    /**
     * Au lancement d'une leçon : télécharge le mp3 du chapitre dans le dossier audio du téléphone
     * s'il n'y est pas encore, pour pouvoir l'écouter ensuite sans Internet.
     */
    public static void prefetch(Activity a, int unit) {
        if (unit < 0 || unit >= BibleCourse.UNITS.length) {
            return;
        }
        FonBible.Chapter ch = null;
        try {
            ch = FonBible.load(a, FonBible.VERSION, "FON13", BibleCourse.UNITS[unit].usfm(), false);
        } catch (Exception ignored) {
        }
        if (ch == null || ch.audioUrl.isEmpty()) {
            return;
        }
        final int u = unit;
        File f = FongbeVoice.audioFile(a, ch.audioUrl);
        if (f.exists() && f.length() > 0) {
            WordSlicer.ensure(a, u, false, null);
            return;
        }
        FongbeVoice.ensureAudio(a, ch.audioUrl, file -> WordSlicer.ensure(a, u, false, null));
    }

    private static boolean prepare(final Activity a, final String usfm,
                                   FonBible.Chapter chapter, File f) {
        if (player != null && usfm.equals(loadedUsfm)) {
            return true;
        }
        stop();
        final ChapterPlayer p = new ChapterPlayer();
        if (!p.load(f)) {
            return false;
        }
        p.verseOnly = true;
        p.loop = false;

        final int n = chapter.verses.size();
        final int[] lens = new int[n];
        for (int i = 0; i < n; i++) {
            lens[i] = chapter.verses.get(i)[1].length();
        }
        final int dur = p.duration();

        FonBible.Times t = FonBible.loadTimes(a, usfm, n);
        if (t != null) {
            p.setStarts(t.starts);
        } else {
            final int[] est = VerseTiming.estimate(dur, lens);
            p.setStarts(est);
            // Comme dans l'écran Bible : on repère les pauses entre versets en arrière-plan.
            final File file = f;
            new Thread(() -> {
                final float[] lv = ChapterPlayer.levels(file, 20);
                a.runOnUiThread(() -> {
                    if (lv != null && lv.length > 50 && player == p) {
                        int[] snapped = VerseTiming.snap(lv, 20, est, dur);
                        p.setStarts(snapped);
                        FonBible.saveTimes(a, usfm, snapped, 1);
                    }
                });
            }).start();
        }

        player = p;
        loadedUsfm = usfm;
        return true;
    }

    public static void stop() {
        if (player != null) {
            player.release();
            player = null;
        }
        loadedUsfm = "";
    }
}
