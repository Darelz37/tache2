import { Component, OnInit, inject } from '@angular/core';
import { DatePipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { ApiService } from '../core/api.service';

@Component({
  standalone: true,
  imports: [RouterLink, DatePipe],
  template: `
    <h1>Résultats d'examens et de concours</h1>
    <p class="muted">Choisissez votre session.</p>
    @if (error) { <div class="err">Le service est momentanément indisponible. Réessayez dans un instant.</div> }
    @if (sessions && !sessions.length) { <p>Aucune session pour le moment.</p> }
    <ul class="list">
      @for (s of sessions; track s.code) {
        <li>
          <div>
            <strong>{{ s.code }}</strong> · {{ s.title }}<br>
            <span class="muted">
              @switch (s.status) {
                @case ('published') { Résultats publiés }
                @case ('scheduled') { Publication le {{ s.publishAt | date:'dd/MM/yyyy à HH:mm' }} }
                @default { Résultats indisponibles }
              }
            </span>
          </div>
          <a class="btn" [routerLink]="['/s', s.code]">Consulter</a>
        </li>
      }
    </ul>
  `,
})
export class HomeComponent implements OnInit {
  private api = inject(ApiService);
  sessions: any[] | null = null;
  error = false;

  ngOnInit() {
    this.api.sessions().subscribe({ next: (s) => (this.sessions = s), error: () => (this.error = true) });
  }
}
