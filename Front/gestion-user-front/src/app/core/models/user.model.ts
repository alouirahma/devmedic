export interface User {
  id: Long;
  username: string;
  email: string;
  roles: Role[];
  createdAt?: string;
  lastLoginAt?: string;
  userHR?: UserHR;
}

export interface UserHR {
  id?: number;
  seniorityLevel: SeniorityLevel;
  yearsOfExperience: number;
  primarySkill: string;
}

export interface Profile {
  id: string;
  username: string;
  email: string;
  roles: string[];
  authenticated: boolean;
}

export type Role = 'ADMIN' | 'TEAM_LEAD' | 'DEVELOPER' | 'ANALYST' | 'VIEWER';
export type SeniorityLevel = 'JUNIOR' | 'MID' | 'SENIOR' | 'LEAD' | 'STAFF';
export type ProviderType = 'GITHUB' | 'GITLAB';
export type Long = number;