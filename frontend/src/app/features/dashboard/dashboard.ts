import { Component, computed, inject } from '@angular/core';
import { MatCardModule } from '@angular/material/card';
import { AuthStore } from '../../core/auth/auth-store';

interface Step {
  title: string;
  detail: string;
  done: boolean;
  milestone?: string;
}

/** The supplier's start page. Empty for now: it fills up as later milestones land. */
@Component({
  selector: 'app-dashboard',
  imports: [MatCardModule],
  templateUrl: './dashboard.html',
  styleUrl: './dashboard.scss',
})
export class Dashboard {
  protected readonly account = inject(AuthStore).account;
  protected readonly currencyName = computed(() => {
    const code = this.account()?.supplier.currency;
    return code ? (new Intl.DisplayNames(['en'], { type: 'currency' }).of(code) ?? code) : '';
  });

  protected readonly steps: readonly Step[] = [
    { title: 'Create your account', detail: 'Your supplier account is ready.', done: true },
    {
      title: 'Import your price list',
      detail: 'Upload a CSV or Excel file and fix any rows with errors.',
      done: false,
      milestone: 'M1',
    },
    {
      title: 'Review AI suggestions',
      detail: 'Approve clearer names, units and categories for your products.',
      done: false,
      milestone: 'M2',
    },
    {
      title: 'Set your business rules',
      detail: 'Bulk price tiers, minimum order value and delivery areas.',
      done: false,
      milestone: 'M3',
    },
    {
      title: 'Connect AI buying agents',
      detail: 'Give buyers an API key so their agents can search and request quotes.',
      done: false,
      milestone: 'M4',
    },
  ];
}
