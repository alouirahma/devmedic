import { Injectable, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Profile } from '../models/user.model';
import { tap } from 'rxjs';
import { GitService } from './git.service';

@Injectable({ providedIn: 'root' })
export class AuthService {

  private tokenKey = 'devmedic_token';
  currentUser = signal<Profile | null>(null);

  constructor(private http: HttpClient, private gitService: GitService) {}

  setToken(token: string) {
    localStorage.setItem(this.tokenKey, token)
    this.gitService.syncAuto().subscribe(res => {
  console.log(`✅ ${res.repositoriesImported} repos synchronisés automatiquement`);
});
  }

  getToken(): string | null {
    return localStorage.getItem(this.tokenKey);
  }

  isLoggedIn(): boolean {
    return !!this.getToken();
  }

  loadProfile() {
    return this.http.get<Profile>('http://localhost:8081/api/users/me').pipe(
      tap(profile => this.currentUser.set(profile))
    );
  }

  hasRole(role: string): boolean {
    const user = this.currentUser();
    if (!user) return false;
    return user.roles.some(r =>
      r.toUpperCase() === role.toUpperCase() ||
      r.toUpperCase() === 'ROLE_' + role.toUpperCase()
    );
  }

  isAdmin(): boolean { return this.hasRole('ADMIN'); }
  isTeamLead(): boolean { return this.hasRole('TEAM_LEAD'); }
  isDeveloper(): boolean { return this.hasRole('DEVELOPER'); }

  logout() {
    localStorage.removeItem(this.tokenKey);
    this.currentUser.set(null);
    window.location.href =
      'http://auth.localhost/realms/devmedic/protocol/openid-connect/logout';
  }
}