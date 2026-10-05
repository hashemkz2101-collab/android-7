package ir.mahroch.tapekhash.ui.screens.khash

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
import ir.mahroch.tapekhash.data.ApiException
import ir.mahroch.tapekhash.data.normalizeDigits
import ir.mahroch.tapekhash.data.toNumberOrNull
import kotlinx.coroutines.launch
import org.json.JSONObject

/** داده‌های اولیه‌ی مشترک بین ثبت و گزارش‌ها: تاریخ امروز، نیروهای فعال، صندوق/بانک‌ها و درصد سهم نیرو. */
private class SmallJobInit(
    val today: String,
    val names: List<String>,
    val accounts: List<JSONObject>,
    val sharePercent: Double
)

private enum class SmallJobSection { ENTRY, EMPLOYEE_REPORT, ACCOUNT_REPORT }

private fun accountTypeLabel(type: String) = if (type == "bank") "بانک" else "صندوق"

private fun parseJalali(s: String): JalaliDate? {
    val p = s.normalizeDigits().split("/")
    if (p.size != 3) return null
    val y = p[0].toIntOrNull() ?: return null
    val m = p[1].toIntOrNull() ?: return null
    val d = p[2].toIntOrNull() ?: return null
    if (m !in 1..12 || d !in 1..31) return null
    return JalaliDate(y, m, d)
}

