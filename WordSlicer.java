package bj.fongbe.academie;

import android.app.Activity;
import android.content.Context;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.os.SystemClock;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ShortBuffer;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Découpe l'enregistrement d'un chapitre en petits fichiers, un par mot.
 *
 * Rangement sur le téléphone : FongbeKouam2/audio/mots/<mot>/<livre>_<chapitre>_<verset>.wav
 * (un dossier par mot, un fichier par verset où le mot apparaît).
 *
 * Méthode : l'audio ne contient aucun repère de mots. Dans chaque verset (début et fin pris dans
 * les positions de VerseTiming, partagées avec l'écran Bible), on retire les silences du début et
 * de la fin, on répartit le temps entre les mots selon la longueur de chacun, puis on accroche
 * chaque limite au creux de volume le plus proche. C'est une estimation : un mot peut être coupé
 * un peu trop tôt ou trop tard, surtout quand la voix enchaîne sans pause.
 */
public class WordSlicer {

    private static final int FRAME_MS = 20;
    private static final int PAD_MS = 40;
    private static volatile boolean running = false;

    private static class Pcm {
        short[] data = new short[1 << 20];
        int length = 0;
        int rate = 44100;

        void add(short s) {
            if (length == data.length) {
                data = Arrays.copyOf(data, data.length * 2);
            }
            data[length++] = s;
        }
    }

    // ------------------------------------------------------------------
    // Emplacements
    // ------------------------------------------------------------------

    public static File wordsDir(Context c) {
        File d = new File(Store.audioDir(c), "mots");
        if (!d.exists()) {
            d.mkdirs();
        }
        return d;
    }

