# API Bridge System Management Service - API 명세서

## 개요

API Bridge System Management Service는 외부 API 모니터링, 헬스체크, 알림 관리, 서킷브레이커 제어 등을 담당하는 시스템 관리 서비스입니다.

**Base URL**: `http://localhost:8080/api/v1`

## ⚠️ 중요 사항
- 많은 엔드포인트가 **POST** 메서드를 사용합니다
- **GET** 메서드로 호출하면 405 Method Not Allowed 오류가 발생합니다
- 올바른 HTTP 메서드를 사용해야 합니다

## 목차

1. [시스템 헬스체크](#1-시스템-헬스체크)
2. [대시보드 및 분석](#2-대시보드-및-분석)
3. [외부 API 헬스체크](#3-외부-api-헬스체크)
4. [API 헬스 관리](#4-api-헬스-관리)
5. [서킷브레이커 관리](#5-서킷브레이커-관리)
6. [알림 관리](#6-알림-관리)
7. [공통 응답 형식](#7-공통-응답-형식)
8. [에러 코드](#8-에러-코드)

---

## 1. 시스템 헬스체크

시스템 전체 상태를 확인하는 API

### 1.1 시스템 헬스체크

**GET** `/health`

시스템의 전반적인 상태를 확인합니다.

#### 응답

```json
{
  "success": true,
  "message": "Service is healthy",
  "data": {
    "service": "systemmanagement-svc",
    "javaVersion": "17.0.13",
    "javaVendor": "Amazon.com Inc.",
    "version": "dev",
    "status": "UP",
    "timestamp": "2025-08-13T14:14:35.070908"
  },
  "timestamp": "2025-08-13T14:14:35.071263"
}
```

---

## 2. 대시보드 및 분석

ELK 스택 기반의 시스템 분석 및 모니터링 대시보드 API

### 2.1 통합 대시보드 분석 데이터 조회

**GET** `/dashboard/analytics`

#### 파라미터

| 파라미터 | 타입 | 필수 | 기본값 | 설명 |
|---------|------|------|--------|------|
| hours | Integer | No | 24 | 분석할 시간 범위 (시간 단위) |
| limit | Integer | No | 10 | 순위별 반환할 최대 개수 |

#### 응답

```json
{
  "success": true,
  "message": "대시보드 분석 데이터 조회 성공",
  "data": {
    "errorRanking": [],
    "apiCallRanking": [],
    "summary": {
      "totalErrors": 0,
      "totalApiCalls": 0,
      "overallSuccessRate": 0.0,
      "activeServiceCount": 0,
      "monitoredApiCount": 0,
      "systemStatus": "CRITICAL"
    },
    "analysisPeriod": {
      "startTime": "2025-08-12T14:15:43",
      "endTime": "2025-08-13T14:15:43",
      "durationHours": 24,
      "description": "최근 24시간"
    }
  },
  "timestamp": "2025-08-13T14:15:43.292163"
}
```

### 2.2 서비스별 에러 발생 순위 조회

**GET** `/dashboard/errors/ranking`

#### 파라미터

| 파라미터 | 타입 | 필수 | 기본값 | 설명 |
|---------|------|------|--------|------|
| hours | Integer | No | 24 | 분석할 시간 범위 (시간 단위) |
| limit | Integer | No | 10 | 반환할 최대 개수 |

#### 응답

```json
{
  "success": true,
  "message": "서비스별 에러 순위 조회 성공",
  "data": [],
  "timestamp": "2025-08-13T14:15:43.292163"
}
```

### 2.3 외부 API 호출 순위 조회

**GET** `/dashboard/api-calls/ranking`

#### 파라미터

| 파라미터 | 타입 | 필수 | 기본값 | 설명 |
|---------|------|------|--------|------|
| hours | Integer | No | 24 | 분석할 시간 범위 (시간 단위) |
| limit | Integer | No | 10 | 반환할 최대 개수 |

#### 응답

```json
{
  "success": true,
  "message": "외부 API 호출 순위 조회 성공",
  "data": [],
  "timestamp": "2025-08-13T14:15:43.292163"
}
```

### 2.4 대시보드 시스템 헬스체크

**GET** `/dashboard/health`

```json
{
  "success": true,
  "message": "대시보드 서비스 정상 동작",
  "data": "OK",
  "timestamp": "2025-08-13T14:17:24.422612"
}
```

---

## 3. 외부 API 헬스체크

외부 API의 헬스체크 실행 및 결과 관리 API

### 3.1 전체 API 헬스체크 실행

**POST** `/health-check/run`

등록된 모든 외부 API에 대해 병렬 헬스체크를 수행합니다.

#### 응답

```json
{
  "success": true,
  "message": "헬스체크 완료: 0개 API 중 0개 정상",
  "data": {
    "totalApis": 0,
    "healthyApis": 0,
    "unhealthyApis": 0,
    "healthRate": 0,
    "averageResponseTime": 0,
    "executionTime": "2025-08-13T14:17:24.422612",
    "results": {}
  },
  "timestamp": "2025-08-13T14:17:24.422612"
}
```

### 3.2 특정 API 헬스체크 실행

**POST** `/health-check/run/{apiId}`

#### 경로 파라미터

| 파라미터 | 타입 | 필수 | 설명 |
|---------|------|------|------|
| apiId | String | Yes | 헬스체크를 수행할 API ID |

#### 응답

```json
{
  "success": true,
  "message": "헬스체크 완료: 정상",
  "data": {
    "checkId": "uuid",
    "apiId": "api-001",
    "status": "HEALTHY",
    "responseTimeMs": 150,
    "checkedAt": "2025-08-13T14:17:24.422612",
    "httpStatusCode": 200,
    "errorMessage": null
  },
  "timestamp": "2025-08-13T14:17:24.422612"
}
```

### 3.3 헬스체크 결과 조회

**GET** `/health-check/results`

#### 파라미터

| 파라미터 | 타입 | 필수 | 기본값 | 설명 |
|---------|------|------|--------|------|
| hours | Integer | No | 24 | 조회할 시간 범위 (시간 단위) |
| page | Integer | No | 0 | 페이지 번호 (0부터 시작) |
| size | Integer | No | 20 | 페이지 크기 |
| sortBy | String | No | checkedAt | 정렬 기준 |
| sortDirection | String | No | desc | 정렬 방향 (asc, desc) |

#### 응답

```json
{
  "success": true,
  "message": "헬스체크 결과 조회 성공: 0건",
  "data": {
    "content": [],
    "pageable": {
      "pageNumber": 0,
      "pageSize": 20
    },
    "totalElements": 0,
    "totalPages": 0
  },
  "timestamp": "2025-08-13T14:17:24.422612"
}
```

### 3.4 현재 비정상 API 목록 조회

**GET** `/health-check/unhealthy`

```json
{
  "success": true,
  "message": "현재 비정상 API: 0개",
  "data": [],
  "timestamp": "2025-08-13T14:17:24.422612"
}
```

### 3.5 헬스체크 통계 조회

**GET** `/health-check/statistics`

#### 파라미터

| 파라미터 | 타입 | 필수 | 기본값 | 설명 |
|---------|------|------|--------|------|
| hours | Integer | No | 24 | 통계 조회 기간 (시간 단위) |

#### 응답

```json
{
  "success": true,
  "message": "헬스체크 통계 조회 성공",
  "data": {
    "period": "24 hours",
    "since": "2025-08-12T14:17:24.422612",
    "successRateByApi": [],
    "averageResponseTimeByApi": [],
    "overallStatistics": {}
  },
  "timestamp": "2025-08-13T14:17:24.422612"
}
```

### 3.6 특정 API 헬스체크 이력 조회

**GET** `/health-check/history/{apiId}`

#### 경로 파라미터

| 파라미터 | 타입 | 필수 | 설명 |
|---------|------|------|------|
| apiId | String | Yes | 조회할 API ID |

#### 파라미터

| 파라미터 | 타입 | 필수 | 기본값 | 설명 |
|---------|------|------|--------|------|
| limit | Integer | No | 50 | 조회할 결과 수 |

### 3.7 등록된 API 목록 조회

**GET** `/health-check/apis`

#### 파라미터

| 파라미터 | 타입 | 필수 | 기본값 | 설명 |
|---------|------|------|--------|------|
| activeOnly | Boolean | No | true | 활성화된 API만 조회 여부 |

---

## 4. API 헬스 관리

고급 API 헬스 모니터링 및 관리 기능

### 4.1 모든 API 상태 조회

**GET** `/health/status`

Redis 캐시에서 모든 API의 현재 상태를 조회합니다.

#### 응답

```json
{
  "success": true,
  "message": "API 상태 조회 성공",
  "data": {
    "summary": {
      "totalApis": 0,
      "healthyApis": 0,
      "unhealthyApis": 0,
      "degradedApis": 0,
      "unknownApis": 0,
      "healthRate": 100.0
    },
    "apiStates": {},
    "lastUpdated": "2025-08-13T14:17:24.422612"
  },
  "timestamp": "2025-08-13T14:17:24.422612"
}
```

### 4.2 특정 API 상태 조회

**GET** `/health/status/{apiId}`

#### 경로 파라미터

| 파라미터 | 타입 | 필수 | 설명 |
|---------|------|------|------|
| apiId | String | Yes | 조회할 API ID |

### 4.3 API 강제 상태 설정

**PUT** `/health/status/{apiId}`

#### 경로 파라미터

| 파라미터 | 타입 | 필수 | 설명 |
|---------|------|------|------|
| apiId | String | Yes | 상태를 설정할 API ID |

#### 요청 바디

```json
{
  "status": "UNHEALTHY",
  "reason": "Manual maintenance mode",
  "ttlSeconds": 3600
}
```

### 4.4 우선순위 기반 헬스체크 실행

**POST** `/health/check/priority`

#### 파라미터

| 파라미터 | 타입 | 필수 | 기본값 | 설명 |
|---------|------|------|--------|------|
| maxConcurrency | Integer | No | 10 | 최대 동시 실행 수 |
| priorityThreshold | Integer | No | 1 | 우선순위 임계값 |

### 4.5 배치 API 상태 업데이트

**POST** `/health/batch-update`

#### 요청 바디

```json
{
  "updates": [
    {
      "apiId": "api-001",
      "status": "HEALTHY",
      "responseTime": 150,
      "reason": "Auto health check"
    }
  ]
}
```

### 4.6 헬스체크 설정 조회

**GET** `/health/config`

### 4.7 헬스체크 설정 업데이트

**PUT** `/health/config`

---

## 5. 서킷브레이커 관리

API 서킷브레이커 상태 관리 및 모니터링 API

### 5.1 모든 서킷브레이커 상태 조회

**GET** `/circuit-breaker/status`

```json
{
  "success": true,
  "message": "서킷브레이커 상태 조회 성공: 총 0개 API",
  "data": {
    "healthRate": 100.0,
    "totalApis": 0,
    "states": {},
    "healthyCount": 0,
    "degradedCount": 0,
    "openCount": 0,
    "lastUpdated": "2025-08-13T14:17:12.051689"
  },
  "timestamp": "2025-08-13T14:17:12.051783"
}
```

### 5.2 서킷브레이커 수동 제어

**POST** `/circuit-breaker/control`

#### 파라미터

| 파라미터 | 타입 | 필수 | 설명 |
|---------|------|------|------|
| apiId | String | Yes | API ID |
| apiName | String | Yes | API 이름 |
| apiProvider | String | Yes | API 제공업체 |
| forcedState | String | Yes | 강제 설정할 상태 (CLOSED, OPEN, HALF_OPEN, FORCE_OPEN, DEGRADED) |
| reason | String | Yes | 변경 사유 |

#### 응답

```json
{
  "success": true,
  "message": "API 'test-api'의 서킷브레이커 상태를 'CLOSED'로 변경했습니다.",
  "data": "CLOSED",
  "timestamp": "2025-08-13T14:17:12.051783"
}
```

### 5.3 서킷브레이커 전체 리셋

**POST** `/circuit-breaker/reset-all`

#### 파라미터

| 파라미터 | 타입 | 필수 | 설명 |
|---------|------|------|------|
| reason | String | Yes | 리셋 사유 |

### 5.4 서킷브레이커 모니터링 수동 실행

**POST** `/circuit-breaker/monitor/trigger`

```json
{
  "success": true,
  "message": "서킷브레이커 모니터링이 시작되었습니다. 결과는 로그에서 확인하세요.",
  "data": "monitoring_started",
  "timestamp": "2025-08-13T14:17:12.051783"
}
```

### 5.5 서킷브레이커 설정 정보 조회

**GET** `/circuit-breaker/config`

```json
{
  "success": true,
  "message": "서킷브레이커 설정 정보 조회 성공",
  "data": {
    "monitoringWindow": "5 minutes",
    "responseTimeThreshold": "5000ms",
    "autoRecoveryTime": "5 minutes",
    "monitoringInterval": "1 minute",
    "consecutiveFailuresThreshold": 5,
    "callRateThreshold": "1000 calls/minute",
    "supportedStates": [
      "CLOSED",
      "OPEN",
      "HALF_OPEN",
      "FORCE_OPEN",
      "DEGRADED"
    ],
    "failureRateThreshold": "50.0%"
  },
  "timestamp": "2025-08-13T14:17:24.422612"
}
```

### 5.6 서킷브레이커 통계 조회

**GET** `/circuit-breaker/statistics`

#### 파라미터

| 파라미터 | 타입 | 필수 | 기본값 | 설명 |
|---------|------|------|--------|------|
| hours | Integer | No | 24 | 조회 기간 (시간 단위) |

---

## 6. 알림 관리

Prometheus Alertmanager 연동 및 알림 처리 API

### 6.1 Prometheus Alertmanager Webhook 수신

**POST** `/alerts/webhook`

#### 요청 바디

```json
{
  "groupKey": "alert-group-001",
  "status": "firing",
  "receiver": "webhook",
  "commonLabels": {
    "alertname": "HighErrorRate",
    "severity": "critical"
  },
  "alerts": [
    {
      "status": "firing",
      "labels": {
        "alertname": "HighErrorRate",
        "instance": "localhost:8080",
        "job": "api-bridge"
      },
      "annotations": {
        "summary": "높은 에러율 감지",
        "description": "API 에러율이 임계치를 초과했습니다"
      },
      "startsAt": "2025-08-13T14:17:24.422612"
    }
  ]
}
```

#### 응답

```json
{
  "success": true,
  "message": "알림이 성공적으로 처리되었습니다",
  "data": {
    "success": true,
    "alertId": "alert-group-001",
    "processedAt": "2025-08-13T14:17:24.422612",
    "processingTimeMs": 150,
    "emailsSent": 1,
    "retryRequested": false
  },
  "timestamp": "2025-08-13T14:17:24.422612"
}
```

### 6.2 알림 설정 테스트

**POST** `/alerts/test`

#### 파라미터

| 파라미터 | 타입 | 필수 | 기본값 | 설명 |
|---------|------|------|--------|------|
| severity | String | No | warning | 테스트 알림 타입 (error, warning, info) |
| message | String | No | 시스템 테스트 알림입니다 | 테스트 메시지 |

#### 요청 예시
```bash
curl -X POST "http://localhost:8080/api/v1/alerts/test?severity=warning&message=test"
```

#### 응답
```json
{
  "success": true,
  "message": "테스트 알림이 발송되었습니다",
  "data": {
    "success": true,
    "message": "Alert notification processed successfully",
    "alertId": "test-alert-1755064290788",
    "processedAt": "2025-08-13T14:51:30.790297",
    "notificationChannels": ["email"],
    "recipientCount": 3,
    "processingTimeMs": null,
    "errorDetails": null,
    "retryRequested": false
  },
  "timestamp": "2025-08-13T14:51:30.790376"
}
```

### 6.3 알림 상태 확인

**GET** `/alerts/status`

#### 응답
```json
{
  "success": true,
  "message": "알림 시스템 상태 조회 성공",
  "data": {
    "slackEnabled": false,
    "teamsEnabled": false,
    "alertingEnabled": true,
    "supportedChannels": ["email"],
    "emailEnabled": true,
    "supportedSeverities": ["critical", "warning", "info"],
    "lastHealthCheck": "2025-08-13T14:51:36.647159"
  },
  "timestamp": "2025-08-13T14:51:36.647185"
}
```

---

## 7. 공통 응답 형식

모든 API는 다음과 같은 공통 응답 형식을 사용합니다:

```json
{
  "success": true,
  "message": "요청 처리 결과 메시지",
  "data": {}, // 실제 응답 데이터
  "timestamp": "2025-08-13T14:17:24.422612"
}
```

### 응답 필드 설명

| 필드 | 타입 | 설명 |
|-----|------|------|
| success | Boolean | 요청 성공 여부 |
| message | String | 응답 메시지 (한국어) |
| data | Object | 실제 응답 데이터 |
| timestamp | String | 응답 생성 시간 (ISO 8601 형식) |

---

## 8. 에러 코드

### HTTP 상태 코드

| 상태 코드 | 설명 |
|----------|------|
| 200 | 성공 |
| 400 | 잘못된 요청 |
| 404 | 리소스를 찾을 수 없음 |
| 500 | 서버 내부 오류 |

### 비즈니스 에러 코드

에러 발생 시 `success: false`와 함께 다음과 같은 형식으로 응답합니다:

```json
{
  "success": false,
  "message": "에러 메시지",
  "data": null,
  "timestamp": "2025-08-13T14:17:24.422612"
}
```

---

## 9. Actuator 엔드포인트

Spring Boot Actuator를 통해 제공되는 모니터링 엔드포인트

### 9.1 헬스체크

**GET** `/actuator/health`

### 9.2 메트릭

**GET** `/actuator/metrics`

사용 가능한 Prometheus 메트릭 목록을 반환합니다.

#### 주요 메트릭

- `apibridge_alerts_processed_total`: 처리된 알림 총 수
- `apibridge_emails_sent_total`: 발송된 이메일 총 수
- `apibridge_health_checks_total`: 수행된 헬스체크 총 수
- `apibridge_health_checks_failed_total`: 실패한 헬스체크 총 수
- `apibridge_system_health_status`: 시스템 전체 헬스 상태

### 9.3 특정 메트릭 조회

**GET** `/actuator/metrics/{metricName}`

---

## 10. 개발 환경 설정

### 10.1 필수 구성요소

- Java 17+
- Spring Boot 3.5.4
- Redis (헬스 상태 캐싱용)
- H2 Database (개발용)

### 10.2 선택적 구성요소

- Elasticsearch (프로덕션 환경, 로그 분석용)
- Prometheus (메트릭 수집용)
- Grafana (대시보드용)

### 10.3 환경별 설정

개발 환경에서는 Elasticsearch 없이도 모든 기능이 정상 동작하도록 설계되어 있습니다.

---

## 11. 인증 및 보안

현재 버전은 개발 환경용으로 기본적인 Spring Security 설정만 적용되어 있습니다.
프로덕션 환경에서는 추가적인 인증 및 권한 관리가 필요합니다.

---

## 12. 빠른 테스트 가이드

### 동작하는 주요 엔드포인트들

```bash
# 시스템 헬스체크 
curl http://localhost:8080/api/v1/health

# 대시보드 헬스체크
curl http://localhost:8080/api/v1/dashboard/health

# 대시보드 분석 데이터
curl http://localhost:8080/api/v1/dashboard/analytics

# 서킷브레이커 상태 조회
curl http://localhost:8080/api/v1/circuit-breaker/status

# 서킷브레이커 설정 조회  
curl http://localhost:8080/api/v1/circuit-breaker/config

# 헬스체크 실행 (POST 메서드 필수!)
curl -X POST http://localhost:8080/api/v1/health-check/run

# 알림 테스트 (POST 메서드 필수!)
curl -X POST "http://localhost:8080/api/v1/alerts/test?severity=warning&message=test"

# 알림 상태 확인
curl http://localhost:8080/api/v1/alerts/status

# Actuator 메트릭 목록
curl http://localhost:8080/api/v1/actuator/metrics

# Actuator 헬스체크
curl http://localhost:8080/api/v1/actuator/health
```

### 주의사항

1. **POST 메서드가 필요한 엔드포인트들**:
   - `/health-check/run`
   - `/alerts/test`
   - `/alerts/webhook`
   - `/circuit-breaker/control`
   - `/circuit-breaker/reset-all`
   - `/circuit-breaker/monitor/trigger`

2. **GET 메서드로 호출 시 405 오류 발생**:
   ```json
   {
     "status": 405,
     "error": "Method Not Allowed",
     "message": "Request method 'GET' is not supported"
   }
   ```

3. **올바른 Content-Type 헤더 필요** (POST 요청 시):
   ```bash
   curl -X POST -H "Content-Type: application/json" \
        -d '{"key": "value"}' \
        http://localhost:8080/api/v1/endpoint
   ```

---

*본 문서는 API Bridge System Management Service v1.0을 기준으로 작성되었습니다.*
*최종 업데이트: 2025-08-13*
*실제 테스트 기반으로 검증된 API 명세서입니다.*