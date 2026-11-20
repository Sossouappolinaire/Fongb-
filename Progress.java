package bj.fongbe.academie;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.util.HashSet;
import java.util.Set;
import java.util.TimeZone;

/**
 * Progression de l'utilisateur connecté, enregistrée dans le dossier FongbeKouam2
 * (fichier progress_<id>.json) : leçons terminées, mots appris, points, série.
 */
public class Progress {

    private static File fileFor(Context c) {
        return Store.file(c, "progress_" + Store.currentUserId(c) + ".json");
    }

    private static JSONObject load(Context c) {
        try {
            String t = Store.readText(fileFor(c));
            if (t != null) {
                return new JSONObject(t);
            }
        } catch (Exception ignored) {
        }
        return new JSONObject();
    }

    private static void save(Context c, JSONObject o) {
        Store.writeText(fileFor(c), o.toString());
    }

    private static void put(JSONObject o, String k, Object v) {
        try {
            o.put(k, v);
        } catch (Exception ignored) {
        }
    }

    private static Set<String> set(JSONObject o, String key) {
        Set<String> s = new HashSet<String>();
        JSONArray a = o.optJSONArray(key);
        if (a != null) {
            for (int i = 0; i < a.length(); i++) {
                s.add(a.optString(i));
            }
        }
        return s;
    }

    private static void putSet(JSONObject o, String key, Set<String> s) {
        JSONArray a = new JSONArray();
        for (String x : s) {
            a.put(x);
        }
        put(o, key, a);
    }

    // ----- Leçons -----

    public static boolean isDone(Context c, int lesson) {
        return set(load(c), "done").contains(String.valueOf(lesson));
    }

    public static int doneCount(Context c) {
        return set(load(c), "done").size();
    }

    public static void markDone(Context c, int lesson) {
        JSONObject o = load(c);
        Set<String> s = set(o, "done");
        if (s.add(String.valueOf(lesson))) {
            putSet(o, "done", s);
            put(o, "points", o.optInt("points", 0) + 10);
            save(c, o);
        }
    }

    // ----- Mots appris -----

    public static boolean isLearned(Context c, String key) {
        return set(load(c), "learned").contains(key);
    }

    public static void setLearned(Context c, String key, boolean learned) {
        JSONObject o = load(c);
        Set<String> s = set(o, "learned");
        if (learned) {
            if (s.add(key)) {
                putSet(o, "learned", s);
                put(o, "points", o.optInt("points", 0) + 2);
                save(c, o);
            }
        } else if (s.remove(key)) {
            putSet(o, "learned", s);
            save(c, o);
        }
    }

    public static int learnedCount(Context c) {
        return set(load(c), "learned").size();
    }

    public static int learnedInRange(Context c, FongbeData.Entry[] words, int start, int end) {
        Set<String> s = set(load(c), "learned");
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
        return load(c).optInt("points", 0);
    }

    public static int getStreak(Context c) {
        return load(c).optInt("streak", 0);
    }

    public static void touchStreak(Context c) {
        JSONObject o = load(c);
        long now = System.currentTimeMillis();
        long today = (now + TimeZone.getDefault().getOffset(now)) / 86400000L;
        long last = o.optLong("lastDay", -1L);
        if (last == today) {
            return;
        }
        int streak = (last == today - 1) ? o.optInt("streak", 0) + 1 : 1;
        put(o, "lastDay", today);
        put(o, "streak", streak);
        save(c, o);
    }

    public static void reset(Context c) {
        fileFor(c).delete();
        touchStreak(c);
    }
}
