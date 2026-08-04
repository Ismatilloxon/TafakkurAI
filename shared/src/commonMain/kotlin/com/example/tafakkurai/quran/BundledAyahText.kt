package com.example.tafakkurai.quran

/**
 * Verified offline excerpt of the mushaf.
 *
 * The full Qurʼan is ~6,236 ayahs and is not bundled with the app. Rather than fill
 * the reader with invented Arabic, only text that has been checked is stored here;
 * everything else comes from [RemoteQuranRepository], and if that is unreachable the
 * reader shows an explicit "text not downloaded" state. Scripture is never guessed.
 *
 * Covers Al-Fatihah, Ayat al-Kursi, and the short surahs used by the Adhkar lists.
 */
internal object BundledAyahText {

    /** surahNumber → (ayahNumber → arabic). */
    private val arabic: Map<Int, Map<Int, String>> = mapOf(
        1 to mapOf(
            1 to "بِسْمِ ٱللَّهِ ٱلرَّحْمَٰنِ ٱلرَّحِيمِ",
            2 to "ٱلْحَمْدُ لِلَّهِ رَبِّ ٱلْعَٰلَمِينَ",
            3 to "ٱلرَّحْمَٰنِ ٱلرَّحِيمِ",
            4 to "مَٰلِكِ يَوْمِ ٱلدِّينِ",
            5 to "إِيَّاكَ نَعْبُدُ وَإِيَّاكَ نَسْتَعِينُ",
            6 to "ٱهْدِنَا ٱلصِّرَٰطَ ٱلْمُسْتَقِيمَ",
            7 to "صِرَٰطَ ٱلَّذِينَ أَنْعَمْتَ عَلَيْهِمْ غَيْرِ ٱلْمَغْضُوبِ عَلَيْهِمْ وَلَا ٱلضَّآلِّينَ",
        ),
        2 to mapOf(
            255 to "ٱللَّهُ لَآ إِلَٰهَ إِلَّا هُوَ ٱلْحَىُّ ٱلْقَيُّومُ ۚ لَا تَأْخُذُهُۥ سِنَةٌ وَلَا نَوْمٌ ۚ " +
                "لَّهُۥ مَا فِى ٱلسَّمَٰوَٰتِ وَمَا فِى ٱلْأَرْضِ ۗ مَن ذَا ٱلَّذِى يَشْفَعُ عِندَهُۥٓ إِلَّا بِإِذْنِهِۦ ۚ " +
                "يَعْلَمُ مَا بَيْنَ أَيْدِيهِمْ وَمَا خَلْفَهُمْ ۖ وَلَا يُحِيطُونَ بِشَىْءٍ مِّنْ عِلْمِهِۦٓ إِلَّا بِمَا شَآءَ ۚ " +
                "وَسِعَ كُرْسِيُّهُ ٱلسَّمَٰوَٰتِ وَٱلْأَرْضَ ۖ وَلَا يَـُٔودُهُۥ حِفْظُهُمَا ۚ وَهُوَ ٱلْعَلِىُّ ٱلْعَظِيمُ",
        ),
        103 to mapOf(
            1 to "وَٱلْعَصْرِ",
            2 to "إِنَّ ٱلْإِنسَٰنَ لَفِى خُسْرٍ",
            3 to "إِلَّا ٱلَّذِينَ ءَامَنُوا۟ وَعَمِلُوا۟ ٱلصَّٰلِحَٰتِ وَتَوَاصَوْا۟ بِٱلْحَقِّ وَتَوَاصَوْا۟ بِٱلصَّبْرِ",
        ),
        108 to mapOf(
            1 to "إِنَّآ أَعْطَيْنَٰكَ ٱلْكَوْثَرَ",
            2 to "فَصَلِّ لِرَبِّكَ وَٱنْحَرْ",
            3 to "إِنَّ شَانِئَكَ هُوَ ٱلْأَبْتَرُ",
        ),
        109 to mapOf(
            1 to "قُلْ يَٰٓأَيُّهَا ٱلْكَٰفِرُونَ",
            2 to "لَآ أَعْبُدُ مَا تَعْبُدُونَ",
            3 to "وَلَآ أَنتُمْ عَٰبِدُونَ مَآ أَعْبُدُ",
            4 to "وَلَآ أَنَا۠ عَابِدٌ مَّا عَبَدتُّمْ",
            5 to "وَلَآ أَنتُمْ عَٰبِدُونَ مَآ أَعْبُدُ",
            6 to "لَكُمْ دِينُكُمْ وَلِىَ دِينِ",
        ),
        110 to mapOf(
            1 to "إِذَا جَآءَ نَصْرُ ٱللَّهِ وَٱلْفَتْحُ",
            2 to "وَرَأَيْتَ ٱلنَّاسَ يَدْخُلُونَ فِى دِينِ ٱللَّهِ أَفْوَاجًا",
            3 to "فَسَبِّحْ بِحَمْدِ رَبِّكَ وَٱسْتَغْفِرْهُ ۚ إِنَّهُۥ كَانَ تَوَّابًۢا",
        ),
        112 to mapOf(
            1 to "قُلْ هُوَ ٱللَّهُ أَحَدٌ",
            2 to "ٱللَّهُ ٱلصَّمَدُ",
            3 to "لَمْ يَلِدْ وَلَمْ يُولَدْ",
            4 to "وَلَمْ يَكُن لَّهُۥ كُفُوًا أَحَدٌۢ",
        ),
        113 to mapOf(
            1 to "قُلْ أَعُوذُ بِرَبِّ ٱلْفَلَقِ",
            2 to "مِن شَرِّ مَا خَلَقَ",
            3 to "وَمِن شَرِّ غَاسِقٍ إِذَا وَقَبَ",
            4 to "وَمِن شَرِّ ٱلنَّفَّٰثَٰتِ فِى ٱلْعُقَدِ",
            5 to "وَمِن شَرِّ حَاسِدٍ إِذَا حَسَدَ",
        ),
        114 to mapOf(
            1 to "قُلْ أَعُوذُ بِرَبِّ ٱلنَّاسِ",
            2 to "مَلِكِ ٱلنَّاسِ",
            3 to "إِلَٰهِ ٱلنَّاسِ",
            4 to "مِن شَرِّ ٱلْوَسْوَاسِ ٱلْخَنَّاسِ",
            5 to "ٱلَّذِى يُوَسْوِسُ فِى صُدُورِ ٱلنَّاسِ",
            6 to "مِنَ ٱلْجِنَّةِ وَٱلنَّاسِ",
        ),
    )

