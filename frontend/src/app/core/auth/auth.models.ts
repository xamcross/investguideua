/** Shared auth/API contract types (mirrors the backend BE-A DTOs). */

export interface UserProfile {
  userId: string;
  email: string;
  emailVerified: boolean;
  tokenBalance: number;
  roles: string[];
}

/** Body of POST /auth/login and POST /auth/refresh. The refresh token is NOT here - it lives in
 *  an HttpOnly cookie set by the backend and is never readable by JS (§10, AC #10). */
export interface AuthResponse {
  accessToken: string;
  expiresInMs: number;
  user: UserProfile;
}

export interface RegisterResponse {
  userId: string;
  email: string;
  emailVerified: boolean;
  message: string;
}

export interface VerifyResponse {
  emailVerified: boolean;
  tokenBalance: number;
  firstVerification: boolean;
  message: string;
}

/** The §5.3 error envelope returned by every backend error. */
export interface ApiErrorEnvelope {
  error: {
    code: string;
    message: string;
    requestId: string;
    details?: Record<string, string>;
  };
}
