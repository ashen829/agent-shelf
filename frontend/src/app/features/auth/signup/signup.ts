import { Component, inject, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { Router, RouterLink } from '@angular/router';
import { AuthStore } from '../../../core/auth/auth-store';
import { toProblem } from '../../../core/errors/problem';
import { currencyOptions } from './currencies';
import { maxUtf8Bytes } from './validators';

@Component({
  selector: 'app-signup',
  imports: [
    ReactiveFormsModule,
    RouterLink,
    MatButtonModule,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
  ],
  templateUrl: './signup.html',
  styleUrl: '../auth-page.scss',
})
export class Signup {
  private readonly store = inject(AuthStore);
  private readonly router = inject(Router);

  protected readonly currencies = currencyOptions();
  protected readonly form = new FormGroup({
    companyName: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.minLength(2), Validators.maxLength(100)],
    }),
    currency: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    email: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.email, Validators.maxLength(254)],
    }),
    password: new FormControl('', {
      nonNullable: true,
      validators: [
        Validators.required,
        Validators.minLength(12),
        Validators.maxLength(64),
        maxUtf8Bytes(72),
      ],
    }),
  });
  protected readonly submitting = signal(false);
  protected readonly error = signal<string | null>(null);

  protected async submit(): Promise<void> {
    if (this.form.invalid || this.submitting()) {
      this.form.markAllAsTouched();
      return;
    }
    this.submitting.set(true);
    this.error.set(null);
    try {
      const value = this.form.getRawValue();
      await this.store.signup({ ...value, companyName: value.companyName.trim() });
      await this.router.navigateByUrl('/');
    } catch (e) {
      this.showProblem(e);
    } finally {
      this.submitting.set(false);
    }
  }

  /** Field errors go under their inputs; anything else is shown above the form. */
  private showProblem(e: unknown): void {
    const problem = toProblem(e);
    let unmatched = false;
    for (const { field, message } of problem.fieldErrors) {
      const control = this.form.get(field);
      if (control) {
        control.setErrors({ ...control.errors, server: message });
        control.markAsTouched();
      } else {
        unmatched = true;
      }
    }
    if (problem.fieldErrors.length === 0 || unmatched) {
      this.error.set(problem.detail);
    }
  }
}
