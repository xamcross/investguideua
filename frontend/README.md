# InvestGuideUA — Frontend

This directory is the Angular 17+ (standalone components) SPA.

**X1 status:** placeholder. X1 only requires a static SPA that builds and is served by Docker
Compose, with the backend reachable at `/api/v1`. The full Angular application — routing, auth
interceptors, feature pages — is delivered by the **FE-CORE1** ticket and the rest of the FE-*
epic in `TASKS.md`.

The current `public/index.html` is a minimal static shell that calls `GET /api/v1/ping` to
prove backend connectivity. When FE-CORE1 lands, replace this folder with the generated Angular
workspace and have `Dockerfile` run `ng build`, serving `dist/` via nginx with the same
`/api/v1` proxy configured in `nginx.conf`.
