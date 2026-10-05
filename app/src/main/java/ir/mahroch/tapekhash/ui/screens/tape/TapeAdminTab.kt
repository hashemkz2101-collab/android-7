package ir.mahroch.tapekhash.ui.screens.tape

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import ir.mahroch.tapekhash.data.ApiClient
import ir.mahroch.tapekhash.data.ApiException
import ir.mahroch.tapekhash.data.Session
import ir.mahroch.tapekhash.data.TapeRow
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File

private enum class AdminSection(val label: String) {
    USERS("کاربران"), ACTIVE("کاربران فعال"), HISTORY("تاریخچه"),
    IMPORT("ایمپورت اکسل"), EDIT("ویرایش تپه"), LIMIT("محدودیت برداشت")
}

@Composable
fun TapeAdminTab() {
    var section by remember { mutableStateOf(AdminSection.USERS) }

    Column(Modifier.fillMaxSize()) {
        ScrollableTabRow(selectedTabIndex = section.ordinal, edgePadding = 8.dp) {
            AdminSection.values().forEach { s ->
                Tab(selected = section == s, onClick = { section = s }, text = { Text(s.label) })
            }
        }
        Box(Modifier.weight(1f)) {
            when (section) {
                AdminSection.USERS -> UsersManagementSection()
                AdminSection.ACTIVE -> ActiveUsersSection()
                AdminSection.HISTORY -> HistorySection()
                AdminSection.IMPORT -> ExcelImportSection()
                AdminSection.EDIT -> EditTapeSection()
                AdminSection.LIMIT -> TakeLimitSection()
            }
        }
    }
}

// ---------------- مدیریت کاربران ----------------

@Composable
private fun UsersManagementSection() {
    var users by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    suspend fun load() {
        try {
            val res = ApiClient.call("getUsers")
            val arr = res.getJSONArray("users")
            users = (0 until arr.length()).map { arr.getJSONObject(it) }
        } catch (e: ApiException) { error = e.message }
        loading = false
    }
    LaunchedEffect(Unit) { load() }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text("کاربران سیستم", style = MaterialTheme.typography.titleMedium)
            Button(onClick = { showAddDialog = true }) { Text("+ کاربر جدید") }
        }
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(users) { u ->
                UserRow(u, onChanged = { scope.launch { load() } })
            }
        }
    }

    if (showAddDialog) {
        AddUserDialog(onDismiss = { showAddDialog = false }, onCreated = {
            showAddDialog = false
            scope.launch { load() }
        })
    }
}

@Composable
private fun UserRow(u: JSONObject, onChanged: () -> Unit) {
    val scope = rememberCoroutineScope()
    var tapeAccess by remember { mutableStateOf(hasApp(u, "tape")) }
    var tape2Access by remember { mutableStateOf(hasApp(u, "tape2")) }
    var khashAccess by remember { mutableStateOf(hasApp(u, "khash")) }
    var salesAccess by remember { mutableStateOf(hasApp(u, "sales")) }
    var accountingAccess by remember { mutableStateOf(hasApp(u, "accounting")) }
    var active by remember { mutableStateOf(u.optBoolean("is_active", true)) }

    fun update() {
        scope.launch {
            try {
                val apps = JSONObject().apply {}
                val appsArr = org.json.JSONArray()
                if (tapeAccess) appsArr.put("tape")
                if (tape2Access) appsArr.put("tape2")
                if (khashAccess) appsArr.put("khash")
                if (salesAccess) appsArr.put("sales")
                if (accountingAccess) appsArr.put("accounting")
                ApiClient.call(
                    "updateUser",
                    JSONObject()
                        .put("id", u.getInt("id"))
                        .put("is_active", active)
                        .put("apps", appsArr)
                )
                onChanged()
            } catch (e: ApiException) { /* نادیده گرفته می‌شود؛ کاربر می‌تواند دوباره تلاش کند */ }
        }
    }

    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text("${u.optString("display_name")} (${u.optString("username")})", style = MaterialTheme.typography.titleSmall)
                Text(if (u.optString("role") == "admin") "مدیر" else "کاربر عادی")
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = tapeAccess, onCheckedChange = { tapeAccess = it; update() })
                Text("مدیریت تپه‌های ستاره جنوب")
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = tape2Access, onCheckedChange = { tape2Access = it; update() })
                Text("مدیریت تپه‌های مروارید")
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = khashAccess, onCheckedChange = { khashAccess = it; update() })
                Text("خرده کاری و فاکتور های خاش")
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = salesAccess, onCheckedChange = { salesAccess = it; update() })
                Text("فروش کالا")
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = accountingAccess, onCheckedChange = { accountingAccess = it; update() })
                Text("حساب چاپ های مستقیم کارگاه")
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = active, onCheckedChange = { active = it; update() })
                Text(if (active) "فعال" else "غیرفعال")
            }
        }
    }
}

