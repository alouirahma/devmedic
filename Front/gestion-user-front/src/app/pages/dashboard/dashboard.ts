import { Component, OnInit, OnDestroy, ViewChild, ElementRef, HostListener } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { Router } from '@angular/router';
import { UserService } from '../../core/services/user.service';
import { User } from '../../core/models/user.model';
import { ActivityService } from '../../core/services/activity.service';
import { GitService } from '../../core/services/git.service';
import { ExportService } from '../../core/services/export.service';
import {
  NgApexchartsModule,
  ApexAxisChartSeries,
  ApexChart,
  ApexXAxis,
  ApexDataLabels,
  ApexStroke,
  ApexYAxis,
  ApexGrid,
  ApexTooltip,
  ApexLegend,
  ApexFill,
  ApexPlotOptions,
  ApexNonAxisChartSeries,
  ApexResponsive
} from 'ng-apexcharts';

export interface RiskData {
  stabilityScore: number;
  hotspotCount: number;
  technicalDebtMinutes: number;
  riskLevel: 'STABLE' | 'MODERATE' | 'CRITICAL';
  calculatedAt?: string;
  message?: string;
  bySeniority?: { [key: string]: number };
  topHotspots?: any[];
}

export interface QualityData {
  duplicationPercent: number;
  complexity: number;
  maintainabilityIndex: number;
  codeSmells: number;
  calculatedAt?: string;
  message?: string;
  hasData?: boolean;
}

export type CommitsChartOptions = {
  series: ApexAxisChartSeries;
  chart: ApexChart;
  xaxis: ApexXAxis;
  stroke: ApexStroke;
  dataLabels: ApexDataLabels;
  yaxis: ApexYAxis;
  grid: ApexGrid;
  tooltip: ApexTooltip;
  fill: ApexFill;
  colors: string[];
  plotOptions?: ApexPlotOptions;
};

export type DonutChartOptions = {
  series: ApexNonAxisChartSeries;
  chart: ApexChart;
  labels: string[];
  colors: string[];
  legend: ApexLegend;
  plotOptions: ApexPlotOptions;
  dataLabels: ApexDataLabels;
  responsive: ApexResponsive[];
};

export type BarChartOptions = {
  series: ApexAxisChartSeries;
  chart: ApexChart;
  xaxis: ApexXAxis;
  yaxis: ApexYAxis;
  colors: string[];
  plotOptions: ApexPlotOptions;
  dataLabels: ApexDataLabels;
  grid: ApexGrid;
  tooltip: ApexTooltip;
};

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [CommonModule, FormsModule, NgApexchartsModule],
  templateUrl: './dashboard.html',
  styleUrl: './dashboard.scss'
})
export class Dashboard implements OnInit {

  activeTab: 'users' | 'git' = 'users';
  activePage: 'overview' | 'commits' | 'branches' | 'pullrequests' | 'contributors' | 'pushes' | 'risks' | 'quality' = 'overview';

  users: User[] = [];
  loading = true;
  totalUsers = 0;
  adminCount = 0;
  developerCount = 0;
  analystCount = 0;
  roleStats: any[] = [];

  repositories: any[] = [];
  selectedRepoId: number | null = null;
  dashboardData: any = null;
  overview: any = null;
  gitLoading = false;
  overviewLoading = true;

  exportMenuOpen = false;
  exporting = false;
  exportSuccessMessage = '';

  riskData: RiskData | null = null;
  riskLoading = false;
  riskAnalyzing = false;

  qualityData: QualityData | null = null;
  qualityLoading = false;
  qualityAnalyzing = false;

  commitsMonthlyChart!: Partial<CommitsChartOptions>;
  linesChart!: Partial<CommitsChartOptions>;
  prDonutChart!: Partial<DonutChartOptions>;
  branchDonutChart!: Partial<DonutChartOptions>;
  contributorsChart!: Partial<BarChartOptions>;
  pushesChart!: Partial<BarChartOptions>;

  constructor(
    private userService: UserService,
    private activityService: ActivityService,
    private gitService: GitService,
    private exportService: ExportService,
    private http: HttpClient,
    private router: Router
  ) {}

  ngOnInit() {
    const token = localStorage.getItem('devmedic_token');
    if (!token) { this.router.navigate(['/login']); return; }
    this.activityService.addActivity('Dashboard visité', 'DASHBOARD_VISIT');
    localStorage.setItem('last_dashboard_visit', new Date().toISOString());
    this.loadUsers();
    this.loadGitOverview();
    this.loadRepositories();
  }

  switchTab(tab: 'users' | 'git') { this.activeTab = tab; }
  switchPage(page: string) { this.activePage = page as any; }

