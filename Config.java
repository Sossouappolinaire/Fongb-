package bj.fongbe.academie;

/**
 * Réglages de la voix fongbé en ligne (facultatif).
 * Priorité de lecture : 1) enregistrement .mp3/.ogg/.wav du projet,
 * 2) audio déjà téléchargé (cache), 3) voix en ligne ci-dessous.
 *
 * La voix en ligne utilise le modèle Meta MMS-TTS « fon » hébergé dans un
 * Space Hugging Face public (aucune clé). Non testé : si le Space est
 * arrêté ou change d'API, mets ONLINE_ENABLED à false.
 */
public class Config {

    public static final boolean ONLINE_ENABLED = true;

    // Adresse du Space (sans « / » final)
    public static final String SPACE_URL = "https://bonadossou-fontts.hf.space";

    // Nom de l'API Gradio du Space
    public static final String API_NAME = "predict";
}
