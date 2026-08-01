"""
Entraînement du modèle de prédiction de risque (DevMedic — IA prédictive).

Algorithme choisi : Random Forest Classifier
  - Interprétable (feature_importances_) — permet d'expliquer les prédictions
    ("pourquoi ce repo est à risque") dans le dashboard et à l'oral
  - Robuste face à des features de nature différente (comptages, pourcentages,
    ratios) sans nécessiter de normalisation stricte
  - Bon compromis performance/simplicité pour un dataset de taille modeste
"""

import pandas as pd
import joblib
from sklearn.model_selection import train_test_split
from sklearn.ensemble import RandomForestClassifier
from sklearn.linear_model import LogisticRegression
from sklearn.preprocessing import StandardScaler
from sklearn.metrics import (
    accuracy_score, precision_score, recall_score, f1_score,
    roc_auc_score, confusion_matrix, classification_report
)

FEATURES = [
    "bus_factor", "commit_count", "hotspot_count", "lines_of_code",
    "reliability_issues", "security_issues", "code_smells",
    "duplication_percent", "complexity", "test_coverage",
    "technical_debt_minutes", "stability_score",
]
TARGET = "becomes_critical_30d"


def main():
    df = pd.read_csv("dataset_simule.csv")

    X = df[FEATURES]
    y = df[TARGET]

    X_train, X_test, y_train, y_test = train_test_split(
        X, y, test_size=0.2, random_state=42, stratify=y
    )
    print(f"Train : {len(X_train)} lignes | Test : {len(X_test)} lignes")
    print(f"Proportion 'critical' — train: {y_train.mean():.2%} | test: {y_test.mean():.2%}\n")

    # ---- Modèle principal : Random Forest ----
    rf = RandomForestClassifier(
        n_estimators=200,
        max_depth=8,
        min_samples_leaf=5,
        class_weight="balanced",
        random_state=42,
    )
    rf.fit(X_train, y_train)
    y_pred_rf = rf.predict(X_test)
    y_proba_rf = rf.predict_proba(X_test)[:, 1]

    print("=" * 60)
    print("RANDOM FOREST — Résultats sur le jeu de test")
    print("=" * 60)
    print(f"Accuracy  : {accuracy_score(y_test, y_pred_rf):.3f}")
    print(f"Precision : {precision_score(y_test, y_pred_rf):.3f}")
    print(f"Recall    : {recall_score(y_test, y_pred_rf):.3f}")
    print(f"F1-score  : {f1_score(y_test, y_pred_rf):.3f}")
    print(f"ROC-AUC   : {roc_auc_score(y_test, y_proba_rf):.3f}")
    print("\nMatrice de confusion :")
    print(confusion_matrix(y_test, y_pred_rf))
    print("\nRapport détaillé :")
    print(classification_report(y_test, y_pred_rf, target_names=["STABLE", "CRITICAL"]))

    importances = pd.Series(rf.feature_importances_, index=FEATURES).sort_values(ascending=False)
    print("Importance des variables (Random Forest) :")
    print(importances.round(3))

    # ---- Comparaison : Régression logistique ----
    scaler = StandardScaler()
    X_train_scaled = scaler.fit_transform(X_train)
    X_test_scaled = scaler.transform(X_test)

    logreg = LogisticRegression(max_iter=2000, class_weight="balanced", random_state=42)
    logreg.fit(X_train_scaled, y_train)
    y_pred_lr = logreg.predict(X_test_scaled)
    y_proba_lr = logreg.predict_proba(X_test_scaled)[:, 1]

    print("\n" + "=" * 60)
    print("RÉGRESSION LOGISTIQUE — Résultats (comparaison)")
    print("=" * 60)
    print(f"Accuracy  : {accuracy_score(y_test, y_pred_lr):.3f}")
    print(f"F1-score  : {f1_score(y_test, y_pred_lr):.3f}")
    print(f"ROC-AUC   : {roc_auc_score(y_test, y_proba_lr):.3f}")

    # ---- Sauvegarde du modèle retenu ----
    joblib.dump(rf, "risk_model.pkl")
    joblib.dump(FEATURES, "model_features.pkl")
    print("\n✅ Modèle Random Forest sauvegardé : risk_model.pkl")


if __name__ == "__main__":
    main()