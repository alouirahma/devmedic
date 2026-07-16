// services/user-hr.service.ts
import { Injectable } from '@angular/core';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Observable } from 'rxjs';
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

export interface CreateUserHRDto {
  seniorityLevel: string;
  yearsOfExperience: number;
  primarySkill: string;
}

@Injectable({
  providedIn: 'root'
})
export class UserHrService {
  private apiUrl = 'http://localhost:8081/api/user-rh';

  constructor(
    private http: HttpClient,
    private router: Router
  ) {}

  private getHeaders(): HttpHeaders {
    const token = localStorage.getItem('devmedic_token');
    if (!token) {
      this.router.navigate(['/login']);
      return new HttpHeaders();
    }
    return new HttpHeaders({ Authorization: `Bearer ${token}` });
  }

  // Créer une fiche RH pour un utilisateur
  create(userId: number, data: CreateUserHRDto): Observable<UserHR> {
    return this.http.post<UserHR>(`${this.apiUrl}/${userId}`, data, {
      headers: this.getHeaders()
    });
  }

  // Récupérer la fiche RH d'un utilisateur
  getByUserId(userId: number): Observable<UserHR> {
    return this.http.get<UserHR>(`${this.apiUrl}/${userId}`, {
      headers: this.getHeaders()
    });
  }

  // Vérifier si l'utilisateur est Senior ou plus
  isSeniorOrAbove(userId: number): Observable<boolean> {
    return this.http.get<boolean>(`${this.apiUrl}/${userId}/is-senior`, {
      headers: this.getHeaders()
    });
  }

  // Mettre à jour le niveau de séniorité
  updateSeniority(userId: number, seniorityLevel: string): Observable<void> {
    return this.http.put<void>(`${this.apiUrl}/${userId}/seniority`, seniorityLevel, {
      headers: this.getHeaders()
    });
  }
}