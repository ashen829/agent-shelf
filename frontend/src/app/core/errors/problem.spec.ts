import { HttpErrorResponse } from '@angular/common/http';
import { FALLBACK_MESSAGE, OFFLINE_MESSAGE, toProblem } from './problem';

describe('toProblem', () => {
  it('reads detail and field errors from a problem detail', () => {
    const error = new HttpErrorResponse({
      status: 400,
      error: {
        detail: 'The request has invalid fields.',
        errors: [{ field: 'email', message: 'must be a well-formed email address' }, 'junk'],
      },
    });

    expect(toProblem(error)).toEqual({
      status: 400,
      detail: 'The request has invalid fields.',
      fieldErrors: [{ field: 'email', message: 'must be a well-formed email address' }],
    });
  });

  it('explains when the server cannot be reached', () => {
    expect(toProblem(new HttpErrorResponse({ status: 0 })).detail).toBe(OFFLINE_MESSAGE);
  });

  it('falls back to a generic message for anything else', () => {
    expect(toProblem(new HttpErrorResponse({ status: 502, error: '<html>' })).detail).toBe(
      FALLBACK_MESSAGE,
    );
    expect(toProblem(new Error('boom')).detail).toBe(FALLBACK_MESSAGE);
  });
});