    /** Nom de dossier sûr pour un mot fon (lettres, marques de ton et chiffres conservés). */
    public static String safe(String word) {
        String s = word.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{M}\\p{N}]+", "_");
        s = s.replaceAll("^_+|_+$", "");
        return s.isEmpty() ? "x" : s;
    }

    public static File clip(Context c, String word, String book, int chapter, int verse) {
        return new File(new File(wordsDir(c), safe(word)),
            book.toLowerCase(Locale.ROOT) + "_" + chapter + "_" + verse + ".wav");
    }

    /** Fichier découpé d'un mot du cours (clé « bw_livre_chapitre_verset_n »), ou null. */
    public static File clipFor(Context c, FongbeData.Entry e) {
        String[] k = e.key.split("_");
        if (k.length < 5 || !k[0].equals("bw")) {
            return null;
        }
        try {
            return clip(c, e.fon, k[1], Integer.parseInt(k[2]), Integer.parseInt(k[3]));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static File marker(Context c, BibleCourse.Unit un) {
        return new File(wordsDir(c), "." + un.usfm() + "_" + un.from + "-" + un.to + ".ok");
    }

    // ------------------------------------------------------------------
    // Découpe d'une unité
    // ------------------------------------------------------------------

    /**
     * Découpe en arrière-plan les mots des versets d'une unité (si ce n'est pas déjà fait, ou si
     * force). Le mp3 du chapitre doit déjà être sur le téléphone. done est appelé à la fin.
     */
    public static void ensure(final Activity a, int unit, final boolean force, final Runnable done) {
        if (running || unit < 0 || unit >= BibleCourse.UNITS.length) {
            return;
        }
        final BibleCourse.Unit un = BibleCourse.UNITS[unit];
        FonBible.Chapter ch = null;
        try {
            ch = FonBible.load(a, FonBible.VERSION, "FON13", un.usfm(), false);
        } catch (Exception ignored) {
        }
        if (ch == null || ch.audioUrl.isEmpty()) {
            return;
        }
        final File mp3 = FongbeVoice.audioFile(a, ch.audioUrl);
        if (!mp3.exists() || mp3.length() == 0) {
            return;
        }
        final File mark = marker(a, un);
        if (mark.exists() && !force) {
            return;
        }
        final FonBible.Chapter chapter = ch;
        running = true;
        if (force) {
            Toast.makeText(a, "Découpe des mots en cours…", Toast.LENGTH_SHORT).show();
        }
        new Thread(() -> {
            boolean ok = false;
            try {
                ok = slice(a, un, chapter, mp3);
                if (ok) {
                    mark.createNewFile();
                }
            } catch (Throwable t) {
                ok = false;
            } finally {
                running = false;
            }
            final boolean fine = ok;
            a.runOnUiThread(() -> {
                if (force) {
                    Toast.makeText(a, fine ? "✅ Mots découpés dans le dossier audio/mots"
                        : "Découpe impossible pour ce chapitre.", Toast.LENGTH_LONG).show();
                }
                if (done != null) {
                    done.run();
                }
            });
        }).start();
    }

    private static final Object LOCK = new Object();

    /**
     * Prépare à la demande les mots d'UN verset : charge le texte du chapitre, télécharge le mp3
     * s'il manque, puis découpe seulement ce verset. À appeler hors du thread principal.
     * book = code USFM en majuscules (ex. « REV »).
     */
    public static boolean sliceVerseNow(Context c, String book, int chapter, int verse) {
        synchronized (LOCK) {
            try {
                FonBible.Chapter ch = FonBible.load(c, FonBible.VERSION, "FON13",
                    book + "." + chapter, true);
                if (ch == null || ch.audioUrl.isEmpty()) {
                    return false;
                }
                if (!FongbeVoice.downloadAudioBlocking(c, ch.audioUrl)) {
                    return false;
                }
                File mp3 = FongbeVoice.audioFile(c, ch.audioUrl);
                BibleCourse.Unit un = new BibleCourse.Unit("", "", book, chapter, verse, verse);
                return slice(c, un, ch, mp3);
            } catch (Throwable t) {
                return false;
            }
        }
    }

    private static boolean slice(Context c, BibleCourse.Unit un, FonBible.Chapter ch, File mp3)
            throws Exception {
        Pcm pcm = decode(mp3);
        if (pcm == null || pcm.length < pcm.rate) {
            return false;
        }
        int dur = (int) (pcm.length * 1000L / pcm.rate);
        float[] lv = levels(pcm);
        float thr = threshold(lv);

        int n = ch.verses.size();
        int[] lens = new int[n];
        for (int i = 0; i < n; i++) {
            lens[i] = ch.verses.get(i)[1].length();
        }
        int[] starts;
        FonBible.Times t = FonBible.loadTimes(c, un.usfm(), n);
        if (t != null) {
            starts = t.starts;
        } else {
            starts = VerseTiming.snap(lv, FRAME_MS, VerseTiming.estimate(dur, lens), dur);
            FonBible.saveTimes(c, un.usfm(), starts, 1);
        }

        boolean any = false;
        for (int i = 0; i < n; i++) {
            int verse;
            try {
                verse = Integer.parseInt(ch.verses.get(i)[0].trim());
            } catch (NumberFormatException e) {
                continue;
            }
            if (verse < un.from || verse > un.to) {
                continue;
            }
            int vs = starts[i];
            int ve = i + 1 < n ? starts[i + 1] : dur;
            any |= sliceVerse(c, un, verse, ch.verses.get(i)[1], pcm, lv, thr, vs, ve);
        }
        return any;
    }

    private static boolean sliceVerse(Context c, BibleCourse.Unit un, int verse, String text,
                                      Pcm pcm, float[] lv, float thr, int vs, int ve)
            throws Exception {
        List<String> toks = BibleCourse.tokens(text);
        int m = toks.size();
        if (m == 0 || ve - vs < 300) {
            return false;
        }

        // Partie parlée du verset : sans le silence du début ni celui de la fin
        int f0 = Math.max(0, vs / FRAME_MS);
        int f1 = Math.min(lv.length - 1, ve / FRAME_MS);
        while (f0 < f1 && lv[f0] < thr) {
            f0++;
        }
        while (f1 > f0 && lv[f1] < thr) {
            f1--;
        }
        int sp0 = f0 * FRAME_MS;
        int sp1 = (f1 + 1) * FRAME_MS;
        if (sp1 - sp0 < 200 * m / 2) {
            sp0 = vs;
            sp1 = ve;
        }

        // Limites estimées d'après la longueur de chaque mot, puis accrochées aux creux de volume
        double[] w = new double[m];
        double total = 0;
        for (int k = 0; k < m; k++) {
            w[k] = letters(toks.get(k)) + 2;
            total += w[k];
        }
        int[] b = new int[m + 1];
        b[0] = sp0;
        b[m] = sp1;
        double acc = 0;
        for (int k = 1; k < m; k++) {
            acc += w[k - 1];
            int expected = (int) (sp0 + (sp1 - sp0) * acc / total);
            int span = (int) ((sp1 - sp0) * Math.min(w[k - 1], w[k]) / total);
            int win = Math.max(FRAME_MS * 2, Math.min(160, (int) (span * 0.4)));
            b[k] = Math.max(b[k - 1] + 100, dip(lv, expected, win));
        }

        boolean any = false;
        Set<String> done = new HashSet<String>();
        for (int k = 0; k < m; k++) {
            String word = toks.get(k);
            if (word.length() < 3) {
                continue;
            }
            File out = clip(c, word, un.book, un.chapter, verse);
            if (!done.add(out.getPath())) {
                continue;
            }
            int from = Math.max(0, (b[k] - PAD_MS)) ;
            int to = Math.min((int) (pcm.length * 1000L / pcm.rate), Math.min(b[k + 1] + PAD_MS, ve));
            if (to - from < 120) {
                continue;
            }
            int s0 = (int) (from * (long) pcm.rate / 1000);
            int s1 = Math.min(pcm.length, (int) (to * (long) pcm.rate / 1000));
            writeWav(out, pcm.data, s0, s1, pcm.rate);
            any = true;
        }
        return any;
    }

    private static int letters(String s) {
        int n = 0;
        for (int i = 0; i < s.length(); i++) {
            int type = Character.getType(s.charAt(i));
            if (type != Character.NON_SPACING_MARK && type != Character.COMBINING_SPACING_MARK) {
                n++;
            }
        }
        return n;
    }

    /** Creux de volume le plus net (et le plus proche) autour de center, en ms. */
    private static int dip(float[] lv, int center, int win) {
        int c = center / FRAME_MS;
        int w = Math.max(1, win / FRAME_MS);
        int best = c;
        double bestScore = Double.MAX_VALUE;
        for (int f = Math.max(1, c - w); f <= Math.min(lv.length - 2, c + w); f++) {
            double v = (lv[f - 1] + lv[f] + lv[f + 1]) / 3.0;
            double score = v * (1.0 + 0.3 * Math.abs(f - c) / w);
            if (score < bestScore) {
                bestScore = score;
                best = f;
            }
        }
        return best * FRAME_MS + FRAME_MS / 2;
    }

    private static float threshold(float[] lv) {
        if (lv.length == 0) {
            return 0.002f;
        }
        float[] sorted = lv.clone();
        Arrays.sort(sorted);
        float p80 = sorted[(int) (sorted.length * 0.8)];
        return Math.max(p80 * 0.12f, 0.002f);
    }

    private static float[] levels(Pcm p) {
        int per = Math.max(1, p.rate * FRAME_MS / 1000);
        int frames = p.length / per;
        float[] out = new float[frames];
        for (int f = 0; f < frames; f++) {
            double sum = 0;
            int base = f * per;
            for (int i = 0; i < per; i++) {
                double v = p.data[base + i] / 32768.0;
                sum += v * v;
            }
            out[f] = (float) Math.sqrt(sum / per);
        }
        return out;
    }

    // ------------------------------------------------------------------
    // Décodage mp3 -> PCM mono, et écriture WAV
    // ------------------------------------------------------------------

    private static Pcm decode(File f) {
        MediaExtractor ex = new MediaExtractor();
        MediaCodec codec = null;
        try {
            ex.setDataSource(f.getPath());
            int track = -1;
            MediaFormat fmt = null;
            for (int i = 0; i < ex.getTrackCount(); i++) {
                MediaFormat t = ex.getTrackFormat(i);
                String mime = t.getString(MediaFormat.KEY_MIME);
                if (mime != null && mime.startsWith("audio/")) {
                    track = i;
                    fmt = t;
                    break;
                }
            }
            if (track < 0 || fmt == null) {
                return null;
            }
            ex.selectTrack(track);
            codec = MediaCodec.createDecoderByType(fmt.getString(MediaFormat.KEY_MIME));
            codec.configure(fmt, null, null, 0);
            codec.start();

            Pcm pcm = new Pcm();
            int ch = fmt.containsKey(MediaFormat.KEY_CHANNEL_COUNT)
                ? fmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT) : 1;
            pcm.rate = fmt.containsKey(MediaFormat.KEY_SAMPLE_RATE)
                ? fmt.getInteger(MediaFormat.KEY_SAMPLE_RATE) : 44100;

            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            boolean inDone = false;
            boolean outDone = false;
            long deadline = SystemClock.uptimeMillis() + 120000;
            while (!outDone && SystemClock.uptimeMillis() < deadline) {
                if (!inDone) {
                    int ii = codec.dequeueInputBuffer(10000);
                    if (ii >= 0) {
                        ByteBuffer ib = codec.getInputBuffer(ii);
                        int sz = ex.readSampleData(ib, 0);
                        if (sz < 0) {
                            codec.queueInputBuffer(ii, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                            inDone = true;
                        } else {
                            codec.queueInputBuffer(ii, 0, sz, ex.getSampleTime(), 0);
                            ex.advance();
                        }
                    }
                }
                int oi = codec.dequeueOutputBuffer(info, 10000);
                if (oi == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    MediaFormat of = codec.getOutputFormat();
                    if (of.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                        pcm.rate = of.getInteger(MediaFormat.KEY_SAMPLE_RATE);
                    }
                    if (of.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                        ch = of.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
                    }
                } else if (oi >= 0) {
                    ByteBuffer ob = codec.getOutputBuffer(oi);
                    if (ob != null && info.size > 0) {
                        ob.position(info.offset);
                        ob.limit(info.offset + info.size);
                        ShortBuffer sb = ob.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer();
                        int channels = Math.max(1, ch);
                        while (sb.remaining() >= channels) {
                            int sum = 0;
                            for (int k = 0; k < channels; k++) {
                                sum += sb.get();
                            }
                            pcm.add((short) (sum / channels));
                        }
                    }
                    codec.releaseOutputBuffer(oi, false);
                    if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        outDone = true;
                    }
                }
            }
            return pcm;
        } catch (Exception e) {
            return null;
        } finally {
            try {
                if (codec != null) {
                    codec.stop();
                    codec.release();
                }
            } catch (Exception ignore) {
                // rien
            }
            ex.release();
        }
    }

    private static void writeWav(File out, short[] d, int from, int to, int rate) throws Exception {
        int len = (to - from) * 2;
        ByteBuffer bb = ByteBuffer.allocate(44 + len).order(ByteOrder.LITTLE_ENDIAN);
        bb.put(new byte[] {'R', 'I', 'F', 'F'});
        bb.putInt(36 + len);
        bb.put(new byte[] {'W', 'A', 'V', 'E', 'f', 'm', 't', ' '});
        bb.putInt(16);
        bb.putShort((short) 1);          // PCM
        bb.putShort((short) 1);          // mono
        bb.putInt(rate);
        bb.putInt(rate * 2);
        bb.putShort((short) 2);
        bb.putShort((short) 16);
        bb.put(new byte[] {'d', 'a', 't', 'a'});
        bb.putInt(len);
        for (int i = from; i < to; i++) {
            bb.putShort(d[i]);
        }
        out.getParentFile().mkdirs();
        File tmp = new File(out.getPath() + ".part");
        FileOutputStream fo = new FileOutputStream(tmp);
        try {
            fo.write(bb.array());
        } finally {
            fo.close();
        }
        if (out.exists()) {
            out.delete();
        }
        if (!tmp.renameTo(out)) {
            tmp.delete();
            throw new Exception("écriture impossible");
        }
    }
}
