import { Component } from '@angular/core';

/** Placeholder start page. Replaced by the dashboard in the identity slices. */
@Component({
  selector: 'app-home',
  template: `
    <h1>Welcome to AgentShelf</h1>
    <p>Your supplier dashboard will appear here.</p>
  `,
  styles: `
    h1 {
      font: var(--mat-sys-headline-small);
      margin: 0 0 8px;
    }
  `,
})
export class Home {}
