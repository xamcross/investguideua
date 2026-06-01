import { Injectable, signal } from '@angular/core';
import { ErrorAction, ErrorSeverity, ErrorUx } from './error-ux.util';

export interface Notification {
  id: number;
  title: string;
  message: string;
  severity: ErrorSeverity;
  action?: ErrorAction;
  requestId?: string;
}

/**
 * In-memory notification (toast) store for the global error surface (ticket FE-CORE4).
 *
 * Signal-based so the host component renders reactively. Toasts are ephemeral session state only —
 * never persisted to web storage (consistent with the FE-CORE2 no-web-storage posture).
 */
@Injectable({ providedIn: 'root' })
export class NotificationService {
  private nextId = 1;
  private readonly _toasts = signal<Notification[]>([]);
  readonly toasts = this._toasts.asReadonly();

  /** Push a mapped error onto the toast stack. Returns the toast id. */
  pushError(ux: ErrorUx): number {
    const id = this.nextId++;
    const toast: Notification = {
      id,
      title: ux.title,
      message: ux.message,
      severity: ux.severity,
      action: ux.action,
      requestId: ux.requestId,
    };
    this._toasts.update((list) => [...list, toast]);
    return id;
  }

  dismiss(id: number): void {
    this._toasts.update((list) => list.filter((t) => t.id !== id));
  }

  clear(): void {
    this._toasts.set([]);
  }
}
