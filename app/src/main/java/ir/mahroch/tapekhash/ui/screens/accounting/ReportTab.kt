package ir.mahroch.tapekhash.ui.screens.accounting

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
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

private enum class ReportSection { ENTRIES, DEBTORS }

/** تب «گزارش»: «همه‌ی ثبت‌ها» با سه جستجوی مستقل (نام/نوع چاپ/تاریخ) + جمع مبلغ، و «بدهکاران». */
@Composable
fun ReportTab() {
    var section by remember { mutableStateOf(ReportSection.ENTRIES) }

    Column(Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = section.ordinal) {
            Tab(selected = section == ReportSection.ENTRIES, onClick = { section = ReportSection.ENTRIES },
                text = { Text("همه‌ی ثبت‌ها") })
            Tab(selected = section == ReportSection.DEBTORS, onClick = { section = ReportSection.DEBTORS },
                text = { Text("بدهکاران") })
        }
        Box(Modifier.weight(1f)) {
            when (section) {
                ReportSection.ENTRIES -> EntriesReportSection()
                ReportSection.DEBTORS -> DebtorsSection()
            }
        }
    }
}

@Composable
private fun EntriesReportSection() {
    var customerName by remember { mutableStateOf("") }
    var printType by remember { mutableStateOf("") }
    var fromDate by remember { mutableStateOf("") }
    var toDate by remember { mutableStateOf("") }
    var entries by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var sumAmount by remember { mutableStateOf(0.0) }
    var loading by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()

    suspend fun load() {
        loading = true
        try {
            val body = JSONObject()
            if (customerName.isNotBlank()) body.put("customerName", customerName)
            if (printType.isNotBlank()) body.put("printType", printType)
            if (fromDate.isNotBlank()) body.put("fromDate", fromDate)
            if (toDate.isNotBlank()) body.put("toDate", toDate)
            val res = ApiClient.call("getAcctEntries", body)
            val arr = res.getJSONArray("entries")
            entries = (0 until arr.length()).map { arr.getJSONObject(it) }
            sumAmount = res.optDouble("sumAmount", 0.0)
        } catch (e: Exception) { }
        loading = false
    }
    LaunchedEffect(customerName, printType, fromDate, toDate) { load() }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        OutlinedTextField(
            value = customerName, onValueChange = { customerName = it },
            label = { Text("جستجو بر اساس نام مشتری") }, modifier = Modifier.fillMaxWidth(), singleLine = true
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = printType, onValueChange = { printType = it },
            label = { Text("جستجو بر اساس نوع چاپ") }, modifier = Modifier.fillMaxWidth(), singleLine = true
        )
        Spacer(Modifier.height(8.dp))
        JalaliDateRangeRow(
            fromValue = fromDate, toValue = toDate,
            onFromChange = { fromDate = it }, onToChange = { toDate = it },
            onClear = { fromDate = ""; toDate = "" }
        )
        Spacer(Modifier.height(8.dp))

        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())

        ElevatedCard(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("تعداد: ${entries.size}")
                Text("جمع مبلغ: ${fmt(sumAmount)} تومان", style = MaterialTheme.typography.titleSmall)
            }
        }
        Spacer(Modifier.height(8.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(entries, key = { it.optInt("itemId") }) { e ->
                EntryRowCard(e, onChanged = { scope.launch { load() } })
            }
        }
        if (!loading && entries.isEmpty()) {
            Spacer(Modifier.height(16.dp))
            Text("موردی پیدا نشد.")
        }
    }
}

@Composable
private fun EntryRowCard(e: JSONObject, onChanged: () -> Unit) {
    var showEdit by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    if (showEdit) {
        EditEntryItemDialog(entry = e, onDismiss = { showEdit = false }, onSaved = { showEdit = false; onChanged() })
    }
    if (showDeleteConfirm) {
        DeleteEntryConfirmDialog(
            itemId = e.optInt("itemId"),
            onDismiss = { showDeleteConfirm = false },
            onDeleted = { showDeleteConfirm = false; onChanged() }
        )
    }

    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("${e.optString("customerName")} — ${e.optString("printType")}", style = MaterialTheme.typography.titleSmall)
                Text(
                    "${fmt(e.optDouble("qty"))} × ${fmt(e.optDouble("unitPrice"))} = ${fmt(e.optDouble("rowTotal"))} تومان",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(e.optString("jalaliDate"), style = MaterialTheme.typography.bodySmall)
            }
            IconButton(onClick = { showEdit = true }) { Icon(Icons.Default.Edit, contentDescription = "ویرایش") }
            IconButton(onClick = { showDeleteConfirm = true }) { Icon(Icons.Default.Delete, contentDescription = "حذف") }
        }
    }
}

