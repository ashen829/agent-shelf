import { Component } from '@angular/core';
import { MatToolbarModule } from '@angular/material/toolbar';
import { RouterLink, RouterOutlet } from '@angular/router';

/** Signed-in layout: top toolbar and the page content below it. */
@Component({
  selector: 'app-shell',
  imports: [MatToolbarModule, RouterLink, RouterOutlet],
  templateUrl: './shell.html',
  styleUrl: './shell.scss',
})
export class Shell {}
