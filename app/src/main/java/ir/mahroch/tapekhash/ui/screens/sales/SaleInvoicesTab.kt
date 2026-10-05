package ir.mahroch.tapekhash.ui.screens.sales

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import ir.mahroch.tapekhash.data.ApiClient
import ir.mahroch.tapekhash.data.normalizeDigits
import ir.mahroch.tapekhash.data.toNumberOrNull
import ir.mahroch.tapekhash.data.toNumber
import ir.mahroch.tapekhash.data.ApiException
import ir.mahroch.tapekhash.ui.screens.khash.JalaliDateRangeRow
import ir.mahroch.tapekhash.ui.screens.khash.fmt
import kotlinx.coroutines.launch
import org.json.JSONObject

private enum class InvoiceFilter(val value: String, val label: String) {
    ALL("all", "همه"), DEBTOR("debtor", "بدهکار")
}

/** تب «لیست فاکتورها»: فیلتر بدهکار/همه، محدوده‌ی تاریخ، و با لمس هر فاکتور جزئیات و ثبت پرداخت جدید. */
@Composable
fun SaleInvoicesTab() {
    var filter by remember { mutableStateOf(InvoiceFilter.ALL) }
    var fromDate by remember { mutableStateOf("") }
    var toDate by remember { mutableStateOf("") }
    var query by remember { mutableStateOf("") }
    var invoices by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var openInvoiceId by remember { mutableStateOf<Int?>(null) }
    val scope = rememberCoroutineScope()

    suspend fun load() {
        loading = true
        try {
            val body = JSONObject().put("filter", filter.value)
            if (query.isNotBlank()) body.put("query", query)
            if (fromDate.isNotBlank()) body.put("fromDate", fromDate)
            if (toDate.isNotBlank()) body.put("toDate", toDate)
            val res = ApiClient.call("getSaleInvoices", body)
            val arr = res.getJSONArray("invoices")
            invoices = (0 until arr.length()).map { arr.getJSONObject(it) }
        } catch (e: Exception) { }
        loading = false
    }
    LaunchedEffect(filter, fromDate, toDate, query) { load() }

    openInvoiceId?.let { id ->
        InvoiceDetailDialog(invoiceId = id, onDismiss = { openInvoiceId = null }, onChanged = { scope.launch { load() } })
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row {
            InvoiceFilter.values().forEach { f ->
                FilterChip(selected = filter == f, onClick = { filter = f }, label = { Text(f.label) })
                Spacer(Modifier.width(8.dp))
            }
        }
        Spacer(Modifier.height(8.dp))
        JalaliDateRangeRow(
            fromValue = fromDate, toValue = toDate,
            onFromChange = { fromDate = it }, onToChange = { toDate = it },
            onClear = { fromDate = ""; toDate = "" }
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = query, onValueChange = { query = it },
            label = { Text("جستجو با نام مشتری یا شماره فاکتور") }, modifier = Modifier.fillMaxWidth(), singleLine = true
        )
        Spacer(Modifier.height(8.dp))

        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(invoices, key = { it.optInt("id") }) { inv ->
                InvoiceListCard(inv, onClick = { openInvoiceId = inv.optInt("id") })
            }
        }
        if (!loading && invoices.isEmpty()) {
            Spacer(Modifier.height(24.dp))
            Text("فاکتوری پیدا نشد.")
        }
    }
}

@Composable
private fun InvoiceListCard(inv: JSONObject, onClick: () -> Unit) {
    val balance = inv.optDouble("balance")
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("فاکتور #${inv.optInt("id")} — ${inv.optString("customerName")}", style = MaterialTheme.typography.titleMedium)
                Text(inv.optString("jalaliDate"), style = MaterialTheme.typography.bodySmall)
                Text("جمع: ${fmt(inv.optDouble("totalAmount"))} تومان", style = MaterialTheme.typography.bodySmall)
            }
            Text(
                "مانده: ${fmt(balance)}",
                color = if (balance > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.titleSmall
            )
        }
    }
}

