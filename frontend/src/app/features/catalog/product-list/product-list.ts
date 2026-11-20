import { Component, computed, DestroyRef, inject, resource, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatDialog } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar } from '@angular/material/snack-bar';
import { MatTableModule } from '@angular/material/table';
import { ActivatedRoute, Params, Router, RouterLink } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { ListProducts$Params } from '../../../api/generated/fn/products/list-products';
import { ProductResponse } from '../../../api/generated/models';
import { ProductsApi } from '../../../api/generated/services';
import { toProblem } from '../../../core/errors/problem';
import { ConfirmDialog, ConfirmDialogData } from '../../../shared/confirm-dialog/confirm-dialog';
import { formatPrice } from '../money';

type Status = 'ACTIVE' | 'ARCHIVED';
type Sort = NonNullable<ListProducts$Params['sort']>;
type Stock = NonNullable<ListProducts$Params['stock']>;

const SORTS: readonly { value: Sort; label: string }[] = [
  { value: 'NAME', label: 'Name A–Z' },
  { value: 'NAME_DESC', label: 'Name Z–A' },
  { value: 'SKU', label: 'SKU' },
  { value: 'PRICE', label: 'Price: low to high' },
  { value: 'PRICE_DESC', label: 'Price: high to low' },
  { value: 'UPDATED_DESC', label: 'Recently changed' },
];
const PAGE_SIZES = [20, 50, 100];

/** Wait this long after the last keystroke before searching. */
export const SEARCH_DELAY_MS = 300;

/**
 * The supplier's products: searched, filtered, sorted and paged. All of that lives in the URL,
 * so back, refresh and shared links show the same list.
 */
@Component({
  selector: 'app-product-list',
  imports: [
    MatButtonModule,
    MatButtonToggleModule,
    MatFormFieldModule,
    MatInputModule,
    MatPaginatorModule,
    MatProgressBarModule,
    MatSelectModule,
    MatTableModule,
    RouterLink,
  ],
  templateUrl: './product-list.html',
  styleUrl: './product-list.scss',
})
export class ProductList {
  private readonly api = inject(ProductsApi);
  private readonly dialog = inject(MatDialog);
  private readonly snackBar = inject(MatSnackBar);

  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly query = toSignal(this.route.queryParamMap, { requireSync: true });
  private searchTimer: ReturnType<typeof setTimeout> | undefined;

  protected readonly sorts = SORTS;
  protected readonly status = computed<Status>(() =>
    this.query().get('status') === 'ARCHIVED' ? 'ARCHIVED' : 'ACTIVE',
  );
  protected readonly q = computed(() => this.query().get('q') ?? '');
  protected readonly category = computed(() => this.query().get('category') ?? '');
  protected readonly stock = computed<Stock | ''>(() => {
    const value = this.query().get('stock');
    return value === 'IN_STOCK' || value === 'OUT_OF_STOCK' ? value : '';
  });
  protected readonly sort = computed<Sort>(
    () => SORTS.find((s) => s.value === this.query().get('sort'))?.value ?? 'NAME',
  );
  protected readonly pageIndex = computed(() => Math.max(0, Number(this.query().get('page')) || 0));
  protected readonly pageSize = computed(() => {
    const size = Number(this.query().get('size'));
    return PAGE_SIZES.includes(size) ? size : 20;
  });
  protected readonly filtered = computed(() => !!(this.q() || this.category() || this.stock()));
  /** What is in the search box right now (the URL follows after a short pause). */
  protected readonly searchText = signal(this.query().get('q') ?? '');
  protected readonly columns = ['sku', 'name', 'category', 'price', 'stockQty', 'actions'];
  protected readonly formatPrice = formatPrice;

  protected readonly products = resource({
    params: () => ({
      status: this.status(),
      q: this.q() || undefined,
      category: this.category() || undefined,
      stock: this.stock() || undefined,
      sort: this.sort(),
      page: this.pageIndex(),
      size: this.pageSize(),
    }),
    loader: ({ params }) => this.api.listProducts(params),
  });

  protected readonly categories = resource({
    loader: () => this.api.listProductCategories({ size: 500 }),
  });

  constructor() {
    inject(DestroyRef).onDestroy(() => clearTimeout(this.searchTimer));
  }

  protected onSearchInput(value: string): void {
    this.searchText.set(value);
    clearTimeout(this.searchTimer);
    this.searchTimer = setTimeout(
      () => this.update({ q: value.trim() || null, page: null }),
      SEARCH_DELAY_MS,
    );
  }

  protected showStatus(status: Status): void {
    this.update({ status: status === 'ARCHIVED' ? status : null, page: null });
  }

  protected filterCategory(category: string): void {
    this.update({ category: category || null, page: null });
  }

  protected filterStock(stock: Stock | ''): void {
    this.update({ stock: stock || null, page: null });
  }

  protected sortBy(sort: Sort): void {
    this.update({ sort: sort === 'NAME' ? null : sort, page: null });
  }

  protected changePage(event: PageEvent): void {
    this.update({
      page: event.pageIndex || null,
      size: event.pageSize === 20 ? null : event.pageSize,
    });
  }

  protected clearFilters(): void {
    clearTimeout(this.searchTimer);
    this.searchText.set('');
    this.update({ q: null, category: null, stock: null, page: null });
  }

  /** Changes the URL; the list follows. Null removes a parameter. */
  private update(params: Params): void {
    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams: params,
      queryParamsHandling: 'merge',
      replaceUrl: true,
    });
  }

  protected errorMessage(): string {
    return toProblem(this.products.error()).detail;
  }

  protected async archive(product: ProductResponse): Promise<void> {
    const confirmed = await firstValueFrom(
      this.dialog
        .open<ConfirmDialog, ConfirmDialogData, boolean>(ConfirmDialog, {
          data: {
            title: `Archive ${product.sku}?`,
            message:
              'AI agents will stop seeing it. It stays in your history and you can restore it later.',
            confirm: 'Archive',
          },
        })
        .afterClosed(),
    );
    if (!confirmed) {
      return;
    }
    await this.run(() => this.api.archiveProduct({ id: product.id }), `Archived ${product.sku}.`);
  }

  protected async restore(product: ProductResponse): Promise<void> {
    await this.run(() => this.api.restoreProduct({ id: product.id }), `Restored ${product.sku}.`);
  }

  private async run(action: () => Promise<unknown>, success: string): Promise<void> {
    try {
      await action();
      this.snackBar.open(success, undefined, { duration: 4000 });
    } catch (e) {
      this.snackBar.open(toProblem(e).detail, 'Close');
    }
    this.products.reload();
  }
}
