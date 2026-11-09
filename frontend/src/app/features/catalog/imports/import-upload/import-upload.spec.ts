import { HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { appTestProviders, problem } from '../../../../testing/test-setup';
import { JOB, PREVIEW } from '../import-test-data';
import { ImportUpload } from './import-upload';

describe('ImportUpload', () => {
  let http: HttpTestingController;
  let navigate: ReturnType<typeof vi.spyOn>;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: appTestProviders() });
    http = TestBed.inject(HttpTestingController);
    navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
  });

  afterEach(() => http.verify());

  function render() {
    const fixture = TestBed.createComponent(ImportUpload);
    fixture.detectChanges();
    return {
      fixture,
      component: fixture.componentInstance,
      element: fixture.nativeElement as HTMLElement,
    };
  }

  it('uploads the file with its name and opens the import', async () => {
    const { component } = render();
    const file = new File(['sku,name,price\nA,B,1\n'], 'prices.csv', { type: 'text/csv' });

    const done = component.upload(file);
    const request = http.expectOne('/api/v1/imports');
    expect(request.request.method).toBe('POST');
    const body = request.request.body as FormData;
    expect((body.get('file') as File).name).toBe('prices.csv');
    request.flush({ importJob: JOB, preview: PREVIEW }, { status: 201, statusText: 'Created' });
    await done;

    expect(navigate).toHaveBeenCalledWith(['/catalog/imports', JOB.id]);
  });

  it('refuses other file types and files over 10 MB before uploading', async () => {
    const { component, fixture, element } = render();

    await component.upload(new File(['x'], 'prices.xlsx'));
    fixture.detectChanges();
    expect(element.querySelector('[role=alert]')?.textContent).toContain('CSV UTF-8');

    const big = new File(['x'], 'big.csv');
    Object.defineProperty(big, 'size', { value: 10 * 1024 * 1024 + 1 });
    await component.upload(big);
    fixture.detectChanges();
    expect(element.querySelector('[role=alert]')?.textContent).toContain('larger than 10 MB');

    http.expectNone('/api/v1/imports');
  });

  it('shows why the server rejected the file', async () => {
    const { component, fixture, element } = render();

    const done = component.upload(new File(['x'], 'latin1.csv'));
    const { body, options } = problem(
      422,
      'The file isn\'t saved as UTF-8. In Excel, use Save As and choose "CSV UTF-8".',
    );
    http.expectOne('/api/v1/imports').flush(body, options);
    await done;
    fixture.detectChanges();

    expect(element.querySelector('[role=alert]')?.textContent).toContain('saved as UTF-8');
    expect(navigate).not.toHaveBeenCalled();
  });
});
