package bj.fongbe.academie;

/**
 * Réglages de la voix fongbé (modèle Meta MMS-TTS « fon »).
 *
 * Ordre de lecture : 1) prononciation importée, 2) enregistrement .mp3/.ogg/.wav
 * du projet (res/raw), 3) audio déjà en cache, puis voix générée par :
 *   a) l'API Hugging Face avec ton jeton gratuit (saisi dans « Traducteur »),
 *   b) le Space public (SPACE_URL), en dernier recours.
 *
 * Licence du modèle facebook/mms-tts-fon : CC-BY-NC 4.0 (usage non commercial).
 */
public class Config {

    public static final boolean ONLINE_ENABLED = true;

    // a) Modèle Meta MMS sur Hugging Face (utilisé seulement si un jeton hf_... est saisi)
    public static final String HF_MODEL_URL =
        "https://api-inference.huggingface.co/models/facebook/mms-tts-fon";

    // b) Space Hugging Face public (sans « / » final). Non testé : peut être arrêté.
    public static final String SPACE_URL = "https://bonadossou-fontts.hf.space";

    // Nom de l'API Gradio du Space
    public static final String API_NAME = "predict";
}
