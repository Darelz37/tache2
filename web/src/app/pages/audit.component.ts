import { Component, OnInit, inject } from '@angular/core';
import { DatePipe } from '@angular/common';
import { ApiService } from '../core/api.service';
import { apiMessage } from '../core/util';

@Component({
  standalone: true,
  imports: [DatePipe],
  template: `
    <h1>Journal d'audit</h1>
    @if (error) { <div class="err">{{ error }}</div> }
    <div class="table-wrap">
      <table>
        <thead><tr><th>Date</th><th>Auteur</th><th>Action</th><th>Cible</th><th>Détails</th></tr></thead>
        <tbody>
          @for (a of rows; track a.id) {
            <tr><td>{{ a.at | date:'dd/MM/yyyy HH:mm:ss' }}</td><td>{{ a.user }}</td><td>{{ a.action }}</td><td>{{ a.target }}</td><td>{{ a.details }}</td></tr>
          }
        </tbody>
      </table>
    </div>
  `,
})
export class AuditComponent implements OnInit {
  private api = inject(ApiService);
  rows: any[] = [];
  error = '';
  ngOnInit() { this.api.audit().subscribe({ next: (r) => (this.rows = r), error: (e) => (this.error = apiMessage(e)) }); }
}
