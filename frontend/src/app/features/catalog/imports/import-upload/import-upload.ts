import { Component, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { Router, RouterLink } from '@angular/router';
import { ImportsApi } from '../../../../api/generated/services';
import { toProblem } from '../../../../core/errors/problem';
import { MAX_FILE_BYTES } from '../import-labels';

/** Step 1: choose a CSV file. It is checked and stored; matching columns comes next. */
@Component({
  selector: 'app-import-upload',
  imports: [MatButtonModule, MatProgressBarModule, RouterLink],
  templateUrl: './import-upload.html',
  styleUrl: './import-upload.scss',
})
export class ImportUpload {
  private readonly api = inject(ImportsApi);
  private readonly router = inject(Router);

  protected readonly uploading = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly dragging = signal(false);

  protected onFileChosen(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.item(0);
    input.value = '';
    if (file) {
      void this.upload(file);
    }
  }

  protected onDrop(event: DragEvent): void {
    event.preventDefault();
    this.dragging.set(false);
    const file = event.dataTransfer?.files.item(0);
    if (file) {
      void this.upload(file);
    }
  }

  protected onDragOver(event: DragEvent): void {
    event.preventDefault();
    this.dragging.set(true);
  }

  async upload(file: File): Promise<void> {
    if (this.uploading()) {
      return;
    }
    this.error.set(null);
    if (!/\.(csv|txt)$/i.test(file.name)) {
      this.error.set('Choose a .csv file. In Excel, use Save As and choose "CSV UTF-8".');
      return;
    }
    if (file.size > MAX_FILE_BYTES) {
      this.error.set('The file is larger than 10 MB. Split it into smaller files.');
      return;
    }
    this.uploading.set(true);
    try {
      // A File (not a plain Blob) so the server receives the original file name.
      const result = await this.api.uploadImport({ body: { file } });
      await this.router.navigate(['/catalog/imports', result.importJob.id]);
    } catch (e) {
      this.error.set(toProblem(e).detail);
    } finally {
      this.uploading.set(false);
    }
  }
}
