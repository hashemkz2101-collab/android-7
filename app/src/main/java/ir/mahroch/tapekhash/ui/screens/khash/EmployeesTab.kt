package ir.mahroch.tapekhash.ui.screens.khash

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ir.mahroch.tapekhash.data.ApiClient
import ir.mahroch.tapekhash.data.ApiException
import ir.mahroch.tapekhash.data.Session
import kotlinx.coroutines.launch
import org.json.JSONObject

@Composable
fun EmployeesTab() {
    var employees by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var showAdd by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    var statementFor by remember { mutableStateOf<String?>(null) }
    val isAdmin = Session.loadCachedUser()?.isAdmin == true
    val scope = rememberCoroutineScope()

    suspend fun load() {
        try {
            val res = ApiClient.call("getAllEmployeeBalances")
            val arr = res.getJSONArray("balances")
            employees = (0 until arr.length()).map { arr.getJSONObject(it) }
        } catch (e: ApiException) { error = e.message }
        loading = false
    }
    LaunchedEffect(Unit) { load() }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text("نیروها و مانده حساب", style = MaterialTheme.typography.titleMedium)
            if (isAdmin) Button(onClick = { showAdd = true }) { Text("+ نیرو جدید") }
        }
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(employees) { e ->
                ElevatedCard(Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.padding(12.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(e.optString("name"), style = MaterialTheme.typography.titleSmall)
                            Text("سهم کل: ${fmt(e.optDouble("totalShare"))} — پرداختی: ${fmt(e.optDouble("totalPaid"))}")
                            Text("مانده: ${fmt(e.optDouble("balance"))}")
                        }
                        TextButton(onClick = { statementFor = e.optString("name") }) { Text("ریز حساب") }
                    }
                }
            }
        }
    }

    if (showAdd) {
        AlertDialog(
            onDismissRequest = { showAdd = false },
            title = { Text("نیروی جدید") },
            text = { OutlinedTextField(value = newName, onValueChange = { newName = it }, label = { Text("نام نیرو") }) },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        try {
                            ApiClient.call("addEmployee", JSONObject().put("name", newName.trim()))
                            newName = ""; showAdd = false
                            load()
                        } catch (e: ApiException) { error = e.message }
                    }
                }) { Text("ثبت") }
            },
            dismissButton = { TextButton(onClick = { showAdd = false }) { Text("انصراف") } }
        )
    }

    statementFor?.let { name ->
        EmployeeStatementDialog(name = name, onDismiss = { statementFor = null })
    }
}

/**
 * ریز حساب یک نیرو: مانده‌ی کل همیشه بر اساس کل تاریخچه است، ولی فهرست فاکتورها و پرداخت‌ها
 * با محدوده‌ی تاریخ (انتخابی، نه تایپی) فیلتر می‌شود.
 */
@Composable
private fun EmployeeStatementDialog(name: String, onDismiss: () -> Unit) {
    var fromDate by remember { mutableStateOf("") }
    var toDate by remember { mutableStateOf("") }
    var statement by remember { mutableStateOf<JSONObject?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    suspend fun load() {
        loading = true
        try {
            val body = JSONObject().put("name", name)
            if (fromDate.isNotBlank()) body.put("fromDate", fromDate)
            if (toDate.isNotBlank()) body.put("toDate", toDate)
            statement = ApiClient.call("getEmployeeStatement", body)
        } catch (ex: ApiException) { error = ex.message }
        loading = false
    }
    LaunchedEffect(fromDate, toDate) { load() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("ریز حساب $name") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                JalaliDateRangeRow(
                    fromValue = fromDate, toValue = toDate,
                    onFromChange = { fromDate = it }, onToChange = { toDate = it },
                    onClear = { fromDate = ""; toDate = "" }
                )
                Spacer(Modifier.height(8.dp))
                if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

                statement?.let { st ->
                    Text("مانده‌ی کل (تمام تاریخچه)", style = MaterialTheme.typography.titleSmall)
                    Text("سهم فاکتورها: ${fmt(st.optDouble("invoiceShare"))}")
                    Text("سهم خرده‌کاری‌ها: ${fmt(st.optDouble("smallJobShare"))}")
                    Text("مجموع سهم: ${fmt(st.optDouble("totalShare"))}")
                    Text("مجموع پرداختی: ${fmt(st.optDouble("totalPaid"))}")
                    Text("مانده: ${fmt(st.optDouble("balance"))}")

                    if (st.optBoolean("hasDateFilter")) {
                        Spacer(Modifier.height(8.dp))
                        Text("در محدوده‌ی انتخاب‌شده", style = MaterialTheme.typography.titleSmall)
                        Text("سهم فاکتورها: ${fmt(st.optDouble("rangeShare"))} — سهم خرده‌کاری: ${fmt(st.optDouble("rangeSmallJobShare"))}")
                        Text("پرداختی: ${fmt(st.optDouble("rangePaid"))}")
                    }

                    Spacer(Modifier.height(12.dp))
                    Text("فاکتورها", style = MaterialTheme.typography.titleSmall)
                    val invoices = st.optJSONArray("invoices")
                    if (invoices == null || invoices.length() == 0) {
                        Text("موردی نیست.")
                    } else {
                        for (i in 0 until invoices.length()) {
                            val inv = invoices.getJSONObject(i)
                            Text(
                                "${inv.optString("full_invoice_no")} — ${fmt(inv.optDouble("total_amount"))} " +
                                    "(سهم: ${fmt(inv.optDouble("employee_share"))}) — ${inv.optString("jalali_date")}",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }

                    Spacer(Modifier.height(12.dp))
                    Text("خرده‌کاری‌ها", style = MaterialTheme.typography.titleSmall)
                    val smallJobs = st.optJSONArray("smallJobs")
                    if (smallJobs == null || smallJobs.length() == 0) {
                        Text("موردی نیست.")
                    } else {
                        for (i in 0 until smallJobs.length()) {
                            val j = smallJobs.getJSONObject(i)
                            Text(
                                "${fmt(j.optDouble("amount"))} (سهم: ${fmt(j.optDouble("employee_share"))}) — " +
                                    "${j.optString("account_name")} — ${j.optString("jalali_date")}",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }

                    Spacer(Modifier.height(12.dp))
                    Text("پرداخت‌ها", style = MaterialTheme.typography.titleSmall)
                    val payments = st.optJSONArray("payments")
                    if (payments == null || payments.length() == 0) {
                        Text("موردی نیست.")
                    } else {
                        for (i in 0 until payments.length()) {
                            val p = payments.getJSONObject(i)
                            Text(
                                "${fmt(p.optDouble("amount"))} — ${p.optString("jalali_date")}",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("بستن") } }
    )
}

fun fmt(n: Double): String {
    return "%,.0f".format(java.util.Locale.US, n)
}
