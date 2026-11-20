package bj.fongbe.academie;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public class QuizActivity extends Activity {

    private static final int TOTAL = 10;

    private final Random random = new Random();

    private int score = 0;
    private int number = 0;
    private int correctIndex = 0;
    private boolean answered = false;

    private FongbeData.Entry current;

    private TextView progress;
    private TextView question;
    private TextView feedback;
    private Button[] answers = new Button[3];
    private Button listen;
    private Button nextQuestion;

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_quiz);

        progress = findViewById(R.id.progressText);
        question = findViewById(R.id.questionText);
        feedback = findViewById(R.id.feedbackText);

        answers[0] = findViewById(R.id.answerA);
        answers[1] = findViewById(R.id.answerB);
        answers[2] = findViewById(R.id.answerC);

        listen = findViewById(R.id.btnListen);
        nextQuestion = findViewById(R.id.btnNextQuestion);

        for (int i = 0; i < 3; i++) {
            final int choice = i;
            answers[i].setOnClickListener(v -> onAnswer(choice));
        }

        listen.setOnClickListener(v -> {
            if (current != null) {
                FongbeVoice.speak(this, current);
            }
        });

        nextQuestion.setOnClickListener(v -> {
            if (number >= TOTAL) {
                score = 0;
                number = 0;
            }
            showQuestion();
        });

        showQuestion();
    }

    private void showQuestion() {

        answered = false;
        number++;

        feedback.setText("");
        listen.setVisibility(View.GONE);
        nextQuestion.setVisibility(View.GONE);

        FongbeData.Entry[] words = FongbeData.WORDS;
        current = words[random.nextInt(words.length)];

        List<FongbeData.Entry> options = new ArrayList<>();
        options.add(current);

        while (options.size() < 3) {
            FongbeData.Entry other = words[random.nextInt(words.length)];
            boolean duplicate = false;
            for (FongbeData.Entry o : options) {
                if (o.fon.equals(other.fon)) {
                    duplicate = true;
                }
            }
            if (!duplicate) {
                options.add(other);
            }
        }

        Collections.shuffle(options, random);

        for (int i = 0; i < 3; i++) {
            answers[i].setEnabled(true);
            answers[i].setText(options.get(i).fon);
            if (options.get(i) == current) {
                correctIndex = i;
            }
        }

        progress.setText("Question " + number + " / " + TOTAL + "   -   Score : " + score);
        question.setText("Comment dit-on « " + current.fr + " » en fongbé ?");
    }

    private void onAnswer(int choice) {

        if (answered) {
            return;
        }
        answered = true;

        for (Button b : answers) {
            b.setEnabled(false);
        }

        if (choice == correctIndex) {
            score++;
            feedback.setText("Bonne réponse !\nTon score : " + score);
        } else {
            feedback.setText(
                "Mauvaise réponse.\nLa bonne réponse : " + current.fon
            );
        }

        progress.setText("Question " + number + " / " + TOTAL + "   -   Score : " + score);

        listen.setVisibility(View.VISIBLE);
        nextQuestion.setVisibility(View.VISIBLE);

        if (number >= TOTAL) {
            nextQuestion.setText("Recommencer");
            feedback.append("\n\nTerminé ! Score final : " + score + " / " + TOTAL);
        } else {
            nextQuestion.setText("Question suivante");
        }
    }

    @Override
    protected void onDestroy() {
        FongbeVoice.stopPlayer();
        super.onDestroy();
    }
}