/**
 * تب «خرده‌کاری» در خرده کاری و فاکتور های خاش:
 *  - ثبت: نیرو، تاریخ (پیش‌فرض امروز)، مبلغ و صندوق/بانک تسویه. سهم نیرو (پیش‌فرض ۵۰٪) به حسابش اضافه می‌شود.
 *  - گزارش نیرو: فیلتر نیرو و بازه‌ی تاریخ (پیش‌فرض امروز).
 *  - گزارش صندوق/بانک: جمع تسویه‌ها به تفکیک صندوق/بانک در بازه‌ی تاریخ (پیش‌فرض امروز).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmallJobsTab() {
    var setup by remember { mutableStateOf<SmallJobInit?>(null) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var section by remember { mutableStateOf(SmallJobSection.ENTRY) }
    val scope = rememberCoroutineScope()

    suspend fun loadInit() {
        try {
            val res = ApiClient.call("getKhashSmallJobInit")
            val namesArr = res.getJSONArray("names")
            val accArr = res.getJSONArray("accounts")
            setup = SmallJobInit(
                today = res.optString("today"),
                names = (0 until namesArr.length()).map { namesArr.getString(it) },
                accounts = (0 until accArr.length()).map { accArr.getJSONObject(it) },
                sharePercent = res.optDouble("sharePercent", 50.0)
            )
            loadError = null
        } catch (e: ApiException) {
            loadError = e.message
        } catch (e: Exception) {
            loadError = "خطا در ارتباط با سرور."
        }
    }
    LaunchedEffect(Unit) { loadInit() }

    Column(Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = section.ordinal) {
            Tab(selected = section == SmallJobSection.ENTRY, onClick = { section = SmallJobSection.ENTRY },
                text = { Text("ثبت") })
            Tab(selected = section == SmallJobSection.EMPLOYEE_REPORT, onClick = { section = SmallJobSection.EMPLOYEE_REPORT },
                text = { Text("گزارش نیرو") })
            Tab(selected = section == SmallJobSection.ACCOUNT_REPORT, onClick = { section = SmallJobSection.ACCOUNT_REPORT },
                text = { Text("صندوق/بانک") })
        }

        val data = setup
        if (data == null) {
            Column(Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                val err = loadError
                if (err != null) {
                    Text(err, color = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { scope.launch { loadInit() } }) { Text("تلاش دوباره") }
                } else {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }
            }
        } else {
            when (section) {
                SmallJobSection.ENTRY -> SmallJobEntry(data, onAccountsChanged = { scope.launch { loadInit() } })
                SmallJobSection.EMPLOYEE_REPORT -> SmallJobEmployeeReport(data)
                SmallJobSection.ACCOUNT_REPORT -> SmallJobAccountReport(data)
            }
        }
    }
}

// ---------------------------------------------------------------- ثبت

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SmallJobEntry(setup: SmallJobInit, onAccountsChanged: () -> Unit) {
    var selectedEmployee by remember { mutableStateOf(setup.names.firstOrNull() ?: "") }
    var employeeExpanded by remember { mutableStateOf(false) }
    var jalaliDate by remember { mutableStateOf(setup.today) }
    var amount by remember { mutableStateOf("") }
    var selectedAccountId by remember { mutableStateOf(setup.accounts.firstOrNull()?.optInt("id") ?: 0) }
    var accountExpanded by remember { mutableStateOf(false) }
    var showAddAccount by remember { mutableStateOf(false) }
    var description by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    if (showAddAccount) {
        AddKhashAccountDialog(
            onDismiss = { showAddAccount = false },
            onAdded = { id -> selectedAccountId = id; showAddAccount = false; onAccountsChanged() }
        )
    }

    val accountName = setup.accounts.firstOrNull { it.optInt("id") == selectedAccountId }?.let {
        "${it.optString("name")} (${accountTypeLabel(it.optString("type"))})"
    } ?: "انتخاب صندوق/بانک"
    val amountValue = amount.toNumberOrNull()

    fun submit() {
        error = null; message = null
        if (selectedEmployee.isBlank()) { error = "نیرو را انتخاب کنید."; return }
        if (amountValue == null || amountValue <= 0) { error = "مبلغ خرده‌کاری را درست وارد کنید."; return }
        if (selectedAccountId == 0) { error = "صندوق یا بانک تسویه را انتخاب کنید."; return }
        saving = true
        scope.launch {
            try {
                val body = JSONObject()
                    .put("employeeName", selectedEmployee)
                    .put("amount", amountValue)
                    .put("accountId", selectedAccountId)
                    .put("jalaliDate", jalaliDate)
                    .put("description", description.trim())
                val res = ApiClient.call("createSmallJob", body)
                message = "خرده‌کاری ثبت شد. ${fmt(res.optDouble("share"))} تومان به حساب $selectedEmployee اضافه شد " +
                    "(مانده‌ی جدید: ${fmt(res.optDouble("balance"))} تومان)."
                amount = ""; description = ""; jalaliDate = setup.today
            } catch (e: ApiException) {
                error = e.message
            } catch (e: Exception) {
                error = "خطا در ارتباط با سرور."
            }
            saving = false
        }
    }

    Column(Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
        Text("ثبت خرده‌کاری", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))

        ExposedDropdownMenuBox(expanded = employeeExpanded, onExpandedChange = { employeeExpanded = it }) {
            OutlinedTextField(
                value = selectedEmployee.ifBlank { "انتخاب نیرو" }, onValueChange = {}, readOnly = true,
                label = { Text("نیرو") }, modifier = Modifier.fillMaxWidth().menuAnchor()
            )
            ExposedDropdownMenu(expanded = employeeExpanded, onDismissRequest = { employeeExpanded = false }) {
                setup.names.forEach { name ->
                    DropdownMenuItem(text = { Text(name) }, onClick = { selectedEmployee = name; employeeExpanded = false })
                }
            }
        }
        Spacer(Modifier.height(8.dp))

        JalaliDateButton(
            label = "تاریخ", value = jalaliDate, onChange = { jalaliDate = it },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))

        OutlinedTextField(
            value = amount, onValueChange = { amount = it.normalizeDigits() },
            label = { Text("مبلغ خرده‌کاری (تومان)") }, modifier = Modifier.fillMaxWidth(), singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )
        if (amountValue != null && amountValue > 0) {
            Spacer(Modifier.height(4.dp))
            Text(
                "سهم نیرو (${fmt(setup.sharePercent)}٪): ${fmt(amountValue * setup.sharePercent / 100)} تومان",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary
            )
        }
        Spacer(Modifier.height(8.dp))

        ExposedDropdownMenuBox(expanded = accountExpanded, onExpandedChange = { accountExpanded = it }) {
            OutlinedTextField(
                value = accountName, onValueChange = {}, readOnly = true,
                label = { Text("صندوق / بانک تسویه") }, modifier = Modifier.fillMaxWidth().menuAnchor()
            )
            ExposedDropdownMenu(expanded = accountExpanded, onDismissRequest = { accountExpanded = false }) {
                setup.accounts.forEach { a ->
                    DropdownMenuItem(
                        text = { Text("${a.optString("name")} (${accountTypeLabel(a.optString("type"))})") },
                        onClick = { selectedAccountId = a.optInt("id"); accountExpanded = false }
                    )
                }
                DropdownMenuItem(
                    text = { Text("+ صندوق یا بانک جدید") },
                    onClick = { accountExpanded = false; showAddAccount = true }
                )
            }
        }
        Spacer(Modifier.height(8.dp))

        OutlinedTextField(
            value = description, onValueChange = { description = it },
            label = { Text("توضیحات (اختیاری)") }, modifier = Modifier.fillMaxWidth()
        )

        error?.let { Spacer(Modifier.height(8.dp)); Text(it, color = MaterialTheme.colorScheme.error) }
        message?.let { Spacer(Modifier.height(8.dp)); Text(it, color = MaterialTheme.colorScheme.primary) }

        Spacer(Modifier.height(12.dp))
        Button(onClick = { submit() }, enabled = !saving, modifier = Modifier.fillMaxWidth()) {
            Text(if (saving) "در حال ثبت..." else "ثبت خرده‌کاری")
        }
    }
}

// ---------------------------------------------------------------- گزارش نیرو

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SmallJobEmployeeReport(setup: SmallJobInit) {
    var filterEmployee by remember { mutableStateOf("") } // خالی = همه‌ی نیروها
    var filterExpanded by remember { mutableStateOf(false) }
    var fromDate by remember { mutableStateOf(setup.today) }
    var toDate by remember { mutableStateOf(setup.today) }
    var jobs by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var sumAmount by remember { mutableStateOf(0.0) }
    var sumShare by remember { mutableStateOf(0.0) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(filterEmployee, fromDate, toDate) {
        loading = true
        try {
            val body = JSONObject()
            if (filterEmployee.isNotBlank()) body.put("employeeName", filterEmployee)
            if (fromDate.isNotBlank()) body.put("fromDate", fromDate)
            if (toDate.isNotBlank()) body.put("toDate", toDate)
            val res = ApiClient.call("getSmallJobs", body)
            val arr = res.getJSONArray("jobs")
            jobs = (0 until arr.length()).map { arr.getJSONObject(it) }
            sumAmount = res.optDouble("sumAmount", 0.0)
            sumShare = res.optDouble("sumShare", 0.0)
            error = null
        } catch (e: ApiException) {
            error = e.message
        } catch (e: Exception) {
            error = "خطا در ارتباط با سرور."
        }
        loading = false
    }

    LazyColumn(
        Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Text("گزارش خرده‌کاری", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            ExposedDropdownMenuBox(expanded = filterExpanded, onExpandedChange = { filterExpanded = it }) {
                OutlinedTextField(
                    value = filterEmployee.ifBlank { "همه‌ی نیروها" }, onValueChange = {}, readOnly = true,
                    label = { Text("فیلتر نیرو") }, modifier = Modifier.fillMaxWidth().menuAnchor()
                )
                ExposedDropdownMenu(expanded = filterExpanded, onDismissRequest = { filterExpanded = false }) {
                    DropdownMenuItem(text = { Text("همه‌ی نیروها") }, onClick = { filterEmployee = ""; filterExpanded = false })
                    setup.names.forEach { name ->
                        DropdownMenuItem(text = { Text(name) }, onClick = { filterEmployee = name; filterExpanded = false })
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            DateFilterRow(
                today = setup.today, fromDate = fromDate, toDate = toDate,
                onFromChange = { fromDate = it }, onToChange = { toDate = it }
            )
            if (loading) { Spacer(Modifier.height(8.dp)); LinearProgressIndicator(Modifier.fillMaxWidth()) }
            error?.let { Spacer(Modifier.height(8.dp)); Text(it, color = MaterialTheme.colorScheme.error) }
        }

        item {
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text("تعداد: ${jobs.size}")
                    Text("جمع مبلغ خرده‌کاری: ${fmt(sumAmount)} تومان", style = MaterialTheme.typography.titleSmall)
                    Text("جمع سهم نیرو: ${fmt(sumShare)} تومان", color = MaterialTheme.colorScheme.primary)
                }
            }
        }

        if (!loading && jobs.isEmpty()) {
            item { Text("خرده‌کاری‌ای در این بازه ثبت نشده است.") }
        }

        items(jobs) { j ->
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text(j.optString("employee_name"), style = MaterialTheme.typography.titleSmall)
                    Text("تاریخ: ${j.optString("jalali_date")}", style = MaterialTheme.typography.bodySmall)
                    Text("مبلغ: ${fmt(j.optDouble("amount"))} تومان — سهم نیرو: ${fmt(j.optDouble("employee_share"))}")
                    Text(
                        "تسویه: ${j.optString("account_name")} (${accountTypeLabel(j.optString("account_type"))})",
                        style = MaterialTheme.typography.bodySmall
                    )
                    val desc = j.optString("description")
                    if (desc.isNotBlank()) Text(desc, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

// ---------------------------------------------------------------- گزارش صندوق/بانک

@Composable
private fun SmallJobAccountReport(setup: SmallJobInit) {
    var fromDate by remember { mutableStateOf(setup.today) }
    var toDate by remember { mutableStateOf(setup.today) }
    var groups by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var grandAmount by remember { mutableStateOf(0.0) }
    var grandCount by remember { mutableStateOf(0) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(fromDate, toDate) {
        loading = true
        try {
            val body = JSONObject()
            if (fromDate.isNotBlank()) body.put("fromDate", fromDate)
            if (toDate.isNotBlank()) body.put("toDate", toDate)
            val res = ApiClient.call("getSmallJobAccountReport", body)
            val arr = res.getJSONArray("accounts")
            groups = (0 until arr.length()).map { arr.getJSONObject(it) }
            grandAmount = res.optDouble("grandAmount", 0.0)
            grandCount = res.optInt("grandCount", 0)
            error = null
        } catch (e: ApiException) {
            error = e.message
        } catch (e: Exception) {
            error = "خطا در ارتباط با سرور."
        }
        loading = false
    }

    LazyColumn(
        Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Text("گزارش صندوق و بانک (تسویه‌ی خرده‌کاری‌ها)", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            DateFilterRow(
                today = setup.today, fromDate = fromDate, toDate = toDate,
                onFromChange = { fromDate = it }, onToChange = { toDate = it }
            )
            if (loading) { Spacer(Modifier.height(8.dp)); LinearProgressIndicator(Modifier.fillMaxWidth()) }
            error?.let { Spacer(Modifier.height(8.dp)); Text(it, color = MaterialTheme.colorScheme.error) }
        }

        item {
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text("تعداد کل خرده‌کاری: $grandCount")
                    Text("جمع کل تسویه‌شده: ${fmt(grandAmount)} تومان", style = MaterialTheme.typography.titleSmall)
                }
            }
        }

        if (!loading && groups.isEmpty()) {
            item { Text("در این بازه تسویه‌ای ثبت نشده است.") }
        }

        items(groups) { g ->
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text(
                        "${g.optString("accountName")} (${accountTypeLabel(g.optString("accountType"))})",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text("تعداد: ${g.optInt("count")} — جمع: ${fmt(g.optDouble("totalAmount"))} تومان")
                    val jobs = g.optJSONArray("jobs")
                    if (jobs != null) {
                        Spacer(Modifier.height(6.dp))
                        for (i in 0 until jobs.length()) {
                            val j = jobs.getJSONObject(i)
                            Text(
                                "${j.optString("jalali_date")} — ${j.optString("employee_name")} — ${fmt(j.optDouble("amount"))}",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- اجزای مشترک

/** دکمه‌ی انتخاب تاریخ شمسی؛ دیالوگ انتخاب با مقدار فعلی باز می‌شود. */
@Composable
private fun JalaliDateButton(label: String, value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier) {
    var picking by remember { mutableStateOf(false) }
    if (picking) {
        JalaliDatePickerDialog(
            initial = parseJalali(value),
            onDismiss = { picking = false },
            onConfirm = { onChange(it.toApiString()); picking = false }
        )
    }
    OutlinedButton(onClick = { picking = true }, modifier = modifier) {
        Text(if (value.isBlank()) label else "$label: $value")
    }
}

/** ردیف «از تاریخ / تا تاریخ» + میان‌بُر «امروز» و «همه‌ی تاریخ‌ها». */
@Composable
private fun DateFilterRow(
    today: String, fromDate: String, toDate: String,
    onFromChange: (String) -> Unit, onToChange: (String) -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        JalaliDateButton("از تاریخ", fromDate, onFromChange, Modifier.weight(1f))
        Spacer(Modifier.width(8.dp))
        JalaliDateButton("تا تاریخ", toDate, onToChange, Modifier.weight(1f))
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = { onFromChange(today); onToChange(today) }) { Text("امروز") }
        TextButton(onClick = { onFromChange(""); onToChange("") }) { Text("همه‌ی تاریخ‌ها") }
    }
}

@Composable
private fun AddKhashAccountDialog(onDismiss: () -> Unit, onAdded: (id: Int) -> Unit) {
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
                                "addKhashAccount",
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
