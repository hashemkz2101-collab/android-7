package ir.mahroch.tapekhash.ui.screens.sales

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
import ir.mahroch.tapekhash.data.plainNumber
import ir.mahroch.tapekhash.data.ApiException
import ir.mahroch.tapekhash.ui.screens.khash.JalaliDate
import ir.mahroch.tapekhash.ui.screens.khash.JalaliDatePickerDialog
import ir.mahroch.tapekhash.ui.screens.khash.fmt
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

private class InvoiceRowState(
    productId: Int = 0, productName: String = "", unitName: String = "",
    qty: String = "1", unitPrice: String = ""
) {
    var productId by mutableStateOf(productId)
    var productName by mutableStateOf(productName)
    var unitName by mutableStateOf(unitName)
    var qty by mutableStateOf(qty)
    var unitPrice by mutableStateOf(unitPrice)

    fun rowTotal(): Double = (qty.toNumberOrNull() ?: 0.0) * (unitPrice.toNumberOrNull() ?: 0.0)
}

/**
 * تب «فاکتور فروش»: انتخاب مشتری (یا افزودن مشتری جدید)، افزودن چند ردیف کالا با تعداد و مبلغ فی
 * قابل‌اصلاح، جمع کل، مبلغ پرداختی با انتخاب صندوق/بانک، و مانده‌ی حساب.
 * با ثبت فاکتور، موجودی کالاهایی که موجودی محدود دارند در سرور خودکار کم می‌شود.
 */
