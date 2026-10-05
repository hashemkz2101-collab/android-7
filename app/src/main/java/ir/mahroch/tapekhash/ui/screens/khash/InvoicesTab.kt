package ir.mahroch.tapekhash.ui.screens.khash

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InvoicesTab() {
    var code by remember { mutableStateOf("") }
    var row by remember { mutableStateOf("") }
    var similar by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var jalaliDate by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var employeeNames by remember { mutableStateOf<List<String>>(emptyList()) }
    var selectedEmployee by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }
    var preview by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    var query by remember { mutableStateOf("") }
    var invoices by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var filterEmployee by remember { mutableStateOf("") } // خالی = همه‌ی نیروها
    var filterExpanded by remember { mutableStateOf(false) }
    var fromDate by remember { mutableStateOf("") }
    var toDate by remember { mutableStateOf("") }

    val scope = rememberCoroutineScope()

    suspend fun loadInvoices() {
        try {
            val body = JSONObject()
            if (query.isNotBlank()) body.put("query", query)
            if (filterEmployee.isNotBlank()) body.put("employeeName", filterEmployee)
            if (fromDate.isNotBlank()) body.put("fromDate", fromDate)
            if (toDate.isNotBlank()) body.put("toDate", toDate)
            val res = ApiClient.call("getInvoices", body)
            val arr = res.getJSONArray("invoices")
            invoices = (0 until arr.length()).map { arr.getJSONObject(it) }
        } catch (e: Exception) { }
    }

    LaunchedEffect(filterEmployee, fromDate, toDate) { loadInvoices() }

    LaunchedEffect(Unit) {
        try {
            val res = ApiClient.call("getActiveEmployeeNames")
            val arr = res.getJSONArray("names")
            employeeNames = (0 until arr.length()).map { arr.getString(it) }
            if (employeeNames.isNotEmpty()) selectedEmployee = employeeNames[0]
        } catch (e: Exception) { }
        loadInvoices()
    }

    LaunchedEffect(code, row, similar) {
        preview = ""
        if (code.isNotBlank() && row.isNotBlank() && similar.isNotBlank()) {
            try {
                val res = ApiClient.call("buildInvoiceNumberPreview", JSONObject().put("code", code).put("row", row).put("similar", similar))
                preview = res.optString("fullNumber")
            } catch (e: Exception) { }
        }
    }

    fun suggestNext() {
        if (code.isBlank()) return
        scope.launch {
            try {
                val res = ApiClient.call("suggestNextInvoiceNumber", JSONObject().put("code", code))
                row = res.optInt("row").toString()
                similar = res.optInt("similar").toString()
            } catch (e: Exception) { }
        }
    }

    fun submit() {
        error = null; message = null
        if (code.isBlank() || row.isBlank() || similar.isBlank() || amount.isBlank() || selectedEmployee.isBlank()) {
            error = "همه‌ی فیلدهای الزامی را پر کنید."
            return
        }
        scope.launch {
            try {
                val body = JSONObject()
                    .put("code", code).put("row", row).put("similar", similar)
                    .put("amount", amount).put("employeeName", selectedEmployee)
                    .put("description", description)
                if (jalaliDate.isNotBlank()) body.put("jalaliDate", jalaliDate)
                val res = ApiClient.call("createInvoice", body)
                message = "فاکتور ${res.optString("fullNumber")} ثبت شد. سهم نیرو: ${fmt(res.optDouble("share"))}"
                code = ""; row = ""; similar = ""; amount = ""; description = ""; jalaliDate = ""
                loadInvoices()
            } catch (e: ApiException) { error = e.message }
        }
    }

    Column(Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
        Text("ثبت فاکتور جدید", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))

        Row {
            OutlinedTextField(value = code, onValueChange = { code = it }, label = { Text("کد فاکتور") }, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            OutlinedTextField(value = row, onValueChange = { row = it }, label = { Text("ردیف") }, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            OutlinedTextField(value = similar, onValueChange = { similar = it }, label = { Text("مشابه") }, modifier = Modifier.weight(1f))
        }
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Text("شماره کامل: ${preview.ifBlank { "—" }}", modifier = Modifier.weight(1f))
            TextButton(onClick = { suggestNext() }) { Text("پیشنهاد شماره بعدی") }
        }

        Spacer(Modifier.height(8.dp))
        OutlinedTextField(value = amount, onValueChange = { amount = it }, label = { Text("مبلغ کل") }, modifier = Modifier.fillMaxWidth())

        Spacer(Modifier.height(8.dp))
        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
            OutlinedTextField(
                value = selectedEmployee, onValueChange = {}, readOnly = true,
                label = { Text("نیرو") }, modifier = Modifier.fillMaxWidth().menuAnchor()
            )
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                employeeNames.forEach { name ->
                    DropdownMenuItem(text = { Text(name) }, onClick = { selectedEmployee = name; expanded = false })
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = jalaliDate, onValueChange = { jalaliDate = it },
            label = { Text("تاریخ شمسی (خالی = امروز) مثل 1405/07/03") }, modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(value = description, onValueChange = { description = it }, label = { Text("توضیحات") }, modifier = Modifier.fillMaxWidth())

        error?.let { Spacer(Modifier.height(8.dp)); Text(it, color = MaterialTheme.colorScheme.error) }
        message?.let { Spacer(Modifier.height(8.dp)); Text(it, color = MaterialTheme.colorScheme.primary) }

        Spacer(Modifier.height(12.dp))
        Button(onClick = { submit() }, modifier = Modifier.fillMaxWidth()) { Text("ثبت فاکتور") }

        Spacer(Modifier.height(24.dp))
        Divider()
        Spacer(Modifier.height(12.dp))
        Text("لیست فاکتورها", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))

        ExposedDropdownMenuBox(expanded = filterExpanded, onExpandedChange = { filterExpanded = it }) {
            OutlinedTextField(
                value = filterEmployee.ifBlank { "همه‌ی نیروها" }, onValueChange = {}, readOnly = true,
                label = { Text("فیلتر نیرو") }, modifier = Modifier.fillMaxWidth().menuAnchor()
            )
            ExposedDropdownMenu(expanded = filterExpanded, onDismissRequest = { filterExpanded = false }) {
                DropdownMenuItem(text = { Text("همه‌ی نیروها") }, onClick = { filterEmployee = ""; filterExpanded = false })
                employeeNames.forEach { name ->
                    DropdownMenuItem(text = { Text(name) }, onClick = { filterEmployee = name; filterExpanded = false })
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        JalaliDateRangeRow(
            fromValue = fromDate, toValue = toDate,
            onFromChange = { fromDate = it }, onToChange = { toDate = it },
            onClear = { fromDate = ""; toDate = "" }
        )
        Spacer(Modifier.height(8.dp))

        Row {
            OutlinedTextField(value = query, onValueChange = { query = it }, label = { Text("جستجو با شماره فاکتور") }, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            Button(onClick = { scope.launch { loadInvoices() } }) { Text("جستجو") }
        }
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
    }
}
