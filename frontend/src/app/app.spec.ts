import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { routes } from './app.routes';

describe('App routes', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideRouter(routes)] });
  });

  async function render(url: string): Promise<HTMLElement> {
    const harness = await RouterTestingHarness.create(url);
    return harness.fixture.nativeElement as HTMLElement;
  }

  it('shows the shell toolbar and the home page at /', async () => {
    const page = await render('/');

    expect(page.querySelector('mat-toolbar .brand')?.textContent).toContain('AgentShelf');
    expect(page.querySelector('main h1')?.textContent).toContain('Welcome to AgentShelf');
  });

  it('redirects unknown paths to the home page', async () => {
    const page = await render('/does-not-exist');

    expect(page.querySelector('main h1')?.textContent).toContain('Welcome to AgentShelf');
  });
});
