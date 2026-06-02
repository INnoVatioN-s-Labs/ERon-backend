# Eron Backend Change Log

이 문서는 이터널리턴 전적 검색 서비스 백엔드 작업 이력을 기록한다.

설계 방향과 장기 아키텍처는 `src/ER_Architecture.md`에 두고, 이 파일에는 실제로 추가하거나 수정한 구현 내용을 날짜별로 정리한다.

---

## 2026-06-01

### 작업 요약

- Spring Boot 백엔드의 공식 Eternal Return API 연동 기본 구조를 추가했다.
- API key를 코드에 직접 넣지 않고 환경변수 `ER_API_KEY`로 주입받도록 설정했다.
- 로컬 API key 관리를 위해 `.env`를 Git 추적에서 제외하고, 예시 파일 `.env.example`을 추가했다.
- 프론트엔드가 붙을 수 있는 기본 REST API 엔드포인트를 추가했다.
- 외부 API 호출 실패와 잘못된 요청을 JSON 에러 응답으로 변환하는 공통 예외 처리를 추가했다.
- API 클라이언트와 컨트롤러 단위 테스트를 추가했다.

### 수정한 설정

#### `build.gradle`

- `spring-boot-starter-web` 의존성을 추가했다.
- `spring-boot-configuration-processor` annotation processor를 추가했다.
- 테스트는 기존처럼 JUnit Platform을 사용한다.

#### `src/main/resources/application.properties`

다음 Eternal Return API 설정을 추가했다.

```properties
eternal-return.api.base-url=https://open-api.bser.io/v1
eternal-return.api.key=${ER_API_KEY:}
eternal-return.api.connect-timeout=3s
eternal-return.api.read-timeout=10s
```

API key는 실행 전에 환경변수로 설정해야 한다.

```bash
export ER_API_KEY=발급받은_공식_API_KEY
./gradlew bootRun
```

로컬에서는 `.env` 파일을 만들어 다음처럼 둘 수 있다. `.env`는 `.gitignore`에 포함되어 GitHub에 올라가지 않는다.

```properties
ER_API_KEY=발급받은_공식_API_KEY
```

실행할 때는 `.env`를 shell 환경변수로 로드한 뒤 Spring Boot를 실행한다.

```bash
source .env
./gradlew bootRun
```

`.env.example`에는 실제 key 없이 필요한 변수 이름만 기록했다.

#### `.gitignore`

- `.env`
- `.env.*`
- `!.env.example`

위 규칙을 추가했다.

### 추가한 백엔드 코드

#### `src/main/java/com/toyproject/eron/global/config/EternalReturnApiProperties.java`

- Eternal Return API 설정 값을 바인딩하는 설정 클래스다.
- 관리하는 값:
  - `baseUrl`
  - `key`
  - `connectTimeout`
  - `readTimeout`

#### `src/main/java/com/toyproject/eron/global/config/EternalReturnApiConfig.java`

- Eternal Return API 호출에 사용할 `RestClient` Bean을 생성한다.
- base URL, timeout, `x-api-key` 기본 헤더를 설정한다.

#### `src/main/java/com/toyproject/eron/erapi/EternalReturnApiClient.java`

- 공식 Eternal Return API를 호출하는 클라이언트다.
- 현재 지원하는 호출:
  - 닉네임 기반 유저 검색
  - 유저 시즌 통계 조회
  - 유저 최근 게임 조회
  - 게임 상세 조회
  - 데이터 테이블 조회
- `ER_API_KEY`가 비어 있으면 외부 API 호출 전에 서버 에러로 차단한다.
- 공식 API HTTP 에러와 timeout을 `EternalReturnApiException`으로 변환한다.
- 원본 API 응답은 `Map<String, Object>` 형태로 유지한다.

#### `src/main/java/com/toyproject/eron/erapi/EternalReturnController.java`

- 클라이언트를 감싸는 백엔드 REST API 컨트롤러다.
- 현재 제공하는 엔드포인트:

```text
GET /api/er/users/search?nickname={nickname}
GET /api/er/users/{userNum}/stats?seasonId={seasonId}
GET /api/er/users/{userNum}/games
GET /api/er/games/{gameId}
GET /api/er/data/{metaType}
```

