# syntax=docker/dockerfile:1

###### Stage 1 — build the Vite bundle (uses the committed OpenAPI types; no backend needed) ######
# Debian (glibc) base so Vite/Rolldown native binaries resolve reliably.
FROM node:22-slim AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN --mount=type=cache,target=/root/.npm npm ci
COPY . .
RUN npm run build

###### Stage 2 — serve the static bundle and reverse-proxy the API ######
FROM nginx:1.27-alpine AS runtime
COPY nginx/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 80
