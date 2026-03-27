<#assign dashboardData = dashboard!{} />
<#assign metrics = dashboardData.metrics!{} />
<#assign topGaps = dashboardData.topGaps![] />
<#assign topDemand = dashboardData.topDemand![] />
<#assign methodMix = dashboardData.methodMix![] />
<#assign riskLanes = dashboardData.riskLanes!{} />
<#assign evaluation = dashboardData.evaluation!{} />
<#assign accuracyScore = dashboardData.accuracyScore! />
<#assign lastRunDate = dashboardData.lastRunDate!"" />
<#assign demandChart = dashboardData.demandChart![] />
<#assign gapChart = dashboardData.gapChart![] />
<#assign timelineChart = dashboardData.timelineChart![] />
<#assign latestRows = dashboardData.latestRows![] />
<#assign noHistoryRows = dashboardData.noHistoryRows![] />
<#assign evaluationStatus = "available" />
<#if evaluation.error?? && evaluation.error?has_content>
  <#assign evaluationStatus = evaluation.error />
</#if>
<#assign formattedAccuracyScore = "" />
<#if accuracyScore?is_number>
  <#assign formattedAccuracyScore = accuracyScore?string("0.0") />
</#if>
<link rel="stylesheet" href="${request.getContextPath()}/dashboard-demand.css" />

