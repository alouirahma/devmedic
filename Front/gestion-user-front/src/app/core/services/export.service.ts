import { Injectable } from '@angular/core';
import jsPDF from 'jspdf';
import autoTable from 'jspdf-autotable';
import * as XLSX from 'xlsx';

/**
 * Service d'export des résultats d'analyse Git (PDF / Excel).
 * Cahier des charges §3.10 — Export et reporting.
 *
 * Usage :
 *   this.exportService.exportToPDF(repoLabel, dashboardData);
 *   this.exportService.exportToExcel(repoLabel, dashboardData);
 */
@Injectable({ providedIn: 'root' })
export class ExportService {

  // Palette DevMedic (cohérente avec le dashboard existant)
  private readonly colors = {
    primary: [12, 68, 124] as [number, number, number],   // #0C447C
    blue:    [55, 138, 221] as [number, number, number],  // #378ADD
    green:   [29, 158, 117] as [number, number, number],  // #1D9E75
    red:     [226, 75, 74] as [number, number, number],   // #E24B4A
    gray:    [107, 114, 128] as [number, number, number], // #6b7280
    lightBg: [248, 250, 252] as [number, number, number]  // #f8fafc
  };

  // ────────────────────────────────────────────────────────────────
  // PDF
  // ────────────────────────────────────────────────────────────────
  exportToPDF(repoLabel: string, data: any): void {
    const doc = new jsPDF({ unit: 'pt', format: 'a4' });
    const pageWidth = doc.internal.pageSize.getWidth();
    const marginX = 40;
    let y = 0;

    y = this.drawHeader(doc, repoLabel, pageWidth, marginX);
    y = this.drawKpiSummary(doc, data, marginX, y, pageWidth);
    y = this.drawCommitsSection(doc, data, marginX, y, pageWidth);
    y = this.drawPullRequestsSection(doc, data, marginX, y, pageWidth);
    y = this.drawBranchesAndPushesSection(doc, data, marginX, y, pageWidth);
    y = this.drawContributorsSection(doc, data, marginX, y, pageWidth);

    this.drawFooterOnAllPages(doc, repoLabel);

    const filename = `${this.slugify(repoLabel)}_rapport_${this.dateStamp()}.pdf`;
    doc.save(filename);
  }

  private drawHeader(doc: jsPDF, repoLabel: string, pageWidth: number, marginX: number): number {
    // Bandeau bleu d'en-tête
    doc.setFillColor(...this.colors.primary);
    doc.rect(0, 0, pageWidth, 80, 'F');

    doc.setTextColor(255, 255, 255);
    doc.setFont('helvetica', 'bold');
    doc.setFontSize(18);
    doc.text('DevMedic — Rapport d\'analyse Git', marginX, 32);

    doc.setFont('helvetica', 'normal');
    doc.setFontSize(11);
    doc.text(repoLabel, marginX, 52);

    doc.setFontSize(9);
    doc.setTextColor(220, 230, 240);
    const dateText = `Généré le ${new Date().toLocaleDateString('fr-FR', { day: '2-digit', month: 'long', year: 'numeric', hour: '2-digit', minute: '2-digit' })}`;
    doc.text(dateText, pageWidth - marginX, 52, { align: 'right' });

    return 100;
  }

  private drawSectionTitle(doc: jsPDF, title: string, marginX: number, y: number): number {
    doc.setTextColor(...this.colors.primary);
    doc.setFont('helvetica', 'bold');
    doc.setFontSize(13);
    doc.text(title, marginX, y);
    doc.setDrawColor(...this.colors.blue);
    doc.setLineWidth(1.2);
    doc.line(marginX, y + 4, marginX + 36, y + 4);
    return y + 22;
  }

  private drawKpiSummary(doc: jsPDF, data: any, marginX: number, y: number, pageWidth: number): number {
    y = this.drawSectionTitle(doc, 'Vue générale', marginX, y);

    const kpis = [
      { label: 'Commits', value: data.commits?.total ?? 0, color: this.colors.blue },
      { label: 'Branches', value: data.branches?.total ?? 0, color: this.colors.primary },
      { label: 'Pull Requests', value: data.pullRequests?.total ?? 0, color: this.colors.green },
      { label: 'Contributeurs', value: data.contributions?.total ?? 0, color: this.colors.primary },
      { label: 'Pushes', value: data.pushes?.total ?? 0, color: this.colors.blue },
      { label: 'Bus Factor', value: data.contributions?.busFactor ?? 0, color: this.colors.red },
    ];

    const cardW = (pageWidth - marginX * 2 - 5 * 8) / 6;
    const cardH = 52;

    kpis.forEach((kpi, i) => {
      const x = marginX + i * (cardW + 8);
      doc.setFillColor(...this.colors.lightBg);
      doc.roundedRect(x, y, cardW, cardH, 4, 4, 'F');

      doc.setTextColor(...kpi.color);
      doc.setFont('helvetica', 'bold');
      doc.setFontSize(16);
      doc.text(String(kpi.value), x + cardW / 2, y + 24, { align: 'center' });

      doc.setTextColor(...this.colors.gray);
      doc.setFont('helvetica', 'normal');
      doc.setFontSize(7.5);
      doc.text(kpi.label, x + cardW / 2, y + 38, { align: 'center' });
    });

    return y + cardH + 28;
  }

