package bj.fongbe.academie;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;

/**
 * Verrouillage de l'appli : la session reste ouverte (on n'a pas à se reconnecter), mais si
 * l'appli a été quittée pendant 3 minutes ou plus, l'écran de verrouillage demande le code PIN
 * du compte (ou l'empreinte). Le verrou est mémorisé sur le téléphone : même si Android ferme
 * l'appli en arrière-plan, elle reste verrouillée tant que le PIN n'a pas été saisi.
 */
public class AppLock implements Application.ActivityLifecycleCallbacks {

    /** Durée hors de l'appli avant de redemander le PIN. */
    public static final long DELAY_MS = 3 * 60 * 1000L;

    private int started = 0;

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("fongbe_lock", Context.MODE_PRIVATE);
    }

    /** Vrai si la session est ouverte, que le compte a un PIN, et que le verrou est actif. */
    public static boolean needsLock(Context c) {
        if (!Store.isLoggedIn(c) || !Store.hasPin(c)) {
            return false;
        }
        SharedPreferences p = prefs(c);
        if (p.getBoolean("locked", false)) {
            return true;
        }
        long left = p.getLong("left", 0);
        if (left == 0) {
            return false;
        }
        long away = System.currentTimeMillis() - left;
        return away >= DELAY_MS || away < 0; // away < 0 : l'heure du téléphone a été changée
    }

    static void markLocked(Context c) {
        prefs(c).edit().putBoolean("locked", true).apply();
    }

    static void markLeft(Context c) {
        prefs(c).edit().putLong("left", System.currentTimeMillis()).apply();
    }

    /** PIN correct, ou déconnexion : on efface le verrou. */
    public static void unlocked(Context c) {
        prefs(c).edit().putBoolean("locked", false).remove("left").apply();
    }

    // ----- Suivi des écrans : on sait quand l'appli passe en arrière-plan / revient -----

    @Override
    public void onActivityStarted(Activity a) {
        if (started == 0 && !(a instanceof LockActivity) && needsLock(a)) {
            markLocked(a);
            Intent it = new Intent(a, LockActivity.class);
            it.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
            a.startActivity(it);
        }
        started++;
    }

    @Override
    public void onActivityStopped(Activity a) {
        started = Math.max(0, started - 1);
        if (started == 0 && !a.isChangingConfigurations()) {
            markLeft(a);
        }
    }

    @Override public void onActivityCreated(Activity a, Bundle b) {}
    @Override public void onActivityResumed(Activity a) {}
    @Override public void onActivityPaused(Activity a) {}
    @Override public void onActivitySaveInstanceState(Activity a, Bundle b) {}
    @Override public void onActivityDestroyed(Activity a) {}
}
