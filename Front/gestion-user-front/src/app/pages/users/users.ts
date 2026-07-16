import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';

export interface UserHR {
  id: number;
  seniorityLevel: 'JUNIOR' | 'MEDIOR' | 'SENIOR' | 'LEAD' | 'STAFF';
  yearsOfExperience: number;
  primarySkill: string;
  user: {
    id: number;
    username: string;
    email: string;
  };
}

@Component({
  selector: 'app-users',
  imports: [CommonModule, FormsModule],
  templateUrl: './users.html',
  styleUrl: './users.scss'
})
export class Users implements OnInit {
  isAdmin = false;
  users: any[] = [];
  filteredUsers: any[] = [];
  loading = true;
  searchQuery = '';
  selectedRole = '';
  selectedSeniority = '';
  showCreateModal = false;
  showDeleteModal = false;
  showUpdateModal = false;
  showUserHrModal = false;
  selectedUser: any = null;

  newUser = {
    username: '',
    email: '',
    roles: ['VIEWER']
  };

  updateUser = {
    id: null,
    username: '',
    email: '',
    roles: [] as string[]
  };

  userHrData: UserHR | null = null;
  newUserHr = {
    seniorityLevel: 'JUNIOR',
    yearsOfExperience: 0,
    primarySkill: ''
  };

  roles = ['ADMIN', 'TEAM_LEAD', 'DEVELOPER', 'ANALYST', 'VIEWER'];
  seniorityLevels = ['JUNIOR', 'MID', 'SENIOR', 'LEAD', 'STAFF'];
  skillsList = [
    'Java', 'Spring Boot', 'Angular', 'React', 'Python', 
    'JavaScript', 'TypeScript', 'Node.js', 'Docker', 'Kubernetes',
    'AWS', 'Azure', 'SQL', 'MongoDB', 'DevOps', 'CI/CD',
    'Git', 'Jenkins', 'Kafka', 'Redis', 'Elasticsearch'
  ];

  constructor(
    private http: HttpClient,
    private router: Router
  ) {}

  ngOnInit() {
  const token = localStorage.getItem('devmedic_token');

  if (token) {
    try {
      const payload = JSON.parse(atob(token.split('.')[1]));
      const roles: string[] = payload?.realm_access?.roles || [];

      this.isAdmin = roles.some(
        role => role.toUpperCase() === 'ADMIN'
      );
    } catch (e) {
      console.error('Erreur décodage token', e);
    }
  }

  this.loadUsers();
}
  getHeaders() {
    const token = localStorage.getItem('devmedic_token');
    if (!token) {
      this.router.navigate(['/login']);
      return new HttpHeaders();
    }
    return new HttpHeaders({ Authorization: `Bearer ${token}` });
  }

  loadUsers() {
    this.loading = true;
    this.http.get<any[]>('http://localhost:8081/api/users', {
      headers: this.getHeaders()
    }).subscribe({
      next: (users) => {
        this.users = users;
        // ⭐ Appel à loadUserRhData pour charger les fiches RH
        this.loadUserRhData(users);
      },
      
    });
  }

  loadUserRhData(users: any[]) {
    let completed = 0;
    
    if (users.length === 0) {
        this.filteredUsers = [];
        this.loading = false;
        return;
    }
    
    users.forEach(user => {
        this.http.get(`http://localhost:8081/api/user-rh/${user.id}`, {
            headers: this.getHeaders()
        }).subscribe({
            next: (hrData: any) => {
                user.hrData = hrData;
                completed++;
                if (completed === users.length) {
                    this.filteredUsers = [...this.users];
                    this.loading = false;
                }
            },
            error: (err) => {
                // 404 = pas de fiche RH, c'est normal
                if (err.status !== 404) {
                    console.error(`Erreur RH pour user ${user.id}:`, err);
                }
                user.hrData = null;
                completed++;
                if (completed === users.length) {
                    this.filteredUsers = [...this.users];
                    this.loading = false;
                }
            }
        });
    });
  }