  private drawCommitsSection(doc: jsPDF, data: any, marginX: number, y: number, pageWidth: number): number {
    y = this.checkPageBreak(doc, y, 100);
    y = this.drawSectionTitle(doc, 'Commits', marginX, y);

    autoTable(doc, {
      startY: y,
      margin: { left: marginX, right: marginX },
      head: [['Indicateur', 'Valeur']],
      body: [
        ['Total commits', String(data.commits?.total ?? 0)],
        ['Lignes ajoutées', `+${data.commits?.linesAdded ?? 0}`],
        ['Lignes supprimées', `-${data.commits?.linesDeleted ?? 0}`],
      ],
      theme: 'striped',
      headStyles: { fillColor: this.colors.primary, fontSize: 9 },
      bodyStyles: { fontSize: 9 },
      styles: { cellPadding: 5 }
    });

    return (doc as any).lastAutoTable.finalY + 24;
  }

  private drawPullRequestsSection(doc: jsPDF, data: any, marginX: number, y: number, pageWidth: number): number {
    y = this.checkPageBreak(doc, y, 120);
    y = this.drawSectionTitle(doc, 'Pull Requests', marginX, y);

    const pr = data.pullRequests ?? {};
    autoTable(doc, {
      startY: y,
      margin: { left: marginX, right: marginX },
      head: [['Total', 'Merged', 'Open', 'Closed', 'Taux de fusion']],
      body: [[
        String(pr.total ?? 0),
        String(pr.merged ?? 0),
        String(pr.open ?? 0),
        String(pr.closed ?? 0),
        `${pr.mergeRate ?? 0}%`
      ]],
      theme: 'grid',
      headStyles: { fillColor: this.colors.primary, fontSize: 9 },
      bodyStyles: { fontSize: 9, halign: 'center' },
      styles: { cellPadding: 6, halign: 'center' }
    });

    return (doc as any).lastAutoTable.finalY + 24;
  }

  private drawBranchesAndPushesSection(doc: jsPDF, data: any, marginX: number, y: number, pageWidth: number): number {
    y = this.checkPageBreak(doc, y, 120);
    y = this.drawSectionTitle(doc, 'Branches & Pushes', marginX, y);

    const br = data.branches ?? {};
    const ps = data.pushes ?? {};
    autoTable(doc, {
      startY: y,
      margin: { left: marginX, right: marginX },
      head: [['Branches totales', 'Actives', 'Obsolètes', 'Pushes totaux', 'Force pushes']],
      body: [[
        String(br.total ?? 0),
        String(br.active ?? 0),
        String(br.stale ?? 0),
        String(ps.total ?? 0),
        String(ps.forcePushCount ?? 0)
      ]],
      theme: 'grid',
      headStyles: { fillColor: this.colors.primary, fontSize: 9 },
      bodyStyles: { fontSize: 9, halign: 'center' },
      styles: { cellPadding: 6, halign: 'center' }
    });

    return (doc as any).lastAutoTable.finalY + 24;
  }

  private drawContributorsSection(doc: jsPDF, data: any, marginX: number, y: number, pageWidth: number): number {
    const top = data.contributions?.topContributors ?? [];
    if (top.length === 0) return y;

    y = this.checkPageBreak(doc, y, 60 + top.length * 16);
    y = this.drawSectionTitle(doc, 'Top contributeurs', marginX, y);

    autoTable(doc, {
      startY: y,
      margin: { left: marginX, right: marginX },
      head: [['#', 'Nom', 'Commits', 'Score']],
      body: top.map((c: any, i: number) => [
        String(i + 1),
        c.name ?? '—',
        String(c.commits ?? 0),
        (c.score ?? 0).toFixed(1)
      ]),
      theme: 'striped',
      headStyles: { fillColor: this.colors.primary, fontSize: 9 },
      bodyStyles: { fontSize: 9 },
      styles: { cellPadding: 5 }
    });

    return (doc as any).lastAutoTable.finalY + 20;
  }

  private checkPageBreak(doc: jsPDF, y: number, neededSpace: number): number {
    const pageHeight = doc.internal.pageSize.getHeight();
    if (y + neededSpace > pageHeight - 50) {
      doc.addPage();
      return 40;
    }
    return y;
  }

