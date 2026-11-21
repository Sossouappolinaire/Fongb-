package bj.fongbe.academie;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.hardware.biometrics.BiometricPrompt;
import android.os.Build;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** Écran de verrouillage : code PIN du compte, ou empreinte digitale si le téléphone l'offre. */
public class LockActivity extends Activity {

    private static final int BG = 0xFFFFF8E7;
    private static final int YELLOW_STROKE = 0xFFF3D36B;
    private static final int GREEN = 0xFF176B52;
    private static final int TEXT = 0xFF2B2B2B;
    private static final int GREY = 0xFF7A7A7A;
    private static final int WHITE = 0xFFFFFFFF;
    private static final int RED = 0xFFB3261E;

    private EditText pin;
    private TextView message, unlockBtn;
    private CancellationSignal cancel;
    private int failures = 0;
    private boolean waiting = false;
    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Pas de capture d'écran ni d'aperçu dans la liste des applis récentes
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE);

        if (!AppLock.needsLock(this)) {
            finish();
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

        TextView logo = text("🔒", 48, TEXT, false);
        logo.setGravity(Gravity.CENTER);
        root.addView(logo, lp(-1, -2, 0, 0, 0, 4));

        TextView app = text("Fongbé Académie", 24, GREEN, true);
        app.setGravity(Gravity.CENTER);
        root.addView(app, lp(-1, -2, 0, 0, 0, 6));

        TextView sub = text("Entre ton code PIN pour continuer.", 14, GREY, false);
        sub.setGravity(Gravity.CENTER);
        root.addView(sub, lp(-1, -2, 0, 0, 0, 18));

        pin = new EditText(this);
        pin.setHint("Code PIN");
        pin.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        pin.setSingleLine(true);
        pin.setGravity(Gravity.CENTER);
        pin.setTextSize(22);
        pin.setTextColor(TEXT);
        pin.setHintTextColor(0xFF9A9A9A);
        pin.setPadding(dp(14), dp(14), dp(14), dp(14));
        pin.setBackground(bg(WHITE, 14, YELLOW_STROKE));
        root.addView(pin, lp(-1, -2, 0, 0, 0, 10));

        message = text("", 13, RED, false);
        message.setGravity(Gravity.CENTER);
        root.addView(message, lp(-1, -2, 0, 0, 0, 10));

        unlockBtn = button("Déverrouiller", WHITE, GREEN);
        unlockBtn.setOnClickListener(v -> checkPin());
        root.addView(unlockBtn, lp(-1, -2, 0, 0, 0, 10));

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            TextView fp = button("👆 Utiliser l'empreinte", GREEN, 0xFFFBE7A1);
            fp.setOnClickListener(v -> askBiometric());
            root.addView(fp, lp(-1, -2, 0, 0, 0, 10));
        }

        TextView other = text("Changer de compte / PIN oublié", 13, GREEN, true);
        other.setGravity(Gravity.CENTER);
        other.setPadding(dp(8), dp(12), dp(8), dp(12));
        other.setOnClickListener(v -> {
            Store.logout(this); // efface aussi le verrou
            Intent it = new Intent(this, LoginActivity.class);
            it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(it);
            finish();
        });
        root.addView(other);

        setContentView(scroll);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            askBiometric(); // propose l'empreinte tout de suite ; on peut l'annuler et taper le PIN
        }
    }

    private void checkPin() {
        if (waiting) {
            return;
        }
        String p = pin.getText().toString().trim();
        if (Store.verifyPin(this, p)) {
            unlock();
            return;
        }
        failures++;
        pin.setText("");
        if (failures >= 5) {
            waiting = true;
            unlockBtn.setAlpha(0.5f);
            countdown(30);
        } else {
            message.setText("Code PIN incorrect (" + failures + "/5).");
        }
    }

    private void countdown(final int left) {
        if (left <= 0) {
            waiting = false;
            failures = 0;
            unlockBtn.setAlpha(1f);
            message.setText("");
            return;
        }
        message.setText("Trop d'essais. Réessaie dans " + left + " s.");
        handler.postDelayed(() -> countdown(left - 1), 1000);
    }

    private void unlock() {
        AppLock.unlocked(this);
        finish();
    }

    @TargetApi(Build.VERSION_CODES.P)
    private void askBiometric() {
        try {
            if (cancel != null) {
                cancel.cancel();
            }
            cancel = new CancellationSignal();
            BiometricPrompt.Builder b = new BiometricPrompt.Builder(this)
                .setTitle("Déverrouiller Fongbé Académie")
                .setSubtitle("Pose ton doigt sur le capteur");
            b.setNegativeButton("Utiliser le PIN", getMainExecutor(), (d, w) -> { });
            b.build().authenticate(cancel, getMainExecutor(),
                new BiometricPrompt.AuthenticationCallback() {
                    @Override
                    public void onAuthenticationSucceeded(BiometricPrompt.AuthenticationResult r) {
                        unlock();
                    }

                    @Override
                    public void onAuthenticationError(int code, CharSequence msg) {
                        // Annulé ou indisponible (aucune empreinte enregistrée…) : le PIN reste là.
                    }
                });
        } catch (Exception ignored) {
            // pas d'empreinte sur ce téléphone : le PIN suffit
        }
    }

    @Override
    public void onBackPressed() {
        // Retour = quitter l'appli, jamais révéler l'écran qui est dessous
        moveTaskToBack(true);
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        if (cancel != null) {
            cancel.cancel();
        }
        super.onDestroy();
    }

    // ----- Outils -----

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

    private TextView button(String label, int fg, int bgColor) {
        TextView b = text(label, 15, fg, true);
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(8), dp(14), dp(8), dp(14));
        b.setBackground(bg(bgColor, 16, 0));
        return b;
    }

    private LinearLayout.LayoutParams lp(int w, int h, int l, int t, int r, int b) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(w, h);
        p.setMargins(dp(l), dp(t), dp(r), dp(b));
        return p;
    }
}
