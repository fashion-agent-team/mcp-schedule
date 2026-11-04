# mcp-schedule

**schedule MCP 서버 · 일정과 장소 종류** · 패션 에이전트(시각장애인용 음성 AI 옷 추천)의 한 부분입니다.

담당: 정지원 (임시 배정, 바꿔도 됨)

## 한눈에

- 포트 8092, MCP 엔드포인트 `http://mcp-schedule:8092/mcp` (Streamable HTTP)
- 기술: Java 21, Spring Boot 4.0.9, Spring AI MCP Server 2.0.2(webmvc, `@McpTool`), Spring Data JPA + Flyway, PostgreSQL, 카카오 로컬 API
- 왜 이 조합인지: [mcp-schedule 도구 비교](https://claude.ai/artifact/R4xZv7yPmUB7fKz616JsT3) (MCP 서버 방식 3가지, 장소 API 3가지, 일정 저장 2가지를 비교)
- 상태 확인: `GET /actuator/health`
- 입출력은 앱 레포 `docs/contracts/mcp-tools.md`(PR #12)의 mcp-schedule 절을 따릅니다.

## MCP 도구

| 도구 | 입력 | 출력 |
| --- | --- | --- |
| `schedule_add` | `userId`, `title`, `start`(ISO-8601, 시간대 포함), `place`(선택) | `eventId`, `occasion`, `placeCategory`, `spokenSummary` |
| `schedule_list` | `userId`, `date`(YYYY-MM-DD, 비우면 오늘) | `events[]`: `eventId`, `title`, `start`, `place`, `placeCategory`, `occasion` |
| `schedule_delete` | `userId`, `eventId` | `deleted` |

결과는 JSON 글과 같은 값의 `structuredContent`로 돌려줍니다. 실패하면 `{"error": {"code", "message"}}`에 `isError: true`를 붙입니다(`bad_request`, `not_found`). 도구마다 성격(annotations)을 적어 둡니다: `schedule_list`는 읽기 전용, `schedule_delete`는 지우는 도구. `userId`는 agent가 `X-User-Id` 값으로 덮어써서 넣습니다.

**데이터만 돌려줍니다.** 이 자리에 어떤 옷이 맞는지는 agent가 Gemini에 맡깁니다.

### 자리 종류(occasion)와 장소 분위기

`mourning`(장례·조문) `wedding`(결혼식) `formal`(면접·발표·상견례) `business`(회의·출근·수업) `active`(운동·등산) `casual`(그 밖).

1. 제목 키워드로 먼저 정합니다. 앱의 `lib/styling/schedule_rules.dart` 표를 그대로 옮겼습니다. "친구 결혼식"은 장소가 호텔이어도 결혼식이기 때문입니다.
2. 제목으로 못 정하면(`casual`) 장소 이름을 카카오 로컬 키워드 검색으로 찾아 분류(`가정,생활 > 예식장`)로 정합니다. 분류의 마지막 마디(`예식장`)가 `placeCategory`입니다.
3. 카카오 키가 없거나 검색이 실패해도 일정은 등록됩니다(제목으로만 정함).
4. 무엇으로 정했는지는 `occasion_source`(`title` / `place` / `default`)에 남깁니다.

장소 API는 `PlaceLookup` 인터페이스 뒤에 있어서, 네이버·구글로 바꿀 때는 구현 하나만 새로 만들면 됩니다.

### 그 밖

- `GET /api/v1/schedules?date=2026-11-14` (헤더 `X-User-Id`): 앱이 게이트웨이를 거쳐 보는 날짜별 일정. 모양은 `schedule_list`와 같습니다.
- 당일 아침 8시(Asia/Seoul) 알림 스케줄러: 지금은 로그만 남깁니다(`LoggingNotifier`). FCM 토큰이 gateway의 `account` 스키마에 있어서 받는 방법을 정한 뒤 붙입니다.

## 실행

```bash
./gradlew test        # 테스트 (MCP 클라이언트로 도구 3개를 실제로 불러 봄)
./gradlew bootRun     # http://localhost:8092/mcp
```

환경 변수는 `.env.example`을 봅니다. 아무것도 넣지 않으면 메모리 DB(H2)로 뜨고 카카오 검색은 꺼집니다.

| 변수 | 설명 |
| --- | --- |
| `KAKAO_REST_API_KEY` | 카카오 디벨로퍼스 REST API 키 |
| `SPRING_DATASOURCE_URL` / `_USERNAME` / `_PASSWORD` | PostgreSQL 접속. 예: `jdbc:postgresql://localhost:5432/fashion` |

테이블은 Flyway(`src/main/resources/db/migration/V1__init.sql`)가 `schedule` 스키마 안에 만들고, JPA는 맞는지 확인만 합니다(`ddl-auto: validate`). 표 설계는 프로젝트 DB 설계(`db/schema.sql`의 schedule 부분)를 따릅니다.

Docker: `docker build -t ghcr.io/fashion-agent-team/mcp-schedule:dev .`

### MCP Inspector로 직접 불러 보기

```bash
npx @modelcontextprotocol/inspector   # Transport: Streamable HTTP, URL: http://localhost:8092/mcp
```

## 할 일

- [x] MCP 서버 뼈대 + `/mcp` + Dockerfile
- [x] `events` 테이블(Flyway), `schedule_add`·`schedule_list`·`schedule_delete`
- [x] 제목 키워드로 occasion 정하기 → 카카오 로컬로 장소 종류 보강
- [ ] 당일 알림 붙이기 (제안: gateway 내부 API `POST /internal/push {userId, title, body}`, 팀 미확정)
- [ ] 실제 카카오 키로 장소 분류 표 다듬기 (비교 스크립트: 프로젝트 파일 `mcp-schedule-compare/compare_places.py`)

## 공통 약속

모든 레포가 지키는 약속은 앱 레포에 있습니다. **코드보다 약속 문서를 먼저 고칩니다.**

- [서비스 간 약속](https://github.com/fashion-agent-team/fashion-agent/blob/develop/docs/contracts/README.md): 공통 규칙, 포트, 인증(`X-User-Id`), 오류 모양, DB 스키마, 옷 추천 흐름
- [MCP 도구 입출력](https://github.com/fashion-agent-team/fashion-agent/blob/develop/docs/contracts/mcp-tools.md)
- [앱 ↔ 게이트웨이 API (OpenAPI)](https://github.com/fashion-agent-team/fashion-agent/blob/develop/docs/contracts/openapi.yaml)
- [아키텍처와 기술 스택](https://github.com/fashion-agent-team/fashion-agent/blob/develop/docs/ARCHITECTURE.md)

## 버전

| 항목 | 버전 |
| --- | --- |
| Java | 21 |
| Spring Boot | 4.0.9 |
| Spring Cloud | 2025.1.4 |
| Spring AI (BOM) | 2.0.2 |

## 브랜치

GitFlow를 따릅니다. `develop`에서 `feature/…` 브랜치를 따서 PR로 합칩니다. 규칙은 앱 레포의 `docs/GITFLOW.md`를 봅니다.

## 비밀 값

API 키와 DB 접속 정보는 레포에 넣지 않습니다. 로컬은 `.env`(커밋 금지), 배포는 서버 환경 변수로 둡니다.
