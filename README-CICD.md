# CI/CD 배포 자동화 시스템

Jenkins와 ArgoCD를 사용한 완전 자동화된 CI/CD 파이프라인입니다.

## 🏗️ 아키텍처 개요

```
┌─────────────┐    ┌─────────────┐    ┌─────────────┐    ┌─────────────┐
│   GitHub    │───▶│   Jenkins   │───▶│ Docker Hub  │───▶│   ArgoCD    │
│             │    │             │    │             │    │             │
│ Source Code │    │ CI Pipeline │    │ Image Store │    │ CD Pipeline │
└─────────────┘    └─────────────┘    └─────────────┘    └─────────────┘
                           │                                      │
                           ▼                                      ▼
                   ┌─────────────┐                        ┌─────────────┐
                   │  SonarQube  │                        │ Kubernetes  │
                   │             │                        │             │
                   │Code Quality │                        │   Cluster   │
                   └─────────────┘                        └─────────────┘
```

## 📁 프로젝트 구조

```
.
├── Jenkinsfile                    # Jenkins 파이프라인 정의
├── Dockerfile                     # 컨테이너 이미지 빌드
├── docker-compose.ci.yml          # CI 테스트 환경
├── k8s/                          # Kubernetes 매니페스트
│   ├── base/                     # 기본 리소스
│   │   ├── deployment.yaml
│   │   ├── service.yaml
│   │   ├── configmap.yaml
│   │   ├── secret.yaml
│   │   ├── hpa.yaml
│   │   └── kustomization.yaml
│   └── overlays/                 # 환경별 설정
│       ├── staging/
│       └── production/
├── argocd/                       # ArgoCD 애플리케이션
│   ├── application-staging.yaml
│   ├── application-production.yaml
│   └── applicationset.yaml
└── scripts/
    └── deploy.sh                 # 배포 스크립트
```

## 🚀 CI/CD 파이프라인

### Jenkins CI 파이프라인

1. **소스 체크아웃**: Git 저장소에서 최신 코드 가져오기
2. **테스트 실행**: 단위 테스트 및 통합 테스트
3. **빌드**: Gradle로 JAR 파일 생성
4. **코드 품질 분석**: SonarQube 정적 분석
5. **Docker 이미지 빌드**: 컨테이너 이미지 생성
6. **보안 스캔**: Trivy로 취약점 검사
7. **이미지 푸시**: Docker Registry에 업로드
8. **매니페스트 업데이트**: K8s 매니페스트 저장소 업데이트

### ArgoCD CD 파이프라인

1. **변경 감지**: Git 저장소 모니터링
2. **매니페스트 동기화**: Kubernetes 클러스터에 배포
3. **헬스 체크**: 애플리케이션 상태 확인
4. **알림**: Slack/Teams 배포 알림

## 🔧 설정 가이드

### 1. Jenkins 설정

#### 필수 플러그인 설치
```bash
# Jenkins 관리 > 플러그인 관리에서 설치
- Pipeline
- Docker Pipeline
- Kubernetes
- SonarQube Scanner
- Slack Notification
- HTML Publisher
```

#### 크리덴셜 설정
```bash
# Jenkins 관리 > Manage Credentials에서 추가
1. docker-registry-credentials: Docker Registry 인증정보
2. git-credentials: Git 저장소 접근 토큰
3. argocd-auth-token: ArgoCD API 토큰
4. sonarqube-token: SonarQube 분석 토큰
```

#### 도구 설정
```bash
# Jenkins 관리 > Global Tool Configuration
1. JDK: OpenJDK-17
2. Gradle: Gradle-8.5
3. SonarQube Scanner: 최신 버전
```

### 2. ArgoCD 설정

#### ArgoCD 설치
```bash
# Kubernetes 클러스터에 ArgoCD 설치
kubectl create namespace argocd
kubectl apply -n argocd -f https://raw.githubusercontent.com/argoproj/argo-cd/stable/manifests/install.yaml

# ArgoCD CLI 설치
curl -sSL -o argocd-linux-amd64 https://github.com/argoproj/argo-cd/releases/latest/download/argocd-linux-amd64
sudo install -m 555 argocd-linux-amd64 /usr/local/bin/argocd
```

#### 애플리케이션 배포
```bash
# ArgoCD 애플리케이션 생성
kubectl apply -f argocd/application-staging.yaml
kubectl apply -f argocd/application-production.yaml

# 또는 ApplicationSet 사용
kubectl apply -f argocd/applicationset.yaml
```

### 3. Kubernetes 클러스터 준비

#### 네임스페이스 생성
```bash
kubectl create namespace system-management-staging
kubectl create namespace system-management-prod
```

#### 시크릿 생성
```bash
# 데이터베이스 시크릿
kubectl create secret generic database-secret \
  --from-literal=url="jdbc:mysql://mysql-service:3306/system_management" \
  --from-literal=username="system_user" \
  --from-literal=password="your-password" \
  -n system-management-staging

# AWS 크리덴셜
kubectl create secret generic aws-credentials \
  --from-literal=access-key-id="your-access-key" \
  --from-literal=secret-access-key="your-secret-key" \
  -n system-management-staging
```

## 🔄 배포 프로세스

### 자동 배포 (추천)

1. **개발 브랜치에 푸시**: `develop` 브랜치에 코드 푸시
   ```bash
   git push origin develop
   ```
   → Jenkins가 자동으로 빌드하고 Staging 환경에 배포

