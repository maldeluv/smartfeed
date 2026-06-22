# SmartFeed

Учебный проект SmartFeed. 

Backend и Big Data стенд готовы для демо. Android-клиент инициализирован как Java/XML skeleton; полноценные экраны и авторизация будут добавляться следующими этапами. Публичный Android API сохранен; admin CRUD вынесен в отдельный namespace `/api/v1/admin`.

## Состав

```text
smartfeed/
├── backend/
│   ├── app/
│   │   ├── main.py
│   │   ├── core/
│   │   │   ├── config.py
│   │   │   └── database.py
│   │   ├── db/
│   │   │   ├── init_db.py
│   │   │   └── seed.py
│   │   ├── api/
│   │   │   ├── deps.py
│   │   │   └── routers/
│   │   ├── models/
│   │   ├── repositories/
│   │   ├── schemas/
│   │   └── services/
│   ├── scripts/
│   ├── tests/
│   ├── requirements.txt
│   └── Dockerfile
├── bigdata/
│   ├── spark/
│   │   ├── structured_streaming.py
│   │   └── batch_recommendations_als.py
│   ├── generator/
│   │   ├── generate_seed_data.py
│   │   └── generate_events.py
│   ├── notebooks/
│   │   └── smartfeed_analytics.ipynb
│   └── data/
├── docs/
│   └── smartfeed.http
├── tz/
├── docker-compose.yml
├── .env.example
└── README.md
```

## Сервисы Docker Compose

- `backend` - FastAPI skeleton с `/health`
- `postgres` - PostgreSQL
- `mongo` - MongoDB
- `zookeeper` - координатор Kafka
- `kafka` - брокер событий
- `spark-master` - Spark master
- `spark-worker` - Spark worker
- `jupyter` - Jupyter/PySpark notebook

## Android Client

Android-проект находится в `android/SmartFeed`.

Сборка debug APK:

```powershell
cd android\SmartFeed
.\gradlew.bat assembleDebug
```

Готовый Android: Java/XML, Retrofit API/DTO, JWT auth, навигация, лента, экран статьи, пользовательские действия и Room offline cache. Android-события сначала записываются в `PendingEventEntity`, затем WorkManager отправляет их batch-запросом; настроены one-time и periodic sync, retryCount/lastError и счетчик очереди в ProfileFragment.

## Архитектура

```text
Android app
  -> FastAPI REST API
  -> PostgreSQL: users, categories, articles, actions, recommendations, analytics scores
  -> Kafka topic smartfeed.user_events
  -> Spark Structured Streaming
       -> MongoDB raw_user_events
       -> parquet aggregates / PostgreSQL user_category_scores
  -> Spark MLlib ALS batch
       -> PostgreSQL recommendations
  -> Jupyter notebook for demo charts
```

Backend разделен на `models`, `schemas`, `repositories`, `services` и `api/routers`. FastAPI dependencies используются для PostgreSQL session, MongoDB database и JWT-пользователя. Роли: `user` для мобильного API и `admin` для `/api/v1/admin/*`.

## API

- `POST /api/v1/auth/register`
- `POST /api/v1/auth/login`
- `GET /api/v1/users/me`
- `GET /api/v1/categories`
- `GET /api/v1/articles?category_id=&search=&limit=&offset=`
- `GET /api/v1/articles/{article_id}`
- `POST /api/v1/categories/{category_id}/subscribe`
- `DELETE /api/v1/categories/{category_id}/subscribe`
- `POST /api/v1/articles/{article_id}/like`
- `DELETE /api/v1/articles/{article_id}/like`
- `POST /api/v1/articles/{article_id}/save`
- `DELETE /api/v1/articles/{article_id}/save`
- `GET /api/v1/saved`
- `GET /api/v1/recommendations`
- `GET /api/v1/recommendations/me` compatibility alias
- `POST /api/v1/events`
- `POST /api/v1/events/batch`
- `GET /api/v1/analytics/me`
- `GET /api/v1/analytics/global`
- `POST /api/v1/admin/articles`
- `PUT /api/v1/admin/articles/{article_id}`
- `PATCH /api/v1/admin/articles/{article_id}`
- `DELETE /api/v1/admin/articles/{article_id}`

Профиль, пользовательские действия, сохраненные статьи, events, рекомендации и личная аналитика защищены Bearer JWT через dependency `current_user`.

Global analytics и admin endpoints защищены dependency `current_admin` и доступны только пользователям с `role=admin`.

