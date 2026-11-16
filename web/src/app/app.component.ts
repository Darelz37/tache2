import { Component, OnInit, inject } from '@angular/core';
import { RouterLink, RouterOutlet } from '@angular/router';
import { ApiService } from './core/api.service';
import { AuthService } from './core/auth.service';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet, RouterLink],
  template: `
    <header class="bar">
      <a class="brand" routerLink="/">Proclama</a>
      <nav>
        @if (auth.logged) {
          <a routerLink="/staff/sessions">Sessions</a>
          @if (auth.isAdmin) { <a routerLink="/staff/audit">Audit</a> }
          <a href="#" (click)="$event.preventDefault(); auth.logout()">Quitter</a>
        } @else {
          <a routerLink="/staff/login">Espace du personnel</a>
        }
      </nav>
    </header>
    <main><router-outlet /></main>
    <footer class="foot">
      <a routerLink="/infra">Infrastructure</a>
      @if (who) { · Instance : {{ who.instance }} · Version {{ who.version }} }
    </footer>
  `,
})
export class AppComponent implements OnInit {
  auth = inject(AuthService);
  private api = inject(ApiService);
  who: any = null;

  ngOnInit() {
    this.api.whoami().subscribe({ next: (w) => (this.who = w), error: () => (this.who = null) });
  }
}
