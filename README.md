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
7. [REST API](#rest-api)
8. [실행 방법](#실행-방법)
9. [테스트](#테스트)
10. [폴더 구조](#폴더-구조)
11. [할 일](#할-일)
12. [공통 약속 · 브랜치 · 비밀 값](#공통-약속)

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
  "spokenSummary": "11월 14일 토요일 오후 2시, 더채플앳청담에서 친구 결혼식을 등록했어요."
}
```

- `placeCategory`: 장소가 없거나 카카오에서 못 찾으면 `null`.
- `spokenSummary`: 그대로 읽어 주면 되는 문장. "을/를"은 받침에 맞춰 붙입니다.

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
| `mourning` | 장례·조문 | 장례, 조문, 빈소, 추도 | 장례식장, 납골당, 추모공원, 공원묘지 |
| `wedding` | 결혼식 하객 | 결혼식, 웨딩, 예식 | 예식장, 웨딩홀, 웨딩컨벤션 |
| `formal` | 격식 있는 자리 | 면접, 발표, 상견례, 시상, 졸업식, 입학식, 세미나 | (없음) |
| `business` | 회의·출근·수업 | 회의, 미팅, 출근, 회사, 거래처, 수업, 강의 | 회사, 사무실, 오피스, 공공기관, 관공서, 학교, 대학교, 학원 |
| `active` | 운동 | 운동, 헬스, 등산, 산책, 러닝, 조깅, 요가, 필라테스, 축구, 농구 | 스포츠,레저, 산, 등산로, 체육관, 헬스, 피트니스, 운동장, 수영장, 골프 |
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
| `created_at`, `updated_at` | timestamptz | 만든·고친 시각 |

인덱스: `(user_id, start_at)` (하루 일정 조회), `(start_at)` (아침 알림).

- 테이블은 **Flyway**(`src/main/resources/db/migration/V1__init.sql`)가 만듭니다. `schedule` 스키마가 없으면 Flyway가 먼저 만듭니다. JPA는 엔티티와 테이블이 맞는지 확인만 합니다(`ddl-auto: validate`).
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

## REST API

앱이 Gateway를 거쳐 직접 보는 날짜별 일정입니다(앱 레포 `openapi.yaml`의 `GET /api/v1/schedules`).

```bash
curl -H 'X-User-Id: 11111111-1111-1111-1111-111111111111' \
  'http://localhost:8092/api/v1/schedules?date=2026-11-14'
```

- 응답 모양은 `schedule_list`와 같습니다(`{"events": [...]}`).
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
| `McpToolsIntegrationTest` | MCP 클라이언트로 서버에 붙어 도구 3개가 보이는지, 추가→조회→삭제, 장소 없는 일정·시간대 없는 시각, 잘못된 입력의 오류 모양 |
| `OccasionClassifierTest` | 제목 키워드 판단, 제목 우선·장소 보강, `occasion_source` 기록, 한 글자 키워드 처리 |
| `KakaoLocalClientTest` | 카카오 응답에서 장소 종류 읽기, 실패하면 빈 값, 키가 없으면 부르지 않음 |
| `KoreanTextTest` | "을/를" 붙이기, 읽어 줄 날짜·시각 문장 |

## 폴더 구조

```
src/main/java/com/fashionagent/schedule/
├── tool/ScheduleTools.java        MCP 도구 3개
├── api/ScheduleController.java    REST GET /api/v1/schedules
├── event/                         일정 저장·조회(ScheduleService), 자리 종류 판단(OccasionClassifier)
├── place/                         장소 검색(PlaceLookup, KakaoLocalClient)
├── notify/                        아침 8시 알림(MorningReminder, LoggingNotifier)
└── common/                        오류 모양, 한국어 문장 도우미
src/main/resources/
├── application.yml
└── db/migration/V1__init.sql      schedule.events 테이블
```

## 할 일

- [x] MCP 서버 + `/mcp` + Dockerfile
- [x] `events` 테이블(Flyway), `schedule_add`·`schedule_list`·`schedule_delete`
- [x] 제목 키워드로 자리 종류 정하기 → 카카오 로컬로 장소 종류 보강
- [x] PR 자동 테스트(GitHub Actions)
- [ ] 아침 알림 실제 푸시 붙이기 (gateway에 발송 API가 생기면 그걸 부르도록)
- [ ] 실제 카카오 키로 장소 분류 키워드 표 다듬기 (비교 스크립트: 프로젝트 파일 `mcp-schedule-compare/compare_places.py`)

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