  loadUsers() {
    this.userService.getAll().subscribe({
      next: (users) => { this.users = users; this.totalUsers = users.length; this.computeStats(users); this.loading = false; },
      error: () => { this.loading = false; }
    });
  }

  computeStats(users: User[]) {
    this.adminCount     = users.filter(u => u.roles?.includes('ADMIN')).length;
    this.developerCount = users.filter(u => u.roles?.includes('DEVELOPER')).length;
    this.analystCount   = users.filter(u => u.roles?.includes('ANALYST')).length;
    this.roleStats = [
      { name: 'ADMIN',     count: this.adminCount,     color: '#378ADD' },
      { name: 'TEAM_LEAD', count: users.filter(u => u.roles?.includes('TEAM_LEAD')).length, color: '#BA7517' },
      { name: 'DEVELOPER', count: this.developerCount, color: '#639922' },
      { name: 'ANALYST',   count: this.analystCount,   color: '#7F77DD' },
      { name: 'VIEWER',    count: users.filter(u => u.roles?.includes('VIEWER')).length, color: '#888780' },
    ];
  }

  getPercent(count: number): number { return this.totalUsers === 0 ? 0 : Math.round((count / this.totalUsers) * 100); }
  getInitials(username: string): string { return username ? username.substring(0, 2).toUpperCase() : '?'; }
  getMainRole(roles: any[]): string {
    if (!roles || roles.length === 0) return 'VIEWER';
    const priority = ['ADMIN', 'TEAM_LEAD', 'DEVELOPER', 'ANALYST', 'VIEWER'];
    for (const p of priority) { if (roles.includes(p)) return p; }
    return roles[0];
  }
  getRoleBadgeClass(roles: any[]): string { return 'badge-' + this.getMainRole(roles).toLowerCase(); }

  loadGitOverview() {
    this.gitService.getDashboardOverview().subscribe({
      next: (data: any) => { this.overview = data; this.overviewLoading = false; },
      error: () => { this.overviewLoading = false; }
    });
  }

  loadRepositories() {
    this.gitService.getAllRepositories().subscribe({
      next: (repos) => {
        this.repositories = repos;
        if (repos.length > 0) { this.selectedRepoId = repos[0].id; this.loadRepoDashboard(repos[0].id); }
      }
    });
  }

  onRepoChange(event: any) {
    const id = +event.target.value;
    this.selectedRepoId = id;
    this.dashboardData = null;
    this.loadRepoDashboard(id);
  }

  loadRepoDashboard(repoId: number) {
    this.gitLoading = true;
    this.dashboardData = null;
    this.gitService.getDashboardByRepo(repoId).subscribe({
      next: (data: any) => { this.dashboardData = data; this.gitLoading = false; this.buildAllCharts(data); },
      error: () => { this.gitLoading = false; }
    });
    this.loadRiskData(repoId);
    this.loadQualityData(repoId);
  }

  // ─── RISK ──────────────────────────────────────────────────────────────

  loadRiskData(repoId: number) {
    this.riskLoading = true;
    this.riskData = null;
    const token = localStorage.getItem('devmedic_token');
    this.http.get<RiskData>(`http://localhost:8082/api/git/risk/repository/${repoId}`, { headers: { Authorization: `Bearer ${token}` } }).subscribe({
      next: (data) => { this.riskData = data; this.riskLoading = false; },
      error: (err) => {
        this.riskLoading = false;
        if (err.status === 404) {
          this.riskData = { 
            stabilityScore: 0, 
            hotspotCount: 0, 
            technicalDebtMinutes: 0, 
            riskLevel: 'STABLE', 
            message: "Aucune analyse de risque disponible. Lancez une analyse d'abord." 
          };
        }
      }
    });
  }

  analyzeRisk() {
    if (!this.selectedRepoId) return;
    this.riskAnalyzing = true;
    const token = localStorage.getItem('devmedic_token');
    this.http.post(
      `http://localhost:8082/api/git/risk/repository/${this.selectedRepoId}/analyze`,
      null,
      { headers: { Authorization: `Bearer ${token}` } }
    ).subscribe({
      next: () => {
        this.riskAnalyzing = false;
        this.loadRiskData(this.selectedRepoId!);
      },
      error: (err) => {
        this.riskAnalyzing = false;
        console.error('❌ Erreur analyse risque:', err);
      }
    });
  }

