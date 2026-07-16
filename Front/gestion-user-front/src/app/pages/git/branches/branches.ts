import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { RouterLink } from '@angular/router';

@Component({
  selector: 'app-branches',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './branches.html',
  styleUrl: './branches.scss'
})
export class Branches implements OnInit {

  Math = Math;

  branches: any[] = [];
  filteredBranches: any[] = [];
  paginatedBranches: any[] = [];
  loading = true;
  error = '';
  searchTerm = '';

  // Filtres
  selectedBranchType = 'ALL';

  // Stats
  totalBranches = 0;
  defaultBranches = 0;
  otherBranches = 0;

  // Pagination
  currentPage = 1;
  itemsPerPage = 10;
  totalPages = 0;
  pageSizes = [5, 10, 25, 50, 100];

  constructor(private http: HttpClient) {}

  ngOnInit() {
    this.loadBranches();
  }

  loadBranches() {
    const token = localStorage.getItem('devmedic_token');
    const headers = { Authorization: `Bearer ${token}` };

    this.http.get<any[]>('http://localhost:8082/api/git/branches', { headers })
      .subscribe({
        next: (data) => {
          console.log('Branches data:', data);
          this.branches = data;
          this.filteredBranches = [...data];
          this.calculateStats();
          this.updatePagination();
          this.loading = false;
        },
        error: (err) => {
          console.error('Error:', err);
          this.error = 'Erreur lors du chargement des branches';
          this.loading = false;
        }
      });
  }

  calculateStats() {
    this.totalBranches = this.branches.length;
    this.defaultBranches = this.branches.filter(b => b.default).length;
    this.otherBranches = this.totalBranches - this.defaultBranches;
  }

  applyFilters() {
    let result = [...this.branches];
    
    // Filtre recherche (UNIQUEMENT par nom de branche)
    if (this.searchTerm) {
      result = result.filter(b =>
        (b.name?.toLowerCase() || '').includes(this.searchTerm)
      );
    }
    
    // Filtre type de branche
    if (this.selectedBranchType === 'DEFAULT') {
      result = result.filter(b => b.default === true);
    } else if (this.selectedBranchType === 'OTHER') {
      result = result.filter(b => b.default !== true);
    }
    
    this.filteredBranches = result;
    this.currentPage = 1;
    this.updatePagination();
  }

  onSearch(event: any) {
    this.searchTerm = event.target.value.toLowerCase();
    this.applyFilters();
  }

  filterByBranchType(type: string) {
    this.selectedBranchType = type;
    this.applyFilters();
  }

  resetFilters() {
    this.searchTerm = '';
    this.selectedBranchType = 'ALL';
    this.applyFilters();
    
    const searchInput = document.querySelector('.search-box input') as HTMLInputElement;
    if (searchInput) searchInput.value = '';
  }

  updatePagination() {
    this.totalPages = Math.ceil(this.filteredBranches.length / this.itemsPerPage);
    const startIndex = (this.currentPage - 1) * this.itemsPerPage;
    const endIndex = startIndex + this.itemsPerPage;
    this.paginatedBranches = this.filteredBranches.slice(startIndex, endIndex);
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
      day: '2-digit', month: 'short', year: 'numeric'
    });
  }

  shortSha(sha: string): string {
    return sha ? sha.substring(0, 7) : '—';
  }
}