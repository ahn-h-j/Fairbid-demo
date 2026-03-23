# ASG 신규 인스턴스 부트스트랩 실패 해결 과정

> ASG가 새 인스턴스를 띄울 때 App이 정상 기동하지 못하는 문제를 해결한 과정

---

## 배경

- 스케일아웃 구조: 인프라 서버(MySQL, Redis) + App 서버(ASG 관리)
- App 서버는 Launch Template의 user-data 스크립트로 자동 시작
- AMI에 Docker + 코드 + `.env` 파일이 포함되어 있음

---

## 문제 1: k6 입찰 부하 테스트 시 403 Forbidden

### 증상
- k6로 경매 생성/입찰 API 호출 시 모든 요청 403
- 경매 목록 조회(GET)는 정상

### 원인
- `X-User-Id` 헤더 인증은 `load-test` 프로필에서만 동작
- App 서버의 `SPRING_PROFILES_ACTIVE=sentinel` (load-test 미포함)

### 해결
- 기존 인스턴스의 `.env`에 `load-test` 프로필 수동 추가
- `SPRING_PROFILES_ACTIVE=sentinel,load-test`
- Docker 재시작으로 반영

### 후속 문제
- 수동으로 1대만 수정 → **스케일 아웃으로 새로 뜬 인스턴스에는 적용 안 됨**
- 새 인스턴스는 AMI의 원본 `.env`(load-test 없음)를 사용하므로 여전히 403

---

## 문제 2: Launch Template user-data에서 INFRA_HOST 변수 치환 실패

### 증상
- Instance Refresh 후 새 인스턴스가 MySQL 연결 실패로 크래시
- `Communications link failure` / `Connection refused`

### 원인
- user-data에 `${infra_private_ip}` Terraform 템플릿 변수 사용
- Launch Template을 AWS CLI로 수동 생성했기 때문에 Terraform 변수 치환이 안 됨
- `.env`에 `INFRA_HOST=` (빈 값)이 그대로 남음

### 시도 1: 하드코딩 (v6)
```bash
sed -i 's/^INFRA_HOST=.*/INFRA_HOST=172.31.34.73/' .env
```
- 동작은 하지만 인프라 서버 IP가 바뀌면 Launch Template을 다시 만들어야 함
- 실서비스에서는 적절하지 않은 방식 → **폐기**

### 시도 2: SSM Parameter Store (v7)
- `/fairbid/infra-host` 파라미터에 인프라 서버 IP 저장
- user-data에서 `aws ssm get-parameter`로 읽기
- App 인스턴스에 IAM Role(`fairbid-app-role`) + SSM 읽기 권한 부여

```bash
INFRA_HOST=$(aws ssm get-parameter --name "/fairbid/infra-host" --region ap-northeast-2 \
  --query "Parameter.Value" --output text)
```

### 결과: 실패
```
[ERROR] SSM에서 INFRA_HOST를 가져올 수 없습니다.
```

### 원인
- **AMI에 AWS CLI가 설치되어 있지 않음**
- `aws` 명령어 자체가 `command not found`

---

## 문제 3: AMI에 AWS CLI 미설치

### 해결 (v8)
- user-data 시작 부분에 AWS CLI 설치 추가

```bash
if ! command -v aws &> /dev/null; then
    apt-get update -qq && apt-get install -y -qq awscli > /dev/null 2>&1
fi
```

### 전체 user-data 흐름 (v8, 최종)
```
1. AWS CLI 설치 (없으면)
2. git pull (최신 코드)
3. SSM에서 INFRA_HOST 조회
4. .env에 INFRA_HOST 주입 (sed로 덮어쓰기)
5. .env에 load-test 프로필 추가
6. docker compose up --build
```

### Instance Refresh 실행
- v8 Launch Template으로 교체 진행 중

---

## 삽질 기록 요약

| 버전 | 변경 | 결과 | 원인 |
|------|------|------|------|
| v4 | 기존 (수동 생성) | 정상 동작 | 수동으로 .env 세팅한 상태 |
| v5 | load-test 프로필 추가 | 실패 | `${infra_private_ip}` 미치환 → INFRA_HOST 빈 값 |
| v6 | INFRA_HOST 하드코딩 | 폐기 | 실서비스 부적합 |
| v7 | SSM Parameter Store | 실패 | AMI에 AWS CLI 미설치 |
| v8 | AWS CLI 설치 + SSM | **확인 중** | - |

---

## 교훈

1. **AMI에 뭐가 설치되어 있는지 확인** — AWS CLI가 없으면 SSM, CloudWatch 등 AWS 서비스 연동 불가
2. **Terraform 변수와 수동 배포 혼용 금지** — `${variable}` 문법은 Terraform에서만 치환됨
3. **설정값 주입은 SSM Parameter Store** — 하드코딩, 환경변수 직접 주입보다 중앙 관리가 확실함
4. **`grep -q` 함정** — `INFRA_HOST=` (빈 값)도 grep에 걸림, 값 존재 여부가 아닌 키 존재 여부만 체크됨
5. **Instance Refresh 전에 수동 테스트** — Launch Template 변경 후 바로 refresh하지 말고, 1대 먼저 수동으로 띄워서 검증

---

## AWS 리소스 현황

### SSM Parameter
- `/fairbid/infra-host` = `172.31.34.73`

### IAM
- Role: `fairbid-app-role` (EC2 assume)
- Policy: `ssm-read` (SSM GetParameter, `/fairbid/*`)
- Instance Profile: `fairbid-app-profile`

### Launch Template
- `fairbid-app-lt` (lt-0d06d790dc2eeaf72)
- 최신 버전: v8
- IAM Instance Profile: `fairbid-app-profile`
