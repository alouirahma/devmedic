import { Component, OnInit } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Router } from '@angular/router';

@Component({
  selector: 'app-navbar',
  imports: [],
  templateUrl: './navbar.html',
  styleUrl: './navbar.scss'
})
export class Navbar implements OnInit {
  user: any = null;
  pageTitle = '';

  constructor(
    private http: HttpClient,
    private router: Router
  ) {}

  ngOnInit() {
  const token = localStorage.getItem('devmedic_token');
  
  if (!token) return; // Ne redirige pas, juste ne charge pas le profil
  
  this.http.get('http://localhost:8081/api/users/me', {
    headers: { Authorization: `Bearer ${token}` }
  }).subscribe({
    next: (data: any) => this.user = data,
    error: () => this.user = null // Ne redirige pas non plus
  });
}
  getInitials(username: string): string {
    if (!username) return '?';
    const parts = username.split(/[\s._-]/);
    if (parts.length >= 2) {
      return (parts[0][0] + parts[1][0]).toUpperCase();
    }
    return username.substring(0, 2).toUpperCase();
  }

  getMainRole(roles: string[]): string {
    if (!roles) return '';
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
  localStorage.removeItem('devmedic_token');
  localStorage.removeItem('devmedic_refresh_token');
  this.router.navigate(['/login']);
}
}