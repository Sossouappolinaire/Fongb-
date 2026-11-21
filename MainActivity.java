package bj.fongbe.academie;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Calendar;

public class MainActivity extends Activity {

    private static final int BG = 0xFFFFF8E7;
    private static final int YELLOW = 0xFFFBE7A1;
    private static final int YELLOW_STROKE = 0xFFF3D36B;
    private static final int GREEN = 0xFF176B52;
    private static final int OLIVE = 0xFF8E9A3A;
    private static final int ORANGE = 0xFFE8782A;
    private static final int TEXT = 0xFF2B2B2B;
    private static final int GREY = 0xFF7A7A7A;
    private static final int WHITE = 0xFFFFFFFF;

    private static final int M = LinearLayout.LayoutParams.MATCH_PARENT;
    private static final int W = LinearLayout.LayoutParams.WRAP_CONTENT;

    private static final int DECK = 20;

    private static final String[] ICONS = {"🏠", "🔁", "👥", "👤"};
    private static final String[] LABELS = {"Accueil", "Révision", "Communauté", "Profil"};
    private static final String[] UNIT_EMOJI = {"🔤", "👋", "🙋", "👨‍👩‍👧"};

    private final ScrollView[] pages = new ScrollView[4];
    private final LinearLayout[] tabs = new LinearLayout[4];
    private LinearLayout homeBox, reviewBox, communityBox, profileBox, bottomBar;
    private TextView streakChip, pointsChip;
    private boolean showVocab = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (!Store.isLoggedIn(this)) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }

        setContentView(R.layout.activity_main);

        pages[0] = findViewById(R.id.pageHome);
        pages[1] = findViewById(R.id.pageReview);
        pages[2] = findViewById(R.id.pageCommunity);
        pages[3] = findViewById(R.id.pageProfile);
        homeBox = findViewById(R.id.homeBox);
        reviewBox = findViewById(R.id.reviewBox);
        communityBox = findViewById(R.id.communityBox);
        profileBox = findViewById(R.id.profileBox);
        bottomBar = findViewById(R.id.bottomBar);
        streakChip = findViewById(R.id.streakChip);
        pointsChip = findViewById(R.id.pointsChip);

        for (TextView chip : new TextView[]{streakChip, pointsChip}) {
            chip.setBackground(bg(WHITE, 16, YELLOW_STROKE));
            chip.setPadding(dp(12), dp(6), dp(12), dp(6));
        }

        Progress.touchStreak(this);
        buildTabs();
        select(0);
        startBibleSync();
    }

    /** Charge (ou complète) les chapitres de la Bible en fon, puis rafraîchit l'écran. */
    private void startBibleSync() {
        BibleCourse.sync(this, () -> {
            if (streakChip != null) {
                refreshAll();
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (streakChip == null) {
            return;
        }
        if (FongbeData.LESSONS.length < BibleCourse.UNITS.length && !BibleCourse.syncing) {
            startBibleSync();
        }
        refreshAll();
    }

    @Override
    protected void onDestroy() {
        BibleAudio.stop();
        super.onDestroy();
    }

    // ------------------------------------------------------------------
    // Outils
    // ------------------------------------------------------------------

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }

    private GradientDrawable bg(int color, int radiusDp, int strokeColor) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radiusDp));
        if (strokeColor != 0) {
            d.setStroke(dp(1), strokeColor);
        }
        return d;
    }

    private TextView tv(String s, int sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) {
            t.setTypeface(Typeface.DEFAULT_BOLD);
        }
        return t;
    }

    private LinearLayout.LayoutParams lp(int w, int h, int l, int t, int r, int b) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(w, h);
        p.setMargins(dp(l), dp(t), dp(r), dp(b));
        return p;
    }

    private LinearLayout box(int orientation, int padDp) {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(orientation);
        l.setPadding(dp(padDp), dp(padDp), dp(padDp), dp(padDp));
        return l;
    }

    // ------------------------------------------------------------------
    // Navigation
    // ------------------------------------------------------------------

    private void buildTabs() {
        bottomBar.removeAllViews();
        for (int i = 0; i < 4; i++) {
            final int idx = i;
            LinearLayout t = new LinearLayout(this);
            t.setOrientation(LinearLayout.VERTICAL);
            t.setGravity(Gravity.CENTER);
            t.setPadding(dp(4), dp(6), dp(4), dp(6));

            t.addView(tv(ICONS[i], 20, TEXT, false));
            t.addView(tv(LABELS[i], 11, GREY, false));

            t.setOnClickListener(v -> select(idx));
            tabs[i] = t;
            bottomBar.addView(t, lp(0, W, 4, 0, 4, 0));
            ((LinearLayout.LayoutParams) t.getLayoutParams()).weight = 1f;
        }
    }

    private void select(int i) {
        for (int k = 0; k < 4; k++) {
            boolean on = (k == i);
            pages[k].setVisibility(on ? View.VISIBLE : View.GONE);
            tabs[k].setBackground(on ? bg(YELLOW, 16, 0) : null);
            TextView label = (TextView) tabs[k].getChildAt(1);
            label.setTextColor(on ? GREEN : GREY);
            label.setTypeface(on ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        }
    }

    private void refreshAll() {
        streakChip.setText("🔥 " + Progress.getStreak(this));
        pointsChip.setText("⭐ " + Progress.getPoints(this));
        buildHome();
        buildReview();
        buildCommunity();
        buildProfile();
    }

    private void refreshHeader() {
        streakChip.setText("🔥 " + Progress.getStreak(this));
        pointsChip.setText("⭐ " + Progress.getPoints(this));
    }

    // ------------------------------------------------------------------
    // Accueil
    // ------------------------------------------------------------------

    private void buildHome() {
        homeBox.removeAllViews();

        final int total = FongbeData.LESSONS.length;
        int done = Progress.doneCount(this);

        if (total == 0) {
            homeBox.addView(loadingCard(), lp(M, W, 0, 0, 0, 12));
            return;
        }

        // Carte de progression
        LinearLayout prog = box(LinearLayout.HORIZONTAL, 16);
        prog.setGravity(Gravity.CENTER_VERTICAL);
        prog.setBackground(bg(YELLOW, 18, YELLOW_STROKE));

        LinearLayout txt = new LinearLayout(this);
        txt.setOrientation(LinearLayout.VERTICAL);
        txt.addView(tv("Voir votre progression", 12, GREY, false));
        txt.addView(tv("Débutant A1 ▾  ·  " + done + "/" + BibleCourse.UNITS.length + " unités", 17, TEXT, true));
        prog.addView(txt, new LinearLayout.LayoutParams(0, W, 1f));
        prog.addView(tv("→", 22, GREEN, true));
        prog.setOnClickListener(v -> select(3));
        homeBox.addView(prog, lp(M, W, 0, 0, 0, 12));

        // Accès rapides
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.addView(tile("🔎", "Mots", DictionaryActivity.class), lp(0, W, 0, 0, 4, 0));
        row.addView(tile("✍️", "Examen", QuizActivity.class), lp(0, W, 4, 0, 4, 0));
        row.addView(tile("🌍", "Traducteur", TranslateActivity.class), lp(0, W, 4, 0, 4, 0));
        row.addView(tile("🎙️", "Voix Fon", FonTtsActivity.class), lp(0, W, 4, 0, 0, 0));
        for (int i = 0; i < 4; i++) {
            ((LinearLayout.LayoutParams) row.getChildAt(i).getLayoutParams()).weight = 1f;
        }
        homeBox.addView(row, lp(M, W, 0, 0, 0, 8));

        LinearLayout row2 = new LinearLayout(this);
        row2.setOrientation(LinearLayout.HORIZONTAL);
        row2.addView(tile("📖", "Bible en fongbé : versets et mots", BibleActivity.class),
            lp(0, W, 0, 0, 0, 0));
        ((LinearLayout.LayoutParams) row2.getChildAt(0).getLayoutParams()).weight = 1f;
        homeBox.addView(row2, lp(M, W, 0, 0, 0, 16));

        homeBox.addView(tv("Parcours", 18, TEXT, true), lp(M, W, 0, 0, 0, 8));

        int firstUndone = -1;
        for (int i = 0; i < total; i++) {
            if (!Progress.isDone(this, i)) {
                firstUndone = i;
                break;
            }
        }

        int[] colors = {YELLOW, OLIVE, GREEN, ORANGE};

        for (int i = 0; i < total; i++) {
            final int idx = i;
            FongbeData.Lesson lesson = FongbeData.LESSONS[i];
            int color = colors[i % 4];
            int textColor = (i % 4 == 0) ? TEXT : WHITE;

            LinearLayout card = box(LinearLayout.VERTICAL, 16);
            card.setBackground(bg(color, 20, 0));

            LinearLayout top = new LinearLayout(this);
            top.setOrientation(LinearLayout.HORIZONTAL);
            top.setGravity(Gravity.CENTER_VERTICAL);

            LinearLayout left = new LinearLayout(this);
            left.setOrientation(LinearLayout.VERTICAL);
            left.addView(tv("Unité " + (i + 1), 12, textColor, false));
            left.addView(tv(lesson.title, 18, textColor, true), lp(M, W, 0, 2, 0, 4));
            left.addView(tv(shortText(lesson.intro), 13, textColor, false));
            top.addView(left, new LinearLayout.LayoutParams(0, W, 1f));
            top.addView(tv(UNIT_EMOJI[i % UNIT_EMOJI.length], 44, textColor, false),
                lp(W, W, 8, 0, 0, 0));
            card.addView(top);

            boolean isDone = Progress.isDone(this, i);
            final boolean locked = firstUndone != -1 && i > firstUndone;
            final int unlockAt = firstUndone;
            String label = isDone ? "Revoir ✓"
                : (locked ? "🔒 Débloquer cette unité" : (i == firstUndone ? "Continuer" : "Commencer"));
            TextView btn = tv(label, 14, GREEN, true);
            btn.setGravity(Gravity.CENTER);
            btn.setPadding(dp(8), dp(10), dp(8), dp(10));
            btn.setBackground(bg(WHITE, 14, 0));
            card.addView(btn, lp(M, W, 0, 12, 0, 0));

            card.setOnClickListener(v -> {
                if (locked) {
                    Toast.makeText(this, "Termine d'abord l'unité " + (unlockAt + 1) + ".",
                        Toast.LENGTH_SHORT).show();
                } else {
                    openLesson(idx);
                }
            });
            homeBox.addView(card, lp(M, W, 0, 0, 0, 12));
        }

        if (total < BibleCourse.UNITS.length) {
            homeBox.addView(loadingCard(), lp(M, W, 0, 0, 0, 12));
        }
    }

    /** Message affiché tant que les passages de la Bible en fon ne sont pas sur le téléphone. */
    private View loadingCard() {
        LinearLayout c = box(LinearLayout.VERTICAL, 16);
        c.setBackground(bg(WHITE, 18, YELLOW_STROKE));
        boolean busy = BibleCourse.syncing;
        c.addView(tv(busy ? "Chargement de la Bible en fon…" : "Passages non chargés", 16, TEXT, true));
        String detail = busy
            ? "Les leçons arrivent au fur et à mesure. Une fois chargées, elles marchent sans Internet."
            : "Le cours utilise la Bible en fon : connecte-toi à Internet une première fois."
                + (BibleCourse.error.isEmpty() ? "" : "\n(" + BibleCourse.error + ")");
        c.addView(tv(detail, 13, GREY, false), lp(M, W, 0, 4, 0, 0));
        if (!busy) {
            TextView retry = tv("Réessayer", 14, GREEN, true);
            retry.setGravity(Gravity.CENTER);
            retry.setPadding(dp(8), dp(10), dp(8), dp(10));
            retry.setBackground(bg(YELLOW, 14, 0));
            retry.setOnClickListener(v -> {
                startBibleSync();
                refreshAll();
            });
            c.addView(retry, lp(M, W, 0, 10, 0, 0));
        }
        return c;
    }

    private String shortText(String s) {
        int dot = s.indexOf(". ");
        String t = (dot > 20 && dot < 110) ? s.substring(0, dot + 1) : s;
        if (t.length() > 110) {
            t = t.substring(0, 107) + "…";
        }
        return t.replace("\n", " ");
    }

    private View tile(String emoji, String label, final Class<?> cls) {
        LinearLayout t = box(LinearLayout.VERTICAL, 12);
        t.setGravity(Gravity.CENTER);
        t.setBackground(bg(WHITE, 16, YELLOW_STROKE));
        t.addView(tv(emoji, 24, TEXT, false));
        TextView l = tv(label, 12, TEXT, true);
        l.setGravity(Gravity.CENTER);
        t.addView(l, lp(W, W, 0, 4, 0, 0));
        t.setOnClickListener(v -> startActivity(new Intent(this, cls)));
        return t;
    }

    private void openLesson(int index) {
        Intent it = new Intent(this, LessonActivity.class);
        it.putExtra("index", index);
        startActivity(it);
    }

    // ------------------------------------------------------------------
    // Révision
    // ------------------------------------------------------------------

    private void buildReview() {
        reviewBox.removeAllViews();
        reviewBox.addView(tv("Révision", 24, TEXT, true), lp(M, W, 0, 0, 0, 12));

        LinearLayout seg = box(LinearLayout.HORIZONTAL, 4);
        seg.setBackground(bg(0xFFFBEFC8, 14, 0));
        seg.addView(segButton("Grammaire", !showVocab, false), new LinearLayout.LayoutParams(0, W, 1f));
        seg.addView(segButton("Vocabulaire", showVocab, true), new LinearLayout.LayoutParams(0, W, 1f));
        reviewBox.addView(seg, lp(M, W, 0, 0, 0, 16));

        if (showVocab) {
            buildVocab();
        } else {
            buildGrammar();
        }
    }

    private View segButton(String label, boolean selected, final boolean vocab) {
        TextView b = tv(label, 14, selected ? GREEN : GREY, selected);
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(8), dp(10), dp(8), dp(10));
        if (selected) {
            b.setBackground(bg(WHITE, 12, 0));
        }
        b.setOnClickListener(v -> {
            showVocab = vocab;
            buildReview();
        });
        return b;
    }

    private void buildVocab() {
        if (FongbeData.WORDS.length == 0) {
            reviewBox.addView(loadingCard(), lp(M, W, 0, 0, 0, 12));
            return;
        }

        // Mot du jour
        int dayIdx = Calendar.getInstance().get(Calendar.DAY_OF_YEAR) % FongbeData.WORDS.length;
        final FongbeData.Entry w = FongbeData.WORDS[dayIdx];

        reviewBox.addView(tv("Mot du jour", 16, TEXT, true), lp(M, W, 0, 0, 0, 8));

        LinearLayout wc = box(LinearLayout.HORIZONTAL, 16);
        wc.setGravity(Gravity.CENTER_VERTICAL);
        wc.setBackground(bg(WHITE, 18, YELLOW_STROKE));

        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        TextView chip = tv("A1 · Mot", 11, GREEN, true);
        chip.setPadding(dp(8), dp(2), dp(8), dp(2));
        chip.setBackground(bg(0xFFE3F1EA, 8, 0));
        info.addView(chip);
        info.addView(tv(w.fon, 24, GREEN, true), lp(M, W, 0, 6, 0, 0));
        info.addView(tv(w.fr, 14, GREY, false));
        wc.addView(info, new LinearLayout.LayoutParams(0, W, 1f));

        TextView speak = tv("🔊", 22, TEXT, false);
        speak.setGravity(Gravity.CENTER);
        speak.setPadding(dp(12), dp(12), dp(12), dp(12));
        speak.setBackground(bg(YELLOW, 24, 0));
        speak.setOnClickListener(v -> BibleAudio.play(this, w));
        wc.addView(speak);
        reviewBox.addView(wc, lp(M, W, 0, 0, 0, 16));

        // Decks
        reviewBox.addView(tv("Mes decks", 16, TEXT, true), lp(M, W, 0, 0, 0, 8));

        int n = (FongbeData.WORDS.length + DECK - 1) / DECK;
        LinearLayout row = null;
        for (int d = 0; d < n; d++) {
            if (d % 2 == 0) {
                row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                reviewBox.addView(row, lp(M, W, 0, 0, 0, 8));
            }
            LinearLayout.LayoutParams p = lp(0, W, d % 2 == 0 ? 0 : 4, 0, d % 2 == 0 ? 4 : 0, 0);
            p.weight = 1f;
            row.addView(deckCard(d), p);
        }
        if (n % 2 == 1 && row != null) {
            View spacer = new View(this);
            LinearLayout.LayoutParams p = lp(0, 1, 4, 0, 0, 0);
            p.weight = 1f;
            row.addView(spacer, p);
        }
    }

    private View deckCard(final int d) {
        int start = d * DECK;
        int end = Math.min(start + DECK, FongbeData.WORDS.length);
        int count = end - start;
        int learned = Progress.learnedInRange(this, FongbeData.WORDS, start, end);

        LinearLayout c = box(LinearLayout.VERTICAL, 14);
        c.setBackground(bg(WHITE, 16, YELLOW_STROKE));

        c.addView(tv("Deck " + (d + 1), 15, TEXT, true));

        TextView sub = tv(FongbeData.WORDS[start].fon
            + (end - start > 1 ? ", " + FongbeData.WORDS[start + 1].fon : "") + "…",
            12, GREY, false);
        sub.setMaxLines(1);
        sub.setEllipsize(TextUtils.TruncateAt.END);
        c.addView(sub, lp(M, W, 0, 2, 0, 8));

        c.addView(tv(learned + "/" + count + " appris", 12, TEXT, false));

        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setBackground(bg(0xFFF1EBD6, 4, 0));
        View fill = new View(this);
        fill.setBackground(bg(ORANGE, 4, 0));
        View rest = new View(this);
        LinearLayout.LayoutParams pf = new LinearLayout.LayoutParams(0, dp(6), (float) learned);
        LinearLayout.LayoutParams pr = new LinearLayout.LayoutParams(0, dp(6), (float) (count - learned));
        bar.addView(fill, pf);
        bar.addView(rest, pr);
        c.addView(bar, lp(M, dp(6), 0, 6, 0, 0));

        c.setOnClickListener(v -> openCard(d, 0));
        return c;
    }

    private void openCard(final int deck, final int i) {
        int start = deck * DECK;
        int end = Math.min(start + DECK, FongbeData.WORDS.length);
        if (start + i >= end) {
            Toast.makeText(this, "Deck terminé 🎉", Toast.LENGTH_SHORT).show();
            refreshHeader();
            buildReview();
            return;
        }
        final FongbeData.Entry e = FongbeData.WORDS[start + i];
        new AlertDialog.Builder(this)
            .setTitle("Deck " + (deck + 1) + " · " + (i + 1) + "/" + (end - start))
            .setMessage(e.fon + "\n\nTu connais ce mot de la Bible en fon ?")
            .setPositiveButton("Voir le verset", (d, w) -> showAnswer(deck, i, e))
            .setNegativeButton("Fermer", (d, w) -> {
                refreshHeader();
                buildReview();
            })
            .setOnCancelListener(d -> {
                refreshHeader();
                buildReview();
            })
            .show();
    }

    private void showAnswer(final int deck, final int i, final FongbeData.Entry e) {
        BibleAudio.play(this, e);
        String context = e.fr;
        if (e instanceof BibleCourse.Word) {
            BibleCourse.Word bw = (BibleCourse.Word) e;
            context = bw.ref + "\n" + bw.verseFon
                + (bw.verseFr.isEmpty() ? "" : "\n\n" + bw.verseFr);
        }
        new AlertDialog.Builder(this)
            .setTitle(e.fon)
            .setMessage(context)
            .setPositiveButton("Je sais ✓", (d, w) -> {
                Progress.setLearned(this, e.key, true);
                openCard(deck, i + 1);
            })
            .setNeutralButton("À revoir", (d, w) -> {
                Progress.setLearned(this, e.key, false);
                openCard(deck, i + 1);
            })
            .setNegativeButton("Fermer", (d, w) -> {
                refreshHeader();
                buildReview();
            })
            .setOnCancelListener(d -> {
                refreshHeader();
                buildReview();
            })
            .show();
    }

    private void buildGrammar() {
        for (int i = 0; i < FongbeData.LESSONS.length; i++) {
            final int idx = i;
            FongbeData.Lesson lesson = FongbeData.LESSONS[i];

            LinearLayout card = box(LinearLayout.VERTICAL, 14);
            card.setBackground(bg(WHITE, 16, YELLOW_STROKE));

            LinearLayout head = new LinearLayout(this);
            head.setOrientation(LinearLayout.HORIZONTAL);
            head.setGravity(Gravity.CENTER_VERTICAL);

            TextView badge = tv(UNIT_EMOJI[i % UNIT_EMOJI.length], 18, TEXT, false);
            badge.setGravity(Gravity.CENTER);
            badge.setPadding(dp(8), dp(6), dp(8), dp(6));
            badge.setBackground(bg(YELLOW, 10, 0));
            head.addView(badge, lp(W, W, 0, 0, 12, 0));

            TextView title = tv(lesson.title, 15, TEXT, true);
            head.addView(title, new LinearLayout.LayoutParams(0, W, 1f));
            final TextView arrow = tv("▾", 18, GREY, false);
            head.addView(arrow);
            card.addView(head);

            final LinearLayout body = new LinearLayout(this);
            body.setOrientation(LinearLayout.VERTICAL);
            body.setVisibility(View.GONE);
            TextView intro = tv(lesson.intro, 14, TEXT, false);
            intro.setLineSpacing(dp(3), 1f);
            body.addView(intro, lp(M, W, 0, 10, 0, 8));
            TextView open = tv("Ouvrir la leçon →", 14, GREEN, true);
            open.setOnClickListener(v -> openLesson(idx));
            body.addView(open);
            card.addView(body);

            card.setOnClickListener(v -> {
                boolean hidden = body.getVisibility() == View.GONE;
                body.setVisibility(hidden ? View.VISIBLE : View.GONE);
                arrow.setText(hidden ? "▴" : "▾");
            });
            reviewBox.addView(card, lp(M, W, 0, 0, 0, 8));
        }
    }

    // ------------------------------------------------------------------
    // Communauté
    // ------------------------------------------------------------------

    private void buildCommunity() {
        communityBox.removeAllViews();
        communityBox.addView(tv("Communauté", 24, TEXT, true), lp(M, W, 0, 0, 0, 12));

        LinearLayout c = box(LinearLayout.VERTICAL, 20);
        c.setGravity(Gravity.CENTER_HORIZONTAL);
        c.setBackground(bg(YELLOW, 20, YELLOW_STROKE));
        c.addView(tv("👥", 44, TEXT, false));
        TextView t = tv("Bientôt : échanges entre apprenants, défis entre amis et classement.",
            15, TEXT, false);
        t.setGravity(Gravity.CENTER);
        c.addView(t, lp(M, W, 0, 8, 0, 0));
        communityBox.addView(c, lp(M, W, 0, 0, 0, 12));

        TextView share = tv("📣 Inviter un ami", 15, WHITE, true);
        share.setGravity(Gravity.CENTER);
        share.setPadding(dp(8), dp(14), dp(8), dp(14));
        share.setBackground(bg(GREEN, 16, 0));
        share.setOnClickListener(v -> {
            Intent s = new Intent(Intent.ACTION_SEND);
            s.setType("text/plain");
            s.putExtra(Intent.EXTRA_TEXT,
                "J'apprends le fongbé avec Fongbé Académie 🇧🇯 Rejoins-moi !");
            startActivity(Intent.createChooser(s, "Partager"));
        });
        communityBox.addView(share, lp(M, W, 0, 0, 0, 0));
    }

    // ------------------------------------------------------------------
    // Profil
    // ------------------------------------------------------------------

    private void buildProfile() {
        profileBox.removeAllViews();
        profileBox.addView(tv("Profil", 24, TEXT, true), lp(M, W, 0, 0, 0, 12));

        org.json.JSONObject user = Store.currentUser(this);
        if (user != null) {
            String name = (user.optString("prenom") + " " + user.optString("nom")).trim();
            LinearLayout pc = box(LinearLayout.HORIZONTAL, 16);
            pc.setGravity(Gravity.CENTER_VERTICAL);
            pc.setBackground(bg(YELLOW, 18, YELLOW_STROKE));
            pc.addView(tv("🙂", 32, TEXT, false), lp(W, W, 0, 0, 12, 0));
            LinearLayout pt = new LinearLayout(this);
            pt.setOrientation(LinearLayout.VERTICAL);
            pt.addView(tv(name, 17, TEXT, true));
            String sub = user.optBoolean("admin") ? "Administrateur" : user.optString("numero");
            pt.addView(tv(sub, 13, GREY, false));
            pc.addView(pt);
            profileBox.addView(pc, lp(M, W, 0, 0, 0, 12));
        }

        LinearLayout r1 = new LinearLayout(this);
        r1.setOrientation(LinearLayout.HORIZONTAL);
        addStat(r1, "🔥", String.valueOf(Progress.getStreak(this)), "jours d'affilée", true);
        addStat(r1, "⭐", String.valueOf(Progress.getPoints(this)), "points", false);
        profileBox.addView(r1, lp(M, W, 0, 0, 0, 8));

        LinearLayout r2 = new LinearLayout(this);
        r2.setOrientation(LinearLayout.HORIZONTAL);
        addStat(r2, "✅", Progress.doneCount(this) + "/" + FongbeData.LESSONS.length,
            "leçons terminées", true);
        addStat(r2, "📖", Progress.learnedCount(this) + "/" + FongbeData.WORDS.length,
            "mots appris", false);
        profileBox.addView(r2, lp(M, W, 0, 0, 0, 16));

        profileBox.addView(menuRow("⚙️  Paramètres", v ->
            startActivity(new Intent(this, SettingsActivity.class))), lp(M, W, 0, 0, 0, 8));

        profileBox.addView(menuRow("🏫  Espace enseignant", v ->
            new AlertDialog.Builder(this)
                .setTitle("Espace enseignant")
                .setMessage("Bienvenue dans l'espace enseignant. "
                    + "La gestion des classes, des élèves et des résultats "
                    + "sera connectée à un serveur scolaire dans une prochaine version.")
                .setPositiveButton("OK", null)
                .show()), lp(M, W, 0, 0, 0, 8));

        profileBox.addView(menuRow("♻️  Réinitialiser ma progression", v ->
            new AlertDialog.Builder(this)
                .setTitle("Réinitialiser ?")
                .setMessage("Tes leçons terminées, mots appris et points seront effacés.")
                .setPositiveButton("Réinitialiser", (d, w) -> {
                    Progress.reset(this);
                    refreshAll();
                })
                .setNegativeButton("Annuler", null)
                .show()), lp(M, W, 0, 0, 0, 16));

        TextView credit = tv("Tous les cours, mots et exercices viennent de la Bible en fon. "
            + "Chargés une première fois, ils fonctionnent sans Internet.\n"
            + FongbeData.CREDIT, 12, GREY, false);
        profileBox.addView(credit);
    }

    private void addStat(LinearLayout row, String emoji, String value, String label, boolean left) {
        LinearLayout t = box(LinearLayout.VERTICAL, 14);
        t.setBackground(bg(WHITE, 16, YELLOW_STROKE));
        t.addView(tv(emoji + "  " + value, 20, TEXT, true));
        t.addView(tv(label, 12, GREY, false), lp(M, W, 0, 2, 0, 0));
        LinearLayout.LayoutParams p = lp(0, W, left ? 0 : 4, 0, left ? 4 : 0, 0);
        p.weight = 1f;
        row.addView(t, p);
    }

    private View menuRow(String label, View.OnClickListener l) {
        TextView t = tv(label, 15, TEXT, true);
        t.setPadding(dp(16), dp(16), dp(16), dp(16));
        t.setBackground(bg(WHITE, 16, YELLOW_STROKE));
        t.setOnClickListener(l);
        return t;
    }
}
