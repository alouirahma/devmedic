import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Router } from '@angular/router';

interface RoleStat {
  name: string;
  label: string;
  description: string;
  userCount: number;
  badgeClass: string;
}

interface RoleUser {
  id: string;
  username: string;
  email: string;
  roles: string[];
}

const PERMISSIONS: Record<string, Record<string, boolean>> = {
  Dashboard:    { ADMIN: true,  TEAM_LEAD: true,  DEVELOPER: false, ANALYST: false, VIEWER: false },
  Utilisateurs: { ADMIN: true,  TEAM_LEAD: true,  DEVELOPER: false, ANALYST: false, VIEWER: false },
  Rôles:        { ADMIN: true,  TEAM_LEAD: false, DEVELOPER: false, ANALYST: false, VIEWER: false },
  'Mon profil': { ADMIN: true,  TEAM_LEAD: true,  DEVELOPER: true,  ANALYST: true,  VIEWER: true  },
};

@Component({
  selector: 'app-roles',
  imports: [CommonModule],
  templateUrl: './roles.html',
  styleUrl: './roles.scss',
})
export class Roles implements OnInit {
  readonly roles = ['ADMIN', 'TEAM_LEAD', 'DEVELOPER', 'ANALYST', 'VIEWER'];
  readonly pages = Object.keys(PERMISSIONS);
  readonly permissions = PERMISSIONS;

  roleStats: RoleStat[] = [];
  usersByRole: Record<string, RoleUser[]> = {};
  selectedRole: string | null = null;
  loading = true;

  private readonly roleMeta: Record<string, { label: string; description: string; badgeClass: string }> = {
    ADMIN:     { label: 'Admin',     description: 'Accès total',       badgeClass: 'badge-admin'     },
    TEAM_LEAD: { label: 'Team Lead', description: 'Dashboard + Users', badgeClass: 'badge-team-lead' },
    DEVELOPER: { label: 'Developer', description: 'Profil seulement',  badgeClass: 'badge-developer' },
    ANALYST:   { label: 'Analyst',   description: 'Profil seulement',  badgeClass: 'badge-analyst'   },
    VIEWER:    { label: 'Viewer',    description: 'Lecture seule',      badgeClass: 'badge-viewer'    },
  };

  constructor(private http: HttpClient, private router: Router) {}

  ngOnInit() {
    this.loadUsers();
  }

  private getHeaders(): HttpHeaders {
    const token = localStorage.getItem('devmedic_token');
    if (!token) { this.router.navigate(['/login']); return new HttpHeaders(); }
    return new HttpHeaders({ Authorization: `Bearer ${token}` });
  }

  private loadUsers() {
    this.loading = true;
    this.http.get<RoleUser[]>('http://localhost:8081/api/users', {
      headers: this.getHeaders()
    }).subscribe({
      next: (users) => {
        this.buildStats(users);
        this.loading = false;
      },
      error: () => this.loading = false
    });
  }

  private buildStats(users: RoleUser[]) {
    // Groupe les users par rôle
    this.usersByRole = {};
    for (const role of this.roles) {
      this.usersByRole[role] = users.filter(u => u.roles?.includes(role));
    }

    // Construit les cartes stats
    this.roleStats = this.roles.map(role => ({
      name: role,
      ...this.roleMeta[role],
      userCount: this.usersByRole[role].length,
    }));
  }

  selectRole(role: string) {
    this.selectedRole = this.selectedRole === role ? null : role;
  }

  hasPermission(page: string, role: string): boolean {
    return this.permissions[page]?.[role] ?? false;
  }

  getInitials(username: string): string {
    if (!username) return '?';
    const parts = username.split(/[\s._-]/);
    return parts.length >= 2
      ? (parts[0][0] + parts[1][0]).toUpperCase()
      : username.substring(0, 2).toUpperCase();
  }

  removeRoleFromUser(user: RoleUser, role: string) {
    const updated = { ...user, roles: user.roles.filter(r => r !== role) };
    this.http.put(`http://localhost:8081/api/users/${user.id}`, updated, {
      headers: this.getHeaders()
    }).subscribe({
      next: () => this.loadUsers(),
      error: (err) => console.error('Erreur retrait rôle:', err)
    });
  }
}