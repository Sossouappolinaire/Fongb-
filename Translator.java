package bj.fongbe.academie;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;

/**
 * Traduction automatique français <-> fongbé.
 * 1) Si un jeton Hugging Face gratuit est saisi : modèle NLLB-200 (fon_Latn).
 * 2) Sinon, ou si NLLB échoue : service Google Translate public (code « fon »).
 * Toujours bloquant : à appeler hors du thread principal.
 */
public class Translator {

    private static final String NLLB_URL =
        "https://api-inference.huggingface.co/models/facebook/nllb-200-distilled-600M";

    public static String translate(String text, boolean frToFon, String hfToken)
            throws Exception {

        Exception last = null;

        if (hfToken != null && hfToken.trim().length() > 0) {
            try {
                return viaNllb(text, frToFon, hfToken.trim());
            } catch (Exception e) {
                last = e;
            }
        }

        try {
            return viaGoogle(text, frToFon);
        } catch (Exception e) {
            if (last != null) {
                throw last;
            }
            throw e;
        }
    }

    private static String viaNllb(String text, boolean frToFon, String token)
            throws Exception {

        HttpURLConnection c = (HttpURLConnection) new URL(NLLB_URL).openConnection();
        c.setRequestMethod("POST");
        c.setDoOutput(true);
        c.setConnectTimeout(20000);
        c.setReadTimeout(90000);
        c.setRequestProperty("Authorization", "Bearer " + token);
        c.setRequestProperty("Content-Type", "application/json");

        JSONObject params = new JSONObject();
        params.put("src_lang", frToFon ? "fra_Latn" : "fon_Latn");
        params.put("tgt_lang", frToFon ? "fon_Latn" : "fra_Latn");

        JSONObject body = new JSONObject();
        body.put("inputs", text);
        body.put("parameters", params);
        body.put("options", new JSONObject().put("wait_for_model", true));

        OutputStream os = c.getOutputStream();
        os.write(body.toString().getBytes("UTF-8"));
        os.close();

        if (c.getResponseCode() != 200) {
            throw new Exception("NLLB : erreur " + c.getResponseCode());
        }

        String resp = readAll(c.getInputStream());
        JSONArray arr = new JSONArray(resp);
        return arr.getJSONObject(0).getString("translation_text");
    }

    private static String viaGoogle(String text, boolean frToFon) throws Exception {

        String url = "https://translate.googleapis.com/translate_a/single"
            + "?client=gtx&dt=t"
            + "&sl=" + (frToFon ? "fr" : "fon")
            + "&tl=" + (frToFon ? "fon" : "fr")
            + "&q=" + URLEncoder.encode(text, "UTF-8");

        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(20000);
        c.setReadTimeout(30000);
        c.setRequestProperty("User-Agent", "Mozilla/5.0");

        if (c.getResponseCode() != 200) {
            throw new Exception("Google : erreur " + c.getResponseCode());
        }

        JSONArray root = new JSONArray(readAll(c.getInputStream()));
        JSONArray parts = root.getJSONArray(0);

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.length(); i++) {
            JSONArray p = parts.optJSONArray(i);
            if (p != null && !p.isNull(0)) {
                sb.append(p.getString(0));
            }
        }
        return sb.toString();
    }

    private static String readAll(InputStream in) throws Exception {
        BufferedReader r = new BufferedReader(new InputStreamReader(in, "UTF-8"));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = r.readLine()) != null) {
            sb.append(line).append('\n');
        }
        r.close();
        return sb.toString();
    }
}
