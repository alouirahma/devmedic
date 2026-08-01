"""
Microservice FastAPI exposant le modèle de prédiction de risque.

Endpoint principal : POST /predict
Reçoit les métriques actuelles d'un repository, retourne une probabilité
qu'il devienne CRITICAL dans les 30 prochains jours, avec les facteurs
explicatifs les plus influents.
"""

from fastapi import FastAPI
from pydantic import BaseModel
import joblib
import pandas as pd

app = FastAPI(title="DevMedic - Service de prédiction de risque")

# Chargement du modèle et des features attendues au démarrage du service
model = joblib.load("risk_model.pkl")
FEATURES = joblib.load("model_features.pkl")


class RepoMetrics(BaseModel):
    bus_factor: int
    commit_count: int
    hotspot_count: int
    lines_of_code: float
    reliability_issues: float
    security_issues: float
    code_smells: float
    duplication_percent: float
    complexity: float
    test_coverage: float
    technical_debt_minutes: float
    stability_score: float


class PredictionResponse(BaseModel):
    probability_critical: float
    risk_level: str
    top_factors: list[dict]


@app.get("/health")
def health():
    """Endpoint de santé, similaire à /actuator/health côté Spring Boot."""
    return {"status": "UP"}


@app.post("/predict", response_model=PredictionResponse)
def predict(metrics: RepoMetrics):
    # Construire le DataFrame dans le même ordre que lors de l'entraînement
    input_df = pd.DataFrame([metrics.dict()])[FEATURES]

    proba = model.predict_proba(input_df)[0][1]  # probabilité de la classe "CRITICAL"

    if proba >= 0.6:
        risk_level = "HIGH"
    elif proba >= 0.3:
        risk_level = "MODERATE"
    else:
        risk_level = "LOW"

    # Facteurs les plus influents pour CETTE prédiction précise (approche simplifiée :
    # on combine l'importance globale du modèle avec la valeur observée du repo)
    importances = model.feature_importances_
    factors = sorted(
        zip(FEATURES, importances, input_df.iloc[0].values),
        key=lambda x: x[1],
        reverse=True
    )[:3]

    top_factors = [
        {"feature": f, "importance": round(float(imp), 3), "value": round(float(val), 2)}
        for f, imp, val in factors
    ]

    return PredictionResponse(
        probability_critical=round(float(proba), 3),
        risk_level=risk_level,
        top_factors=top_factors,
    )