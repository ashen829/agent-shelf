import { HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { AuthStore } from './core/auth/auth-store';
import { ACCOUNT, appTestProviders, problem } from './testing/test-setup';

describe('App routes', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: appTestProviders() });
    http = TestBed.inject(HttpTestingController);
  });

  async function signedIn(): Promise<void> {
    const done = TestBed.inject(AuthStore).refresh();
    http.expectOne('/api/v1/auth/me').flush(ACCOUNT);
    await done;
  }

  async function signedOut(): Promise<void> {
    const done = TestBed.inject(AuthStore).refresh();
    const { body, options } = problem(401, 'Authentication required.');
    http.expectOne('/api/v1/auth/me').flush(body, options);
    await done;
  }

  it('shows the shell with the company name and the home page when signed in', async () => {
    await signedIn();
    const harness = await RouterTestingHarness.create('/');
    const page = harness.fixture.nativeElement as HTMLElement;

    expect(page.querySelector('mat-toolbar .brand')?.textContent).toContain('AgentShelf');
    expect(page.querySelector('mat-toolbar .account')?.textContent).toContain('Acme Hardware');
    expect(page.querySelector('main h1')?.textContent).toContain('Welcome to AgentShelf');
  });

  it('sends signed-out users to login and remembers where they were going', async () => {
    await signedOut();
    await RouterTestingHarness.create('/somewhere');

    expect(TestBed.inject(Router).url).toBe('/login?returnUrl=%2F');
  });

  it('keeps signed-in users away from the login page', async () => {
    await signedIn();
    await RouterTestingHarness.create('/login');

    expect(TestBed.inject(Router).url).toBe('/');
  });
});