  openUserHrModal(user: any) {
    if (!this.isAdmin) return;
    this.selectedUser = user;
    this.userHrData = null;
    this.newUserHr = {
      seniorityLevel: 'JUNIOR',
      yearsOfExperience: 0,
      primarySkill: ''
    };
    
    this.http.get(`http://localhost:8081/api/user-rh/${user.id}`, {
      headers: this.getHeaders()
    }).subscribe({
      next: (data: any) => {
        this.userHrData = data;
        this.newUserHr = {
          seniorityLevel: data.seniorityLevel,
          yearsOfExperience: data.yearsOfExperience,
          primarySkill: data.primarySkill
        };
      },
      error: (err) => {
        if (err.status !== 404) {
          console.error('Erreur chargement RH:', err);
        }
        this.userHrData = null;
      }
    });
    
    this.showUserHrModal = true;
  }

  saveUserHr() {
    if (this.userHrData) {
        this.http.put(
            `http://localhost:8081/api/user-rh/${this.selectedUser.id}/seniority`,
            `"${this.newUserHr.seniorityLevel}"`,
            { headers: this.getHeaders() }
        ).subscribe({
            next: () => {
                alert('✅ Fiche RH mise à jour');
                this.showUserHrModal = false;
                this.refreshUserHrData();
            },
            error: (err) => {
                console.error('Erreur mise à jour RH:', err);
                alert('❌ Erreur: ' + this.getErrorMessage(err));
            }
        });
    } else {
        const requestBody = {
            seniorityLevel: this.newUserHr.seniorityLevel,
            yearsOfExperience: this.newUserHr.yearsOfExperience,
            primarySkill: this.newUserHr.primarySkill
        };
        
        this.http.post(
            `http://localhost:8081/api/user-rh/${this.selectedUser.id}`,
            requestBody,
            { headers: this.getHeaders() }
        ).subscribe({
            next: (response) => {
                console.log('Réponse succès:', response);
                alert('✅ Fiche RH créée avec succès');
                this.showUserHrModal = false;
                this.refreshUserHrData();
            },
            error: (err) => {
                console.error('Erreur création RH:', err);
                alert('❌ Erreur création: ' + this.getErrorMessage(err));
            }
        });
    }
  }

  private getErrorMessage(err: any): string {
    if (err.error?.error) return err.error.error;
    if (err.error?.message) return err.error.message;
    if (err.message) return err.message;
    return 'Erreur inconnue';
  }

  refreshUserHrData() {
    if (!this.selectedUser) return;
    
    this.http.get(`http://localhost:8081/api/user-rh/${this.selectedUser.id}`, {
        headers: this.getHeaders()
    }).subscribe({
        next: (data: any) => {
            const user = this.users.find(u => u.id === this.selectedUser.id);
            if (user) {
                user.hrData = data;
            }
            const filteredUser = this.filteredUsers.find(u => u.id === this.selectedUser.id);
            if (filteredUser) {
                filteredUser.hrData = data;
            }
            console.log(`✅ Données RH mises à jour pour user ${this.selectedUser.id}`);
        },
        error: (err) => {
            if (err.status === 404) {
                const user = this.users.find(u => u.id === this.selectedUser.id);
                if (user) {
                    user.hrData = null;
                }
            }
        }
    });
  }

  getSeniorityBadgeClass(seniority: string): string {
    const classes: Record<string, string> = {
      'JUNIOR': 'badge-junior',
      'MEDIOR': 'badge-medior',
      'SENIOR': 'badge-senior',
      'LEAD': 'badge-lead',
      'STAFF': 'badge-staff'
    };
    return classes[seniority] || 'badge-default';
  }

  translateSeniority(seniority: string): string {
    const translations: Record<string, string> = {
      'JUNIOR': 'Junior',
      'MEDIOR': 'Medior',
      'SENIOR': 'Senior',
      'LEAD': 'Lead',
      'STAFF': 'Staff'
    };
    return translations[seniority] || seniority;
  }

