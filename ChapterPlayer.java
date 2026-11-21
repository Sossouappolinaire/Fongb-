package bj.fongbe.academie;

import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaPlayer;
import android.media.PlaybackParams;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ShortBuffer;
import java.util.ArrayList;

/**
 * Lecteur d'un chapitre (mp3) avec lecture à partir d'un verset précis : on donne le début de
 * chaque verset (en ms), et le lecteur sait jouer un verset seul, le répéter ou enchaîner.
 */
public class ChapterPlayer {

    public interface Listener {
        /** Verset en cours de lecture (-1 = avant le premier verset). */
        void onVerse(int index);

        void onPlaying(boolean playing);
    }

    public boolean loop = false;
    public boolean verseOnly = true;

    private float speed = 1f;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private MediaPlayer mp;
    private Listener listener;
    private int[] starts = new int[0];
    private int duration;
    private int segStart = -1;
    private int segEnd = -1;
    private int current = -2;
    private long ignoreUntil;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            onTick();
        }
    };

    public void setListener(Listener l) {
        listener = l;
    }

    public void setStarts(int[] s) {
        starts = s == null ? new int[0] : s;
        current = -2;
    }

    public int duration() {
        return duration;
    }

    public int position() {
        try {
            return mp == null ? 0 : mp.getCurrentPosition();
        } catch (IllegalStateException e) {
            return 0;
        }
    }

    public boolean isPlaying() {
        try {
            return mp != null && mp.isPlaying();
        } catch (IllegalStateException e) {
            return false;
        }
    }

    /** Charge le fichier mp3 ; renvoie false si Android n'arrive pas à le lire. */
    public boolean load(File f) {
        release();
        MediaPlayer p = new MediaPlayer();
        try {
            p.setDataSource(f.getPath());
            p.prepare();
            p.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                @Override
                public void onCompletion(MediaPlayer m) {
                    handler.removeCallbacks(tick);
                    segEnd = -1;
                    notifyPlaying(false);
                }
            });
            mp = p;
            duration = p.getDuration();
            return true;
        } catch (Exception e) {
            try {
                p.release();
            } catch (Exception ignore) {
                // rien
            }
            mp = null;
            return false;
        }
    }

    public void release() {
        handler.removeCallbacks(tick);
        if (mp != null) {
            try {
                mp.release();
            } catch (Exception ignore) {
                // rien
            }
            mp = null;
        }
        segStart = -1;
        segEnd = -1;
        current = -2;
    }

    public void pause() {
        try {
            if (mp != null && mp.isPlaying()) {
                mp.pause();
                notifyPlaying(false);
            }
        } catch (IllegalStateException e) {
            // rien
        }
    }

    public void toggle() {
        if (mp == null) {
            return;
        }
        try {
            if (mp.isPlaying()) {
                mp.pause();
                notifyPlaying(false);
                return;
            }
            int pos = mp.getCurrentPosition();
            if (segEnd > 0 && pos >= segEnd && starts.length > 0) {
                playVerse(Math.max(0, verseAt(pos)));
                return;
            }
            mp.start();
            applySpeed();
            ignoreUntil = SystemClock.uptimeMillis() + 300;
            notifyPlaying(true);
            handler.removeCallbacks(tick);
            handler.post(tick);
        } catch (IllegalStateException e) {
            // rien
        }
    }

    public void playVerse(int i) {
        if (mp == null || i < 0 || i >= starts.length) {
            return;
        }
        segStart = starts[i];
        segEnd = verseOnly ? verseEnd(i) : -1;
        startAt(segStart);
    }

    /** Joue à partir d'une position (ms) jusqu'à la fin du chapitre. */
    public void seekAndPlay(int ms) {
        if (mp == null) {
            return;
        }
        segStart = -1;
        segEnd = -1;
        startAt(ms);
    }

    public void nextVerse() {
        if (starts.length == 0) {
            return;
        }
        playVerse(Math.min(starts.length - 1, Math.max(0, current + 1)));
    }

    public void prevVerse() {
        if (starts.length == 0) {
            return;
        }
        playVerse(Math.max(0, current - 1));
    }

    public void setSpeed(float s) {
        speed = s;
        if (isPlaying()) {
            applySpeed();
        }
    }

    // ----- Intérieur -----

    private int verseEnd(int i) {
        int end = i + 1 < starts.length ? starts[i + 1] : duration;
        return Math.max(end, starts[i] + 300);
    }

    private int verseAt(int pos) {
        for (int i = starts.length - 1; i >= 0; i--) {
            if (starts[i] <= pos) {
                return i;
            }
        }
        return -1;
    }

    private void startAt(int ms) {
        try {
            mp.seekTo(ms);
            mp.start();
            applySpeed();
            ignoreUntil = SystemClock.uptimeMillis() + 400;
            notifyPlaying(true);
            handler.removeCallbacks(tick);
            handler.post(tick);
        } catch (IllegalStateException e) {
            // rien
        }
    }

    @SuppressWarnings("deprecation")
    private void applySpeed() {
        if (Build.VERSION.SDK_INT >= 23 && mp != null) {
            try {
                PlaybackParams pp = new PlaybackParams();
                pp.setSpeed(speed);
                mp.setPlaybackParams(pp);
            } catch (Exception ignore) {
                // vitesse non gérée par cet appareil
            }
        }
    }

    private void onTick() {
        MediaPlayer p = mp;
        if (p == null) {
            return;
        }
        try {
            if (!p.isPlaying()) {
                return;
            }
            int pos = p.getCurrentPosition();
            int idx = verseAt(pos);
            if (idx != current) {
                current = idx;
                if (listener != null) {
                    listener.onVerse(idx);
                }
            }
            if (segEnd > 0 && SystemClock.uptimeMillis() > ignoreUntil && pos >= segEnd) {
                if (loop) {
                    p.seekTo(segStart);
                    ignoreUntil = SystemClock.uptimeMillis() + 400;
                } else {
                    p.pause();
                    notifyPlaying(false);
                    return;
                }
            }
        } catch (IllegalStateException e) {
            return;
        }
        handler.postDelayed(tick, 50);
    }

    private void notifyPlaying(boolean playing) {
        if (listener != null) {
            listener.onPlaying(playing);
        }
    }

    // ----- Niveau sonore de l'audio (pour repérer les pauses entre les versets) -----

    /** Décode le mp3 et renvoie le niveau sonore par tranche de frameMs ms (null si impossible). */
    public static float[] levels(File f, int frameMs) {
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

            int rate = fmt.containsKey(MediaFormat.KEY_SAMPLE_RATE)
                ? fmt.getInteger(MediaFormat.KEY_SAMPLE_RATE) : 44100;
            int ch = fmt.containsKey(MediaFormat.KEY_CHANNEL_COUNT)
                ? fmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT) : 1;
            int perFrame = Math.max(1, rate * ch * frameMs / 1000);

            ArrayList<Float> out = new ArrayList<Float>();
            double sum = 0;
            int cnt = 0;
            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            boolean inDone = false;
            boolean outDone = false;
            long deadline = SystemClock.uptimeMillis() + 90000;
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
                    if (of.containsKey(MediaFormat.KEY_SAMPLE_RATE)
                        && of.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                        perFrame = Math.max(1, of.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                            * of.getInteger(MediaFormat.KEY_CHANNEL_COUNT) * frameMs / 1000);
                    }
                } else if (oi >= 0) {
                    ByteBuffer ob = codec.getOutputBuffer(oi);
                    if (ob != null && info.size > 0) {
                        ob.position(info.offset);
                        ob.limit(info.offset + info.size);
                        ShortBuffer sb = ob.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer();
                        while (sb.hasRemaining()) {
                            double v = sb.get() / 32768.0;
                            sum += v * v;
                            cnt++;
                            if (cnt >= perFrame) {
                                out.add((float) Math.sqrt(sum / cnt));
                                sum = 0;
                                cnt = 0;
                            }
                        }
                    }
                    codec.releaseOutputBuffer(oi, false);
                    if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        outDone = true;
                    }
                }
            }
            float[] arr = new float[out.size()];
            for (int i = 0; i < arr.length; i++) {
                arr[i] = out.get(i);
            }
            return arr;
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
}
