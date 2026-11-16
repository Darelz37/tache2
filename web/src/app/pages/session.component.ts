import { Component, OnDestroy, OnInit, inject } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { ApiService } from '../core/api.service';
import { saveBlob } from '../core/util';

/** WF02 (avant publication), WF03 (formulaire) et WF04 (résultat et messages d'erreur). */
@Component({
  standalone: true,
  imports: [FormsModule, RouterLink],
  template: `
    <p><a routerLink="/">← Accueil</a></p>
    @if (notFound) {
      <h1>Session introuvable</h1>
    } @else if (!info) {
      <p class="muted">Chargement…</p>
    } @else {
      <h1>{{ info.code }} · {{ info.title }}</h1>

      @if (result) {
        <div class="box">
          <h2 style="margin-top:0">{{ result.candidate.fullName }}</h2>
          <dl>
            <dt>Matricule</dt><dd>{{ result.candidate.matricule }}</dd>
            <dt>Décision</dt><dd>{{ result.result.decision }}</dd>
            <dt>Moyenne</dt><dd>{{ result.result.average === null ? '-' : (result.result.average.toFixed(2).replace('.', ',') + ' / 20') }}</dd>
            <dt>Mention</dt><dd>{{ result.result.mention || '-' }}</dd>
          </dl>
          <div class="row">
            <button (click)="downloadAttestation()" [disabled]="busy">Télécharger l'attestation (PDF)</button>
            <button class="alt" (click)="reset()">Nouvelle recherche</button>
          </div>
          @if (error) { <div class="err">{{ error }}</div> }
        </div>
      } @else if (info.status === 'published') {
        <div class="box">
          <h2 style="margin-top:0">Consulter mon résultat</h2>
          <form (ngSubmit)="submit()">
            <label for="m">Matricule</label>
            <input id="m" name="m" type="text" [(ngModel)]="matricule" autocomplete="off" required>
            <label for="d">Date de naissance</label>
            <input id="d" name="d" type="date" [(ngModel)]="dob" required>
            <p><button type="submit" [disabled]="busy || !matricule || !dob">Consulter</button></p>
          </form>
          @if (error) { <div class="err">{{ error }}</div> }
        </div>
      } @else if (info.status === 'scheduled') {
        <div class="box">
          <div class="big">Publication dans {{ countdown }}</div>
          <p class="muted">Cette page se met à jour toute seule. Inutile de la recharger.</p>
        </div>
        <div class="box off">
          <label>Matricule</label><input type="text" disabled>
          <label>Date de naissance</label><input type="text" disabled>
          <p class="muted">Formulaire inactif jusqu'à l'heure de publication.</p>
        </div>
      } @else {
        <div class="box"><p>Les résultats de cette session ne sont pas disponibles pour le moment.</p></div>
      }
    }
  `,
})
export class SessionComponent implements OnInit, OnDestroy {
  private api = inject(ApiService);
  private route = inject(ActivatedRoute);
  code = '';
  info: any = null;
  notFound = false;
  offset = 0; // décalage serveur - horloge locale
  countdown = '';
  matricule = '';
  dob = '';
  busy = false;
  error = '';
  result: any = null;
  private pollTimer: any;
  private tickTimer: any;
  private fired = false;

  ngOnInit() {
    this.code = this.route.snapshot.paramMap.get('code') ?? '';
    this.loop();
    this.tickTimer = setInterval(() => this.tick(), 1000);
  }

  ngOnDestroy() {
    clearTimeout(this.pollTimer);
    clearInterval(this.tickTimer);
  }

  /** Interrogation du statut : toutes les 5 s + gigue aléatoire de 0 à 3 s. */
  private loop() {
    this.fetch();
    this.pollTimer = setTimeout(() => { if (this.info?.status !== 'published') this.loop(); }, 5000 + Math.random() * 3000);
  }

  private fetch() {
    this.api.status(this.code).subscribe({
      next: (s) => {
        this.info = s;
        this.offset = new Date(s.serverTime).getTime() - Date.now();
        if (s.status !== 'scheduled') this.fired = false;
        this.tick();
      },
      error: (e: HttpErrorResponse) => { if (e.status === 404) this.notFound = true; },
    });
  }

  private tick() {
    if (this.info?.status !== 'scheduled' || !this.info.publishAt) return;
    const ms = new Date(this.info.publishAt).getTime() - (Date.now() + this.offset);
    const s = Math.max(0, Math.floor(ms / 1000));
    const p = (n: number) => String(n).padStart(2, '0');
    this.countdown = `${p(Math.floor(s / 3600))} : ${p(Math.floor((s % 3600) / 60))} : ${p(s % 60)}`;
    if (ms <= 0 && !this.fired) { // à l'heure : une interrogation avec gigue, sans attendre le prochain cycle
      this.fired = true;
      setTimeout(() => this.fetch(), Math.random() * 1500);
    }
  }

  submit() {
    this.busy = true;
    this.error = '';
    this.api.lookup(this.code, this.matricule.trim(), this.dob).subscribe({
      next: (r) => { this.busy = false; this.result = r; },
      error: (e: HttpErrorResponse) => { this.busy = false; this.error = this.message(e); },
    });
  }

  downloadAttestation() {
    this.busy = true;
    this.error = '';
    this.api.attestation(this.code, this.matricule.trim(), this.dob).subscribe({
      next: (b) => { this.busy = false; saveBlob(b, `attestation-${this.matricule.trim()}.pdf`); },
      error: (e: HttpErrorResponse) => { this.busy = false; this.error = this.message(e); },
    });
  }

  reset() { this.result = null; this.matricule = ''; this.dob = ''; this.error = ''; }

  private message(e: HttpErrorResponse): string {
    switch (e.status) {
      case 404: return 'Aucun résultat pour ces informations. Vérifiez matricule et date de naissance.';
      case 409:
        this.info = { ...this.info, status: 'scheduled' === this.info.status ? 'scheduled' : 'unpublished' };
        this.loop();
        return 'Les résultats ne sont pas (ou plus) publiés.';
      case 422: return 'Format invalide : vérifiez le matricule et la date de naissance.';
      case 429: {
        const s = e.headers.get('Retry-After');
        return `Trop de tentatives. Réessayez dans ${s ?? 'quelques'} s.`;
      }
      default: return 'Le service est momentanément indisponible. Réessayez dans un instant.';
    }
  }
}
