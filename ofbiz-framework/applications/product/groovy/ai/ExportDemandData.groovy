import org.apache.ofbiz.entity.util.EntityQuery
import org.apache.ofbiz.entity.condition.EntityCondition
import org.apache.ofbiz.entity.condition.EntityOperator
import org.apache.ofbiz.service.DispatchContext
import org.apache.ofbiz.base.util.UtilDateTime
import org.apache.ofbiz.base.util.Debug

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.sql.Timestamp

Map exportDemandDataBundle() {
    return exportDemandDataBundle(resolveDispatchContext(), resolveServiceContext())
}

Map exportDemandDataBundle(DispatchContext dctx, Map context) {
    def res1 = exportOrderLinesForDemand(dctx, context)
    if (res1.responseMessage != "success") return res1
    def res2 = exportProductsForDemand(dctx, context)
    if (res2.responseMessage != "success") return res2
    def res3 = exportProductFacilityForDemand(dctx, context)
    if (res3.responseMessage != "success") return res3
    def res4 = exportInventoryItemsForDemand(dctx, context)
    if (res4.responseMessage != "success") return res4
    return [responseMessage: "success"]
}

Map exportDemandDataDelta() {
    return exportDemandDataDelta(resolveDispatchContext(), resolveServiceContext())
}

Map exportDemandDataDelta(DispatchContext dctx, Map context) {
    Integer daysBack = (context.daysBack ?: 1) as Integer
    def ctx = [daysBack: daysBack]
    return exportDemandDataBundle(dctx, ctx)
}

Map exportOrderLinesForDemand() {
    return exportOrderLinesForDemand(resolveDispatchContext(), resolveServiceContext())
}

Map exportOrderLinesForDemand(DispatchContext dctx, Map context) {
    def delegator = dctx?.delegator
    if (!delegator) return [responseMessage: "error", errorMessage: "No delegator available"]

    def statusId = context.statusId
    Timestamp fromDate = parseDate(context.fromDate)
    Timestamp toDate = parseDate(context.toDate)
    Integer daysBack = (context.daysBack ?: 0) as Integer
    if (!fromDate && daysBack > 0) {
        fromDate = UtilDateTime.addDaysToTimestamp(UtilDateTime.nowTimestamp(), -daysBack)
    }
    List conditions = []
    if (statusId) {
        conditions << EntityCondition.makeCondition("statusId", EntityOperator.EQUALS, statusId)
    }
    if (fromDate) {
        conditions << EntityCondition.makeCondition("orderDate", EntityOperator.GREATER_THAN_EQUAL_TO, fromDate)
    }
    if (toDate) {
        conditions << EntityCondition.makeCondition("orderDate", EntityOperator.LESS_THAN_EQUAL_TO, toDate)
    }

    def query = EntityQuery.use(delegator)
            .from("OrderHeader")
            .orderBy("orderDate")
    if (!conditions.isEmpty()) {
        query = query.where(conditions)
    }

    def headers = query.queryList()

    List<List<String>> rows = []
    headers.each { oh ->
        EntityQuery.use(delegator)
                .from("OrderItem")
                .where("orderId", oh.orderId)
                .queryList()
                .each { oi ->
                    def facilityId = ""
                    def shipGrpAssoc = EntityQuery.use(delegator)
                            .from("OrderItemShipGroupAssoc")
                            .where("orderId", oi.orderId, "orderItemSeqId", oi.orderItemSeqId)
                            .queryFirst()
                    if (shipGrpAssoc) {
                        def shipGrp = EntityQuery.use(delegator)
                                .from("OrderItemShipGroup")
                                .where("orderId", oi.orderId, "shipGroupSeqId", shipGrpAssoc.shipGroupSeqId)
                                .queryFirst()
                        if (shipGrp) {
                            facilityId = shipGrp.facilityId ?: ""
                        }
                    }
                    rows << [
                            oi.orderId,
                            formatDate(oh.orderDate),
                            oi.productId ?: "",
                            sanitizeQuantity(oi.quantity),
                            facilityId
                    ]
                }
    }

    int originalCount = rows.size()
    if (isDemoSeedEnabled()) {
        rows.addAll(buildDemoDemandRows(daysBack))
    }

    writeCsv("order_lines.csv", ["orderId", "orderDate", "productId", "quantity", "facilityId"], rows)
    Debug.logInfo("Exported ${rows.size()} order lines for demand data (${rows.size() - originalCount} seeded demo rows)", "AI_DEMAND")
    return [responseMessage: "success", exported: rows.size(), originalExported: originalCount, demoSeeded: rows.size() - originalCount]
}

