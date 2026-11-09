# syntax=docker/dockerfile:1
# Frontend image: build the Angular app, serve it with nginx (non-root), proxy /api to the backend.

FROM node:22.23.4-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
# Same npm as development and CI (packageManager), so blocked install scripts stay blocked.
RUN npm install -g "$(node -p "require('./package.json').packageManager")" \
 && npm ci --no-audit --no-fund
COPY . .
RUN npx ng build

FROM nginxinc/nginx-unprivileged:1.29-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist/agentshelf/browser/ /usr/share/nginx/html/
EXPOSE 8080
HEALTHCHECK --interval=10s --timeout=3s --retries=5 \
  CMD wget -q -O /dev/null http://127.0.0.1:8080/healthz || exit 1
