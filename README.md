# tickets-backend

Единый бэкенд для Telegram-ботов слежения за билетами в театры. Хранит данные, периодически скрапит сайты театров и уведомляет подписчиков о появлении билетов.

Поддерживаемые театры: **РАМТ**, **Театр Наций**, **Театр им. Вахтангова**, **Мастерская Петра Фоменко**.

## Запуск

```bash
# Поднять PostgreSQL
cd posgres.local && docker compose up -d

# Запустить сервер (порт 8080)
./gradlew run
```

При старте схема БД создаётся автоматически, театры засеиваются, скраперы запускаются в фоне.

## Аутентификация

Все эндпоинты требуют заголовок:
```
Authorization: Bearer <api-key>
```

Ключи настраиваются в `application.yaml` (или через env-переменные):

| Ключ (по умолчанию) | Театр | Права |
|---|---|---|
| `ramt-secret` | РАМТ | свой театр |
| `nations-secret` | Театр Наций | свой театр |
| `vakhtangov-secret` | Вахтангов | свой театр |
| `fomenki-secret` | Фоменко | свой театр |
| `admin-secret` | — | все театры |

Боты могут обращаться только к данным своего театра. Admin-ключ не ограничен.

---

## API

Базовый URL: `http://localhost:8080/api`

### Пользователи

#### `POST /users/sync`
Создать или обновить пользователя по Telegram ID. Вызывается при первом взаимодействии с ботом и при любом обновлении профиля.

**Тело запроса:**
```json
{
  "telegramId": 123456789,
  "firstName": "Иван",
  "lastName": "Иванов",
  "username": "ivan"
}
```
`lastName` и `username` — необязательные.

**Ответ `200`:**
```json
{
  "id": "uuid",
  "telegramId": 123456789,
  "firstName": "Иван",
  "lastName": "Иванов",
  "username": "ivan",
  "isActive": true,
  "isVip": false
}
```

---

#### `GET /users/{telegramId}/subscriptions?theatre={slug}`
Список активных подписок пользователя, сгруппированных по театрам. Используется для команды `/mysubs`.

Параметр `theatre` необязателен — если передан, возвращает подписки только для указанного театра (`ramt`, `nations`, `vakhtangov`, `fomenki`).

**Ответ `200`:**
```json
[
  {
    "theatre": {
      "id": "uuid",
      "slug": "ramt",
      "name": "РАМТ",
      "websiteUrl": "https://ramt.ru"
    },
    "subscriptions": [
      {
        "id": "uuid",
        "performance": {
          "id": "uuid",
          "theatreId": "uuid",
          "title": "Гамлет",
          "url": "https://ramt.ru/plays/item/hamlet/",
          "scene": null
        },
        "theatre": {
          "id": "uuid",
          "slug": "ramt",
          "name": "РАМТ",
          "websiteUrl": "https://ramt.ru"
        },
        "subscribedAt": "2026-03-28T15:00:00",
        "notificationCount": 2
      }
    ]
  }
]
```

---

### Театры

#### `GET /theatres`
Список всех театров.

**Ответ `200`:**
```json
[
  {
    "id": "uuid",
    "slug": "ramt",
    "name": "РАМТ",
    "websiteUrl": "https://ramt.ru"
  }
]
```

---

### Спектакли

#### `GET /theatres/{slug}/performances?telegramId={id}`
Список всех спектаклей театра. Параметр `telegramId` необязателен — если передан, в ответе будет поле `isSubscribed` для каждого спектакля. Используется для команды `/perfs`.

`{slug}` — один из: `ramt`, `nations`, `vakhtangov`, `fomenki`. Бот может запрашивать только свой театр (или admin-ключ).

**Ответ `200`:**
```json
[
  {
    "id": "uuid",
    "theatreId": "uuid",
    "title": "Алые паруса",
    "url": "https://ramt.ru/plays/item/alye-parusa/",
    "scene": null,
    "isSubscribed": false
  }
]
```

Поле `scene` заполнено только для Вахтангова (название сцены).

---

### Подписки

#### `POST /subscriptions`
Подписать пользователя на спектакль.

**Тело запроса:**
```json
{
  "telegramId": 123456789,
  "performanceId": "uuid"
}
```

**Ответ `201`:**
```json
{ "status": "subscribed" }
```

Если пользователь ранее отписался — подписка реактивируется.

---

#### `DELETE /subscriptions`
Отписать пользователя от спектакля (мягкое удаление).

**Тело запроса:**
```json
{
  "telegramId": 123456789,
  "performanceId": "uuid"
}
```

**Ответ `200`:**
```json
{ "status": "unsubscribed" }
```

---

### Уведомления

#### `GET /notifications/pending?theatreSlug={slug}`
Список неотправленных уведомлений для театра. Бот вызывает этот эндпоинт в фоновом цикле.

Параметр `theatreSlug` необязателен — если не передан, используется slug из API-ключа.

**Ответ `200`:**
```json
[
  {
    "id": "uuid",
    "telegramId": 123456789,
    "performanceTitle": "Гамлет",
    "performanceUrl": "https://ramt.ru/plays/item/hamlet/",
    "theatreSlug": "ramt",
    "scheduleSummary": "• 15 апреля 19:00\n• 22 апреля 19:00",
    "createdAt": "2026-03-28T12:00:00"
  }
]
```