  // ─── QUALITY ──────────────────────────────────────────────────────────
// ✅ §3.7 — Charge la dernière analyse qualité SonarQube pour ce repo.
// Si aucune analyse n'existe encore, en déclenche une automatiquement.
loadQualityData(repoId: number) {
  this.qualityLoading = true;
  this.qualityData = null;
  const token = localStorage.getItem('devmedic_token');
  this.http.get<QualityData>(`http://localhost:8082/api/git/quality/repository/${repoId}`, { headers: { Authorization: `Bearer ${token}` } }).subscribe({
    next: (data) => { this.qualityData = data; this.qualityLoading = false; },
    error: (err) => {
      if (err.status === 404) {
        // Pas encore d'analyse pour ce repo → on la lance automatiquement
        this.qualityData = { duplicationPercent: 0, complexity: 0, maintainabilityIndex: 0, codeSmells: 0, message: "Analyse qualité en cours..." };
        this.triggerQualityAnalysis(repoId);
      } else {
        this.qualityLoading = false;
        console.error('❌ Error loading quality data:', err);
      }
    }
  });
}

// ✅ Déclenche un scan SonarQube pour ce repo (backend récupère automatiquement
// le token GitHub/GitLab via gestion-user, rien à transmettre ici)
private triggerQualityAnalysis(repoId: number) {
  const token = localStorage.getItem('devmedic_token');
  this.http.post(
    `http://localhost:8082/api/git/quality/repository/${repoId}/analyze`,
    null,
    { headers: { Authorization: `Bearer ${token}` } }
  ).subscribe({
    next: () => {
      // Scan terminé avec succès → on recharge les vraies données
      this.loadQualityData(repoId);
    },
    error: (err) => {
      this.qualityLoading = false;
      console.error('❌ Erreur lors du scan qualité automatique:', err);
      this.qualityData = {
        duplicationPercent: 0, complexity: 0, maintainabilityIndex: 0, codeSmells: 0,
        message: err.error?.error || "Échec de l'analyse qualité automatique."
      };
    }
  });
}

  /**
   * ✅ Lance une analyse qualité (scan SonarQube)
   * POST /api/git/quality/repository/{repoId}/analyze
   */
  analyzeQuality() {
    if (!this.selectedRepoId) return;
    this.qualityAnalyzing = true;
    const token = localStorage.getItem('devmedic_token');
    this.http.post(
      `http://localhost:8082/api/git/quality/repository/${this.selectedRepoId}/analyze`,
      null,
      { headers: { Authorization: `Bearer ${token}` } }
    ).subscribe({
      next: () => {
        this.qualityAnalyzing = false;
        this.loadQualityData(this.selectedRepoId!);
      },
      error: (err) => {
        this.qualityAnalyzing = false;
        console.error('❌ Erreur analyse qualité:', err);
        // Afficher une erreur plus explicite
        const errorMsg = err.error?.error || err.error?.message || 'Erreur inconnue';
        this.qualityData = {
          duplicationPercent: 0,
          complexity: 0,
          maintainabilityIndex: 0,
          codeSmells: 0,
          hasData: false,
          message: `Erreur: ${errorMsg}`
        };
      }
    });
  }

  // ─── REPOSITORY ──────────────────────────────────────────────────────

  analyzeRepo() {
    if (!this.selectedRepoId) return;
    this.gitLoading = true;
    this.gitService.analyzeRepositories([this.selectedRepoId]).subscribe({
      next: () => this.loadRepoDashboard(this.selectedRepoId!),
      error: () => { this.gitLoading = false; }
    });
  }

  get selectedRepoLabel(): string {
    const repo = this.repositories.find(r => r.id === this.selectedRepoId);
    return repo ? `${repo.owner}/${repo.name}` : 'repository';
  }

  // ─── EXPORT ──────────────────────────────────────────────────────────

  toggleExportMenu(event?: Event) { event?.stopPropagation(); if (!this.dashboardData) return; this.exportMenuOpen = !this.exportMenuOpen; }
  closeExportMenu() { this.exportMenuOpen = false; }

  @HostListener('document:click')
  onDocumentClick() { if (this.exportMenuOpen) this.exportMenuOpen = false; }

  exportPDF(event?: Event) { event?.stopPropagation(); if (!this.dashboardData) return; this.runExport(() => this.exportService.exportToPDF(this.selectedRepoLabel, this.dashboardData)); }
  exportExcel(event?: Event) { event?.stopPropagation(); if (!this.dashboardData) return; this.runExport(() => this.exportService.exportToExcel(this.selectedRepoLabel, this.dashboardData)); }

