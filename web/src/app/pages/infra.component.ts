import { Component, OnInit, inject } from '@angular/core';
import { DatePipe } from '@angular/common';
import { ApiService } from '../core/api.service';

/** WF08 : version, dépendances et compteurs par instance. Chaque appel est servi par une instance différente. */
@Component({
  standalone: true,
  imports: [DatePipe],
  template: `
    <h1>État de l'infrastructure</h1>
    @if (current) {
      <p>Instance qui vous répond : <strong>{{ current.instance }}</strong></p>
      <p class="muted">Version {{ current.version }} · Base : {{ current.db }} · Stockage : {{ current.s3 }} ·
        Worker : {{ current.workerLastRunSeconds === null ? 'aucun passage' : ('dernier passage il y a ' + current.workerLastRunSeconds + ' s') }}</p>
    } @else if (failed) { <div class="err">Impossible de joindre l'API.</div> }
    <h2>Compteurs par instance (depuis leur démarrage)</h2>
    <div class="table-wrap">
      <table>
        <thead><tr><th>Instance</th><th>Requêtes</th><th>Consultations</th><th>Statuts servis</th><th>Démarrée</th></tr></thead>
        <tbody>
          @for (w of rows(); track w.instance) {
            <tr><td>{{ w.instance }}</td><td>{{ w.counters.requests }}</td><td>{{ w.counters.lookups }}</td>
              <td>{{ w.counters.statuses }}</td><td>{{ w.startedAt | date:'dd/MM HH:mm:ss' }}</td></tr>
          }
        </tbody>
      </table>
    </div>
    <p class="row">
      <button (click)="refresh()">Rafraîchir</button>
      <button class="alt" (click)="probe()">Sonder les instances (30 appels)</button>
    </p>
    <p class="muted">La répartition des compteurs prouve l'équilibrage de charge.</p>
  `,
})
export class InfraComponent implements OnInit {
  private api = inject(ApiService);
  current: any = null;
  failed = false;
  private seen = new Map<string, any>();

  ngOnInit() { this.refresh(); }
  rows() { return Array.from(this.seen.values()).sort((a, b) => a.instance.localeCompare(b.instance)); }

  refresh() {
    this.api.whoami().subscribe({
      next: (w) => { this.current = w; this.seen.set(w.instance, w); this.failed = false; },
      error: () => (this.failed = true),
    });
  }
  probe() { for (let i = 0; i < 30; i++) this.refresh(); }
}
