import groovy.json.JsonSlurper
import org.apache.ofbiz.base.util.Debug
import org.apache.ofbiz.base.util.UtilDateTime
import org.apache.ofbiz.entity.condition.EntityCondition
import org.apache.ofbiz.entity.condition.EntityOperator
import org.apache.ofbiz.entity.util.EntityQuery
import org.apache.ofbiz.service.DispatchContext

import java.net.HttpURLConnection

Map getDemandForecastDashboard() {
    return getDemandForecastDashboard(resolveDispatchContext(), resolveServiceContext())
}

Map getDemandForecastDashboard(Map context) {
    DispatchContext dctx = context?.dispatcher?.dispatchContext ?: context?.dctx
    return getDemandForecastDashboard(dctx, context)
}

Map getDemandForecastDashboard(DispatchContext dctx, Map context) {
    if (!dctx) return serviceError("No DispatchContext provided")
    def delegator = dctx.delegator
    int evalDays = (context.evalDays ?: 30) as Integer
    String productFilter = context.productId?.toString()?.trim()

    def since = UtilDateTime.addDaysToTimestamp(UtilDateTime.nowTimestamp(), -30)
    def recent = EntityQuery.use(delegator)
            .from("DemandForecast")
            .where(EntityCondition.makeCondition("runDate", EntityOperator.GREATER_THAN_EQUAL_TO, since))
            .orderBy("-runDate")
            .queryList()
    def sourceForecasts = recent
    if (!sourceForecasts) {
        sourceForecasts = EntityQuery.use(delegator)
                .from("DemandForecast")
                .orderBy("-runDate")
                .queryList()
                .take(250)
    }
    def last = sourceForecasts ? sourceForecasts.first() : null
    def latestForecasts = dedupeLatestByProduct(sourceForecasts ?: [])
    def productMap = fetchProductNames(delegator, latestForecasts.collect { it.productId })
    def enrichedLatest = latestForecasts.collect { enrichForecastRow(it, productMap[it.productId]) }
    def filteredRows = productFilter ? enrichedLatest.findAll { it.productId?.toLowerCase()?.contains(productFilter.toLowerCase()) } : enrichedLatest

    def meaningfulRows = filteredRows.findAll { it.historyDaysValue > 0 || it.totalValue > 0 }
    def riskRows = filteredRows.findAll { it.stockGapValue > 0 }
    long forecastCount = meaningfulRows.size()
    double avgStockGap = meaningfulRows ? meaningfulRows.collect { it.stockGapValue }.sum() / meaningfulRows.size() : 0
    double maxStockGap = meaningfulRows ? meaningfulRows.collect { it.stockGapValue }.max() : 0
    double projectedDemand = meaningfulRows.collect { it.totalValue }.sum() ?: 0
    long atRiskCount = riskRows.size()
    long nonZeroDemandCount = meaningfulRows.count { it.totalValue > 0 }
    long noHistoryCount = filteredRows.count { it.historyDaysValue <= 0 }

    def riskLanes = [
            critical: riskRows.count { it.stockGapValue >= 10 },
            watch   : riskRows.count { it.stockGapValue > 0 && it.stockGapValue < 10 },
            healthy : meaningfulRows.count { it.stockGapValue <= 0 && it.totalValue > 0 }
    ]

    def topGaps = riskRows.sort { a, b -> b.stockGapValue <=> a.stockGapValue }.take(8)
    def topDemand = meaningfulRows.sort { a, b -> b.totalValue <=> a.totalValue }.take(8)
    def evaluation = fetchEvaluation(evalDays)
    Double accuracyScore = (evaluation?.samples ?: 0) > 0 && evaluation?.mape != null ? Math.max(0D, 100D - (evaluation.mape as Double)) : null
    def methodMix = buildMethodMix(meaningfulRows)
    def latestRows = meaningfulRows.sort { a, b -> b.totalValue <=> a.totalValue }.take(24)
    def noHistoryRows = filteredRows.findAll { it.historyDaysValue <= 0 }.sort { a, b -> b.stockGapValue <=> a.stockGapValue }.take(12)
    def demandChart = buildCategoryChart(topDemand.take(6), "totalValue", 280, 120)
    def gapChart = buildCategoryChart(topGaps.take(6), "stockGapValue", 280, 120)
    def timelineChart = buildTimelineChart(sourceForecasts.findAll { (it.historyDays ?: 0) as Integer > 0 })

    return [
            responseMessage: "success",
            forecastCount: forecastCount,
            lastRunDate: last?.runDate,
            avgStockGap: avgStockGap,
            maxStockGap: maxStockGap,
            evalDays: evalDays,
            evaluation: evaluation,
            accuracyScore: accuracyScore,
            metrics: [
                    forecastCount: forecastCount,
                    projectedDemand: projectedDemand,
                    avgStockGap: avgStockGap,
                    maxStockGap: maxStockGap,
                    atRiskCount: atRiskCount,
                    nonZeroDemandCount: nonZeroDemandCount,
                    noHistoryCount: noHistoryCount,
                    totalTrackedCount: filteredRows.size()
            ],
            riskLanes: riskLanes,
            methodMix: methodMix,
            topGaps: topGaps,
            topDemand: topDemand,
            latestRows: latestRows,
            noHistoryRows: noHistoryRows,
            productFilter: productFilter,
            demandChart: demandChart,
            gapChart: gapChart,
            timelineChart: timelineChart
    ]
}

