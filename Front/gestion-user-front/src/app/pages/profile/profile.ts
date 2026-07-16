import { Component, OnInit, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Router } from '@angular/router';
import { ActivityService } from '../../core/services/activity.service';
import { Subject, takeUntil } from 'rxjs';

@Component({
  selector: 'app-profile',
  imports: [CommonModule, FormsModule],
  templateUrl: './profile.html',
  styleUrl: './profile.scss'
})
export class Profile implements OnInit, OnDestroy {
  user: any = null;
  loading = true;
  error = '';
  daysSinceMember = 0;
  recentActivity: any[] = [];

  showPasswordModal = false;
  newPassword = '';
  confirmPassword = '';
  passwordError = '';
  passwordSuccess = false;
  passwordLoading = false;

  private destroy$ = new Subject<void>();
  private profileLoaded = false; // ← évite les appels multiples

  constructor(
    private http: HttpClient,
    private router: Router,
    private activityService: ActivityService
  ) {}

  ngOnInit() {
    if (this.profileLoaded) return; // ← protection boucle infinie
    this.profileLoaded = true;

    const token = localStorage.getItem('devmedic_token');
    if (!token) {
      this.router.navigate(['/login']);
      return;
    }

    this.loadUserProfile();
  }

  ngOnDestroy() {
    this.destroy$.next();
    this.destroy$.complete();
  }

  loadUserProfile() {
    const token = localStorage.getItem('devmedic_token');

    this.http.get('http://localhost:8081/api/users/me', {
      headers: new HttpHeaders({ Authorization: `Bearer ${token}` })
    })
    .pipe(takeUntil(this.destroy$))
    .subscribe({
      next: (data: any) => {
        this.user = data;
        this.loading = false;
        this.error = '';
        this.calculateMemberDays();
        this.loadActivities();

        // ← enregistrer l'activité APRÈS chargement réussi seulement
        this.activityService.addActivity('Profil consulté', 'PROFILE_VIEW');
        localStorage.setItem('last_profile_view', new Date().toISOString());
      },
      error: (err) => {
        console.error('Erreur chargement profil:', err);
        this.loading = false;

        if (err.status === 401) {
          // token expiré → nettoyer et rediriger
          localStorage.removeItem('devmedic_token');
          localStorage.removeItem('devmedic_refresh_token');
          this.router.navigate(['/login']);
        } else {
          this.error = 'Impossible de charger le profil';
        }
      }
    });
  }

  calculateMemberDays() {
    this.daysSinceMember = 30;
  }

  loadActivities() {
    const activities = this.activityService.getRecentActivities(10);
    this.recentActivity = activities.map(activity => ({
      action: activity.action,
      type: activity.type,
      timestamp: activity.timestamp
    }));
  }

  getActivityIcon(type: string): string {
    const icons: any = {
      'LOGIN': 'bi-box-arrow-in-right',
      'DASHBOARD_VISIT': 'bi-grid',
      'PROFILE_VIEW': 'bi-person',
      'TOKEN_LOGIN': 'bi-key',
      'LOGOUT': 'bi-box-arrow-left'
    };
    return icons[type] || 'bi-clock-history';
  }

  getActivityColor(type: string): string {
    const colors: any = {
      'LOGIN': '#1D9E75',
      'DASHBOARD_VISIT': '#BA7517',
      'PROFILE_VIEW': '#378ADD',
      'TOKEN_LOGIN': '#854F0B',
      'LOGOUT': '#A32D2D'
    };
    return colors[type] || '#888888';
  }

  getTimeAgo(timestamp: string): string {
    const date = new Date(timestamp);
    const now = new Date();
    const diffInSeconds = Math.floor((now.getTime() - date.getTime()) / 1000);
    const diffInMinutes = Math.floor(diffInSeconds / 60);
    const diffInHours = Math.floor(diffInMinutes / 60);
    const diffInDays = Math.floor(diffInHours / 24);

    if (diffInSeconds < 60) return "À l'instant";
    if (diffInMinutes < 60) return `Il y a ${diffInMinutes} min`;
    if (diffInHours < 24) return `Il y a ${diffInHours} h`;
    if (diffInDays === 1) return 'Hier';
    if (diffInDays < 30) return `Il y a ${diffInDays} jours`;
    return date.toLocaleDateString('fr-FR');
  }

