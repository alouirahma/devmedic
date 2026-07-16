import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { forkJoin } from 'rxjs';
import { FormsModule } from '@angular/forms';

@Component({
  selector: 'app-commits',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './commit.html',
  styleUrl: './commit.scss'
})
export class Commit implements OnInit {

  Math = Math;

  commits: any[] = [];
  filteredCommits: any[] = [];
  paginatedCommits: any[] = [];
  loading = true;
  error = '';
  searchTerm = '';

  // Filtres
  selectedProvider = 'ALL';
  selectedRepository = 'ALL';
  selectedAuthor = 'ALL';
  dateRange: 'ALL' | 'TODAY' | 'WEEK' | 'MONTH' = 'ALL';

  // Stats
  totalCommits = 0;
  todayCommits = 0;
  thisWeekCommits = 0;
  thisMonthCommits = 0;

  // Listes pour les filtres
  repositories: string[] = [];
  authors: string[] = [];

  // Pagination
  currentPage = 1;
  itemsPerPage = 10;
  totalPages = 0;
  pageSizes = [5, 10, 25, 50, 100];

  constructor(private http: HttpClient) {}

  ngOnInit() {
    this.loadCommits();
  }

  loadCommits() {
    const token = localStorage.getItem('devmedic_token');
    const headers = { Authorization: `Bearer ${token}` };

    this.http.get<any[]>('http://localhost:8082/api/git/repositories', { headers })
      .subscribe({
        next: (repos) => {
          if (repos.length === 0) {
            this.commits = [];
            this.filteredCommits = [];
            this.calculateStats();
            this.updatePagination();
            this.loading = false;
            return;
          }

          const commitRequests = repos.map(repo => 
            this.http.get<any[]>(`http://localhost:8082/api/git/commits/repository/${repo.id}`, { headers })
          );

          forkJoin(commitRequests).subscribe({
            next: (results: any[]) => {
              this.commits = results.flatMap((commits, index) => {
                return commits.map((commit: any) => ({
                  ...commit,
                  repositoryName: repos[index].name,
                  repositoryId: repos[index].id,
                  provider: repos[index].provider
                }));
              });

              this.commits.sort((a, b) => {
                const dateA = new Date(a.committedAt || a.commitTime || a.date);
                const dateB = new Date(b.committedAt || b.commitTime || b.date);
                return dateB.getTime() - dateA.getTime();
              });

              this.repositories = [...new Set(this.commits.map(c => c.repositoryName))].sort();
              this.authors = [...new Set(this.commits.map(c => c.authorName || c.author))].sort();

              this.filteredCommits = [...this.commits];
              this.calculateStats();
              this.updatePagination();
              this.loading = false;
            },
            error: (err) => {
              console.error('Error loading commits:', err);
              this.error = 'Erreur lors du chargement des commits';
              this.loading = false;
            }
          });
        },
        error: (err) => {
          console.error('Error loading repositories:', err);
          this.error = 'Erreur lors du chargement des repositories';
          this.loading = false;
        }
      });
  }

  calculateStats() {
    this.totalCommits = this.commits.length;
    
    const now = new Date();
    const today = new Date(now.getFullYear(), now.getMonth(), now.getDate());
    const weekStart = new Date(today);
    weekStart.setDate(weekStart.getDate() - 7);
    const monthStart = new Date(today);
    monthStart.setDate(monthStart.getDate() - 30);

    this.todayCommits = this.commits.filter(c => {
      const date = new Date(c.committedAt || c.commitTime || c.date);
      return date >= today;
    }).length;

    this.thisWeekCommits = this.commits.filter(c => {
      const date = new Date(c.committedAt || c.commitTime || c.date);
      return date >= weekStart;
    }).length;

    this.thisMonthCommits = this.commits.filter(c => {
      const date = new Date(c.committedAt || c.commitTime || c.date);
      return date >= monthStart;
    }).length;
  }

