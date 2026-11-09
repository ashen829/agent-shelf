import { Component, inject, resource, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatDialog } from '@angular/material/dialog';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSnackBar } from '@angular/material/snack-bar';
import { MatTableModule } from '@angular/material/table';
import { RouterLink } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { ProductResponse } from '../../../api/generated/models';
import { ProductsApi } from '../../../api/generated/services';
import { toProblem } from '../../../core/errors/problem';
import { ConfirmDialog, ConfirmDialogData } from '../../../shared/confirm-dialog/confirm-dialog';
import { formatPrice } from '../money';

type Status = 'ACTIVE' | 'ARCHIVED';

/** The supplier's products, one page at a time, active or archived. */
@Component({
  selector: 'app-product-list',
  imports: [
    MatButtonModule,
    MatButtonToggleModule,
    MatPaginatorModule,
    MatProgressBarModule,
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

  protected readonly status = signal<Status>('ACTIVE');
  protected readonly pageIndex = signal(0);
  protected readonly pageSize = signal(20);
  protected readonly columns = ['sku', 'name', 'category', 'price', 'stockQty', 'actions'];
  protected readonly formatPrice = formatPrice;

  protected readonly products = resource({
    params: () => ({ status: this.status(), page: this.pageIndex(), size: this.pageSize() }),
    loader: ({ params }) => this.api.listProducts({ ...params, sort: 'NAME' }),
  });

  protected showStatus(status: Status): void {
    this.status.set(status);
    this.pageIndex.set(0);
  }

  protected changePage(event: PageEvent): void {
    this.pageIndex.set(event.pageIndex);
    this.pageSize.set(event.pageSize);
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
