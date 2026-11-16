import { Component, OnDestroy, OnInit, inject } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { ApiService } from '../core/api.service';
import { AuthService } from '../core/auth.service';
import { STATUS_LABEL, apiMessage, saveBlob } from '../core/util';

/** WF07 : publication (admin), dépôt CSV, suivi des imports, statistiques. */
@Component({
  standalone: true,
  imports: [FormsModule, DatePipe, RouterLink],
  template: `
    <p><a routerLink="/staff/sessions">← Sessions</a></p>
    @if (s) {
      <h1>{{ s.code }} · {{ s.title }}</h1>
      <p class="muted">Statut : {{ label(s.status) }}
        @if (s.publishAt) { · Publication {{ s.status === 'scheduled' ? 'prévue' : '' }} le {{ s.publishAt | date:'dd/MM/yyyy à HH:mm' }} }
        · {{ s.candidates }} candidats</p>
      @if (msg) { <div class="err">{{ msg }}</div> }

      @if (auth.isAdmin) {
        <h2>Publication (responsable)</h2>
        <div class="row">
          <input type="datetime-local" [(ngModel)]="when" aria-label="Date et heure de publication">
          <button (click)="schedule()" [disabled]="!when">Planifier</button>
          <button (click)="publish()">Publier maintenant</button>
          <button class="alt" (click)="unpublish()">Dépublier</button>
        </div>
      }

      <h2>Importer des résultats</h2>
      <div class="drop" (dragover)="$event.preventDefault()" (drop)="onDrop($event)">
        <p>Déposez un fichier CSV ici (10 Mo, 50 000 lignes maximum)</p>
        <input type="file" accept=".csv,text/csv" (change)="onFile($event)" aria-label="Choisir un fichier CSV">
      </div>

      <h2>Imports</h2>
      <div class="table-wrap">
        <table>
          <thead><tr><th>Fichier</th><th>Statut</th><th>Progression</th><th>Lignes lues</th><th>Valides</th><th>Erreurs</th><th></th></tr></thead>
          <tbody>
            @for (i of imports; track i.id) {
              <tr>
                <td>{{ i.filename }}</td>
                <td>{{ importLabel(i.status) }}</td>
                <td>
                  @if (i.status === 'processing') { <div class="progress"><span></span></div> }
                  @else if (i.status === 'done') { 100 % }
                  @else { - }
                </td>
                <td>{{ i.rowsTotal }}</td><td>{{ i.rowsOk }}</td>
                <td>{{ i.rowsError }}{{ i.errorsTruncated ? ' (rapport tronqué)' : '' }}</td>
                <td>@if (i.hasReport) { <button class="alt" (click)="report(i)">Rapport</button> }</td>
              </tr>
            }
          </tbody>
        </table>
      </div>

      <h2>Statistiques</h2>
      @if (stats) {
        <dl>
          <dt>Candidats</dt><dd>{{ stats.candidates }}</dd>
          @for (d of decisions(); track d[0]) { <dt>{{ d[0] }}</dt><dd>{{ d[1] }}</dd> }
          <dt>Dernier import</dt><dd>{{ stats.lastImport ? (stats.lastImport.filename + ' (' + importLabel(stats.lastImport.status) + ')') : '-' }}</dd>
        </dl>
      }
    }
  `,
})
export class StaffDetailComponent implements OnInit, OnDestroy {
  private api = inject(ApiService);
  private route = inject(ActivatedRoute);
  auth = inject(AuthService);
  code = '';
  s: any = null;
  imports: any[] = [];
  stats: any = null;
  when = '';
  msg = '';
  private timer: any;

  ngOnInit() {
    this.code = this.route.snapshot.paramMap.get('code') ?? '';
    this.loadAll();
    this.timer = setInterval(() => {
      if (this.imports.some((i) => i.status === 'pending' || i.status === 'processing')) this.loadImports(true);
    }, 2000);
  }
  ngOnDestroy() { clearInterval(this.timer); }

  label(s: string) { return STATUS_LABEL[s] ?? s; }
  importLabel(s: string) { return ({ pending: 'en attente', processing: 'en cours', done: 'terminé', failed: 'échoué' } as any)[s] ?? s; }
  decisions(): [string, number][] { return Object.entries(this.stats?.decisions ?? {}) as [string, number][]; }

  loadAll() {
    this.api.adminSession(this.code).subscribe((s) => (this.s = s));
    this.loadImports(false);
    this.loadStats();
  }
  loadImports(refreshStats: boolean) {
    const wasActive = this.imports.some((i) => i.status === 'pending' || i.status === 'processing');
    this.api.imports(this.code).subscribe((r) => {
      this.imports = r;
      const active = r.some((i) => i.status === 'pending' || i.status === 'processing');
      if (refreshStats && wasActive && !active) { this.loadStats(); this.api.adminSession(this.code).subscribe((s) => (this.s = s)); }
    });
  }
  loadStats() { this.api.stats(this.code).subscribe((x) => (this.stats = x)); }

  private act(obs: any) {
    this.msg = '';
    obs.subscribe({ next: (s: any) => (this.s = s), error: (e: any) => (this.msg = apiMessage(e)) });
  }
  publish() { this.act(this.api.publish(this.code)); }
  unpublish() { this.act(this.api.unpublish(this.code)); }
  schedule() { this.act(this.api.schedule(this.code, new Date(this.when).toISOString())); }

  onFile(ev: Event) {
    const f = (ev.target as HTMLInputElement).files?.[0];
    if (f) this.send(f);
    (ev.target as HTMLInputElement).value = '';
  }
  onDrop(ev: DragEvent) {
    ev.preventDefault();
    const f = ev.dataTransfer?.files?.[0];
    if (f) this.send(f);
  }
  private send(f: File) {
    this.msg = '';
    this.api.upload(this.code, f).subscribe({
      next: () => this.loadImports(false),
      error: (e) => (this.msg = apiMessage(e, 'Dépôt impossible.')),
    });
  }
  report(i: any) { this.api.importErrors(i.id).subscribe((b) => saveBlob(b, `erreurs-${i.filename}`)); }
}
