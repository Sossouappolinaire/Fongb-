package bj.fongbe.academie;

import android.app.Activity;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Exercices et examen : toutes les questions sont tirées des versets de la Bible en fon.
 *  - « Complète le verset » : un mot du verset fon est caché, choisis le bon mot ;
 *  - « Quel verset ? » : un verset en français (Louis Segond) s'affiche, retrouve le verset fon.
 * Lancé depuis une leçon (extra « unit »), l'exercice porte sur ce passage ; lancé depuis
 * l'accueil, c'est l'examen sur tous les passages chargés.
 */
public class QuizActivity extends Activity {

    private static final int BG = 0xFFFFF8E7;
    private static final int YELLOW = 0xFFFBE7A1;
    private static final int YELLOW_STROKE = 0xFFF3D36B;
    private static final int GREEN = 0xFF176B52;
    private static final int RED = 0xFFC0392B;
    private static final int ORANGE = 0xFFE8782A;
    private static final int TEXT = 0xFF2B2B2B;
    private static final int GREY = 0xFF7A7A7A;
    private static final int WHITE = 0xFFFFFFFF;

    private static final int M = LinearLayout.LayoutParams.MATCH_PARENT;
    private static final int W = LinearLayout.LayoutParams.WRAP_CONTENT;

    private static final int TOTAL = 10;
    private static final Pattern TOKEN = Pattern.compile("[\\p{L}\\p{M}]+");

    private final Random random = new Random();

    private LinearLayout root;
    private List<BibleCourse.Verse> pool = new ArrayList<BibleCourse.Verse>();
    private List<String> allWords = new ArrayList<String>();
    private int unit = -1;

    private int score = 0;
    private int number = 0;
    private int correctIndex = 0;
    private boolean answered = false;

    private BibleCourse.Verse current;
    private String[] options = new String[3];
    private String prompt = "";
    private String instruction = "";