private fun hasApp(u: JSONObject, app: String): Boolean {
    val arr = u.optJSONArray("apps") ?: return false
    for (i in 0 until arr.length()) if (arr.getString(i) == app) return true
    return false
}

@Composable
private fun AddUserDialog(onDismiss: () -> Unit, onCreated: () -> Unit) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf("") }
    var isAdmin by remember { mutableStateOf(false) }
    var tapeAccess by remember { mutableStateOf(true) }
    var tape2Access by remember { mutableStateOf(false) }
    var khashAccess by remember { mutableStateOf(false) }
    var salesAccess by remember { mutableStateOf(false) }
    var accountingAccess by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("کاربر جدید") },
        text = {
            Column {
                OutlinedTextField(value = username, onValueChange = { username = it }, label = { Text("نام کاربری") }, singleLine = true)
                OutlinedTextField(value = password, onValueChange = { password = it }, label = { Text("رمز عبور") }, singleLine = true)
                OutlinedTextField(value = displayName, onValueChange = { displayName = it }, label = { Text("نام نمایشی") }, singleLine = true)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = isAdmin, onCheckedChange = { isAdmin = it }); Text("مدیر سیستم")
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = tapeAccess, onCheckedChange = { tapeAccess = it }); Text("دسترسی به مدیریت تپه‌های ستاره جنوب")
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = tape2Access, onCheckedChange = { tape2Access = it }); Text("دسترسی به مدیریت تپه‌های مروارید")
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = khashAccess, onCheckedChange = { khashAccess = it }); Text("دسترسی به خرده کاری و فاکتور های خاش")
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = salesAccess, onCheckedChange = { salesAccess = it }); Text("دسترسی به فروش کالا")
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = accountingAccess, onCheckedChange = { accountingAccess = it }); Text("دسترسی به حساب چاپ های مستقیم کارگاه")
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                scope.launch {
                    try {
                        val appsArr = org.json.JSONArray()
                        if (tapeAccess) appsArr.put("tape")
                if (tape2Access) appsArr.put("tape2")
                        if (khashAccess) appsArr.put("khash")
                        if (salesAccess) appsArr.put("sales")
                        if (accountingAccess) appsArr.put("accounting")
                        ApiClient.call(
                            "addUser",
                            JSONObject()
                                .put("username", username.trim())
                                .put("password", password)
                                .put("display_name", displayName.trim())
                                .put("role", if (isAdmin) "admin" else "user")
                                .put("apps", appsArr)
                        )
                        onCreated()
                    } catch (e: ApiException) { error = e.message }
                }
            }) { Text("ثبت") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } }
    )
}

// ---------------- کاربران دارای تپه فعال ----------------

