# Stage 1: 빌드
FROM node:20-alpine AS builder
WORKDIR /app

# Cloudinary 환경변수 (빌드 시점에 필요)
ARG VITE_CLOUDINARY_CLOUD_NAME
ARG VITE_CLOUDINARY_UPLOAD_PRESET

COPY package.json package-lock.json .npmrc ./
RUN npm ci --legacy-peer-deps

COPY . .

# 빌드 시점에 환경변수를 .env로 생성
RUN echo "VITE_CLOUDINARY_CLOUD_NAME=${VITE_CLOUDINARY_CLOUD_NAME}" > .env && \
    echo "VITE_CLOUDINARY_UPLOAD_PRESET=${VITE_CLOUDINARY_UPLOAD_PRESET}" >> .env

RUN npm run build

# Stage 2: Nginx로 서빙
FROM nginx:1.27-alpine

COPY nginx.conf /etc/nginx/nginx.conf
COPY --from=builder /app/dist /usr/share/nginx/html

EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]
