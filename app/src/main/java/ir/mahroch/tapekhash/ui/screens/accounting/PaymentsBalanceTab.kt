package ir.mahroch.tapekhash.ui.screens.accounting

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ir.mahroch.tapekhash.data.ApiClient
import ir.mahroch.tapekhash.ui.screens.khash.fmt
import org.json.JSONObject

/**
 * تب «پرداخت و مانده‌حساب»: بدهکارترین مشتری همیشه بالا نشان داده می‌شود، جستجوی نام مشتری،
 * و با لمس هر مشتری می‌توان جزئیات را دید و پرداخت جدید ثبت کرد (که از مانده‌ی حسابش کم می‌شود).
 */
@Composable
fun PaymentsBalanceTab() {
    var mostIndebted by remember { mutableStateOf<JSONObject?>(null) }
    var query by remember { mutableStateOf("") }
    var customers by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var openId by remember { mutableStateOf<Int?>(null) }

    suspend fun loadTop() {
        try {
            val res = ApiClient.call("getMostIndebtedCustomer", JSONObject())
            mostIndebted = res.optJSONObject("customer")
        } catch (e: Exception) { }
    }

    suspend fun loadList() {
        loading = true
        try {
            val body = JSONObject().put("filter", "all")
            if (query.isNotBlank()) body.put("query", query)
            val res = ApiClient.call("getAcctCustomers", body)
            val arr = res.getJSONArray("customers")
            customers = (0 until arr.length()).map { arr.getJSONObject(it) }
        } catch (e: Exception) { }
        loading = false
    }

    LaunchedEffect(Unit) { loadTop() }
    LaunchedEffect(query) { loadList() }

    openId?.let { id ->
        AcctCustomerDetailDialog(
            customerId = id, onDismiss = { openId = null },
            onChanged = { }
        )
    }
    // با بستن دیالوگ، ارقام بالای صفحه (بدهکارترین) و لیست را دوباره می‌خوانیم تا تغییرات دیده شود
    LaunchedEffect(openId) { if (openId == null) { loadTop(); loadList() } }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        mostIndebted?.let { m ->
            ElevatedCard(onClick = { openId = m.optInt("id") }, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text("بدهکارترین مشتری", style = MaterialTheme.typography.labelMedium)
                    Text(m.optString("name"), style = MaterialTheme.typography.titleLarge)
                    Text(
                        "${fmt(m.optDouble("balance"))} تومان بدهکار",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
        }

        OutlinedTextField(
            value = query, onValueChange = { query = it },
            label = { Text("جستجوی نام مشتری") }, modifier = Modifier.fillMaxWidth(), singleLine = true
        )
        Spacer(Modifier.height(8.dp))

        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(customers, key = { it.optInt("id") }) { c ->
                val balance = c.optDouble("balance", 0.0)
                Card(onClick = { openId = c.optInt("id") }, modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(c.optString("name"), style = MaterialTheme.typography.titleMedium)
                            Text(c.optString("phone"), style = MaterialTheme.typography.bodySmall)
                        }
                        Text(
                            "${fmt(balance)} تومان",
                            color = if (balance > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.titleSmall
                        )
                    }
                }
            }
        }
        if (!loading && customers.isEmpty()) {
            Spacer(Modifier.height(16.dp))
            Text("مشتری‌ای پیدا نشد.")
        }
    }
}
