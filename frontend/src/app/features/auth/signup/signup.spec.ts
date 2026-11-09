import { TestbedHarnessEnvironment } from '@angular/cdk/testing/testbed';
import { HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { MatButtonHarness } from '@angular/material/button/testing';
import { MatInputHarness } from '@angular/material/input/testing';
import { MatSelectHarness } from '@angular/material/select/testing';
import { Router } from '@angular/router';
import { ACCOUNT, appTestProviders, problem } from '../../../testing/test-setup';
import { Signup } from './signup';

describe('Signup', () => {
  let http: HttpTestingController;
  let navigateByUrl: ReturnType<typeof vi.spyOn>;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: appTestProviders() });
    http = TestBed.inject(HttpTestingController);
    navigateByUrl = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
  });

  afterEach(() => http.verify());

  async function fillAndSubmit() {
    const fixture = TestBed.createComponent(Signup);
    const loader = TestbedHarnessEnvironment.loader(fixture);
    await (
      await loader.getHarness(MatInputHarness.with({ selector: '[formControlName=companyName]' }))
    ).setValue('  Acme Hardware ');
    const currency = await loader.getHarness(MatSelectHarness);
    await currency.open();
    await currency.clickOptions({ text: /^LKR/ });
    await (
      await loader.getHarness(MatInputHarness.with({ selector: '[type=email]' }))
    ).setValue('owner@acme.example');
    await (
      await loader.getHarness(MatInputHarness.with({ selector: '[type=password]' }))
    ).setValue('a long enough password');
    await (await loader.getHarness(MatButtonHarness.with({ text: 'Create account' }))).click();
    return { fixture, element: fixture.nativeElement as HTMLElement };
  }

  it('creates the account with a trimmed company name and opens the app', async () => {
    await fillAndSubmit();

    const request = http.expectOne('/api/v1/auth/signup');
    expect(request.request.body).toEqual({
      companyName: 'Acme Hardware',
      currency: 'LKR',
      email: 'owner@acme.example',
      password: 'a long enough password',
    });
    request.flush(ACCOUNT, { status: 201, statusText: 'Created' });

    await vi.waitFor(() => expect(navigateByUrl).toHaveBeenCalledWith('/'));
  });

  it('shows server field errors under the matching inputs', async () => {
    const { fixture, element } = await fillAndSubmit();

    const { body, options } = problem(400, 'The request has invalid fields.', [
      { field: 'email', message: 'must be a well-formed email address' },
    ]);
    http.expectOne('/api/v1/auth/signup').flush(body, options);

    await vi.waitFor(async () => {
      await fixture.whenStable();
      expect(element.querySelector('mat-error')?.textContent).toContain(
        'must be a well-formed email address',
      );
    });
    expect(element.querySelector('[role=alert]')).toBeNull();
  });

  it('explains when the email is already registered', async () => {
    const { fixture, element } = await fillAndSubmit();

    const { body, options } = problem(409, 'An account with this email already exists.');
    http.expectOne('/api/v1/auth/signup').flush(body, options);

    await vi.waitFor(async () => {
      await fixture.whenStable();
      expect(element.querySelector('[role=alert]')?.textContent).toContain(
        'An account with this email already exists.',
      );
    });
    expect(navigateByUrl).not.toHaveBeenCalled();
  });
});
