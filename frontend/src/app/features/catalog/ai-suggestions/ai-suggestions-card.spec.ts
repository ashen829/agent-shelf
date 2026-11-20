import { HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { EnrichmentJobResponse } from '../../../api/generated/models';
import { appTestProviders, problem } from '../../../testing/test-setup';
import { AiSuggestionsCard } from './ai-suggestions-card';

const JOB: EnrichmentJobResponse = {
  id: '0193a0b4-0000-7000-8000-0000000000e1',
  status: 'SUBMITTED',
  model: 'claude-haiku-5-5',
  productsTotal: 500,
  productsFailed: 0,
  suggestionsCreated: 0,
  suggestionsDropped: 0,
  inputTokens: 0,
  outputTokens: 0,
  cacheReadTokens: 0,
  cacheWriteTokens: 0,
  failureMessage: null,
  createdAt: '2026-11-20T10:00:00Z',
  submittedAt: '2026-11-20T10:00:05Z',
  finishedAt: null,
};

describe('AiSuggestionsCard', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: appTestProviders() });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    vi.useRealTimers();
    http.verify();
  });

  /** whenStable() waits for real timers, so with fake timers just let promises run. */
  async function settle(fixture: { detectChanges(): void; whenStable(): Promise<unknown> }) {
    if (vi.isFakeTimers()) {
      await vi.advanceTimersByTimeAsync(0);
    } else {
      await fixture.whenStable();
    }
    fixture.detectChanges();
  }

  async function render(enabled: boolean, latestJob: EnrichmentJobResponse | null) {
    const fixture = TestBed.createComponent(AiSuggestionsCard);
    http.expectOne('/api/v1/enrichment').flush({ enabled, latestJob });
    await settle(fixture);
    return {
      fixture,
      component: fixture.componentInstance,
      element: fixture.nativeElement as HTMLElement,
    };
  }

  it('stays hidden while the feature is switched off', async () => {
    const { element } = await render(false, null);

    expect(element.textContent?.trim()).toBe('');
  });

  it('starts a job and shows that products are being checked', async () => {
    const { fixture, component, element } = await render(true, null);
    expect(element.querySelector('[role=status]')?.textContent).toContain('Nothing changes');

    const done = component.start();
    const request = http.expectOne('/api/v1/enrichment/jobs');
    expect(request.request.method).toBe('POST');
    request.flush({ ...JOB, status: 'QUEUED', productsTotal: 0 });
    await done;
    fixture.detectChanges();

    expect(element.querySelector('[role=status]')?.textContent).toContain('Checking your products');
    const button = element.querySelector('button') as HTMLButtonElement;
    expect(button.disabled).toBe(true);
    expect(button.textContent).toContain('Checking');
  });

  it('checks again while a job is running and shows the result', async () => {
    vi.useFakeTimers();
    const { fixture, element } = await render(true, JOB);
    expect(element.querySelector('[role=status]')?.textContent).toContain('Checking 500 products');

    await vi.advanceTimersByTimeAsync(AiSuggestionsCard.POLL_MS);
    http.expectOne('/api/v1/enrichment').flush({
      enabled: true,
      latestJob: { ...JOB, status: 'COMPLETED', suggestionsCreated: 42 },
    });
    await vi.advanceTimersByTimeAsync(0);
    fixture.detectChanges();

    expect(element.querySelector('[role=status]')?.textContent).toContain(
      '42 suggestions are ready',
    );
    await vi.advanceTimersByTimeAsync(AiSuggestionsCard.POLL_MS * 2);
    http.expectNone('/api/v1/enrichment');
  });

  it('shows why a job failed and why starting was refused', async () => {
    const { fixture, component, element } = await render(true, {
      ...JOB,
      status: 'FAILED',
      failureMessage: 'AI suggestions took too long. Try again later.',
    });
    expect(element.querySelector('[role=status]')?.textContent).toContain('took too long');

    const done = component.start();
    const refused = problem(409, 'AI suggestions are already being prepared for your catalog.');
    http.expectOne('/api/v1/enrichment/jobs').flush(refused.body, refused.options);
    await done;
    fixture.detectChanges();

    expect(element.querySelector('[role=alert]')?.textContent).toContain('already being prepared');
  });
});
