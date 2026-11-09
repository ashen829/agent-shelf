import { Component, inject, resource, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatTableModule } from '@angular/material/table';
import { RouterLink } from '@angular/router';
import { ImportResponse } from '../../../../api/generated/models';
import { ImportsApi } from '../../../../api/generated/services';
import { toProblem } from '../../../../core/errors/problem';
import { STATUS_LABELS } from '../import-labels';

/** Earlier imports, newest first. */
@Component({
  selector: 'app-import-history',
  imports: [MatButtonModule, MatPaginatorModule, MatProgressBarModule, MatTableModule, RouterLink],
  templateUrl: './import-history.html',
  styleUrl: './import-history.scss',
})
export class ImportHistory {
  private readonly api = inject(ImportsApi);

  protected readonly pageIndex = signal(0);
  protected readonly pageSize = signal(20);
  protected readonly columns = ['createdAt', 'fileName', 'status', 'result'];
  private readonly dateFormat = new Intl.DateTimeFormat(undefined, {
    dateStyle: 'medium',
    timeStyle: 'short',
  });

  protected readonly imports = resource({
    params: () => ({ page: this.pageIndex(), size: this.pageSize() }),
    loader: ({ params }) => this.api.listImports(params),
  });

  /** Typed: table cell variables are untyped in templates. */
  protected statusLabel(job: ImportResponse): string {
    return STATUS_LABELS[job.status];
  }

  protected formatDate(iso: string): string {
    return this.dateFormat.format(new Date(iso));
  }

  protected changePage(event: PageEvent): void {
    this.pageIndex.set(event.pageIndex);
    this.pageSize.set(event.pageSize);
  }

  protected errorMessage(): string {
    return toProblem(this.imports.error()).detail;
  }
}