/** ویرایش یک ردیفِ ثبت‌شده (اگر اشتباه تایپ شده بود): نوع چاپ، تعداد، مبلغ فی. */
@Composable
private fun EditEntryItemDialog(entry: JSONObject, onDismiss: () -> Unit, onSaved: () -> Unit) {
    var printType by remember { mutableStateOf(entry.optString("printType")) }
    var qty by remember { mutableStateOf(fmt(entry.optDouble("qty"))) }
    var unitPrice by remember { mutableStateOf(fmt(entry.optDouble("unitPrice"))) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("ویرایش ثبت") },
        text = {
            Column {
                OutlinedTextField(
                    value = printType, onValueChange = { printType = it },
                    label = { Text("نوع چاپ") }, modifier = Modifier.fillMaxWidth(), singleLine = true
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = qty, onValueChange = { qty = it.normalizeDigits() },
                    label = { Text("تعداد") }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = unitPrice, onValueChange = { unitPrice = it.normalizeDigits() },
                    label = { Text("مبلغ فی") }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                error?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !saving,
                onClick = {
                    val qtyVal = qty.toNumberOrNull()
                    val priceVal = unitPrice.toNumberOrNull()
                    if (printType.isBlank()) { error = "نوع چاپ را وارد کنید."; return@TextButton }
                    if (qtyVal == null || qtyVal <= 0) { error = "تعداد نامعتبر است."; return@TextButton }
                    if (priceVal == null || priceVal < 0) { error = "مبلغ فی نامعتبر است."; return@TextButton }

                    saving = true; error = null
                    scope.launch {
                        try {
                            ApiClient.call(
                                "updateAcctEntryItem",
                                JSONObject()
                                    .put("itemId", entry.optInt("itemId"))
                                    .put("printType", printType.trim())
                                    .put("qty", qtyVal)
                                    .put("unitPrice", priceVal)
                            )
                            onSaved()
                        } catch (e: ApiException) {
                            error = e.message
                        } catch (e: Exception) {
                            error = "خطا در ارتباط با سرور."
                        }
                        saving = false
                    }
                }
            ) { Text("ذخیره") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } }
    )
}

@Composable
private fun DeleteEntryConfirmDialog(itemId: Int, onDismiss: () -> Unit, onDeleted: () -> Unit) {
    var deleting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("حذف این ثبت") },
        text = {
            Column {
                Text("این ردیف برای همیشه حذف می‌شود. مطمئن هستید؟")
                error?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !deleting,
                onClick = {
                    deleting = true; error = null
                    scope.launch {
                        try {
                            ApiClient.call("deleteAcctEntryItem", JSONObject().put("itemId", itemId))
                            onDeleted()
                        } catch (e: ApiException) {
                            error = e.message
                        } catch (e: Exception) {
                            error = "خطا در ارتباط با سرور."
                        }
                        deleting = false
                    }
                }
            ) { Text("حذف", color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } }
    )
}

@Composable
private fun DebtorsSection() {
    var query by remember { mutableStateOf("") }
    var customers by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var openId by remember { mutableStateOf<Int?>(null) }
    val scope = rememberCoroutineScope()

    suspend fun load() {
        loading = true
        try {
            val body = JSONObject().put("filter", "debtor")
            if (query.isNotBlank()) body.put("query", query)
            val res = ApiClient.call("getAcctCustomers", body)
            val arr = res.getJSONArray("customers")
            customers = (0 until arr.length()).map { arr.getJSONObject(it) }
        } catch (e: Exception) { }
        loading = false
    }
    LaunchedEffect(query) { load() }

    openId?.let { id ->
        AcctCustomerDetailDialog(customerId = id, onDismiss = { openId = null }, onChanged = { scope.launch { load() } })
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        OutlinedTextField(
            value = query, onValueChange = { query = it },
            label = { Text("جستجوی نام مشتری") }, modifier = Modifier.fillMaxWidth(), singleLine = true
        )
        Spacer(Modifier.height(8.dp))

        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(customers, key = { it.optInt("id") }) { c ->
                Card(onClick = { openId = c.optInt("id") }, modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(c.optString("name"), style = MaterialTheme.typography.titleMedium)
                            Text("تعداد ثبت: ${c.optInt("invoiceCount")}", style = MaterialTheme.typography.bodySmall)
                        }
                        Text(
                            "${fmt(c.optDouble("balance"))} تومان",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.titleSmall
                        )
                    }
                }
            }
        }
        if (!loading && customers.isEmpty()) {
            Spacer(Modifier.height(16.dp))
            Text("بدهکاری ثبت نشده است.")
        }
    }
}