@Composable
private fun ActiveUsersSection() {
    var rows by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        try {
            val res = ApiClient.call("getUsersStatus")
            val arr = res.getJSONArray("users")
            rows = (0 until arr.length()).map { arr.getJSONObject(it) }
        } catch (e: Exception) { }
        loading = false
    }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(rows) { u ->
                ElevatedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text("${u.optString("displayName")} — ${u.optInt("count")} تپه فعال")
                    }
                }
            }
        }
    }
}

// ---------------- تاریخچه ----------------

@Composable
private fun HistorySection() {
    var query by remember { mutableStateOf("") }
    var rows by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    val scope = rememberCoroutineScope()

    fun load() {
        scope.launch {
            try {
                val body = JSONObject()
                if (query.isNotBlank()) body.put("tapeQuery", query)
                val res = ApiClient.call("getHistory", body)
                val arr = res.getJSONArray("history")
                rows = (0 until arr.length()).map { arr.getJSONObject(it) }
            } catch (e: Exception) { }
        }
    }
    LaunchedEffect(Unit) { load() }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row {
            OutlinedTextField(value = query, onValueChange = { query = it }, label = { Text("کد تپه (اختیاری)") }, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            Button(onClick = { load() }) { Text("فیلتر") }
        }
        Spacer(Modifier.height(8.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(rows) { h ->
                ElevatedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(10.dp)) {
                        Text("${h.optString("action")} — ${h.optString("tape_code")}", style = MaterialTheme.typography.titleSmall)
                        Text("${h.optString("display_name")} · ${h.optString("event_time")}")
                        if (h.optString("notes").isNotBlank()) Text(h.optString("notes"))
                    }
                }
            }
        }
    }
}

// ---------------- ایمپورت اکسل ----------------

@Composable
private fun ExcelImportSection() {
    var resultText by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        loading = true; error = null; resultText = null
        scope.launch {
            try {
                val tempFile = File(context.cacheDir, "import_${System.currentTimeMillis()}.xlsx")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    tempFile.outputStream().use { output -> input.copyTo(output) }
                }
                val url = Session.baseUrl + "?action=importTapesExcel"
                val res = ApiClient.uploadMultipart(
                    url = url, fileField = "file", file = tempFile,
                    mimeType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    auth = true
                )
                tempFile.delete()
                resultText = "ایجاد شده: ${res.optInt("created")} — به‌روزرسانی: ${res.optInt("updated")} — رد شده: ${res.optInt("skipped")}"
            } catch (e: ApiException) { error = e.message } catch (e: Exception) { error = "خطا در ارسال فایل." }
            loading = false
        }
    }

    Column(Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
        Text("ایمپورت اکسل کد تپه / کد باکس", style = MaterialTheme.typography.titleMedium)
        Text("ستون اول: کد تپه (اسم فایل در پوشه gol) — ستون دوم: کد باکس")
        Spacer(Modifier.height(12.dp))
        Button(onClick = { pickFile.launch("*/*") }, enabled = !loading) { Text("انتخاب فایل اکسل (.xlsx)") }
        if (loading) { Spacer(Modifier.height(8.dp)); LinearProgressIndicator(Modifier.fillMaxWidth()) }
        error?.let { Spacer(Modifier.height(8.dp)); Text(it, color = MaterialTheme.colorScheme.error) }
        resultText?.let { Spacer(Modifier.height(8.dp)); Text(it, color = MaterialTheme.colorScheme.primary) }
    }
}

// ---------------- ویرایش هر تپه (مدیر) ----------------

