# mcp-schedule

**일정 MCP 서버** · 패션 에이전트(시각장애인용 음성 AI 옷 추천)의 한 부분입니다.

사용자가 말로 넣은 일정을 저장하고, 일정마다 **어떤 자리인지(occasion)**와 **장소 종류(placeCategory)**를 붙여 돌려줍니다.
agent는 이 값을 보고 그날 어떤 옷이 맞는지 정합니다. 이 서버는 **데이터만 돌려주고 옷은 판단하지 않습니다.**

담당: 정지원

## 목차

1. [한눈에](#한눈에)
2. [동작 흐름](#동작-흐름)
3. [MCP 도구](#mcp-도구)
4. [자리 종류(occasion) 정하는 법](#자리-종류occasion-정하는-법)
5. [저장 방식](#저장-방식)
6. [아침 8시 알림](#아침-8시-알림)
7. [격식 일정 드라이클리닝 안내](#격식-일정-드라이클리닝-안내)
8. [REST API](#rest-api)
9. [실행 방법](#실행-방법)
10. [테스트](#테스트)
11. [폴더 구조](#폴더-구조)
12. [할 일](#할-일)
13. [공통 약속 · 브랜치 · 비밀 값](#공통-약속)

## 한눈에

| 항목 | 값 |
| --- | --- |
| 포트 | `8092` (환경 변수 `PORT`로 바꿀 수 있음) |
| MCP 엔드포인트 | `http://localhost:8092/mcp` (Streamable HTTP, Docker 안에서는 `http://mcp-schedule:8092/mcp`) |
| 상태 확인 | `GET /actuator/health` |
| 기술 | Java 21, Spring Boot 4.0.9, Spring AI MCP Server 2.0.2 (webmvc, `@McpTool`), Spring Data JPA + Flyway |
| 저장 | PostgreSQL `schedule.events` (설정이 없으면 메모리 DB) |
| 장소 검색 | 카카오 로컬 API 키워드 검색 (키가 없으면 끔) |
| 시간대 | `Asia/Seoul` |

왜 이 조합인지는 [mcp-schedule 도구 비교](https://claude.ai/artifact/R4xZv7yPmUB7fKz616JsT3)에 있습니다. MCP 서버 방식 3가지, 장소 API 3가지(카카오·네이버·구글), 일정 저장 2가지(자체 DB·구글 캘린더)를 비교했습니다.

## 동작 흐름

```
사용자 "다음 주 토요일 두 시에 더채플앳청담에서 친구 결혼식"
  → 앱 → Gateway → agent(GPT)
  → agent가 제목·시각·장소를 나눠 schedule_add 호출
  → mcp-schedule
       1. 제목 키워드로 자리 종류 판단 ("결혼식" → wedding)
       2. 장소가 있으면 카카오 로컬로 장소 종류 찾기 ("예식장")
       3. schedule.events에 저장
       4. 읽어 줄 문장(spokenSummary)과 함께 돌려줌
  → agent가 spokenSummary를 사용자에게 읽어 줌
```

옷 추천할 때는 agent가 오늘 날짜로 `schedule_list`를 불러 그날 일정의 `occasion`·`placeCategory`를 받습니다.

## MCP 도구

도구 입출력은 앱 레포 [`docs/contracts/mcp-tools.md`](https://github.com/fashion-agent-team/fashion-agent/blob/develop/docs/contracts/mcp-tools.md)의 mcp-schedule 절을 따릅니다. 코드는 `tool/ScheduleTools.java`.

| 도구 | 하는 일 | 성격(annotations) |
| --- | --- | --- |
| `schedule_add` | 일정 하나 등록 | 쓰기, 외부 API 사용(`openWorldHint`) |
| `schedule_list` | 날짜별 일정 보기 | 읽기 전용 |
| `schedule_delete` | 일정 하나 삭제 | 지우는 도구(`destructiveHint`) |
| `schedule_briefing` | 오늘 일정 + 3일 안 중요한 일정을 읽어 줄 문장으로 | 읽기 전용 |
| `schedule_set_reminder` | 일정의 폰 알림 받기·며칠 전에 미리 알릴지 | 쓰기, 같은 값이면 같은 결과(`idempotentHint`) |

`userId`는 모든 도구에 들어가며, agent가 Gateway에서 받은 `X-User-Id` 값으로 덮어써서 넣습니다. UUID여야 합니다.

### `schedule_add`

입력

| 이름 | 필수 | 설명 | 예 |
| --- | --- | --- | --- |
| `userId` | O | 사용자 id (UUID) | `3f2c…` |
| `title` | O | 일정 이름, 사용자가 말한 그대로 | `친구 결혼식` |
| `start` | O | 시작 시각 ISO-8601. 시간대가 없으면 Asia/Seoul로 봄 | `2026-11-14T14:00:00+09:00` |
| `place` | | 장소 이름. 말하지 않았으면 비움 | `더채플앳청담` |

출력

```json
{
  "eventId": "e_k3m8pq2xa9vd",
  "occasion": "wedding",
  "placeCategory": "예식장",
  "spokenSummary": "11월 14일 토요일 오후 2시, 더채플앳청담에서 친구 결혼식을 등록했어요. 그날 입을 옷은 미리 드라이클리닝을 맡기면 좋아요. 보통 1~3일 걸려요.",
  "careTip": "그날 입을 옷은 미리 드라이클리닝을 맡기면 좋아요. 보통 1~3일 걸려요."
}
```

- `placeCategory`: 장소가 없거나 카카오에서 못 찾으면 `null`.
- `spokenSummary`: 그대로 읽어 주면 되는 문장. "을/를"은 받침에 맞춰 붙입니다. 격식 일정이면 끝에 `careTip`이 붙어 있어서 agent는 따로 할 일이 없습니다.
- `careTip`: 격식 일정의 드라이클리닝 안내([아래](#격식-일정-드라이클리닝-안내)). 그 밖의 일정이면 `null`.

### `schedule_list`

입력

| 이름 | 필수 | 설명 | 예 |
| --- | --- | --- | --- |
| `userId` | O | 사용자 id (UUID) | |
| `date` | | 날짜 `YYYY-MM-DD` (Asia/Seoul 기준). 비우면 오늘 | `2026-11-14` |

출력 (시작 시각 순서, 일정이 없으면 `events: []`)

```json
{
  "events": [
    {
      "eventId": "e_k3m8pq2xa9vd",
      "title": "친구 결혼식",
      "start": "2026-11-14T14:00:00+09:00",
      "place": "더채플앳청담",
      "placeCategory": "예식장",
      "occasion": "wedding"
    }
  ]
}
```

### `schedule_delete`

입력: `userId`, `eventId`(`schedule_list` 결과의 값). 출력: `{"deleted": true}`.
"토요일 결혼식 지워 줘"처럼 말하면 agent가 먼저 `schedule_list`로 `eventId`를 찾은 뒤 부릅니다. 다른 사람의 일정은 지울 수 없습니다(`not_found`).

### `schedule_set_reminder`

일정을 넣은 뒤 agent가 "일정 전에 알림을 드릴까요?" → (응) "며칠 전에 미리 알려 드릴까요?"를 묻고 답으로 부릅니다(2026-11-11 진행 님).

입력: `userId`, `eventId`, `remind`(비우면 true), `daysBefore`(0~14, 비우면 1). 출력: 그 일정(`schedule_list`의 한 건 모양).

- `remind=false`: 알림 없음. `remindDaysBefore`는 null이 됩니다.
- `daysBefore=N`: N일 전 저녁 8시에 미리 알리고, 당일 아침에도 알립니다. 0이면 당일 아침만.
- 묻기 전에 넣은 일정은 `remind=true`, `remindDaysBefore=null`(전날 저녁 + 당일 아침)입니다.

알림은 앱이 폰 안에서 예약합니다(원격 푸시는 유료 Apple 계정이 필요해서). 앱은 `GET /api/v1/schedules?date=…&endDate=…`(둘 다 포함, 최대 31일)로 여러 날을 한 번에 받아 이 값대로 예약합니다.

### `schedule_briefing`

앱을 열고 인사한 뒤에 읽어 줄 안내입니다. 오늘 일정과, 내일부터 3일 뒤까지의 **격식 일정**(면접·결혼식·장례식 등)을 한 번에 돌려줍니다. 중요한 일정이 있으면 입을 옷을 세탁소에 맡겼는지 묻는 말이 붙습니다([드라이클리닝 안내](#격식-일정-드라이클리닝-안내)). 읽기만 하고 알림 기록은 바꾸지 않습니다.

입력: `userId`만.

출력

```json
{
  "spoken": "오늘은 오후 7시에 친구랑 저녁 일정이 있어요. 모레 11월 13일 금요일 오후 2시에 면접 일정이 있어요. 입을 옷은 세탁소에 맡기셨어요? 드라이클리닝은 보통 1~3일 걸려서 아직이면 오늘 맡기면 좋아요.",
  "today": [ { "eventId": "…", "title": "친구랑 저녁", "occasion": "casual", "…": "…" } ],
  "upcoming": [ { "eventId": "…", "title": "면접", "occasion": "formal", "…": "…" } ]
}
```

- `spoken`: 그대로 읽어 주면 되는 문장. 오늘 일정이 없으면 "오늘은 일정이 없어요."로 시작합니다. 오늘 일정이 여럿이면 "오늘 일정은 2개예요. 오전 10시에 팀 회의, 오후 2시에 …"
- `today`, `upcoming`: `schedule_list`의 일정 모양과 같습니다. 격식이 아닌 3일 안 일정은 `upcoming`에 넣지 않습니다.
- 앱은 REST `GET /api/v1/schedules/briefing`으로 같은 값을 받을 수 있습니다([REST API](#rest-api)).

### 결과와 오류 모양

결과는 JSON 글(text)과 같은 값의 `structuredContent` 두 가지로 돌려줍니다.
실패하면 `isError: true`와 함께 약속 문서의 오류 모양을 돌려줍니다.

```json
{ "error": { "code": "bad_request", "message": "start는 2026-11-14T14:00:00+09:00 모양이어야 해요." } }
```

| code | 언제 |
| --- | --- |
| `bad_request` | `userId`가 UUID가 아님, `title`·`start`·`eventId`가 비어 있음, 날짜·시각 모양이 틀림 |
| `not_found` | 지울 일정이 없거나 다른 사람 것 |
| `internal_error` | 그 밖의 서버 오류 (로그에 남김) |

## 자리 종류(occasion) 정하는 법

코드는 `event/OccasionClassifier.java`. 값은 앱의 `lib/styling/schedule_rules.dart`와 같고, 앞에 있을수록 격식 있는 자리입니다.

| occasion | 뜻 | 제목 키워드 | 카카오 장소 분류 키워드 |
| --- | --- | --- | --- |
| `mourning` | 장례·조문 | 장례, 조문, 빈소, 추도 | 장례식장, 납골당, 화장터, 추모공원, 공원묘지 |
| `wedding` | 결혼식 하객 | 결혼식, 웨딩, 예식 | 예식장, 웨딩홀, 웨딩컨벤션 |
| `formal` | 격식 있는 자리 | 면접, 발표, 상견례, 시상, 졸업식, 입학식, 세미나 | (없음) |
| `business` | 회의·출근·수업 | 회의, 미팅, 출근, 회사, 거래처, 수업, 강의 | 회사, 사무실, 오피스, 공공기관, 관공서, 학교, 대학교, 학원 |
| `active` | 운동 | 운동, 헬스, 등산, 산책, 러닝, 조깅, 요가, 필라테스, 축구, 농구 | 스포츠,레저, 산, 국립공원, 등산로, 체육관, 헬스, 피트니스, 운동장, 수영장, 골프 |
| `casual` | 그 밖 | | |

정하는 순서

1. **제목 키워드가 먼저입니다.** "친구 결혼식"은 장소가 호텔이어도 결혼식이기 때문입니다.
2. 제목으로 못 정하면(casual) **카카오 로컬 장소 분류**로 정합니다. 장소 이름으로 키워드 검색을 해서 첫 결과의 분류(`가정,생활 > 예식장`)를 봅니다. 분류의 마지막 마디(`예식장`)가 `placeCategory`가 됩니다.
3. 둘 다 아니면 `casual`.
4. 무엇으로 정했는지는 DB의 `occasion_source`에 남깁니다: `title` / `place` / `default`.

참고

- `산`처럼 한 글자 키워드는 분류 마디가 정확히 같을 때만 맞춥니다("부동산"에 걸리지 않게).
- **`KAKAO_REST_API_KEY`가 없으면** 카카오를 부르지 않고 제목 키워드로만 정합니다. 이때 `placeCategory`는 `null`입니다.
- 카카오 검색이 실패하거나 느려도(연결 3초, 응답 5초 제한) 일정은 그대로 등록됩니다.
- 카카오 분류 예(2026-11-10 실제 검색): 예식장 `가정,생활 > 결혼 > 예식장`, 장례식장 `가정,생활 > 장례 > 장례식장`, 산 `여행 > 관광,명소 > 산`, 호텔 `여행 > 숙박 > 호텔 > 롯데호텔`(→ 제목으로 정함).
- 검색 첫 결과가 다른 지점일 수 있습니다(예: "스타벅스 강남역점" → 강남구청역점). 분류는 같아서 자리 판단에는 문제가 없습니다.
- 장소 API는 `place/PlaceLookup` 인터페이스 뒤에 있어서, 네이버·구글로 바꿀 때는 구현 하나만 새로 만들면 됩니다.

## 저장 방식

일정은 PostgreSQL의 **`schedule` 스키마 `events` 테이블**에 저장합니다. 이 서비스는 `schedule` 스키마만 씁니다.

| 컬럼 | 타입 | 설명 |
| --- | --- | --- |
| `id` | varchar(40) PK | `e_` + 임의 12자. 도구의 `eventId` |
| `user_id` | uuid | 사용자 id (`account` 스키마라 FK 없음) |
| `title` | varchar(200) | 일정 이름 (빈 값 금지) |
| `start_at` | timestamptz | 시작 시각 |
| `place` | varchar(200) | 사용자가 말한 장소 이름 |
| `kakao_place_id` | varchar(20) | 카카오 장소 id (못 찾으면 null) |
| `place_category` | varchar(100) | 카카오 분류의 마지막 마디 |
| `occasion` | varchar(20) | 자리 종류 (위 6가지만) |
| `occasion_source` | varchar(10) | `title` / `place` / `default` |
| `notified_at` | timestamptz | 아침 알림 보낸 시각 (아직이면 null) |
| `care_reminded_at` | timestamptz | 격식 일정 드라이클리닝 안내를 마친 시각 (아직이면 null) |
| `created_at`, `updated_at` | timestamptz | 만든·고친 시각 |

인덱스: `(user_id, start_at)` (하루 일정 조회), `(start_at)` (아침 알림).

- 테이블은 **Flyway**(`src/main/resources/db/migration/V1__init.sql`, 열 추가는 `V2__care_reminded_at.sql`)가 만듭니다. `schedule` 스키마가 없으면 Flyway가 먼저 만듭니다. JPA는 엔티티와 테이블이 맞는지 확인만 합니다(`ddl-auto: validate`).
- 표 설계는 프로젝트 DB 설계(`db/schema.sql`의 schedule 부분)를 따릅니다.
- **`SPRING_DATASOURCE_URL`이 없으면 메모리 DB(H2, PostgreSQL 모드)로 뜹니다.** 테스트나 빠른 확인용이고, **서버를 껐다 켜면 일정이 모두 사라집니다.**

## 아침 8시 알림

코드는 `notify/MorningReminder.java`. 도구가 아니라 서버 안의 스케줄러입니다.

- 매일 아침 8시(Asia/Seoul, `schedule.reminder.cron`)에 오늘 일정 중 아직 알림을 안 보낸 것(`notified_at IS NULL`)을 찾습니다.
- 문장 예: "오늘 오후 2시에 더채플앳청담에서 친구 결혼식 일정이 있어요. 옷 추천을 받아 보세요."
- 보내면 `notified_at`을 채워서 다시 보내지 않습니다.
- **지금은 로그만 남깁니다**(`LoggingNotifier`, 사용자 id와 일정 id만 기록). 실제 푸시는 아직 없습니다.
- 푸시를 보내려면 사용자 폰의 주소(FCM 토큰)가 필요한데, 이 주소는 개인정보라 **gateway만 갖고 있고 일정 서버는 보지 않습니다.**
- 그래서 일정 서버는 "이 사용자에게 이 문장을 보내 주세요"만 gateway에 부탁하고, **실제 발송은 gateway가 합니다.** (안: `POST /internal/push {userId, title, body}`)
- gateway 쪽 기능이 생기면 `Notifier` 구현(예: `GatewayPushNotifier`)을 하나 만들어 `LoggingNotifier` 대신 끼우면 됩니다.

## 격식 일정 드라이클리닝 안내

시각장애인은 혼자 다림질하기 어렵고, 사진으로 구김을 찾기도 어렵습니다. 그래서 구김을 찾는 대신 **격식 일정이 잡히면 그날 입을 옷을 미리 세탁소에 맡기라고 말해 줍니다.** 드라이클리닝은 보통 1~3일 걸려서 당일 아침 알림으로는 늦기 때문에 두 번 말합니다.

대상 자리: `formal`(면접·발표·상견례 등), `wedding`(결혼식), `mourning`(장례식). 회의·출근(`business`)은 매일 입는 옷이라 뺍니다.

1. **등록할 때** — `schedule_add`의 `spokenSummary` 끝에 붙여서 agent가 그대로 읽어 줍니다. 남은 날에 따라 문장이 다릅니다.

   | 남은 날 | 문장 |
   | --- | --- |
   | 3일 이상 | 그날 입을 옷은 미리 드라이클리닝을 맡기면 좋아요. 보통 1~3일 걸려요. |
   | 모레 | 그날 입을 옷은 오늘이나 내일 드라이클리닝을 맡기면 좋아요. 보통 1~3일 걸려요. |
   | 내일 | 내일이라 드라이클리닝은 빠듯해요. 빨리 해 주는 세탁소를 찾아보거나, 입을 옷을 오늘 미리 꺼내 걸어 두세요. |
   | 오늘·지난 일정 | (말하지 않음) |

2. **3일 전 아침 8시** — 코드는 `notify/CareReminder.java`(`schedule.care-reminder.cron`). 내일~3일 뒤 시작하는 격식 일정 중 아직 안내 안 한 것(`care_reminded_at IS NULL`)에 보냅니다.
   - 문장 예: "3일 뒤 11월 14일 토요일 오후 2시에 면접 일정이 있어요. 입을 옷은 세탁소에 맡기셨어요? 드라이클리닝은 보통 1~3일 걸려서 아직이면 오늘 맡기면 좋아요."
   - 3일 전에 서버가 꺼져 있었으면 다음 날 "모레 …"로 보냅니다. 당일 일정은 아침 알림이 맡습니다.
   - 등록할 때 이미 3일 안쪽이었으면 등록 때 말한 것으로 끝내고 따로 보내지 않습니다(등록 때 `care_reminded_at`을 채움).
   - 아침 알림과 같은 `Notifier`로 보냅니다. **지금은 로그만 남고**, 실제 푸시는 gateway 발송 API가 생기면 함께 붙습니다.

## REST API

앱이 Gateway를 거쳐 직접 보는 날짜별 일정입니다(앱 레포 `openapi.yaml`의 `GET /api/v1/schedules`).

```bash
curl -H 'X-User-Id: 11111111-1111-1111-1111-111111111111' \
  'http://localhost:8092/api/v1/schedules?date=2026-11-14'
```

- 응답 모양은 `schedule_list`와 같습니다(`{"events": [...]}`).
- **`GET /api/v1/schedules/briefing`**: 앱을 열고 인사한 뒤 읽어 줄 안내. 응답은 `schedule_briefing`과 같습니다(`spoken`을 그대로 읽으면 됨).
- `X-User-Id`가 없으면 `401 unauthorized`, 입력이 틀리면 `400 bad_request`.

## 실행 방법

### 준비

- **Java 21** (Gradle wrapper가 들어 있어 Gradle은 따로 설치하지 않아도 됩니다)
- PostgreSQL 16 (없어도 메모리 DB로 뜸)
- 카카오 디벨로퍼스 REST API 키 (없어도 뜸)

macOS Homebrew로 설치한 Java를 쓴다면:

```bash
export JAVA_HOME=$(brew --prefix openjdk@21)
```

### 환경 변수 (`.env`)

`.env.example`을 복사해서 `.env`로 쓰고 값을 채웁니다. **`.env`는 커밋하지 않습니다**(`.gitignore`에 있음).

```bash
cp .env.example .env
```

```dotenv
# 카카오 로컬 API (장소 종류). 비우면 제목 키워드로만 자리를 정합니다.
KAKAO_REST_API_KEY=여기에_카카오_REST_키

# PostgreSQL. 비우면 메모리 DB(H2)로 뜹니다(껐다 켜면 일정이 사라짐).
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/fashion
SPRING_DATASOURCE_USERNAME=fashion
SPRING_DATASOURCE_PASSWORD=여기에_DB_비밀번호

# 포트 (기본 8092)
# PORT=8092
```

| 변수 | 없을 때 |
| --- | --- |
| `KAKAO_REST_API_KEY` | 카카오 검색 끔, 제목 키워드로만 판단 |
| `SPRING_DATASOURCE_URL` / `_USERNAME` / `_PASSWORD` | 메모리 DB(H2). 재시작하면 일정 사라짐 |
| `PORT` | 8092 |

### 실행

Spring Boot는 `.env`를 자동으로 읽지 않으므로 셸에 불러온 뒤 실행합니다.

```bash
set -a; source .env; set +a
./gradlew bootRun
```

뜨면 확인:

```bash
curl http://localhost:8092/actuator/health   # {"status":"UP"}
```

### Docker

```bash
docker build -t ghcr.io/fashion-agent-team/mcp-schedule:dev .
docker run --rm -p 8092:8092 --env-file .env ghcr.io/fashion-agent-team/mcp-schedule:dev
```

컨테이너 안에서 호스트의 PostgreSQL에 붙을 때는 `localhost` 대신 `host.docker.internal`을 씁니다.

### MCP Inspector로 직접 불러 보기

```bash
npx @modelcontextprotocol/inspector
# Transport: Streamable HTTP, URL: http://localhost:8092/mcp
```

## 테스트

```bash
./gradlew test
```

외부 서비스 없이 돕니다(메모리 DB, 카카오는 가짜 서버). PR과 `develop`·`main` 푸시마다 GitHub Actions(`.github/workflows/ci.yml`)가 같은 테스트를 돌립니다.

| 테스트 | 확인하는 것 |
| --- | --- |
| `McpToolsIntegrationTest` | MCP 클라이언트로 서버에 붙어 도구 4개가 보이는지, 추가→조회→삭제, 브리핑 도구와 REST가 같은지, 장소 없는 일정·시간대 없는 시각, 잘못된 입력의 오류 모양 |
| `OccasionClassifierTest` | 제목 키워드 판단, 제목 우선·장소 보강, `occasion_source` 기록, 한 글자 키워드 처리 |
| `KakaoLocalClientTest` | 카카오 응답에서 장소 종류 읽기, 실패하면 빈 값, 키가 없으면 부르지 않음 |
| `KoreanTextTest` | "을/를" 붙이기, 읽어 줄 날짜·시각 문장 |
| `DryCleaningAdviceTest` | 격식 자리만 안내, 남은 날에 맞춘 등록 문장, 3일 전 알림 문장 |
| `CareReminderTest` | 날짜를 옮겨 가며 3일 전 아침에 한 번만 알림, 놓치면 다음 날, 가까이 등록하면 알림 안 함, 격식 아닌 일정·당일 일정 제외, 브리핑 문장(일정 없음·여러 개·3일 안 중요 일정·다른 사람 일정 제외) |

## 폴더 구조

```
src/main/java/com/fashionagent/schedule/
├── tool/ScheduleTools.java        MCP 도구 4개
├── api/ScheduleController.java    REST GET /api/v1/schedules, /briefing
├── event/                         일정 저장·조회(ScheduleService), 자리 종류 판단(OccasionClassifier), 드라이클리닝 문장(DryCleaningAdvice)
├── place/                         장소 검색(PlaceLookup, KakaoLocalClient)
├── notify/                        아침 8시 알림(MorningReminder), 3일 전 드라이클리닝 알림(CareReminder), LoggingNotifier
└── common/                        오류 모양, 한국어 문장 도우미
src/main/resources/
├── application.yml
└── db/migration/                  V1 schedule.events 테이블, V2 care_reminded_at 열
```

## 할 일

- [x] MCP 서버 + `/mcp` + Dockerfile
- [x] `events` 테이블(Flyway), `schedule_add`·`schedule_list`·`schedule_delete`
- [x] 제목 키워드로 자리 종류 정하기 → 카카오 로컬로 장소 종류 보강
- [x] PR 자동 테스트(GitHub Actions)
- [x] 격식 일정 드라이클리닝 안내(등록 때 + 3일 전)
- [x] 앱 인사용 브리핑(`schedule_briefing`, `GET /api/v1/schedules/briefing`)
- [ ] 앱 인사 뒤에 브리핑 읽기 (앱 쪽, 인사 대사는 진행 님과 맞춘 뒤)
- [ ] 아침 알림·3일 전 알림 실제 푸시 붙이기 (gateway에 발송 API가 생기면 그걸 부르도록)
- [x] 실제 카카오 키로 장소 30곳 검색해 분류 키워드 표 확인 (화장터·국립공원 추가)

## 공통 약속

모든 레포가 지키는 약속은 앱 레포에 있습니다. **코드보다 약속 문서를 먼저 고칩니다.**

- [서비스 간 약속](https://github.com/fashion-agent-team/fashion-agent/blob/develop/docs/contracts/README.md): 공통 규칙, 포트, 인증(`X-User-Id`), 오류 모양, DB 스키마, 옷 추천 흐름
- [MCP 도구 입출력](https://github.com/fashion-agent-team/fashion-agent/blob/develop/docs/contracts/mcp-tools.md)
- [앱 ↔ 게이트웨이 API (OpenAPI)](https://github.com/fashion-agent-team/fashion-agent/blob/develop/docs/contracts/openapi.yaml)
- [아키텍처와 기술 스택](https://github.com/fashion-agent-team/fashion-agent/blob/develop/docs/ARCHITECTURE.md)

### 버전

| 항목 | 버전 |
| --- | --- |
| Java | 21 |
| Spring Boot | 4.0.9 |
| Spring AI (BOM) | 2.0.2 |

### 브랜치

GitFlow를 따릅니다. `develop`에서 `feature/…` 브랜치를 따서 PR로 `develop`에 합칩니다. 규칙은 앱 레포의 `docs/GITFLOW.md`를 봅니다.

### 비밀 값

API 키와 DB 비밀번호는 레포에 넣지 않습니다. 로컬은 `.env`(커밋 금지), 배포는 서버 환경 변수로 둡니다.
