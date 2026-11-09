import { HttpTestingController, TestRequest } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { MatDialog } from '@angular/material/dialog';
import { of } from 'rxjs';
import { ProductPage, ProductResponse } from '../../../api/generated/models';
import { appTestProviders } from '../../../testing/test-setup';
import { ProductList } from './product-list';

const PIPE: ProductResponse = {
  id: '0193a0b4-0000-7000-8000-0000000000a1',
  sku: 'PVC-20',
  name: 'PVC pipe 20 mm',
  category: 'Pipes',
  unit: 'm',
  price: { amount: '0.0125', currency: 'USD' },
  stockQty: 120,
  status: 'ACTIVE',
  version: 0,
  updatedAt: '2026-11-09T10:00:00Z',
};

function page(items: ProductResponse[], totalItems = items.length): ProductPage {
  return { items, page: 0, size: 20, totalItems, totalPages: Math.ceil(totalItems / 20) };
}

describe('ProductList', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: appTestProviders() });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  function expectList(): TestRequest {
    return http.expectOne((r) => r.url === '/api/v1/products' && r.method === 'GET');
  }

  async function render() {
    const fixture = TestBed.createComponent(ProductList);
    fixture.detectChanges();
    return { fixture, element: fixture.nativeElement as HTMLElement };
  }

  async function settle(fixture: { whenStable(): Promise<unknown>; detectChanges(): void }) {
    await fixture.whenStable();
    fixture.detectChanges();
  }

  it('asks for the first page of active products and shows them', async () => {
    const { fixture, element } = await render();

    const request = expectList();
    expect(request.request.params.get('status')).toBe('ACTIVE');
    expect(request.request.params.get('page')).toBe('0');
    expect(request.request.params.get('sort')).toBe('NAME');
    request.flush(page([PIPE]));
    await settle(fixture);

    const row = element.querySelector('tr.mat-mdc-row');
    expect(row?.textContent).toContain('PVC-20');
    expect(row?.textContent).toContain('$0.0125');
    expect(row?.textContent).toContain('/ m');
  });

  it('invites the supplier to add a first product when the catalog is empty', async () => {
    const { fixture, element } = await render();

    expectList().flush(page([]));
    await settle(fixture);

    expect(element.querySelector('.empty h2')?.textContent).toContain('No products yet');
  });

  it('switches to archived products', async () => {
    const { fixture, element } = await render();
    expectList().flush(page([PIPE]));
    await settle(fixture);

    // Clicked directly: harnesses wait for the app to be idle, and a pending request keeps it busy.
    const archived = [...element.querySelectorAll('mat-button-toggle button')].find((b) =>
      b.textContent?.includes('Archived'),
    ) as HTMLButtonElement;
    archived.click();
    fixture.detectChanges();

    const request = expectList();
    expect(request.request.params.get('status')).toBe('ARCHIVED');
    request.flush(page([]));
  });

  it('archives after confirmation and reloads the list', async () => {
    vi.spyOn(TestBed.inject(MatDialog), 'open').mockReturnValue({
      afterClosed: () => of(true),
    } as ReturnType<MatDialog['open']>);
    const { fixture, element } = await render();
    expectList().flush(page([PIPE]));
    await settle(fixture);

    (element.querySelector('button[aria-label="Archive PVC-20"]') as HTMLButtonElement).click();

    await vi.waitFor(() =>
      http
        .expectOne((r) => r.method === 'DELETE' && r.url === `/api/v1/products/${PIPE.id}`)
        .flush(null, { status: 204, statusText: 'No Content' }),
    );
    await vi.waitFor(() => expectList().flush(page([])));
  });

  it('does nothing when archiving is cancelled', async () => {
    vi.spyOn(TestBed.inject(MatDialog), 'open').mockReturnValue({
      afterClosed: () => of(false),
    } as ReturnType<MatDialog['open']>);
    const { fixture, element } = await render();
    expectList().flush(page([PIPE]));
    await settle(fixture);

    (element.querySelector('button[aria-label="Archive PVC-20"]') as HTMLButtonElement).click();
    await fixture.whenStable();

    http.expectNone((r) => r.method === 'DELETE');
  });
});
