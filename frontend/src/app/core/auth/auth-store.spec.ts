import { HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ACCOUNT, appTestProviders, problem } from '../../testing/test-setup';
import { AuthStore } from './auth-store';

describe('AuthStore', () => {
  let store: AuthStore;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: appTestProviders() });
    store = TestBed.inject(AuthStore);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('is signed in when /me returns the account', async () => {
    const done = store.refresh();
    // Exact same-origin path: guards against "//api/..." URLs.
    http.expectOne('/api/v1/auth/me').flush(ACCOUNT);
    await done;

    expect(store.isSignedIn()).toBe(true);
    expect(store.account()?.supplier.name).toBe('Acme Hardware');
  });

  it('is signed out when /me returns 401', async () => {
    const done = store.refresh();
    const { body, options } = problem(401, 'Authentication required.');
    http.expectOne('/api/v1/auth/me').flush(body, options);
    await done;

    expect(store.isSignedIn()).toBe(false);
    expect(store.account()).toBeNull();
  });

  it('is signed out when the server cannot be reached', async () => {
    const done = store.refresh();
    http.expectOne('/api/v1/auth/me').error(new ProgressEvent('error'));
    await done;

    expect(store.isSignedIn()).toBe(false);
  });

  it('logs in and out', async () => {
    const login = store.login({ email: 'owner@acme.example', password: 'a long password' });
    const request = http.expectOne('/api/v1/auth/login');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({
      email: 'owner@acme.example',
      password: 'a long password',
    });
    request.flush(ACCOUNT);
    await login;
    expect(store.isSignedIn()).toBe(true);

    const logout = store.logout();
    http.expectOne('/api/v1/auth/logout').flush(null, { status: 204, statusText: 'No Content' });
    await logout;
    expect(store.isSignedIn()).toBe(false);
  });

  it('is signed out locally even if the logout call fails', async () => {
    const login = store.login({ email: 'owner@acme.example', password: 'a long password' });
    http.expectOne('/api/v1/auth/login').flush(ACCOUNT);
    await login;

    const logout = store.logout();
    http.expectOne('/api/v1/auth/logout').error(new ProgressEvent('error'));
    await expect(logout).rejects.toBeTruthy();
    expect(store.isSignedIn()).toBe(false);
  });
});