  private runExport(fn: () => void) {
    this.exportMenuOpen = false; this.exporting = true; this.exportSuccessMessage = '';
    setTimeout(() => {
      try { fn(); this.exportSuccessMessage = 'Export téléchargé avec succès'; }
      catch (e) { this.exportSuccessMessage = "Erreur lors de l'export"; }
      finally { this.exporting = false; setTimeout(() => this.exportSuccessMessage = '', 3000); }
    }, 250);
  }

  // ─── CHARTS ──────────────────────────────────────────────────────────

  private buildAllCharts(data: any) {
    this.buildCommitsMonthlyChart(data); 
    this.buildLinesChart(data);
    this.buildPRDonut(data); 
    this.buildBranchDonut(data);
    this.buildContributorsChart(data); 
    this.buildPushesChart(data);
  }

  private buildCommitsMonthlyChart(data: any) {
    const byMonth = data.commits.byMonth as Record<string, number>;
    const sorted = Object.entries(byMonth).sort(([a], [b]) => a.localeCompare(b)).slice(-12);
    this.commitsMonthlyChart = {
      series: [{ name: 'Commits', data: sorted.map(([, v]) => v) }],
      chart: { type: 'area', height: 300, toolbar: { show: false }, fontFamily: 'inherit', animations: { enabled: true, speed: 600 } },
      xaxis: { categories: sorted.map(([k]) => k), labels: { style: { fontSize: '11px' } } },
      yaxis: { labels: { style: { fontSize: '11px' } } },
      stroke: { curve: 'smooth', width: 2 },
      fill: { type: 'gradient', gradient: { shadeIntensity: 1, opacityFrom: 0.45, opacityTo: 0.05, stops: [0, 100] } },
      dataLabels: { enabled: false }, 
      grid: { borderColor: '#f0f0f0', strokeDashArray: 4 }, 
      colors: ['#378ADD'], 
      tooltip: { theme: 'light' }
    };
  }

  private buildLinesChart(data: any) {
    const commits = data.commits;
    this.linesChart = {
      series: [{ name: 'Lignes ajoutées', data: [commits.linesAdded] }, { name: 'Lignes supprimées', data: [commits.linesDeleted] }],
      chart: { type: 'bar', height: 200, toolbar: { show: false }, fontFamily: 'inherit' },
      xaxis: { categories: ['Total'] }, 
      yaxis: { labels: { style: { fontSize: '11px' } } },
      plotOptions: { bar: { horizontal: true, borderRadius: 6, dataLabels: { position: 'top' } } },
      dataLabels: { enabled: true, style: { fontSize: '11px' } }, 
      colors: ['#1D9E75', '#E24B4A'],
      grid: { borderColor: '#f0f0f0' }, 
      tooltip: { theme: 'light' }
    };
  }

  private buildPRDonut(data: any) {
    const pr = data.pullRequests;
    this.prDonutChart = {
      series: [pr.merged, pr.open, pr.closed],
      chart: { type: 'donut', height: 280, fontFamily: 'inherit', animations: { enabled: true, speed: 600 } },
      labels: ['Merged', 'Open', 'Closed'], 
      colors: ['#1D9E75', '#378ADD', '#E24B4A'],
      plotOptions: { pie: { donut: { size: '70%', labels: { show: true, total: { show: true, label: 'Total', formatter: () => pr.total.toString() } } } } },
      dataLabels: { enabled: false }, 
      legend: { position: 'bottom', fontSize: '12px' },
      responsive: [{ breakpoint: 480, options: { chart: { height: 220 } } }]
    };
  }

  private buildBranchDonut(data: any) {
    const br = data.branches;
    this.branchDonutChart = {
      series: [br.active, br.stale],
      chart: { type: 'donut', height: 280, fontFamily: 'inherit', animations: { enabled: true, speed: 600 } },
      labels: ['Actives', 'Obsolètes'], 
      colors: ['#1D9E75', '#E24B4A'],
      plotOptions: { pie: { donut: { size: '70%', labels: { show: true, total: { show: true, label: 'Total', formatter: () => br.total.toString() } } } } },
      dataLabels: { enabled: false }, 
      legend: { position: 'bottom', fontSize: '12px' },
      responsive: [{ breakpoint: 480, options: { chart: { height: 220 } } }]
    };
  }

  private buildContributorsChart(data: any) {
    const authors = data.commits.topAuthors as any[];
    this.contributorsChart = {
      series: [{ name: 'Commits', data: authors.map((a: any) => a.commits) }],
      chart: { type: 'bar', height: 280, toolbar: { show: false }, fontFamily: 'inherit', animations: { enabled: true, speed: 600 } },
      xaxis: { categories: authors.map((a: any) => a.name), labels: { style: { fontSize: '11px' } } },
      yaxis: { labels: { style: { fontSize: '11px' } } },
      plotOptions: { bar: { borderRadius: 6, columnWidth: '50%' } }, 
      dataLabels: { enabled: false },
      colors: ['#0C447C'], 
      grid: { borderColor: '#f0f0f0', strokeDashArray: 4 }, 
      tooltip: { theme: 'light' }
    };
  }