private Map fetchEvaluation(int evalDays) {
    String baseUrl = System.getProperty("ai.demand.url", "http://localhost:8000")
    String apiKey = System.getProperty("ai.demand.apiKey", "")
    Integer timeoutMs = Integer.getInteger("ai.demand.timeoutMs", 5000)

    try {
        def url = new URL(baseUrl + "/evaluation?eval_days=" + evalDays)
        HttpURLConnection conn = (HttpURLConnection) url.openConnection()
        conn.setRequestMethod("GET")
        conn.setConnectTimeout(timeoutMs)
        conn.setReadTimeout(timeoutMs)
        if (apiKey) {
            conn.setRequestProperty("X-API-Key", apiKey)
        }
        int code = conn.responseCode
        if (code >= 300) {
            return [mae: 0, mape: 0, samples: 0, error: "AI service HTTP ${code}"]
        }
        def text = conn.inputStream.text
        def data = new JsonSlurper().parseText(text)
        if ((data?.samples ?: 0) as Integer <= 0) {
            data.error = "insufficient history"
        }
        return data
    } catch (Throwable t) {
        Debug.logWarning("Failed to fetch evaluation: ${t.message}", "AI_DEMAND")
        return [mae: 0, mape: 0, samples: 0, error: "unavailable"]
    }
}

private List<Map> dedupeLatestByProduct(List records) {
    Set<String> seen = [] as Set<String>
    List<Map> latest = []
    records.each { record ->
        if (record?.productId && !seen.contains(record.productId)) {
            latest << record
            seen << record.productId
        }
    }
    return latest
}

private Map fetchProductNames(def delegator, List productIds) {
    if (!productIds) {
        return [:]
    }

    Map productMap = [:]
    productIds.each { productId ->
        def product = EntityQuery.use(delegator).from("Product").where("productId", productId).queryFirst()
        productMap[productId] = product?.internalName ?: productId
    }
    return productMap
}

private Map enrichForecastRow(def record, String productName) {
    double stockGap = (record.stockGap ?: 0) as Double
    double total = (record.total ?: 0) as Double
    double avgDaily = (record.avgDaily ?: 0) as Double
    String severityClass = stockGap >= 10 ? "is-critical" : (stockGap > 0 ? "is-watch" : "is-healthy")

    return [
            productId: record.productId,
            productName: productName ?: record.productId,
            stockGap: stockGap,
            stockGapValue: stockGap,
            onHand: (record.onHand ?: 0) as Double,
            minStock: (record.minStock ?: 0) as Double,
            total: total,
            totalValue: total,
            avgDaily: avgDaily,
            historyDaysValue: (record.historyDays ?: 0) as Integer,
            intervalLow: (record.intervalLow ?: 0) as Double,
            intervalHigh: (record.intervalHigh ?: 0) as Double,
            forecastMethod: record.forecastMethod ?: "rolling_mean",
            methodLabel: prettifyMethod(record.forecastMethod ?: "rolling_mean"),
            severityClass: severityClass,
            runDate: record.runDate
    ]
}