  search() {
    this.filteredUsers = this.users.filter(u => {
      const matchSearch = !this.searchQuery ||
        u.username?.toLowerCase().includes(this.searchQuery.toLowerCase()) ||
        u.email?.toLowerCase().includes(this.searchQuery.toLowerCase());
      
      const matchRole = !this.selectedRole ||
        u.roles?.includes(this.selectedRole);
      
      const matchSeniority = !this.selectedSeniority ||
        u.hrData?.seniorityLevel === this.selectedSeniority;
      
      return matchSearch && matchRole && matchSeniority;
    });
  }

  createUser() {
  if (!this.isAdmin) return;

  this.http.post(
    'http://localhost:8081/api/users',
    this.newUser,
    { headers: this.getHeaders() }
  ).subscribe({
    next: () => {
      this.showCreateModal = false;
      this.newUser = {
        username: '',
        email: '',
        roles: ['VIEWER']
      };
      this.loadUsers();
    }
  });
}
  openUpdateModal(user: any) {
  if (!this.isAdmin) return;

  this.selectedUser = user;

  this.updateUser = {
    id: user.id,
    username: user.username,
    email: user.email,
    roles: [...user.roles]
  };

  this.showUpdateModal = true;
}

  updateUserAction() {
      if (!this.isAdmin) return; 
      
    if (!this.updateUser.username || !this.updateUser.email) {
      alert('Veuillez remplir tous les champs');
      return;
    }

    if (!this.updateUser.roles || this.updateUser.roles.length === 0) {
      alert('Veuillez sélectionner au moins un rôle');
      return;
    }

    this.http.put(
      `http://localhost:8081/api/users/${this.updateUser.id}`,
      {
        username: this.updateUser.username,
        email: this.updateUser.email,
        roles: this.updateUser.roles
      },
      { headers: this.getHeaders() }
    ).subscribe({
      next: () => {
        this.showUpdateModal = false;
        this.selectedUser = null;
        this.resetUpdateForm();
        this.loadUsers();
        alert('✅ Utilisateur mis à jour avec succès');
      },
      error: (err) => {
        console.error('Erreur mise à jour:', err);
        alert('❌ Erreur lors de la mise à jour');
      }
    });
  }

  resetUpdateForm() {
    this.updateUser = {
      id: null,
      username: '',
      email: '',
      roles: []
    };
  }

  toggleRoleForUpdate(role: string, event: any) {
    if (event.target.checked) {
      if (!this.updateUser.roles.includes(role)) {
        this.updateUser.roles.push(role);
      }
    } else {
      const index = this.updateUser.roles.indexOf(role);
      if (index > -1) {
        this.updateUser.roles.splice(index, 1);
      }
    }
  }

  isRoleSelectedForUpdate(role: string): boolean {
    return this.updateUser.roles?.includes(role) || false;
  }

  confirmDelete(user: any) {
    this.selectedUser = user;
    this.showDeleteModal = true;
  }

  deleteUser() {
    this.http.delete(`http://localhost:8081/api/users/${this.selectedUser.id}`, {
      headers: this.getHeaders()
    }).subscribe({
      next: () => {
        this.showDeleteModal = false;
        this.selectedUser = null;
        this.loadUsers();
      },
      error: (err) => console.error('Erreur suppression:', err)
    });
  }

  getInitials(username: string): string {
    if (!username) return '?';
    const parts = username.split(/[\s._-]/);
    if (parts.length >= 2) return (parts[0][0] + parts[1][0]).toUpperCase();
    return username.substring(0, 2).toUpperCase();
  }

  getMainRole(roles: any[]): string {
    if (!roles || roles.length === 0) return 'VIEWER';
    const priority = ['ADMIN', 'TEAM_LEAD', 'DEVELOPER', 'ANALYST', 'VIEWER'];
    for (const p of priority) {
      if (roles.includes(p)) return p;
    }
    return roles[0];
  }

  getRoleBadgeClass(roles: any[]): string {
    return 'badge-' + this.getMainRole(roles).toLowerCase().replace('_', '-');
  }
}