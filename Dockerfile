# =====================================================================
# 데모 통합 빌드 — backend(Spring) 하나가 frontend(React)까지 서빙
# =====================================================================
# Railway 등에서 이 Dockerfile 하나로 단일 서비스 배포.
#  Stage1: frontend 빌드 → dist
#  Stage2: dist 를 backend 정적 리소스로 포함해 bootJar
#  Stage3: 경량 JRE 런타임
#
# 로컬 빌드:  docker build -t fairbid-demo .
# 실행:      docker run -p 8080:8080 --env-file .env fairbid-demo
# =====================================================================

# ===== Stage 1: frontend 빌드 =====
FROM node:20-alpine AS frontend
WORKDIR /fe
ARG VITE_CLOUDINARY_CLOUD_NAME
ARG VITE_CLOUDINARY_UPLOAD_PRESET
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci
COPY frontend/ ./
# 빌드 시점 환경변수(.env) 생성 후 정적 빌드
RUN printf "VITE_CLOUDINARY_CLOUD_NAME=%s\nVITE_CLOUDINARY_UPLOAD_PRESET=%s\n" \
        "$VITE_CLOUDINARY_CLOUD_NAME" "$VITE_CLOUDINARY_UPLOAD_PRESET" > .env \
    && npm run build

# ===== Stage 2: backend 빌드 (frontend dist 를 static 으로 포함) =====
FROM eclipse-temurin:17-jdk AS backend
WORKDIR /app
COPY backend/gradlew ./
COPY backend/gradle gradle
RUN sed -i 's/\r$//' ./gradlew && chmod +x ./gradlew
COPY backend/build.gradle backend/settings.gradle ./
RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew dependencies --no-daemon || true
COPY backend/src src
# frontend 빌드 산출물을 Spring 정적 리소스 경로로 복사 → bootJar 에 포함되어 classpath:/static/ 으로 서빙
COPY --from=frontend /fe/dist src/main/resources/static
RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew bootJar --no-daemon
RUN java -Djarmode=layertools -jar build/libs/*.jar extract

# ===== Stage 3: 런타임 =====
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=backend /app/dependencies/ ./
COPY --from=backend /app/spring-boot-loader/ ./
COPY --from=backend /app/snapshot-dependencies/ ./
COPY --from=backend /app/application/ ./
EXPOSE 8080
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
