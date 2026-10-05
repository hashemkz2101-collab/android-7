package ir.mahroch.tapekhash.ui.screens.sales

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ir.mahroch.tapekhash.data.ApiClient
import ir.mahroch.tapekhash.data.normalizeDigits
import ir.mahroch.tapekhash.data.toNumberOrNull
import ir.mahroch.tapekhash.data.toNumber
import ir.mahroch.tapekhash.data.plainNumber
import ir.mahroch.tapekhash.data.ApiException
import kotlinx.coroutines.launch
import org.json.JSONObject

private fun fmtPrice(v: Double): String {
    val n = v.toLong()
    return if (v == n.toDouble()) "%,d".format(java.util.Locale.US, n) else "%,.2f".format(java.util.Locale.US, v)
}

/**
 * تب «کالاها»: افزودن/ویرایش کالا با قیمت، واحد (قابل‌تعریف)، و موجودی انبار برای کالاهای محدود.
 * با هر فروشی که بعداً در «فاکتور فروش» ثبت شود، موجودی کالاهایی که «track_stock» دارند کم می‌شود.
 */
@Composable
fun ProductsTab() {
    var products by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var units by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var query by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var showAdd by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<JSONObject?>(null) }
    val scope = rememberCoroutineScope()

    suspend fun loadUnits() {
        try {
            val res = ApiClient.call("getSaleUnits", JSONObject())
            val arr = res.getJSONArray("units")
            units = (0 until arr.length()).map { arr.getJSONObject(it) }
        } catch (e: Exception) { }
    }

    suspend fun loadProducts() {
        loading = true; error = null
        try {
            val body = JSONObject()
            if (query.isNotBlank()) body.put("query", query)
            val res = ApiClient.call("getSaleProducts", body)
            val arr = res.getJSONArray("products")
            products = (0 until arr.length()).map { arr.getJSONObject(it) }
        } catch (e: ApiException) {
            error = e.message
        } catch (e: Exception) {
            error = "خطا در ارتباط با سرور."
        }
        loading = false
    }

    LaunchedEffect(Unit) { loadUnits(); loadProducts() }
    LaunchedEffect(query) { loadProducts() }

    if (showAdd) {
        ProductEditDialog(
            product = null, units = units,
            onUnitAdded = { scope.launch { loadUnits() } },
            onDismiss = { showAdd = false },
            onSaved = { showAdd = false; scope.launch { loadProducts() } }
        )
    }
    editing?.let { p ->
        ProductEditDialog(
            product = p, units = units,
            onUnitAdded = { scope.launch { loadUnits() } },
            onDismiss = { editing = null },
            onSaved = { editing = null; scope.launch { loadProducts() } }
        )
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = query, onValueChange = { query = it },
                label = { Text("جستجوی کالا") }, modifier = Modifier.weight(1f), singleLine = true
            )
            Spacer(Modifier.width(8.dp))
            Button(onClick = { showAdd = true }) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text("کالا")
            }
        }
        Spacer(Modifier.height(8.dp))

        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(products, key = { it.optInt("id") }) { p ->
                ProductCard(p, onClick = { editing = p })
            }
        }
        if (!loading && products.isEmpty()) {
            Spacer(Modifier.height(24.dp))
            Text("هنوز کالایی ثبت نشده است.", modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun ProductCard(p: JSONObject, onClick: () -> Unit) {
    val isActive = p.optBoolean("isActive", true)
    val trackStock = p.optBoolean("trackStock", false)
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(p.optString("name"), style = MaterialTheme.typography.titleMedium)
                Text(
                    "${fmtPrice(p.optDouble("price"))} تومان / ${p.optString("unitName")}",
                    style = MaterialTheme.typography.bodyMedium
                )
                if (trackStock) {
                    Text(
                        "موجودی انبار: ${fmtPrice(p.optDouble("stockQty"))} ${p.optString("unitName")}",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (p.optDouble("stockQty") <= 0)
                            MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                    )
                } else {
                    Text("موجودی نامحدود", style = MaterialTheme.typography.bodySmall)
                }
            }
            if (!isActive) {
                AssistChip(onClick = {}, label = { Text("غیرفعال") })
            }
        }
    }
}

