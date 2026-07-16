import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { RouterLink } from '@angular/router';

@Component({
  selector: 'app-tags',
  standalone: true,
  imports: [CommonModule, RouterLink],
  templateUrl: './tags.html',
  styleUrl: './tags.scss'
})
export class Tags implements OnInit {

  tags: any[] = [];
  filteredTags: any[] = [];
  loading = true;
  error = '';
  searchTerm = '';
  totalTags = 0;

  constructor(private http: HttpClient) {}

  ngOnInit() {
    this.loadTags();
  }

  loadTags() {
    const token = localStorage.getItem('devmedic_token');
    const headers = { Authorization: `Bearer ${token}` };

    this.http.get<any[]>('http://localhost:8082/api/git/tags', { headers })
      .subscribe({
        next: (data) => {
          this.tags = data;
          this.filteredTags = data;
          this.totalTags = data.length;
          this.loading = false;
        },
        error: () => {
          this.error = 'Erreur lors du chargement des tags';
          this.loading = false;
        }
      });
  }

  onSearch(event: any) {
    this.searchTerm = event.target.value.toLowerCase();
    this.filteredTags = this.tags.filter(t =>
      t.name.toLowerCase().includes(this.searchTerm) ||
      t.repository?.name?.toLowerCase().includes(this.searchTerm)
    );
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