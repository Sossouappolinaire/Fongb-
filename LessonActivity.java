package bj.fongbe.academie;

import android.app.Activity;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Locale;

public class LessonActivity extends Activity {

    private TextToSpeech tts;
    private int index = 0;

    private TextView title;
    private TextView body;
    private LinearLayout phrases;
    private Button prev;
    private Button next;

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_lesson);

        index = getIntent().getIntExtra("index", 0);
        if (index < 0 || index >= FongbeData.LESSONS.length) {
            index = 0;
        }

        title = findViewById(R.id.lessonTitle);
        body = findViewById(R.id.lessonBody);
        phrases = findViewById(R.id.phrasesContainer);

        Button speak = findViewById(R.id.btnSpeak);
        Button back = findViewById(R.id.btnBack);
        prev = findViewById(R.id.btnPrev);
        next = findViewById(R.id.btnNext);

        // La voix française ne lit que l'explication en français.
        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                tts.setLanguage(Locale.FRENCH);
            }
        });

        speak.setOnClickListener(v -> {
            if (tts != null) {
                tts.speak(
                    FongbeData.LESSONS[index].intro,
                    TextToSpeech.QUEUE_FLUSH,
                    null,
                    "lesson"
                );
            }
        });

        prev.setOnClickListener(v -> {
            if (index > 0) {
                index--;
                showLesson();
            }
        });

        next.setOnClickListener(v -> {
            Progress.markDone(this, index);
            if (index < FongbeData.LESSONS.length - 1) {
                index++;
                showLesson();
            } else {
                finish();
            }
        });

        back.setOnClickListener(v -> finish());

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

    private void showLesson() {

        FongbeData.Lesson lesson = FongbeData.LESSONS[index];

        title.setText(
            "Cours " + (index + 1) + "/" + FongbeData.LESSONS.length
            + " : " + lesson.title
        );
        body.setText(lesson.intro);

        phrases.removeAllViews();

        for (final FongbeData.Entry e : lesson.phrases) {

            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(14), dp(10), dp(10), dp(10));
            row.setBackground(round(0xFFFFFFFF, 16, 0xFFF3D36B));

            TextView t = new TextView(this);
            t.setTextSize(17);
            t.setTextColor(0xFF2B2B2B);
            t.setLayoutParams(new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            t.setText(e.fon + "\n" + e.fr);

            TextView b = new TextView(this);
            b.setText("🔊");
            b.setTextSize(20);
            b.setGravity(Gravity.CENTER);
            b.setPadding(dp(12), dp(10), dp(12), dp(10));
            b.setBackground(round(0xFFFBE7A1, 22, 0));
            b.setOnClickListener(v -> FongbeVoice.speak(this, e));

            row.addView(t);
            row.addView(b);

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.setMargins(0, 0, 0, dp(8));
            phrases.addView(row, lp);
        }

        prev.setEnabled(index > 0);
        next.setEnabled(true);
        next.setText(index < FongbeData.LESSONS.length - 1
            ? "Suivant →" : "Terminer ✓");
    }

    @Override
    protected void onDestroy() {

        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
        FongbeVoice.stopPlayer();

        super.onDestroy();
    }
}
