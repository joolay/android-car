package com.carbox.launcher;

/** Travel prayers offered for the splash screen. */
final class Prayers {
    private Prayers() {
    }

    static final class P {
        final String id, faith, name, orig, translit, english, source;
        final boolean rtl;

        P(String id, String faith, String name, boolean rtl, String orig, String translit, String english, String source) {
            this.id = id;
            this.faith = faith;
            this.name = name;
            this.rtl = rtl;
            this.orig = orig;
            this.translit = translit;
            this.english = english;
            this.source = source;
        }
    }

    static final String[] FAITHS = {"Islam", "Christianity", "Judaism"};

    static final P[] ALL = {
            new P("islam1", "Islam", "Dua for travelling", true,
                    "سُبْحَانَ الَّذِي سَخَّرَ لَنَا هَٰذَا وَمَا كُنَّا لَهُ مُقْرِنِينَ وَإِنَّا إِلَىٰ رَبِّنَا لَمُنقَلِبُونَ",
                    "Subhanal-ladhi sakh-khara lana hadha wa ma kunna lahu muqrinin, wa inna ila Rabbina lamunqalibun",
                    "Glory be to Him who has placed this at our service, for we could never have done it ourselves. And surely to our Lord we shall return.",
                    "Qur'an 43:13–14"),
            new P("islam2", "Islam", "Dua at the start of a journey", true,
                    "اللَّهُمَّ إِنَّا نَسْأَلُكَ فِي سَفَرِنَا هَٰذَا الْبِرَّ وَالتَّقْوَىٰ، وَمِنَ الْعَمَلِ مَا تَرْضَىٰ",
                    "Allahumma inna nas'aluka fi safarina hadha al-birra wat-taqwa, wa minal-'amali ma tarda",
                    "O Allah, we ask You on this journey of ours for righteousness and piety, and for deeds that please You.",
                    "Sahih Muslim"),
            new P("islam3", "Islam", "Bismillah", true,
                    "بِسْمِ اللَّهِ الرَّحْمَٰنِ الرَّحِيمِ",
                    "Bismillahir-Rahmanir-Rahim",
                    "In the name of God, the Most Gracious, the Most Merciful.",
                    "Qur'an 1:1"),
            new P("christ1", "Christianity", "Psalm 121:8", false, null, null,
                    "The LORD shall preserve thy going out and thy coming in from this time forth, and even for evermore.",
                    "Psalm 121:8 · King James Version"),
            new P("christ2", "Christianity", "Psalm 91:11", false, null, null,
                    "For he shall give his angels charge over thee, to keep thee in all thy ways.",
                    "Psalm 91:11 · King James Version"),
            new P("jud1", "Judaism", "Tefilat HaDerech", true,
                    "יְהִי רָצוֹן מִלְּפָנֶיךָ ה׳ אֱלֹהֵינוּ וֵאלֹהֵי אֲבוֹתֵינוּ, שֶׁתּוֹלִיכֵנוּ לְשָׁלוֹם וְתַצְעִידֵנוּ לְשָׁלוֹם",
                    "Yehi ratzon milfanecha Adonai Eloheinu velohei avoteinu, shetolicheinu leshalom vetatz'ideinu leshalom",
                    "May it be Your will, Lord our God and God of our ancestors, that You lead us toward peace and guide our footsteps toward peace.",
                    "Traveller's Prayer (opening)")};

    static P get(String id) {
        if (id == null) return null;
        if ("own".equals(id)) {
            String t = Prefs.str(Prefs.CUSTOM_TEXT, "");
            if (t.isEmpty()) return null;
            return new P("own", "My own", "My own prayer", false, null, null, t, Prefs.str(Prefs.CUSTOM_SUB, ""));
        }
        for (P p : ALL) if (p.id.equals(id)) return p;
        return null;
    }
}
