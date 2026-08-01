"""
Génération d'un dataset simulé pour l'entraînement du modèle de prédiction de risque.

Contexte : DevMedic n'a que quelques repos réels exploitables (données insuffisantes
pour entraîner un modèle ML sérieux). Ce script génère un dataset simulé mais réaliste,
calibré sur les vraies plages de valeurs observées (SonarQube + RiskAnalysisService),
combinant :
  - Métriques de RISQUE (RiskAnalysisService) : bus_factor, hotspot_count, commit_count
  - Métriques de QUALITÉ (SonarQube)          : complexity, duplication_percent,
                                                  code_smells, reliability_issues

Cible à prédire : becomes_critical_30d (1 si le repo devient CRITICAL dans les 30j, 0 sinon)
"""

import numpy as np
import pandas as pd

np.random.seed(42)

N_SAMPLES = 1500  # nombre de "repos virtuels" simulés


def generate_dataset(n=N_SAMPLES):
    data = {}

    # ---- Métriques de RISQUE (RiskAnalysisService) ----
    data["bus_factor"] = np.random.choice([1, 2, 3, 4, 5, 6], size=n,
                                           p=[0.25, 0.30, 0.20, 0.15, 0.07, 0.03])
    data["commit_count"] = np.random.poisson(lam=25, size=n).clip(1, 300)
    data["hotspot_count"] = np.random.poisson(lam=2.5, size=n).clip(0, 20)

    # ---- Métriques de QUALITÉ (SonarQube) — calibrées sur tes vraies valeurs ----
    data["lines_of_code"] = np.random.lognormal(mean=8.5, sigma=0.8, size=n).clip(500, 50000)
    data["reliability_issues"] = np.random.gamma(shape=2, scale=30, size=n).clip(0, 400)
    data["security_issues"] = np.random.poisson(lam=3, size=n).clip(0, 30)
    data["code_smells"] = np.random.gamma(shape=3, scale=80, size=n).clip(10, 1000)
    data["duplication_percent"] = np.random.beta(a=1.5, b=8, size=n) * 30
    data["complexity"] = np.random.gamma(shape=4, scale=15, size=n).clip(1, 500)
    data["test_coverage"] = np.where(
        np.random.random(n) < 0.55,
        0.0,
        np.random.beta(a=2, b=5, size=n) * 100
    )
    data["technical_debt_minutes"] = (data["code_smells"] * np.random.uniform(3, 8, n)).clip(0, 20000)

    df = pd.DataFrame(data)

    # ---- Score de risque composite latent (sert à générer une cible cohérente) ----
    risk_score = (
        (6 - df["bus_factor"]) / 5 * 22
        + (df["hotspot_count"] / 20).clip(0, 1) * 18
        + (df["reliability_issues"] / 400).clip(0, 1) * 15
        + (df["security_issues"] / 30).clip(0, 1) * 10
        + (df["duplication_percent"] / 30).clip(0, 1) * 10
        + (df["complexity"] / df["lines_of_code"] * 1000).clip(0, 1) * 10
        + ((100 - df["test_coverage"]) / 100) * 10
        + (df["technical_debt_minutes"] / 20000).clip(0, 1) * 5
    )

    noise = np.random.normal(0, 8, n)
    risk_score_noisy = (risk_score + noise).clip(0, 100)

    prob = 1 / (1 + np.exp(-(risk_score_noisy - 58) / 9))
    df["becomes_critical_30d"] = (np.random.random(n) < prob).astype(int)
    df["stability_score"] = (100 - risk_score).clip(0, 100).round(1)

    cols = ["bus_factor", "commit_count", "hotspot_count", "lines_of_code",
            "reliability_issues", "security_issues", "code_smells",
            "duplication_percent", "complexity", "test_coverage",
            "technical_debt_minutes", "stability_score", "becomes_critical_30d"]
    df = df[cols].round(2)

    return df


if __name__ == "__main__":
    df = generate_dataset()
    df.to_csv("dataset_simule.csv", index=False)

    print(f"Dataset généré : {len(df)} lignes")
    print(f"\nRépartition de la cible (becomes_critical_30d) :")
    print(df["becomes_critical_30d"].value_counts(normalize=True).round(3))
    print(f"\nAperçu statistique :")
    print(df.describe().round(2))