import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { EnvironmentProviders, Provider } from '@angular/core';
import { provideRouter, withComponentInputBinding } from '@angular/router';
import { provideApiConfiguration } from '../api/generated/api-configuration';
import { AccountResponse } from '../api/generated/models';
import { routes } from '../app.routes';
import { authInterceptor } from '../core/auth/auth-interceptor';

/** The same HTTP and routing setup as the app, with HTTP calls answered by the test. */
export function appTestProviders(): (Provider | EnvironmentProviders)[] {
  return [
    provideRouter(routes, withComponentInputBinding()),
    provideHttpClient(withInterceptors([authInterceptor])),
    provideHttpClientTesting(),
    provideApiConfiguration(''),
  ];
}

export const ACCOUNT: AccountResponse = {
  userId: '0193a0b4-0000-7000-8000-000000000001',
  email: 'owner@acme.example',
  role: 'OWNER',
  supplier: {
    id: '0193a0b4-0000-7000-8000-000000000002',
    name: 'Acme Hardware',
    slug: 'acme-hardware',
    currency: 'EUR',
  },
};

/** A Spring problem detail body as the backend sends it. */
export function problem(status: number, detail: string, errors?: unknown[]) {
  return {
    body: { type: 'about:blank', status, detail, requestId: 'test-request-1', errors },
    options: { status, statusText: 'Error' },
  };
}
