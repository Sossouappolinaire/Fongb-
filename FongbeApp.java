package bj.fongbe.academie;

import android.app.Application;

public class FongbeApp extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        // Fusionne le contenu de base avec les fichiers .mkou déjà importés.
        FongbeData.reload(this);
        // Verrouillage : PIN (ou empreinte) demandé après 3 minutes hors de l'appli.
        registerActivityLifecycleCallbacks(new AppLock());
    }
}
