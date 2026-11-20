import gradio as gr
import numpy as np
import torch
from transformers import AutoTokenizer, VitsModel

MODEL_ID = "facebook/mms-tts-fon"
tokenizer = AutoTokenizer.from_pretrained(MODEL_ID)
model = VitsModel.from_pretrained(MODEL_ID)
model.eval()


def tts(text):
    text = " ".join((text or "").lower().split())
    if not text:
        raise gr.Error("Texte vide")
    inputs = tokenizer(text, return_tensors="pt")
    with torch.no_grad():
        wave = model(**inputs).waveform[0].cpu().numpy()
    return model.config.sampling_rate, (np.clip(wave, -1, 1) * 32767).astype(np.int16)


demo = gr.Interface(
    fn=tts,
    inputs=gr.Textbox(label="Texte en fongbé"),
    outputs=gr.Audio(label="Voix", type="numpy"),
    api_name="predict",
    title="Fongbé TTS (Meta MMS)",
)
demo.launch()
