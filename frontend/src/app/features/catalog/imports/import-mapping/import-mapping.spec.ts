import { TestbedHarnessEnvironment } from '@angular/cdk/testing/testbed';
import { HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { MatButtonHarness } from '@angular/material/button/testing';
import { MatCheckboxHarness } from '@angular/material/checkbox/testing';
import { MatDialog } from '@angular/material/dialog';
import { MatSelectHarness } from '@angular/material/select/testing';
import { of } from 'rxjs';
import { ImportResponse } from '../../../../api/generated/models';
import { appTestProviders } from '../../../../testing/test-setup';
import { FIELDS } from '../import-labels';
import { JOB, PREVIEW } from '../import-test-data';

/** Field selects in form order (the field is bound as a property, not an HTML attribute). */
function selectFor(selects: MatSelectHarness[], key: (typeof FIELDS)[number]['key']) {
  return selects[FIELDS.findIndex((f) => f.key === key)];
}
import { ImportMapping } from './import-mapping';

describe('ImportMapping', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: appTestProviders() });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  async function render() {
    const fixture = TestBed.createComponent(ImportMapping);
    fixture.componentRef.setInput('job', JOB);
    fixture.componentRef.setInput('preview', PREVIEW);
    const started: ImportResponse[] = [];
    fixture.componentInstance.started.subscribe((j) => started.push(j));
    fixture.detectChanges();
    const loader = TestbedHarnessEnvironment.loader(fixture);
    return { fixture, loader, started, element: fixture.nativeElement as HTMLElement };
  }

  it('starts from the suggested columns and shows an example value', async () => {
    const { loader, element } = await render();

    const sku = selectFor(await loader.getAllHarnesses(MatSelectHarness), 'sku');
    expect(await sku.getValueText()).toBe('Item code');
    expect(element.textContent).toContain('For example: PVC-20');
  });

  it('starts the import with the mapping and options', async () => {
    const { loader, started } = await render();

    await (await loader.getHarness(MatButtonHarness.with({ text: /^Import 3 rows/ }))).click();

    const request = http.expectOne(`/api/v1/imports/${JOB.id}/start`);
    expect(request.request.body).toEqual({
      mapping: {
        sku: 0,
        name: 1,
        price: 2,
        stockQty: 3,
        unit: null,
        category: null,
        description: null,
      },
      decimalSeparator: '.',
      completeCatalog: false,
    });
    request.flush({ ...JOB, status: 'QUEUED' }, { status: 202, statusText: 'Accepted' });
    await vi.waitFor(() => expect(started.map((j) => j.status)).toEqual(['QUEUED']));
  });

  it('needs a price column and each column only once', async () => {
    const { loader, element, fixture } = await render();
    const selects = await loader.getAllHarnesses(MatSelectHarness);
    const price = selectFor(selects, 'price');
    await price.open();
    await price.clickOptions({ text: '— Choose a column —' });
    const unit = selectFor(selects, 'unit');
    await unit.open();
    await unit.clickOptions({ text: 'Item code' });

    await (await loader.getHarness(MatButtonHarness.with({ text: /^Import/ }))).click();
    await fixture.whenStable();

    http.expectNone(`/api/v1/imports/${JOB.id}/start`);
    expect(element.textContent).toContain('Choose the column for price.');
    expect(element.textContent).toContain('Each column can only be used for one field.');
  });

  it('asks before archiving products that are not in the file', async () => {
    const open = vi.spyOn(TestBed.inject(MatDialog), 'open').mockReturnValue({
      afterClosed: () => of(false),
    } as ReturnType<MatDialog['open']>);
    const { loader } = await render();

    await (await loader.getHarness(MatCheckboxHarness)).check();
    await (await loader.getHarness(MatButtonHarness.with({ text: /^Import/ }))).click();
    http
      .expectOne((r) => r.url === '/api/v1/products')
      .flush({ items: [], page: 0, size: 1, totalItems: 12, totalPages: 12 });

    await vi.waitFor(() => expect(open).toHaveBeenCalled());
    const data = (open.mock.calls[0][1] as { data: { message: string } }).data;
    expect(data.message).toContain('You have 12 active products and this file has 3 rows.');
    http.expectNone(`/api/v1/imports/${JOB.id}/start`);
  });
});
