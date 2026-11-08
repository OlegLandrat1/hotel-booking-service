# Hotel Booking Service
Система бронирования гостиниц на Spring Boot и Spring Cloud. Booking Service выпускает JWT, Hotel Service проверяет их по публичному ключу. Внешний доступ идёт через API Gateway.

## Состав системы

| Модуль | Порт | Назначение |
| --- | --- | --- |
| eureka-server | 8761 | Реестр сервисов |
| api-gateway | 8080 | Единая точка входа, маршрутизация |
| booking-service | 8081 | Пользователи, токены, бронирования |
| hotel-service | 8082 | Гостиницы, номера, занятость по датам |

## Запуск

### 1. Ключи

Из корня проекта:
```bash
mkdir -p booking-service/secrets booking-service/src/main/resources/keys
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out booking-service/secrets/booking-private.pem
openssl pkey -in booking-service/secrets/booking-private.pem -pubout -out booking-service/src/main/resources/keys/jwt-public.pem
cp booking-service/src/main/resources/keys/jwt-public.pem hotel-service/src/main/resources/keys/
```

Закрытый ключ остаётся только у Booking Service и в git не попадает. Публичный ключ нужен обоим сервисам.

### 2. Переменные окружения

| Переменная | Назначение | Пример |
| --- | --- | --- |
| `ADMIN_PASSWORD` | Пароль первого администратора (обязательна) | `admin123` |
| `ADMIN_USERNAME` | Логин администратора, по умолчанию `admin` | `admin` |
| `JWT_PRIVATE_KEY_PATH` | Путь к закрытому ключу, если рабочий каталог другой | `/abs/path/booking-private.pem` |
| `EUREKA_URL` | Адрес Eureka, по умолчанию `http://localhost:8761/eureka/` |  |

### 3. Порядок запуска

1. eureka-server
2. hotel-service
3. booking-service
4. api-gateway

Проверка: в панели Eureka http://localhost:8761 должны быть три сервиса: HOTEL-SERVICE, BOOKING-SERVICE, API-GATEWAY.

## Пользователи и роли

| Роль | Что может |
| --- | --- |
| Без токена | Регистрация, вход, Swagger |
| USER | Бронировать, смотреть свои бронирования, смотреть гостиницы и номера |
| ADMIN | Управлять пользователями, добавлять гостиницы и номера |
| SERVICE | Внутренний служебный токен для вызова Hotel из Booking, снаружи недоступен |

Администратор создаётся при первом запуске из `ADMIN_USERNAME` и `ADMIN_PASSWORD`. Пароль в файлах настроек не хранится.
Токен действует один час. В нём sub содержит идентификатор пользователя, role содержит роль.

## Как пользоваться

Пример сценария через шлюз. Замените адреса на свои, если меняли порты.

#### 1. Регистрация: 
получаем токен
   ```bash
   curl -s -X POST http://localhost:8080/api/user/register \
   -H 'Content-Type: application/json' \
   -d '{"login":"ivan","password":"secret"}'
   ```

#### 2. Вход: 
тот же результат для уже зарегистрированного пользователя
   ```bash
curl -s -X POST http://localhost:8080/api/user/auth \
   -H 'Content-Type: application/json' \
   -d '{"login":"ivan","password":"secret"}'
```

#### 3. Список гостиниц
   ```bash
curl -s http://localhost:8080/api/hotels -H "Authorization: Bearer $TOKEN"
```

#### 4. Свободные номера на даты
   ```bash
curl -s "http://localhost:8080/api/rooms?arrival=2026-12-01&departure=2026-12-05" \
   -H "Authorization: Bearer $TOKEN"
```
#### 5. Бронирование выбранного номера
   ```bash
curl -s -X POST http://localhost:8080/api/booking \
   -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
   -d '{"requestId":"r-1","roomId":1,"autoSelect":false,"arrival":"2026-12-01","departure":"2026-12-05"}'
```

Если Hotel отказал в номере, бронирование возвращается со статусом `CANCELLED`. Статус проверяйте в поле status, код ответа при этом 201.
Администратор получает токен через /api/user/auth с логином и паролем из переменных. С его токеном добавляются гостиницы и номера:
```bash
curl -s -X POST http://localhost:8080/api/hotels \
-H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' \
-d '{"name":"Dune","address":"Coast 1"}'
```
```bash
curl -s -X POST http://localhost:8080/api/rooms \
-H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' \
-d '{"hotel":1,"number":"101"}'
```

