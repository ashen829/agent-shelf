import { HttpClient } from '@angular/common/http';
import { HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { appTestProviders, problem } from '../../testing/test-setup';
import { expectsUnauthenticated } from './auth-context';
import { AuthStore } from './auth-store';

describe('authInterceptor', () => {
  let http: HttpClient;
  let backend: HttpTestingController;
  let navigate: ReturnType<typeof vi.spyOn>;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: appTestProviders() });
    http = TestBed.inject(HttpClient);
    backend = TestBed.inject(HttpTestingController);
    navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
  });

  afterEach(() => backend.verify());

  it('sends the user to login when the session has expired', async () => {
    const clear = vi.spyOn(TestBed.inject(AuthStore), 'clear');
    const call = firstValueFrom(http.get('/api/v1/products'));
    const { body, options } = problem(401, 'Authentication required.');
    backend.expectOne('/api/v1/products').flush(body, options);

    await expect(call).rejects.toBeTruthy();
    expect(clear).toHaveBeenCalled();
    expect(navigate).toHaveBeenCalledWith(['/login'], { queryParams: { returnUrl: '/' } });
  });

  it('ignores 401 on calls that expect it, such as /me and login', async () => {
    const call = firstValueFrom(http.get('/api/v1/auth/me', { context: expectsUnauthenticated() }));
    const { body, options } = problem(401, 'Authentication required.');
    backend.expectOne('/api/v1/auth/me').flush(body, options);

    await expect(call).rejects.toBeTruthy();
    expect(navigate).not.toHaveBeenCalled();
  });

  it('leaves other errors alone', async () => {
    const call = firstValueFrom(http.get('/api/v1/products'));
    const { body, options } = problem(500, 'An unexpected error occurred.');
    backend.expectOne('/api/v1/products').flush(body, options);

    await expect(call).rejects.toBeTruthy();
    expect(navigate).not.toHaveBeenCalled();
  });
});
