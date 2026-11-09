import { Component, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatMenuModule } from '@angular/material/menu';
import { MatToolbarModule } from '@angular/material/toolbar';
import { Router, RouterLink, RouterOutlet } from '@angular/router';
import { AuthStore } from '../../auth/auth-store';

/** Signed-in layout: top toolbar with the account menu, and the page content below it. */
@Component({
  selector: 'app-shell',
  imports: [MatButtonModule, MatMenuModule, MatToolbarModule, RouterLink, RouterOutlet],
  templateUrl: './shell.html',
  styleUrl: './shell.scss',
})
export class Shell {
  private readonly store = inject(AuthStore);
  private readonly router = inject(Router);

  protected readonly account = this.store.account;

  protected async logout(): Promise<void> {
    await this.store.logout();
    await this.router.navigateByUrl('/login');
  }
}
