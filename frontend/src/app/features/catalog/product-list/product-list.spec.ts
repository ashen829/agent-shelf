import { provideHttpClient } from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting,
  TestRequest,
} from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { MatDialog } from '@angular/material/dialog';
import { provideRouter, Router } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { of } from 'rxjs';
import { provideApiConfiguration } from '../../../api/generated/api-configuration';
import { ProductPage, ProductResponse } from '../../../api/generated/models';
import { ProductList, SEARCH_DELAY_MS } from './product-list';

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
    TestBed.configureTestingModule({
      providers: [
        provideRouter([{ path: 'catalog', component: ProductList }]),
        provideHttpClient(),
        provideHttpClientTesting(),
        provideApiConfiguration(''),
      ],
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    vi.useRealTimers();
    http.verify();
  });

  function expectList(): TestRequest {
    return http.expectOne((r) => r.url === '/api/v1/products' && r.method === 'GET');
  }

  async function render(url = '/catalog') {
    const harness = await RouterTestingHarness.create(url);
    http
      .expectOne((r) => r.url === '/api/v1/products/categories')
      .flush({
        items: [{ name: 'Pipes', count: 1 }],
        page: 0,
        size: 500,
        totalItems: 1,
        totalPages: 1,
      });
    const element = harness.fixture.nativeElement as HTMLElement;
    const settle = async () => {
      await harness.fixture.whenStable();
      harness.detectChanges();
    };
    return { harness, element, settle };
  }

  it('asks for the first page of active products and shows them', async () => {
    const { element, settle } = await render();

    const request = expectList();
    expect(request.request.params.get('status')).toBe('ACTIVE');
    expect(request.request.params.get('page')).toBe('0');
    expect(request.request.params.get('sort')).toBe('NAME');
    expect(request.request.params.has('q')).toBe(false);
    request.flush(page([PIPE]));
    await settle();

    const row = element.querySelector('tr.mat-mdc-row');
    expect(row?.textContent).toContain('PVC-20');
    expect(row?.textContent).toContain('$0.0125');
    expect(row?.textContent).toContain('/ m');
  });

  it('takes search, filters and sort from the URL', async () => {
    const { element, settle } = await render(
      '/catalog?q=pvc%2020&category=Pipes&stock=IN_STOCK&sort=PRICE_DESC&page=2&size=50',
    );

    const request = expectList();
    const params = request.request.params;
    expect(params.get('q')).toBe('pvc 20');
    expect(params.get('category')).toBe('Pipes');
    expect(params.get('stock')).toBe('IN_STOCK');
    expect(params.get('sort')).toBe('PRICE_DESC');
    expect(params.get('page')).toBe('2');
    expect(params.get('size')).toBe('50');
    request.flush(page([PIPE]));
    await settle();
    expect((element.querySelector('input[type=search]') as HTMLInputElement).value).toBe('pvc 20');
  });

  it('searches after a short pause in typing and keeps it in the URL', async () => {
    vi.useFakeTimers({ toFake: ['setTimeout', 'clearTimeout'] });
    const { element, settle } = await render();
    expectList().flush(page([PIPE]));
    await settle();

    const input = element.querySelector('input[type=search]') as HTMLInputElement;
    for (const text of ['p', 'pv', 'pvc']) {
      input.value = text;
      input.dispatchEvent(new Event('input'));
    }
    await vi.advanceTimersByTimeAsync(SEARCH_DELAY_MS - 1);
    http.expectNone((r) => r.params.get('q') !== null);

    await vi.advanceTimersByTimeAsync(1);
    await vi.waitFor(() => expect(TestBed.inject(Router).url).toBe('/catalog?q=pvc'));
    expect(expectList().request.params.get('q')).toBe('pvc');
  });

  it('explains when nothing matches and clears the search', async () => {
    const { element, settle } = await render('/catalog?q=zzz&stock=OUT_OF_STOCK');
    expectList().flush(page([]));
    await settle();

    expect(element.querySelector('.empty h2')?.textContent).toContain('No products match');
    expect(element.querySelector('.empty')?.textContent).toContain('"zzz"');

    (element.querySelector('.empty button') as HTMLButtonElement).click();
    await vi.waitFor(() => expect(TestBed.inject(Router).url).toBe('/catalog'));
    expectList().flush(page([PIPE]));
  });

  it('invites the supplier to add a first product when the catalog is empty', async () => {
    const { element, settle } = await render();

    expectList().flush(page([]));
    await settle();

    expect(element.querySelector('.empty h2')?.textContent).toContain('No products yet');
  });

  it('switches to archived products', async () => {
    const { element, settle } = await render();
    expectList().flush(page([PIPE]));
    await settle();

    // Clicked directly: harnesses wait for the app to be idle, and a pending request keeps it busy.
    const archived = [...element.querySelectorAll('mat-button-toggle button')].find((b) =>
      b.textContent?.includes('Archived'),
    ) as HTMLButtonElement;
    archived.click();

    await vi.waitFor(() => expect(TestBed.inject(Router).url).toBe('/catalog?status=ARCHIVED'));
    const request = expectList();
    expect(request.request.params.get('status')).toBe('ARCHIVED');
    request.flush(page([]));
  });

  it('archives after confirmation and reloads the list', async () => {
    vi.spyOn(TestBed.inject(MatDialog), 'open').mockReturnValue({
      afterClosed: () => of(true),
    } as ReturnType<MatDialog['open']>);
    const { element, settle } = await render();
    expectList().flush(page([PIPE]));
    await settle();

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
    const { element, settle } = await render();
    expectList().flush(page([PIPE]));
    await settle();

    (element.querySelector('button[aria-label="Archive PVC-20"]') as HTMLButtonElement).click();
    await settle();

    http.expectNone((r) => r.method === 'DELETE');
  });
});
