import { Component, OnInit, inject } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { ApiService } from '../core/api.service';
import { STATUS_LABEL, apiMessage } from '../core/util';

@Component({
  standalone: true,
  imports: [FormsModule, DatePipe],
  template: `
    <div class="row" style="justify-content:space-between">
      <h1>Sessions</h1>
      <button (click)="open = !open">+ Nouvelle session</button>
    </div>
    @if (open) {
      <form class="box" (ngSubmit)="create()">
        <label for="c">Code (3 à 30 caractères : lettres, chiffres, tirets)</label>
        <input id="c" name="c" type="text" [(ngModel)]="code" required>
        <label for="t">Titre</label>
        <input id="t" name="t" type="text" [(ngModel)]="title" required>
        <p><button type="submit" [disabled]="!code || !title">Créer la session</button></p>
        @if (error) { <div class="err">{{ error }}</div> }
      </form>
    }
    <div class="table-wrap">
      <table>
        <thead><tr><th>Code</th><th>Titre</th><th>Statut</th><th>Publication</th><th>Candidats</th></tr></thead>
        <tbody>
          @for (s of sessions; track s.code) {
            <tr class="click" (click)="go(s.code)">
              <td>{{ s.code }}</td><td>{{ s.title }}</td><td>{{ label(s.status) }}</td>
              <td>{{ s.publishAt ? (s.publishAt | date:'dd/MM/yyyy HH:mm') : '-' }}</td><td>{{ s.candidates }}</td>
            </tr>
          }
        </tbody>
      </table>
    </div>
  `,
})
export class StaffSessionsComponent implements OnInit {
  private api = inject(ApiService);
  private router = inject(Router);
  sessions: any[] = [];
  open = false;
  code = '';
  title = '';
  error = '';

  ngOnInit() { this.load(); }
  load() { this.api.adminSessions().subscribe((s) => (this.sessions = s)); }
  label(s: string) { return STATUS_LABEL[s] ?? s; }
  go(code: string) { this.router.navigate(['/staff/sessions', code]); }

  create() {
    this.error = '';
    this.api.createSession(this.code.trim(), this.title.trim()).subscribe({
      next: (s) => this.go(s.code),
      error: (e) => (this.error = apiMessage(e)),
    });
  }
}
