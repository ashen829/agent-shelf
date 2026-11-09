import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthStore } from './auth-store';

/** Pages behind sign-in. Signed-out users go to the login page and come back afterwards. */
export const authGuard: CanActivateFn = (_route, state) => {
  if (inject(AuthStore).isSignedIn()) {
    return true;
  }
  return inject(Router).createUrlTree(['/login'], { queryParams: { returnUrl: state.url } });
};

/** Login and sign-up pages: signed-in users go straight to the app. */
export const guestGuard: CanActivateFn = () =>
  inject(AuthStore).isSignedIn() ? inject(Router).createUrlTree(['/']) : true;
