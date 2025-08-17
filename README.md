# 🏦 Loan API - Redis 캐시 기반 대출 조회 시스템

Redis 캐시를 활용한 대출 상품 조회 및 인기 상품 추천 API입니다. 핀테크 기술 세미나를 위한 데모 프로젝트로, 캐시 전략과 실시간 데이터 처리를 구현했습니다.

## ✨ 주요 기능

- 🚀 **고성능 캐싱**: Redis를 활용한 빠른 대출 상품 조회
- 📊 **실시간 인기 상품**: 조회수 기반 실시간 순위 업데이트
- 🔄 **데이터 동기화**: Redis와 Oracle DB 간 자동 동기화
- 📈 **모니터링**: Prometheus 메트릭 수집 및 성능 모니터링
- ⚡ **스케줄링**: 정기적인 캐시 갱신 및 데이터 정리

## 🏗️ 아키텍처

```
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│   Client App    │    │   Spring Boot   │    │     Oracle DB   │
│                 │◄──►│     API         │◄──►│   (Persistent)  │
└─────────────────┘    └─────────────────┘    └─────────────────┘
                                │
                                ▼
                       ┌─────────────────┐
                       │     Redis       │
                       │   (Cache)       │
                       └─────────────────┘
```

## 🛠️ 기술 스택

- **Backend**: Spring Boot 3.5.3, Java 17
- **Database**: Oracle DB, Spring Data JPA
- **Cache**: Redis, Spring Data Redis
- **Monitoring**: Prometheus, Micrometer
- **Build Tool**: Maven
- **Utilities**: Lombok

## 📋 요구사항

- Java 17+
- Maven 3.6+
- Redis 6.0+
- Oracle Database 19c+

## 🚀 시작하기

### 애플리케이션 접속

- **애플리케이션**: http://localhost:8080
- **Actuator**: http://localhost:8080/actuator
- **Prometheus**: http://localhost:8080/actuator/prometheus

## 📡 API 엔드포인트

### Redis 캐시 기반 API (`/redis`)

| Method | Endpoint | Description |
|--------|----------|-------------|
| `GET` | `/redis/detail/{loanId}` | 대출 상품 상세 조회 (캐시 우선) |
| `POST` | `/redis/view/{loanId}` | 조회수 증가 (Redis + Oracle 동기화) |
| `GET` | `/redis/status/{loanId}` | 캐시 상태 확인 |
| `GET` | `/redis/keys` | 모든 캐시 키 조회 |
| `GET` | `/redis/popular` | Top 20 인기 상품 캐싱 |

### Oracle DB 직접 API (`/oracle`)

| Method | Endpoint | Description |
|--------|----------|-------------|
| `GET` | `/oracle/list` | 전체 대출 상품 목록 |
| `GET` | `/oracle/detail/{loanId}` | 대출 상품 상세 조회 (DB 직접) |
| `GET` | `/oracle/popular` | 인기 상품 Top N 조회 |
| `POST` | `/oracle/view/{loanId}` | 조회수 증가 (DB만) |
| `GET` | `/oracle/total-views` | 전체 조회수 합계 |

## 🔧 설정

### application.properties

```properties
# Redis 설정
spring.redis.host=localhost
spring.redis.port=6379

# Oracle DB 설정
spring.datasource.url=jdbc:oracle:thin:@localhost:1521:xe
spring.datasource.username=fisa
spring.datasource.password=fisa

# 모니터링
management.endpoints.web.exposure.include=health,info,prometheus
management.metrics.export.prometheus.enabled=true
```

## 📊 데이터 모델

### Loan Entity
```java
@Entity
@Table(name = "LOAN_PRODUCTS")
public class Loan {
    private Long id;
    private String productName;
    private String bank;
    private String jobType;
    private String purpose;
    private String rateType;
    private String interestRate;
    private int maxLimit;
    private int periodMonths;
}
```

### LoanView Entity
```java
@Entity
@Table(name = "loan_views")
public class LoanView {
    private Long loanId;
    private Long viewCount;
}
```

## ⏰ 스케줄러

### LoanScheduler
- **1분마다** 인기 상품 Top 20 캐싱 갱신
- 주간/야간 시간대별 차등 처리

### RedisViewSyncScheduler
- **1분마다** Redis → Oracle DB 동기화
- 조회수 감쇠 처리 (감쇠 계수: 0.85)
- 낮은 점수 데이터 자동 삭제

## 🎯 캐시 전략

### Cache-Aside Pattern
- Redis에 데이터가 없으면 DB에서 조회 후 캐싱
- TTL: 2분 설정으로 최신성 보장

### Write-Through Pattern
- 조회수 증가 시 Redis와 Oracle DB 동시 업데이트
- 데이터 일관성 유지

### ZSet 기반 인기 상품
- Redis Sorted Set을 활용한 실시간 순위 관리
- 조회수를 점수로 사용하여 자동 정렬

## 📈 모니터링

### Prometheus 메트릭
- `loan_cache_hit`: 캐시 히트 횟수
- `loan_cache_miss`: 캐시 미스 횟수

### Actuator 엔드포인트
- `/actuator/health`: 애플리케이션 상태
- `/actuator/info`: 애플리케이션 정보
- `/actuator/prometheus`: Prometheus 메트릭

## 🔍 성능 특징

- **캐시 히트율**: Redis 캐싱으로 응답 속도 향상
- **실시간 순위**: 조회수 기반 실시간 인기 상품 업데이트
- **데이터 동기화**: Redis와 Oracle DB 간 자동 동기화
- **자동 정리**: 낮은 인기도 상품 자동 제거

