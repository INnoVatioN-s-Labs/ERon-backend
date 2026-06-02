# 이터널리턴 전적 검색 서비스 아키텍처 설계서

## 1. 개요 (Overview)

본 문서는 이터널 리턴(Eternal Return)의 공식 Developer API를 활용하여 유저 전적 검색, 통계 분석, 메타 분석 기능을 제공하는 웹 서비스의 아키텍처 설계 문서이다.

본 프로젝트는 단순 전적 조회 기능을 넘어 다음과 같은 기능을 포함한 서비스 수준의 구조를 목표로 한다.

- 데이터 수집
- 캐싱
- 통계 집계
- 메타 분석
- 실험체 운영 팁 시스템
- 조합 분석 시스템

또한 Dak.gg와 같이 비회원 상태에서도 전적 검색이 가능하도록 설계하며, 회원 기능은 부가 기능 제공을 위한 확장 요소로 사용한다.

---

# 2. 요구사항

## 2.1 전적 검색

- 닉네임 기반 유저 검색
- userNum 기반 내부 식별
- 최근 매치 조회
- 시즌 전적 조회
- 실험체별 전적 조회

## 2.2 통계 기능

- 승률
- 평균 순위
- KDA
- TOP3 비율
- 최근 N게임 추이
- 시즌별 통계

## 2.3 전적 상세 조회

- 참가자 정보 조회
- 팀 구성 조회
- 실험체 정보 조회
- 킬 / 데스 / 어시스트
- 딜량
- 성장 지표

## 2.4 메타 분석

- 실험체 티어
- 픽률
- 승률
- 팀 조합 통계
- 실험체 시너지 분석
- 패치별 메타 변화

## 2.5 운영 분석

단순 승률 조회를 넘어 실제 플레이 데이터를 기반으로 운영 팁을 제공한다.

### 나딘

- 평균 야성 스택
- 야성 스택 시간대
- 곰 / 늑대 사냥 수

### 쇼우

- 선호 음식
- 음식 제작 수
- 음식별 승률

### 아이솔

- 평균 함정 설치 수
- 함정 승률

### 재키

- 초반 킬 관여율
- 1일차 교전 빈도

## 2.6 회원 기능

비회원도 서비스 이용 가능

### 비회원

- 전적 검색
- 루트 조회
- 메타 조회
- 조합 조회

### 회원

- 즐겨찾기
- 최근 검색 기록
- 운영 팁 작성
- 댓글 기능
- 개인 대시보드
- 관심 실험체 등록

# 3. 기술 스택

## Frontend

- React 18
- Vite
- TailwindCSS
- Zustand
- React Query
- Recharts

## Backend

- Spring Boot 3.x
- Java 17
- Spring Security
- JWT
- Spring Data JPA
- MySQL
- Redis
- WebClient
- Scheduler
- Async
- Resilience4j

# 4. 데이터베이스 설계

## 4.1 MySQL

### user

서비스 회원

| 컬럼명 | 타입 | 설명 |
|----------|----------|----------|
| id | BIGINT | PK |
| login_id | VARCHAR | 로그인 ID |
| password | VARCHAR | 암호화 비밀번호 |
| nickname | VARCHAR | 서비스 닉네임 |
| role | VARCHAR | USER / ADMIN |
| is_deleted | TINYINT(1) | 탈퇴 여부 |
| created_at | TIMESTAMP | 생성일 |
| updated_at | TIMESTAMP | 수정일 |

### er_user

이터널리턴 유저 정보

| 컬럼명 | 타입 |
|----------|----------|
| id | BIGINT |
| user_num | INT |
| nickname | VARCHAR |
| last_updated | TIMESTAMP |

### favorite_user

즐겨찾기 유저

| 컬럼명 | 타입 |
|----------|----------|
| id | BIGINT |
| user_id | BIGINT |
| er_user_num | INT |
| created_at | TIMESTAMP |

### match

| 컬럼명 | 타입 |
|----------|----------|
| id | BIGINT |
| match_id | VARCHAR |
| started_at | TIMESTAMP |

### match_participant

