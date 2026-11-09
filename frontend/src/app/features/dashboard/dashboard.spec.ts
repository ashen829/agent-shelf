import { HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { AuthStore } from '../../core/auth/auth-store';
import { ACCOUNT, appTestProviders } from '../../testing/test-setup';
import { Dashboard } from './dashboard';

describe('Dashboard', () => {
  beforeEach(async () => {
    TestBed.configureTestingModule({ providers: appTestProviders() });
    const done = TestBed.inject(AuthStore).refresh();
    TestBed.inject(HttpTestingController).expectOne('/api/v1/auth/me').flush(ACCOUNT);
    await done;
  });

  async function render(): Promise<HTMLElement> {
    const fixture = TestBed.createComponent(Dashboard);
    await fixture.whenStable();
    return fixture.nativeElement as HTMLElement;
  }

  it('greets the supplier and shows their currency', async () => {
    const page = await render();

    expect(page.querySelector('h1')?.textContent).toContain('Welcome, Acme Hardware');
    expect(page.textContent).toContain('Prices in EUR (Euro)');
  });

  it('marks only account creation as done and labels the later steps', async () => {
    const steps = [...(await render()).querySelectorAll('.step')];

    expect(steps).toHaveLength(5);
    expect(steps.filter((s) => s.classList.contains('done'))).toHaveLength(1);
    expect(steps[0].textContent).toContain('(done)');
    expect(steps[1].querySelector('a')?.getAttribute('href')).toBe('/catalog/import');
    expect(steps[2].textContent).toContain('Coming in M2');
  });

  it('explains the empty activity area', async () => {
    expect((await render()).textContent).toContain('No activity yet.');
  });
});