## Эндпойнты
Все пути даны так, как они видны через шлюз (/api/...). 
Прямые адреса сервисов: 
Booking на 8081, 
Hotel на 8082.

### Booking Service

| Метод и путь | Роль | Назначение | Ответ |
| --- | --- | --- | --- |
| POST `/api/user/register` | без токена | Регистрация, выдаёт токен | 201 `{token}` |
| POST `/api/user/auth` | без токена | Вход, выдаёт токен | 200 `{token}`, 401 при неверных данных |
| POST `/api/user` | ADMIN | Создать пользователя | 201 |
| PATCH `/api/user/{id}` | ADMIN | Изменить логин, пароль или роль | 200 |
| DELETE `/api/user/{id}` | ADMIN | Удалить пользователя | 204, 409 если есть бронирования |
| POST `/api/booking` | USER | Создать бронирование | 201, поле `status` |
| GET `/api/bookings` | USER | История своих бронирований | 200 |
| GET `/api/booking/{id}` | USER | Бронирование по id | 200, 404 |
| DELETE `/api/booking/{id}` | USER | Отменить бронирование | 200 |

Тело POST /api/booking:
requestId — строка, идентификатор запроса.
roomId — номер в Hotel Service.
autoSelect — пока не реализовано, поле принимается, но не используется.
arrival, departure — даты в формате ГГГГ-ММ-ДД.

### Hotel Service

| Метод и путь | Роль | Назначение | Ответ |
| --- | --- | --- | --- |
| GET `/api/hotels` | USER | Список гостиниц | 200 |
| POST `/api/hotels` | ADMIN | Добавить гостиницу | 201 |
| GET `/api/rooms?arrival&departure` | USER | Все свободные на даты номера | 200 |
| GET `/api/rooms/recommend?arrival&departure` | USER | Свободные номера, от наименее загруженного | 200 |
| POST `/api/rooms` | ADMIN | Добавить номер в гостиницу | 201 |
| POST `/api/rooms/{id}/confirm-availability` | SERVICE | Занять номер на даты | 200 `{lockId}`, 409 если занят |
| POST `/api/rooms/{id}/release` | SERVICE | Снять занятость по `requestId` | 204 |

Внутренние эндпойнты confirm-availability и release шлюз закрывает снаружи. Пользовательский токен на них даёт 403.

## Ошибки
Сервисы отвечают статусами HTTP и телом в формате ProblemDetail:
| Код | Когда |
| --- | --- |
| 400 | Неверные данные |
| 401 | Нет токена или неверный вход |
| 403 | Роль не подходит |
| 404 | Ресурс не найден |
| 409 | Конфликт: занятый номер, занятый логин, связанные бронирования |

## Тестирование

#### Запуск всех тестов из корня:
```bash
mvn -pl hotel-service,booking-service -am test
```
Отчёты лежат в `target/surefire-reports/`. 
Сводка:
```bash
grep -h "Tests run" hotel-service/target/surefire-reports/*.txt booking-service/target/surefire-reports/*.txt
```
### тесты:
    
+ **Доступ по ролям** (`AvailabilityControllerTest`, `BookingControllerTest`, `AccountControllerTest`): 
анонимный запрос даёт 401, неверная роль 403.

+ **Занятость** (`AvailabilityControllerTest`): пересечение дат даёт 409, выезд в день заезда не блокирует, повтор с тем же `requestId` возвращает тот же `lockId`, `release` освобождает даты.

+ **Список номеров** (`RoomListControllerTest`).

+ **Конкуренция** (`ConcurrentConfirmTest`): восемь одновременных подтверждений на один номер, принимается одно.

+ **Подпись JWT** (`SignedTokenTest`): токен, подписанный чужим ключом, отклоняется, токен роли `USER` не пускает на внутренний эндпойнт. Тестовые ключи лежат в `hotel-service/src/test/resources/test-keys/`.
+ **Восстановление зависших PENDING** (`StaleBookingTest`).

Тесты не обращаются к Eureka и к внешней сети. Порт и имя администратора в тестах задаются свойствами, переменная `ADMIN_PASSWORD` для них не нужна.




