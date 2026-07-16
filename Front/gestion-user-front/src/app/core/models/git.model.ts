export interface GitRepository {
  id: number;
  remoteId: string;
  name: string;
  owner: string;
  defaultBranchName: string;
  provider: 'GITHUB' | 'GITLAB';
  private: boolean;
  lastAnalyzedAt: string;
  userId: number;
  branches?: Branch[];
  tags?: Tag[];
}

export interface Branch {
  id: number;
  name: string;
  default: boolean;
  lastCommitSha: string;
  lastCommitTime: string;
}

export interface Tag {
  id: number;
  name: string;
  commitSha: string;
  taggedAt: string;
}