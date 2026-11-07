# Multi-arch: the build stage runs natively on the build machine (its output is platform-independent),
# only the runtime stage is built per target platform. All base images exist for amd64 and arm64.

FROM --platform=$BUILDPLATFORM node:24-alpine AS build
WORKDIR /src
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
RUN npm run build

FROM nginx:1.28-alpine
COPY nginx/default.conf /etc/nginx/conf.d/default.conf
COPY nginx/15-logis-real-ip.sh /docker-entrypoint.d/15-logis-real-ip.sh
COPY --from=build /src/dist /usr/share/nginx/html
