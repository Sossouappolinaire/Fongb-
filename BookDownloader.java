package bj.fongbe.academie;

import android.app.Activity;

import java.util.HashSet;
import java.util.Set;

/**
 * Télécharge d'un coup les mp3 de tous les chapitres d'un livre (ex. Genèse : 50 chapitres)
 * dès qu'on entre dans ce livre. Un chapitre déjà présent est simplement sauté.
 * Un seul livre à la fois ; si on change de livre, le précédent s'arrête.
 */
public class BookDownloader {

    public interface Listener {
        void onProgress(String message);
    }

    private static volatile int token = 0;
    private static volatile int runningBook = -1;
    private static final Set<Integer> finished = new HashSet<Integer>();

    public static void start(final Activity a, final int book, final Listener l) {
        if (book < 0 || book >= FonBible.CODES.length) {
            return;
        }
        synchronized (finished) {
            if (finished.contains(book) || runningBook == book) {
                return;
            }
        }
        final int my = ++token;
        runningBook = book;
        final String name = FonBible.NAMES[book];
        final int total = FonBible.CHAPTERS[book];

        new Thread(() -> {
            int have = 0;
            int noAudio = 0;
            int failsInRow = 0;
            boolean complete = true;
            for (int ch = 1; ch <= total; ch++) {
                if (my != token) {
                    complete = false;
                    break;
                }
                try {
                    FonBible.Chapter c = FonBible.load(a, FonBible.VERSION, "FON13",
                        FonBible.CODES[book] + "." + ch, true);
                    if (c == null || c.audioUrl.isEmpty()) {
                        noAudio++;
                    } else if (FongbeVoice.downloadAudioBlocking(a, c.audioUrl)) {
                        have++;
                        failsInRow = 0;
                    } else {
                        failsInRow++;
                    }
                } catch (Exception ex) {
                    failsInRow++;
                }
                if (failsInRow >= 3) {
                    complete = false;
                    break;
                }
                final String msg = "⬇️ " + name + " : audios " + (have + noAudio) + "/" + total;
                if (l != null) {
                    a.runOnUiThread(() -> l.onProgress(msg));
                }
            }
            if (runningBook == book) {
                runningBook = -1;
            }
            if (complete) {
                synchronized (finished) {
                    finished.add(book);
                }
            }
            final String end = complete
                ? "✅ " + name + " : audios disponibles hors ligne (" + have + "/" + total + ")"
                : (my != token ? "" : "⚠️ " + name + " : téléchargement interrompu (connexion ?)");
            if (l != null) {
                a.runOnUiThread(() -> l.onProgress(end));
            }
        }).start();
    }
}