  applyFilters() {
    let result = [...this.commits];
    
    // Filtre recherche
    if (this.searchTerm) {
      const term = this.searchTerm.toLowerCase();
      result = result.filter(c =>
        (c.message?.toLowerCase() || '').includes(term) ||
        (c.authorName?.toLowerCase() || '').includes(term) ||
        (c.sha?.toLowerCase() || '').includes(term) ||
        (c.repositoryName?.toLowerCase() || '').includes(term)
      );
    }
    
    // Filtre provider
    if (this.selectedProvider !== 'ALL') {
      result = result.filter(c => c.provider === this.selectedProvider);
    }

    // Filtre repository
    if (this.selectedRepository !== 'ALL') {
      result = result.filter(c => c.repositoryName === this.selectedRepository);
    }

    // Filtre auteur
    if (this.selectedAuthor !== 'ALL') {
      result = result.filter(c => (c.authorName || c.author) === this.selectedAuthor);
    }

    // Filtre date
    const now = new Date();
    const today = new Date(now.getFullYear(), now.getMonth(), now.getDate());
    
    if (this.dateRange === 'TODAY') {
      result = result.filter(c => {
        const date = new Date(c.committedAt || c.commitTime || c.date);
        return date >= today;
      });
    } else if (this.dateRange === 'WEEK') {
      const weekStart = new Date(today);
      weekStart.setDate(weekStart.getDate() - 7);
      result = result.filter(c => {
        const date = new Date(c.committedAt || c.commitTime || c.date);
        return date >= weekStart;
      });
    } else if (this.dateRange === 'MONTH') {
      const monthStart = new Date(today);
      monthStart.setDate(monthStart.getDate() - 30);
      result = result.filter(c => {
        const date = new Date(c.committedAt || c.commitTime || c.date);
        return date >= monthStart;
      });
    }
    
    this.filteredCommits = result;
    this.currentPage = 1;
    this.updatePagination();
  }

  onSearch(event: any) {
    this.searchTerm = event.target.value.toLowerCase();
    this.applyFilters();
  }

  filterByProvider(provider: string) {
    this.selectedProvider = provider;
    this.applyFilters();
  }

  filterByDateRange(range: 'ALL' | 'TODAY' | 'WEEK' | 'MONTH') {
    this.dateRange = range;
    this.applyFilters();
  }

  resetFilters() {
    this.searchTerm = '';
    this.selectedProvider = 'ALL';
    this.selectedRepository = 'ALL';
    this.selectedAuthor = 'ALL';
    this.dateRange = 'ALL';
    this.applyFilters();
    
    const searchInput = document.querySelector('.search-box input') as HTMLInputElement;
    if (searchInput) searchInput.value = '';
  }

  updatePagination() {
    this.totalPages = Math.ceil(this.filteredCommits.length / this.itemsPerPage);
    const startIndex = (this.currentPage - 1) * this.itemsPerPage;
    const endIndex = startIndex + this.itemsPerPage;
    this.paginatedCommits = this.filteredCommits.slice(startIndex, endIndex);
  }

  goToPage(page: number) {
    if (page >= 1 && page <= this.totalPages) {
      this.currentPage = page;
      this.updatePagination();
    }
  }

  previousPage() {
    this.goToPage(this.currentPage - 1);
  }

  nextPage() {
    this.goToPage(this.currentPage + 1);
  }

  changeItemsPerPage(event: any) {
    this.itemsPerPage = parseInt(event.target.value, 10);
    this.currentPage = 1;
    this.updatePagination();
  }

  getPages(): number[] {
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

  formatDate(date: string): string {
    if (!date) return '—';
    return new Date(date).toLocaleDateString('fr-FR', {
      day: '2-digit', month: 'short', year: 'numeric',
      hour: '2-digit', minute: '2-digit'
    });
  }

  shortSha(sha: string): string {
    return sha ? sha.substring(0, 7) : '—';
  }

  getProviderIcon(provider: string): string {
    return provider === 'GITHUB' ? 'bi-github' : 'bi-gitlab';
  }

  getProviderClass(provider: string): string {
    return provider === 'GITHUB' ? 'provider-github' : 'provider-gitlab';
  }

  isFilterActive(): boolean {
    return this.searchTerm !== '' ||
           this.selectedProvider !== 'ALL' ||
           this.selectedRepository !== 'ALL' ||
           this.selectedAuthor !== 'ALL' ||
           this.dateRange !== 'ALL';
  }
  // Ajouter cette méthode dans la classe Commits
getAvatarColor(name: string): string {
  const colors = [
    '#0C447C', '#2E7D32', '#C62828', '#E65100', 
    '#6A1B9A', '#00838F', '#4E342E', '#BF360C'
  ];
  let hash = 0;
  if (name) {
    for (let i = 0; i < name.length; i++) {
      hash = name.charCodeAt(i) + ((hash << 5) - hash);
    }
  }
  return colors[Math.abs(hash) % colors.length];
}

// Ajouter ces méthodes pour les filtres
filterByRepository(repo: string) {
  this.selectedRepository = repo;
  this.applyFilters();
}

filterByAuthor(author: string) {
  this.selectedAuthor = author;
  this.applyFilters();
}

clearSearch() {
  this.searchTerm = '';
  this.applyFilters();
  const searchInput = document.querySelector('.search-box input') as HTMLInputElement;
  if (searchInput) searchInput.value = '';
}
}