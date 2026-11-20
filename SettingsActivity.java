package bj.fongbe.academie;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONObject;

public class SettingsActivity extends Activity {

    private static final int REQ_IMPORT = 4521;

    private static final int BG = 0xFFFFF8E7;
    private static final int YELLOW = 0xFFFBE7A1;
    private static final int YELLOW_STROKE = 0xFFF3D36B;
    private static final int GREEN = 0xFF176B52;
    private static final int TEXT = 0xFF2B2B2B;
    private static final int GREY = 0xFF7A7A7A;
    private static final int WHITE = 0xFFFFFFFF;

    private LinearLayout root;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(BG);
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(16), dp(16), dp(16));
        scroll.addView(root);
        setContentView(scroll);

        build();
    }

    private void build() {
        root.removeAllViews();

        TextView back = text("← Retour", 14, GREEN, true);
        back.setPadding(0, 0, 0, dp(8));
        back.setOnClickListener(v -> finish());
        root.addView(back);

        root.addView(text("Paramètres", 24, TEXT, true), lp(-1, -2, 0, 0, 0, 12));

        // Compte
        JSONObject u = Store.currentUser(this);
        String who = "Invité";
        if (u != null) {
            who = (u.optString("prenom") + " " + u.optString("nom")).trim();
            if (u.optBoolean("admin")) {
                who += " (administrateur)";
            }
        }
        LinearLayout account = card();
        account.addView(text("Compte", 12, GREY, false));
        account.addView(text(who, 16, TEXT, true), lp(-1, -2, 0, 2, 0, 0));
        account.addView(text("Dossier : " + Store.dir(this).getAbsolutePath(), 11, GREY, false),
            lp(-1, -2, 0, 6, 0, 0));
        root.addView(account, lp(-1, -2, 0, 0, 0, 12));

        // Contenu
        LinearLayout content = card();
        content.addView(text("Contenu", 12, GREY, false));
        content.addView(text(FongbeData.WORDS.length + " mots · "
            + FongbeData.LESSONS.length + " leçons", 16, TEXT, true), lp(-1, -2, 0, 2, 0, 0));
        content.addView(text("Un fichier .mkou ajoute des mots, des leçons et des "
            + "prononciations. Le contenu importé est fusionné avec l'existant.",
            12, GREY, false), lp(-1, -2, 0, 6, 0, 10));

        TextView imp = text("📥 Importer un fichier .mkou", 15, TEXT, true);
        imp.setGravity(Gravity.CENTER);
        imp.setPadding(dp(8), dp(14), dp(8), dp(14));
        imp.setBackground(bg(YELLOW, 14, 0));
        imp.setOnClickListener(v -> {
            Intent it = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            it.addCategory(Intent.CATEGORY_OPENABLE);
            it.setType("*/*");
            startActivityForResult(it, REQ_IMPORT);
        });
        content.addView(imp);
        root.addView(content, lp(-1, -2, 0, 0, 0, 12));

        // Déconnexion
        TextView out = text("🚪 Se déconnecter", 15, WHITE, true);
        out.setGravity(Gravity.CENTER);
        out.setPadding(dp(8), dp(14), dp(8), dp(14));
        out.setBackground(bg(GREEN, 16, 0));
        out.setOnClickListener(v -> {
            Store.logout(this);
            Intent it = new Intent(this, LoginActivity.class);
            it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(it);
        });
        root.addView(out);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_IMPORT || resultCode != RESULT_OK || data == null
            || data.getData() == null) {
            return;
        }
        final Uri uri = data.getData();
        new Thread(() -> {
            String msg;
            try {
                msg = Store.importMkou(this, uri);
            } catch (Exception e) {
                msg = "Import impossible : "
                    + (e.getMessage() == null ? "fichier invalide." : e.getMessage());
            }
            final String result = msg;
            runOnUiThread(() -> {
                new AlertDialog.Builder(this)
                    .setTitle("Fichier .mkou")
                    .setMessage(result)
                    .setPositiveButton("OK", null)
                    .show();
                build();
            });
        }).start();
    }

    // ----- Outils -----

    private LinearLayout card() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(16), dp(16), dp(16), dp(16));
        c.setBackground(bg(WHITE, 16, YELLOW_STROKE));
        return c;
    }

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

    private TextView text(String s, int sp, int color, boolean bold) {
        TextView tv = new TextView(this);
        tv.setText(s);
        tv.setTextSize(sp);
        tv.setTextColor(color);
        if (bold) {
            tv.setTypeface(Typeface.DEFAULT_BOLD);
        }
        return tv;
    }

    private LinearLayout.LayoutParams lp(int w, int h, int l, int t, int r, int b) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(w, h);
        p.setMargins(dp(l), dp(t), dp(r), dp(b));
        return p;
    }
}
