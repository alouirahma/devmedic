import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { forkJoin, of } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { GitService } from '../../../core/services/git.service';

interface AnalyzeResult {
  repositoriesAnalyzed: number;
  errors: string[];
  riskResults?: Record<string, RepoRiskInfo>; // ✅ §3.8 — clé = nom du repo
}

// ✅ §3.8 — Résultat du calcul de risque, reçu directement dans la réponse d'analyse
interface RepoRiskInfo {
  stabilityScore: number;
  hotspotCount: number;
  technicalDebtMinutes: number;
  riskLevel: 'STABLE' | 'MODERATE' | 'CRITICAL';
  bySeniority?: { seniority: string; hotspotCount: number }[];
  topHotspots?: {
    shortSha: string;
    message: string;
    authorName: string;
    totalChanges: number;
    committedAt: string;
  }[];
}

// ✅ Stats par repo affichées dans le drawer après analyse
interface RepoAnalyzeStats {
  totalPushes: number;
  normalPushes: number;
  forcePushes: number;
  totalCommits: number;
  totalPullRequests: number;
  branchesTotal: number;
  branchesActive: number;
  branchesStale: number;
  mergeRate: number;
  topContributors: { name: string; commits: number; score: number }[];
  loaded: boolean;
}

@Component({
  selector: 'app-repositories',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './repositories.html',
  styleUrl: './repositories.scss'
})
export class Repositories implements OnInit {

  Math = Math;

  repositories: any[] = [];
  filteredRepos: any[] = [];
  paginatedRepos: any[] = [];
  loading = true;
  error = '';

  searchTerm = '';
  selectedProvider = 'ALL';
  selectedVisibility = 'ALL';

  totalCount = 0;
  githubCount = 0;
  gitlabCount = 0;
  publicCount = 0;
  privateCount = 0;

  currentPage = 1;
  itemsPerPage = 10;
  totalPages = 0;
  pageSizes = [5, 10, 25, 50, 100];

  selectedRepos = new Set<number>();

  // ✅ Drawer d'analyse
  drawerVisible = false;
  analyzeLoading = false;
  analyzeResult: AnalyzeResult | null = null;
  analyzeRepoNames: { id: number; name: string; provider: string }[] = [];

  // ✅ Stats détaillées (pushes / commits / PR) par repoId, affichées après l'analyse
  repoStats = new Map<number, RepoAnalyzeStats>();

  constructor(
    private http: HttpClient,
    private gitService: GitService
  ) {}

  ngOnInit() {
    this.loadRepositories();
  }

  private getHeaders() {
    const token = localStorage.getItem('devmedic_token');
    return { Authorization: `Bearer ${token}` };
  }

  loadRepositories() {
    this.http.get<any[]>('http://localhost:8082/api/git/repositories', { headers: this.getHeaders() })
      .subscribe({
        next: (data) => {
          console.log('✅ Repositories loaded:', data.length);
          console.log('📦 First repo:', data[0]?.name); // Debug

          this.repositories = data;
          this.filteredRepos = [...data];
          this.calculateStats();

          // ✅ CORRECTION : Mettre à jour la pagination après chargement
          this.totalPages = Math.ceil(this.filteredRepos.length / this.itemsPerPage);
          if (this.totalPages === 0) this.totalPages = 1;
          this.updatePagination();

          this.loading = false;
        },
        error: (err) => {
          console.error('❌ Error loading repositories:', err);
          this.error = 'Erreur lors du chargement des repositories';
          this.loading = false;
        }
      });
  }

  calculateStats() {
    this.totalCount = this.repositories.length;
    this.githubCount = this.repositories.filter(r => r.provider === 'GITHUB').length;
    this.gitlabCount = this.repositories.filter(r => r.provider === 'GITLAB').length;
    this.publicCount = this.repositories.filter(r => !r.private).length;
    this.privateCount = this.repositories.filter(r => r.private).length;
  }

  applyFilters() {
    console.log('🔍 Applying filters...');
    this.filteredRepos = this.repositories.filter(repo => {
      const matchSearch = !this.searchTerm ||
        repo.name.toLowerCase().includes(this.searchTerm.toLowerCase()) ||
        repo.owner.toLowerCase().includes(this.searchTerm.toLowerCase());
      const matchProvider = this.selectedProvider === 'ALL' || repo.provider === this.selectedProvider;
      const matchVisibility = this.selectedVisibility === 'ALL' ||
        (this.selectedVisibility === 'PUBLIC' && !repo.private) ||
        (this.selectedVisibility === 'PRIVATE' && repo.private);
      return matchSearch && matchProvider && matchVisibility;
    });
    console.log('📊 Filtered repos:', this.filteredRepos.length);
    this.currentPage = 1;
    this.totalPages = Math.ceil(this.filteredRepos.length / this.itemsPerPage);
    if (this.totalPages === 0) this.totalPages = 1;
    this.updatePagination();
    this.selectedRepos.forEach(id => {
      if (!this.filteredRepos.find(r => r.id === id)) {
        this.selectedRepos.delete(id);
      }
    });
  }

