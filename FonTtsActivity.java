package bj.fongbe.academie;

import android.app.Activity;
import android.content.ContentValues;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.text.InputType;
import android.view.Gravity;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;

/** Écris une phrase en fongbé : écoute la voix (Meta MMS) et télécharge l'audio. */
public class FonTtsActivity extends Activity {

    private static final int BG = 0xFFFFF8E7;
    private static final int GREEN = 0xFF176B52;
    private static final int YELLOW = 0xFFFBE7A1;
    private static final int TEXT = 0xFF2B2B2B;
    private static final int GREY = 0xFF7A7A7A;

    private EditText input;
    private TextView status;
    private TextView listen;
    private TextView download;
    private EditText spaceInput;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(BG);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(20), dp(20), dp(20));
        scroll.addView(root);
        setContentView(scroll);

        TextView back = label("← Retour", 14, GREEN, true);
        back.setOnClickListener(v -> finish());
        root.addView(back);

        root.addView(label("Voix fongbé", 24, GREEN, true), lp(0, 8, 0, 4));
        root.addView(label("Écris une phrase en fongbé : l'outil produit une voix "
            + "(modèle Meta MMS). Tu peux l'écouter et la télécharger.", 13, GREY, false));

        input = new EditText(this);
        input.setHint("Écris ta phrase en fongbé ici");
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        input.setMinLines(3);
        input.setGravity(Gravity.TOP);
        root.addView(input, lp(0, 12, 0, 0));
        String pre = getIntent().getStringExtra("fon");
        if (pre != null) {
            input.setText(pre);
        }

        listen = button("🔊 Écouter", GREEN, 0xFFFFFFFF);
        listen.setOnClickListener(v -> run(false));
        root.addView(listen, lp(0, 10, 0, 0));

        download = button("💾 Télécharger l'audio", YELLOW, TEXT);
        download.setOnClickListener(v -> run(true));
        root.addView(download, lp(0, 10, 0, 0));

        status = label("", 13, TEXT, false);
        root.addView(status, lp(0, 12, 0, 0));

        root.addView(label("Voix synthétique : les tons peuvent être imprécis. "
            + "Licence du modèle : CC-BY-NC (usage non commercial).", 12, GREY, false),
            lp(0, 12, 0, 0));

        root.addView(label("Si la voix ne marche pas : colle ici l'adresse de ton Space "
            + "Hugging Face (voir hfspace/LISEZ-MOI.txt). Laisse vide pour l'adresse par défaut.",
            12, GREY, false), lp(0, 16, 0, 0));
        spaceInput = new EditText(this);
        spaceInput.setHint("https://tonpseudo-fon-tts.hf.space");
        spaceInput.setSingleLine(true);
        spaceInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        spaceInput.setText(getSharedPreferences("fongbe", MODE_PRIVATE).getString("space_url", ""));
        root.addView(spaceInput, lp(0, 4, 0, 0));
    }

    private void run(final boolean save) {
        final String text = input.getText().toString().trim();
        if (text.isEmpty()) {
            status.setText("Écris d'abord une phrase en fongbé.");
            return;
        }
        getSharedPreferences("fongbe", MODE_PRIVATE).edit()
            .putString("space_url", spaceInput.getText().toString().trim()).apply();
        status.setText("Génération de la voix…");
        listen.setEnabled(false);
        download.setEnabled(false);

        new Thread(() -> {
            String msg = null;
            File f = null;
            try {
                f = FongbeVoice.synthesize(this, text);
                if (save) {
                    msg = "Audio enregistré : " + saveToDownloads(f, text);
                }
            } catch (Exception e) {
                f = null;
                msg = "Voix indisponible. Détail : " + e.getMessage();
            }
            final File audio = f;
            final String fm = msg;
            runOnUiThread(() -> {
                listen.setEnabled(true);
                download.setEnabled(true);
                status.setText(fm == null ? "" : fm);
                if (audio != null && !save) {
                    FongbeVoice.playCached(this, audio);
                }
            });
        }).start();
    }

    /** Copie l'audio dans Téléchargements/FongbeAcademie (ou dossier de l'appli avant Android 10). */
    private String saveToDownloads(File src, String text) throws Exception {
        String name = "fongbe_" + FongbeVoice.slug(text) + src.getName()
            .substring(src.getName().lastIndexOf('.'));

        if (Build.VERSION.SDK_INT >= 29) {
            ContentValues v = new ContentValues();
            v.put(MediaStore.Downloads.DISPLAY_NAME, name);
            v.put(MediaStore.Downloads.MIME_TYPE, FongbeVoice.mimeFor(src));
            v.put(MediaStore.Downloads.RELATIVE_PATH,
                Environment.DIRECTORY_DOWNLOADS + "/FongbeAcademie");
            Uri uri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
            if (uri == null) {
                throw new Exception("insert");
            }
            OutputStream os = getContentResolver().openOutputStream(uri);
            copy(src, os);
            return "Téléchargements/FongbeAcademie/" + name;
        }

        File dir = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
        if (dir == null) {
            throw new Exception("stockage");
        }
        dir.mkdirs();
        File out = new File(dir, name);
        copy(src, new java.io.FileOutputStream(out));
        return out.getAbsolutePath();
    }

    private static void copy(File src, OutputStream os) throws Exception {
        InputStream in = new FileInputStream(src);
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) {
            os.write(buf, 0, n);
        }
        in.close();
        os.close();
    }

    // ----- petits outils d'interface -----

    private TextView label(String t, int sp, int color, boolean bold) {
        TextView tv = new TextView(this);
        tv.setText(t);
        tv.setTextSize(sp);
        tv.setTextColor(color);
        if (bold) {
            tv.setTypeface(tv.getTypeface(), android.graphics.Typeface.BOLD);
        }
        return tv;
    }

    private TextView button(String t, int bg, int fg) {
        TextView tv = label(t, 16, fg, true);
        tv.setGravity(Gravity.CENTER);
        tv.setPadding(dp(8), dp(14), dp(8), dp(14));
        android.graphics.drawable.GradientDrawable d = new android.graphics.drawable.GradientDrawable();
        d.setColor(bg);
        d.setCornerRadius(dp(14));
        tv.setBackground(d);
        return tv;
    }

    private LinearLayout.LayoutParams lp(int l, int t, int r, int b) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
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
