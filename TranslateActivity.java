package bj.fongbe.academie;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

public class TranslateActivity extends Activity {

    private boolean frToFon = true;
    private String lastFon = "";
    private String lastFr = "";

    private Button direction;
    private EditText input;
    private EditText tokenInput;
    private TextView result;
    private Button listen;
    private Button translate;

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_translate);

        direction = findViewById(R.id.btnDirection);
        input = findViewById(R.id.inputText);
        tokenInput = findViewById(R.id.tokenInput);
        result = findViewById(R.id.resultText);
        listen = findViewById(R.id.btnListenResult);
        translate = findViewById(R.id.btnTranslate);
        Button back = findViewById(R.id.btnBackTranslate);

        final SharedPreferences prefs = getSharedPreferences("fongbe", MODE_PRIVATE);
        tokenInput.setText(prefs.getString("hf_token", ""));

        direction.setOnClickListener(v -> {
            frToFon = !frToFon;
            direction.setText(frToFon
                ? "Sens : Français → Fongbé"
                : "Sens : Fongbé → Français");
            result.setText("");
            listen.setVisibility(View.GONE);
        });

        translate.setOnClickListener(v -> {

            final String text = input.getText().toString().trim();
            if (text.isEmpty()) {
                result.setText("Écris d'abord un texte.");
                return;
            }

            final String token = tokenInput.getText().toString().trim();
            prefs.edit().putString("hf_token", token).apply();

            result.setText("Traduction en cours…");
            listen.setVisibility(View.GONE);
            translate.setEnabled(false);

            final boolean direct = frToFon;

            new Thread(() -> {
                String out;
                try {
                    out = Translator.translate(text, direct, token);
                } catch (Exception e) {
                    out = null;
                }
                final String res = out;
                runOnUiThread(() -> {
                    translate.setEnabled(true);
                    if (res == null || res.trim().isEmpty()) {
                        result.setText(
                            "Traduction impossible (vérifie la connexion Internet).");
                        return;
                    }
                    result.setText(res);
                    if (direct) {
                        lastFr = text;
                        lastFon = res;
                        listen.setVisibility(View.VISIBLE);
                    }
                });
            }).start();
        });

        listen.setOnClickListener(v -> {
            if (!lastFon.isEmpty()) {
                FongbeVoice.speak(
                    this,
                    new FongbeData.Entry(lastFr, lastFon, "traduction_libre")
                );
            }
        });

        back.setOnClickListener(v -> finish());
    }

    @Override
    protected void onDestroy() {
        FongbeVoice.stopPlayer();
        super.onDestroy();
    }
}
