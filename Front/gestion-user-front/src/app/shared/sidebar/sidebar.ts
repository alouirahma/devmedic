import { Component, OnInit } from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';

@Component({
  selector: 'app-sidebar',
  imports: [RouterLink, RouterLinkActive],
  templateUrl: './sidebar.html',
  styleUrl: './sidebar.scss'
})
export class Sidebar implements OnInit {
  isAdmin = false;
  isAdminOrLead = false;
  gitOpen = false;

  ngOnInit() {
    const token = localStorage.getItem('devmedic_token');
    if (!token) return;

    try {
      const payload = JSON.parse(atob(token.split('.')[1]));
      const roles: string[] = payload?.realm_access?.roles || [];
      this.isAdmin = roles.some(r => r.toUpperCase() === 'ADMIN');
      this.isAdminOrLead = roles.some(r =>
        ['ADMIN', 'TEAM_LEAD'].includes(r.toUpperCase())
      );
    } catch {}
  }

  toggleGit() {
    this.gitOpen = !this.gitOpen;
  }
}