## 📊 성능 테스트 결과

### 🚀 Redis vs Oracle DB 성능 비교

JMeter를 사용하여 4,500건의 요청을 대상으로 성능 테스트를 진행했습니다.

| 항목 | Oracle DB | Redis Cache | 개선율 |
|------|-----------|-------------|--------|
| **평균 응답 시간** | 15ms | 5ms | **3배 향상** |
| **초당 수신 데이터량** | 25 | 15 | **40% 감소** |
| **초당 요청 처리량** | 동일 | 동일 | - |

**결론**: Redis 캐싱으로 응답 속도가 3배 향상되고, 네트워크 부하도 감소하여 더 많은 TPS 처리가 가능합니다.

> 📸 JMeter 성능 테스트 결과 화면 (Redis vs Oracle DB 비교)
<img width="1357" height="469" alt="image" src="https://github.com/user-attachments/assets/b2decf8c-cd9e-465f-b512-f3537bc79cac" />
<img width="1299" height="427" alt="image" src="https://github.com/user-attachments/assets/57ed23fb-dd90-47db-8fed-4b37d5278e88" />

### 🎯 캐시 전략별 성능 분석

#### **TTL 전략**
- **장점**: 전체적으로 높은 캐시 히트율
- **단점**: 메모리 사용량 불규칙, 정렬 불가, 메모리 한계
- **적합한 경우**: 날씨 API, 단기 데이터 등 유효기간이 명확한 데이터

#### **단순 갱신 전략**
- **장점**: 캐시 연산 부하 낮음, 안정적인 메모리 사용
- **단점**: 최신 트렌드 반영이 상대적으로 느림
- **적합한 경우**: 누적 조회수 기반의 안정적인 인기 상품

#### **갱신 + 감쇠 전략** ⭐ **최적화된 전략**
- **장점**: 인기 변동 민감, 고착화 방지, 빠른 캐시 히트 복구
- **단점**: 상대적으로 높은 메모리 점유율
- **적합한 경우**: 데이터가 많고, 인기 상품이 빠르게 자주 바뀌는 상황

### 📈 감쇠 계수 최적화

여러 감쇠치 값을 테스트한 결과, **0.85**가 가장 이상적인 캐시 히트율을 보입니다.

```java
private static final double DECAY_FACTOR = 0.85;   // 최적화된 감쇠 계수
```

### 🔄 트렌드 변화 시나리오 테스트

**테스트 시나리오**: 초반에는 상위 인기 상품에 트래픽 집중 → 후반에는 하위권 상품 집중 조회

**결과**: 
- **초반**: 높은 캐시 히트율 유지
- **중반**: 트렌드 변화로 인한 캐시 히트율 일시적 감소
- **후반**: 감쇠 + 갱신 전략으로 빠른 캐시 히트율 복구

> 📸 트렌드 변화 시나리오 테스트 결과 그래프 (캐시 히트율 변화)
<img width="1060" height="519" alt="image" src="https://github.com/user-attachments/assets/5dd29851-99cc-43fb-a487-dac6a932c5a3" />

### 💾 메모리 사용량 분석

- **TTL**: 초반 높은 메모리 사용량, 중후반 안정화
- **단순 갱신**: 주기적 메모리 변동 패턴
- **갱신 + 감쇠**: 빠른 변동 주기, 상대적으로 높은 메모리 점유율

> 📸 메모리 사용량 분석 그래프 (각 전략별 비교)
<img width="1058" height="517" alt="image" src="https://github.com/user-attachments/assets/ab8c97c4-41f5-4ce9-8585-d1dc85572603" />

### ⚡ 초당 명령어 처리량

- **갱신 시점**: 3분마다 주기적 피크 발생
- **감쇠 시점**: 작은 피크 발생
- **TTL**: 초반 높은 처리율, 키 감소에 따른 처리율 감소

> 📸 초당 명령어 처리량 그래프 (각 전략별 비교)
<img width="1055" height="513" alt="image" src="https://github.com/user-attachments/assets/cf6c68d6-9f54-4c56-8c56-609a674a4976" />

## 🎯 핵심 문제 해결

### 문제 상황
**초반에는 상품 A인 상위 인기 상품에 트래픽이 몰리다가, 후반에는 B, C같은 하위권 상품이 집중적으로 조회되는 상황**

### 문제점
- 순위권 외 급격히 인기가 생긴 트렌드 상품 조회 시 캐시 미스 발생
- 과거에 인기가 높았던 상품이 Redis에 계속 캐싱되어 최신 인기상품 반영 불가
- **고착화 문제** 발생

### 해결책: 감쇠 + 인기순위 갱신 전략
- **감쇠 계수 0.85** 적용으로 최적 캐시 히트율 달성
- 스케줄러를 통한 주기적 실행으로 ZSet 인기 상품 순위에 최신성 반영
- 트렌드 변화에 민감하게 대응

## 🔍 시스템 설계 고려사항

### 파레토 법칙 적용
- 20%의 원인이 80%의 결과를 만들어내는 파레토 법칙 참고
- 조회수 기반으로 상위 20위에 드는 상품만 캐싱하여 효율성 극대화

### 메모리 관리
- 인메모리 특성상 메모리 한계 고려
- 낮은 인기도 상품 자동 제거로 메모리 효율성 확보

### 데이터 일관성
- Redis와 Oracle DB 간 주기적 동기화
- Cache-Aside와 Write-Through 패턴 조합으로 일관성 유지





---

**핀테크 기술 세미나** - Redis 캐시 기반 대출 조회 시스템 데모
