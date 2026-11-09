import { HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { appTestProviders } from '../../../../testing/test-setup';
import { JOB } from '../import-test-data';
import { ImportHistory } from './import-history';

describe('ImportHistory', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: appTestProviders() });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  async function render(items: (typeof JOB)[]) {
    const fixture = TestBed.createComponent(ImportHistory);
    fixture.detectChanges();
    http
      .expectOne((r) => r.url === '/api/v1/imports')
      .flush({ items, page: 0, size: 20, totalItems: items.length, totalPages: 1 });
    await vi.waitFor(async () => {
      fixture.detectChanges();
      await fixture.whenStable();
      expect((fixture.nativeElement as HTMLElement).querySelector('mat-progress-bar')).toBeNull();
    });
    return fixture.nativeElement as HTMLElement;
  }

  it('lists imports with their status and result', async () => {
    const element = await render([
      {
        ...JOB,
        status: 'COMPLETED_WITH_ERRORS',
        startedAt: '2026-11-09T10:01:00Z',
        rowsCreated: 480,
        rowsUpdated: 15,
        rowsFailed: 5,
      },
    ]);

    const row = element.querySelector('tr.mat-mdc-row');
    expect(row?.textContent).toContain('prices.csv');
    expect(row?.textContent).toContain('Done, some rows have errors');
    expect(row?.textContent).toContain('480 added · 15 updated');
    expect(row?.textContent).toContain('5 with errors');
  });

  it('suggests a first import when there are none', async () => {
    const element = await render([]);

    expect(element.querySelector('.empty h2')?.textContent).toContain('No imports yet');
  });
});
