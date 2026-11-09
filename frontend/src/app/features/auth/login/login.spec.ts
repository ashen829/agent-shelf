import { TestbedHarnessEnvironment } from '@angular/cdk/testing/testbed';
import { HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { MatButtonHarness } from '@angular/material/button/testing';
import { MatInputHarness } from '@angular/material/input/testing';
import { Router } from '@angular/router';
import { ACCOUNT, appTestProviders, problem } from '../../../testing/test-setup';
import { Login } from './login';

describe('Login', () => {
  let http: HttpTestingController;
  let navigateByUrl: ReturnType<typeof vi.spyOn>;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: appTestProviders() });
    http = TestBed.inject(HttpTestingController);
    navigateByUrl = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
  });

  afterEach(() => http.verify());

  async function render(returnUrl?: string) {
    const fixture = TestBed.createComponent(Login);
    if (returnUrl) {
      fixture.componentRef.setInput('returnUrl', returnUrl);
    }
    const loader = TestbedHarnessEnvironment.loader(fixture);
    const email = await loader.getHarness(MatInputHarness.with({ selector: '[type=email]' }));
    const password = await loader.getHarness(MatInputHarness.with({ selector: '[type=password]' }));
    const submit = await loader.getHarness(MatButtonHarness.with({ text: 'Log in' }));
    return { fixture, email, password, submit, element: fixture.nativeElement as HTMLElement };
  }

  it('shows field errors and sends nothing when the form is empty', async () => {
    const { submit, element, fixture } = await render();

    await submit.click();
    await fixture.whenStable();

    http.expectNone('/api/v1/auth/login');
    expect(element.textContent).toContain('Enter your email.');
    expect(element.textContent).toContain('Enter your password.');
  });

  it('signs in and goes back to where the user was going', async () => {
    const { email, password, submit } = await render('/catalog');

    await email.setValue('owner@acme.example');
    await password.setValue('a long password');
    await submit.click();
    http.expectOne('/api/v1/auth/login').flush(ACCOUNT);

    await vi.waitFor(() => expect(navigateByUrl).toHaveBeenCalledWith('/catalog'));
  });

  it('never follows a return URL to another site', async () => {
    const { email, password, submit } = await render('//evil.example');

    await email.setValue('owner@acme.example');
    await password.setValue('a long password');
    await submit.click();
    http.expectOne('/api/v1/auth/login').flush(ACCOUNT);

    await vi.waitFor(() => expect(navigateByUrl).toHaveBeenCalledWith('/'));
  });

  it('shows the server message when the password is wrong', async () => {
    const { email, password, submit, element, fixture } = await render();

    await email.setValue('owner@acme.example');
    await password.setValue('wrong password');
    await submit.click();
    const { body, options } = problem(401, 'Email or password is incorrect.');
    http.expectOne('/api/v1/auth/login').flush(body, options);

    await vi.waitFor(async () => {
      await fixture.whenStable();
      expect(element.querySelector('[role=alert]')?.textContent).toContain(
        'Email or password is incorrect.',
      );
    });
    expect(navigateByUrl).not.toHaveBeenCalled();
  });
});