| 컬럼명 | 타입 |
|----------|----------|
| id | BIGINT |
| match_id | VARCHAR |
| user_num | INT |
| character_id | INT |
| rank | INT |
| kills | INT |
| deaths | INT |
| assists | INT |
| damage | BIGINT |
| created_at | TIMESTAMP |

### character_stats

| 컬럼명 | 타입 |
|----------|----------|
| id | BIGINT |
| user_num | INT |
| character_id | INT |
| total_games | INT |
| wins | INT |
| avg_rank | FLOAT |
| updated_at | TIMESTAMP |

### user_feedback

운영 팁

| 컬럼명 | 타입 |
|----------|----------|
| id | BIGINT |
| character_id | INT |
| writer_id | BIGINT |
| content | TEXT |
| created_at | TIMESTAMP |

### character_meta

메타 통계 저장

| 컬럼명 | 타입 |
|----------|----------|
| id | BIGINT |
| character_id | INT |
| pick_rate | FLOAT |
| win_rate | FLOAT |
| avg_rank | FLOAT |
| updated_at | TIMESTAMP |

### team_combination_stats

조합 통계

| 컬럼명 | 타입 |
|----------|----------|
| id | BIGINT |
| character_a | INT |
| character_b | INT |
| character_c | INT |
| total_games | INT |
| win_rate | FLOAT |
| updated_at | TIMESTAMP |

## 4.2 Redis 설계

```text
user:{nickname}
matches:{userNum}
stats:{userNum}
meta:{characterId}
refreshToken:{userId}
rate-limit:{apiKey}
api-queue
```

# 5. 핵심 비즈니스 로직

## 5.1 유저 검색

1. 사용자 검색 요청
2. Redis 조회
3. 없으면 MySQL 조회
4. 없으면 Eternal Return API 호출
5. DB 저장
6. Redis 저장
7. 결과 반환

## 5.2 매치 데이터 수집

1. userNum 조회
2. matchId 목록 조회
3. 상세 조회
4. DB 저장
5. 중복 제거
6. 통계 갱신

## 5.3 통계 집계

- Scheduler 기반 집계
- 실험체 메타 생성
- 조합 통계 생성
- 운영 통계 생성

## 5.4 캐싱 전략

Cache Aside Pattern

## 5.5 API 호출 최적화

- Resilience4j RateLimiter
- Retry
- CircuitBreaker

## 5.6 API Queue 처리

Redis Stream 기반 Queue 적용

# 6. 시스템 아키텍처

```text
[ React Client ]
        ↓
[ Spring Boot API ]
        ↓
 ┌────────────────────┐
 │ MySQL              │
 │ Redis              │
 └────────────────────┘
        ↓
[ Eternal Return API ]
```

# 7. 차별화 포인트

1. Dak.gg 수준 전적 검색
2. 운영 데이터 분석
3. ERWAGG 스타일 조합 분석
4. 유저 참여형 팁 시스템
5. 패치 메타 분석

# 8. 추가 확장 아이디어

- 유저 비교
- 루트 추천
- 실험체 추천
- AI 기반 운영 피드백
- 최근 메타 변화
- 패치 노트 분석
- Top 플레이어 랭킹
- 관심 실험체 알림

# 9. 설계 시 고려 사항

- API Rate Limit 최소화
- Redis TTL 전략
- 통계 실시간 계산 여부
- 배치 집계 주기
- 조합 통계 저장 범위
- 운영 데이터 저장 범위
- 메타 갱신 주기

# 10. 결론

본 프로젝트는 단순 전적 검색 서비스를 넘어 전적 데이터 수집, 캐싱, 메타 분석, 운영 분석, 조합 통계 기능을 포함하는 이터널리턴 데이터 플랫폼 구축을 목표로 한다.

비회원 상태에서도 Dak.gg 수준의 전적 검색 기능을 제공하며, 회원 기능을 통해 즐겨찾기, 개인화, 커뮤니티 기능을 확장할 수 있도록 설계한다.

이를 통해 백엔드 개발 관점에서 외부 API 연동, 캐싱, 비동기 처리, 인증, 통계 집계, 데이터 모델링 경험을 모두 담을 수 있는 포트폴리오 프로젝트를 목표로 한다.
