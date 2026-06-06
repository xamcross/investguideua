import { Routes } from '@angular/router';
import { adminGuard, authGuard, verifiedGuard } from './core/auth/auth.guards';

/**
 * Application routes (tickets FE-CORE1, FE-CORE3). Every feature area is lazy-loaded via
 * `loadComponent`.
 *
 * Route guards (FE-CORE3): `authGuard` protects authenticated areas (History, Account, Payment
 * status); `verifiedGuard` additionally gates the token-spending areas (Search, Buy Tokens) on a
 * freshly re-checked verified email; `adminGuard` gates the ADMIN-only Providers area on the ADMIN
 * role (008-providers-admin-only). Guards are best-effort UX - the backend `401`/`403` remain the
 * source of truth and are surfaced by FE-CORE4.
 *
 * `title` holds a translation KEY (e.g. `title.search`); TranslatedTitleStrategy resolves it
 * via ngx-translate and re-applies it when the language changes.
 */
export const routes: Routes = [
  {
    path: '',
    title: 'seo.home.title',
    loadComponent: () =>
      import('./features/landing/landing.component').then((m) => m.LandingComponent),
  },
  {
    path: 'register',
    title: 'title.register',
    loadComponent: () =>
      import('./features/auth/register.component').then((m) => m.RegisterComponent),
  },
  {
    path: 'login',
    title: 'title.signIn',
    loadComponent: () => import('./features/auth/login.component').then((m) => m.LoginComponent),
  },
  {
    path: 'verify',
    title: 'title.verify',
    loadComponent: () => import('./features/auth/verify.component').then((m) => m.VerifyComponent),
  },
  {
    path: 'search',
    title: 'title.search',
    canActivate: [verifiedGuard],
    loadComponent: () =>
      import('./features/search/search.component').then((m) => m.SearchComponent),
  },
  {
    path: 'history',
    title: 'title.history',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./features/history/history.component').then((m) => m.HistoryComponent),
  },
  {
    path: 'history/:id',
    title: 'title.historyDetail',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./features/history/history-detail.component').then((m) => m.HistoryDetailComponent),
  },
  {
    path: 'tokens',
    title: 'title.tokens',
    canActivate: [verifiedGuard],
    loadComponent: () =>
      import('./features/payments/tokens.component').then((m) => m.TokensComponent),
  },
  {
    path: 'payments/result',
    title: 'title.paymentStatus',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./features/payments/payment-result.component').then((m) => m.PaymentResultComponent),
  },
  {
    path: 'account',
    title: 'title.account',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./features/account/account.component').then((m) => m.AccountComponent),
  },
  {
    // Providers is ADMIN-only (008-providers-admin-only): authGuard ensures a session, adminGuard
    // enforces the ADMIN role (non-admins are redirected to /account). The API also enforces ADMIN.
    path: 'providers',
    title: 'title.providers',
    canActivate: [authGuard, adminGuard],
    loadComponent: () =>
      import('./features/providers/providers.component').then((m) => m.ProvidersComponent),
  },
  // Public articles section (feature 006-seo-optimization). No guard - indexable, prerendered.
  {
    path: 'articles',
    title: 'seo.articles.title',
    loadComponent: () =>
      import('./features/articles/articles-index.component').then((m) => m.ArticlesIndexComponent),
  },
  {
    path: 'articles/:slug',
    loadComponent: () =>
      import('./features/articles/article-detail.component').then((m) => m.ArticleDetailComponent),
  },
  // Public legal screens linked from the footer (feature 004). One shared LegalDocumentComponent
  // renders both; `data.doc` selects the i18n content namespace via component-input binding. No guard.
  {
    path: 'terms',
    title: 'seo.terms.title',
    data: { doc: 'terms' },
    loadComponent: () =>
      import('./features/legal/legal-document.component').then((m) => m.LegalDocumentComponent),
  },
  {
    path: 'privacy',
    title: 'seo.privacy.title',
    data: { doc: 'privacy' },
    loadComponent: () =>
      import('./features/legal/legal-document.component').then((m) => m.LegalDocumentComponent),
  },
  {
    path: '**',
    title: 'title.notFound',
    loadComponent: () =>
      import('./features/not-found/not-found.component').then((m) => m.NotFoundComponent),
  },
];