/**
 * دیالوگ افزودن/ویرایش کالا. اگر «product» خالی باشد یعنی کالای جدید، وگرنه ویرایش همان کالاست.
 * از همین‌جا می‌توان واحد جدید هم اضافه کرد (بدون بستن این دیالوگ).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProductEditDialog(
    product: JSONObject?,
    units: List<JSONObject>,
    onUnitAdded: () -> Unit,
    onDismiss: () -> Unit,
    onSaved: () -> Unit
) {
    val isEdit = product != null
    var name by remember { mutableStateOf(product?.optString("name") ?: "") }
    var price by remember { mutableStateOf(if (isEdit) plainNumber(product!!.optDouble("price")) else "") }
    var unitId by remember { mutableStateOf(product?.optInt("unitId") ?: units.firstOrNull()?.optInt("id") ?: 0) }
    var unitExpanded by remember { mutableStateOf(false) }
    var trackStock by remember { mutableStateOf(product?.optBoolean("trackStock") ?: false) }
    var stockQty by remember { mutableStateOf(if (isEdit) plainNumber(product!!.optDouble("stockQty")) else "") }
    var isActive by remember { mutableStateOf(product?.optBoolean("isActive") ?: true) }
    var showAddUnit by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    if (showAddUnit) {
        AddUnitDialog(
            onDismiss = { showAddUnit = false },
            onAdded = { newId, _ -> unitId = newId; onUnitAdded(); showAddUnit = false }
        )
    }

    val unitName = units.firstOrNull { it.optInt("id") == unitId }?.optString("name") ?: ""

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isEdit) "ویرایش کالا" else "افزودن کالا") },
        text = {
            Column {
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text("نام کالا") }, modifier = Modifier.fillMaxWidth(), singleLine = true
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = price, onValueChange = { price = it.normalizeDigits() },
                    label = { Text("مبلغ (تومان)") }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)
                )
                Spacer(Modifier.height(8.dp))

                ExposedDropdownMenuBox(expanded = unitExpanded, onExpandedChange = { unitExpanded = it }) {
                    OutlinedTextField(
                        value = unitName.ifBlank { "انتخاب واحد" }, onValueChange = {}, readOnly = true,
                        label = { Text("واحد") }, modifier = Modifier.fillMaxWidth().menuAnchor()
                    )
                    ExposedDropdownMenu(expanded = unitExpanded, onDismissRequest = { unitExpanded = false }) {
                        units.forEach { u ->
                            DropdownMenuItem(
                                text = { Text(u.optString("name")) },
                                onClick = { unitId = u.optInt("id"); unitExpanded = false }
                            )
                        }
                        Divider()
                        DropdownMenuItem(
                            text = { Text("+ افزودن واحد جدید") },
                            onClick = { unitExpanded = false; showAddUnit = true }
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = trackStock, onCheckedChange = { trackStock = it })
                    Spacer(Modifier.width(8.dp))
                    Text("موجودی این کالا محدود است")
                }
                if (trackStock) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = stockQty, onValueChange = { stockQty = it.normalizeDigits() },
                        label = { Text("تعداد موجودی در انبار (${unitName.ifBlank { "واحد" }})") },
                        modifier = Modifier.fillMaxWidth(), singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)
                    )
                }

                if (isEdit) {
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(checked = isActive, onCheckedChange = { isActive = it })
                        Spacer(Modifier.width(8.dp))
                        Text(if (isActive) "فعال" else "غیرفعال (در فاکتور فروش نمایش داده نمی‌شود)")
                    }
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
                    val priceVal = price.trim().toNumberOrNull()
                    if (name.isBlank()) { error = "نام کالا را وارد کنید."; return@TextButton }
                    if (priceVal == null || priceVal < 0) { error = "مبلغ نامعتبر است."; return@TextButton }
                    if (unitId == 0) { error = "واحد را انتخاب کنید."; return@TextButton }
                    val stockVal = if (trackStock) (stockQty.trim().toNumberOrNull() ?: -1.0) else 0.0
                    if (trackStock && stockVal < 0) { error = "موجودی نامعتبر است."; return@TextButton }

                    saving = true; error = null
                    scope.launch {
                        try {
                            if (isEdit) {
                                ApiClient.call(
                                    "updateSaleProduct",
                                    JSONObject()
                                        .put("id", product!!.optInt("id"))
                                        .put("name", name.trim())
                                        .put("price", priceVal)
                                        .put("unitId", unitId)
                                        .put("trackStock", trackStock)
                                        .put("stockQty", stockVal)
                                        .put("isActive", isActive)
                                )
                            } else {
                                ApiClient.call(
                                    "addSaleProduct",
                                    JSONObject()
                                        .put("name", name.trim())
                                        .put("price", priceVal)
                                        .put("unitId", unitId)
                                        .put("trackStock", trackStock)
                                        .put("stockQty", stockVal)
                                )
                            }
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
private fun AddUnitDialog(onDismiss: () -> Unit, onAdded: (id: Int, name: String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("واحد جدید") },
        text = {
            Column {
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text("نام واحد (مثل کیلوگرم، رول)") }, modifier = Modifier.fillMaxWidth(), singleLine = true
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
                    if (name.isBlank()) { error = "نام واحد را وارد کنید."; return@TextButton }
                    saving = true; error = null
                    scope.launch {
                        try {
                            val res = ApiClient.call("addSaleUnit", JSONObject().put("name", name.trim()))
                            onAdded(res.optInt("id"), res.optString("name"))
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