private List<Map> buildMethodMix(List<Map> rows) {
    if (!rows) {
        return []
    }

    Map<String, Integer> counts = [:].withDefault { 0 }
    rows.each { row ->
        String key = row.forecastMethod ?: "rolling_mean"
        counts[key] = counts[key] + 1
    }

    int maxCount = counts.values().max() ?: 1
    return counts.collect { method, count ->
        [
                key: method,
                label: prettifyMethod(method),
                count: count,
                widthPercent: Math.max(12, Math.round((count * 100.0D) / maxCount))
        ]
    }.sort { a, b -> b.count <=> a.count }
}

private List<Map> buildCategoryChart(List<Map> rows, String metricKey, int width, int height) {
    if (!rows) {
        return []
    }

    double maxValue = rows.collect { (it[metricKey] ?: 0) as Double }.max() ?: 1D
    int barCount = rows.size()
    double gap = 10D
    double barWidth = Math.max(22D, (width - (gap * (barCount - 1))) / Math.max(1, barCount))

    return rows.withIndex().collect { row, index ->
        double value = (row[metricKey] ?: 0) as Double
        double barHeight = maxValue > 0 ? (value / maxValue) * height : 0
        [
                productId: row.productId,
                productName: row.productName,
                value: value,
                x: Math.round(index * (barWidth + gap)),
                y: Math.round(height - barHeight),
                width: Math.round(barWidth),
                height: Math.round(barHeight)
        ]
    }
}

private List<Map> buildTimelineChart(List records) {
    if (!records) {
        return []
    }

    def buckets = records.groupBy { it.runDate?.toLocalDateTime()?.toLocalDate()?.toString() ?: "unknown" }
    def points = buckets.collect { label, grouped ->
        double total = grouped.collect { (it.total ?: 0) as Double }.sum() ?: 0
        double avgGap = grouped ? (grouped.collect { (it.stockGap ?: 0) as Double }.sum() / grouped.size()) : 0
        [label: label, total: total, avgGap: avgGap]
    }.sort { a, b -> a.label <=> b.label }.takeRight(8)

    double maxTotal = points.collect { it.total as Double }.max() ?: 1D
    int width = 340
    int height = 140
    int count = points.size()
    double step = count > 1 ? width / (count - 1) : width

    return points.withIndex().collect { point, index ->
        double totalHeight = maxTotal > 0 ? ((point.total as Double) / maxTotal) * height : 0
        [
                label: point.label,
                total: point.total,
                avgGap: point.avgGap,
                x: Math.round(index * step),
                y: Math.round(height - totalHeight)
        ]
    }
}

private String prettifyMethod(String method) {
    return method?.replace("_", " ")?.split(" ")?.collect { it.capitalize() }?.join(" ") ?: "Unknown"
}

private Map serviceError(String msg) {
    return [responseMessage: "error", errorMessage: msg]
}

private DispatchContext resolveDispatchContext() {
    if (binding?.hasVariable("dctx")) {
        return binding.getVariable("dctx") as DispatchContext
    }
    if (binding?.hasVariable("dispatcher")) {
        return binding.getVariable("dispatcher")?.dispatchContext as DispatchContext
    }
    if (binding?.hasVariable("context")) {
        def ctx = binding.getVariable("context") as Map
        return (ctx?.dispatcher?.dispatchContext ?: ctx?.dctx) as DispatchContext
    }
    return null
}

private Map resolveServiceContext() {
    if (binding?.hasVariable("context")) {
        return (binding.getVariable("context") as Map) ?: [:]
    }
    return [:]
}