@Composable
fun SaleInvoiceTab() {
    var customers by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var products by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var accounts by remember { mutableStateOf<List<JSONObject>>(emptyList()) }

    var newCustomerMode by remember { mutableStateOf(false) }
    var selectedCustomerId by remember { mutableStateOf(0) }
    var customerExpanded by remember { mutableStateOf(false) }
    var newCustomerName by remember { mutableStateOf("") }
    var newCustomerPhone by remember { mutableStateOf("") }

    val rows = remember { mutableStateListOf(InvoiceRowState()) }

    var paidAmount by remember { mutableStateOf("") }
    var selectedAccountId by remember { mutableStateOf(0) }
    var accountExpanded by remember { mutableStateOf(false) }
    var showAddAccount by remember { mutableStateOf(false) }

    var invoiceDate by remember { mutableStateOf<JalaliDate?>(null) }
    var showDatePicker by remember { mutableStateOf(false) }
    var description by remember { mutableStateOf("") }

    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var success by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    suspend fun loadAll() {
        try {
            val c = ApiClient.call("getSaleCustomers", JSONObject().put("filter", "all"))
            val cArr = c.getJSONArray("customers")
            customers = (0 until cArr.length()).map { cArr.getJSONObject(it) }
        } catch (e: Exception) { }
        try {
            val p = ApiClient.call("getSaleProducts", JSONObject())
            val pArr = p.getJSONArray("products")
            products = (0 until pArr.length()).map { pArr.getJSONObject(it) }
        } catch (e: Exception) { }
        try {
            val a = ApiClient.call("getSaleAccounts", JSONObject())
            val aArr = a.getJSONArray("accounts")
            accounts = (0 until aArr.length()).map { aArr.getJSONObject(it) }
            if (selectedAccountId == 0 && accounts.isNotEmpty()) selectedAccountId = accounts[0].optInt("id")
        } catch (e: Exception) { }
    }
    LaunchedEffect(Unit) { loadAll() }

    fun resetForm() {
        rows.clear(); rows.add(InvoiceRowState())
        newCustomerMode = false; selectedCustomerId = 0
        newCustomerName = ""; newCustomerPhone = ""
        paidAmount = ""; description = ""; invoiceDate = null
    }

    val total = rows.sumOf { it.rowTotal() }
    val balance = total - (paidAmount.toNumberOrNull() ?: 0.0)

    if (showAddAccount) {
        AddAccountDialog(
            onDismiss = { showAddAccount = false },
            onAdded = { id -> selectedAccountId = id; showAddAccount = false; scope.launch { loadAll() } }
        )
    }
    if (showDatePicker) {
        JalaliDatePickerDialog(
            initial = invoiceDate,
            onDismiss = { showDatePicker = false },
            onConfirm = { invoiceDate = it; showDatePicker = false }
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
        Text("کالاها", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))

        rows.forEachIndexed { index, row ->
            InvoiceRowCard(
                row = row, products = products,
                onRemove = if (rows.size > 1) { { rows.removeAt(index) } } else null
            )
            Spacer(Modifier.height(8.dp))
        }
        OutlinedButton(onClick = { rows.add(InvoiceRowState()) }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(Modifier.width(4.dp))
            Text("افزودن ردیف کالا")
        }

        Spacer(Modifier.height(20.dp))
        Divider()
        Spacer(Modifier.height(12.dp))
        Text("جمع کل: ${fmt(total)} تومان", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = paidAmount, onValueChange = { paidAmount = it.normalizeDigits() },
            label = { Text("مبلغ پرداختی") }, modifier = Modifier.fillMaxWidth(), singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )
        Spacer(Modifier.height(8.dp))

        if (paidAmount.toNumberOrNull()?.let { it > 0 } == true) {
            val accName = accounts.firstOrNull { it.optInt("id") == selectedAccountId }?.optString("name") ?: "انتخاب صندوق/بانک"
            ExposedDropdownMenuBox(expanded = accountExpanded, onExpandedChange = { accountExpanded = it }) {
                OutlinedTextField(
                    value = accName, onValueChange = {}, readOnly = true,
                    label = { Text("پرداخت به صندوق/بانک") }, modifier = Modifier.fillMaxWidth().menuAnchor()
                )
                ExposedDropdownMenu(expanded = accountExpanded, onDismissRequest = { accountExpanded = false }) {
                    accounts.forEach { a ->
                        DropdownMenuItem(
                            text = { Text(a.optString("name")) },
                            onClick = { selectedAccountId = a.optInt("id"); accountExpanded = false }
                        )
                    }
                    Divider()
                    DropdownMenuItem(
                        text = { Text("+ افزودن صندوق یا بانک جدید") },
                        onClick = { accountExpanded = false; showAddAccount = true }
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        Text(
            "مانده حساب: ${fmt(balance)} تومان",
            style = MaterialTheme.typography.titleMedium,
            color = if (balance > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
        )

        Spacer(Modifier.height(12.dp))
        OutlinedButton(onClick = { showDatePicker = true }, modifier = Modifier.fillMaxWidth()) {
            Text("تاریخ فاکتور: ${invoiceDate?.toDisplayString() ?: "امروز"}")
        }
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = description, onValueChange = { description = it },
            label = { Text("توضیحات (اختیاری)") }, modifier = Modifier.fillMaxWidth()
        )

        error?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = MaterialTheme.colorScheme.error)
        }
        success?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = MaterialTheme.colorScheme.primary)
        }

        Spacer(Modifier.height(16.dp))
        Button(
            enabled = !saving,
            onClick = {
                error = null; success = null
                if (!newCustomerMode && selectedCustomerId == 0) { error = "مشتری را انتخاب کنید."; return@Button }
                if (newCustomerMode && newCustomerName.isBlank()) { error = "نام مشتری جدید را وارد کنید."; return@Button }
                val validRows = rows.filter { it.productId != 0 }
                if (validRows.isEmpty()) { error = "حداقل یک ردیف کالا را کامل کنید."; return@Button }
                for (r in validRows) {
                    if ((r.qty.toNumberOrNull() ?: 0.0) <= 0) { error = "تعداد همه‌ی ردیف‌ها باید بیشتر از صفر باشد."; return@Button }
                    if (r.unitPrice.toNumberOrNull() == null) { error = "مبلغ فی همه‌ی ردیف‌ها را وارد کنید."; return@Button }
                }
                val paidVal = paidAmount.toNumberOrNull() ?: 0.0
                if (paidVal > 0 && selectedAccountId == 0) { error = "صندوق یا بانک دریافت‌کننده را انتخاب کنید."; return@Button }

                saving = true
                scope.launch {
                    try {
                        val itemsArr = JSONArray()
                        validRows.forEach { r ->
                            itemsArr.put(
                                JSONObject()
                                    .put("productId", r.productId)
                                    .put("qty", r.qty.toNumber())
                                    .put("unitPrice", r.unitPrice.toNumber())
                            )
                        }
                        val body = JSONObject()
                            .put("items", itemsArr)
                            .put("paidAmount", paidVal)
                            .put("description", description)
                        if (newCustomerMode) {
                            body.put("newCustomerName", newCustomerName.trim())
                            body.put("newCustomerPhone", newCustomerPhone.trim())
                        } else {
                            body.put("customerId", selectedCustomerId)
                        }
                        if (paidVal > 0) body.put("accountId", selectedAccountId)
                        invoiceDate?.let { body.put("jalaliDate", it.toApiString()) }

                        val res = ApiClient.call("createSaleInvoice", body)
                        success = "فاکتور شماره ${res.optInt("invoiceId")} ثبت شد."
                        resetForm()
                        loadAll()
                    } catch (e: ApiException) {
                        error = e.message
                    } catch (e: Exception) {
                        error = "خطا در ارتباط با سرور."
                    }
                    saving = false
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text(if (saving) "در حال ثبت..." else "ثبت فاکتور") }
    }
}

@Composable
private fun InvoiceRowCard(row: InvoiceRowState, products: List<JSONObject>, onRemove: (() -> Unit)?) {
    var productExpanded by remember { mutableStateOf(false) }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ExposedDropdownMenuBox(
                    expanded = productExpanded, onExpandedChange = { productExpanded = it },
                    modifier = Modifier.weight(1f)
                ) {
                    OutlinedTextField(
                        value = row.productName.ifBlank { "انتخاب کالا" }, onValueChange = {}, readOnly = true,
                        modifier = Modifier.fillMaxWidth().menuAnchor(), singleLine = true
                    )
                    ExposedDropdownMenu(expanded = productExpanded, onDismissRequest = { productExpanded = false }) {
                        products.forEach { p ->
                            DropdownMenuItem(
                                text = { Text("${p.optString("name")} — ${fmt(p.optDouble("price"))}") },
                                onClick = {
                                    row.productId = p.optInt("id")
                                    row.productName = p.optString("name")
                                    row.unitName = p.optString("unitName")
                                    row.unitPrice = plainNumber(p.optDouble("price"))
                                    productExpanded = false
                                }
                            )
                        }
                    }
                }
                if (onRemove != null) {
                    IconButton(onClick = onRemove) { Icon(Icons.Default.Delete, contentDescription = "حذف ردیف") }
                }
            }

            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = row.qty, onValueChange = { row.qty = it.normalizeDigits() },
                    label = { Text("تعداد (${row.unitName.ifBlank { "واحد" }})") },
                    modifier = Modifier.weight(1f), singleLine = true,
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

@Composable
private fun AddAccountDialog(onDismiss: () -> Unit, onAdded: (id: Int) -> Unit) {
    var name by remember { mutableStateOf("") }
    var isBank by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("صندوق یا بانک جدید") },
        text = {
            Column {
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text("نام (مثل صندوق نقدی، بانک ملی)") }, modifier = Modifier.fillMaxWidth(), singleLine = true
                )
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FilterChip(selected = !isBank, onClick = { isBank = false }, label = { Text("صندوق نقدی") })
                    Spacer(Modifier.width(8.dp))
                    FilterChip(selected = isBank, onClick = { isBank = true }, label = { Text("بانک") })
                }
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
                    if (name.isBlank()) { error = "نام را وارد کنید."; return@TextButton }
                    saving = true; error = null
                    scope.launch {
                        try {
                            val res = ApiClient.call(
                                "addSaleAccount",
                                JSONObject().put("name", name.trim()).put("type", if (isBank) "bank" else "cash")
                            )
                            onAdded(res.optInt("id"))
                        } catch (e: ApiException) {
                            error = e.message
                        } catch (e: Exception) {
                            error = "خطا در ارتباط با سرور."
                        }
                        saving = false
                    }
                }
            ) { Text("افزودن") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } }
    )
}
