import { Component, DestroyRef, effect, inject, input, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { RouterLink } from '@angular/router';
import {
  ImportErrorPage,
  ImportPreviewResponse,
  ImportResponse,
} from '../../../../api/generated/models';
import { ImportsApi } from '../../../../api/generated/services';
import { toProblem } from '../../../../core/errors/problem';
import { ImportMapping } from '../import-mapping/import-mapping';
import { fieldLabel, isFinished, isRunning, STATUS_LABELS } from '../import-labels';

/** One import: match columns, watch it run, then see the results and the rows to fix. */
@Component({
  selector: 'app-import-detail',
  imports: [ImportMapping, MatButtonModule, MatPaginatorModule, MatProgressBarModule, RouterLink],
  templateUrl: './import-detail.html',
  styleUrl: './import-detail.scss',
})
export class ImportDetail {
  /** Bound from the `:id` route parameter. */
  readonly id = input.required<string>();

  static readonly POLL_MS = 2000;

  private readonly api = inject(ImportsApi);

  protected readonly job = signal<ImportResponse | null>(null);
  protected readonly preview = signal<ImportPreviewResponse | null>(null);
  protected readonly errors = signal<ImportErrorPage | null>(null);
  protected readonly error = signal<string | null>(null);
  protected readonly labels = STATUS_LABELS;
  protected readonly isRunning = isRunning;
  protected readonly isFinished = isFinished;
  protected readonly fieldLabel = fieldLabel;
  private pollTimer: ReturnType<typeof setTimeout> | undefined;

  constructor() {
    inject(DestroyRef).onDestroy(() => clearTimeout(this.pollTimer));
    effect(() => {
      void this.load(this.id());
    });
  }

  protected percent(job: ImportResponse): number {
    return job.rowsTotal === 0 ? 0 : Math.round((job.rowsProcessed / job.rowsTotal) * 100);
  }

  protected onStarted(job: ImportResponse): void {
    this.preview.set(null);
    this.show(job);
  }

  protected async changeErrorPage(event: PageEvent): Promise<void> {
    await this.loadErrors(event.pageIndex, event.pageSize);
  }

  private async load(id: string): Promise<void> {
    clearTimeout(this.pollTimer);
    this.error.set(null);
    try {
      const job = await this.api.getImport({ id });
      if (job.status === 'UPLOADED' && !this.preview()) {
        this.preview.set(await this.api.getImportPreview({ id }));
      }
      this.show(job);
    } catch (e) {
      this.error.set(toProblem(e).detail);
    }
  }

  private show(job: ImportResponse): void {
    this.job.set(job);
    if (isRunning(job.status)) {
      this.pollTimer = setTimeout(() => void this.load(job.id), ImportDetail.POLL_MS);
    } else if (job.rowsFailed > 0 && !this.errors()) {
      void this.loadErrors(0, 50);
    }
  }

  private async loadErrors(page: number, size: number): Promise<void> {
    try {
      this.errors.set(await this.api.listImportErrors({ id: this.id(), page, size }));
    } catch (e) {
      this.error.set(toProblem(e).detail);
    }
  }
}
