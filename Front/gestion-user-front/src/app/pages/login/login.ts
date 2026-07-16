import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { ActivityService } from '../../core/services/activity.service';

@Component({
  selector: 'app-login',
  imports: [CommonModule, FormsModule],
  templateUrl: './login.html',
  styleUrl: './login.scss'
})
export class Login implements OnInit {
  username = '';
  password = '';
  error = '';
  loading = false;
  showTokenInput = false;
  token = '';

  constructor(
    private router: Router,
    private http: HttpClient,
    private activityService: ActivityService
  ) {}

  ngOnInit() {
    const token = localStorage.getItem('devmedic_token');
    if (token) {
      this.router.navigate(['/dashboard']);
    }
  }

  loginWithCredentials() {
    if (!this.username || !this.password) {
      this.error = 'Veuillez remplir tous les champs';
      return;
    }

    this.loading = true;
    this.error = '';

    const body = new URLSearchParams();
    body.set('grant_type', 'password');
    body.set('client_id', 'gestion-user');
    body.set('client_secret', 'eBrabPoFXmvao9VvieU2pKNcOYg7Y3TS');
    body.set('username', this.username);
    body.set('password', this.password);

    this.http.post(
      'http://auth.localhost/realms/devmedic/protocol/openid-connect/token',
      body.toString(),
      { headers: { 'Content-Type': 'application/x-www-form-urlencoded' } }
    ).subscribe({
      next: (res: any) => {
        localStorage.setItem('devmedic_token', res.access_token);
        
        // Enregistrer l'activité de connexion
        this.activityService.addActivity('Connexion réussie', 'LOGIN');
        localStorage.setItem('last_login', new Date().toISOString());
        
        this.loading = false;
        this.router.navigate(['/dashboard']);
      },
      error: () => {
        this.loading = false;
        this.error = 'Identifiants incorrects';
      }
    });
  }

  loginWith(provider: 'github' | 'gitlab') {
    const keycloakUrl = 'http://auth.localhost/realms/devmedic/protocol/openid-connect/auth';
    const clientId = 'gestion-user';
    const redirectUri = encodeURIComponent('http://localhost:4200/callback');
    const idpHint = provider === 'github' ? 'github' : 'gitlab';
    window.location.href =
      `${keycloakUrl}?client_id=${clientId}&redirect_uri=${redirectUri}&response_type=code&scope=openid&kc_idp_hint=${idpHint}`;
  }

  forgotPassword() {
    window.location.href =
      'http://auth.localhost/realms/devmedic/login-actions/reset-credentials?client_id=gestion-user';
  }

  loginWithToken() {
    if (!this.token.trim()) {
      this.error = 'Veuillez entrer un token JWT';
      return;
    }
    localStorage.setItem('devmedic_token', this.token.trim());
    
    // Enregistrer l'activité de connexion par token
    this.activityService.addActivity('Connexion par token JWT', 'TOKEN_LOGIN');
    
    this.router.navigate(['/dashboard']);
  }
}