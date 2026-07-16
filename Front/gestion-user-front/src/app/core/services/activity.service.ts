// src/app/core/services/activity.service.ts
import { Injectable } from '@angular/core';

@Injectable({ providedIn: 'root' })
export class ActivityService {
  private readonly STORAGE_KEY = 'user_activities';

  constructor() {
    this.initActivities();
  }

  private initActivities() {
    if (!localStorage.getItem(this.STORAGE_KEY)) {
      localStorage.setItem(this.STORAGE_KEY, JSON.stringify([]));
    }
  }

  addActivity(action: string, type: string) {
    const activities = this.getActivities();
    activities.unshift({
      action: action,
      type: type,
      timestamp: new Date().toISOString()
    });
    
    // Garder seulement les 50 dernières activités
    const limited = activities.slice(0, 50);
    localStorage.setItem(this.STORAGE_KEY, JSON.stringify(limited));
    
    console.log(`Activité ajoutée: ${action} - ${new Date().toISOString()}`);
  }

  getActivities(): any[] {
    const stored = localStorage.getItem(this.STORAGE_KEY);
    return stored ? JSON.parse(stored) : [];
  }

  getRecentActivities(limit: number = 10): any[] {
    return this.getActivities().slice(0, limit);
  }

  clearActivities() {
    localStorage.removeItem(this.STORAGE_KEY);
    this.initActivities();
  }
}