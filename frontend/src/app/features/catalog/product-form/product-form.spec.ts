import { TestbedHarnessEnvironment } from '@angular/cdk/testing/testbed';
import { HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { MatButtonHarness } from '@angular/material/button/testing';
import { MatInputHarness } from '@angular/material/input/testing';
import { Router } from '@angular/router';
import { ProductResponse } from '../../../api/generated/models';
import { appTestProviders, problem } from '../../../testing/test-setup';
import { ProductForm } from './product-form';

const SAVED: ProductResponse = {
  id: '0193a0b4-0000-7000-8000-0000000000b2',
  sku: 'PVC-20',
  name: 'PVC pipe 20 mm',
  description: null,
  category: 'Pipes',
  unit: 'm',
  price: { amount: '4.5000', currency: 'USD' },
  stockQty: 100,
  status: 'ACTIVE',
  version: 3,
  updatedAt: '2026-11-09T10:00:00Z',
};

describe('ProductForm', () => {
  let http: HttpTestingController;
  let navigateByUrl: ReturnType<typeof vi.spyOn>;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: appTestProviders() });
    http = TestBed.inject(HttpTestingController);
    navigateByUrl = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
  });

  afterEach(() => http.verify());

  async function render(id?: string) {
    const fixture = TestBed.createComponent(ProductForm);
    if (id) {
      fixture.componentRef.setInput('id', id);
    }
    fixture.detectChanges();
    const loader = TestbedHarnessEnvironment.loader(fixture);
    const input = (name: string) =>
      loader.getHarness(MatInputHarness.with({ selector: `[formControlName=${name}]` }));
    return { fixture, loader, input, element: fixture.nativeElement as HTMLElement };
  }

  it('adds a product with trimmed text and empty optional fields as null', async () => {
    const { loader, input } = await render();

    await (await input('sku')).setValue(' PVC-20 ');
    await (await input('name')).setValue(' PVC pipe 20 mm ');
    await (await input('price')).setValue('0.0125');
    await (await input('stockQty')).setValue('120');
    await (await loader.getHarness(MatButtonHarness.with({ text: 'Add product' }))).click();

    const request = http.expectOne('/api/v1/products');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({
      sku: 'PVC-20',
      name: 'PVC pipe 20 mm',
      description: null,
      category: null,
      unit: null,
      price: '0.0125',
      stockQty: 120,
    });
    request.flush(SAVED, { status: 201, statusText: 'Created' });
    await vi.waitFor(() => expect(navigateByUrl).toHaveBeenCalledWith('/catalog'));
  });

  it('rejects a price with too many decimals before sending anything', async () => {
    const { loader, input, element, fixture } = await render();

    await (await input('sku')).setValue('PVC-20');
    await (await input('name')).setValue('Pipe');
    await (await input('price')).setValue('1.23456');
    await (await loader.getHarness(MatButtonHarness.with({ text: 'Add product' }))).click();
    await fixture.whenStable();

    http.expectNone('/api/v1/products');
    expect(element.textContent).toContain('at most 4 decimals');
  });

  it('shows a duplicate SKU under the SKU field', async () => {
    const { loader, input, element, fixture } = await render();
    await (await input('sku')).setValue('PVC-20');
    await (await input('name')).setValue('Pipe');
    await (await input('price')).setValue('1');
    await (await loader.getHarness(MatButtonHarness.with({ text: 'Add product' }))).click();

    const { body, options } = problem(409, 'Another product already uses SKU PVC-20.', [
      { field: 'sku', message: 'Another product already uses SKU PVC-20.' },
    ]);
    http.expectOne('/api/v1/products').flush(body, options);

    await vi.waitFor(async () => {
      await fixture.whenStable();
      expect(element.querySelector('mat-error')?.textContent).toContain('already uses SKU');
    });
    expect(navigateByUrl).not.toHaveBeenCalled();
  });

  it('loads a product for editing and saves it with the loaded version', async () => {
    const { loader, input, fixture } = await render(SAVED.id);

    http.expectOne(`/api/v1/products/${SAVED.id}`).flush(SAVED);
    await fixture.whenStable();
    expect(await (await input('price')).getValue()).toBe('4.50');

    await (await input('price')).setValue('4.75');
    await (await loader.getHarness(MatButtonHarness.with({ text: 'Save changes' }))).click();

    const request = http.expectOne(`/api/v1/products/${SAVED.id}`);
    expect(request.request.method).toBe('PUT');
    expect(request.request.body).toMatchObject({ price: '4.75', version: 3, unit: 'm' });
    request.flush({ ...SAVED, version: 4 });
    await vi.waitFor(() => expect(navigateByUrl).toHaveBeenCalledWith('/catalog'));
  });

  it('explains a lost update and offers to load the latest version', async () => {
    const { loader, element, fixture } = await render(SAVED.id);
    http.expectOne(`/api/v1/products/${SAVED.id}`).flush(SAVED);
    await fixture.whenStable();

    await (await loader.getHarness(MatButtonHarness.with({ text: 'Save changes' }))).click();
    const { body, options } = problem(
      409,
      'This record was changed by someone else. Reload it and try again.',
    );
    http.expectOne(`/api/v1/products/${SAVED.id}`).flush(body, options);

    await vi.waitFor(async () => {
      await fixture.whenStable();
      expect(element.querySelector('[role=alert]')?.textContent).toContain(
        'Someone else changed this product',
      );
    });
    await (
      await loader.getHarness(MatButtonHarness.with({ text: 'Load the latest version' }))
    ).click();
    http.expectOne(`/api/v1/products/${SAVED.id}`).flush({ ...SAVED, version: 5 });
  });
});
