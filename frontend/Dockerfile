# Multi-stage build: compile the Angular SPA, then serve the static output via nginx with an
# /api reverse proxy to the backend (ticket FE-CORE1; replaces the X1 placeholder shell).

# ---- build stage ----
FROM node:22-alpine AS build
WORKDIR /app
COPY package.json package-lock.json* ./
RUN npm ci || npm install
COPY . .
RUN npm run build

# ---- runtime stage ----
FROM nginx:1.27-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
# The @angular-devkit/build-angular:application builder emits the browser bundle under browser/.
COPY --from=build /app/dist/investguide-frontend/browser/ /usr/share/nginx/html/
EXPOSE 80
