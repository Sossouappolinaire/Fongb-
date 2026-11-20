package bj.fongbe.academie;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public class LoginActivity extends Activity {

    private static final int BG = 0xFFFFF8E7;
    private static final int YELLOW_STROKE = 0xFFF3D36B;
    private static final int GREEN = 0xFF176B52;
    private static final int TEXT = 0xFF2B2B2B;
    private static final int GREY = 0xFF7A7A7A;
    private static final int WHITE = 0xFFFFFFFF;

    private boolean registerMode = false;

    private LinearLayout form;
    private TextView title, submit, toggle, error;
    private EditText nom, prenom, email, numero, pin, ident;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (Store.isLoggedIn(this)) {
            goHome();
            return;
        }

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(BG);
        scroll.setFillViewport(true);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_VERTICAL);
        root.setPadding(dp(24), dp(24), dp(24), dp(24));
        scroll.addView(root);

        TextView logo = text("🇧🇯", 48, TEXT, false);
        logo.setGravity(Gravity.CENTER);
        root.addView(logo, lp(-1, -2, 0, 0, 0, 4));

        TextView app = text("Fongbé Académie", 26, GREEN, true);
        app.setGravity(Gravity.CENTER);
        root.addView(app, lp(-1, -2, 0, 0, 0, 20));

        title = text("", 20, TEXT, true);
        root.addView(title, lp(-1, -2, 0, 0, 0, 12));

        form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        root.addView(form, lp(-1, -2, 0, 0, 0, 0));

        nom = field("Nom", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        prenom = field("Prénom", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        email = field("E-mail (facultatif)",
            InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        numero = field("Numéro de téléphone", InputType.TYPE_CLASS_PHONE);
        ident = field("Numéro ou e-mail",
            InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        pin = field("Code PIN (4 à 6 chiffres)",
            InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);

        TextWatcher watcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(Editable s) { updatePinVisibility(); }
        };
        ident.addTextChangedListener(watcher);
        email.addTextChangedListener(watcher);

        error = text("", 13, 0xFFC0392B, false);
        root.addView(error, lp(-1, -2, 0, 8, 0, 0));

        submit = text("", 16, WHITE, true);
        submit.setGravity(Gravity.CENTER);
        submit.setPadding(dp(8), dp(14), dp(8), dp(14));
        submit.setBackground(bg(GREEN, 16, 0));
        submit.setOnClickListener(v -> submit());
        root.addView(submit, lp(-1, -2, 0, 12, 0, 0));

        toggle = text("", 14, GREEN, true);
        toggle.setGravity(Gravity.CENTER);
        toggle.setPadding(dp(8), dp(14), dp(8), dp(14));
        toggle.setOnClickListener(v -> {
            registerMode = !registerMode;
            render();
        });
        root.addView(toggle, lp(-1, -2, 0, 4, 0, 0));

        TextView note = text("Tes données restent sur ce téléphone, dans le dossier "
            + Store.FOLDER + ".", 12, GREY, false);
        note.setGravity(Gravity.CENTER);
        root.addView(note, lp(-1, -2, 0, 12, 0, 0));

        setContentView(scroll);
        render();
    }

    private void render() {
        form.removeAllViews();
        error.setText("");
        if (registerMode) {
            title.setText("Créer un compte");
            addField(nom);
            addField(prenom);
            addField(email);
            addField(numero);
            addField(pin);
            submit.setText("S'inscrire");
            toggle.setText("Déjà un compte ? Se connecter");
        } else {
            title.setText("Connexion");
            addField(ident);
            addField(pin);
            submit.setText("Se connecter");
            toggle.setText("Pas de compte ? S'inscrire");
        }
        updatePinVisibility();
    }

    /** Pas de PIN à saisir pour l'e-mail administrateur. */
    private void updatePinVisibility() {
        EditText src = registerMode ? email : ident;
        boolean admin = Store.isAdminEmail(src.getText().toString());
        pin.setVisibility(admin ? View.GONE : View.VISIBLE);
    }

    private void submit() {
        String err;
        if (registerMode) {
            err = Store.register(this, t(nom), t(prenom), t(email), t(numero), t(pin));
        } else {
            err = Store.login(this, t(ident), t(pin));
        }
        if (err == null) {
            goHome();
        } else {
            error.setText(err);
        }
    }

    private void goHome() {
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }

    // ----- Outils -----

    private String t(EditText e) {
        return e.getText().toString();
    }

    private void addField(EditText e) {
        form.addView(e, lp(-1, -2, 0, 0, 0, 10));
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

    private EditText field(String hint, int type) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setInputType(type);
        e.setSingleLine(true);
        e.setTextColor(TEXT);
        e.setHintTextColor(0xFF9A9A9A);
        e.setPadding(dp(14), dp(14), dp(14), dp(14));
        e.setBackground(bg(WHITE, 14, YELLOW_STROKE));
        return e;
    }

    private LinearLayout.LayoutParams lp(int w, int h, int l, int t, int r, int b) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(w, h);
        p.setMargins(dp(l), dp(t), dp(r), dp(b));
        return p;
    }
}