Все действия создают событие `SmartFeedEvent` и отправляют его в Kafka topic `smartfeed.user_events`. Если Kafka недоступна, событие сохраняется в PostgreSQL таблицу `pending_events`; API возвращает понятный `delivery` status вместо 500.

## Запуск

Для PowerShell:

```powershell
Copy-Item .env.example .env
docker compose up -d --build
```

Для bash:

```bash
cp .env.example .env
docker compose up -d --build
```

## Схема запуска

Минимальный backend:

```powershell
Copy-Item .env.example .env
docker compose up -d --build postgres mongo zookeeper kafka backend
docker compose exec backend python -m app.db.init_db
docker compose exec backend python -m app.db.seed
```

Полный стенд для демонстрации Big Data:

```powershell
docker compose up -d --build
docker compose exec backend python generator/generate_seed_data.py --fallback --to-db
docker compose exec backend python generator/generate_events.py --fallback --to-kafka --to-db
```

Проверить состояние сервисов:

```powershell
docker compose ps
docker compose logs backend --tail=100
```

## Проверка

```powershell
docker compose ps
curl http://localhost:8000/health
```

Ожидаемый ответ:

```json
{"status":"ok"}
```

## PostgreSQL Init And Seed

Создать таблицы:

```powershell
docker compose exec backend python -m app.db.init_db
```

чтобы создать новую таблицу `pending_events`.

Заполнить seed-данными:

```powershell
docker compose exec backend python -m app.db.seed
```

Seed создает:

- admin-пользователя: `admin@smartfeed.local` / `admin12345`
- demo-пользователя: `demo@smartfeed.local` / `demo12345`
- 12 категорий IT-контента
- 200 seed-статей

Команда seed idempotent: повторный запуск не создает дубликаты пользователей, категорий и статей.

## Data Generator

Основной масштаб из ТЗ:

```powershell
docker compose exec backend python generator/generate_seed_data.py --users 500 --articles 1000 --to-db
docker compose exec backend python generator/generate_events.py --users 500 --articles 1000 --events 100000 --to-kafka --to-db
```

Fallback-масштаб для слабого железа:

```powershell
docker compose exec backend python generator/generate_seed_data.py --fallback --to-db
docker compose exec backend python generator/generate_events.py --fallback --to-kafka --to-db
```

Сгенерировать JSONL без записи в PostgreSQL и Kafka:

```powershell
python bigdata/generator/generate_seed_data.py --users 100 --articles 200
python bigdata/generator/generate_events.py --users 100 --articles 200 --events 10000
```

`generate_events.py` создает события за последние 30 дней, сохраняет связь `article_id -> category_id`, использует разные `event_type`, `session_id`, `device` и `metadata`. При `--to-db` события пишутся в `pending_events`, при `--to-kafka` отправляются в topic `smartfeed.user_events`.

## Spark Structured Streaming

Job reads Kafka topic `smartfeed.user_events`, validates event fields, writes valid raw events to MongoDB collection `raw_user_events`, writes invalid events to parquet, writes parquet aggregates, and updates PostgreSQL table `user_category_scores`. Score deltas are also written to parquet, so the batch is not lost if JDBC is temporarily unavailable.

Start streaming job:

```powershell
docker compose exec spark-master /opt/spark/bin/spark-submit `
  --master spark://spark-master:7077 `
  --conf spark.jars.ivy=/tmp/.ivy2 `
  --packages org.apache.spark:spark-sql-kafka-0-10_2.12:3.5.6,org.mongodb.spark:mongo-spark-connector_2.12:10.4.0,org.postgresql:postgresql:42.7.4 `
  spark/structured_streaming.py `
  --starting-offsets latest
```

The job runs continuously. In another terminal, send demo events:

```powershell
docker compose exec backend python generator/generate_events.py --users 100 --articles 200 --events 1000 --to-kafka
```

For a finite smoke run, add `--available-now` to the `spark-submit` script arguments.

Outputs:

- MongoDB raw events: `smartfeed.raw_user_events`
- Parquet aggregates: `bigdata/data/streaming/aggregates/`
- Score delta fallback/audit: `bigdata/data/streaming/aggregates/user_category_score_deltas/`
- Invalid events: `bigdata/data/streaming/dead_letter_events/`
- Checkpoints: `bigdata/data/checkpoints/structured_streaming/`
- PostgreSQL profile scores: `user_category_scores`

Checks:

