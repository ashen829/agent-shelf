import { HttpContext, HttpContextToken } from '@angular/common/http';

/** Marks API calls whose 401 is expected (who-am-I and login), so the interceptor ignores it. */
export const EXPECTS_UNAUTHENTICATED = new HttpContextToken<boolean>(() => false);

export function expectsUnauthenticated(): HttpContext {
  return new HttpContext().set(EXPECTS_UNAUTHENTICATED, true);
}
