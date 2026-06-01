import { Routes } from '@angular/router';
import { authGuard, verifiedGuard } from './core/auth/auth.guards';

/**
 * Application routes (tickets FE-CORE1, FE-CORE3). Every feature area is lazy-loaded via
 * `loadComponent`.
 *
 * Route guards (FE-CORE3): `authGuard` protects authenticated areas (History, Account, Providers,
 * Payment status); `verifiedGuard` additionally gates the token-spending areas (Search, Buy Tokens)
 * on a freshly re-checked verified email. Guards are best-effort UX — the backend `401`/`403` remain
 * the source of truth and are surfaced by FE-CORE4.
 */
export const routes: Routes = [
  {
    path: '',
    title: 'InvestGuideUA',
    loadComponent: () =>
      import('./features/landing/landing.component').then((m) => m.LandingComponent),
  },
  {
    path: 'register',
    title: 'Register - InvestGuideUA',
    loadComponent: () =>
      import('./features/auth/register.component').then((m) => m.RegisterComponent),
  },
  {
    path: 'login',
    title: 'Sign in - InvestGuideUA',
    loadComponent: () => import('./features/auth/login.component').then((m) => m.LoginComponent),
  },
  {
    path: 'verify',
    title: 'Verify email - InvestGuideUA',
    loadComponent: () => import('./features/auth/verify.component').then((m) => m.VerifyComponent),
  },
  {
    path: 'search',
    title: 'Search - InvestGuideUA',
    canActivate: [verifiedGuard],
    loadComponent: () =>
      import('./features/search/search.component').then((m) => m.SearchComponent),
  },
  {
    path: 'history',
    title: 'History - InvestGuideUA',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./features/history/history.component').then((m) => m.HistoryComponent),
  },
  {
    path: 'history/:id',
    title: 'Search detail - InvestGuideUA',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./features/history/history-detail.component').then((m) => m.HistoryDetailComponent),
  },
  {
    path: 'tokens',
    title: 'Buy tokens - InvestGuideUA',
    canActivate: [verifiedGuard],
    loadComponent: () =>
      import('./features/payments/tokens.component').then((m) => m.TokensComponent),
  },
  {
    path: 'payments/result',
    title: 'Payment status - InvestGuideUA',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./features/payments/payment-result.component').then((m) => m.PaymentResultComponent),
  },
  {
    path: 'account',
    title: 'Account - InvestGuideUA',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./features/account/account.component').then((m) => m.AccountComponent),
  },
  {
    path: 'providers',
    title: 'Providers - InvestGuideUA',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./features/providers/providers.component').then((m) => m.ProvidersComponent),
  },
  {
    path: '**',
    title: 'Not found - InvestGuideUA',
    loadComponent: () =>
      import('./features/not-found/not-found.component').then((m) => m.NotFoundComponent),
  },
];
