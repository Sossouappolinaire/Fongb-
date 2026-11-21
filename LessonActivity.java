package bj.fongbe.academie;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Une leçon = un passage de la Bible en fon : lire et écouter les versets, apprendre
 * les mots du passage, puis faire l'exercice. Présentation : cartes arrondies, sections
 * numérotées, couleurs de l'écran d'accueil.
 */
public class LessonActivity extends Activity {

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

    private TextToSpeech tts;
    private int index = 0;
    private LinearLayout root;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_lesson);
        root = findViewById(R.id.lessonRoot);

        index = getIntent().getIntExtra("index", 0);
        if (index < 0 || index >= FongbeData.LESSONS.length) {
            index = 0;
        }

        // La voix française ne lit que l'explication en français.
        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                tts.setLanguage(Locale.FRENCH);
            }
        });

        showLesson();
    }

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

    private LinearLayout card(int color, int stroke, int pad) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(pad), dp(pad), dp(pad), dp(pad));
        c.setBackground(round(color, 18, stroke));
        return c;
    }

    private TextView button(String label, int bg, int fg, View.OnClickListener l) {
        TextView b = tv(label, 15, fg, true);
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(12), dp(14), dp(12), dp(14));
        b.setBackground(round(bg, 16, 0));
        b.setOnClickListener(l);
        return b;
    }

    /** En-tête d'une section : pastille numérotée (✓ si la leçon est terminée) + titre. */
    private LinearLayout sectionCard(int number, String title, boolean done) {
        LinearLayout c = card(WHITE, YELLOW_STROKE, 14);
        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);

        TextView dot = tv(done ? "✓" : String.valueOf(number), 14, done ? WHITE : GREEN, true);
        dot.setGravity(Gravity.CENTER);
        dot.setBackground(round(done ? GREEN : YELLOW, 16, 0));
        head.addView(dot, new LinearLayout.LayoutParams(dp(32), dp(32)));

        head.addView(tv("Section " + number, 11, GREY, false), lp(W, W, 12, 0, 6, 0));
        head.addView(tv(title, 16, TEXT, true), new LinearLayout.LayoutParams(0, W, 1f));
        c.addView(head, lp(M, W, 0, 0, 0, 10));
        return c;
    }

    private void showLesson() {
        root.removeAllViews();

        final int total = FongbeData.LESSONS.length;
        if (total == 0) {
            root.addView(tv("Le cours n'est pas encore chargé. Reviens à l'accueil et "
                + "connecte-toi à Internet une première fois.", 16, TEXT, false));
            root.addView(button("Retour", YELLOW, GREEN, v -> finish()), lp(M, W, 0, 16, 0, 0));
            return;
        }

        final FongbeData.Lesson lesson = FongbeData.LESSONS[index];
        BibleAudio.prefetch(this, index);
        final boolean done = Progress.isDone(this, index);
        final List<BibleCourse.Verse> verses = BibleCourse.versesOf(index);

        // Barre du haut
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        TextView back = tv("←", 24, GREEN, true);
        back.setPadding(dp(4), dp(4), dp(16), dp(4));
        back.setOnClickListener(v -> finish());
        bar.addView(back);
        bar.addView(tv("Unité " + (index + 1) + " / " + total, 13, GREY, false));
        root.addView(bar, lp(M, W, 0, 0, 0, 8));

        // Carte de titre (comme la carte « Leçon 1 » de la maquette)
        int[] colors = {YELLOW, OLIVE, GREEN, ORANGE};
        int color = colors[index % 4];
        int fg = (index % 4 == 0) ? TEXT : WHITE;
        LinearLayout head = card(color, 0, 16);
        head.addView(tv("Leçon " + (index + 1), 12, fg, false));
        head.addView(tv(lesson.title, 22, fg, true), lp(M, W, 0, 2, 0, 4));
        head.addView(tv(lesson.intro, 13, fg, false));
        TextView listen = tv("🔊 Écouter l'explication", 13, fg, true);
        listen.setPadding(0, dp(10), 0, 0);
        listen.setOnClickListener(v -> {
            if (tts != null) {
                tts.speak(lesson.intro, TextToSpeech.QUEUE_FLUSH, null, "lesson");
            }
        });
        head.addView(listen);
        root.addView(head, lp(M, W, 0, 0, 0, 12));

        // Section 1 : lire et écouter
        LinearLayout s1 = sectionCard(1, "Lire et écouter", done);
        for (final BibleCourse.Verse v : verses) {
            s1.addView(verseRow(v), lp(M, W, 0, 0, 0, 8));
        }
        root.addView(s1, lp(M, W, 0, 0, 0, 10));

        // Section 2 : mots du passage
        LinearLayout s2 = sectionCard(2, "Mots du passage", done);
        Set<String> refs = new HashSet<String>();
        for (BibleCourse.Verse v : verses) {
            refs.add(v.ref);
        }
        LinearLayout line = null;
        int inLine = 0;
        for (FongbeData.Entry e : FongbeData.WORDS) {
            if (!(e instanceof BibleCourse.Word) || !refs.contains(((BibleCourse.Word) e).ref)) {
                continue;
            }
            if (line == null || inLine == 3) {
                line = new LinearLayout(this);
                line.setOrientation(LinearLayout.HORIZONTAL);
                s2.addView(line, lp(M, W, 0, 0, 0, 6));
                inLine = 0;
            }
            LinearLayout.LayoutParams p = lp(0, W, 0, 0, 6, 0);
            p.weight = 1f;
            line.addView(wordChip((BibleCourse.Word) e), p);
            inLine++;
        }
        s2.addView(tv("Touche un mot pour l'entendre. Les mots sont découpés dans l'audio du "
            + "chapitre (dossier audio/mots, un dossier par mot). La coupe est automatique, "
            + "donc approximative : si un mot est mal coupé, relance la découpe.",
            12, GREY, false), lp(M, W, 0, 4, 0, 8));
        s2.addView(button("✂️ Redécouper les mots", YELLOW, GREEN, v ->
            WordSlicer.ensure(this, index, true, null)));
        root.addView(s2, lp(M, W, 0, 0, 0, 10));

        // Section 3 : exercice
        LinearLayout s3 = sectionCard(3, "Exercice", done);
        s3.addView(tv("Complète des versets et retrouve-les, tirés de ce passage.",
            14, TEXT, false), lp(M, W, 0, 0, 0, 10));
        s3.addView(button("Commencer l'exercice", GREEN, WHITE, v -> {
            Intent it = new Intent(this, QuizActivity.class);
            it.putExtra("unit", index);
            startActivity(it);
        }));
        root.addView(s3, lp(M, W, 0, 0, 0, 14));

        // Navigation
        LinearLayout nav = new LinearLayout(this);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        if (index > 0) {
            LinearLayout.LayoutParams p = lp(0, W, 0, 0, 6, 0);
            p.weight = 1f;
            nav.addView(button("← Précédent", YELLOW, GREEN, v -> {
                index--;
                showLesson();
            }), p);
        }
        LinearLayout.LayoutParams pn = lp(0, W, index > 0 ? 6 : 0, 0, 0, 0);
        pn.weight = 1f;
        nav.addView(button(index < total - 1 ? "Terminer et continuer →" : "Terminer ✓",
            GREEN, WHITE, v -> {
                Progress.markDone(this, index);
                if (index < total - 1) {
                    index++;
                    showLesson();
                    ((View) root.getParent()).scrollTo(0, 0);
                } else {
                    finish();
                }
            }), pn);
        root.addView(nav);
    }

    private View verseRow(final BibleCourse.Verse v) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(12), dp(10), dp(8), dp(10));
        row.setBackground(round(0xFFFFFBEF, 14, YELLOW_STROKE));

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.addView(tv(v.ref, 11, GREY, false));
        col.addView(tv(v.fon, 17, GREEN, true), lp(M, W, 0, 2, 0, 0));
        if (!v.fr.isEmpty()) {
            col.addView(tv(v.fr, 13, GREY, false), lp(M, W, 0, 4, 0, 0));
        }
        row.addView(col, new LinearLayout.LayoutParams(0, W, 1f));

        TextView b = tv("🔊", 20, TEXT, false);
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(12), dp(10), dp(12), dp(10));
        b.setBackground(round(YELLOW, 22, 0));
        b.setOnClickListener(x -> BibleAudio.play(this, new FongbeData.Entry(v.ref, v.fon, v.key)));
        row.addView(b);
        return row;
    }

    private View wordChip(final BibleCourse.Word w) {
        TextView c = tv(w.fon, 15, GREEN, true);
        c.setGravity(Gravity.CENTER);
        c.setPadding(dp(6), dp(10), dp(6), dp(10));
        c.setBackground(round(0xFFE3F1EA, 12, 0));
        c.setOnClickListener(x -> {
            BibleAudio.play(this, w);
        });
        return c;
    }

    @Override
    protected void onDestroy() {

        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
        FongbeVoice.stopPlayer();
        BibleAudio.stop();

        super.onDestroy();
    }
}
