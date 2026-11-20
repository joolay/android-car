"""Generate natural-sounding (neural) recordings of the built-in travel prayers.
Run by the Voices workflow; writes app/src/main/assets/voice/<id>_orig.mp3 and <id>_en.mp3."""
import asyncio, json, os, sys
import edge_tts

OUT = "app/src/main/assets/voice"
VOICES = {"ar": "ar-SA-HamedNeural", "he": "he-IL-AvriNeural", "en": "en-ZA-LukeNeural"}

def lang(text):
    for ch in text:
        o = ord(ch)
        if 0x0600 <= o <= 0x06FF: return "ar"
        if 0x0590 <= o <= 0x05FF: return "he"
    return None

async def say(text, voice, path, rate):
    await edge_tts.Communicate(text, voice, rate=rate).save(path)
    print("wrote", path, os.path.getsize(path), "bytes")

async def main():
    os.makedirs(OUT, exist_ok=True)
    for p in json.load(open("tools/prayers.json", encoding="utf-8")):
        if p.get("orig"):
            l = lang(p["orig"])
            if l: await say(p["orig"], VOICES[l], f"{OUT}/{p['id']}_orig.mp3", "-12%")  # a little slower for recitation
        await say(p["en"], VOICES["en"], f"{OUT}/{p['id']}_en.mp3", "-5%")
    # salaah time announcements
    for name in ["Fajr", "Dhuhr", "Asr", "Maghrib", "Isha"]:
        await say(f"It's time for Salaah. {name}.", VOICES["en"], f"{OUT}/salah_{name.lower()}.mp3", "-5%")

asyncio.run(main())