  updatePagination() {
    const totalItems = this.filteredRepos.length;

    // ✅ Recalculer totalPages
    this.totalPages = Math.ceil(totalItems / this.itemsPerPage);
    if (this.totalPages === 0) this.totalPages = 1;

    // ✅ S'assurer que currentPage est valide
    if (this.currentPage > this.totalPages) {
      this.currentPage = this.totalPages;
    }
    if (this.currentPage < 1) {
      this.currentPage = 1;
    }

    const startIndex = (this.currentPage - 1) * this.itemsPerPage;
    const endIndex = Math.min(startIndex + this.itemsPerPage, totalItems);
    this.paginatedRepos = this.filteredRepos.slice(startIndex, endIndex);

    console.log(`📄 Page ${this.currentPage}/${this.totalPages} - Items ${startIndex + 1} à ${endIndex} sur ${totalItems}`);
    console.log('📋 Paginated repos:', this.paginatedRepos.map(r => r.name).join(', '));
  }

  goToPage(page: number) {
    if (page >= 1 && page <= this.totalPages) {
      this.currentPage = page;
      this.updatePagination();
    }
  }

  previousPage() {
    if (this.currentPage > 1) {
      this.goToPage(this.currentPage - 1);
    }
  }

  nextPage() {
    if (this.currentPage < this.totalPages) {
      this.goToPage(this.currentPage + 1);
    }
  }

  changeItemsPerPage(event: any) {
    const newSize = parseInt(event.target.value, 10);
    if (newSize && newSize > 0) {
      this.itemsPerPage = newSize;
      this.currentPage = 1;
      this.totalPages = Math.ceil(this.filteredRepos.length / this.itemsPerPage);
      if (this.totalPages === 0) this.totalPages = 1;
      this.updatePagination();
    }
  }

  getPages(): number[] {
    if (this.totalPages <= 1) return [1];

    const pages: number[] = [];
    const maxVisible = 5;
    let start = Math.max(1, this.currentPage - Math.floor(maxVisible / 2));
    let end = Math.min(this.totalPages, start + maxVisible - 1);

    if (end - start + 1 < maxVisible) {
      start = Math.max(1, end - maxVisible + 1);
    }

    for (let i = start; i <= end; i++) {
      pages.push(i);
    }
    return pages;
  }

  onSearch(event: any) {
    this.searchTerm = event.target.value;
    this.applyFilters();
  }

  filterByProvider(provider: string) {
    this.selectedProvider = provider;
    this.applyFilters();
  }

  filterByVisibility(visibility: string) {
    this.selectedVisibility = visibility;
    this.applyFilters();
  }

  toggleRepo(id: number) {
    if (this.selectedRepos.has(id)) {
      this.selectedRepos.delete(id);
    } else {
      this.selectedRepos.add(id);
    }
  }

  toggleAll(event: any) {
    if (event.target.checked) {
      this.paginatedRepos.forEach(r => this.selectedRepos.add(r.id));
    } else {
      this.paginatedRepos.forEach(r => this.selectedRepos.delete(r.id));
    }
  }

  isAllSelected(): boolean {
    return this.paginatedRepos.length > 0 &&
      this.paginatedRepos.every(r => this.selectedRepos.has(r.id));
  }

  isIndeterminate(): boolean {
    if (this.paginatedRepos.length === 0) return false;
    const count = this.paginatedRepos.filter(r => this.selectedRepos.has(r.id)).length;
    return count > 0 && count < this.paginatedRepos.length;
  }

  clearSelection() {
    this.selectedRepos.clear();
  }

  // ✅ Récupère le nom + provider des repos sélectionnés pour l'affichage dans le drawer
  private getSelectedRepoMeta(): { id: number; name: string; provider: string }[] {
    return Array.from(this.selectedRepos).map(id => {
      const repo = this.repositories.find(r => r.id === id);
      return { id, name: repo?.name ?? String(id), provider: repo?.provider ?? 'GITHUB' };
    });
  }

  analyzeSelected() {
    const ids = Array.from(this.selectedRepos);

    // ✅ Snapshot des métadonnées AVANT de vider la sélection
    this.analyzeRepoNames = ids.map(id => {
      const repo = this.repositories.find(r => r.id === id);
      return { id, name: repo?.name ?? String(id), provider: repo?.provider ?? 'GITHUB' };
    });

    this.analyzeLoading = true;
    this.analyzeResult = null;
    this.repoStats.clear();
    this.drawerVisible = true;

    this.http.post<AnalyzeResult>(
      'http://localhost:8082/api/git/import/analyze',
      { repositoryIds: ids },
      { headers: this.getHeaders() }
    ).subscribe({
      next: (result) => {
        this.analyzeResult = result;
        this.analyzeLoading = false;

        // ✅ Une fois l'analyse confirmée, on va chercher les stats détaillées
        this.loadStatsForAnalyzedRepos(ids);

        this.clearSelection();
        this.loadRepositories();
      },
      error: () => {
        this.analyzeResult = {
          repositoriesAnalyzed: 0,
          errors: ['Erreur réseau lors de l\'analyse']
        };
        this.analyzeLoading = false;
      }
    });
  }

