package bj.fongbe.academie;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.Locale;

/** Versets bibliques et mots (bibliques / du quotidien) en fongbé, avec voix. */
public class BibleActivity extends Activity {

    private static final int BG = 0xFFFFF8E7;
    private static final int GREEN = 0xFF176B52;
    private static final int YELLOW = 0xFFFBE7A1;
    private static final int YELLOW_STROKE = 0xFFF3D36B;
    private static final int TEXT = 0xFF2B2B2B;
    private static final int GREY = 0xFF7A7A7A;
    private static final int WHITE = 0xFFFFFFFF;

    private SharedPreferences prefs;
    private LinearLayout list;
    private final TextView[] tabs = new TextView[4];
    private int tab = 0;

    // Onglet « Bible Fon13 »
    private int fonBook = 0;
    private int fonChap = 1;
    private int fonToken = 0;
    private TextView fonBookBtn;
    private TextView fonChapBtn;
    private TextView fonStatus;
    private TextView fonDl;
    private TextView fonAudioBtn;
    private LinearLayout fonVerses;
    private ScrollView scrollView;

    // Lecture verset par verset
    private ChapterPlayer player;
    private boolean playerReady = false;
    private FonBible.Chapter fonCh;
    private String fonUsfm = "";
    private int[] fonStarts = new int[0];
    private int fonMode = 0;                 // 0 estimé, 1 détecté automatiquement, 2 calé à la main
    private LinearLayout[] verseRows = new LinearLayout[0];
    private int hi = -1;
    private boolean optLoop = false;
    private boolean optVerseOnly = true;
    private float optSpeed = 1f;
    private LinearLayout fonPanel;
    private LinearLayout calBar;
    private TextView btnPlay;
    private TextView btnLoop;
    private TextView btnMode;
    private TextView btnSpeed;
    private TextView btnCal;
    private TextView timesNote;
    private TextView calText;
    private TextView calBtn;
    private boolean calibrating = false;
    private int calIdx = 0;
    private int[] calStarts = new int[0];

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences("fongbe", MODE_PRIVATE);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        root.setPadding(dp(16), dp(16), dp(16), dp(8));
        setContentView(root);

