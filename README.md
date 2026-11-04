# mcp-schedule

**schedule MCP 서버 · 일정과 장소 종류** · 패션 에이전트(시각장애인용 음성 AI 옷 추천)의 한 부분입니다.

담당: (미정)

## 한눈에

- 포트: 8092, 엔드포인트: `http://mcp-schedule:8092/mcp` (Streamable HTTP)
- 기술: Spring Boot, Spring AI MCP Server, 카카오 로컬 API(장소 종류), FCM(당일 알림), `@Scheduled`, Spring Data JPA, PostgreSQL
- 상태 확인: `GET /actuator/health`

## MCP 도구

| 도구 | 하는 일 |
| --- | --- |
| `schedule_list` | 날짜별 일정 + 장소 종류(placeCategory) + 자리 종류(occasion). 옷 추천 1단계에서 오늘 날짜로 불림 |
| `schedule_add` | 일정 추가. 장소 이름을 카카오 로컬로 찾아 occasion 결정 |
| `schedule_delete` | 일정 삭제 |

입출력 JSON은 [MCP 도구 입출력](https://github.com/fashion-agent-team/fashion-agent/blob/develop/docs/contracts/mcp-tools.md)를 그대로 따릅니다.

## 이 레포가 지킬 것

- **데이터만 돌려줍니다.** 이 자리에 어떤 옷이 맞는지는 Gemini가 판단합니다.
- `occasion`: `mourning`(장례·조문) `wedding`(결혼식) `formal`(면접·발표·상견례) `business`(회의·출근·수업) `active`(운동·등산) `casual`(그 밖). 앱 레포 `lib/styling/schedule_rules.dart`의 키워드 표를 옮깁니다.
- 일정 당일 아침 8시 FCM 알림(도구가 아니라 스케줄러).
- 앱이 직접 보는 `GET /api/v1/schedules?date=`도 엽니다(게이트웨이 경유).
- DB 스키마 `schedule`만 씁니다(`events`).

## 할 일

- [ ] 뼈대 + `/mcp` + Dockerfile
- [ ] `events` 테이블, `schedule_add`·`schedule_list`·`schedule_delete`
- [ ] 제목 키워드로 occasion 정하기 → 카카오 로컬로 장소 종류 보강
- [ ] 당일 알림 (FCM)

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

API 키와 DB 비밀번호는 레포에 넣지 않습니다. 로컬은 `.env`(커밋 금지), 배포는 서버 환경 변수로 둡니다. 목록은 앱 레포 `docs/infra/.env.example`에 있습니다.