  private buildPushesChart(data: any) {
    const pushes = data.pushes;
    this.pushesChart = {
      series: [{ name: 'Push normaux', data: [pushes.total - pushes.forcePushCount] }, { name: 'Force push', data: [pushes.forcePushCount] }],
      chart: { type: 'bar', height: 200, stacked: true, toolbar: { show: false }, fontFamily: 'inherit' },
      xaxis: { categories: ['Pushes'] }, 
      yaxis: { labels: { style: { fontSize: '11px' } } },
      plotOptions: { bar: { horizontal: true, borderRadius: 6 } },
      dataLabels: { enabled: true, style: { fontSize: '12px' } }, 
      colors: ['#1D9E75', '#E24B4A'],
      grid: { borderColor: '#f0f0f0' }, 
      tooltip: { theme: 'light' }
    };
  }

  // ─── HELPERS ──────────────────────────────────────────────────────────

  // ── §3.8 Helpers Risque ───────────────────────────────────────────
  getRiskLevelLabel(level: string): string {
    switch (level) {
      case 'STABLE': return '✅ Stable';
      case 'MODERATE': return '⚠️ Risque modéré';
      case 'CRITICAL': return '🔴 Risque critique';
      default: return level ?? '—';
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

  formatDate(dateString?: string): string {
    if (!dateString) return '';
    return new Date(dateString).toLocaleDateString('fr-FR', { day: '2-digit', month: '2-digit', year: 'numeric', hour: '2-digit', minute: '2-digit' });
  }

  // ── §3.7 Helpers Qualité ──────────────────────────────────────────
  getQualityLabel(score: number): string {
    if (score >= 80) return 'Excellente';
    if (score >= 60) return 'Bonne';
    if (score >= 40) return 'Moyenne';
    return 'Faible';
  }

  getQualityClass(score: number): string {
    if (score >= 80) return 'quality-excellent';
    if (score >= 60) return 'quality-good';
    if (score >= 40) return 'quality-average';
    return 'quality-poor';
  }

  getComplexityLabel(complexity: number): string {
    if (complexity <= 10) return 'Faible (bon)';
    if (complexity <= 20) return 'Moderee';
    if (complexity <= 50) return 'Elevee';
    return 'Tres elevee';
  }

  getComplexityClass(complexity: number): string {
    if (complexity <= 10) return 'quality-excellent';
    if (complexity <= 20) return 'quality-good';
    if (complexity <= 50) return 'quality-average';
    return 'quality-poor';
  }

  getDuplicationClass(percent: number): string {
    if (percent <= 10) return 'quality-excellent';
    if (percent <= 20) return 'quality-average';
    return 'quality-poor';
  }

  getCodeSmellsClass(count: number): string {
    if (count === 0) return 'quality-excellent';
    if (count <= 20) return 'quality-good';
    if (count <= 50) return 'quality-average';
    return 'quality-poor';
  }

  // ── Helpers généraux ─────────────────────────────────────────────
  getBusFactorColor(bf: number): string {
    if (bf <= 1) return 'danger';
    if (bf <= 2) return 'warning';
    return 'success';
  }

  getMergeRateColor(rate: number): string {
    if (rate >= 70) return 'success';
    if (rate >= 40) return 'warning';
    return 'danger';
  }

  formatNumber(n: number): string {
    if (!n) return '0';
    if (n >= 1000) return (n / 1000).toFixed(1) + 'k';
    return n.toString();
  }

  get pages() {
    return [
      { key: 'overview',     label: 'Vue générale',  icon: 'bi-grid-1x2-fill' },
      { key: 'commits',      label: 'Commits',        icon: 'bi-git' },
      { key: 'branches',     label: 'Branches',       icon: 'bi-diagram-2-fill' },
      { key: 'pullrequests', label: 'Pull Requests',  icon: 'bi-arrow-left-right' },
      { key: 'contributors', label: 'Contributeurs',  icon: 'bi-people-fill' },
      { key: 'pushes',       label: 'Pushes',         icon: 'bi-cloud-upload-fill' },
      { key: 'risks',        label: 'Risques',        icon: 'bi-shield-fill-exclamation' },
      { key: 'quality',      label: 'Qualité',        icon: 'bi-stars' },
    ];
  }
}