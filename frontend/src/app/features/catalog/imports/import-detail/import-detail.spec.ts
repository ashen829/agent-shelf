import { HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { appTestProviders } from '../../../../testing/test-setup';
import { ERRORS, JOB, PREVIEW } from '../import-test-data';
import { ImportDetail } from './import-detail';

describe('ImportDetail', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: appTestProviders() });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    vi.useRealTimers();
    http.verify();
  });

  function render() {
    const fixture = TestBed.createComponent(ImportDetail);
    fixture.componentRef.setInput('id', JOB.id);
    fixture.detectChanges();
    return { fixture, element: fixture.nativeElement as HTMLElement };
  }

  async function shows(
    fixture: { detectChanges(): void; whenStable(): Promise<unknown> },
    check: () => void,
  ) {
    await vi.waitFor(async () => {
      fixture.detectChanges();
      await fixture.whenStable();
      check();
    });
  }

  it('shows the column matching step for a new upload', async () => {
    const { fixture, element } = render();

    http.expectOne(`/api/v1/imports/${JOB.id}`).flush(JOB);
    await vi.waitFor(() => http.expectOne(`/api/v1/imports/${JOB.id}/preview`).flush(PREVIEW));

    await shows(fixture, () => expect(element.querySelector('app-import-mapping')).not.toBeNull());
  });

  it('follows a running import until it is done, then lists the rows to fix', async () => {
    vi.useFakeTimers({ toFake: ['setTimeout', 'clearTimeout'] });
    const { fixture, element } = render();

    http
      .expectOne(`/api/v1/imports/${JOB.id}`)
      .flush({ ...JOB, status: 'RUNNING', rowsProcessed: 1 });
    await shows(fixture, () => expect(element.textContent).toContain('1 of 3 rows done'));

    await vi.advanceTimersByTimeAsync(ImportDetail.POLL_MS);
    http.expectOne(`/api/v1/imports/${JOB.id}`).flush({
      ...JOB,
      status: 'COMPLETED_WITH_ERRORS',
      rowsProcessed: 3,
      rowsCreated: 2,
      rowsFailed: 1,
    });
    await vi.waitFor(() =>
      http.expectOne((r) => r.url === `/api/v1/imports/${JOB.id}/errors`).flush(ERRORS),
    );

    await shows(fixture, () => {
      expect(element.querySelector('.counts')?.textContent).toContain('Added');
      expect(element.querySelector('.errors tbody')?.textContent).toContain('12.5O');
      expect(element.querySelector('.errors tbody td:nth-child(2)')?.textContent).toBe('Price');
    });
    const download = element.querySelector('a[download]');
    expect(download?.getAttribute('href')).toBe(`/api/v1/imports/${JOB.id}/errors.csv`);

    await vi.advanceTimersByTimeAsync(ImportDetail.POLL_MS * 3);
    http.expectNone(`/api/v1/imports/${JOB.id}`);
  });
});