Map exportProductsForDemand() {
    return exportProductsForDemand(resolveDispatchContext(), resolveServiceContext())
}

Map exportProductsForDemand(DispatchContext dctx, Map context) {
    def delegator = dctx?.delegator
    if (!delegator) return [responseMessage: "error", errorMessage: "No delegator available"]
    def products = EntityQuery.use(delegator).from("Product").queryList()

    List<List<String>> rows = []
    products.each { p ->
        rows << [
                p.productId ?: "",
                p.internalName ?: "",
                p.productTypeId ?: "",
                formatDate(p.introductionDate),
                formatDate(p.salesDiscontinuationDate)
        ]
    }
    writeCsv("products.csv", ["productId", "internalName", "productTypeId", "introductionDate", "salesDiscontinuationDate"], rows)
    Debug.logInfo("Exported ${rows.size()} products for demand data", "AI_DEMAND")
    return [responseMessage: "success", exported: rows.size()]
}

Map exportProductFacilityForDemand() {
    return exportProductFacilityForDemand(resolveDispatchContext(), resolveServiceContext())
}

Map exportProductFacilityForDemand(DispatchContext dctx, Map context) {
    def delegator = dctx?.delegator
    if (!delegator) return [responseMessage: "error", errorMessage: "No delegator available"]
    def rows = []
    EntityQuery.use(delegator).from("ProductFacility").queryList().each { pf ->
        rows << [
                pf.productId ?: "",
                pf.facilityId ?: "",
                pf.minimumStock ?: "",
                pf.reorderQuantity ?: "",
                ""
        ]
    }
    if (isDemoSeedEnabled()) {
        upsertDemoProductFacilityRows(rows)
    }
    writeCsv("product_facility.csv", ["productId", "facilityId", "minimumStock", "reorderQuantity", "lastInventoryCountDate"], rows)
    Debug.logInfo("Exported ${rows.size()} product facility rows for demand data", "AI_DEMAND")
    return [responseMessage: "success", exported: rows.size()]
}

Map exportInventoryItemsForDemand() {
    return exportInventoryItemsForDemand(resolveDispatchContext(), resolveServiceContext())
}

Map exportInventoryItemsForDemand(DispatchContext dctx, Map context) {
    def delegator = dctx?.delegator
    if (!delegator) return [responseMessage: "error", errorMessage: "No delegator available"]
    def rows = []
    EntityQuery.use(delegator).from("InventoryItem").queryList().each { ii ->
        rows << [
                ii.inventoryItemId ?: "",
                ii.productId ?: "",
                ii.facilityId ?: "",
                ii.quantityOnHandTotal ?: "",
                ii.availableToPromiseTotal ?: ""
        ]
    }
    writeCsv("inventory_items.csv", ["inventoryItemId", "productId", "facilityId", "quantityOnHandTotal", "availableToPromiseTotal"], rows)
    Debug.logInfo("Exported ${rows.size()} inventory items for demand data", "AI_DEMAND")
    return [responseMessage: "success", exported: rows.size()]
}

private void writeCsv(String filename, List<String> headers, List<List<String>> rows) {
    def exportDir = new File("runtime/data/export")
    exportDir.mkdirs()
    def outFile = new File(exportDir, filename)
    StringBuilder out = new StringBuilder(headers.join(",") + "\n")
    rows.each { cols ->
        out.append(cols.collect { sanitize(it) }.join(",")).append("\n")
    }
    outFile.text = out.toString()
}

private List<List<String>> buildDemoDemandRows(Integer requestedDaysBack) {
    int totalDays = Math.max((requestedDaysBack ?: 0) as Integer, 180)
    LocalDate today = LocalDate.now()
    List<List<String>> rows = []

    demoProductProfiles().eachWithIndex { profile, productIndex ->
        (0..<totalDays).each { offset ->
            LocalDate orderDate = today.minusDays(totalDays - offset)
            int quantity = calculateDemoQuantity(profile, orderDate, offset, productIndex)
            rows << [
                    "DEMO-${profile.productId}-${orderDate.format(DateTimeFormatter.BASIC_ISO_DATE)}",
                    orderDate.toString(),
                    profile.productId,
                    quantity.toString(),
                    profile.facilityId
            ]
        }
    }

    return rows
}