```powershell
docker compose exec mongo mongosh -u smartfeed -p smartfeed --authenticationDatabase admin smartfeed --eval "db.raw_user_events.countDocuments()"
docker compose exec postgres psql -U smartfeed -d smartfeed -c "select * from user_category_scores limit 10;"
Get-ChildItem -Recurse bigdata\data\streaming
```

## Spark MLlib ALS Recommendations

Batch job reads user interactions, builds implicit ratings, trains Spark MLlib ALS, adds fallback recommendations from subscribed categories and globally popular articles, and writes the final top-N into PostgreSQL table `recommendations`.

Spark master/worker use the local `smartfeed-spark:3.5.6` image from `bigdata/spark/Dockerfile`, because PySpark MLlib needs `numpy`.

Rating weights:

- `view_article = 1`
- `like_article = 3`
- `save_article = 5`
- `open_recommended_article = 2`

Run ALS batch:

```powershell
docker compose up -d --build spark-master spark-worker
docker compose exec spark-master /opt/spark/bin/spark-submit `
  --master spark://spark-master:7077 `
  --conf spark.jars.ivy=/tmp/.ivy2 `
  --packages org.mongodb.spark:mongo-spark-connector_2.12:10.4.0,org.postgresql:postgresql:42.7.4 `
  spark/batch_recommendations_als.py `
  --top-n 10 `
  --min-events 5 `
  --model-version als-v1
```

The job uses MongoDB `smartfeed.raw_user_events` as the main interactions source. If the raw collection is empty or unavailable, it falls back to PostgreSQL `pending_events`.

Check saved recommendations:

```powershell
docker compose exec postgres psql -U smartfeed -d smartfeed -c "select user_id, article_id, score, reason, model_version from recommendations order by created_at desc limit 10;"
```

## Analytics API And Jupyter

Personal analytics:

```powershell
curl http://localhost:8000/api/v1/analytics/me -H "Authorization: Bearer <ACCESS_TOKEN>"
```

Global analytics:

```powershell
curl "http://localhost:8000/api/v1/analytics/global?limit=10&days=30" -H "Authorization: Bearer <ADMIN_ACCESS_TOKEN>"
```

The API uses MongoDB `smartfeed.raw_user_events` as the primary analytics source. If raw events are not available, it falls back to PostgreSQL `pending_events`; category interests can also fall back to `user_category_scores`.

Notebook for demo charts:

```text
http://localhost:8888/?token=smartfeed
```

Open `work/smartfeed_analytics.ipynb`. The notebook logs in as `demo@smartfeed.local`, calls the analytics API, and plots events by day, event type distribution, top categories, and recommendation CTR.

## Admin API

Admin credentials from seed:

```text
admin@smartfeed.local / admin12345
```

Create article:

```powershell
curl -X POST http://localhost:8000/api/v1/admin/articles `
  -H "Authorization: Bearer <ADMIN_ACCESS_TOKEN>" `
  -H "Content-Type: application/json" `
  -d "{\"title\":\"Admin Demo Article\",\"summary\":\"Article created by admin API.\",\"content\":\"Long enough article content for admin API demo.\",\"category_id\":1,\"author\":\"SmartFeed Admin\",\"popularity_score\":1}"
```

Update article:

```powershell
curl -X PUT http://localhost:8000/api/v1/admin/articles/<ARTICLE_ID> `
  -H "Authorization: Bearer <ADMIN_ACCESS_TOKEN>" `
  -H "Content-Type: application/json" `
  -d "{\"title\":\"Admin Demo Article Updated\",\"popularity_score\":2}"
```

`PATCH /api/v1/admin/articles/<ARTICLE_ID>` is kept as a compatibility alias.

Delete article:

```powershell
curl -X DELETE http://localhost:8000/api/v1/admin/articles/<ARTICLE_ID> `
  -H "Authorization: Bearer <ADMIN_ACCESS_TOKEN>"
```

HTTP collection with the main requests is available at `docs/smartfeed.http`.

## Tests And Smoke

Run pytest inside the backend container:

```powershell
docker compose up -d --build backend
docker compose exec backend python -m pytest tests
```

Run HTTP smoke-test against the running backend:

```powershell
docker compose exec backend python scripts/smoke_test.py --base-url http://localhost:8000
```

Smoke-test checks registration, login, article listing, like, save, event publishing, and recommendations endpoint.
Pytest also includes a generator scale test for 100000 synthetic events.

## Auth Check

Регистрация:

```powershell
curl -X POST http://localhost:8000/api/v1/auth/register `
  -H "Content-Type: application/json" `
  -d "{\"email\":\"user1@smartfeed.local\",\"full_name\":\"User One\",\"password\":\"password123\"}"
```