#### `src/main/java/com/toyproject/eron/erapi/dto/UserSearchResponse.java`

- 닉네임 검색 결과를 프론트에서 바로 쓰기 쉽게 정리한 응답 DTO다.
- 포함 값:
  - `userNum`
  - `nickname`
  - `raw`

#### `src/main/java/com/toyproject/eron/erapi/EternalReturnApiException.java`

- Eternal Return API 연동 중 발생한 문제를 표현하는 커스텀 런타임 예외다.
- HTTP status를 함께 보관한다.

#### `src/main/java/com/toyproject/eron/global/error/ApiErrorResponse.java`

- 공통 JSON 에러 응답 DTO다.
- 포함 값:
  - `timestamp`
  - `status`
  - `error`
  - `message`

#### `src/main/java/com/toyproject/eron/global/error/GlobalExceptionHandler.java`

- `EternalReturnApiException`을 JSON 에러 응답으로 변환한다.
- 필수 request parameter 누락도 `400 Bad Request` JSON 응답으로 변환한다.

### 추가한 테스트

#### `src/test/java/com/toyproject/eron/erapi/EternalReturnApiClientTest.java`

외부 네트워크를 사용하지 않고 로컬 테스트 서버로 API 클라이언트를 검증한다.

검증한 내용:

- 닉네임 검색 응답을 `UserSearchResponse`로 매핑한다.
- 공식 API 호출 시 `x-api-key` 헤더를 전달한다.
- 닉네임 검색 응답에 `user`가 없으면 `404 Not Found` 예외로 처리한다.
- 공식 API의 HTTP 에러 응답을 `EternalReturnApiException`으로 변환한다.
- `ER_API_KEY`가 비어 있으면 외부 API 호출 전에 에러를 발생시킨다.

#### `src/test/java/com/toyproject/eron/erapi/EternalReturnControllerTest.java`

`MockMvc` standalone 방식으로 컨트롤러를 검증한다.

검증한 내용:

- `/api/er/users/search`가 정상 응답을 반환한다.
- `EternalReturnApiException`이 구조화된 JSON 에러 응답으로 변환된다.
- 필수 query parameter 누락 시 `400 Bad Request`를 반환한다.

### 검증 결과

다음 명령으로 전체 테스트를 통과했다.

```bash
./gradlew test
```

결과:

```text
BUILD SUCCESSFUL
```

### 현재 의도적으로 하지 않은 것

- MySQL/JPA Entity 설계는 아직 추가하지 않았다.
- Redis cache layer는 아직 추가하지 않았다.
- Spring Security/JWT는 아직 추가하지 않았다.
- Resilience4j rate limit/retry/circuit breaker는 아직 추가하지 않았다.
- 공식 API 응답을 도메인별 DTO로 완전히 정규화하지 않았다.

현재 단계에서는 공식 API 연동을 얇게 세우고, 프론트엔드나 후속 백엔드 계층이 붙을 수 있는 최소 조회 API를 만드는 데 집중했다.

### 다음 작업 후보

- 유저 검색 결과를 DB에 저장할 `er_user` Entity와 Repository 추가
- 최근 게임 조회 결과를 캐싱하는 service layer 추가
- 공식 API 응답 구조를 기준으로 match/game DTO 정리
- Redis 없이 먼저 in-memory cache 또는 DB cache-aside 흐름 구현
- 실제 API key로 닉네임 검색 통합 테스트 수동 검증
- API 호출 실패 케이스별 에러 메시지 정리

### 추가 로컬 실행 스크립트

`.env`를 매번 직접 `source`하지 않아도 되도록 루트에 실행 스크립트를 추가했다.

#### `run-local.sh`

- `.env` 파일을 읽는다.
- `ER_API_KEY`가 비어 있으면 실행을 중단한다.
- 정상인 경우 `./gradlew bootRun`으로 서버를 실행한다.

사용법:

```bash
./run-local.sh
```

#### `test-er-api.sh`

- 서버가 켜진 상태에서 닉네임 검색 API를 간단히 호출한다.

사용법:

```bash
./test-er-api.sh 검색할닉네임
```
