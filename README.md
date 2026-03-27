# OFBiz AI Demand Forecasting

Customized Apache OFBiz demand forecasting project by **Anish Kumar**.

This repository combines a customized OFBiz application with a Python forecasting microservice so anyone can clone the repo, run it locally, and see demand forecasting screens, dashboard views, and seeded demo forecast data.

## Author

**Anish Kumar**  
Creator and maintainer of this project.

## What This Project Is

This is a customized OFBiz-based product, not a clean-room rewrite of Apache OFBiz.

It contains:

- `ofbiz-framework/`
  Customized Apache OFBiz application and UI
- `ai-service/`
  FastAPI demand forecasting service
- `docs/production.md`
  Production notes

The application flow is:

1. OFBiz exports order, product, facility, and inventory data to CSV.
2. The AI service loads those exports and generates demand forecasts.
3. OFBiz stores the forecast output in `DemandForecast`.
4. Users view the results in:
   - `Demand Forecasts`
   - `Forecast Dashboard`

## Features

- OFBiz data export for forecasting
- FastAPI forecasting API
- OFBiz forecast persistence
- Dashboard and forecast result views
- Seeded six-month demo demand for local showcasing
- Inventory-aware stock gap analysis

## Prerequisites

Install these on your machine:

- Java 17
- Python 3.13+ or another compatible Python 3.x version
- Git

Optional:

- Docker Desktop
- Docker Compose

Helpful but not required globally:

- Gradle
- Groovy

The Gradle wrapper is already included, so a global Gradle install is not required.

## Quick Start From A Fresh Clone

### 1. Clone the repository

```powershell
git clone https://github.com/Anish17-Shanu/ofbiz-ai-demand.git
cd ofbiz-ai-demand
```

### 2. Set up the AI service

```powershell
cd ai-service
python -m venv .venv
.\.venv\Scripts\python.exe -m pip install --upgrade pip
.\.venv\Scripts\python.exe -m pip install -r requirements.txt
cd ..
```

### 3. Start the AI service

Open terminal 1:

```powershell
cd ai-service
.\.venv\Scripts\python.exe -m uvicorn main:app --host 127.0.0.1 --port 8000
```

Health check:

```powershell
Invoke-RestMethod http://127.0.0.1:8000/health
```

### 4. Start OFBiz

Open terminal 2:

```powershell
cd ofbiz-framework
$env:GRADLE_USER_HOME=(Join-Path (Split-Path (Get-Location) -Parent) '.gradle-home')
.\gradlew.bat loadAll
.\gradlew.bat ofbizBackground
```

After the first run, usually this is enough:

```powershell
cd ofbiz-framework
$env:GRADLE_USER_HOME=(Join-Path (Split-Path (Get-Location) -Parent) '.gradle-home')
.\gradlew.bat ofbizBackground
```

### 5. Open the product

Open:

- `https://localhost:8443/catalog/control/main`

Login:

- username: `admin`
- password: `ofbiz`

### 6. Generate demo forecast data

Open:

- `https://localhost:8443/catalog/control/demandForecasts`

Then:

1. Click `Export`
2. Click `Queue Run`
3. Refresh the page
4. Open `Forecast Dashboard`

The export flow seeds six months of deterministic demo demand for selected real OFBiz products, so the project can be showcased immediately on a fresh machine.

## Main URLs

- Main catalog app:
  `https://localhost:8443/catalog/control/main`
- Demand Forecasts:
  `https://localhost:8443/catalog/control/demandForecasts`
- Forecast Dashboard:
  `https://localhost:8443/catalog/control/demandForecastDashboard`

## Stop OFBiz

```powershell
cd ofbiz-framework
$env:GRADLE_USER_HOME=(Join-Path (Split-Path (Get-Location) -Parent) '.gradle-home')
.\gradlew.bat terminateOfbiz
```

## Reload The AI Model Manually

If you refresh export CSVs and want to force the AI service to reload:

```powershell
Invoke-RestMethod -Method Post -Uri "http://127.0.0.1:8000/reload"
```

## Docker Option

If Docker Desktop is installed and running:

```powershell
docker compose up --build
```

Then open:

- `https://localhost:8443/catalog/control/main`

Docker is optional. The direct Java + Python workflow above is the primary local path.

## Local Setup Notes

- The first OFBiz startup is slower because Gradle and OFBiz initialize a lot of runtime state.
- The AI service should be running before you queue forecasts.
- If the UI looks stale after an update, do one hard refresh in the browser.
- The project now includes seeded demo demand data to make local showcasing easier.

## Environment and Secrets

The repo includes `.env.example` for AI-service-related environment variables.

Important:

- do not commit real API keys
- do not commit generated local OFBiz security keys
- generate machine-specific security keys locally if needed

## Production

Production notes are here:

- `docs/production.md`

## Credit

Project creator and author: **Anish Kumar**
