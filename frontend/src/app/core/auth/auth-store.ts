import { computed, inject, Injectable, signal } from '@angular/core';
import { AccountResponse, LoginRequest, SignupRequest } from '../../api/generated/models';
import { AuthApi } from '../../api/generated/services';
import { expectsUnauthenticated } from './auth-context';

type AuthState =
  | { readonly status: 'unknown' }
  | { readonly status: 'signed-out' }
  | { readonly status: 'signed-in'; readonly account: AccountResponse };

/** The signed-in account. The session itself lives in an HttpOnly cookie the app can't read. */
@Injectable({ providedIn: 'root' })
export class AuthStore {
  private readonly api = inject(AuthApi);
  private readonly state = signal<AuthState>({ status: 'unknown' });

  readonly account = computed(() => {
    const state = this.state();
    return state.status === 'signed-in' ? state.account : null;
  });
  readonly isSignedIn = computed(() => this.state().status === 'signed-in');

  /** Asks the backend who is signed in. Any failure, including no network, means signed out. */
  async refresh(): Promise<void> {
    try {
      this.signedIn(await this.api.me(undefined, expectsUnauthenticated()));
    } catch {
      this.clear();
    }
  }

  async login(body: LoginRequest): Promise<void> {
    this.signedIn(await this.api.login({ body }, expectsUnauthenticated()));
  }

  async signup(body: SignupRequest): Promise<void> {
    this.signedIn(await this.api.signup({ body }));
  }

  async logout(): Promise<void> {
    try {
      await this.api.logout();
    } finally {
      this.clear();
    }
  }

  clear(): void {
    this.state.set({ status: 'signed-out' });
  }

  private signedIn(account: AccountResponse): void {
    this.state.set({ status: 'signed-in', account });
  }
}
