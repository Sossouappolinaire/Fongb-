package bj.fongbe.academie;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.HashSet;
import java.util.Set;
import java.util.TimeZone;

/**
 * Progression locale : leçons terminées, mots appris, points et série de jours.
 */
public class Progress {

    private static SharedPreferences p(Context c) {
        return c.getSharedPreferences("fongbe_progress", Context.MODE_PRIVATE);
    }

    private static Set<String> set(Context c, String key) {
        return new HashSet<String>(p(c).getStringSet(key, new HashSet<String>()));
    }

    // ----- Leçons -----

    public static boolean isDone(Context c, int lesson) {
        return set(c, "done").contains(String.valueOf(lesson));
    }

    public static int doneCount(Context c) {
        return set(c, "done").size();
    }

    public static void markDone(Context c, int lesson) {
        Set<String> s = set(c, "done");
        if (s.add(String.valueOf(lesson))) {
            p(c).edit()
                .putStringSet("done", s)
                .putInt("points", getPoints(c) + 10)
                .apply();
        }
    }

    // ----- Mots appris -----

    public static boolean isLearned(Context c, String key) {
        return set(c, "learned").contains(key);
    }

    public static void setLearned(Context c, String key, boolean learned) {
        Set<String> s = set(c, "learned");
        if (learned) {
            if (s.add(key)) {
                p(c).edit()
                    .putStringSet("learned", s)
                    .putInt("points", getPoints(c) + 2)
                    .apply();
            }
        } else if (s.remove(key)) {
            p(c).edit().putStringSet("learned", s).apply();
        }
    }

    public static int learnedCount(Context c) {
        return set(c, "learned").size();
    }

    public static int learnedInRange(Context c, FongbeData.Entry[] words, int start, int end) {
        Set<String> s = set(c, "learned");
        int n = 0;
        for (int i = start; i < end && i < words.length; i++) {
            if (s.contains(words[i].key)) {
                n++;
            }
        }
        return n;
    }

    // ----- Points et série -----

    public static int getPoints(Context c) {
        return p(c).getInt("points", 0);
    }

    public static int getStreak(Context c) {
        return p(c).getInt("streak", 0);
    }

    public static void touchStreak(Context c) {
        long now = System.currentTimeMillis();
        long today = (now + TimeZone.getDefault().getOffset(now)) / 86400000L;
        long last = p(c).getLong("lastDay", -1L);
        if (last == today) {
            return;
        }
        int streak = (last == today - 1) ? p(c).getInt("streak", 0) + 1 : 1;
        p(c).edit().putLong("lastDay", today).putInt("streak", streak).apply();
    }

    public static void reset(Context c) {
        p(c).edit().clear().apply();
        touchStreak(c);
    }
}
