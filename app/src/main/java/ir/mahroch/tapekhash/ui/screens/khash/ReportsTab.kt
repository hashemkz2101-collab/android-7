package ir.mahroch.tapekhash.ui.screens.khash

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ir.mahroch.tapekhash.data.ApiClient
import kotlinx.coroutines.launch
import org.json.JSONObject

private enum class ReportSection(val label: String) {
    OVERALL("گزارش کلی"), DETAILED("ریز گزارشات")
}

@Composable
fun ReportsTab() {
    var section by remember { mutableStateOf(ReportSection.OVERALL) }

    Column(Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = section.ordinal) {
            ReportSection.values().forEach { s ->
                Tab(selected = section == s, onClick = { section = s }, text = { Text(s.label) })
            }
        }
        Box(Modifier.weight(1f)) {
            when (section) {
                ReportSection.OVERALL -> OverallReportSection()
                ReportSection.DETAILED -> DetailedReportsSection()
            }
        }
    }
}

/** یک گزارش کلی از همه‌چیزِ برنامه (فاکتورها، پرداخت‌ها، سفارشات) با یک فیلتر مشترک محدوده‌ی تاریخ. */
@Composable
private fun OverallReportSection() {
    var fromDate by remember { mutableStateOf("") }
    var toDate by remember { mutableStateOf("") }
    var report by remember { mutableStateOf<JSONObject?>(null) }
    var dashboard by remember { mutableStateOf<JSONObject?>(null) }
    var loading by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()

    suspend fun load() {
        loading = true
        try {
            val body = JSONObject()
            if (fromDate.isNotBlank()) body.put("fromDate", fromDate)
            if (toDate.isNotBlank()) body.put("toDate", toDate)
            report = ApiClient.call("getKhashOverallReport", body)
        } catch (e: Exception) { }
        loading = false
    }

    LaunchedEffect(Unit) {
        try { dashboard = ApiClient.call("getKhashDashboard") } catch (e: Exception) { }
        load()
    }
    LaunchedEffect(fromDate, toDate) { load() }

    Column(Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
        Text("محدوده‌ی تاریخ گزارش", style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(8.dp))
        JalaliDateRangeRow(
            fromValue = fromDate, toValue = toDate,
            onFromChange = { fromDate = it }, onToChange = { toDate = it },
            onClear = { fromDate = ""; toDate = "" }
        )
        Spacer(Modifier.height(16.dp))

        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())

        dashboard?.let { d ->
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text("سفارشات امروز: ${d.optInt("todayCount")}")
                    Text("منتظر چاپ: ${d.optInt("pendingPrintCount")}")
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        report?.let { r ->
            Text("مالی (در محدوده‌ی انتخاب‌شده)", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text("تعداد فاکتور: ${r.optInt("invoiceCount")}")
                    Text("مبلغ کل فاکتورها: ${fmt(r.optDouble("invoiceAmount"))}")
                    Text("سهم نیروها: ${fmt(r.optDouble("employeeShareAmount"))}")
                    Text("مجموع پرداختی: ${fmt(r.optDouble("paymentAmount"))}")
                }
            }

            Spacer(Modifier.height(16.dp))
            Text("سفارشات خاش (در محدوده‌ی انتخاب‌شده)", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text("کل: ${r.optInt("orderTotal")}")
                    Text("ارسال از خاش: ${r.optInt("sentFromKhash")}")
                    Text("دریافت از خاش: ${r.optInt("receivedFromKhash")}")
                    Text("چاپ‌شده: ${r.optInt("printedIranshahr")}")
                    Text("ارسال به خاش: ${r.optInt("sentBackToKhash")}")
                }
            }
        }
    }
}

private enum class DetailKind(val label: String) {
    ORDERS("سفارشات"), INVOICES("فاکتورها"), PAYMENTS("پرداخت‌ها")
}

/** ریز گزارشات: هر بخش (سفارشات/فاکتورها/پرداخت‌ها) تب جدا و محدوده‌ی تاریخ مستقل خودش را دارد. */
@Composable
private fun DetailedReportsSection() {
    var kind by remember { mutableStateOf(DetailKind.ORDERS) }

    Column(Modifier.fillMaxSize()) {
        ScrollableTabRow(selectedTabIndex = kind.ordinal, edgePadding = 12.dp) {
            DetailKind.values().forEach { k ->
                Tab(selected = kind == k, onClick = { kind = k }, text = { Text(k.label) })
            }
        }
        Box(Modifier.weight(1f).padding(16.dp)) {
            when (kind) {
                DetailKind.ORDERS -> OrdersDetailReport()
                DetailKind.INVOICES -> InvoicesDetailReport()
                DetailKind.PAYMENTS -> PaymentsDetailReport()
            }
        }
    }
}

