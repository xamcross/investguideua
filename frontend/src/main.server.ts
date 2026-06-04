import { bootstrapApplication } from '@angular/platform-browser';
import { AppComponent } from './app/app.component';
import { config } from './app/app.config.server';

/**
 * Server/prerender bootstrap entry (feature 006-seo-optimization). Invoked by the Angular
 * `application` builder during build-time prerendering only. The produced server bundle
 * (`dist/investguide-frontend/server/`) is NOT deployed - production ships `browser/` only.
 */
const bootstrap = () => bootstrapApplication(AppComponent, config);

export default bootstrap;
