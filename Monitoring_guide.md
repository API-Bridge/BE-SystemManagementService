# 🔍 API Bridge 모니터링 스택 설치 가이드

> **API Bridge System 모니터링 환경 구축을 위한 완전 가이드**

---

## 📋 목차
- [개요](#개요)
- [사전 요구사항](#사전-요구사항)
- [설치 순서](#설치-순서)
- [Prometheus 설정](#prometheus-설정)
- [Node Exporter 설정](#node-exporter-설정)
- [Alertmanager 설정](#alertmanager-설정)
- [Grafana 설정](#grafana-설정)
- [서비스 시작/중지](#서비스-시작중지)
- [접속 정보](#접속-정보)
- [트러블슈팅](#트러블슈팅)

---

## 🎯 개요

이 가이드는 API Bridge System의 모니터링을 위한 다음 컴포넌트들의 설치 및 설정을 다룹니다:

- **Prometheus 3.1.0**: 메트릭 수집 및 저장
- **Node Exporter 1.8.2**: 시스템 메트릭 수집
- **Alertmanager 0.27.0**: 알림 관리
- **Grafana 10.2.3**: 시각화 대시보드

---

## ✅ 사전 요구사항

### 시스템 요구사항
- **OS**: macOS, Linux, Windows
- **Java**: 17+ (Spring Boot 애플리케이션용)
- **메모리**: 최소 4GB RAM
- **디스크**: 10GB 이상 여유 공간

### 포트 사용 현황
| 서비스 | 포트 | 용도 |
|--------|------|------|
| Spring Boot | 8080 | API Bridge 애플리케이션 |
| Prometheus | 9090 | 메트릭 수집 서버 |
| Node Exporter | 9100 | 시스템 메트릭 |
| Alertmanager | 9093 | 알림 관리 |
| Grafana | 3000 | 웹 대시보드 |

---

## 🚀 설치 순서

### 1️⃣ 프로젝트 클론 및 디렉토리 생성

```bash
# 프로젝트 루트에서 실행
cd /path/to/BE-SystemManagementService
mkdir -p monitoring
cd monitoring
```

### 2️⃣ Prometheus 설치

#### macOS
```bash
# Prometheus 다운로드
curl -LO https://github.com/prometheus/prometheus/releases/download/v3.1.0/prometheus-3.1.0.darwin-amd64.tar.gz

# 압축 해제
tar xzf prometheus-3.1.0.darwin-amd64.tar.gz

# 실행 권한 부여
chmod +x prometheus-3.1.0.darwin-amd64/prometheus
chmod +x prometheus-3.1.0.darwin-amd64/promtool
```

#### Linux
```bash
# Prometheus 다운로드
curl -LO https://github.com/prometheus/prometheus/releases/download/v3.1.0/prometheus-3.1.0.linux-amd64.tar.gz

# 압축 해제
tar xzf prometheus-3.1.0.linux-amd64.tar.gz

# 실행 권한 부여
chmod +x prometheus-3.1.0.linux-amd64/prometheus
chmod +x prometheus-3.1.0.linux-amd64/promtool
```

### 3️⃣ Node Exporter 설치

#### macOS
```bash
# Node Exporter 다운로드
curl -LO https://github.com/prometheus/node_exporter/releases/download/v1.8.2/node_exporter-1.8.2.darwin-amd64.tar.gz

# 압축 해제
tar xzf node_exporter-1.8.2.darwin-amd64.tar.gz

# 실행 권한 부여
chmod +x node_exporter-1.8.2.darwin-amd64/node_exporter
```

#### Linux
```bash
# Node Exporter 다운로드
curl -LO https://github.com/prometheus/node_exporter/releases/download/v1.8.2/node_exporter-1.8.2.linux-amd64.tar.gz

# 압축 해제
tar xzf node_exporter-1.8.2.linux-amd64.tar.gz

# 실행 권한 부여
chmod +x node_exporter-1.8.2.linux-amd64/node_exporter
```

### 4️⃣ Alertmanager 설치

#### macOS
```bash
# Alertmanager 다운로드
curl -LO https://github.com/prometheus/alertmanager/releases/download/v0.27.0/alertmanager-0.27.0.darwin-amd64.tar.gz

# 압축 해제
tar xzf alertmanager-0.27.0.darwin-amd64.tar.gz

# 실행 권한 부여
chmod +x alertmanager-0.27.0.darwin-amd64/alertmanager
chmod +x alertmanager-0.27.0.darwin-amd64/amtool
```

#### Linux
```bash
# Alertmanager 다운로드
curl -LO https://github.com/prometheus/alertmanager/releases/download/v0.27.0/alertmanager-0.27.0.linux-amd64.tar.gz

# 압축 해제
tar xzf alertmanager-0.27.0.linux-amd64.tar.gz

# 실행 권한 부여
chmod +x alertmanager-0.27.0.linux-amd64/alertmanager
chmod +x alertmanager-0.27.0.linux-amd64/amtool
```

### 5️⃣ Grafana 설치

#### macOS
```bash
# Grafana 다운로드
curl -LO https://dl.grafana.com/oss/release/grafana-10.2.3.darwin-amd64.tar.gz

# 압축 해제
tar xzf grafana-10.2.3.darwin-amd64.tar.gz

# 이름 변경 (일관성을 위해)
mv grafana-10.2.3 grafana-v10.2.3
```

#### Linux
```bash
# Grafana 다운로드
curl -LO https://dl.grafana.com/oss/release/grafana-10.2.3.linux-amd64.tar.gz

# 압축 해제
tar xzf grafana-10.2.3.linux-amd64.tar.gz

# 이름 변경 (일관성을 위해)
mv grafana-10.2.3 grafana-v10.2.3
```

---

## ⚙️ Prometheus 설정

### Prometheus 설정 파일 생성

`monitoring/prometheus.yml` 파일을 생성합니다:

```yaml
# Global configurations
global:
  scrape_interval: 15s
  evaluation_interval: 15s

# Rule files specifies a list of globs. Rules and alerts are read from all matching files.
rule_files:
  - "alert_rules.yml"

# Alert manager configuration
alerting:
  alertmanagers:
    - static_configs:
        - targets:
          - localhost:9093

# Scrape configuration
scrape_configs:
  # Prometheus 자체 모니터링
  - job_name: 'prometheus'
    static_configs:
      - targets: ['localhost:9090']

  # API Bridge System 모니터링
  - job_name: 'api-bridge-system'
    static_configs:
      - targets: ['localhost:8080']
    metrics_path: '/actuator/prometheus'
    scrape_interval: 30s

  # Node Exporter (시스템 메트릭)
  - job_name: 'node'
    static_configs:
      - targets: ['localhost:9100']

  # Alertmanager 모니터링
  - job_name: 'alertmanager'
    static_configs:
      - targets: ['localhost:9093']
```

### 알림 규칙 파일 생성

`monitoring/alert_rules.yml` 파일을 생성합니다:

```yaml
groups:
- name: api_bridge_alerts
  rules:
  # 시스템 다운 알림
  - alert: ServiceDown
    expr: up == 0
    for: 1m
    labels:
      severity: critical
    annotations:
      summary: "Service {{ $labels.instance }} is down"
      description: "{{ $labels.instance }} of job {{ $labels.job }} has been down for more than 1 minute."

  # 높은 메모리 사용률
  - alert: HighMemoryUsage
    expr: (jvm_memory_used_bytes{area="heap"} / jvm_memory_max_bytes{area="heap"}) * 100 > 90
    for: 2m
    labels:
      severity: warning
    annotations:
      summary: "High JVM heap memory usage on {{ $labels.instance }}"
      description: "JVM heap memory usage is above 90% on {{ $labels.instance }}"

  # HTTP 에러율 증가
  - alert: HighErrorRate
    expr: (rate(http_server_requests_seconds_count{status=~"5.."}[5m]) / rate(http_server_requests_seconds_count[5m])) * 100 > 5
    for: 2m
    labels:
      severity: warning
    annotations:
      summary: "High HTTP error rate on {{ $labels.instance }}"
      description: "HTTP error rate is above 5% on {{ $labels.instance }}"
```

---

## 🖥️ Node Exporter 설정

Node Exporter는 별도 설정 파일이 필요하지 않습니다. 바로 실행 가능합니다.

---

## 📧 Alertmanager 설정

### Alertmanager 설정 파일 생성

`monitoring/alertmanager.yml` 파일을 생성합니다:

```yaml
# Global configuration
global:
  smtp_smarthost: 'your-smtp-server:587'
  smtp_from: 'alerts@yourcompany.com'
  smtp_auth_username: 'your-username'
  smtp_auth_password: 'your-password'

# Template files
templates:
  - '/etc/alertmanager/templates/*.tmpl'

# Route tree
route:
  group_by: ['alertname']
  group_wait: 10s
  group_interval: 10s
  repeat_interval: 1h
  receiver: 'api-bridge-team'

# Alert receivers
receivers:
- name: 'api-bridge-team'
  email_configs:
  - to: 'team@yourcompany.com'
    subject: '[API Bridge Alert] {{ .GroupLabels.alertname }}'
    body: |
      {{ range .Alerts }}
      Alert: {{ .Annotations.summary }}
      Description: {{ .Annotations.description }}
      Labels: {{ .Labels }}
      {{ end }}

# Inhibit rules
inhibit_rules:
  - source_match:
      severity: 'critical'
    target_match:
      severity: 'warning'
    equal: ['alertname', 'dev', 'instance']
```

---

## 📊 Grafana 설정

### Grafana 설정 파일 생성

`monitoring/grafana.ini` 파일을 생성합니다:

```ini
[server]
http_port = 3000
protocol = http
domain = localhost
root_url = http://localhost:3000/

[security]
admin_user = admin
admin_password = admin123
secret_key = SW2YcwTIb9zpOOhoPsMm

[database]
type = sqlite3
path = grafana.db

[analytics]
reporting_enabled = false
check_for_updates = true

[log]
mode = console
level = info

[paths]
data = data
logs = data/log
plugins = data/plugins
provisioning = conf/provisioning
```

### Grafana 프로비저닝 설정

#### 데이터소스 자동 설정

디렉토리 생성 후 설정 파일을 생성합니다:

```bash
mkdir -p grafana-provisioning/datasources
mkdir -p grafana-provisioning/dashboards
mkdir -p grafana-dashboards
```

`monitoring/grafana-provisioning/datasources/prometheus.yaml`:

```yaml
apiVersion: 1

datasources:
  - name: Prometheus
    type: prometheus
    uid: prometheus-uid
    access: proxy
    url: http://localhost:9090
    isDefault: true
    editable: true
    jsonData:
      timeInterval: "30s"
      httpMethod: POST
      exemplarTraceIdDestinations:
        - name: traceID
          datasourceUid: tempo-uid
```

#### 대시보드 프로비더 설정

`monitoring/grafana-provisioning/dashboards/dashboard-provider.yaml`:

```yaml
apiVersion: 1

providers:
  - name: 'API Bridge Dashboards'
    orgId: 1
    folder: 'API Bridge Monitoring'
    folderUid: 'apibridge-folder'
    type: file
    updateIntervalSeconds: 30
    allowUiUpdates: true
    options:
      path: /path/to/your/project/monitoring/grafana-dashboards
```

---

## 🎮 서비스 시작/중지

### 모든 서비스 시작 스크립트

`monitoring/start-monitoring.sh` 파일을 생성합니다:

```bash
#!/bin/bash

# API Bridge 모니터링 스택 시작 스크립트

echo "🚀 Starting API Bridge Monitoring Stack..."

# 현재 디렉토리 확인
MONITORING_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$MONITORING_DIR"

# Node Exporter 시작
echo "📊 Starting Node Exporter..."
nohup ./node_exporter-*-amd64/node_exporter > node_exporter.log 2>&1 &
echo $! > node_exporter.pid
echo "✅ Node Exporter started (PID: $(cat node_exporter.pid))"

# Prometheus 시작
echo "🔍 Starting Prometheus..."
nohup ./prometheus-*-amd64/prometheus --config.file=prometheus.yml --storage.tsdb.path=prometheus-data --web.console.libraries=prometheus-*-amd64/console_libraries --web.console.templates=prometheus-*-amd64/consoles --web.enable-lifecycle > prometheus.log 2>&1 &
echo $! > prometheus.pid
echo "✅ Prometheus started (PID: $(cat prometheus.pid))"

# Alertmanager 시작
echo "🚨 Starting Alertmanager..."
nohup ./alertmanager-*-amd64/alertmanager --config.file=alertmanager.yml --storage.path=alertmanager-data > alertmanager.log 2>&1 &
echo $! > alertmanager.pid
echo "✅ Alertmanager started (PID: $(cat alertmanager.pid))"

# Grafana 시작
echo "📈 Starting Grafana..."
nohup ./grafana-v10.2.3/bin/grafana-server --homepath=./grafana-v10.2.3 --config=grafana.ini > grafana.log 2>&1 &
echo $! > grafana.pid
echo "✅ Grafana started (PID: $(cat grafana.pid))"

echo ""
echo "🎉 All services started successfully!"
echo ""
echo "📍 Service URLs:"
echo "   • Prometheus: http://localhost:9090"
echo "   • Alertmanager: http://localhost:9093"
echo "   • Grafana: http://localhost:3000 (admin/admin123)"
echo "   • Node Exporter: http://localhost:9100"
echo ""
echo "🔍 Check logs:"
echo "   tail -f *.log"
```

### 모든 서비스 중지 스크립트

`monitoring/stop-monitoring.sh` 파일을 생성합니다:

```bash
#!/bin/bash

# API Bridge 모니터링 스택 중지 스크립트

echo "🛑 Stopping API Bridge Monitoring Stack..."

# 현재 디렉토리 확인
MONITORING_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$MONITORING_DIR"

# PID 파일들을 확인하고 프로세스 종료
for service in prometheus alertmanager node_exporter grafana; do
    if [ -f "${service}.pid" ]; then
        PID=$(cat "${service}.pid")
        if kill -0 "$PID" 2>/dev/null; then
            echo "🔄 Stopping $service (PID: $PID)..."
            kill "$PID"
            sleep 2
            if kill -0 "$PID" 2>/dev/null; then
                echo "⚠️  Force killing $service..."
                kill -9 "$PID"
            fi
            echo "✅ $service stopped"
        else
            echo "⚠️  $service process not found"
        fi
        rm -f "${service}.pid"
    else
        echo "⚠️  ${service}.pid not found"
    fi
done

echo ""
echo "🎉 All services stopped!"
```

### 실행 권한 부여

```bash
chmod +x start-monitoring.sh
chmod +x stop-monitoring.sh
```

---

## 🔗 접속 정보

### 기본 접속 URL

| 서비스 | URL | 계정 정보 |
|--------|-----|-----------|
| **Prometheus** | http://localhost:9090 | 인증 없음 |
| **Alertmanager** | http://localhost:9093 | 인증 없음 |
| **Grafana** | http://localhost:3000 | admin / admin123 |
| **Node Exporter** | http://localhost:9100 | 인증 없음 |
| **Spring Boot** | http://localhost:8080/actuator/prometheus | 인증 없음 |

### Grafana 대시보드

1. **로그인**: http://localhost:3000 (admin/admin123)
2. **대시보드 위치**: 좌측 메뉴 > Dashboards > API Bridge Monitoring 폴더
3. **주요 대시보드**: "API Bridge - Overview Dashboard"

---

## 🔧 트러블슈팅

### 자주 발생하는 문제들

#### 1. 포트 충돌 문제

```bash
# 사용 중인 포트 확인
lsof -i :9090  # Prometheus
lsof -i :9093  # Alertmanager
lsof -i :9100  # Node Exporter
lsof -i :3000  # Grafana

# 프로세스 강제 종료
kill -9 <PID>
```

#### 2. 권한 문제

```bash
# 실행 권한 부여
chmod +x prometheus-*-amd64/prometheus
chmod +x alertmanager-*-amd64/alertmanager
chmod +x node_exporter-*-amd64/node_exporter
chmod +x grafana-v10.2.3/bin/grafana-server
```

#### 3. Prometheus 타겟 연결 실패

- Spring Boot 애플리케이션이 실행 중인지 확인
- `/actuator/prometheus` 엔드포인트 접근 가능한지 확인
- 방화벽 설정 확인

#### 4. Grafana 데이터소스 연결 실패

- Prometheus 서버 상태 확인 (http://localhost:9090)
- 프로비저닝 설정 파일 경로 확인
- Grafana 로그 확인: `tail -f grafana.log`

### 로그 확인 방법

```bash
# 모든 서비스 로그 실시간 확인
tail -f *.log

# 개별 서비스 로그 확인
tail -f prometheus.log
tail -f alertmanager.log  
tail -f node_exporter.log
tail -f grafana.log
```

### 설정 파일 검증

```bash
# Prometheus 설정 검증
./prometheus-*-amd64/promtool check config prometheus.yml

# Alertmanager 설정 검증
./alertmanager-*-amd64/amtool check-config alertmanager.yml
```

---

## 🚀 빠른 시작

```bash
# 1. monitoring 디렉토리로 이동
cd monitoring

# 2. 모든 서비스 시작
./start-monitoring.sh

# 3. 웹 브라우저에서 접속
# - Grafana: http://localhost:3000 (admin/admin123)
# - Prometheus: http://localhost:9090

# 4. 서비스 중지 (필요시)
./stop-monitoring.sh
```

---

## ❓ 문의사항

모니터링 스택 관련 문의사항은 다음으로 연락주세요:
- **팀 채널**: #api-bridge-monitoring
- **이슈 트래커**: GitHub Issues
- **문서 업데이트**: 이 README.md 파일 수정 후 PR

---

**📝 마지막 업데이트**: 2025-08-11  
**📋 버전**: v1.0.0  
**👥 작성자**: API Bridge Team