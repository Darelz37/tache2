import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';

@Injectable({ providedIn: 'root' })
export class ApiService {
  private http = inject(HttpClient);

  // Public
  sessions() { return this.http.get<any[]>('/api/sessions'); }
  status(code: string) { return this.http.get<any>(`/api/sessions/${code}/status`); }
  lookup(code: string, matricule: string, dob: string) {
    return this.http.post<any>(`/api/sessions/${code}/lookup`, { matricule, dob });
  }
  attestation(code: string, matricule: string, dob: string) {
    return this.http.post(`/api/sessions/${code}/attestation`, { matricule, dob }, { responseType: 'blob' as const });
  }
  whoami() { return this.http.get<any>('/api/whoami'); }

  // Personnel
  login(email: string, password: string) { return this.http.post<any>('/api/auth/login', { email, password }); }
  adminSessions() { return this.http.get<any[]>('/api/admin/sessions'); }
  adminSession(code: string) { return this.http.get<any>(`/api/admin/sessions/${code}`); }
  createSession(code: string, title: string) { return this.http.post<any>('/api/admin/sessions', { code, title }); }
  publish(code: string) { return this.http.post<any>(`/api/admin/sessions/${code}/publish`, {}); }
  schedule(code: string, publishAt: string) { return this.http.post<any>(`/api/admin/sessions/${code}/schedule`, { publishAt }); }
  unpublish(code: string) { return this.http.post<any>(`/api/admin/sessions/${code}/unpublish`, {}); }
  upload(code: string, file: File) {
    const f = new FormData();
    f.append('file', file);
    return this.http.post<any>(`/api/admin/sessions/${code}/imports`, f);
  }
  imports(code: string) { return this.http.get<any[]>(`/api/admin/sessions/${code}/imports`); }
  importErrors(id: string) { return this.http.get(`/api/admin/imports/${id}/errors`, { responseType: 'blob' as const }); }
  stats(code: string) { return this.http.get<any>(`/api/admin/sessions/${code}/stats`); }
  audit() { return this.http.get<any[]>('/api/admin/audit'); }
}
