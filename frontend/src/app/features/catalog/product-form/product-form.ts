import { Component, computed, effect, inject, input, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSnackBar } from '@angular/material/snack-bar';
import { Router, RouterLink } from '@angular/router';
import { ProductRequest, ProductResponse } from '../../../api/generated/models';
import { ProductsApi } from '../../../api/generated/services';
import { AuthStore } from '../../../core/auth/auth-store';
import { toProblem } from '../../../core/errors/problem';
import { PRICE_PATTERN } from '../money';

/** Add a product (no id) or edit one (id from the route). */
@Component({
  selector: 'app-product-form',
  imports: [
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    MatProgressBarModule,
    ReactiveFormsModule,
    RouterLink,
  ],
  templateUrl: './product-form.html',
  styleUrl: './product-form.scss',
})
export class ProductForm {
  /** Bound from the `:id` route parameter when editing. */
  readonly id = input<string>();

  private readonly api = inject(ProductsApi);
  private readonly router = inject(Router);
  private readonly snackBar = inject(MatSnackBar);
  private readonly auth = inject(AuthStore);

  protected readonly currency = computed(() => this.auth.account()?.supplier.currency ?? '');
  protected readonly editing = computed(() => !!this.id());
  protected readonly loading = signal(false);
  protected readonly saving = signal(false);
  protected readonly error = signal<string | null>(null);
  /** Someone else saved this product after it was loaded. */
  protected readonly stale = signal(false);
  private version: number | null = null;

  protected readonly form = new FormGroup({
    sku: new FormControl('', {
      nonNullable: true,
      // Surrounding spaces are fine (pasted codes): they are trimmed before saving.
      validators: [
        Validators.required,
        Validators.pattern(/^\s*[A-Za-z0-9][A-Za-z0-9._/-]{0,63}\s*$/),
      ],
    }),
    name: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.maxLength(200)],
    }),
    description: new FormControl('', {
      nonNullable: true,
      validators: [Validators.maxLength(2000)],
    }),
    category: new FormControl('', { nonNullable: true, validators: [Validators.maxLength(100)] }),
    unit: new FormControl('', { nonNullable: true, validators: [Validators.maxLength(30)] }),
    price: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.pattern(PRICE_PATTERN)],
    }),
    stockQty: new FormControl<number>(0, {
      nonNullable: true,
      validators: [Validators.required, Validators.min(0), Validators.pattern(/^\d+$/)],
    }),
  });

  constructor() {
    effect(() => {
      const id = this.id();
      if (id) {
        void this.load(id);
      }
    });
  }

  protected async reload(): Promise<void> {
    const id = this.id();
    if (id) {
      await this.load(id);
    }
  }

  protected async submit(): Promise<void> {
    if (this.form.invalid || this.saving()) {
      this.form.markAllAsTouched();
      return;
    }
    this.saving.set(true);
    this.error.set(null);
    this.stale.set(false);
    try {
      const id = this.id();
      const body = this.body();
      const saved = id
        ? await this.api.updateProduct({ id, body: { ...body, version: this.version } })
        : await this.api.createProduct({ body });
      this.snackBar.open(`Saved ${saved.sku}.`, undefined, { duration: 4000 });
      await this.router.navigateByUrl('/catalog');
    } catch (e) {
      this.showProblem(e);
    } finally {
      this.saving.set(false);
    }
  }

  private async load(id: string): Promise<void> {
    this.loading.set(true);
    this.error.set(null);
    this.stale.set(false);
    try {
      this.fill(await this.api.getProduct({ id }));
    } catch (e) {
      this.error.set(toProblem(e).detail);
    } finally {
      this.loading.set(false);
    }
  }

  private fill(product: ProductResponse): void {
    this.version = product.version;
    this.form.reset({
      sku: product.sku,
      name: product.name,
      description: product.description ?? '',
      category: product.category ?? '',
      unit: product.unit ?? '',
      price: trimZeros(product.price.amount),
      stockQty: product.stockQty,
    });
  }

  private body(): ProductRequest {
    const v = this.form.getRawValue();
    return {
      sku: v.sku.trim(),
      name: v.name.trim(),
      description: v.description.trim() || null,
      category: v.category.trim() || null,
      unit: v.unit.trim() || null,
      price: v.price.trim(),
      stockQty: Number(v.stockQty),
    };
  }

  /** Field errors go under their inputs; a lost update gets its own message. */
  private showProblem(e: unknown): void {
    const problem = toProblem(e);
    if (problem.status === 409 && problem.fieldErrors.length === 0) {
      this.stale.set(true);
      return;
    }
    let unmatched = false;
    for (const { field, message } of problem.fieldErrors) {
      const control = this.form.get(field);
      if (control) {
        control.setErrors({ ...control.errors, server: message });
        control.markAsTouched();
      } else {
        unmatched = true;
      }
    }
    if (problem.fieldErrors.length === 0 || unmatched) {
      this.error.set(problem.detail);
    }
  }
}

/** API amounts have 4 decimals: "12.5000" -> "12.50", "0.0125" stays. Easier to read and edit. */
export function trimZeros(amount: string): string {
  const [whole, fraction = ''] = amount.split('.');
  return `${whole}.${fraction.replace(/0+$/, '').padEnd(2, '0')}`;
}