@Composable
private fun OrdersDetailReport() {
    var fromDate by remember { mutableStateOf("") }
    var toDate by remember { mutableStateOf("") }
    var byDate by remember { mutableStateOf<List<JSONObject>>(emptyList()) }

    suspend fun load() {
        try {
            val body = JSONObject()
            if (fromDate.isNotBlank()) body.put("fromDate", fromDate)
            if (toDate.isNotBlank()) body.put("toDate", toDate)
            val res = ApiClient.call("getKhashReportByDate", body)
            val arr = res.getJSONArray("report")
            byDate = (0 until arr.length()).map { arr.getJSONObject(it) }
        } catch (e: Exception) { }
    }
    LaunchedEffect(fromDate, toDate) { load() }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        JalaliDateRangeRow(
            fromValue = fromDate, toValue = toDate,
            onFromChange = { fromDate = it }, onToChange = { toDate = it },
            onClear = { fromDate = ""; toDate = "" }
        )
        Spacer(Modifier.height(12.dp))
        byDate.forEach { g ->
            ElevatedCard(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Column(Modifier.padding(10.dp)) {
                    Text(g.optString("date"), style = MaterialTheme.typography.titleSmall)
                    Text("کل: ${g.optInt("total")} — چاپ‌شده: ${g.optInt("printedIranshahr")} — ارسال به خاش: ${g.optInt("sentBackToKhash")}")
                }
            }
        }
        if (byDate.isEmpty()) Text("موردی در این بازه‌ی تاریخ نیست.")
    }
}

@Composable
private fun InvoicesDetailReport() {
    var fromDate by remember { mutableStateOf("") }
    var toDate by remember { mutableStateOf("") }
    var invoices by remember { mutableStateOf<List<JSONObject>>(emptyList()) }

    suspend fun load() {
        try {
            val body = JSONObject()
            if (fromDate.isNotBlank()) body.put("fromDate", fromDate)
            if (toDate.isNotBlank()) body.put("toDate", toDate)
            val res = ApiClient.call("getInvoices", body)
            val arr = res.getJSONArray("invoices")
            invoices = (0 until arr.length()).map { arr.getJSONObject(it) }
        } catch (e: Exception) { }
    }
    LaunchedEffect(fromDate, toDate) { load() }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        JalaliDateRangeRow(
            fromValue = fromDate, toValue = toDate,
            onFromChange = { fromDate = it }, onToChange = { toDate = it },
            onClear = { fromDate = ""; toDate = "" }
        )
        Spacer(Modifier.height(8.dp))
        Text("تعداد: ${invoices.size} — مجموع: ${fmt(invoices.sumOf { it.optDouble("total_amount") })}")
        Spacer(Modifier.height(8.dp))
        invoices.forEach { inv ->
            ElevatedCard(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Column(Modifier.padding(10.dp)) {
                    Text(inv.optString("full_invoice_no"), style = MaterialTheme.typography.titleSmall)
                    Text("مبلغ: ${fmt(inv.optDouble("total_amount"))} — سهم نیرو: ${fmt(inv.optDouble("employee_share"))}")
                    Text("نیرو: ${inv.optString("employee_name")} — تاریخ: ${inv.optString("jalali_date")}")
                }
            }
        }
        if (invoices.isEmpty()) Text("موردی در این بازه‌ی تاریخ نیست.")
    }
}

@Composable
private fun PaymentsDetailReport() {
    var fromDate by remember { mutableStateOf("") }
    var toDate by remember { mutableStateOf("") }
    var payments by remember { mutableStateOf<List<JSONObject>>(emptyList()) }

    suspend fun load() {
        try {
            val body = JSONObject()
            if (fromDate.isNotBlank()) body.put("fromDate", fromDate)
            if (toDate.isNotBlank()) body.put("toDate", toDate)
            val res = ApiClient.call("getPayments", body)
            val arr = res.getJSONArray("payments")
            payments = (0 until arr.length()).map { arr.getJSONObject(it) }
        } catch (e: Exception) { }
    }
    LaunchedEffect(fromDate, toDate) { load() }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        JalaliDateRangeRow(
            fromValue = fromDate, toValue = toDate,
            onFromChange = { fromDate = it }, onToChange = { toDate = it },
            onClear = { fromDate = ""; toDate = "" }
        )
        Spacer(Modifier.height(8.dp))
        Text("تعداد: ${payments.size} — مجموع: ${fmt(payments.sumOf { it.optDouble("amount") })}")
        Spacer(Modifier.height(8.dp))
        payments.forEach { p ->
            ElevatedCard(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Column(Modifier.padding(10.dp)) {
                    Text("${p.optString("employee_name")} — ${fmt(p.optDouble("amount"))}")
                    Text("تاریخ: ${p.optString("jalali_date")}")
                }
            }
        }
        if (payments.isEmpty()) Text("موردی در این بازه‌ی تاریخ نیست.")
    }
}
