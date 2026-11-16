import { Injectable, inject } from '@angular/core';
import { Router } from '@angular/router';
import { tap } from 'rxjs';
import { ApiService } from './api.service';

/** Le jeton reste en sessionStorage (effacé à la fermeture de l'onglet). */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private api = inject(ApiService);
  private router = inject(Router);

  get token(): string | null { return sessionStorage.getItem('token'); }
  get user(): any { const u = sessionStorage.getItem('user'); return u ? JSON.parse(u) : null; }
  get logged(): boolean { return !!this.token; }
  get isAdmin(): boolean { return this.user?.role === 'admin'; }

  login(email: string, password: string) {
    return this.api.login(email, password).pipe(
      tap((r: any) => { sessionStorage.setItem('token', r.token); sessionStorage.setItem('user', JSON.stringify(r.user)); }),
    );
  }

  logout() {
    sessionStorage.removeItem('token');
    sessionStorage.removeItem('user');
    this.router.navigate(['/staff/login']);
  }
}