<div class="df-dashboard">
  <div class="df-hero">
    <div>
      <div class="df-eyebrow">Demand planning cockpit</div>
      <h1>Forecast Dashboard</h1>
      <p>Six months of deterministic demo demand are blended into the export bundle so this workspace can show realistic demand movement, replenishment pressure, and stronger forecasting behavior without waiting on live ERP order volume.</p>
    </div>
    <div class="df-hero-meta">
      <div class="df-hero-chip">
        <span>Latest run</span>
        <strong><#if lastRunDate?has_content>${lastRunDate?string("yyyy-MM-dd HH:mm")}<#else>Not run yet</#if></strong>
      </div>
      <div class="df-hero-chip">
        <span>Forecast confidence</span>
        <strong><#if formattedAccuracyScore?has_content>${formattedAccuracyScore}%<#else>Not enough data</#if></strong>
      </div>
      <div class="df-hero-chip">
        <span>Evaluation window</span>
        <strong>${dashboardData.evalDays!30} days</strong>
      </div>
    </div>
  </div>

  <div class="df-actions">
    <form method="post" action="<@ofbizUrl>runDemandExport</@ofbizUrl>" class="df-action-card">
      <div>
        <h3>Refresh Six-Month Export</h3>
        <p>Rebuild the CSV bundle with ERP records plus seeded six-month history for the dashboard showcase products.</p>
      </div>
      <label>
        <span>Days Back</span>
        <input type="number" name="daysBack" value="180" min="30" max="365" />
      </label>
      <button type="submit">Export Demand Data</button>
    </form>

    <form method="post" action="<@ofbizUrl>runDemandForecasts</@ofbizUrl>" class="df-action-card">
      <div>
        <h3>Generate Forecasts</h3>
        <p>Queue a fresh AI demand run so the cards and tables reflect the latest seeded demand profile.</p>
      </div>
      <label>
        <span>Horizon Days</span>
        <input type="number" name="horizonDays" value="14" min="7" max="90" />
      </label>
      <button type="submit">Queue Forecast Run</button>
    </form>

    <form method="get" action="<@ofbizUrl>demandForecastDashboard</@ofbizUrl>" class="df-action-card df-action-card--ghost">
      <div>
        <h3>Re-score Accuracy</h3>
        <p>Change the retrospective evaluation window and refresh the dashboard without leaving the screen.</p>
      </div>
      <label>
        <span>Eval Days</span>
        <input type="number" name="evalDays" value="${dashboardData.evalDays!30}" min="7" max="180" />
      </label>
      <button type="submit">Refresh Dashboard</button>
    </form>
  </div>

  <div class="df-card-grid">
    <div class="df-kpi">
      <span>Forecasted products</span>
      <strong>${metrics.forecastCount!0}</strong>
      <em>${metrics.noHistoryCount!0} products still have no usable history</em>
    </div>
    <div class="df-kpi">
      <span>Products at risk</span>
      <strong>${metrics.atRiskCount!0}</strong>
      <em>${riskLanes.critical!0} critical, ${riskLanes.watch!0} on watch</em>
    </div>
    <div class="df-kpi">
      <span>Projected 14-day demand</span>
      <strong>${metrics.projectedDemand!0?string["0.0"]}</strong>
      <em>Summed across products with real forecast history</em>
    </div>
    <div class="df-kpi">
      <span>Average stock gap</span>
      <strong>${metrics.avgStockGap!0?string["0.0"]}</strong>
      <em>Peak meaningful gap ${metrics.maxStockGap!0?string["0.0"]}</em>
    </div>
  </div>

  <div class="df-band-grid">
    <section class="df-panel">
      <div class="df-panel-head">
        <h2>Demand curve</h2>
        <span>Top forecast totals in the current run</span>
      </div>
      <div class="df-bar-list">
        <#list demandChart as bar>
          <div class="df-bar-row">
            <div class="df-bar-meta">
              <strong>${bar.productName!bar.productId}</strong>
              <span>${bar.productId} • ${bar.value?string["0.0"]}</span>
            </div>
            <div class="df-bar-track">
              <div class="df-bar-fill df-bar-fill--teal" style="width:${(bar.height / 120.0 * 100)?string["0"]}%"></div>
            </div>
          </div>
        </#list>
      </div>
    </section>

    <section class="df-panel">
      <div class="df-panel-head">
        <h2>Gap curve</h2>
        <span>Most exposed products by stock gap</span>
      </div>
      <div class="df-bar-list">
        <#list gapChart as bar>
          <div class="df-bar-row">
            <div class="df-bar-meta">
              <strong>${bar.productName!bar.productId}</strong>
              <span>${bar.productId} • gap ${bar.value?string["0.0"]}</span>
            </div>
            <div class="df-bar-track">
              <div class="df-bar-fill df-bar-fill--orange" style="width:${(bar.height / 120.0 * 100)?string["0"]}%"></div>
            </div>
          </div>
        </#list>
      </div>
    </section>
  </div>

  <div class="df-band-grid">
    <section class="df-panel">
      <div class="df-panel-head">
        <h2>Risk lanes</h2>
        <span>Products grouped by replenishment urgency</span>
      </div>
      <div class="df-lanes">
        <div class="df-lane df-lane--critical">
          <strong>${riskLanes.critical!0}</strong>
          <span>Critical gap</span>
          <em>Gap of 10 units or more</em>
        </div>
        <div class="df-lane df-lane--watch">
          <strong>${riskLanes.watch!0}</strong>
          <span>Watch list</span>
          <em>Positive gap, but still recoverable</em>
        </div>
        <div class="df-lane df-lane--healthy">
          <strong>${riskLanes.healthy!0}</strong>
          <span>Covered</span>
          <em>Demand currently supported by inventory</em>
        </div>
      </div>
    </section>

    <section class="df-panel">
      <div class="df-panel-head">
        <h2>Model readout</h2>
        <span>Transparent evaluation from the AI service</span>
      </div>
      <div class="df-model-grid">
        <div>
          <span>MAE</span>
          <strong>${evaluation.mae!0?string["0.00"]}</strong>
        </div>
        <div>
          <span>MAPE</span>
          <strong>${evaluation.mape!0?string["0.00"]}%</strong>
        </div>
        <div>
          <span>Samples</span>
          <strong>${evaluation.samples!0}</strong>
        </div>
        <div>
          <span>Status</span>
          <strong>${evaluationStatus}</strong>
        </div>
      </div>
      <div class="df-methods">
        <#list methodMix as item>
          <div class="df-method-row">
            <span>${item.label}</span>
            <div class="df-method-bar"><i style="width:${item.widthPercent!0}%"></i></div>
            <strong>${item.count!0}</strong>
          </div>
        </#list>
      </div>
    </section>
  </div>

  <section class="df-panel">
    <div class="df-panel-head">
      <h2>Forecast timeline</h2>
      <span>Recent forecast totals by run date</span>
    </div>
    <div class="df-timeline-strip">
      <#list timelineChart as point>
        <div class="df-timeline-card">
          <span>${point.label}</span>
          <strong>${point.total?string["0.0"]}</strong>
          <em>avg gap ${point.avgGap?string["0.0"]}</em>
        </div>
      </#list>
    </div>
  </section>

  <section class="df-panel">
    <div class="df-panel-head">
      <h2>Products still missing history</h2>
      <span>These rows have inventory context but no demand pattern yet</span>
    </div>
    <table class="df-table df-table--dense">
      <thead>
        <tr>
          <th>Product</th>
          <th>On Hand</th>
          <th>Min Stock</th>
          <th>Gap</th>
        </tr>
      </thead>
      <tbody>
        <#list noHistoryRows as row>
          <tr>
            <td>
              <strong>${row.productName!row.productId}</strong>
              <span>${row.productId}</span>
            </td>
            <td>${row.onHand!0?string["0.0"]}</td>
            <td>${row.minStock!0?string["0.0"]}</td>
            <td><span class="df-pill ${row.severityClass!''}">${row.stockGap!0?string["0.0"]}</span></td>
          </tr>
        </#list>
      </tbody>
    </table>
  </section>

  <div class="df-tables">
    <section class="df-panel">
      <div class="df-panel-head">
        <h2>Top stock gaps</h2>
        <span>The products most likely to need replenishment</span>
      </div>
      <table class="df-table">
        <thead>
          <tr>
            <th>Product</th>
            <th>Gap</th>
            <th>On hand</th>
            <th>Min stock</th>
            <th>14-day total</th>
          </tr>
        </thead>
        <tbody>
          <#list topGaps as row>
            <tr>
              <td>
                <strong>${row.productName!row.productId}</strong>
                <span>${row.productId}</span>
              </td>
              <td><span class="df-pill ${row.severityClass!''}">${row.stockGap!0?string["0.0"]}</span></td>
              <td>${row.onHand!0?string["0.0"]}</td>
              <td>${row.minStock!0?string["0.0"]}</td>
              <td>${row.total!0?string["0.0"]}</td>
            </tr>
          </#list>
        </tbody>
      </table>
    </section>

    <section class="df-panel">
      <div class="df-panel-head">
        <h2>Demand leaders</h2>
        <span>The strongest movers from the latest run</span>
      </div>
      <table class="df-table">
        <thead>
          <tr>
            <th>Product</th>
            <th>Avg daily</th>
            <th>Total</th>
            <th>Method</th>
            <th>Range</th>
          </tr>
        </thead>
        <tbody>
          <#list topDemand as row>
            <tr>
              <td>
                <strong>${row.productName!row.productId}</strong>
                <span>${row.productId}</span>
              </td>
              <td>${row.avgDaily!0?string["0.0"]}</td>
              <td>${row.total!0?string["0.0"]}</td>
              <td>${row.methodLabel!row.forecastMethod!'-'}</td>
              <td>${row.intervalLow!0?string["0.0"]} to ${row.intervalHigh!0?string["0.0"]}</td>
            </tr>
          </#list>
        </tbody>
      </table>
    </section>
  </div>

  <section class="df-panel">
    <div class="df-panel-head">
      <h2>Latest forecast outcomes</h2>
      <span>Readable forecast rows with inventory context</span>
    </div>
    <table class="df-table df-table--dense">
      <thead>
        <tr>
          <th>Product</th>
          <th>Avg Daily</th>
          <th>Total</th>
          <th>Range</th>
          <th>On Hand</th>
          <th>Gap</th>
          <th>Method</th>
        </tr>
      </thead>
      <tbody>
        <#list latestRows as row>
          <tr>
            <td>
              <strong>${row.productName!row.productId}</strong>
              <span>${row.productId}</span>
            </td>
            <td>${row.avgDaily!0?string["0.0"]}</td>
            <td>${row.total!0?string["0.0"]}</td>
            <td>${row.intervalLow!0?string["0.0"]} to ${row.intervalHigh!0?string["0.0"]}</td>
            <td>${row.onHand!0?string["0.0"]}</td>
            <td><span class="df-pill ${row.severityClass!''}">${row.stockGap!0?string["0.0"]}</span></td>
            <td>${row.methodLabel!row.forecastMethod!'-'}</td>
          </tr>
        </#list>
      </tbody>
    </table>
  </section>

  <div class="df-footer-link">
    <a href="<@ofbizUrl>demandForecasts</@ofbizUrl>">Open raw forecast rows</a>
  </div>
</div>
