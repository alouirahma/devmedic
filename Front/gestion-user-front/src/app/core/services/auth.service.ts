import { Injectable, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Profile } from '../models/user.model';
import { tap } from 'rxjs';
import { GitService } from './git.service';

@Injectable({ providedIn: 'root' })
export class AuthService {

  private tokenKey = 'devmedic_token';
  private keycloakTokenKey = 'keycloak_token'; // 🔥 AJOUT
  currentUser = signal<Profile | null>(null);

  constructor(private http: HttpClient, private gitService: GitService) {}

  setToken(token: string) {
    // 🔥 STOCKER DANS LES DEUX CLÉS
    localStorage.setItem(this.tokenKey, token);
    localStorage.setItem(this.keycloakTokenKey, token);
    
    this.gitService.syncAuto().subscribe(res => {
      console.log(`✅ ${res.repositoriesImported} repos synchronisés automatiquement`);
    });
  }

  getToken(): string | null {
    // 🔥 RÉCUPÉRER DEPUIS LES DEUX CLÉS
    return localStorage.getItem(this.tokenKey) || localStorage.getItem(this.keycloakTokenKey);
  }

  getGithubToken(): string | null {
    return localStorage.getItem('github_token');
  }

  isLoggedIn(): boolean {
    return !!this.getToken();
  }

  loadProfile() {
    const token = this.getToken();
    if (!token) {
      console.warn('⏭️ Aucun token pour charger le profil');
      return this.http.get<Profile>('http://localhost:8081/api/users/me');
    }
    
    return this.http.get<Profile>('http://localhost:8081/api/users/me', {
      headers: { Authorization: `Bearer ${token}` }
    }).pipe(
      tap(profile => {
        console.log('✅ Profil chargé:', profile);
        this.currentUser.set(profile);
      })
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
    // 🔥 SUPPRIMER TOUS LES TOKENS
    localStorage.removeItem(this.tokenKey);
    localStorage.removeItem(this.keycloakTokenKey);
    localStorage.removeItem('github_token');
    localStorage.removeItem('devmedic_refresh_token');
    
    this.currentUser.set(null);
    
    // Redirection vers Keycloak logout
    window.location.href =
      'http://auth.localhost/realms/devmedic/protocol/openid-connect/logout';
  }
}