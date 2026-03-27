<#assign dashboardData = dashboard!{} />
<#assign metrics = dashboardData.metrics!{} />
<#assign latestRows = dashboardData.latestRows![] />
<#assign noHistoryRows = dashboardData.noHistoryRows![] />
<#assign demandChart = dashboardData.demandChart![] />
<#assign gapChart = dashboardData.gapChart![] />
<#assign timelineChart = dashboardData.timelineChart![] />
<#assign productFilter = dashboardData.productFilter!"" />
<link rel="stylesheet" href="${request.getContextPath()}/dashboard-demand.css" />

<div class="df-dashboard df-dashboard--list">
  <div class="df-hero df-hero--compact">
    <div>
      <div class="df-eyebrow">Forecast outcomes</div>
      <h1>Demand Forecast Results</h1>
      <p>This view shows the latest saved forecast per product, with projected totals, confidence bands, inventory coverage, and stock-gap risk in one place.</p>
    </div>
    <div class="df-hero-meta">
      <div class="df-hero-chip">
        <span>Forecasted products</span>
        <strong>${metrics.forecastCount!0}</strong>
      </div>
      <div class="df-hero-chip">
        <span>Projected demand</span>
        <strong>${metrics.projectedDemand!0?string["0.0"]}</strong>
      </div>
    </div>
  </div>

  <div class="df-actions">
    <form method="post" action="<@ofbizUrl>runDemandExport</@ofbizUrl>" class="df-action-card">
      <div>
        <h3>Export data</h3>
        <p>Refresh the six-month demand bundle used by the AI service.</p>
      </div>
      <label>
        <span>Days Back</span>
        <input type="number" name="daysBack" value="180" min="30" max="365" />
      </label>
      <button type="submit">Export</button>
    </form>

    <form method="post" action="<@ofbizUrl>runDemandForecasts</@ofbizUrl>" class="df-action-card">
      <div>
        <h3>Forecast run</h3>
        <p>Generate and persist fresh forecast rows.</p>
      </div>
      <label>
        <span>Horizon Days</span>
        <input type="number" name="horizonDays" value="14" min="7" max="90" />
      </label>
      <button type="submit">Queue Run</button>
    </form>

    <form method="get" action="<@ofbizUrl>demandForecasts</@ofbizUrl>" class="df-action-card df-action-card--ghost">
      <div>
        <h3>Filter products</h3>
        <p>Search saved outcomes by product id.</p>
      </div>
      <label>
        <span>Product Id</span>
        <input type="text" name="productId" value="${productFilter}" />
      </label>
      <button type="submit">Apply Filter</button>
    </form>
  </div>

  <div class="df-band-grid">
    <section class="df-panel">
      <div class="df-panel-head">
        <h2>Projected demand</h2>
        <span>Top products by 14-day total</span>
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
        <h2>Stock gap pressure</h2>
        <span>Products with the biggest replenishment gap</span>
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

  <section class="df-panel">
    <div class="df-panel-head">
      <h2>Forecast timeline</h2>
      <span>Latest run totals across recent forecast dates</span>
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
      <h2>Saved forecast outcomes</h2>
      <span>Latest forecast row per product with meaningful demand history</span>
    </div>
    <table class="df-table df-table--dense">
      <thead>
        <tr>
          <th>Product</th>
          <th>Avg Daily</th>
          <th>14-Day Total</th>
          <th>Confidence Range</th>
          <th>On Hand</th>
          <th>Min Stock</th>
          <th>Stock Gap</th>
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
            <td>${row.minStock!0?string["0.0"]}</td>
            <td><span class="df-pill ${row.severityClass!''}">${row.stockGap!0?string["0.0"]}</span></td>
            <td>${row.methodLabel!row.forecastMethod!'-'}</td>
          </tr>
        </#list>
      </tbody>
    </table>
  </section>

  <section class="df-panel">
    <div class="df-panel-head">
      <h2>No-history products</h2>
      <span>Rows that still need real demand or broader seeded coverage</span>
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
</div>
