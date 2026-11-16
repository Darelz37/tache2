import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, throwError } from 'rxjs';
import { AuthService } from './auth.service';

export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(AuthService);
  const staff = req.url.startsWith('/api/admin') || req.url.startsWith('/api/me');
  const withToken = staff && auth.token ? req.clone({ setHeaders: { Authorization: `Bearer ${auth.token}` } }) : req;
  return next(withToken).pipe(
    catchError((e) => {
      if (staff && e.status === 401) auth.logout();
      return throwError(() => e);
    }),
  );
};