@Composable
private fun InvoiceDetailDialog(invoiceId: Int, onDismiss: () -> Unit, onChanged: () -> Unit) {
    var data by remember { mutableStateOf<JSONObject?>(null) }
    var loading by remember { mutableStateOf(true) }
    var accounts by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var payAmount by remember { mutableStateOf("") }
    var selectedAccountId by remember { mutableStateOf(0) }
    var accountExpanded by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    suspend fun load() {
        loading = true
        try {
            data = ApiClient.call("getSaleInvoiceDetail", JSONObject().put("id", invoiceId))
        } catch (e: Exception) { }
        loading = false
    }
    LaunchedEffect(invoiceId) {
        load()
        try {
            val res = ApiClient.call("getSaleAccounts", JSONObject())
            val arr = res.getJSONArray("accounts")
            accounts = (0 until arr.length()).map { arr.getJSONObject(it) }
            if (selectedAccountId == 0 && accounts.isNotEmpty()) selectedAccountId = accounts[0].optInt("id")
        } catch (e: Exception) { }
    }

    val balance = data?.optDouble("balance") ?: 0.0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("فاکتور #$invoiceId") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                data?.let { inv ->
                    Text("${inv.optString("customerName")} — ${inv.optString("customerPhone")}", style = MaterialTheme.typography.titleSmall)
                    Text(inv.optString("jalaliDate"), style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(8.dp))

                    Text("کالاها", style = MaterialTheme.typography.titleSmall)
                    val items = inv.optJSONArray("items")
                    if (items != null) for (i in 0 until items.length()) {
                        val it = items.getJSONObject(i)
                        Text(
                            "${it.optString("productName")}: ${fmt(it.optDouble("qty"))} ${it.optString("unitName")} × ${fmt(it.optDouble("unitPrice"))} = ${fmt(it.optDouble("rowTotal"))}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    Spacer(Modifier.height(8.dp))
                    Text("پرداخت‌ها", style = MaterialTheme.typography.titleSmall)
                    val pays = inv.optJSONArray("payments")
                    if (pays == null || pays.length() == 0) {
                        Text("هنوز پرداختی ثبت نشده.", style = MaterialTheme.typography.bodySmall)
                    } else for (i in 0 until pays.length()) {
                        val p = pays.getJSONObject(i)
                        Text(
                            "${fmt(p.optDouble("amount"))} — ${p.optString("accountName")} — ${p.optString("jalaliDate")}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    Spacer(Modifier.height(8.dp))
                    Text(
                        "جمع: ${fmt(inv.optDouble("totalAmount"))} — پرداخت‌شده: ${fmt(inv.optDouble("paidAmount"))} — مانده: ${fmt(balance)}",
                        color = if (balance > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodyMedium
                    )

                    if (balance > 0) {
                        Spacer(Modifier.height(12.dp))
                        Divider()
                        Spacer(Modifier.height(8.dp))
                        Text("ثبت پرداخت جدید", style = MaterialTheme.typography.titleSmall)
                        OutlinedTextField(
                            value = payAmount, onValueChange = { payAmount = it.normalizeDigits() },
                            label = { Text("مبلغ") }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                        )
                        Spacer(Modifier.height(8.dp))
                        val accName = accounts.firstOrNull { it.optInt("id") == selectedAccountId }?.optString("name") ?: "انتخاب صندوق/بانک"
                        ExposedDropdownMenuBox(expanded = accountExpanded, onExpandedChange = { accountExpanded = it }) {
                            OutlinedTextField(
                                value = accName, onValueChange = {}, readOnly = true,
                                modifier = Modifier.fillMaxWidth().menuAnchor()
                            )
                            ExposedDropdownMenu(expanded = accountExpanded, onDismissRequest = { accountExpanded = false }) {
                                accounts.forEach { a ->
                                    DropdownMenuItem(
                                        text = { Text(a.optString("name")) },
                                        onClick = { selectedAccountId = a.optInt("id"); accountExpanded = false }
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Button(
                            enabled = !saving,
                            onClick = {
                                val amt = payAmount.toNumberOrNull()
                                if (amt == null || amt <= 0) { error = "مبلغ نامعتبر است."; return@Button }
                                if (selectedAccountId == 0) { error = "صندوق/بانک را انتخاب کنید."; return@Button }
                                saving = true; error = null
                                scope.launch {
                                    try {
                                        ApiClient.call(
                                            "addSalePayment",
                                            JSONObject()
                                                .put("invoiceId", invoiceId)
                                                .put("amount", amt)
                                                .put("accountId", selectedAccountId)
                                        )
                                        payAmount = ""
                                        load()
                                        onChanged()
                                    } catch (e: ApiException) {
                                        error = e.message
                                    } catch (e: Exception) {
                                        error = "خطا در ارتباط با سرور."
                                    }
                                    saving = false
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("ثبت پرداخت") }
                    }
                }
                error?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("بستن") } }
    )
}
