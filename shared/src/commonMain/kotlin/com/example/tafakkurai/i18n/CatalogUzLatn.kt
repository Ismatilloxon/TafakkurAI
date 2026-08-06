package com.example.tafakkurai.i18n

/**
 * Uzbek, Latin script — the app's default language and the one the onboarding copy was
 * originally written in, so these are the authored strings rather than translations.
 *
 * Uses the official modifier letters `oʻ` / `gʻ` (U+02BB) and `ʼ` (U+02BC), never ASCII
 * apostrophes; [UZ_CYRL] is the same text in the other script.
 */
internal val UZ_LATN: Map<String, String> = mapOf(
    "lang.overline" to "TIL",
    "lang.title" to "Tilni tanlang",
    "lang.subtitle" to "Buni keyinroq profilingizda oʻzgartirishingiz mumkin.",
    "lang.continue" to "Davom etish",

    "common.back" to "Orqaga",
    "common.retry" to "Qayta urinish",
    "common.done" to "Tayyor",
    "common.cancel" to "Bekor qilish",
    "common.save" to "Saqlash",
    "common.close" to "Yopish",
    "common.loading" to "Yuklanmoqda…",
    "common.next" to "Keyingi",

    "onb.skip" to "Oʻtkazish",
    "onb.start" to "Boshlash",
    "onb.continue" to "Davom etish",
    "onb.aspect.spiritual" to "RUHIY",
    "onb.aspect.physical" to "JISMONIY",
    "onb.aspect.educational" to "ILM",
    "onb.aspect.social" to "IJTIMOIY",
    "onb.aspect.financial" to "MOLIYA",

    "login.title" to "Xush kelibsiz",
    "login.subtitle" to "Davom etish uchun hisobingizga kiring. Maʼlumotlaringiz qurilmangizda, " +
        "maxfiy saqlanadi.",
    "login.apple" to "Apple bilan davom etish",
    "login.google" to "Google bilan davom etish",
    "login.phone" to "Telefon raqami bilan davom etish",
    "login.privacy" to "On-device AI · maʼlumot sotilmaydi",
    "login.terms" to "Davom etish orqali Foydalanish shartlari va Maxfiylik siyosatiga rozilik " +
        "bildirasiz.",
    "login.signing_in" to "Kirilmoqda…",
    "login.provider_unavailable" to "Bu kirish usuli hozircha mavjud emas. Telefon raqamingizdan foydalaning.",
    "login.failed" to "Kirishda xatolik. Qayta urinib koʻring.",
    "login.google_timeout" to "Google orqali kirish vaqti tugadi. Tugmani qayta bosing.",

    "phone.overline" to "TELEFON RAQAMI",
    "phone.title" to "Raqamingizni kiriting",
    "phone.subtitle" to "Tasdiqlash uchun SMS kod yuboramiz.",
    "phone.send_code" to "Kod yuborish",
    "otp.overline" to "TASDIQLASH",
    "otp.title" to "Kodni kiriting",
    "otp.sent_to" to "6 xonali kod +998 {0} raqamiga yuborildi.",
    "otp.verified" to "Tasdiqlandi",
    "otp.resend_in" to "Qayta yuborish · 0:{0}",
    "otp.resend" to "Kodni qayta yuborish",
    "otp.demo_hint" to "Kod SMS orqali keladi va 5 daqiqada eskiradi.",

    "profile.title" to "Profil",
    "profile.change_photo" to "Rasmni oʻzgartirish",
    "profile.streak" to "Ketma-ketlik",
    "profile.level" to "Daraja",
    "profile.xp" to "XP",
    "profile.coach" to "Murabbiy",
    "profile.language" to "Til",
    "profile.city" to "Shahar",
    "profile.coach.name" to "Murabbiy",
    "profile.language_hint" to "Butun ilova darhol almashadi.",

    "appearance.title" to "Koʻrinish",
    "appearance.heading" to "Oʻzingizga moslang",
    "appearance.subtitle" to "Mavzuni tanlang — butun ilova darhol yangilanadi. Qorongʻi va yorugʻ " +
        "tanlovlaringiz alohida eslab qolinadi.",
    "appearance.dark" to "Qorongʻi",
    "appearance.light" to "Yorugʻ",

    // ── Feedback: errors ─────────────────────────────────────────────────────
    "feedback.error.no_internet" to "Internetga ulanish yoʻq. Tarmoqni tekshirib, qayta urinib koʻring.",
    "feedback.error.timeout" to "Soʻrov juda uzoq davom etdi. Qayta urinib koʻring.",
    "feedback.error.bad_request" to "Soʻrovda xatolik bor. Qayta urinib koʻring.",
    "feedback.error.unauthorized" to "Sessiya muddati tugadi. Qaytadan kiring.",
    "feedback.error.forbidden" to "Bu amal uchun ruxsatingiz yoʻq.",
    "feedback.error.not_found" to "Soʻralgan maʼlumot topilmadi.",
    "feedback.error.conflict" to "Amal joriy maʼlumotlarga zid keldi. Yangilab, qayta urinib koʻring.",
    "feedback.error.validation" to "Belgilangan maydonlarni tekshirib, qayta urinib koʻring.",
    "feedback.error.rate_limited" to "Urinishlar juda koʻp. Biroz kutib, qayta urinib koʻring.",
    "feedback.error.server" to "Bizning tomonimizda xatolik yuz berdi. Birozdan soʻng urinib koʻring.",
    "feedback.error.parse" to "Kutilmagan javob keldi. Qayta urinib koʻring.",
    "feedback.error.unknown" to "Xatolik yuz berdi. Qayta urinib koʻring.",

    // ── Feedback: success ────────────────────────────────────────────────────
    "feedback.success.login" to "Tizimga muvaffaqiyatli kirdingiz.",
    "feedback.success.registered" to "Hisobingiz tayyor. Xush kelibsiz!",
    "feedback.success.profile_updated" to "Profil yangilandi.",
    "feedback.success.photo_updated" to "Rasm yangilandi.",
    "feedback.success.interests_saved" to "Qiziqishlar saqlandi.",
    "feedback.success.settings_saved" to "Sozlamalar saqlandi.",
    "feedback.success.thought_published" to "Fikringiz eʼlon qilindi.",
    "feedback.success.lesson_completed" to "Dars yakunlandi.",
    "feedback.success.entry_saved" to "Saqlandi.",

    // ── Feedback: warnings ───────────────────────────────────────────────────
    "feedback.warning.weak_connection" to "Internet aloqasi sust. Bu odatdagidan koʻra uzoqroq davom etishi mumkin.",
    "feedback.warning.unsaved_changes" to "Saqlanmagan oʻzgarishlar bor.",
    "feedback.warning.session_expiring" to "Sessiya muddati tugash arafasida.",

    // ── Sanalar ──────────────────────────────────────────────────────────────
    // {0} hafta kuni, {1} kun, {2} oy — "Dushanba, 3-avgust".
    "date.header" to "{0}, {1}-{2}",
    "date.weekday.0" to "Yakshanba",
    "date.weekday.1" to "Dushanba",
    "date.weekday.2" to "Seshanba",
    "date.weekday.3" to "Chorshanba",
    "date.weekday.4" to "Payshanba",
    "date.weekday.5" to "Juma",
    "date.weekday.6" to "Shanba",
    "date.month.1" to "yanvar",
    "date.month.2" to "fevral",
    "date.month.3" to "mart",
    "date.month.4" to "aprel",
    "date.month.5" to "may",
    "date.month.6" to "iyun",
    "date.month.7" to "iyul",
    "date.month.8" to "avgust",
    "date.month.9" to "sentabr",
    "date.month.10" to "oktabr",
    "date.month.11" to "noyabr",
    "date.month.12" to "dekabr",

    // ── Bugun: vaqtga qarab salomlashuv ──────────────────────────────────────
    "today.greeting.morning" to "Xayrli tong",
    "today.greeting.afternoon" to "Xayrli kun",
    "today.greeting.evening" to "Xayrli kech",
    "today.greeting.night" to "Xayrli tun",

    // ── Pastki menyu ─────────────────────────────────────────────────────────
    "tab.today" to "Bugun",
    "tab.learn" to "Oʻrganish",
    "tab.do" to "Bajarish",
    "tab.thoughts" to "Fikrlar",
    "tab.more" to "Yana",

    // ── Fikrlar ──────────────────────────────────────────────────────────────
    "thoughts.title" to "Fikrlar",
    "thoughts.compose_hint" to "Fikringizni ulashing…",
    "thoughts.circles" to "Doiralaringiz",
)
