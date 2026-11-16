import { Routes } from '@angular/router';
import { authGuard } from './core/auth.guard';
import { HomeComponent } from './pages/home.component';
import { SessionComponent } from './pages/session.component';
import { LoginComponent } from './pages/login.component';
import { StaffSessionsComponent } from './pages/staff-sessions.component';
import { StaffDetailComponent } from './pages/staff-detail.component';
import { AuditComponent } from './pages/audit.component';
import { InfraComponent } from './pages/infra.component';

export const routes: Routes = [
  { path: '', component: HomeComponent },
  { path: 's/:code', component: SessionComponent },
  { path: 'staff/login', component: LoginComponent },
  { path: 'staff/sessions', component: StaffSessionsComponent, canActivate: [authGuard] },
  { path: 'staff/sessions/:code', component: StaffDetailComponent, canActivate: [authGuard] },
  { path: 'staff/audit', component: AuditComponent, canActivate: [authGuard] },
  { path: 'infra', component: InfraComponent },
  { path: '**', redirectTo: '' },
];
