import { Component, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { AuthService } from '../core/auth.service';

@Component({
  standalone: true,
  imports: [FormsModule],
  template: `
    <h1>Connexion</h1>
    <p class="muted">Espace réservé au personnel de scolarité.</p>
    <form class="box" (ngSubmit)="submit()">
      <label for="e">E-mail</label>
      <input id="e" name="e" type="email" [(ngModel)]="email" autocomplete="username" required>
      <label for="p">Mot de passe</label>
      <input id="p" name="p" type="password" [(ngModel)]="password" autocomplete="current-password" required>
      <p><button type="submit" [disabled]="busy || !email || !password">Se connecter</button></p>
      @if (error) { <div class="err">{{ error }}</div> }
    </form>
  `,
})
export class LoginComponent {
  private auth = inject(AuthService);
  private router = inject(Router);
  email = '';
  password = '';
  busy = false;
  error = '';

  submit() {
    this.busy = true;
    this.error = '';
    this.auth.login(this.email, this.password).subscribe({
      next: () => this.router.navigate(['/staff/sessions']),
      error: () => { this.busy = false; this.error = 'Identifiants invalides.'; },
    });
  }
}
