import { Component, DestroyRef, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { EnrichmentJobResponse } from '../../../api/generated/models';
import { AiSuggestionsApi } from '../../../api/generated/services';
import { toProblem } from '../../../core/errors/problem';

/**
 * "Suggest fixes" on the catalog page: starts AI cleanup and shows how the latest job is doing.
 * Hidden while the feature is switched off on the server.
 */
@Component({
  selector: 'app-ai-suggestions-card',
  imports: [MatButtonModule, MatProgressBarModule],
  templateUrl: './ai-suggestions-card.html',
  styleUrl: './ai-suggestions-card.scss',
})
export class AiSuggestionsCard {
  /** Claude works on a batch for minutes: no need to ask more often. */
  static readonly POLL_MS = 15_000;

  private readonly api = inject(AiSuggestionsApi);

  protected readonly enabled = signal(false);
  protected readonly job = signal<EnrichmentJobResponse | null>(null);
  protected readonly starting = signal(false);
  protected readonly error = signal<string | null>(null);
  private pollTimer: ReturnType<typeof setTimeout> | undefined;

  constructor() {
    inject(DestroyRef).onDestroy(() => clearTimeout(this.pollTimer));
    void this.load();
  }

  protected isRunning(job: EnrichmentJobResponse | null): boolean {
    return job?.status === 'QUEUED' || job?.status === 'SUBMITTED';
  }

  async start(): Promise<void> {
    if (this.starting() || this.isRunning(this.job())) {
      return;
    }
    this.starting.set(true);
    this.error.set(null);
    try {
      this.show(await this.api.startEnrichment());
    } catch (e) {
      this.error.set(toProblem(e).detail);
    } finally {
      this.starting.set(false);
    }
  }

  private async load(): Promise<void> {
    clearTimeout(this.pollTimer);
    try {
      const overview = await this.api.getEnrichmentOverview();
      this.enabled.set(overview.enabled);
      if (overview.latestJob) {
        this.show(overview.latestJob);
      }
    } catch {
      // The card is optional: the catalog page works without it.
      this.enabled.set(false);
    }
  }

  private show(job: EnrichmentJobResponse): void {
    this.job.set(job);
    clearTimeout(this.pollTimer);
    if (this.isRunning(job)) {
      this.pollTimer = setTimeout(() => void this.load(), AiSuggestionsCard.POLL_MS);
    }
  }
}