        TextView back = tv("← Retour", 14, GREEN, true);
        back.setOnClickListener(v -> finish());
        root.addView(back);
        root.addView(tv("Bible en fongbé", 24, TEXT, true), lp(-1, -2, 0, 6, 0, 2));
        root.addView(tv(BibleData.CREDIT, 11, GREY, false), lp(-1, -2, 0, 0, 0, 8));

        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        String[] names = {"📖 Versets choisis", "🙏 Mots bibliques", "📚 Mots du jour", "🎧 Bible Fon13"};
        for (int i = 0; i < tabs.length; i++) {
            final int idx = i;
            tabs[i] = tv(names[i], 11, TEXT, true);
            tabs[i].setGravity(Gravity.CENTER);
            tabs[i].setPadding(dp(4), dp(10), dp(4), dp(10));
            tabs[i].setOnClickListener(v -> show(idx));
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, -2, 1f);
            p.setMargins(dp(2), 0, dp(2), 0);
            bar.addView(tabs[i], p);
        }
        root.addView(bar, lp(-1, -2, 0, 0, 0, 8));

        scrollView = new ScrollView(this);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        scrollView.addView(list);
        root.addView(scrollView, new LinearLayout.LayoutParams(-1, 0, 1f));

        show(3);
    }

    // ----- Onglet « Bible Fon13 » : texte fon, audio du chapitre, lecture à partir d'un verset -----

    private void showFon() {
        stopPlayer();
        fonPanel = null;
        fonBook = Math.max(0, Math.min(FonBible.NAMES.length - 1, prefs.getInt("fonbible_book", 0)));
        fonChap = Math.max(1, Math.min(FonBible.CHAPTERS[fonBook], prefs.getInt("fonbible_chap", 1)));

        list.addView(tv("Bible Fon13 : les 66 livres, de Genèse à Apocalypse. Appuie sur un verset pour "
            + "l'écouter à partir de là. Internet sert la première fois, ensuite le chapitre reste "
            + "disponible hors ligne.",
            12, GREY, false), lp(-1, -2, 0, 0, 0, 8));

        LinearLayout nav = new LinearLayout(this);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        nav.setGravity(Gravity.CENTER_VERTICAL);

        TextView prev = fonNav("◀");
        prev.setOnClickListener(v -> fonStep(-1));
        TextView next = fonNav("▶");
        next.setOnClickListener(v -> fonStep(1));
        fonBookBtn = fonNav("");
        fonBookBtn.setOnClickListener(v -> pickBook());
        fonChapBtn = fonNav("");
        fonChapBtn.setOnClickListener(v -> {
            String[] nums = new String[FonBible.CHAPTERS[fonBook]];
            for (int i = 0; i < nums.length; i++) {
                nums[i] = "Chapitre " + (i + 1);
            }
            new AlertDialog.Builder(this).setTitle(FonBible.NAMES[fonBook])
                .setItems(nums, (d, w) -> {
                    fonChap = w + 1;
                    loadFon();
                }).show();
        });

        nav.addView(prev, new LinearLayout.LayoutParams(-2, -2));
        nav.addView(fonBookBtn, new LinearLayout.LayoutParams(0, -2, 2f));
        nav.addView(fonChapBtn, new LinearLayout.LayoutParams(0, -2, 1f));
        nav.addView(next, new LinearLayout.LayoutParams(-2, -2));
        list.addView(nav, lp(-1, -2, 0, 0, 0, 8));

        fonAudioBtn = button("🎧 Écouter le chapitre en fongbé");
        fonAudioBtn.setVisibility(View.GONE);
        fonAudioBtn.setOnClickListener(v -> withPlayer(() -> {
            applyOpts();
            player.toggle();
        }));
        list.addView(fonAudioBtn, lp(-1, -2, 0, 0, 0, 8));

        buildPanel();

        fonDl = tv("", 12, GREY, false);
        list.addView(fonDl, lp(-1, -2, 0, 0, 0, 6));

        fonStatus = tv("", 13, GREY, false);
        list.addView(fonStatus, lp(-1, -2, 0, 0, 0, 8));

        fonVerses = new LinearLayout(this);
        fonVerses.setOrientation(LinearLayout.VERTICAL);
        list.addView(fonVerses);

        loadFon();
    }

    /** Choix du livre : d'abord le Testament, puis le livre (Genèse → Malachie, Matthieu → Apocalypse). */
    private void pickBook() {
        new AlertDialog.Builder(this).setTitle("Bible Fon13")
            .setItems(new String[] {
                "Ancien Testament : Genèse → Malachie (39 livres)",
                "Nouveau Testament : Matthieu → Apocalypse (27 livres)"}, (d, w) -> {
                final int from = w == 0 ? 0 : 39;
                final int to = w == 0 ? 39 : FonBible.NAMES.length;
                String[] names = java.util.Arrays.copyOfRange(FonBible.NAMES, from, to);
                new AlertDialog.Builder(this)
                    .setTitle(w == 0 ? "Ancien Testament" : "Nouveau Testament")
                    .setItems(names, (d2, w2) -> {
                        fonBook = from + w2;
                        fonChap = 1;
                        loadFon();
                    }).show();
            }).show();
    }

    private TextView fonNav(String t) {
        TextView v = tv(t, 14, TEXT, true);
        v.setGravity(Gravity.CENTER);
        v.setPadding(dp(12), dp(10), dp(12), dp(10));
        v.setBackground(bg(WHITE, 12, YELLOW_STROKE));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-2, -2);
        p.setMargins(dp(2), 0, dp(2), 0);
        v.setLayoutParams(p);
        return v;
    }

    /** Petit bouton qui prend une part égale de la ligne. */
    private TextView chip(String t) {
        TextView v = tv(t, 12, TEXT, true);
        v.setGravity(Gravity.CENTER);
        v.setPadding(dp(6), dp(10), dp(6), dp(10));
        v.setBackground(bg(WHITE, 12, YELLOW_STROKE));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, -2, 1f);
        p.setMargins(dp(2), 0, dp(2), 0);
        v.setLayoutParams(p);
        return v;
    }

    private LinearLayout hrow() {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        return r;
    }

    private void buildPanel() {
        fonPanel = new LinearLayout(this);
        fonPanel.setOrientation(LinearLayout.VERTICAL);
        fonPanel.setVisibility(View.GONE);

        LinearLayout r1 = hrow();
        TextView prevV = chip("⏮ Verset");
        TextView nextV = chip("Verset ⏭");
        btnPlay = chip("▶ Lecture");
        prevV.setOnClickListener(v -> withPlayer(() -> {
            applyOpts();
            player.prevVerse();
        }));
        nextV.setOnClickListener(v -> withPlayer(() -> {
            applyOpts();
            player.nextVerse();
        }));
        btnPlay.setOnClickListener(v -> withPlayer(() -> {
            applyOpts();
            player.toggle();
        }));
        r1.addView(prevV);
        r1.addView(btnPlay);
        r1.addView(nextV);
        fonPanel.addView(r1, lp(-1, -2, 0, 0, 0, 6));

        LinearLayout r2 = hrow();
        btnLoop = chip("");
        btnMode = chip("");
        btnSpeed = chip("");
        btnLoop.setOnClickListener(v -> {
            optLoop = !optLoop;
            refreshOpts();
            applyOpts();
        });
        btnMode.setOnClickListener(v -> {
            optVerseOnly = !optVerseOnly;
            refreshOpts();
            applyOpts();
        });
        btnSpeed.setOnClickListener(v -> {
            if (android.os.Build.VERSION.SDK_INT < 23) {
                Toast.makeText(this, "Vitesse non disponible sur cet Android.", Toast.LENGTH_SHORT).show();
                return;
            }
            optSpeed = optSpeed == 1f ? 0.75f : (optSpeed == 0.75f ? 0.5f : 1f);
            refreshOpts();
            applyOpts();
        });
        r2.addView(btnLoop);
        r2.addView(btnMode);
        r2.addView(btnSpeed);
        fonPanel.addView(r2, lp(-1, -2, 0, 0, 0, 6));
        refreshOpts();

        timesNote = tv("", 11, GREY, false);
        fonPanel.addView(timesNote, lp(-1, -2, 0, 0, 0, 6));

        btnCal = chip("🎯 Caler les versets à la main");
        btnCal.setOnClickListener(v -> startCalibration());
        LinearLayout r3 = hrow();
        r3.addView(btnCal);
        fonPanel.addView(r3, lp(-1, -2, 0, 0, 0, 6));

        calBar = new LinearLayout(this);
        calBar.setOrientation(LinearLayout.VERTICAL);
        calBar.setVisibility(View.GONE);
        calBar.setPadding(dp(10), dp(10), dp(10), dp(10));
        calBar.setBackground(bg(YELLOW, 14, YELLOW_STROKE));
        calText = tv("Écoute, et appuie sur le bouton au moment où chaque verset commence.", 12, TEXT, false);
        calBar.addView(calText, lp(-1, -2, 0, 0, 0, 6));
        calBtn = button("▶ Le verset 1 commence ici");
        calBtn.setOnClickListener(v -> calMark());
        calBar.addView(calBtn, lp(-1, -2, 0, 0, 0, 6));
        TextView calCancel = chip("Annuler le calage");
        calCancel.setOnClickListener(v -> stopCalibration(true));
        LinearLayout r4 = hrow();
        r4.addView(calCancel);
        calBar.addView(r4);
        fonPanel.addView(calBar, lp(-1, -2, 0, 0, 0, 6));

        list.addView(fonPanel, lp(-1, -2, 0, 0, 0, 6));
    }

    private void refreshOpts() {
        btnLoop.setText("🔁 Répéter : " + (optLoop ? "oui" : "non"));
        btnMode.setText(optVerseOnly ? "Verset seul" : "Enchaîner");
        btnSpeed.setText("🐢 " + (optSpeed == 1f ? "1×" : (optSpeed == 0.75f ? "0,75×" : "0,5×")));
        btnLoop.setBackground(bg(optLoop ? YELLOW : WHITE, 12, YELLOW_STROKE));
        btnMode.setBackground(bg(optVerseOnly ? YELLOW : WHITE, 12, YELLOW_STROKE));
        btnSpeed.setBackground(bg(optSpeed == 1f ? WHITE : YELLOW, 12, YELLOW_STROKE));
    }

    private void applyOpts() {
        if (player != null) {
            player.loop = optLoop;
            player.verseOnly = optVerseOnly;
            player.setSpeed(optSpeed);
        }
    }

    /** Fonce sur la fin de la ligne : exécute r quand le lecteur est prêt (télécharge l'audio si besoin). */
    private void withPlayer(final Runnable r) {
        if (fonCh == null || fonCh.audioUrl.isEmpty()) {
            Toast.makeText(this, "Pas d'audio fongbé pour ce chapitre.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (player != null && playerReady) {
            r.run();
            return;
        }
        final int token = fonToken;
        FongbeVoice.ensureAudio(this, fonCh.audioUrl, f -> {
            if (token != fonToken || tab != 3) {
                return;
            }
            if (setupPlayer(f)) {
                r.run();
            }
        });
    }

    private boolean setupPlayer(File f) {
        if (player == null) {
            player = new ChapterPlayer();
            player.setListener(new ChapterPlayer.Listener() {
                @Override
                public void onVerse(int index) {
                    if (!calibrating) {
                        highlight(index);
                    }
                }

                @Override
                public void onPlaying(boolean playing) {
                    if (btnPlay != null) {
                        btnPlay.setText(playing ? "⏸ Pause" : "▶ Lecture");
                    }
                }
            });
        }
        if (!player.load(f)) {
            Toast.makeText(this, "Impossible de lire cet audio.", Toast.LENGTH_LONG).show();
            return false;
        }
        playerReady = true;
        final int dur = player.duration();
        final int n = fonCh.verses.size();
        final int[] lens = new int[n];
        for (int i = 0; i < n; i++) {
            lens[i] = fonCh.verses.get(i)[1].length();
        }
        FonBible.Times t = FonBible.loadTimes(this, fonUsfm, n);
        if (t != null) {
            fonStarts = t.starts;
            fonMode = t.mode;
        } else {
            fonStarts = VerseTiming.estimate(dur, lens);
            fonMode = 0;
        }
        player.setStarts(fonStarts);
        fonAudioBtn.setVisibility(View.GONE);
        fonPanel.setVisibility(View.VISIBLE);
        refreshTimesNote(false);
        applyOpts();
        if (fonMode == 0) {
            detectAsync(f, dur, lens);
        }
        return true;
    }

    /** Cherche en arrière-plan les pauses entre les versets pour placer précisément leurs débuts. */
    private void detectAsync(final File f, final int dur, final int[] lens) {
        refreshTimesNote(true);
        final int token = fonToken;
        final String usfm = fonUsfm;
        new Thread(() -> {
            final float[] lv = ChapterPlayer.levels(f, 20);
            runOnUiThread(() -> {
                if (token != fonToken || tab != 3 || fonMode != 0 || player == null) {
                    return;
                }
                if (lv != null && lv.length > 50) {
                    fonStarts = VerseTiming.snap(lv, 20, fonStarts, dur);
                    player.setStarts(fonStarts);
                    fonMode = 1;
                    FonBible.saveTimes(this, usfm, fonStarts, 1);
                }
                refreshTimesNote(false);
            });
        }).start();
    }

    private void refreshTimesNote(boolean detecting) {
        if (timesNote == null) {
            return;
        }
        if (detecting) {
            timesNote.setText("Positions des versets : estimées ≈, détection des pauses en cours…");
        } else if (fonMode == 2) {
            timesNote.setText("Positions des versets : calées à la main ✅");
        } else if (fonMode == 1) {
            timesNote.setText("Positions des versets : détectées automatiquement. Si un verset "
                + "démarre mal, appuie longuement dessus pour le régler, ou cale-les à la main.");
        } else {
            timesNote.setText("Positions des versets : estimées ≈ (pas assez précises). "
                + "Utilise « Caler les versets à la main ».");
        }
    }

    private void highlight(int idx) {
        if (hi >= 0 && hi < verseRows.length) {
            verseRows[hi].setBackground(bg(WHITE, 14, YELLOW_STROKE));
        }
        hi = idx;
        if (idx >= 0 && idx < verseRows.length) {
            verseRows[idx].setBackground(bg(YELLOW, 14, YELLOW_STROKE));
            int y = fonVerses.getTop() + verseRows[idx].getTop() - dp(140);
            scrollView.smoothScrollTo(0, Math.max(0, y));
        }
    }

    private void onVerseTap(final int i) {
        if (calibrating) {
            return;
        }
        withPlayer(() -> {
            applyOpts();
            player.playVerse(i);
        });
    }

    /** Appui long : le verset commence à la position actuelle de l'audio. */
    private void onVerseLong(final int i) {
        if (player == null || !playerReady || calibrating) {
            Toast.makeText(this, "Lance d'abord l'audio, place-le au début de ce verset, "
                + "puis fais un appui long sur le verset.", Toast.LENGTH_LONG).show();
            return;
        }
        new AlertDialog.Builder(this)
            .setTitle("Verset " + fonCh.verses.get(i)[0])
            .setItems(new String[] {"Il commence à la position actuelle de l'audio", "Annuler"},
                (d, w) -> {
                    if (w != 0) {
                        return;
                    }
                    int pos = Math.max(0, player.position() - 150);
                    if ((i > 0 && pos <= fonStarts[i - 1] + 200)
                        || (i + 1 < fonStarts.length && pos >= fonStarts[i + 1] - 200)) {
                        Toast.makeText(this, "Position incohérente avec les versets voisins.",
                            Toast.LENGTH_LONG).show();
                        return;
                    }
                    fonStarts[i] = pos;
                    fonMode = 2;
                    player.setStarts(fonStarts);
                    FonBible.saveTimes(this, fonUsfm, fonStarts, 2);
                    refreshTimesNote(false);
                    Toast.makeText(this, "✅ Position du verset enregistrée", Toast.LENGTH_SHORT).show();
                }).show();
    }

    // ----- Calage à la main : on appuie au début de chaque verset pendant l'écoute -----

    private void startCalibration() {
        if (calibrating) {
            stopCalibration(true);
            return;
        }
        withPlayer(() -> {
            final int n = fonCh.verses.size();
            calibrating = true;
            calIdx = 0;
            calStarts = new int[n];
            player.verseOnly = false;
            player.loop = false;
            player.setSpeed(1f);
            calBar.setVisibility(View.VISIBLE);
            calBtn.setText("▶ Le verset " + fonCh.verses.get(0)[0] + " commence ici");
            highlight(0);
            player.seekAndPlay(0);
        });
    }

    private void calMark() {
        if (!calibrating || player == null) {
            return;
        }
        int pos = Math.max(0, player.position() - 250);   // temps de réaction
        if (calIdx > 0 && pos <= calStarts[calIdx - 1] + 200) {
            pos = calStarts[calIdx - 1] + 200;
        }
        calStarts[calIdx] = pos;
        calIdx++;
        if (calIdx >= calStarts.length) {
            fonStarts = calStarts;
            fonMode = 2;
            player.setStarts(fonStarts);
            FonBible.saveTimes(this, fonUsfm, fonStarts, 2);
            stopCalibration(false);
            player.pause();
            refreshTimesNote(false);
            Toast.makeText(this, "✅ Positions enregistrées : appuie sur un verset pour l'écouter",
                Toast.LENGTH_LONG).show();
        } else {
            calBtn.setText("▶ Le verset " + fonCh.verses.get(calIdx)[0] + " commence ici");
            highlight(calIdx);
        }
    }

    private void stopCalibration(boolean pause) {
        calibrating = false;
        if (calBar != null) {
            calBar.setVisibility(View.GONE);
        }
        if (pause && player != null) {
            player.pause();
        }
        applyOpts();
    }

    private void stopPlayer() {
        calibrating = false;
        if (player != null) {
            player.release();
        }
        player = null;
        playerReady = false;
        hi = -1;
        verseRows = new LinearLayout[0];
        if (fonPanel != null) {
            fonPanel.setVisibility(View.GONE);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (player != null) {
            player.pause();
        }
    }

    /** Chapitre précédent (-1) ou suivant (+1) ; passe au livre voisin en bout de livre. */
    private void fonStep(int d) {
        int chap = fonChap + d;
        if (chap < 1) {
            if (fonBook == 0) {
                return;
            }
            fonBook--;
            chap = FonBible.CHAPTERS[fonBook];
        } else if (chap > FonBible.CHAPTERS[fonBook]) {
            if (fonBook >= FonBible.NAMES.length - 1) {
                return;
            }
            fonBook++;
            chap = 1;
        }
        fonChap = chap;
        loadFon();
    }

    private void loadFon() {
        stopPlayer();
        prefs.edit().putInt("fonbible_book", fonBook).putInt("fonbible_chap", fonChap).apply();
        fonBookBtn.setText("📚 " + FonBible.NAMES[fonBook]);
        fonChapBtn.setText("Chap. " + fonChap);
        fonVerses.removeAllViews();
        fonAudioBtn.setVisibility(View.GONE);
        fonCh = null;
        fonStatus.setText("Chargement…");
        BookDownloader.start(this, fonBook, msg -> {
            if (fonDl != null && tab == 3) {
                fonDl.setText(msg);
            }
        });
        final int token = ++fonToken;
        fonUsfm = FonBible.CODES[fonBook] + "." + fonChap;
        final String usfm = fonUsfm;
        new Thread(() -> {
            try {
                final FonBible.Chapter c = FonBible.load(this, usfm);
                runOnUiThread(() -> {
                    if (token == fonToken && tab == 3) {
                        renderFon(c);
                    }
                });
            } catch (final Exception e) {
                runOnUiThread(() -> {
                    if (token == fonToken && tab == 3) {
                        fonStatus.setText("Impossible de charger ce chapitre (" + e.getMessage()
                            + "). Vérifie ta connexion Internet.");
                    }
                });
            }
        }).start();
    }

    private void renderFon(final FonBible.Chapter c) {
        fonCh = c;
        if (c.audioUrl.isEmpty()) {
            fonStatus.setText("Pas d'audio fongbé pour ce chapitre.");
        } else {
            fonStatus.setText("");
            fonAudioBtn.setText(FongbeVoice.audioFile(this, c.audioUrl).exists()
                ? "🎧 Écouter le chapitre (hors ligne ✅)" : "🎧 Écouter le chapitre en fongbé");
            fonAudioBtn.setVisibility(View.VISIBLE);
        }
        verseRows = new LinearLayout[c.verses.size()];
        hi = -1;
        for (int i = 0; i < c.verses.size(); i++) {
            final int idx = i;
            String[] v = c.verses.get(i);
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setPadding(dp(12), dp(10), dp(12), dp(10));
            row.setBackground(bg(WHITE, 14, YELLOW_STROKE));
            TextView n = tv(v[0], 13, GREEN, true);
            n.setMinWidth(dp(26));
            row.addView(n, new LinearLayout.LayoutParams(-2, -2));
            row.addView(tv(v[1], 16, TEXT, false), new LinearLayout.LayoutParams(0, -2, 1f));
            row.setOnClickListener(x -> onVerseTap(idx));
            row.setOnLongClickListener(x -> {
                onVerseLong(idx);
                return true;
            });
            verseRows[i] = row;
            fonVerses.addView(row, lp(-1, -2, 0, 0, 0, 6));
        }
    }

    private BibleData.Item[] current() {
        if (tab == 0) {
            return BibleData.VERSES;
        }
        return tab == 1 ? BibleData.BIBLE_WORDS : BibleData.DAILY_WORDS;
    }

    private void show(int t) {
        tab = t;
        for (int i = 0; i < tabs.length; i++) {
            tabs[i].setBackground(bg(i == t ? YELLOW : WHITE, 12, YELLOW_STROKE));
        }
        list.removeAllViews();
        if (t != 3) {
            stopPlayer();
        }
        if (t == 3) {
            showFon();
            return;
        }
        for (final BibleData.Item it : current()) {
            list.addView(row(it), lp(-1, -2, 0, 0, 0, 8));
        }
    }

    private android.view.View row(final BibleData.Item it) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.VERTICAL);
        r.setPadding(dp(14), dp(12), dp(14), dp(12));
        r.setBackground(bg(WHITE, 14, YELLOW_STROKE));

        if (!it.ref.isEmpty()) {
            r.addView(tv(it.ref, 15, GREEN, true));
        }
        TextView fr = tv(it.fr, it.ref.isEmpty() ? 16 : 13, TEXT, it.ref.isEmpty());
        fr.setMaxLines(2);
        fr.setEllipsize(TextUtils.TruncateAt.END);
        r.addView(fr);

        String fon = fonOf(it);
        TextView f = tv(fon.isEmpty() ? "Touche pour traduire en fongbé" : fon,
            14, fon.isEmpty() ? GREY : GREEN, !fon.isEmpty());
        f.setMaxLines(2);
        f.setEllipsize(TextUtils.TruncateAt.END);
        r.addView(f, lp(-1, -2, 0, 4, 0, 0));

        r.setOnClickListener(v -> open(it));
        return r;
    }

    // ----- Fongbé d'un élément : enregistré > connu > mot du cours -----

    private FongbeData.Entry known(BibleData.Item it) {
        if (!it.ref.isEmpty()) {
            return null;
        }
        String t = it.fr.toLowerCase(Locale.ROOT);
        for (FongbeData.Entry e : FongbeData.WORDS) {
            if (e.fr.toLowerCase(Locale.ROOT).equals(t)) {
                return e;
            }
        }
        return null;
    }

    private String fonOf(BibleData.Item it) {
        String saved = prefs.getString("bfon_" + it.id, "");
        if (!saved.isEmpty()) {
            return saved;
        }
        if (!it.fon.isEmpty()) {
            return it.fon;
        }
        FongbeData.Entry e = known(it);
        return e == null ? "" : e.fon;
    }

    private String infoOf(BibleData.Item it) {
        if (!prefs.getString("bfon_" + it.id, "").isEmpty()) {
            return "user".equals(prefs.getString("bsrc_" + it.id, "auto"))
                ? "✏️ Corrigé par toi"
                : "⚠️ Traduction automatique : à faire valider par un locuteur natif";
        }
        if (!it.fon.isEmpty() || known(it) != null) {
            return "✔ Mot du cours (à valider avec un locuteur natif)";
        }
        return "Pas encore de fongbé : touche « Traduire ».";
    }

    private String keyOf(BibleData.Item it, String fon) {
        FongbeData.Entry e = known(it);
        return (e != null && e.fon.equals(fon)) ? e.key : it.id;
    }

    // ----- Fenêtre de détail -----

    private void open(final BibleData.Item it) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(16), dp(20), dp(8));

        if (!it.ref.isEmpty()) {
            box.addView(tv(it.ref, 16, GREEN, true));
        }
        box.addView(tv(it.fr, 15, TEXT, false), lp(-1, -2, 0, 4, 0, 10));

        final TextView fon = tv("", 18, GREEN, true);
        final TextView info = tv("", 12, GREY, false);
        box.addView(fon);
        box.addView(info, lp(-1, -2, 0, 4, 0, 10));
        fill(fon, info, it);

        TextView listen = button("🔊 Écouter");
        TextView translate = button("🌍 Traduire en fongbé");
        TextView edit = button("✏️ Corriger le fongbé");
        TextView voice = button("🎙️ Voix et télécharger");
        box.addView(listen, lp(-1, -2, 0, 4, 0, 0));

        final String chapterUrl = BibleData.chapterAudioUrl(it.ref);
        if (!chapterUrl.isEmpty()) {
            TextView chapter = button("🎧 Écouter le chapitre en fongbé");
            chapter.setOnClickListener(v -> FongbeVoice.playRemote(this, chapterUrl));
            box.addView(chapter, lp(-1, -2, 0, 6, 0, 0));
        }
        box.addView(translate, lp(-1, -2, 0, 6, 0, 0));
        box.addView(edit, lp(-1, -2, 0, 6, 0, 0));
        box.addView(voice, lp(-1, -2, 0, 6, 0, 0));

        ScrollView sv = new ScrollView(this);
        sv.addView(box);
        final AlertDialog d = new AlertDialog.Builder(this)
            .setView(sv)
            .setNegativeButton("Fermer", null)
            .create();
        d.setOnDismissListener(x -> show(tab));

        listen.setOnClickListener(v -> {
            String f = fonOf(it);
            if (f.isEmpty()) {
                Toast.makeText(this, "Traduis d'abord en fongbé.", Toast.LENGTH_SHORT).show();
                return;
            }
            FongbeVoice.speak(this, new FongbeData.Entry(it.fr, f, keyOf(it, f)));
        });

        translate.setOnClickListener(v -> {
            info.setText("Traduction en cours…");
            final String token = prefs.getString("hf_token", "");
            new Thread(() -> {
                String out;
                try {
                    out = Translator.translate(it.fr, true, token);
                } catch (Exception e) {
                    out = null;
                }
                final String res = out;
                runOnUiThread(() -> {
                    if (res == null || res.trim().isEmpty()) {
                        info.setText("Traduction impossible (vérifie la connexion Internet).");
                        return;
                    }
                    prefs.edit()
                        .putString("bfon_" + it.id, res.trim())
                        .putString("bsrc_" + it.id, "auto")
                        .apply();
                    fill(fon, info, it);
                });
            }).start();
        });

        edit.setOnClickListener(v -> {
            final EditText et = new EditText(this);
            et.setText(fonOf(it));
            et.setMinLines(2);
            new AlertDialog.Builder(this)
                .setTitle("Fongbé corrigé")
                .setView(et)
                .setPositiveButton("Enregistrer", (dd, w) -> {
                    String t = et.getText().toString().trim();
                    SharedPreferences.Editor ed = prefs.edit();
                    if (t.isEmpty()) {
                        ed.remove("bfon_" + it.id).remove("bsrc_" + it.id);
                    } else {
                        ed.putString("bfon_" + it.id, t).putString("bsrc_" + it.id, "user");
                    }
                    ed.apply();
                    fill(fon, info, it);
                })
                .setNegativeButton("Annuler", null)
                .show();
        });

        voice.setOnClickListener(v -> {
            String f = fonOf(it);
            if (f.isEmpty()) {
                Toast.makeText(this, "Traduis d'abord en fongbé.", Toast.LENGTH_SHORT).show();
                return;
            }
            startActivity(new Intent(this, FonTtsActivity.class).putExtra("fon", f));
        });

        d.show();
    }

    private void fill(TextView fon, TextView info, BibleData.Item it) {
        String f = fonOf(it);
        fon.setText(f.isEmpty() ? "—" : f);
        info.setText(infoOf(it));
    }

    // ----- Outils d'interface -----

    private TextView tv(String t, int sp, int color, boolean bold) {
        TextView v = new TextView(this);
        v.setText(t);
        v.setTextSize(sp);
        v.setTextColor(color);
        if (bold) {
            v.setTypeface(v.getTypeface(), Typeface.BOLD);
        }
        return v;
    }

    private TextView button(String t) {
        TextView v = tv(t, 15, TEXT, true);
        v.setGravity(Gravity.CENTER);
        v.setPadding(dp(8), dp(12), dp(8), dp(12));
        v.setBackground(bg(YELLOW, 14, 0));
        return v;
    }

    private GradientDrawable bg(int color, int radiusDp, int stroke) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radiusDp));
        if (stroke != 0) {
            d.setStroke(dp(1), stroke);
        }
        return d;
    }

    private LinearLayout.LayoutParams lp(int w, int h, int l, int t, int r, int b) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(w, h);
        p.setMargins(dp(l), dp(t), dp(r), dp(b));
        return p;
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onDestroy() {
        stopPlayer();
        FongbeVoice.stopPlayer();
        super.onDestroy();
    }
}