Логин demo-пользователя:

```powershell
curl -X POST http://localhost:8000/api/v1/auth/login `
  -H "Content-Type: application/json" `
  -d "{\"email\":\"demo@smartfeed.local\",\"password\":\"demo12345\"}"
```

Проверка профиля:

```powershell
curl http://localhost:8000/api/v1/users/me -H "Authorization: Bearer <ACCESS_TOKEN>"
```

Контент:

```powershell
curl http://localhost:8000/api/v1/categories
curl "http://localhost:8000/api/v1/articles?limit=5&offset=0"
curl http://localhost:8000/api/v1/articles/1
```

Пользовательские действия:

```powershell
curl -X POST http://localhost:8000/api/v1/categories/1/subscribe -H "Authorization: Bearer <ACCESS_TOKEN>"
curl -X POST http://localhost:8000/api/v1/articles/1/like -H "Authorization: Bearer <ACCESS_TOKEN>"
curl -X POST http://localhost:8000/api/v1/articles/1/save -H "Authorization: Bearer <ACCESS_TOKEN>"
curl http://localhost:8000/api/v1/saved -H "Authorization: Bearer <ACCESS_TOKEN>"
```

Произвольное событие:

```powershell
curl -X POST http://localhost:8000/api/v1/events `
  -H "Authorization: Bearer <ACCESS_TOKEN>" `
  -H "Content-Type: application/json" `
  -d "{\"eventType\":\"view_article\",\"articleId\":1,\"categoryId\":1,\"metadata\":{\"source\":\"feed\"}}"
```

Пакет offline-событий:

```powershell
curl -X POST http://localhost:8000/api/v1/events/batch `
  -H "Authorization: Bearer <ACCESS_TOKEN>" `
  -H "Content-Type: application/json" `
  -d "{\"events\":[{\"eventType\":\"search\",\"metadata\":{\"query\":\"spark\"}},{\"eventType\":\"open_recommendations\",\"metadata\":{}}]}"
```

## Demo Runbook

Оптимальный порядок запуска перед защитой: сначала поднять инфраструктуру и базовые данные, затем прогнать события через Kafka/Spark, затем построить рекомендации и только после этого показывать API, аналитику и notebook.

1. Подготовить `.env` и поднять весь стенд:

```powershell
Copy-Item .env.example .env
docker compose up -d --build
docker compose ps
```

Ожидаемо должны быть запущены `backend`, `postgres`, `mongo`, `zookeeper`, `kafka`, `spark-master`, `spark-worker`, `jupyter`. У `backend` и `jupyter` должен быть статус `healthy`.

2. Создать таблицы PostgreSQL и базовый seed:

```powershell
docker compose exec backend python -m app.db.init_db
docker compose exec backend python -m app.db.seed
```

Seed создает пользователей:

```text
demo@smartfeed.local / demo12345
admin@smartfeed.local / admin12345
```

3. Быстро проверить backend перед Big Data частью:

```powershell
curl http://localhost:8000/health
docker compose exec backend python scripts/smoke_test.py --base-url http://localhost:8000
```

4. Сгенерировать demo-данные. Для быстрой демонстрации используй fallback-объем:

```powershell
docker compose exec backend python generator/generate_seed_data.py --fallback --to-db
docker compose exec backend python generator/generate_events.py --fallback --to-kafka --to-db
```

Для полного объема из ТЗ вместо fallback можно запустить:

```powershell
docker compose exec backend python generator/generate_seed_data.py --users 500 --articles 1000 --to-db
docker compose exec backend python generator/generate_events.py --users 500 --articles 1000 --events 100000 --to-kafka --to-db
```

5. Обработать события Spark Structured Streaming. Для демо удобен конечный запуск `--available-now`, он прочитает события из Kafka и завершится:

```powershell
docker compose exec spark-master /opt/spark/bin/spark-submit `
  --master spark://spark-master:7077 `
  --conf spark.jars.ivy=/tmp/.ivy2 `
  --packages org.apache.spark:spark-sql-kafka-0-10_2.12:3.5.6,org.mongodb.spark:mongo-spark-connector_2.12:10.4.0,org.postgresql:postgresql:42.7.4 `
  spark/structured_streaming.py `
  --starting-offsets earliest `
  --available-now
