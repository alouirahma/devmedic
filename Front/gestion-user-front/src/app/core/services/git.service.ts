import { Injectable } from '@angular/core';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Observable, catchError, of } from 'rxjs';

@Injectable({ providedIn: 'root' })
export class GitService {

  private baseUrl = 'http://localhost:8082/api/git';

  constructor(private http: HttpClient) {}

  private getHeaders(): HttpHeaders {
    const token = localStorage.getItem('devmedic_token');
    return new HttpHeaders({ Authorization: `Bearer ${token}` });
  }

  // ─── SYNC ──────────────────────────────────────────────────────────
  syncAuto(): Observable<any> {
    return this.http.post<any>(
      `${this.baseUrl}/sync`, {},
      { headers: this.getHeaders() }
    ).pipe(catchError(err => {
      console.warn('Git sync non bloquant:', err);
      return of({ repositoriesImported: 0, errors: [] });
    }));
  }

  // ─── REPOSITORIES ──────────────────────────────────────────────────
  getAllRepositories(): Observable<any[]> {
    return this.http.get<any[]>(
      `${this.baseUrl}/repositories`,
      { headers: this.getHeaders() }
    );
  }

  getRepositoryById(id: number): Observable<any> {
    return this.http.get<any>(
      `${this.baseUrl}/repositories/${id}`,
      { headers: this.getHeaders() }
    );
  }

  analyzeRepositories(ids: number[], githubToken?: string, gitlabToken?: string): Observable<any> {
  const body: any = { repositoryIds: ids };
  if (githubToken) body.githubToken = githubToken;
  if (gitlabToken) body.gitlabToken = gitlabToken;

  return this.http.post<any>(
    `${this.baseUrl}/import/analyze`,
    body,
    { headers: this.getHeaders() }
  );
}

  // ─── DASHBOARD ─────────────────────────────────────────────────────
  getDashboardOverview(): Observable<any> {
    return this.http.get<any>(
      `${this.baseUrl}/dashboard/overview`,
      { headers: this.getHeaders() }
    );
  }

  getDashboardByRepo(repoId: number): Observable<any> {
    return this.http.get<any>(
      `${this.baseUrl}/dashboard/${repoId}`,
      { headers: this.getHeaders() }
    );
  }

  // ─── COMMITS ───────────────────────────────────────────────────────
  getCommitsByRepo(repoId: number): Observable<any[]> {
    return this.http.get<any[]>(
      `${this.baseUrl}/commits/repository/${repoId}`,
      { headers: this.getHeaders() }
    );
  }

  getCommitStats(repoId: number): Observable<any> {
    return this.http.get<any>(
      `${this.baseUrl}/commits/repository/${repoId}/stats`,
      { headers: this.getHeaders() }
    );
  }

  // ─── BRANCHES ──────────────────────────────────────────────────────
  getBranchesByRepo(repoId: number): Observable<any[]> {
    return this.http.get<any[]>(
      `${this.baseUrl}/branches/repository/${repoId}`,
      { headers: this.getHeaders() }
    );
  }

  // ─── PULL REQUESTS ─────────────────────────────────────────────────
  getPullRequestsByRepo(repoId: number): Observable<any[]> {
    return this.http.get<any[]>(
      `${this.baseUrl}/pull-requests/repository/${repoId}`,
      { headers: this.getHeaders() }
    );
  }

  getPullRequestStats(repoId: number): Observable<any> {
    return this.http.get<any>(
      `${this.baseUrl}/pull-requests/repository/${repoId}/stats`,
      { headers: this.getHeaders() }
    );
  }

  // ─── CONTRIBUTIONS ─────────────────────────────────────────────────
  getContributionStats(repoId: number): Observable<any> {
    return this.http.get<any>(
      `${this.baseUrl}/contributions/repository/${repoId}/stats`,
      { headers: this.getHeaders() }
    );
  }

  // ─── TAGS ──────────────────────────────────────────────────────────
  getTagsByRepo(repoId: number): Observable<any[]> {
    return this.http.get<any[]>(
      `${this.baseUrl}/tags/repository/${repoId}`,
      { headers: this.getHeaders() }
    );
  }
}