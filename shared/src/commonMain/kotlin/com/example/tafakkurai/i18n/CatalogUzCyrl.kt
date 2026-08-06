package com.example.tafakkurai.i18n

/**
 * Uzbek, Cyrillic script. The same words as [UZ_LATN] — one language, two alphabets, and
 * readers of one script generally do not read the other comfortably.
 *
 * Kept as its own table rather than transliterated at runtime: loanwords and proper nouns
 * ("Apple", "Google", "XP") stay in Latin here, which a mechanical converter would mangle.
 */
internal val UZ_CYRL: Map<String, String> = mapOf(
    "lang.overline" to "ТИЛ",
    "lang.title" to "Тилни танланг",
    "lang.subtitle" to "Буни кейинроқ профилингизда ўзгартиришингиз мумкин.",
    "lang.continue" to "Давом этиш",

    "common.back" to "Орқага",
    "common.retry" to "Қайта уриниш",
    "common.done" to "Тайёр",
    "common.cancel" to "Бекор қилиш",
    "common.save" to "Сақлаш",
    "common.close" to "Ёпиш",
    "common.loading" to "Юкланмоқда…",
    "common.next" to "Кейинги",

    "onb.skip" to "Ўтказиш",
    "onb.start" to "Бошлаш",
    "onb.continue" to "Давом этиш",
    "onb.aspect.spiritual" to "РУҲИЙ",
    "onb.aspect.physical" to "ЖИСМОНИЙ",
    "onb.aspect.educational" to "ИЛМ",
    "onb.aspect.social" to "ИЖТИМОИЙ",
    "onb.aspect.financial" to "МОЛИЯ",

    "login.title" to "Хуш келибсиз",
    "login.subtitle" to "Давом этиш учун ҳисобингизга киринг. Маълумотларингиз қурилмангизда, " +
        "махфий сақланади.",
    "login.apple" to "Apple билан давом этиш",
    "login.google" to "Google билан давом этиш",
    "login.phone" to "Телефон рақами билан давом этиш",
    "login.privacy" to "On-device AI · маълумот сотилмайди",
    "login.terms" to "Давом этиш орқали Фойдаланиш шартлари ва Махфийлик сиёсатига розилик " +
        "билдирасиз.",
    "login.signing_in" to "Кирилмоқда…",
    "login.provider_unavailable" to "Бу кириш усули ҳозирча мавжуд эмас. Телефон рақамингиздан фойдаланинг.",
    "login.failed" to "Киришда хатолик. Қайта уриниб кўринг.",
    "login.google_timeout" to "Google орқали кириш вақти тугади. Тугмани қайта босинг.",

    "phone.overline" to "ТЕЛЕФОН РАҚАМИ",
    "phone.title" to "Рақамингизни киритинг",
    "phone.subtitle" to "Тасдиқлаш учун SMS код юборамиз.",
    "phone.send_code" to "Код юбориш",
    "otp.overline" to "ТАСДИҚЛАШ",
    "otp.title" to "Кодни киритинг",
    "otp.sent_to" to "6 хонали код +998 {0} рақамига юборилди.",
    "otp.verified" to "Тасдиқланди",
    "otp.resend_in" to "Қайта юбориш · 0:{0}",
    "otp.resend" to "Кодни қайта юбориш",
    "otp.demo_hint" to "Код SMS орқали келади ва 5 дақиқада эскиради.",

    "profile.title" to "Профил",
    "profile.change_photo" to "Расмни ўзгартириш",
    "profile.streak" to "Кетма-кетлик",
    "profile.level" to "Даража",
    "profile.xp" to "XP",
    "profile.coach" to "Мураббий",
    "profile.language" to "Тил",
    "profile.city" to "Шаҳар",
    "profile.coach.name" to "Мураббий",
    "profile.language_hint" to "Бутун илова дарҳол алмашади.",

    "appearance.title" to "Кўриниш",
    "appearance.heading" to "Ўзингизга мосланг",
    "appearance.subtitle" to "Мавзуни танланг — бутун илова дарҳол янгиланади. Қоронғи ва ёруғ " +
        "танловларингиз алоҳида эслаб қолинади.",
    "appearance.dark" to "Қоронғи",
    "appearance.light" to "Ёруғ",

    // ── Feedback: errors ─────────────────────────────────────────────────────
    "feedback.error.no_internet" to "Интернетга уланиш йўқ. Тармоқни текшириб, қайта уриниб кўринг.",
    "feedback.error.timeout" to "Сўров жуда узоқ давом этди. Қайта уриниб кўринг.",
    "feedback.error.bad_request" to "Сўровда хатолик бор. Қайта уриниб кўринг.",
    "feedback.error.unauthorized" to "Сессия муддати тугади. Қайтадан киринг.",
    "feedback.error.forbidden" to "Бу амал учун рухсатингиз йўқ.",
    "feedback.error.not_found" to "Сўралган маълумот топилмади.",
    "feedback.error.conflict" to "Амал жорий маълумотларга зид келди. Янгилаб, қайта уриниб кўринг.",
    "feedback.error.validation" to "Белгиланган майдонларни текшириб, қайта уриниб кўринг.",
    "feedback.error.rate_limited" to "Уринишлар жуда кўп. Бироз кутиб, қайта уриниб кўринг.",
    "feedback.error.server" to "Бизнинг томонимизда хатолик юз берди. Бироздан сўнг уриниб кўринг.",
    "feedback.error.parse" to "Кутилмаган жавоб келди. Қайта уриниб кўринг.",
    "feedback.error.unknown" to "Хатолик юз берди. Қайта уриниб кўринг.",

    // ── Feedback: success ────────────────────────────────────────────────────
    "feedback.success.login" to "Тизимга муваффақиятли кирдингиз.",
    "feedback.success.registered" to "Ҳисобингиз тайёр. Хуш келибсиз!",
    "feedback.success.profile_updated" to "Профиль янгиланди.",
    "feedback.success.photo_updated" to "Расм янгиланди.",
    "feedback.success.interests_saved" to "Қизиқишлар сақланди.",
    "feedback.success.settings_saved" to "Созламалар сақланди.",
    "feedback.success.thought_published" to "Фикрингиз эълон қилинди.",
    "feedback.success.lesson_completed" to "Дарс якунланди.",
    "feedback.success.entry_saved" to "Сақланди.",

    // ── Feedback: warnings ───────────────────────────────────────────────────
    "feedback.warning.weak_connection" to "Интернет алоқаси суст. Бу одатдагидан кўра узоқроқ давом этиши мумкин.",
    "feedback.warning.unsaved_changes" to "Сақланмаган ўзгаришлар бор.",
    "feedback.warning.session_expiring" to "Сессия муддати тугаш арафасида.",

    // ── Саналар ──────────────────────────────────────────────────────────────
    // {0} ҳафта куни, {1} кун, {2} ой — "Душанба, 3-август".
    "date.header" to "{0}, {1}-{2}",
    "date.weekday.0" to "Якшанба",
    "date.weekday.1" to "Душанба",
    "date.weekday.2" to "Сешанба",
    "date.weekday.3" to "Чоршанба",
    "date.weekday.4" to "Пайшанба",
    "date.weekday.5" to "Жума",
    "date.weekday.6" to "Шанба",
    "date.month.1" to "январ",
    "date.month.2" to "феврал",
    "date.month.3" to "март",
    "date.month.4" to "апрел",
    "date.month.5" to "май",
    "date.month.6" to "июн",
    "date.month.7" to "июл",
    "date.month.8" to "август",
    "date.month.9" to "сентябр",
    "date.month.10" to "октябр",
    "date.month.11" to "ноябр",
    "date.month.12" to "декабр",

    // ── Бугун: вақтга қараб саломлашув ───────────────────────────────────────
    "today.greeting.morning" to "Хайрли тонг",
    "today.greeting.afternoon" to "Хайрли кун",
    "today.greeting.evening" to "Хайрли кеч",
    "today.greeting.night" to "Хайрли тун",

    // ── Пастки меню ──────────────────────────────────────────────────────────
    "tab.today" to "Бугун",
    "tab.learn" to "Ўрганиш",
    "tab.do" to "Бажариш",
    "tab.thoughts" to "Фикрлар",
    "tab.more" to "Яна",

    // ── Фикрлар ──────────────────────────────────────────────────────────────
    "thoughts.title" to "Фикрлар",
    "thoughts.compose_hint" to "Фикрингизни улашинг…",
    "thoughts.circles" to "Доираларингиз",
)
