# Kibana-Elasticsearch 연결 문제 해결 가이드

## 🔍 발견된 문제점

Elasticsearch 8.x 버전에서는 기본적으로 **보안이 활성화**되어 있어서 Kibana 연결이 실패할 수 있습니다.

## ⚡ 해결된 설정

### 1. **Elasticsearch 설정 개선**
```yaml
elasticsearch:
  image: docker.elastic.co/elasticsearch/elasticsearch:8.8.0
  ports:
    - "9200:9200"
    - "9300:9300"  # 클러스터 통신 포트 추가
  environment:
    - discovery.type=single-node
    - xpack.security.enabled=false         # 보안 비활성화
    - xpack.security.http.ssl.enabled=false    # HTTP SSL 비활성화
    - xpack.security.transport.ssl.enabled=false  # Transport SSL 비활성화
    - "ES_JAVA_OPTS=-Xms512m -Xmx512m"
    - network.host=0.0.0.0                 # 네트워크 바인딩
  healthcheck:
    test: ["CMD-SHELL", "curl -f http://localhost:9200/_cluster/health || exit 1"]
    interval: 30s
    timeout: 10s
    retries: 5
```

### 2. **Kibana 설정 개선**
```yaml
kibana:
  image: docker.elastic.co/kibana/kibana:8.8.0
  ports:
    - "5601:5601"
  environment:
    - ELASTICSEARCH_HOSTS=http://elasticsearch:9200
    - ELASTICSEARCH_USERNAME=elastic        # 기본 사용자
    - ELASTICSEARCH_PASSWORD=""             # 보안 비활성화시 빈 패스워드
    - xpack.security.enabled=false         # Kibana 보안 비활성화
    - xpack.encryptedSavedObjects.encryptionKey=fhjskloppd678ehkdfdlliverpoolfcr
  depends_on:
    elasticsearch:
      condition: service_healthy            # Elasticsearch 헬스체크 대기
  healthcheck:
    test: ["CMD-SHELL", "curl -f http://localhost:5601/api/status || exit 1"]
    interval: 30s
    timeout: 10s
    retries: 5
```

## 🔧 핵심 변경사항

| 항목 | 기존 | 개선 후 | 이유 |
|------|------|---------|------|
| **SSL 설정** | 기본값 (활성화) | `ssl.enabled=false` | HTTP 연결 허용 |
| **보안 모드** | 기본값 (활성화) | `xpack.security.enabled=false` | 인증 없이 연결 |
| **헬스체크** | 없음 | 추가 | 의존성 순서 보장 |
| **사용자/패스워드** | 없음 | `elastic/""` | 명시적 인증 정보 |
| **네트워크 바인딩** | 기본값 | `0.0.0.0` | 컨테이너 간 통신 허용 |

## 📝 테스트 방법

### 1. **ELK 스택만 시작**
```bash
docker-compose up elasticsearch kibana -d
```

### 2. **연결 확인**
```bash
# Elasticsearch 상태 확인
curl http://localhost:9200/_cluster/health

# Kibana 상태 확인
curl http://localhost:5601/api/status
```

### 3. **웹 접속 확인**
- Elasticsearch: http://localhost:9200
- Kibana: http://localhost:5601

## 🚨 주의사항

⚠️ **이 설정은 개발 환경 전용입니다!**

운영 환경에서는 반드시:
- X-Pack 보안 활성화
- SSL/TLS 인증서 설정
- 강력한 사용자/패스워드 설정
- 네트워크 접근 제한

## ✅ 최종 해결 방법

Elasticsearch 8.x의 복잡한 보안 설정 문제를 피하기 위해 **7.17.0 버전으로 변경**했습니다.

### 변경된 설정:
```yaml
# Elasticsearch 7.17.0
elasticsearch:
  image: docker.elastic.co/elasticsearch/elasticsearch:7.17.0
  environment:
    - discovery.type=single-node
    - "ES_JAVA_OPTS=-Xms512m -Xmx512m"
    - network.host=0.0.0.0
    - http.cors.enabled=true
    - http.cors.allow-origin="*"

# Kibana 7.17.0  
kibana:
  image: docker.elastic.co/kibana/kibana:7.17.0
  environment:
    - ELASTICSEARCH_HOSTS=http://elasticsearch:9200
    - SERVER_PUBLICBASEURL=http://localhost:5601
```

### 7.x vs 8.x 차이점:
| 기능 | 7.x | 8.x |
|------|-----|-----|
| 기본 보안 | 비활성화 | 활성화 (복잡한 설정 필요) |
| SSL | 선택사항 | 기본 활성화 |
| 사용자 인증 | 불필요 | Service Account Token 필요 |
| 설정 복잡도 | 간단 | 복잡 |

## 🔧 테스트 명령어

### 새로운 ELK 스택 시작:
```bash
docker-compose down
docker-compose up elasticsearch kibana -d
```

### 연결 확인:
```bash
# Elasticsearch 확인
curl http://localhost:9200/_cluster/health

# Kibana 접속
open http://localhost:5601
```