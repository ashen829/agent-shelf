import { HttpErrorResponse } from '@angular/common/http';

/** One invalid field reported by the backend (`errors` in a 400 problem detail). */
export interface FieldError {
  field: string;
  message: string;
}

/** What the UI needs from an RFC 9457 problem detail returned by the backend. */
export interface Problem {
  status: number;
  detail: string;
  fieldErrors: FieldError[];
}

export const FALLBACK_MESSAGE = 'Something went wrong. Please try again.';
export const OFFLINE_MESSAGE = 'Cannot reach the server. Check your connection and try again.';

/** Turns any error from an API call into a message the user can read. */
export function toProblem(error: unknown): Problem {
  if (!(error instanceof HttpErrorResponse)) {
    return { status: -1, detail: FALLBACK_MESSAGE, fieldErrors: [] };
  }
  if (error.status === 0) {
    return { status: 0, detail: OFFLINE_MESSAGE, fieldErrors: [] };
  }
  const body: unknown = error.error;
  const detail = isRecord(body) && typeof body['detail'] === 'string' ? body['detail'] : null;
  const errors = isRecord(body) && Array.isArray(body['errors']) ? body['errors'] : [];
  return {
    status: error.status,
    detail: detail ?? FALLBACK_MESSAGE,
    fieldErrors: errors.filter(isFieldError),
  };
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null;
}

function isFieldError(value: unknown): value is FieldError {
  return (
    isRecord(value) && typeof value['field'] === 'string' && typeof value['message'] === 'string'
  );
}