    /** English meanings (Saheeh International rendering). */
    private val english: Map<Int, Map<Int, String>> = mapOf(
        1 to mapOf(
            1 to "In the name of Allah, the Entirely Merciful, the Especially Merciful.",
            2 to "All praise is due to Allah, Lord of the worlds.",
            3 to "The Entirely Merciful, the Especially Merciful.",
            4 to "Sovereign of the Day of Recompense.",
            5 to "It is You we worship and You we ask for help.",
            6 to "Guide us to the straight path.",
            7 to "The path of those upon whom You have bestowed favour, not of those who have evoked Your anger or of those who are astray.",
        ),
        2 to mapOf(
            255 to "Allah — there is no deity except Him, the Ever-Living, the Sustainer of existence. " +
                "Neither drowsiness overtakes Him nor sleep. To Him belongs whatever is in the heavens and whatever is on the earth. " +
                "Who is it that can intercede with Him except by His permission? He knows what is before them and what will be after them, " +
                "and they encompass not a thing of His knowledge except for what He wills. His Kursi extends over the heavens and the earth, " +
                "and their preservation tires Him not. And He is the Most High, the Most Great.",
        ),
        103 to mapOf(
            1 to "By time,",
            2 to "Indeed, mankind is in loss,",
            3 to "Except for those who have believed and done righteous deeds and advised each other to truth and advised each other to patience.",
        ),
        108 to mapOf(
            1 to "Indeed, We have granted you al-Kawthar.",
            2 to "So pray to your Lord and sacrifice.",
            3 to "Indeed, your enemy is the one cut off.",
        ),
        109 to mapOf(
            1 to "Say, O disbelievers,",
            2 to "I do not worship what you worship.",
            3 to "Nor are you worshippers of what I worship.",
            4 to "Nor will I be a worshipper of what you worship.",
            5 to "Nor will you be worshippers of what I worship.",
            6 to "For you is your religion, and for me is my religion.",
        ),
        110 to mapOf(
            1 to "When the victory of Allah has come and the conquest,",
            2 to "And you see the people entering into the religion of Allah in multitudes,",
            3 to "Then exalt Him with praise of your Lord and ask forgiveness of Him. Indeed, He is ever Accepting of repentance.",
        ),
        112 to mapOf(
            1 to "Say, He is Allah, who is One,",
            2 to "Allah, the Eternal Refuge.",
            3 to "He neither begets nor is born,",
            4 to "Nor is there to Him any equivalent.",
        ),
        113 to mapOf(
            1 to "Say, I seek refuge in the Lord of daybreak",
            2 to "From the evil of that which He created",
            3 to "And from the evil of darkness when it settles",
            4 to "And from the evil of the blowers in knots",
            5 to "And from the evil of an envier when he envies.",
        ),
        114 to mapOf(
            1 to "Say, I seek refuge in the Lord of mankind,",
            2 to "The Sovereign of mankind,",
            3 to "The God of mankind,",
            4 to "From the evil of the retreating whisperer",
            5 to "Who whispers in the breasts of mankind",
            6 to "From among the jinn and mankind.",
        ),
    )

    fun arabicOf(surahNumber: Int, ayahNumber: Int): String? = arabic[surahNumber]?.get(ayahNumber)

    fun englishOf(surahNumber: Int, ayahNumber: Int): String? = english[surahNumber]?.get(ayahNumber)

    /** True when every ayah of this surah is available offline. */
    fun isComplete(surahNumber: Int): Boolean =
        (arabic[surahNumber]?.size ?: 0) == ayahCountOf(surahNumber) && ayahCountOf(surahNumber) > 0

    /** Surahs that read fully offline — used by the reader's "available offline" hint. */
    val offlineSurahs: Set<Int> = arabic.keys.filter { isComplete(it) }.toSet()
}