    private final TextView[] answerViews = new TextView[3];
    private TextView feedback;
    private LinearLayout actions;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_quiz);
        root = findViewById(R.id.quizRoot);

        unit = getIntent().getIntExtra("unit", -1);

        BibleCourse.Verse[] all = BibleCourse.verses();
        for (BibleCourse.Verse v : all) {
            if (unit < 0 || v.unit == unit) {
                pool.add(v);
            }
            for (String t : BibleCourse.tokens(v.fon)) {
                if (t.length() >= 3 && !containsIgnoreCase(allWords, t)) {
                    allWords.add(t);
                }
            }
        }

        if (all.length < 3 || pool.isEmpty()) {
            root.addView(tv("Les exercices utilisent le texte de la Bible en fon. Il n'est pas "
                + "encore chargé : reviens à l'accueil avec une connexion Internet.",
                16, TEXT, false));
            root.addView(button("Retour", YELLOW, GREEN, v -> finish()), lp(M, W, 0, 16, 0, 0));
            return;
        }

        showQuestion();
    }

    // ------------------------------------------------------------------
    // Outils
    // ------------------------------------------------------------------

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }

    private GradientDrawable round(int color, int radiusDp, int strokeColor) {
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

    private TextView button(String label, int bg, int fg, View.OnClickListener l) {
        TextView b = tv(label, 15, fg, true);
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(12), dp(14), dp(12), dp(14));
        b.setBackground(round(bg, 16, 0));
        b.setOnClickListener(l);
        return b;
    }

    private static boolean containsIgnoreCase(List<String> list, String s) {
        for (String x : list) {
            if (x.equalsIgnoreCase(s)) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------
    // Génération des questions
    // ------------------------------------------------------------------

    private void makeQuestion() {
        for (int attempt = 0; attempt < 30; attempt++) {
            current = pool.get(random.nextInt(pool.size()));
            boolean canMatch = !current.fr.isEmpty() && BibleCourse.verses().length >= 3;
            boolean wantMatch = canMatch && random.nextBoolean();
            if (wantMatch) {
                makeMatch();
                return;
            }
            if (makeCloze()) {
                return;
            }
            if (canMatch) {
                makeMatch();
                return;
            }
        }
        // dernier recours : on ne devrait jamais arriver ici avec du texte chargé
        current = pool.get(0);
        makeMatch();
    }

    /** Complète le verset : un mot du verset fon est remplacé par un blanc. */
    private boolean makeCloze() {
        List<int[]> spots = new ArrayList<int[]>();
        Matcher m = TOKEN.matcher(current.fon);
        while (m.find()) {
            if (m.end() - m.start() >= 3) {
                spots.add(new int[] {m.start(), m.end()});
            }
        }
        if (spots.isEmpty() || allWords.size() < 3) {
            return false;
        }
        int[] spot = spots.get(random.nextInt(spots.size()));
        String answer = current.fon.substring(spot[0], spot[1]);

        List<String> opts = new ArrayList<String>();
        opts.add(answer);
        int guard = 0;
        while (opts.size() < 3 && guard++ < 200) {
            String other = allWords.get(random.nextInt(allWords.size()));
            if (!containsIgnoreCase(opts, other)) {
                opts.add(other);
            }
        }
        if (opts.size() < 3) {
            return false;
        }
        Collections.shuffle(opts, random);
        for (int i = 0; i < 3; i++) {
            options[i] = opts.get(i);
            if (options[i].equals(answer)) {
                correctIndex = i;
            }
        }
        instruction = "Complète le verset · " + current.ref;
        prompt = current.fon.substring(0, spot[0]) + "_____" + current.fon.substring(spot[1]);
        return true;
    }

    /** Quel verset ? : le texte français (LSG) est donné, il faut retrouver le verset en fon. */
    private void makeMatch() {
        List<String> opts = new ArrayList<String>();
        opts.add(current.fon);
        BibleCourse.Verse[] all = BibleCourse.verses();
        int guard = 0;
        while (opts.size() < 3 && guard++ < 200) {
            String other = all[random.nextInt(all.length)].fon;
            if (!opts.contains(other)) {
                opts.add(other);
            }
        }
        Collections.shuffle(opts, random);
        for (int i = 0; i < 3; i++) {
            options[i] = opts.get(i);
            if (options[i].equals(current.fon)) {
                correctIndex = i;
            }
        }
        instruction = "Quel est ce verset en fon ?";
        prompt = current.fr.isEmpty() ? current.ref : "« " + current.fr + " » (" + current.ref + ")";
    }

    // ------------------------------------------------------------------
    // Affichage
    // ------------------------------------------------------------------

    private void showQuestion() {
        answered = false;
        number++;
        makeQuestion();

        root.removeAllViews();

        // Barre du haut : retour + titre
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        TextView back = tv("←", 24, GREEN, true);
        back.setPadding(dp(4), dp(4), dp(16), dp(4));
        back.setOnClickListener(v -> finish());
        bar.addView(back);
        String title = unit >= 0 && unit < BibleCourse.UNITS.length
            ? "Exercice · " + BibleCourse.UNITS[unit].title : "Examen · toute la Bible du cours";
        bar.addView(tv(title, 14, GREY, false), new LinearLayout.LayoutParams(0, W, 1f));
        bar.addView(tv("⭐ " + score, 14, TEXT, true));
        root.addView(bar, lp(M, W, 0, 0, 0, 8));

        // Barre de progression
        LinearLayout prog = new LinearLayout(this);
        prog.setOrientation(LinearLayout.HORIZONTAL);
        prog.setBackground(round(0xFFF1EBD6, 4, 0));
        View fill = new View(this);
        fill.setBackground(round(ORANGE, 4, 0));
        View rest = new View(this);
        prog.addView(fill, new LinearLayout.LayoutParams(0, dp(8), (float) (number - 1)));
        prog.addView(rest, new LinearLayout.LayoutParams(0, dp(8), (float) (TOTAL - number + 1)));
        root.addView(prog, lp(M, dp(8), 0, 0, 0, 6));
        root.addView(tv("Question " + number + " / " + TOTAL, 12, GREY, false),
            lp(M, W, 0, 0, 0, 12));

        // Carte de la question
        LinearLayout q = new LinearLayout(this);
        q.setOrientation(LinearLayout.VERTICAL);
        q.setPadding(dp(16), dp(16), dp(16), dp(16));
        q.setBackground(round(YELLOW, 18, YELLOW_STROKE));
        q.addView(tv(instruction, 12, GREY, false));
        q.addView(tv(prompt, 19, TEXT, true), lp(M, W, 0, 6, 0, 0));
        root.addView(q, lp(M, W, 0, 0, 0, 14));

        // Réponses
        for (int i = 0; i < 3; i++) {
            final int choice = i;
            TextView a = tv(options[i], 16, TEXT, true);
            a.setPadding(dp(16), dp(14), dp(16), dp(14));
            a.setBackground(round(WHITE, 16, YELLOW_STROKE));
            a.setOnClickListener(v -> onAnswer(choice));
            answerViews[i] = a;
            root.addView(a, lp(M, W, 0, 0, 0, 8));
        }

        feedback = tv("", 15, TEXT, true);
        root.addView(feedback, lp(M, W, 0, 8, 0, 8));

        actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.VERTICAL);
        actions.setVisibility(View.GONE);
        root.addView(actions, lp(M, W, 0, 0, 0, 0));
    }

    private void onAnswer(int choice) {
        if (answered) {
            return;
        }
        answered = true;

        for (int i = 0; i < 3; i++) {
            if (i == correctIndex) {
                answerViews[i].setBackground(round(0xFFE3F1EA, 16, GREEN));
            } else if (i == choice) {
                answerViews[i].setBackground(round(0xFFF8DEDA, 16, RED));
            }
        }

        if (choice == correctIndex) {
            score++;
            Progress.addPoints(this, 1);
            feedback.setTextColor(GREEN);
            feedback.setText("Bonne réponse !");
        } else {
            feedback.setTextColor(RED);
            feedback.setText("Pas tout à fait. Le verset : " + current.ref);
        }

        // Le verset complet, tel qu'écrit dans la Bible en fon
        actions.removeAllViews();
        TextView full = tv(current.fon, 15, GREEN, true);
        full.setPadding(dp(14), dp(12), dp(14), dp(12));
        full.setBackground(round(WHITE, 14, YELLOW_STROKE));
        actions.addView(full, lp(M, W, 0, 0, 0, 8));

        actions.addView(button("🔊 Écouter le verset", YELLOW, GREEN, v ->
            BibleAudio.play(this, new FongbeData.Entry(current.ref, current.fon, current.key))),
            lp(M, W, 0, 0, 0, 8));

        if (number >= TOTAL) {
            actions.addView(tv("Terminé ! Score final : " + score + " / " + TOTAL,
                17, TEXT, true), lp(M, W, 0, 4, 0, 8));
            actions.addView(button("Recommencer", GREEN, WHITE, v -> {
                score = 0;
                number = 0;
                showQuestion();
            }), lp(M, W, 0, 0, 0, 8));
            actions.addView(button("Retour", YELLOW, GREEN, v -> finish()));
        } else {
            actions.addView(button("Question suivante", GREEN, WHITE, v -> showQuestion()));
        }
        actions.setVisibility(View.VISIBLE);
    }

    @Override
    protected void onDestroy() {
        FongbeVoice.stopPlayer();
        BibleAudio.stop();
        super.onDestroy();
    }
}
