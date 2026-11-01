# Multi-arch: all base images exist for amd64 and arm64, hence no platform setting.

FROM node:24-alpine AS build
WORKDIR /src
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
RUN npm run build

FROM nginx:1.28-alpine
COPY nginx/default.conf /etc/nginx/conf.d/default.conf
COPY nginx/15-logis-real-ip.sh /docker-entrypoint.d/15-logis-real-ip.sh
COPY --from=build /src/dist /usr/share/nginx/html
