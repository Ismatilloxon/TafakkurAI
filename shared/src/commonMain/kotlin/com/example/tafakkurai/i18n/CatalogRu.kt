package com.example.tafakkurai.i18n

/** Russian table. */
internal val RU: Map<String, String> = mapOf(
    "lang.overline" to "ЯЗЫК",
    "lang.title" to "Выберите язык",
    "lang.subtitle" to "Это можно изменить позже в профиле.",
    "lang.continue" to "Продолжить",

    "common.back" to "Назад",
    "common.retry" to "Повторить",
    "common.done" to "Готово",
    "common.cancel" to "Отмена",
    "common.save" to "Сохранить",
    "common.close" to "Закрыть",
    "common.loading" to "Загрузка…",
    "common.next" to "Далее",

    "onb.skip" to "Пропустить",
    "onb.start" to "Начать",
    "onb.continue" to "Продолжить",
    "onb.aspect.spiritual" to "ДУХОВНОЕ",
    "onb.aspect.physical" to "ФИЗИЧЕСКОЕ",
    "onb.aspect.educational" to "ЗНАНИЯ",
    "onb.aspect.social" to "СОЦИАЛЬНОЕ",
    "onb.aspect.financial" to "ФИНАНСЫ",

    "login.title" to "Добро пожаловать",
    "login.subtitle" to "Войдите, чтобы продолжить. Ваши данные хранятся приватно, на устройстве.",
    "login.apple" to "Продолжить с Apple",
    "login.google" to "Продолжить с Google",
    "login.phone" to "Продолжить по номеру телефона",
    "login.privacy" to "ИИ на устройстве · данные не продаются",
    "login.terms" to "Продолжая, вы принимаете Условия использования и Политику конфиденциальности.",
    "login.signing_in" to "Вход…",
    "login.provider_unavailable" to "Этот способ входа пока недоступен. Используйте номер телефона.",
    "login.failed" to "Не удалось войти. Попробуйте ещё раз.",

    "phone.overline" to "НОМЕР ТЕЛЕФОНА",
    "phone.title" to "Введите номер",
    "phone.subtitle" to "Мы отправим код подтверждения по SMS.",
    "phone.send_code" to "Отправить код",
    "otp.overline" to "ПОДТВЕРЖДЕНИЕ",
    "otp.title" to "Введите код",
    "otp.sent_to" to "Код из 6 цифр отправлен на +998 {0}.",
    "otp.verified" to "Подтверждено",
    "otp.resend_in" to "Отправить снова · 0:{0}",
    "otp.resend" to "Отправить код снова",
    "otp.demo_hint" to "Код придёт по SMS и действует 5 минут.",

    "profile.title" to "Профиль",
    "profile.change_photo" to "Изменить фото",
    "profile.streak" to "Серия",
    "profile.level" to "Уровень",
    "profile.xp" to "XP",
    "profile.coach" to "Наставник",
    "profile.language" to "Язык",
    "profile.city" to "Город",
    "profile.coach.name" to "Мураббий",
    "profile.language_hint" to "Всё приложение переключится сразу.",

    "appearance.title" to "Оформление",
    "appearance.heading" to "Настройте под себя",
    "appearance.subtitle" to "Выберите тему — всё приложение изменится мгновенно. Тёмный и светлый " +
        "варианты запоминаются отдельно.",
    "appearance.dark" to "Тёмная",
    "appearance.light" to "Светлая",

    // ── Feedback: errors ─────────────────────────────────────────────────────
    "feedback.error.no_internet" to "Нет подключения к интернету. Проверьте сеть и повторите попытку.",
    "feedback.error.timeout" to "Запрос занял слишком много времени. Попробуйте ещё раз.",
    "feedback.error.bad_request" to "Что-то не так с запросом. Попробуйте ещё раз.",
    "feedback.error.unauthorized" to "Сессия истекла. Войдите снова.",
    "feedback.error.forbidden" to "У вас нет прав для этого действия.",
    "feedback.error.not_found" to "Не удалось найти запрошенные данные.",
    "feedback.error.conflict" to "Действие конфликтует с текущими данными. Обновите и попробуйте снова.",
    "feedback.error.validation" to "Проверьте выделенные поля и попробуйте снова.",
    "feedback.error.rate_limited" to "Слишком много попыток. Подождите немного и повторите.",
    "feedback.error.server" to "На нашей стороне произошла ошибка. Повторите попытку позже.",
    "feedback.error.parse" to "Получен неожиданный ответ. Попробуйте ещё раз.",
    "feedback.error.unknown" to "Что-то пошло не так. Попробуйте ещё раз.",

    // ── Feedback: success ────────────────────────────────────────────────────
    "feedback.success.login" to "Вход выполнен.",
    "feedback.success.registered" to "Аккаунт готов. Добро пожаловать!",
    "feedback.success.profile_updated" to "Профиль обновлён.",
    "feedback.success.photo_updated" to "Фото обновлено.",
    "feedback.success.interests_saved" to "Интересы сохранены.",
    "feedback.success.settings_saved" to "Настройки сохранены.",
    "feedback.success.thought_published" to "Ваша мысль опубликована.",
    "feedback.success.lesson_completed" to "Урок завершён.",
    "feedback.success.entry_saved" to "Сохранено.",

    // ── Feedback: warnings ───────────────────────────────────────────────────
    "feedback.warning.weak_connection" to "Слабое соединение. Это может занять больше времени.",
    "feedback.warning.unsaved_changes" to "Есть несохранённые изменения.",
    "feedback.warning.session_expiring" to "Сессия скоро истечёт.",

    // ── Даты ─────────────────────────────────────────────────────────────────
    // {0} день недели, {1} число, {2} месяц — "Понедельник, 3 августа".
    "date.header" to "{0}, {1} {2}",
    "date.weekday.0" to "Воскресенье",
    "date.weekday.1" to "Понедельник",
    "date.weekday.2" to "Вторник",
    "date.weekday.3" to "Среда",
    "date.weekday.4" to "Четверг",
    "date.weekday.5" to "Пятница",
    "date.weekday.6" to "Суббота",
    // Родительный падеж — форма, в которой месяц стоит в дате.
    "date.month.1" to "января",
    "date.month.2" to "февраля",
    "date.month.3" to "марта",
    "date.month.4" to "апреля",
    "date.month.5" to "мая",
    "date.month.6" to "июня",
    "date.month.7" to "июля",
    "date.month.8" to "августа",
    "date.month.9" to "сентября",
    "date.month.10" to "октября",
    "date.month.11" to "ноября",
    "date.month.12" to "декабря",

    // ── Сегодня: приветствие по времени суток ────────────────────────────────
    "today.greeting.morning" to "Доброе утро",
    "today.greeting.afternoon" to "Добрый день",
    "today.greeting.evening" to "Добрый вечер",
    "today.greeting.night" to "Доброй ночи",

    // ── Нижнее меню ──────────────────────────────────────────────────────────
    "tab.today" to "Сегодня",
    "tab.learn" to "Учёба",
    "tab.do" to "Дела",
    "tab.thoughts" to "Мысли",
    "tab.more" to "Ещё",

    // ── Мысли ────────────────────────────────────────────────────────────────
    "thoughts.title" to "Мысли",
    "thoughts.compose_hint" to "Поделитесь мыслью…",
    "thoughts.circles" to "Ваши круги",
)
