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
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

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
    private final TextView[] tabs = new TextView[3];
    private int tab = 0;

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
        String[] names = {"📖 Versets", "🙏 Mots bibliques", "📚 Mots du jour"};
        for (int i = 0; i < 3; i++) {
            final int idx = i;
            tabs[i] = tv(names[i], 12, TEXT, true);
            tabs[i].setGravity(Gravity.CENTER);
            tabs[i].setPadding(dp(4), dp(10), dp(4), dp(10));
            tabs[i].setOnClickListener(v -> show(idx));
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, -2, 1f);
            p.setMargins(dp(2), 0, dp(2), 0);
            bar.addView(tabs[i], p);
        }
        root.addView(bar, lp(-1, -2, 0, 0, 0, 8));

        ScrollView scroll = new ScrollView(this);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(list);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));

        show(0);
    }

    private BibleData.Item[] current() {
        if (tab == 0) {
            return BibleData.VERSES;
        }
        return tab == 1 ? BibleData.BIBLE_WORDS : BibleData.DAILY_WORDS;
    }

    private void show(int t) {
        tab = t;
        for (int i = 0; i < 3; i++) {
            tabs[i].setBackground(bg(i == t ? YELLOW : WHITE, 12, YELLOW_STROKE));
        }
        list.removeAllViews();
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
        FongbeVoice.stopPlayer();
        super.onDestroy();
    }
}
