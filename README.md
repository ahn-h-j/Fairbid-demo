<div align="center">

<img src="imgs/banner.png" alt="FairBid Banner" width="100%"/>

<br/>

# FairBid

중고 거래의 '흥정 피로도'를 없애고 제값을 찾아주는 실시간 경매 플랫폼

<br/>

</div>

<br/>

## 프로젝트 소개

중고 거래에서 판매자는 항상 고민합니다 — "이 가격이 맞나?", "더 받을 수 있는 거 아닌가?"

FairBid는 이 문제를 경매로 풀었습니다. 판매자는 물건만 올리면 되고, 가격은 구매자들의 경쟁이 결정합니다.
깎이는 거래가 아니라 올라가는 거래.

<br/>

## 주요 기능

| 기능 | 설명 |
|------|------|
| **실시간 경매** | WebSocket(STOMP)으로 입찰 즉시 반영, 종료 5분 전 입찰 시 자동 연장 |
| **입찰** | 원터치 / 직접 입력 / 즉시 구매 3종, 가격 구간별 입찰 단위 차등 적용 |
| **낙찰 · 노쇼 처리** | 1순위/2순위 낙찰, 노쇼 시 2순위 자동 승계, 경고 3회 누적 차단 |
| **거래** | 직거래(시간 제안/역제안) · 택배(입금 확인/송장 입력) 선택 |
| **알림** | FCM Push + 인앱 알림 이중 구조, 15종 알림 |
| **소셜 로그인** | 카카오 / 네이버 / 구글 OAuth 2.0 |
| **관리자** | 낙찰률, 경쟁률, 일별 통계, 시간대별 입찰 패턴, 유저/경매 관리 |

<br/>

## 스크린샷

<!-- TODO: 실제 화면 캡처 추가 -->

<br/>

## 아키텍처

<img src="imgs/architecture.png" alt="FairBid Architecture" width="100%"/>

<br/>

## 신규 개발자 온보딩

이 폴더 하나로 환경 셋업, 기능 이해, 장애 대응, 기능 개선까지 가능한 **온보딩 사이트**가 있다.
MkDocs Material로 빌드되며, 16개 기능 문서 + 핵심 9문서로 구성.

```bash
# 1) 의존성 설치 (1회)
pip install mkdocs-material pymdown-extensions

# 2) 로컬에서 띄우기
mkdocs serve -a 127.0.0.1:8765
# → http://127.0.0.1:8765
```

- 진입점: [`docs/onboarding/index.md`](docs/onboarding/index.md)
- 키트 운영 가이드: [`docs/onboarding/USAGE.md`](docs/onboarding/USAGE.md)
- 정적 빌드: `mkdocs build` → `docs/_site/`

<br/>

## 기술적 의사결정

- [입찰 응답시간 병목 추적 및 개선 (3,600ms → 4.5ms)](https://tkgkd159.tistory.com/entry/%EC%9E%85%EC%B0%B0-%EB%B2%88%ED%8A%BC-%EB%88%84%EB%A5%B4%EA%B3%A0-98%EC%B4%88-%E2%80%94-%EC%9D%91%EB%8B%B5%EC%8B%9C%EA%B0%84-3600ms%EC%97%90%EC%84%9C-45ms%EA%B9%8C%EC%A7%80-%EB%B3%91%EB%AA%A9%EC%9D%84-%EC%B6%94%EC%A0%81%ED%95%9C-%EA%B3%BC%EC%A0%95)
- [DB 영속화 전략 변경 과정 — 동기 → @Async → Redis Stream](https://tkgkd159.tistory.com/entry/DB-%EC%98%81%EC%86%8D%ED%99%94-%EC%A0%84%EB%9E%B5%EC%9D%84-%EC%84%B8-%EB%B2%88-%EB%B0%94%EA%BE%B8%EB%A9%B0-%EB%B0%B0%EC%9A%B4-%EA%B2%83-%E2%80%94-%EB%8F%99%EA%B8%B0-Async-Redis-Stream)
- [Redis HA 구성 — 단일 인스턴스에서 Sentinel까지](https://tkgkd159.tistory.com/entry/FairBid-Redis%EB%A5%BC-%EB%A9%94%EC%9D%B8-DB%EB%A1%9C-%EC%93%B0%EB%A9%B4%EC%84%9C-%EC%9E%A5%EC%95%A0%EC%97%90-%EB%8C%80%EB%B9%84%ED%95%9C-%EA%B3%BC%EC%A0%95-%E2%80%94-%EB%8B%A8%EC%9D%BC-%EC%9D%B8%EC%8A%A4%ED%84%B4%EC%8A%A4%EC%97%90%EC%84%9C-Sentinel%EA%B9%8C%EC%A7%80)

<br/>

## 기술 스택

### Backend
![Java](https://img.shields.io/badge/Java-17-ED8B00?style=flat-square&logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.x-6DB33F?style=flat-square&logo=springboot&logoColor=white)
![MySQL](https://img.shields.io/badge/MySQL-4479A1?style=flat-square&logo=mysql&logoColor=white)
![Redis](https://img.shields.io/badge/Redis-DC382D?style=flat-square&logo=redis&logoColor=white)

### Frontend
![React](https://img.shields.io/badge/React-19-61DAFB?style=flat-square&logo=react&logoColor=black)
![Vite](https://img.shields.io/badge/Vite-646CFF?style=flat-square&logo=vite&logoColor=white)
![Tailwind CSS](https://img.shields.io/badge/Tailwind%20CSS-v4-06B6D4?style=flat-square&logo=tailwindcss&logoColor=white)

### Infrastructure
![AWS EC2](https://img.shields.io/badge/AWS%20EC2-FF9900?style=flat-square&logo=amazonec2&logoColor=white)
![Docker](https://img.shields.io/badge/Docker-2496ED?style=flat-square&logo=docker&logoColor=white)
![GitHub Actions](https://img.shields.io/badge/GitHub%20Actions-2088FF?style=flat-square&logo=githubactions&logoColor=white)

<br/>

## 실행 방법

```bash
# Backend
cd backend
./gradlew bootRun

# Frontend
cd frontend
npm install && npm run dev

# Docker (전체)
docker-compose up -d
```
