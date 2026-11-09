import { Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';

/** Root component. Layouts and pages are chosen by the router. */
@Component({
  selector: 'app-root',
  imports: [RouterOutlet],
  template: '<router-outlet />',
})
export class App {}
