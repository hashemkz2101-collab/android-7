package ir.mahroch.tapekhash.ui.screens.sales

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ir.mahroch.tapekhash.data.ApiClient
import ir.mahroch.tapekhash.ui.screens.khash.JalaliDateRangeRow
import ir.mahroch.tapekhash.ui.screens.khash.fmt
import org.json.JSONObject

/** تب «گزارش فروش»: خلاصه‌ی فروش در یک محدوده‌ی تاریخ (انتخابی، نه تایپی) به‌همراه پرفروش‌ترین کالاها. */
@Composable
fun SalesReportTab() {
    var fromDate by remember { mutableStateOf("") }
    var toDate by remember { mutableStateOf("") }
    var report by remember { mutableStateOf<JSONObject?>(null) }
    var loading by remember { mutableStateOf(true) }

    suspend fun load() {
        loading = true
        try {
            val body = JSONObject()
            if (fromDate.isNotBlank()) body.put("fromDate", fromDate)
            if (toDate.isNotBlank()) body.put("toDate", toDate)
            report = ApiClient.call("getSalesReport", body)
        } catch (e: Exception) { }
        loading = false
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

        report?.let { r ->
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text("تعداد فاکتور: ${r.optInt("invoiceCount")}")
                    Text("مبلغ کل فروش: ${fmt(r.optDouble("totalAmount"))} تومان")
                    Text("مبلغ پرداخت‌شده: ${fmt(r.optDouble("totalPaid"))} تومان")
                    Text(
                        "مانده‌ی کل: ${fmt(r.optDouble("totalBalance"))} تومان",
                        color = if (r.optDouble("totalBalance") > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            Text("پرفروش‌ترین کالاها", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            val byProduct = r.optJSONArray("byProduct")
            if (byProduct == null || byProduct.length() == 0) {
                Text("موردی در این بازه‌ی تاریخ نیست.")
            } else {
                for (i in 0 until byProduct.length()) {
                    val p = byProduct.getJSONObject(i)
                    ElevatedCard(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Column(Modifier.padding(10.dp)) {
                            Text(p.optString("productName"), style = MaterialTheme.typography.titleSmall)
                            Text("${fmt(p.optDouble("qty"))} ${p.optString("unitName")} — ${fmt(p.optDouble("amount"))} تومان")
                        }
                    }
                }
            }
        }
    }
}
