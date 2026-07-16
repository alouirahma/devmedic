import { Component, OnInit } from '@angular/core';
import { Router } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'app-callback',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './callback.html',
  styleUrl: './callback.scss'
})
export class Callback implements OnInit {
  message = 'Connexion en cours...';
  private codeExchanged = false;

  constructor(
    private router: Router,
    private http: HttpClient
  ) {}

  ngOnInit() {
    if (this.codeExchanged) return;
    this.codeExchanged = true;

    const params = new URLSearchParams(window.location.search);
    const code = params.get('code');
    const error = params.get('error');

    console.log('Callback ngOnInit — code:', code);

    if (error) {
      console.error('Erreur Keycloak:', error);
      this.message = 'Erreur de connexion';
      setTimeout(() => this.router.navigate(['/login']), 2000);
      return;
    }

    if (code) {
      window.history.replaceState({}, '', '/callback');
      this.exchangeCode(code);
    } else {
      this.message = 'Code non trouvé';
      setTimeout(() => this.router.navigate(['/login']), 2000);
    }
  }

  exchangeCode(code: string) {
    const body = new URLSearchParams();
    body.set('grant_type', 'authorization_code');
    body.set('client_id', 'gestion-user');
    body.set('client_secret', 'eBrabPoFXmvao9VvieU2pKNcOYg7Y3TS');
    body.set('code', code);
    body.set('redirect_uri', 'http://localhost:4200/callback');

    console.log('Échange du code...');
    console.log('Code:', code);
    console.log('Redirect URI:', 'http://localhost:4200/callback');

    this.http.post(
      'http://auth.localhost/realms/devmedic/protocol/openid-connect/token',
      body.toString(),
      { headers: { 'Content-Type': 'application/x-www-form-urlencoded' } }
    ).subscribe({
      next: (res: any) => {
        console.log('✅ Token reçu !');
        localStorage.setItem('devmedic_token', res.access_token);

        // ✅ Récupère le token GitHub depuis Keycloak broker et le sauvegarde en DB
        this.fetchGithubToken(res.access_token);

        this.router.navigate(['/dashboard']);
      },
      error: (err) => {
        console.error('❌ Status:', err.status);
        console.error('❌ Error:', JSON.stringify(err.error));
        this.message = `Erreur: ${err.error?.error_description || 'Connexion échouée'}`;
        setTimeout(() => this.router.navigate(['/login']), 3000);
      }
    });
  }

  private async fetchGithubToken(accessToken: string) {
    console.log('🔍 Récupération du token GitHub...');
    
    try {
      // 1. Récupérer le token depuis Keycloak
      const keycloakResponse = await fetch('http://auth.localhost/realms/devmedic/broker/github/token', {
        headers: { 'Authorization': `Bearer ${accessToken}` }
      });
      
      const text = await keycloakResponse.text();
      console.log('📝 Réponse brute:', text);
      
      // Extraire le token de la réponse (format: access_token=xxx&...)
      const params = new URLSearchParams(text);
      const githubToken = params.get('access_token');
      
      if (githubToken) {
        console.log('✅ Token GitHub récupéré !');
        
        // 2. Stocker dans localStorage
        localStorage.setItem('github_token', githubToken);
        
        // 3. Sauvegarder dans le backend (8081)
        await this.saveGithubTokenToBackend(githubToken, accessToken);
        
        console.log('✅ Token GitHub sauvegardé dans le backend');
      } else {
        console.log('ℹ️ Aucun token GitHub trouvé');
      }
    } catch (error) {
      console.error('❌ Erreur fetchGithubToken:', error);
    }
  }

  private async saveGithubTokenToBackend(githubToken: string, keycloakToken: string) {
    try {
      // Utiliser POST pour sauvegarder le token
      const response = await fetch('http://localhost:8081/api/users/me/github-token', {
        method: 'POST',
        headers: {
          'Authorization': `Bearer ${keycloakToken}`,
          'Content-Type': 'application/json'
        },
        body: JSON.stringify({ githubToken })
      });
      
      if (response.ok) {
        const data = await response.json();
        console.log('✅ Token GitHub sauvegardé dans le backend', data);
      } else {
        const errorText = await response.text();
        console.log('⚠️ Sauvegarde backend échouée:', response.status, errorText);
      }
    } catch (error) {
      console.error('❌ Erreur sauvegarde backend:', error);
    }
  }
}