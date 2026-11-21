package bj.fongbe.academie;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Début de chaque verset dans le mp3 d'un chapitre. Aucune donnée de temps n'est fournie avec
 * l'audio : on estime d'abord d'après la longueur du texte, puis on accroche chaque début de verset
 * à la pause la plus nette qui se trouve près de la position estimée.
 */
public class VerseTiming {

    /** Estimation : durée répartie selon la longueur du texte de chaque verset. */
    public static int[] estimate(int durationMs, int[] lens) {
        int n = lens.length;
        int[] s = new int[n];
        if (n == 0) {
            return s;
        }
        double off = Math.min(4000, durationMs * 0.03);
        double total = 0;
        for (int l : lens) {
            total += l + 14;
        }
        double span = durationMs - off - Math.min(1500, durationMs * 0.01);
        double acc = 0;
        for (int i = 0; i < n; i++) {
            s[i] = (int) (off + span * acc / total);
            acc += lens[i] + 14;
        }
        return s;
    }

    /**
     * Accroche les débuts estimés aux silences de l'audio.
     * @param lv niveau sonore par tranche de frameMs millisecondes
     * @param est débuts estimés (ms)
     */
    public static int[] snap(float[] lv, int frameMs, int[] est, int durationMs) {
        int n = est.length;
        int[] out = est.clone();
        if (lv == null || lv.length < 50 || n == 0) {
            return out;
        }
        float[] sorted = lv.clone();
        Arrays.sort(sorted);
        float p80 = sorted[(int) (sorted.length * 0.8)];
        float thr = Math.max(p80 * 0.12f, 0.002f);
        int minFrames = Math.max(2, 220 / frameMs);

        List<int[]> sil = new ArrayList<>();
        int run = -1;
        for (int i = 0; i < lv.length; i++) {
            if (lv[i] < thr) {
                if (run < 0) {
                    run = i;
                }
            } else {
                if (run >= 0 && i - run >= minFrames) {
                    sil.add(new int[] {run * frameMs, i * frameMs});
                }
                run = -1;
            }
        }

        int shift = 0;
        int prevRaw = -1000;
        for (int k = 0; k < n; k++) {
            int expected = est[k] + shift;
            int span = k + 1 < n ? est[k + 1] - est[k] : durationMs - est[k];
            int win = k == 0 ? 7000 : Math.max(2500, (int) (span * 0.6));
            int best = -1;
            double bestScore = -1e9;
            for (int[] s : sil) {
                int t = s[1];                    // la voix reprend à la fin du silence
                int len = s[1] - s[0];
                if (Math.abs(t - expected) > win || t <= prevRaw + 400) {
                    continue;
                }
                double score = len - 0.12 * Math.abs(t - expected);
                if (score > bestScore) {
                    bestScore = score;
                    best = t;
                }
            }
            int raw;
            if (best >= 0) {
                raw = best;
                shift = raw - est[k];
            } else {
                raw = Math.max(prevRaw + 400, expected);
            }
            prevRaw = raw;
            out[k] = Math.max(0, raw - 120);     // un peu avant la reprise de la voix
        }
        return out;
    }
}