  // ✅ Charge les stats (pushes/commits/PR/branches/contributeurs) pour chaque repo analysé sans erreur.
  private loadStatsForAnalyzedRepos(ids: number[]) {
    const successIds = ids.filter(id => {
      const repo = this.analyzeRepoNames.find(r => r.id === id);
      return repo && !this.getRepoError(repo.name);
    });

    if (successIds.length === 0) return;

    const requests = successIds.map(id =>
      this.gitService.getDashboardByRepo(id).pipe(
        catchError(() => of(null))
      )
    );

    forkJoin(requests).subscribe((results: any[]) => {
      successIds.forEach((id, index) => {
        const data = results[index];
        if (!data) {
          this.repoStats.set(id, {
            totalPushes: 0, normalPushes: 0, forcePushes: 0,
            totalCommits: 0, totalPullRequests: 0,
            branchesTotal: 0, branchesActive: 0, branchesStale: 0,
            mergeRate: 0,
            topContributors: [],
            loaded: false
          });
          return;
        }

        const pushes = data.pushes ?? { total: 0, forcePushCount: 0 };
        const branches = data.branches ?? { total: 0, active: 0, stale: 0 };
        const pullRequests = data.pullRequests ?? { total: 0, merged: 0, open: 0, closed: 0, mergeRate: 0 };

        this.repoStats.set(id, {
          totalPushes: pushes.total ?? 0,
          forcePushes: pushes.forcePushCount ?? 0,
          normalPushes: (pushes.total ?? 0) - (pushes.forcePushCount ?? 0),
          totalCommits: data.commits?.total ?? 0,
          totalPullRequests: pullRequests.total ?? 0,
          branchesTotal: branches.total ?? 0,
          branchesActive: branches.active ?? 0,
          branchesStale: branches.stale ?? 0,
          mergeRate: pullRequests.mergeRate ?? 0,
          topContributors: data.contributions?.topContributors ?? [],
          loaded: true
        });
      });
    });
  }

  // ✅ Récupère les stats d'un repo pour le template (avec valeurs par défaut à 0)
  getRepoStats(repoId: number): RepoAnalyzeStats {
    return this.repoStats.get(repoId) ?? {
      totalPushes: 0, normalPushes: 0, forcePushes: 0,
      totalCommits: 0, totalPullRequests: 0,
      branchesTotal: 0, branchesActive: 0, branchesStale: 0,
      mergeRate: 0,
      topContributors: [],
      loaded: false
    };
  }

  // ✅ §3.8 — Récupère le résultat de risque d'un repo (par nom, clé renvoyée par le backend)
  getRepoRisk(repoName: string): RepoRiskInfo | null {
    return this.analyzeResult?.riskResults?.[repoName] ?? null;
  }

  getRiskLevelLabel(level: string): string {
    switch (level) {
      case 'STABLE': return 'Stable';
      case 'MODERATE': return 'Risque modéré';
      case 'CRITICAL': return 'Risque critique';
      default: return level;
    }
  }

  getRiskLevelClass(level: string): string {
    switch (level) {
      case 'STABLE': return 'risk-stable';
      case 'MODERATE': return 'risk-moderate';
      case 'CRITICAL': return 'risk-critical';
      default: return '';
    }
  }

  formatTechnicalDebt(minutes: number): string {
    if (!minutes || minutes <= 0) return '0 min';
    const hours = Math.floor(minutes / 60);
    const mins = minutes % 60;
    if (hours === 0) return `${mins} min`;
    return mins > 0 ? `${hours}h ${mins}min` : `${hours}h`;
  }

  retryAnalyze() {
    // Re-sélectionne les repos qui avaient des erreurs et relance
    this.analyzeRepoNames.forEach(r => this.selectedRepos.add(r.id));
    this.analyzeSelected();
  }

  closeDrawer() {
    this.drawerVisible = false;
  }

  // ✅ Détermine si un repo a une erreur dans le résultat
  getRepoError(repoName: string): string | null {
    if (!this.analyzeResult) return null;
    const err = this.analyzeResult.errors.find(e =>
      e.toLowerCase().startsWith(repoName.toLowerCase() + ':')
    );
    return err ? err.substring(err.indexOf(':') + 1).trim() : null;
  }

  getSuccessCount(): number {
    if (!this.analyzeResult) return 0;
    return this.analyzeRepoNames.filter(r => !this.getRepoError(r.name)).length;
  }

  getProviderIcon(provider: string): string {
    return provider === 'GITHUB' ? 'bi-github' : 'bi-gitlab';
  }

  getProviderClass(provider: string): string {
    return provider === 'GITHUB' ? 'badge-github' : 'badge-gitlab';
  }

  formatDate(date: string): string {
    if (!date) return '—';
    return new Date(date).toLocaleDateString('fr-FR', {
      day: '2-digit', month: 'short', year: 'numeric'
    });
  }

  get analyzeDate(): string {
    return new Date().toLocaleDateString('fr-FR', {
      day: '2-digit', month: 'short', year: 'numeric',
      hour: '2-digit', minute: '2-digit'
    });
  }
}