2. **프로덕션 배포**: `main` 브랜치에 머지
   ```bash
   git checkout main
   git merge develop
   git push origin main
   ```
   → Jenkins가 빌드하고 ArgoCD가 Production 환경에 배포

### 수동 배포

#### 스크립트 사용
```bash
# Staging 환경 배포
ENVIRONMENT=staging ./scripts/deploy.sh deploy

# Production 환경 배포
ENVIRONMENT=production ./scripts/deploy.sh deploy

# 특정 태그로 배포
./scripts/deploy.sh deploy v1.2.3
```

#### 직접 명령어 사용
```bash
# 이미지 빌드 및 푸시
docker build -t your-registry.com/system-management-service:v1.0.0 .
docker push your-registry.com/system-management-service:v1.0.0

# Kubernetes 배포
cd k8s/overlays/staging
kustomize edit set image system-management-service=your-registry.com/system-management-service:v1.0.0
kustomize build . | kubectl apply -f -
```

## 📊 모니터링 및 관찰성

### 헬스 체크
```bash
# 애플리케이션 상태 확인
curl http://localhost:8080/api/v1/health

# Kubernetes 리소스 상태
kubectl get pods -n system-management-staging -l app=system-management-service
```

### 로그 확인
```bash
# 애플리케이션 로그
kubectl logs -f deployment/system-management-service -n system-management-staging

# Jenkins 빌드 로그
# Jenkins UI에서 Build History > Console Output 확인

# ArgoCD 동기화 로그
argocd app logs system-management-service-staging
```

### 메트릭 모니터링
```bash
# Prometheus 메트릭 엔드포인트
curl http://localhost:8080/api/v1/actuator/prometheus

# Kubernetes 메트릭
kubectl top pods -n system-management-staging
```

## 🔧 트러블슈팅

### 일반적인 문제들

#### 1. Jenkins 빌드 실패
```bash
# 빌드 로그 확인
Jenkins UI > Build History > Console Output

# 일반적인 원인:
- Gradle wrapper 실행 권한 없음: chmod +x gradlew
- 테스트 실패: ./gradlew test --info
- Docker 빌드 실패: docker logs <container-id>
```

#### 2. ArgoCD 동기화 실패
```bash
# ArgoCD 애플리케이션 상태 확인
argocd app get system-management-service-staging

# 동기화 강제 실행
argocd app sync system-management-service-staging --force

# 일반적인 원인:
- Git 저장소 접근 권한
- Kubernetes RBAC 권한
- 잘못된 매니페스트 문법
```

#### 3. Pod 시작 실패
```bash
# Pod 이벤트 확인
kubectl describe pod <pod-name> -n system-management-staging

# 일반적인 원인:
- 이미지 Pull 실패: 레지스트리 인증 확인
- 리소스 부족: kubectl top nodes
- 환경변수 오류: ConfigMap/Secret 확인
```

### 롤백 절차

#### Jenkins를 통한 롤백
```bash
# 이전 빌드 번호로 재배포
Jenkins UI > Build History > 이전 성공 빌드 > Rebuild
```

#### Kubernetes 롤백
```bash
# 이전 버전으로 롤백
kubectl rollout undo deployment/system-management-service -n system-management-staging

# 특정 버전으로 롤백
kubectl rollout undo deployment/system-management-service --to-revision=2 -n system-management-staging
```

#### ArgoCD 롤백
```bash
# ArgoCD UI 또는 CLI로 이전 버전 동기화
argocd app rollback system-management-service-staging <revision-id>
```

## 🔐 보안 고려사항

### 이미지 보안
- Trivy 스캔을 통한 취약점 검사
- 멀티스테이지 빌드로 이미지 크기 최적화
- Non-root 사용자로 컨테이너 실행

### 크리덴셜 관리
- Jenkins 크리덴셜 스토어 사용
- Kubernetes Secret으로 민감 정보 관리
- ArgoCD에서 Git 저장소 접근 시 토큰 사용

### 네트워크 보안
- Service Mesh (Istio) 적용 고려
- Network Policy로 트래픽 제어
- TLS 인증서 자동 관리 (cert-manager)

## 📈 성능 최적화

### 빌드 최적화
- Gradle 빌드 캐시 활용
- Docker 레이어 캐싱
- 병렬 테스트 실행

### 배포 최적화
- Rolling Update 전략
- HPA로 자동 스케일링
- PDB로 고가용성 보장

### 리소스 최적화
- JVM 메모리 튜닝
- Kubernetes 리소스 요청/제한 설정
- 컨테이너 이미지 최적화

## 📚 추가 자료

- [Jenkins Pipeline 문서](https://jenkins.io/doc/book/pipeline/)
- [ArgoCD 공식 문서](https://argo-cd.readthedocs.io/)
- [Kubernetes 배포 가이드](https://kubernetes.io/docs/concepts/workloads/controllers/deployment/)
- [Kustomize 사용법](https://kustomize.io/)

## 🤝 기여하기

1. 이슈 생성 또는 개선 제안
2. 브랜치 생성: `git checkout -b feature/improvement`
3. 변경사항 커밋: `git commit -m 'Add improvement'`
4. 푸시: `git push origin feature/improvement`
5. Pull Request 생성

## 📄 라이선스

이 프로젝트는 MIT 라이선스를 따릅니다.