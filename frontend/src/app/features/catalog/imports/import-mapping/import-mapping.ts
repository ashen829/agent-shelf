import { Component, computed, inject, input, OnInit, output, signal } from '@angular/core';
import {
  AbstractControl,
  FormControl,
  FormGroup,
  ReactiveFormsModule,
  ValidationErrors,
  Validators,
} from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatDialog } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatRadioModule } from '@angular/material/radio';
import { MatSelectModule } from '@angular/material/select';
import { firstValueFrom } from 'rxjs';
import {
  ColumnMappingDto,
  ImportPreviewResponse,
  ImportResponse,
} from '../../../../api/generated/models';
import { ImportsApi, ProductsApi } from '../../../../api/generated/services';
import { toProblem } from '../../../../core/errors/problem';
import { ConfirmDialog, ConfirmDialogData } from '../../../../shared/confirm-dialog/confirm-dialog';
import { FIELDS, FieldKey } from '../import-labels';

type MappingControls = Record<FieldKey, FormControl<number | null>>;

/** No column may be used for two fields. */
function eachColumnOnce(group: AbstractControl): ValidationErrors | null {
  const used = Object.values((group as FormGroup<MappingControls>).getRawValue()).filter(
    (v): v is number => v !== null,
  );
  return new Set(used).size === used.length ? null : { duplicateColumn: true };
}

/** Step 2: say which column holds which product field, then start the import. */
@Component({
  selector: 'app-import-mapping',
  imports: [
    MatButtonModule,
    MatCheckboxModule,
    MatFormFieldModule,
    MatRadioModule,
    MatSelectModule,
    ReactiveFormsModule,
  ],
  templateUrl: './import-mapping.html',
  styleUrl: './import-mapping.scss',
})
export class ImportMapping implements OnInit {
  readonly job = input.required<ImportResponse>();
  readonly preview = input.required<ImportPreviewResponse>();
  /** Emits the queued import after a successful start. */
  readonly started = output<ImportResponse>();

  private readonly api = inject(ImportsApi);
  private readonly products = inject(ProductsApi);
  private readonly dialog = inject(MatDialog);

  protected readonly fields = FIELDS;
  protected readonly starting = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly columns = computed(() =>
    this.preview().headers.map((header, index) => ({ index, header })),
  );

  protected readonly mapping = new FormGroup<MappingControls>(
    Object.fromEntries(
      FIELDS.map((f) => [
        f.key,
        new FormControl<number | null>(null, f.required ? [Validators.required] : []),
      ]),
    ) as MappingControls,
    { validators: eachColumnOnce },
  );
  protected readonly decimalSeparator = new FormControl<'.' | ','>('.', { nonNullable: true });
  protected readonly completeCatalog = new FormControl(false, { nonNullable: true });

  /** First value in a column, as an example under its drop-down. */
  protected example(column: number | null): string | null {
    if (column === null) {
      return null;
    }
    const value = this.preview().rows[0]?.values[column];
    return value ? value : null;
  }

  /** Start from the columns the server recognised by their header names. */
  ngOnInit(): void {
    const suggested: ColumnMappingDto = this.preview().suggestedMapping;
    for (const field of FIELDS) {
      this.mapping.controls[field.key].setValue(suggested[field.key] ?? null);
    }
  }

  protected async start(): Promise<void> {
    if (this.mapping.invalid || this.starting()) {
      this.mapping.markAllAsTouched();
      return;
    }
    if (this.completeCatalog.value && !(await this.confirmCompleteCatalog())) {
      return;
    }
    this.starting.set(true);
    this.error.set(null);
    try {
      const queued = await this.api.startImport({
        id: this.job().id,
        body: {
          mapping: this.mapping.getRawValue(),
          decimalSeparator: this.decimalSeparator.value,
          completeCatalog: this.completeCatalog.value,
        },
      });
      this.started.emit(queued);
    } catch (e) {
      this.error.set(toProblem(e).detail);
    } finally {
      this.starting.set(false);
    }
  }

  private async confirmCompleteCatalog(): Promise<boolean> {
    const active = (await this.products.listProducts({ size: 1 })).totalItems;
    return (
      (await firstValueFrom(
        this.dialog
          .open<ConfirmDialog, ConfirmDialogData, boolean>(ConfirmDialog, {
            data: {
              title: 'Archive products that are not in this file?',
              message:
                `You have ${active} active products and this file has ${this.job().rowsTotal} rows. ` +
                'Active products whose SKU is not in the file will be archived. AI agents stop ' +
                'seeing them; you can restore them from the Archived list.',
              confirm: 'Import and archive',
            },
          })
          .afterClosed(),
      )) === true
    );
  }
}
