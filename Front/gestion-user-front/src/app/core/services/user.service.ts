import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { User, Profile, Role, UserHR} from '../models/user.model';

@Injectable({ providedIn: 'root' })
export class UserService {

  private api = 'http://localhost:8081/api';

  constructor(private http: HttpClient) {}

  // ── Profile ──
  getMyProfile(): Observable<Profile> {
    return this.http.get<Profile>(`${this.api}/users/me`);
  }

  // ── Users ──
  getAll(): Observable<User[]> {
    return this.http.get<User[]>(`${this.api}/users`);
  }

  getById(id: number): Observable<User> {
    return this.http.get<User>(`${this.api}/users/${id}`);
  }

  create(user: User): Observable<User> {
    return this.http.post<User>(`${this.api}/users`, user);
  }

  delete(id: number): Observable<void> {
    return this.http.delete<void>(`${this.api}/users/${id}`);
  }

  changeEmail(id: number, email: string): Observable<void> {
    return this.http.put<void>(`${this.api}/users/${id}/email`, email);
  }

  addRole(id: number, role: Role): Observable<void> {
    return this.http.post<void>(`${this.api}/users/${id}/roles`, role);
  }

  // ── UserHR ──
  getUserHR(userId: number): Observable<UserHR> {
    return this.http.get<UserHR>(`${this.api}/user-rh/${userId}`);
  }

  createUserHR(userId: number, userHR: UserHR): Observable<UserHR> {
    return this.http.post<UserHR>(`${this.api}/user-rh/${userId}`, userHR);
  }

  updateSeniority(userId: number, level: string): Observable<void> {
    return this.http.put<void>(`${this.api}/user-rh/${userId}/seniority`, level);
  }

  isSenior(userId: number): Observable<boolean> {
    return this.http.get<boolean>(`${this.api}/user-rh/${userId}/is-senior`);
  }

}