`scheduleSummary` — готовый текст с датами и временем показов, где есть билеты.

---

#### `POST /notifications/{id}/ack`
Пометить уведомление как отправленное. Бот вызывает после успешной отправки сообщения в Telegram.

**Ответ `200`** (пустое тело).

---

### Административные эндпоинты

Требуют либо admin-ключ, либо ключ соответствующего театра.

#### `GET /admin/theatres/{slug}/subscriptions`
Все активные подписки театра, сгруппированные по спектаклям. Используется для команды `/subs`.

**Ответ `200`:**
```json
[
  {
    "performance": {
      "id": "uuid",
      "theatreId": "uuid",
      "title": "Гамлет",
      "url": "https://ramt.ru/plays/item/hamlet/",
      "scene": null
    },
    "subscribers": [
      {
        "telegramId": 123456789,
        "firstName": "Иван",
        "username": "ivan",
        "subscribedAt": "2026-03-28T15:00:00",
        "notificationCount": 2
      }
    ]
  }
]
```

---

#### `POST /admin/users/{telegramId}/vip`
Присвоить пользователю статус VIP. Только admin-ключ.

**Ответ `200`** (пустое тело).

---

#### `DELETE /admin/users/{telegramId}/vip`
Снять статус VIP с пользователя. Только admin-ключ.

**Ответ `200`** (пустое тело).

---

#### `GET /admin/users`
Список всех пользователей. Только admin-ключ.

Параметр `hasSubscriptions` необязателен:
- `?hasSubscriptions=true` — только пользователи с хотя бы одной подпиской
- `?hasSubscriptions=false` — только пользователи без подписок

**Ответ `200`:**
```json
[
  {
    "id": "uuid",
    "telegramId": 123456789,
    "firstName": "Иван",
    "lastName": "Иванов",
    "username": "ivan",
    "isActive": true,
    "isVip": false
  }
]
```

---

#### `GET /admin/stats`
Общая статистика по всем театрам. Только admin-ключ.

**Ответ `200`:**
```json
{
  "totalUsers": 150,
  "activeUsers": 130,
  "totalSubscriptions": 420,
  "activeSubscriptions": 380,
  "pendingNotifications": 5,
  "byTheatre": [
    {
      "slug": "ramt",
      "name": "РАМТ",
      "performanceCount": 62,
      "activeSubscriptionCount": 95
    }
  ]
}
```

---

## Коды ошибок

Все ошибки возвращаются в формате:
```json
{ "code": "NOT_FOUND", "message": "Performance not found" }
```

| HTTP | code | Причина |
|---|---|---|
| 400 | `BAD_REQUEST` | Некорректные параметры запроса |
| 401 | — | Отсутствует или неверный API-ключ |
| 403 | `FORBIDDEN` | Бот запрашивает чужой театр |
| 404 | `NOT_FOUND` | Ресурс не найден |
| 409 | `CONFLICT` | Конфликт данных |
| 500 | `INTERNAL_ERROR` | Внутренняя ошибка сервера |

---

## Переменные окружения

| Переменная | По умолчанию | Описание |
|---|---|---|
| `DB_URL` | `jdbc:postgresql://localhost:5455/tickets` | JDBC URL базы данных |
| `DB_USER` | `tickets` | Пользователь БД |
| `DB_PASSWORD` | `tickets` | Пароль БД |
| `RAMT_API_KEY` | `ramt-secret` | API-ключ для бота РАМТ |
| `NATIONS_API_KEY` | `nations-secret` | API-ключ для бота Театра Наций |
| `VAKHTANGOV_API_KEY` | `vakhtangov-secret` | API-ключ для бота Вахтангова |
| `FOMENKI_API_KEY` | `fomenki-secret` | API-ключ для бота Фоменко |
| `ADMIN_API_KEY` | `admin-secret` | Мастер API-ключ |
| `BOT_WEBHOOK_URL_RAMT` | — | URL вебхука бота РАМТ |
| `BOT_WEBHOOK_SECRET_RAMT` | — | Секрет вебхука бота РАМТ |
| `BOT_WEBHOOK_URL_NATIONS` | — | URL вебхука бота Театра Наций |
| `BOT_WEBHOOK_SECRET_NATIONS` | — | Секрет вебхука бота Театра Наций |
| `BOT_WEBHOOK_URL_VAKHTANGOV` | — | URL вебхука бота Вахтангова |
| `BOT_WEBHOOK_SECRET_VAKHTANGOV` | — | Секрет вебхука бота Вахтангова |
| `BOT_WEBHOOK_URL_FOMENKI` | — | URL вебхука бота Фоменко |
| `BOT_WEBHOOK_SECRET_FOMENKI` | — | Секрет вебхука бота Фоменко |

## Поток интеграции бота

```
Пользователь пишет /start
  → POST /api/users/sync

Пользователь пишет /perfs
  → GET /api/theatres/{slug}/performances?telegramId={id}
  → Бот показывает инлайн-клавиатуру со спектаклями (✅ если подписан)

Пользователь нажимает на спектакль
  → POST /api/subscriptions  или  DELETE /api/subscriptions

Пользователь пишет /mysubs
  → GET /api/users/{telegramId}/subscriptions

Фоновый цикл бота (раз в N секунд)
  → GET /api/notifications/pending
  → Отправить каждое уведомление в Telegram
  → POST /api/notifications/{id}/ack
```