  closeModal() {
    this.showPasswordModal = false;
    this.newPassword = '';
    this.confirmPassword = '';
    this.passwordError = '';
    this.passwordSuccess = false;
    this.passwordLoading = false;
  }

  changePassword() {
    if (!this.newPassword || !this.confirmPassword) {
      this.passwordError = 'Veuillez remplir tous les champs';
      return;
    }

    if (this.newPassword.length < 8) {
      this.passwordError = 'Le mot de passe doit contenir au moins 8 caractères';
      return;
    }

    if (this.newPassword !== this.confirmPassword) {
      this.passwordError = 'Les mots de passe ne correspondent pas';
      return;
    }

    this.passwordLoading = true;
    this.passwordError = '';

    const token = localStorage.getItem('devmedic_token');

    this.http.put(
      'http://localhost:8081/api/users/change-password',
      { newPassword: this.newPassword },
      {
        headers: new HttpHeaders({
          Authorization: `Bearer ${token}`,
          'Content-Type': 'application/json'
        })
      }
    )
    .pipe(takeUntil(this.destroy$))
    .subscribe({
      next: () => {
        this.passwordSuccess = true;
        this.passwordLoading = false;
        setTimeout(() => this.closeModal(), 2000);
      },
      error: (err) => {
        console.error('Erreur changement mot de passe:', err);
        if (err.status === 400) {
          this.passwordError = 'Le mot de passe ne respecte pas les règles de sécurité';
        } else if (err.status === 401) {
          this.passwordError = 'Session expirée, veuillez vous reconnecter';
          setTimeout(() => this.router.navigate(['/login']), 2000);
        } else if (err.status === 405) {
          this.passwordError = 'Méthode non supportée par le serveur';
        } else {
          this.passwordError = err.error?.message || 'Erreur lors du changement de mot de passe';
        }
        this.passwordLoading = false;
      }
    });
  }

  getPasswordStrength(): number {
    if (!this.newPassword) return 0;
    let strength = 0;
    if (this.newPassword.length >= 8) strength += 25;
    if (this.newPassword.match(/[a-z]/)) strength += 25;
    if (this.newPassword.match(/[A-Z]/)) strength += 25;
    if (this.newPassword.match(/[0-9]/)) strength += 25;
    return strength;
  }

  getPasswordStrengthColor(): string {
    const strength = this.getPasswordStrength();
    if (strength < 50) return '#A32D2D';
    if (strength < 75) return '#BA7517';
    return '#1D9E75';
  }

  getPasswordStrengthLabel(): string {
    const strength = this.getPasswordStrength();
    if (strength < 50) return 'Faible';
    if (strength < 75) return 'Moyen';
    if (strength < 100) return 'Fort';
    return 'Très fort';
  }

  getInitials(username: string): string {
    if (!username) return '?';
    const parts = username.split(/[\s._-]/);
    if (parts.length >= 2) return (parts[0][0] + parts[1][0]).toUpperCase();
    return username.substring(0, 2).toUpperCase();
  }

  getMainRole(roles: string[]): string {
    if (!roles || roles.length === 0) return 'VIEWER';
    const priority = ['ADMIN', 'TEAM_LEAD', 'DEVELOPER', 'ANALYST', 'VIEWER'];
    for (const p of priority) {
      if (roles.includes(p)) return p;
    }
    return roles[0] || '';
  }

  getRoleBadgeClass(role: string): string {
    return 'badge-' + role.toLowerCase().replace('_', '-');
  }

  logout() {
    this.activityService.addActivity('Déconnexion', 'LOGOUT');
    localStorage.removeItem('devmedic_token');
    localStorage.removeItem('devmedic_refresh_token');
    this.router.navigate(['/login']);
  }
}