private int calculateDemoQuantity(Map profile, LocalDate orderDate, int offset, int productIndex) {
    int weeklyPattern = (((offset + productIndex) % 7) in [4, 5]) ? profile.weeklySwing as Integer : 0
    int paydayLift = (orderDate.dayOfMonth in [1, 14, 15, 28]) ? 2 : 0
    int campaignLift = ((offset + productIndex * 3) % 29 == 0) ? 4 : 0
    double trend = offset * (profile.trendFactor as BigDecimal)
    int quantity = Math.round((profile.baseDemand as Integer) + weeklyPattern + paydayLift + campaignLift + trend)
    return Math.max(profile.floor as Integer, quantity)
}

private void upsertDemoProductFacilityRows(List<List<String>> rows) {
    Set<String> existing = rows.collect { "${it[0]}::${it[1]}" }.toSet()
    demoProductProfiles().each { profile ->
        List<String> demoRow = [
                profile.productId,
                profile.facilityId,
                formatWholeNumber(profile.minimumStock),
                formatWholeNumber(profile.reorderQuantity),
                ""
        ]
        String key = "${profile.productId}::${profile.facilityId}"
        int rowIndex = rows.findIndexOf { it[0] == profile.productId && it[1] == profile.facilityId }
        if (rowIndex >= 0) {
            rows[rowIndex] = demoRow
        } else if (!existing.contains(key)) {
            rows << demoRow
        }
    }
}

private boolean isDemoSeedEnabled() {
    return !System.getProperty("ai.demand.demoSeed", "true").equalsIgnoreCase("false")
}

private List<Map> demoProductProfiles() {
    return [
            [productId: "GZ-2644", facilityId: "WebStoreWarehouse", baseDemand: 9, weeklySwing: 3, trendFactor: 0.025, floor: 4, minimumStock: 12, reorderQuantity: 30],
            [productId: "GZ-8544", facilityId: "WebStoreWarehouse", baseDemand: 6, weeklySwing: 3, trendFactor: 0.018, floor: 3, minimumStock: 24, reorderQuantity: 60],
            [productId: "WG-1111", facilityId: "WebStoreWarehouse", baseDemand: 7, weeklySwing: 2, trendFactor: 0.020, floor: 3, minimumStock: 10, reorderQuantity: 28],
            [productId: "WG-5569", facilityId: "WebStoreWarehouse", baseDemand: 5, weeklySwing: 2, trendFactor: 0.022, floor: 2, minimumStock: 28, reorderQuantity: 70],
            [productId: "WG-9943-B3", facilityId: "WebStoreWarehouse", baseDemand: 4, weeklySwing: 2, trendFactor: 0.016, floor: 2, minimumStock: 18, reorderQuantity: 36],
            [productId: "WG-9943-B4", facilityId: "WebStoreWarehouse", baseDemand: 4, weeklySwing: 2, trendFactor: 0.017, floor: 2, minimumStock: 18, reorderQuantity: 36],
            [productId: "GZ-1000", facilityId: "WebStoreWarehouse", baseDemand: 3, weeklySwing: 1, trendFactor: 0.015, floor: 1, minimumStock: 14, reorderQuantity: 30],
            [productId: "GZ-1001", facilityId: "WebStoreWarehouse", baseDemand: 2, weeklySwing: 1, trendFactor: 0.012, floor: 1, minimumStock: 12, reorderQuantity: 24]
    ]
}

private String formatWholeNumber(Object value) {
    if (value == null) return ""
    return new BigDecimal(value.toString()).setScale(6, BigDecimal.ROUND_HALF_UP).toPlainString()
}

private String sanitize(Object value) {
    if (value == null) return ""
    def v = value.toString().replaceAll(/\r?\n/, " ")
    if (v.contains(",") || v.contains("\"")) {
        v = '"' + v.replace("\"", "\"\"") + '"'
    }
    return v
}

private String formatDate(Object value) {
    if (!value) return ""
    try {
        def ts = value instanceof java.sql.Timestamp ? value : UtilDateTime.toTimestamp(value)
        return ts.toLocalDateTime().toLocalDate().toString()
    } catch (Throwable ignored) {
        return value.toString()
    }
}

private Timestamp parseDate(Object value) {
    if (!value) return null
    try {
        def ts = value instanceof Timestamp ? value : UtilDateTime.toTimestamp(value.toString())
        return ts
    } catch (Throwable ignored) {
        return null
    }
}

private String sanitizeQuantity(Object quantity) {
    def q = 0
    try {
        q = quantity ? quantity.toString().toBigDecimal() : 0
    } catch (Throwable ignored) {
        q = 0
    }
    if (q < 0) q = 0
    return q.toString()
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
