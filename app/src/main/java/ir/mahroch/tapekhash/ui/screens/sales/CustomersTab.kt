package ir.mahroch.tapekhash.ui.screens.sales

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
import ir.mahroch.tapekhash.ui.screens.khash.fmt
import kotlinx.coroutines.launch
import org.json.JSONObject

private enum class CustomerFilter(val value: String, val label: String) {
    ALL("all", "همه‌ی مشتری‌ها"), DEBTOR("debtor", "بدهکار")
}

/** تب «مشتری‌ها»: فیلتر بدهکار/همه، و با لمس هر مشتری، تمام فاکتورهایش نمایش داده می‌شود. */
@Composable
fun CustomersTab() {
    var filter by remember { mutableStateOf(CustomerFilter.ALL) }
    var query by remember { mutableStateOf("") }
    var customers by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var openCustomerId by remember { mutableStateOf<Int?>(null) }
    val scope = rememberCoroutineScope()

    suspend fun load() {
        loading = true
        try {
            val body = JSONObject().put("filter", filter.value)
            if (query.isNotBlank()) body.put("query", query)
            val res = ApiClient.call("getSaleCustomers", body)
            val arr = res.getJSONArray("customers")
            customers = (0 until arr.length()).map { arr.getJSONObject(it) }
        } catch (e: Exception) { }
        loading = false
    }
    LaunchedEffect(filter, query) { load() }

    openCustomerId?.let { id ->
        CustomerInvoicesDialog(customerId = id, onDismiss = { openCustomerId = null })
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row {
            CustomerFilter.values().forEach { f ->
                FilterChip(selected = filter == f, onClick = { filter = f }, label = { Text(f.label) })
                Spacer(Modifier.width(8.dp))
            }
        }
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = query, onValueChange = { query = it },
            label = { Text("جستجوی نام یا شماره تماس") }, modifier = Modifier.fillMaxWidth(), singleLine = true
        )
        Spacer(Modifier.height(8.dp))

        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(customers, key = { it.optInt("id") }) { c ->
                CustomerCard(c, onClick = { openCustomerId = c.optInt("id") })
            }
        }
        if (!loading && customers.isEmpty()) {
            Spacer(Modifier.height(24.dp))
            Text("مشتری‌ای پیدا نشد.")
        }
    }
}

@Composable
private fun CustomerCard(c: JSONObject, onClick: () -> Unit) {
    val balance = c.optDouble("balance", 0.0)
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(c.optString("name"), style = MaterialTheme.typography.titleMedium)
                Text(c.optString("phone"), style = MaterialTheme.typography.bodySmall)
                Text("تعداد فاکتور: ${c.optInt("invoiceCount")}", style = MaterialTheme.typography.bodySmall)
            }
            Text(
                "${fmt(balance)} تومان",
                color = if (balance > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.titleSmall
            )
        }
    }
}

@Composable
private fun CustomerInvoicesDialog(customerId: Int, onDismiss: () -> Unit) {
    var data by remember { mutableStateOf<JSONObject?>(null) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(customerId) {
        try {
            data = ApiClient.call("getCustomerInvoices", JSONObject().put("customerId", customerId))
        } catch (e: Exception) { }
        loading = false
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(data?.optString("customerName") ?: "فاکتورهای مشتری") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                val invoices = data?.optJSONArray("invoices")
                if (invoices == null || invoices.length() == 0) {
                    if (!loading) Text("این مشتری هنوز فاکتوری ندارد.")
                } else {
                    for (i in 0 until invoices.length()) {
                        val inv = invoices.getJSONObject(i)
                        val balance = inv.optDouble("balance")
                        Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            Column(Modifier.padding(10.dp)) {
                                Text("فاکتور #${inv.optInt("id")} — ${inv.optString("jalaliDate")}", style = MaterialTheme.typography.titleSmall)
                                val items = inv.optJSONArray("items")
                                if (items != null) {
                                    for (j in 0 until items.length()) {
                                        val it = items.getJSONObject(j)
                                        Text(
                                            "${it.optString("productName")}: ${fmt(it.optDouble("qty"))} ${it.optString("unitName")} × ${fmt(it.optDouble("unitPrice"))} = ${fmt(it.optDouble("rowTotal"))}",
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                }
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "جمع: ${fmt(inv.optDouble("totalAmount"))} — پرداخت‌شده: ${fmt(inv.optDouble("paidAmount"))} — مانده: ${fmt(balance)}",
                                    color = if (balance > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("بستن") } }
    )
}
