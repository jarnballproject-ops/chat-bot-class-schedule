# build stage: ติดตั้งตาม lockfile ให้ได้ของชุดเดิมทุกเครื่อง
FROM node:22-alpine AS build
WORKDIR /build
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
# ยิง API ผ่าน /api ของ nginx เครื่องเดียวกัน ไม่ต้องรู้ว่า backend อยู่โฮสต์ไหน
ENV VITE_API_BASE=/api/v1
RUN npm run build

FROM nginx:alpine
COPY --from=build /build/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