```

После этого можно проверить, что raw events попали в MongoDB, а category scores появились в PostgreSQL:

```powershell
docker compose exec mongo mongosh -u smartfeed -p smartfeed --authenticationDatabase admin smartfeed --eval "db.raw_user_events.countDocuments()"
docker compose exec postgres psql -U smartfeed -d smartfeed -c "select user_id, category_id, score from user_category_scores order by updated_at desc limit 10;"
```

6. Построить рекомендации ALS:

```powershell
docker compose exec spark-master /opt/spark/bin/spark-submit `
  --master spark://spark-master:7077 `
  --conf spark.jars.ivy=/tmp/.ivy2 `
  --packages org.mongodb.spark:mongo-spark-connector_2.12:10.4.0,org.postgresql:postgresql:42.7.4 `
  spark/batch_recommendations_als.py `
  --top-n 10 `
  --min-events 5 `
  --model-version als-demo
```

Проверить записи рекомендаций:

```powershell
docker compose exec postgres psql -U smartfeed -d smartfeed -c "select user_id, article_id, score, reason, model_version from recommendations order by created_at desc limit 10;"
```

7. Получить demo/admin токены для ручной проверки API:

```powershell
$demoLogin = Invoke-RestMethod -Uri http://localhost:8000/api/v1/auth/login -Method Post -ContentType "application/json" -Body '{"email":"demo@smartfeed.local","password":"demo12345"}'
$demoToken = $demoLogin.access_token

$adminLogin = Invoke-RestMethod -Uri http://localhost:8000/api/v1/auth/login -Method Post -ContentType "application/json" -Body '{"email":"admin@smartfeed.local","password":"admin12345"}'
$adminToken = $adminLogin.access_token
```

8. Показать мобильный API:

```powershell
Invoke-RestMethod -Uri http://localhost:8000/api/v1/categories
Invoke-RestMethod -Uri "http://localhost:8000/api/v1/articles?limit=5&offset=0"
Invoke-RestMethod -Uri "http://localhost:8000/api/v1/recommendations?limit=10&offset=0" -Headers @{Authorization = "Bearer $demoToken"}
Invoke-RestMethod -Uri http://localhost:8000/api/v1/analytics/me -Headers @{Authorization = "Bearer $demoToken"}
Invoke-RestMethod -Uri "http://localhost:8000/api/v1/analytics/global?limit=10&days=30" -Headers @{Authorization = "Bearer $adminToken"}
```

9. Показать UI-инструменты:

```text
FastAPI OpenAPI: http://localhost:8000/docs
Spark UI: http://localhost:8080
Jupyter: http://localhost:8888/?token=smartfeed
Notebook: work/smartfeed_analytics.ipynb
HTTP collection: docs/smartfeed.http
```

10. Если нужно прогнать тесты прямо перед демонстрацией:

```powershell
docker compose exec backend python -m pytest tests
```

11. Если нужно начать демо с полностью чистых данных:

```powershell
docker compose down -v
docker compose up -d --build
docker compose exec backend python -m app.db.init_db
docker compose exec backend python -m app.db.seed
```

## Demo Readiness Checklist

- Docker Desktop запущен, `docker compose ps` показывает `backend`, `postgres`, `mongo`, `kafka`, `zookeeper`, `spark-master`, `spark-worker`, `jupyter`.
- `backend` имеет статус `healthy`, `curl http://localhost:8000/health` возвращает `{"status":"ok"}`.
- PostgreSQL таблицы созданы: `docker compose exec backend python -m app.db.init_db`.
- Seed выполнен: `docker compose exec backend python -m app.db.seed`.
- Generator выполнил seed/events хотя бы в fallback-режиме.
- Spark Structured Streaming `--available-now` пишет raw events в MongoDB и агрегаты.
- ALS batch записывает строки в PostgreSQL `recommendations`.
- `docker compose exec backend python -m pytest tests` проходит без падений.
- `docker compose exec backend python scripts/smoke_test.py --base-url http://localhost:8000` проходит полностью.
- OpenAPI доступен на `http://localhost:8000/docs`.
- Jupyter доступен на `http://localhost:8888/?token=smartfeed`, notebook `work/smartfeed_analytics.ipynb` открывается.
- `docs/smartfeed.http` содержит основные запросы для демонстрации API.

Дополнительные UI:

- FastAPI OpenAPI: `http://localhost:8000/docs`
- Spark UI: `http://localhost:8080`
- Jupyter: `http://localhost:8888/?token=smartfeed`

Остановка стенда:

```powershell
docker compose down
```

Остановка с удалением volume-данных:

```powershell
docker compose down -v
```
