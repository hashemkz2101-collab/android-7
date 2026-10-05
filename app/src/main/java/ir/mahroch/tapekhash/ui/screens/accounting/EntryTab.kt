package ir.mahroch.tapekhash.ui.screens.accounting

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
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
import ir.mahroch.tapekhash.ui.screens.khash.JalaliDate
import ir.mahroch.tapekhash.ui.screens.khash.JalaliDatePickerDialog
import ir.mahroch.tapekhash.ui.screens.khash.fmt
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

private class PrintRowState(printType: String = "", qty: String = "1", unitPrice: String = "") {
    var printType by mutableStateOf(printType)
    var qty by mutableStateOf(qty)
    var unitPrice by mutableStateOf(unitPrice)
    fun rowTotal(): Double = (qty.toNumberOrNull() ?: 0.0) * (unitPrice.toNumberOrNull() ?: 0.0)
}

/**
 * تب «ثبت چاپ مستقیم در کارگاه»: مشتری (موجود یا جدید)، چند ردیف با نوع چاپ آزاد (دستی تایپ می‌شود)،
 * تعداد و مبلغ فی، جمعِ هر ردیف و جمع کل، و تاریخ ثبت (با انتخاب، نه تایپ).
 */
@Composable
fun EntryTab() {
    var customers by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var newCustomerMode by remember { mutableStateOf(false) }
    var selectedCustomerId by remember { mutableStateOf(0) }
    var customerExpanded by remember { mutableStateOf(false) }
    var newCustomerName by remember { mutableStateOf("") }
    var newCustomerPhone by remember { mutableStateOf("") }

    val rows = remember { mutableStateListOf(PrintRowState()) }
    var entryDate by remember { mutableStateOf<JalaliDate?>(null) }
    var showDatePicker by remember { mutableStateOf(false) }
    var description by remember { mutableStateOf("") }

    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var success by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    suspend fun loadCustomers() {
        try {
            val res = ApiClient.call("getAcctCustomers", JSONObject().put("filter", "all"))
            val arr = res.getJSONArray("customers")
            customers = (0 until arr.length()).map { arr.getJSONObject(it) }
        } catch (e: Exception) { }
    }
    LaunchedEffect(Unit) { loadCustomers() }

    fun resetForm() {
        rows.clear(); rows.add(PrintRowState())
        newCustomerMode = false; selectedCustomerId = 0
        newCustomerName = ""; newCustomerPhone = ""
        description = ""; entryDate = null
    }

    val total = rows.sumOf { it.rowTotal() }

    if (showDatePicker) {
        JalaliDatePickerDialog(
            initial = entryDate,
            onDismiss = { showDatePicker = false },
            onConfirm = { entryDate = it; showDatePicker = false }
        )
    }

    Column(Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
        Text("مشتری", style = MaterialTheme.typography.titleMedium)
        Row {
            FilterChip(selected = !newCustomerMode, onClick = { newCustomerMode = false }, label = { Text("انتخاب مشتری") })
            Spacer(Modifier.width(8.dp))
            FilterChip(selected = newCustomerMode, onClick = { newCustomerMode = true }, label = { Text("مشتری جدید") })
        }
        Spacer(Modifier.height(8.dp))

        if (newCustomerMode) {
            OutlinedTextField(
                value = newCustomerName, onValueChange = { newCustomerName = it },
                label = { Text("نام مشتری") }, modifier = Modifier.fillMaxWidth(), singleLine = true
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = newCustomerPhone, onValueChange = { newCustomerPhone = it },
                label = { Text("شماره تماس") }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
            )
        } else {
            val selectedName = customers.firstOrNull { it.optInt("id") == selectedCustomerId }
                ?.let { "${it.optString("name")} (${it.optString("phone")})" } ?: "انتخاب مشتری"
            ExposedDropdownMenuBox(expanded = customerExpanded, onExpandedChange = { customerExpanded = it }) {
                OutlinedTextField(
                    value = selectedName, onValueChange = {}, readOnly = true,
                    modifier = Modifier.fillMaxWidth().menuAnchor()
                )
                ExposedDropdownMenu(expanded = customerExpanded, onDismissRequest = { customerExpanded = false }) {
                    customers.forEach { c ->
                        DropdownMenuItem(
                            text = { Text("${c.optString("name")} (${c.optString("phone")})") },
                            onClick = { selectedCustomerId = c.optInt("id"); customerExpanded = false }
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(20.dp))
        Text("ردیف‌های چاپ", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))

        rows.forEachIndexed { index, row ->
            PrintRowCard(row = row, onRemove = if (rows.size > 1) { { rows.removeAt(index) } } else null)
            Spacer(Modifier.height(8.dp))
        }
        OutlinedButton(onClick = { rows.add(PrintRowState()) }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(Modifier.width(4.dp))
            Text("افزودن ردیف")
        }

        Spacer(Modifier.height(16.dp))
        Divider()
        Spacer(Modifier.height(12.dp))
        Text("جمع کل: ${fmt(total)} تومان", style = MaterialTheme.typography.titleMedium)

        Spacer(Modifier.height(12.dp))
        OutlinedButton(onClick = { showDatePicker = true }, modifier = Modifier.fillMaxWidth()) {
            Text("تاریخ ثبت: ${entryDate?.toDisplayString() ?: "امروز"}")
        }
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = description, onValueChange = { description = it },
            label = { Text("توضیحات (اختیاری)") }, modifier = Modifier.fillMaxWidth()
        )

        error?.let { Spacer(Modifier.height(8.dp)); Text(it, color = MaterialTheme.colorScheme.error) }
        success?.let { Spacer(Modifier.height(8.dp)); Text(it, color = MaterialTheme.colorScheme.primary) }

        Spacer(Modifier.height(16.dp))
        Button(
            enabled = !saving,
            onClick = {
                error = null; success = null
                if (!newCustomerMode && selectedCustomerId == 0) { error = "مشتری را انتخاب کنید."; return@Button }
                if (newCustomerMode && newCustomerName.isBlank()) { error = "نام مشتری جدید را وارد کنید."; return@Button }
                val validRows = rows.filter { it.printType.isNotBlank() }
                if (validRows.isEmpty()) { error = "حداقل یک ردیف را کامل کنید."; return@Button }
                for (r in validRows) {
                    if ((r.qty.toNumberOrNull() ?: 0.0) <= 0) { error = "تعداد همه‌ی ردیف‌ها باید بیشتر از صفر باشد."; return@Button }
                    if (r.unitPrice.toNumberOrNull() == null) { error = "مبلغ فی همه‌ی ردیف‌ها را وارد کنید."; return@Button }
                }

                saving = true
                scope.launch {
                    try {
                        val itemsArr = JSONArray()
                        validRows.forEach { r ->
                            itemsArr.put(
                                JSONObject()
                                    .put("printType", r.printType.trim())
                                    .put("qty", r.qty.toNumber())
                                    .put("unitPrice", r.unitPrice.toNumber())
                            )
                        }
                        val body = JSONObject().put("items", itemsArr).put("description", description)
                        if (newCustomerMode) {
                            body.put("newCustomerName", newCustomerName.trim())
                            body.put("newCustomerPhone", newCustomerPhone.trim())
                        } else {
                            body.put("customerId", selectedCustomerId)
                        }
                        entryDate?.let { body.put("jalaliDate", it.toApiString()) }

                        val res = ApiClient.call("createAcctEntry", body)
                        success = "ثبت شد — جمع: ${fmt(res.optDouble("totalAmount"))} تومان"
                        resetForm()
                        loadCustomers()
                    } catch (e: ApiException) {
                        error = e.message
                    } catch (e: Exception) {
                        error = "خطا در ارتباط با سرور."
                    }
                    saving = false
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text(if (saving) "در حال ثبت..." else "ثبت") }
    }
}

@Composable
private fun PrintRowCard(row: PrintRowState, onRemove: (() -> Unit)?) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = row.printType, onValueChange = { row.printType = it },
                    label = { Text("نوع چاپ") }, modifier = Modifier.weight(1f), singleLine = true
                )
                if (onRemove != null) {
                    IconButton(onClick = onRemove) { Icon(Icons.Default.Delete, contentDescription = "حذف ردیف") }
                }
            }
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = row.qty, onValueChange = { row.qty = it.normalizeDigits() },
                    label = { Text("تعداد") }, modifier = Modifier.weight(1f), singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                Spacer(Modifier.width(8.dp))
                OutlinedTextField(
                    value = row.unitPrice, onValueChange = { row.unitPrice = it.normalizeDigits() },
                    label = { Text("مبلغ فی") }, modifier = Modifier.weight(1f), singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
            }
            Spacer(Modifier.height(4.dp))
            Text("جمع ردیف: ${fmt(row.rowTotal())} تومان", style = MaterialTheme.typography.bodySmall)
        }
    }
}
