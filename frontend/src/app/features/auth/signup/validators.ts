import { AbstractControl, ValidationErrors, ValidatorFn } from '@angular/forms';

/** bcrypt only uses the first 72 bytes of a password; the backend rejects longer ones. */
export function maxUtf8Bytes(max: number): ValidatorFn {
  const encoder = new TextEncoder();
  return (control: AbstractControl): ValidationErrors | null => {
    const value: unknown = control.value;
    return typeof value === 'string' && encoder.encode(value).length > max
      ? { maxUtf8Bytes: { max } }
      : null;
  };
}