  private drawFooterOnAllPages(doc: jsPDF, repoLabel: string): void {
    const pageCount = doc.getNumberOfPages();
    const pageWidth = doc.internal.pageSize.getWidth();
    const pageHeight = doc.internal.pageSize.getHeight();

    for (let i = 1; i <= pageCount; i++) {
      doc.setPage(i);
      doc.setDrawColor(230, 230, 230);
      doc.setLineWidth(0.5);
      doc.line(40, pageHeight - 36, pageWidth - 40, pageHeight - 36);

      doc.setFont('helvetica', 'normal');
      doc.setFontSize(8);
      doc.setTextColor(...this.colors.gray);
      doc.text('DevMedic — Plateforme d\'analyse intelligente GitHub & GitLab', 40, pageHeight - 22);
      doc.text(`Page ${i} / ${pageCount}`, pageWidth - 40, pageHeight - 22, { align: 'right' });
    }
  }

  // ────────────────────────────────────────────────────────────────
  // EXCEL
  // ────────────────────────────────────────────────────────────────
  exportToExcel(repoLabel: string, data: any): void {
    const wb = XLSX.utils.book_new();

    // Feuille 1 — Résumé
    const summaryRows = [
      ['Rapport DevMedic — Analyse Git'],
      ['Repository', repoLabel],
      ['Date export', new Date().toLocaleString('fr-FR')],
      [],
      ['Indicateur', 'Valeur'],
      ['Commits', data.commits?.total ?? 0],
      ['Branches', data.branches?.total ?? 0],
      ['Pull Requests', data.pullRequests?.total ?? 0],
      ['Contributeurs', data.contributions?.total ?? 0],
      ['Pushes', data.pushes?.total ?? 0],
      ['Bus Factor', data.contributions?.busFactor ?? 0],
    ];
    const summarySheet = XLSX.utils.aoa_to_sheet(summaryRows);
    summarySheet['!cols'] = [{ wch: 28 }, { wch: 24 }];
    XLSX.utils.book_append_sheet(wb, summarySheet, 'Résumé');

    // Feuille 2 — Commits
    const commitsSheet = XLSX.utils.aoa_to_sheet([
      ['Indicateur', 'Valeur'],
      ['Total commits', data.commits?.total ?? 0],
      ['Lignes ajoutées', data.commits?.linesAdded ?? 0],
      ['Lignes supprimées', data.commits?.linesDeleted ?? 0],
    ]);
    commitsSheet['!cols'] = [{ wch: 24 }, { wch: 16 }];
    XLSX.utils.book_append_sheet(wb, commitsSheet, 'Commits');

    // Feuille 3 — Pull Requests
    const pr = data.pullRequests ?? {};
    const prSheet = XLSX.utils.aoa_to_sheet([
      ['Total', 'Merged', 'Open', 'Closed', 'Taux de fusion (%)'],
      [pr.total ?? 0, pr.merged ?? 0, pr.open ?? 0, pr.closed ?? 0, pr.mergeRate ?? 0],
    ]);
    XLSX.utils.book_append_sheet(wb, prSheet, 'Pull Requests');

    // Feuille 4 — Branches & Pushes
    const br = data.branches ?? {};
    const ps = data.pushes ?? {};
    const bpSheet = XLSX.utils.aoa_to_sheet([
      ['Branches totales', 'Actives', 'Obsolètes', 'Pushes totaux', 'Push normaux', 'Force pushes'],
      [
        br.total ?? 0, br.active ?? 0, br.stale ?? 0,
        ps.total ?? 0, (ps.total ?? 0) - (ps.forcePushCount ?? 0), ps.forcePushCount ?? 0
      ],
    ]);
    XLSX.utils.book_append_sheet(wb, bpSheet, 'Branches & Pushes');

    // Feuille 5 — Contributeurs
    const top = data.contributions?.topContributors ?? [];
    if (top.length > 0) {
      const contribRows = [
        ['Rang', 'Nom', 'Commits', 'Score'],
        ...top.map((c: any, i: number) => [i + 1, c.name ?? '—', c.commits ?? 0, c.score ?? 0])
      ];
      const contribSheet = XLSX.utils.aoa_to_sheet(contribRows);
      contribSheet['!cols'] = [{ wch: 8 }, { wch: 24 }, { wch: 12 }, { wch: 10 }];
      XLSX.utils.book_append_sheet(wb, contribSheet, 'Contributeurs');
    }

    const filename = `${this.slugify(repoLabel)}_rapport_${this.dateStamp()}.xlsx`;
    XLSX.writeFile(wb, filename);
  }

  // ────────────────────────────────────────────────────────────────
  // Helpers
  // ────────────────────────────────────────────────────────────────
  private slugify(text: string): string {
    return text
      .toLowerCase()
      .replace(/[^a-z0-9]+/g, '_')
      .replace(/^_+|_+$/g, '');
  }

  private dateStamp(): string {
    const d = new Date();
    const pad = (n: number) => String(n).padStart(2, '0');
    return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
  }
}