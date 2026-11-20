package bj.fongbe.academie;

import android.app.Activity;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.text.Normalizer;
import java.util.Locale;

public class DictionaryActivity extends Activity {

    private LinearLayout container;

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dictionary);

        EditText input = findViewById(R.id.searchInput);
        container = findViewById(R.id.resultsContainer);

        TextView credit = findViewById(R.id.creditText);
        credit.setText(FongbeData.CREDIT);

        input.addTextChangedListener(new TextWatcher() {

            @Override
            public void beforeTextChanged(
                CharSequence s, int start, int count, int after
            ) {}

            @Override
            public void onTextChanged(
                CharSequence s, int start, int before, int count
            ) {
                search(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {}

        });

        search("");
    }

    private static String norm(String s) {
        return Normalizer.normalize(s, Normalizer.Form.NFD)
            .replaceAll("\\p{M}", "")
            .toLowerCase(Locale.ROOT)
            .trim();
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }

    private void search(String query) {

        container.removeAllViews();

        String q = norm(query);
        int count = 0;

        for (final FongbeData.Entry e : FongbeData.WORDS) {

            if (q.isEmpty()
                    || norm(e.fr).contains(q)
                    || norm(e.fon).contains(q)) {

                addRow(e);
                count++;
            }
        }

        if (count == 0) {
            TextView none = new TextView(this);
            none.setTextSize(18);
            none.setText("Aucun résultat trouvé.");
            container.addView(none);
        }
    }

    private void addRow(final FongbeData.Entry e) {

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(4), 0, dp(4));

        TextView t = new TextView(this);
        t.setTextSize(18);
        t.setLayoutParams(new LinearLayout.LayoutParams(
            0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        t.setText("Français : " + e.fr + "\nFongbé : " + e.fon);

        Button b = new Button(this);
        b.setText("🔊");
        b.setOnClickListener((View v) -> FongbeVoice.speak(this, e));

        row.addView(t);
        row.addView(b);
        container.addView(row);
    }

    @Override
    protected void onDestroy() {
        FongbeVoice.stopPlayer();
        super.onDestroy();
    }
}