@Composable
private fun EditTapeSection() {
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<TapeRow>>(emptyList()) }
    var editing by remember { mutableStateOf<TapeRow?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun search() {
        scope.launch {
            try {
                val res = ApiClient.call("searchAdminTapes", JSONObject().put("query", query))
                val arr = res.getJSONArray("results")
                results = (0 until arr.length()).map { TapeRow.fromJson(arr.getJSONObject(it)) }
            } catch (e: ApiException) { error = e.message }
        }
    }
    LaunchedEffect(Unit) { search() }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row {
            OutlinedTextField(value = query, onValueChange = { query = it }, label = { Text("جستجوی کد تپه/باکس") }, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            Button(onClick = { search() }) { Text("جستجو") }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Spacer(Modifier.height(8.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(results) { row ->
                ElevatedCard(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column {
                            Text("تپه: ${row.tape}")
                            Text("باکس: ${row.box}")
                        }
                        TextButton(onClick = { editing = row }) { Text("ویرایش") }
                    }
                }
            }
        }
    }

    editing?.let { row ->
        EditTapeDialog(row = row, onDismiss = { editing = null }, onSaved = {
            editing = null
            search()
        })
    }
}

@Composable
private fun EditTapeDialog(row: TapeRow, onDismiss: () -> Unit, onSaved: () -> Unit) {
    var boxCode by remember { mutableStateOf(row.box) }
    var tapeCode by remember { mutableStateOf(row.tape) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("ویرایش تپه") },
        text = {
            Column {
                OutlinedTextField(value = boxCode, onValueChange = { boxCode = it }, label = { Text("کد باکس") })
                OutlinedTextField(value = tapeCode, onValueChange = { tapeCode = it }, label = { Text("کد تپه") })
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                scope.launch {
                    try {
                        ApiClient.call(
                            "updateTape",
                            JSONObject()
                                .put("id", row.id)
                                .put("boxCode", boxCode.trim())
                                .put("tapeCode", tapeCode.trim())
                                .put("imageCode", tapeCode.trim())
                        )
                        onSaved()
                    } catch (e: ApiException) { error = e.message }
                }
            }) { Text("ذخیره") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } }
    )
}

// ---------------- محدودیت تعداد برداشت (برای همه‌ی کاربران) ----------------

@Composable
private fun TakeLimitSection() {
    var limitText by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        try {
            val res = ApiClient.call("getTakeLimit")
            limitText = res.optInt("limit", 5).toString()
        } catch (e: ApiException) { error = e.message } catch (e: Exception) { error = "خطا در ارتباط با سرور." }
        loading = false
    }

    fun change(delta: Int) {
        val cur = limitText.trim().toIntOrNull() ?: 0
        limitText = (cur + delta).coerceAtLeast(1).toString()
    }

    Column(Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
        Text("حداکثر تعداد تپه‌ای که هر کاربر هم‌زمان می‌تواند از باکس خارج کند", style = MaterialTheme.typography.titleMedium)
        Text("این عدد برای همه‌ی کاربران اعمال می‌شود.", style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(16.dp))
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())

        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = { change(-1) }, enabled = !loading) { Text("−") }
            Spacer(Modifier.width(8.dp))
            OutlinedTextField(
                value = limitText,
                onValueChange = { limitText = it.filter { c -> c.isDigit() }.take(4) },
                label = { Text("تعداد مجاز") },
                singleLine = true, modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            OutlinedButton(onClick = { change(1) }, enabled = !loading) { Text("+") }
        }

        error?.let { Spacer(Modifier.height(8.dp)); Text(it, color = MaterialTheme.colorScheme.error) }
        message?.let { Spacer(Modifier.height(8.dp)); Text(it, color = MaterialTheme.colorScheme.primary) }

        Spacer(Modifier.height(16.dp))
        Button(
            enabled = !loading && !saving,
            onClick = {
                val n = limitText.trim().toIntOrNull()
                if (n == null || n < 1) { error = "یک عدد معتبر (حداقل ۱) وارد کنید."; message = null; return@Button }
                saving = true; error = null; message = null
                scope.launch {
                    try {
                        ApiClient.call("setTakeLimit", JSONObject().put("limit", n))
                        message = "ذخیره شد: هر کاربر حداکثر $n تپه می‌تواند در اختیار داشته باشد."
                    } catch (e: ApiException) { error = e.message } catch (e: Exception) { error = "خطا در ارتباط با سرور." }
                    saving = false
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("ذخیره") }
    }
}
