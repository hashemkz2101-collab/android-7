package ir.mahroch.tapekhash.ui.screens.khash

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ir.mahroch.tapekhash.data.ApiClient
import ir.mahroch.tapekhash.data.ApiException
import kotlinx.coroutines.launch
import org.json.JSONObject

private val STEP_LABELS = listOf(
    "sent_from_khash" to "ارسال از خاش",
    "received_from_khash" to "دریافت از خاش",
    "printed_iranshahr" to "چاپ در ایرانشهر",
    "sent_iranshahr_to_khash" to "ارسال به خاش"
)

@Composable
fun OrdersTab() {
    var code by remember { mutableStateOf("") }
    var row by remember { mutableStateOf("") }
    var similar by remember { mutableStateOf("") }
    var preview by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var filter by remember { mutableStateOf("all") }
    var orders by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var searchQuery by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    suspend fun loadOrders() {
        try {
            val res = ApiClient.call("getKhashOrders", JSONObject().put("statusFilter", filter))
            val arr = res.getJSONArray("orders")
            orders = (0 until arr.length()).map { arr.getJSONObject(it) }
        } catch (e: Exception) { }
    }

    suspend fun searchOrders() {
        try {
            val res = ApiClient.call("searchKhashOrders", JSONObject().put("query", searchQuery))
            val arr = res.getJSONArray("orders")
            orders = (0 until arr.length()).map { arr.getJSONObject(it) }
        } catch (e: Exception) { }
    }

    // با تایپ در جستجو، لیست بر اساس نتیجه‌ی جستجو می‌شود؛ با پاک‌کردنش، به فیلتر وضعیت برمی‌گردد.
    LaunchedEffect(searchQuery, filter) {
        if (searchQuery.isBlank()) loadOrders() else searchOrders()
    }

    LaunchedEffect(code, row, similar) {
        preview = ""
        if (code.isNotBlank() && row.isNotBlank() && similar.isNotBlank()) {
            try {
                val res = ApiClient.call("buildKhashNumberPreview", JSONObject().put("code", code).put("row", row).put("similar", similar))
                preview = res.optString("fullNumber")
            } catch (e: Exception) { }
        }
    }

    fun suggestNext() {
        if (code.isBlank()) return
        scope.launch {
            try {
                val res = ApiClient.call("suggestNextKhashNumber", JSONObject().put("code", code))
                row = res.optInt("row").toString()
                similar = res.optInt("similar").toString()
            } catch (e: Exception) { }
        }
    }

    fun submit() {
        error = null; message = null
        if (code.isBlank() || row.isBlank() || similar.isBlank()) {
            error = "کد، ردیف و شماره مشابه را وارد کنید."
            return
        }
        scope.launch {
            try {
                val res = ApiClient.call("createKhashOrder", JSONObject().put("code", code).put("row", row).put("similar", similar))
                message = "سفارش ${res.optString("fullNumber")} ثبت شد."
                code = ""; row = ""; similar = ""
                loadOrders()
            } catch (e: ApiException) { error = e.message }
        }
    }

    fun toggleStep(order: JSONObject, field: String, current: Boolean) {
        scope.launch {
            try {
                ApiClient.call(
                    "updateKhashStatus",
                    JSONObject().put("orderNo", order.optString("order_no")).put("field", field).put("value", !current)
                )
                if (searchQuery.isBlank()) loadOrders() else searchOrders()
            } catch (e: ApiException) { error = e.message }
        }
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("ثبت سفارش جدید خاش", style = MaterialTheme.typography.titleMedium)
        Row {
            OutlinedTextField(value = code, onValueChange = { code = it }, label = { Text("کد") }, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            OutlinedTextField(value = row, onValueChange = { row = it }, label = { Text("ردیف") }, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            OutlinedTextField(value = similar, onValueChange = { similar = it }, label = { Text("مشابه") }, modifier = Modifier.weight(1f))
        }
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Text("شماره: ${preview.ifBlank { "—" }}", modifier = Modifier.weight(1f))
            TextButton(onClick = { suggestNext() }) { Text("پیشنهاد بعدی") }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
        Button(onClick = { submit() }, modifier = Modifier.fillMaxWidth()) { Text("ثبت سفارش") }

        Spacer(Modifier.height(16.dp))
        Divider()
        Spacer(Modifier.height(8.dp))
        Text("جستجوی فاکتور برای چک یا تغییر وضعیت", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            value = searchQuery, onValueChange = { searchQuery = it },
            label = { Text("شماره فاکتور، کد، ردیف یا تاریخ") }, modifier = Modifier.fillMaxWidth(), singleLine = true
        )

        Spacer(Modifier.height(12.dp))
        Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
            listOf(
                "all" to "همه", "pending_receive" to "منتظر دریافت", "pending_print" to "منتظر چاپ",
                "pending_send_back" to "منتظر ارسال", "completed" to "تکمیل‌شده"
            ).forEach { (key, label) ->
                FilterChip(selected = filter == key, onClick = { filter = key }, label = { Text(label) }, modifier = Modifier.padding(end = 6.dp))
            }
        }

        Spacer(Modifier.height(8.dp))
        Box(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Column {
                orders.forEach { o ->
                    ElevatedCard(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Column(Modifier.padding(10.dp)) {
                            Text(o.optString("order_no"), style = MaterialTheme.typography.titleSmall)
                            Text("تاریخ: ${o.optString("jalali_date")}")
                            Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                                STEP_LABELS.forEach { (field, label) ->
                                    val current = o.optBoolean(field)
                                    FilterChip(
                                        selected = current,
                                        onClick = { toggleStep(o, field, current) },
                                        label = { Text(label) },
                                        modifier = Modifier.padding(end = 6.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
