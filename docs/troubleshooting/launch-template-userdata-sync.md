# Launch Template user-data 미동기화 문제

> 코드에서 user-data를 업데이트했지만 AWS Launch Template에 반영하지 않아 새 인스턴스가 부팅 실패한 문제

---

## 배경

- ECR 기반 CD 전환 완료 (커밋 `a82fdad`, `06720dd`)
- `infra/user-data-app.sh`를 2가지 변경:
  1. `docker compose up --build` → ECR에서 이미지 pull 방식으로 전환
  2. AWS CLI v1 (apt awscli) → v2 (공식 설치) 변경
- Instance Refresh로 새 인스턴스 배포 시도

---

## 문제

### 증상
- Instance Refresh 후 새 인스턴스가 ALB health check 실패 (`unhealthy`)
- ASG가 인스턴스를 반복적으로 교체하지만 계속 실패
- Instance Refresh가 `InProgress`에서 멈춤

### 원인
**코드(Git)의 user-data와 AWS Launch Template의 user-data가 불일치**

| 항목 | 코드 (`infra/user-data-app.sh`) | Launch Template v9 |
|------|------|------|
| 이미지 | ECR pull | `--build` (소스 빌드) |
| AWS CLI | v2 (공식 설치) | 없음 |
| ECR 로그인 | `aws ecr get-login-password` | 없음 |

코드에서 ECR 기반으로 전환했지만, **Launch Template에는 반영하지 않음** → 새 인스턴스는 여전히 옛날 방식으로 부팅 시도 → 실패

### 실패 흐름
```
새 인스턴스 부팅
  → Launch Template v9의 user-data 실행
  → docker compose up --build (ECR이 아닌 소스 빌드)
  → docker-compose-app.yml은 이미 ECR 이미지로 변경됨
  → 빌드 컨텍스트 없음 → 앱 실행 실패
  → ALB health check 실패 → unhealthy
  → ASG가 교체 반복
```

---

## 해결

### Launch Template v10 생성

```bash
# 1. 최신 user-data를 base64 인코딩하여 Launch Template v10 생성
USER_DATA_B64=$(base64 -w 0 infra/user-data-app.sh)
aws ec2 create-launch-template-version \
  --launch-template-name fairbid-app-lt \
  --source-version 9 \
  --launch-template-data "{\"UserData\":\"$USER_DATA_B64\"}" \
  --version-description "ECR-based deploy + AWS CLI v2"

# 2. v10을 기본 버전으로 설정
aws ec2 modify-launch-template \
  --launch-template-name fairbid-app-lt \
  --default-version 10

# 3. 막혀있던 Instance Refresh 취소 후 재시작
aws autoscaling cancel-instance-refresh \
  --auto-scaling-group-name fairbid-app-asg
```

### 결과
- v10 기반 인스턴스 정상 부팅
- AWS CLI v2 설치 → ECR 로그인 → 이미지 pull → 앱 기동 → ALB healthy

---

## Launch Template 버전 이력

| 버전 | 변경 | 결과 |
|------|------|------|
| v4 | 수동 생성 (기본) | 정상 (수동 .env 세팅) |
| v5 | load-test 프로필 추가 | 실패 (`${infra_private_ip}` 미치환) |
| v6 | INFRA_HOST 하드코딩 | 폐기 (유지보수 부적합) |
| v7 | SSM Parameter Store | 실패 (AWS CLI 미설치) |
| v8 | AWS CLI v1 설치 + SSM | 정상 (소스 빌드 방식) |
| v9 | CD 파이프라인 연동 | 정상 (소스 빌드 방식) |
| v10 | **ECR pull + AWS CLI v2** | **정상** |

---

## 교훈

1. **코드 변경 ≠ 인프라 반영** — `infra/user-data-app.sh`를 수정해도 AWS Launch Template은 자동으로 업데이트되지 않는다. CD 파이프라인에서 Launch Template 버전도 함께 갱신해야 한다.
2. **docker-compose 파일과 user-data의 정합성** — `docker-compose-app.yml`에서 `image:`로 바꿨으면 user-data도 pull 방식으로 맞춰야 한다. 둘 중 하나만 바꾸면 부팅 실패.
3. **Instance Refresh 실패 시 원인 파악 순서**:
   - ALB target health 확인 → unhealthy면 앱 문제
   - Launch Template 버전 확인 → 의도한 버전인지
   - user-data 내용 확인 → `base64 -d`로 디코딩해서 실제 스크립트 확인
