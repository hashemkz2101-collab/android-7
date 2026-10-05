package ir.mahroch.tapekhash.ui.screens.tape

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import ir.mahroch.tapekhash.data.ApiClient
import ir.mahroch.tapekhash.data.ApiException
import ir.mahroch.tapekhash.data.Session
import ir.mahroch.tapekhash.data.TapeRow
import kotlinx.coroutines.launch
import org.json.JSONObject

private enum class TapeTab { SEARCH, LIST, MINE, ADD, ADMIN }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TapeMainScreen(onBack: () -> Unit) {
    val user = Session.loadCachedUser()
    var tab by remember { mutableStateOf(TapeTab.SEARCH) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("مدیریت تپه‌های ستاره جنوب") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "بازگشت") }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = tab == TapeTab.SEARCH,
                    onClick = { tab = TapeTab.SEARCH },
                    icon = { Icon(Icons.Default.Search, contentDescription = null) },
                    label = { Text("جستجو") }
                )
                NavigationBarItem(
                    selected = tab == TapeTab.LIST,
                    onClick = { tab = TapeTab.LIST },
                    icon = { Icon(Icons.Default.ViewList, contentDescription = null) },
                    label = { Text("لیست") }
                )
                NavigationBarItem(
                    selected = tab == TapeTab.MINE,
                    onClick = { tab = TapeTab.MINE },
                    icon = { Icon(Icons.Default.Inventory2, contentDescription = null) },
                    label = { Text("تپه‌های من") }
                )
                NavigationBarItem(
                    selected = tab == TapeTab.ADD,
                    onClick = { tab = TapeTab.ADD },
                    icon = { Icon(Icons.Default.AddBox, contentDescription = null) },
                    label = { Text("ثبت تپه") }
                )
                if (user?.isAdmin == true) {
                    NavigationBarItem(
                        selected = tab == TapeTab.ADMIN,
                        onClick = { tab = TapeTab.ADMIN },
                        icon = { Icon(Icons.Default.AdminPanelSettings, contentDescription = null) },
                        label = { Text("مدیریت") }
                    )
                }
            }
        }
    ) { padding ->
        Box(Modifier.padding(padding)) {
            when (tab) {
                TapeTab.SEARCH -> SearchTab()
                TapeTab.LIST -> TapeListTab()
                TapeTab.MINE -> MyTapesTab()
                TapeTab.ADD -> TapeAddTab()
                TapeTab.ADMIN -> TapeAdminTab()
            }
        }
    }
}

@Composable
private fun SearchTab() {
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<TapeRow>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var actionMsg by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun search() {
        loading = true; error = null
        scope.launch {
            try {
                val res = ApiClient.call("searchData", JSONObject().put("query", query))
                val arr = res.getJSONArray("results")
                results = (0 until arr.length()).map { TapeRow.fromJson(arr.getJSONObject(it)) }
            } catch (e: ApiException) { error = e.message } catch (e: Exception) { error = "خطا در ارتباط با سرور." }
            loading = false
        }
    }

    fun takeTape(row: TapeRow) {
        scope.launch {
            try {
                ApiClient.call("takeTape", JSONObject().put("id", row.id))
                actionMsg = "تپه «${row.tape}» برداشته شد."
                search()
            } catch (e: ApiException) { error = e.message }
        }
    }

    fun returnTape(row: TapeRow) {
        scope.launch {
            try {
                ApiClient.call("returnTape", JSONObject().put("id", row.id))
                actionMsg = "تپه «${row.tape}» برگشت داده شد."
                search()
            } catch (e: ApiException) { error = e.message }
        }
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = query, onValueChange = { query = it },
                label = { Text("کد باکس یا کد تپه") },
                modifier = Modifier.weight(1f), singleLine = true
            )
            Spacer(Modifier.width(8.dp))
            Button(onClick = { search() }) { Text("جستجو") }
        }

        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 8.dp))
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp)) }
        actionMsg?.let { Text(it, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp)) }

        Spacer(Modifier.height(8.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(results) { row ->
                TapeCard(row = row, onTake = { takeTape(row) }, onReturn = { returnTape(row) })
            }
        }
    }
}

@Composable
private fun MyTapesTab() {
    var rows by remember { mutableStateOf<List<TapeRow>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var limit by remember { mutableStateOf<Int?>(null) }
    val scope = rememberCoroutineScope()

    suspend fun load() {
        try {
            // سقف مجاز برداشت (اگر سرور هنوز این اکشن را نداشته باشد، فقط تعداد نمایش داده می‌شود)
            limit = try { ApiClient.call("getTakeLimit").optInt("limit", 0).takeIf { it > 0 } } catch (e: Exception) { null }
            val res = ApiClient.call("getMyTapes")
            val arr = res.getJSONArray("tapes")
            rows = (0 until arr.length()).map { TapeRow.fromJson(arr.getJSONObject(it)) }
        } catch (e: ApiException) { error = e.message } catch (e: Exception) { error = "خطا در ارتباط با سرور." }
        loading = false
    }

    LaunchedEffect(Unit) { load() }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text(if (limit != null) "تپه‌های در اختیار شما (${rows.size} از ${limit})" else "تپه‌های در اختیار شما (${rows.size})", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(rows) { row ->
                TapeCard(row = row, onTake = null, onReturn = {
                    scope.launch {
                        try {
                            ApiClient.call("returnTape", JSONObject().put("id", row.id))
                            load()
                        } catch (e: ApiException) { error = e.message }
                    }
                })
            }
        }
    }
}

@Composable
fun TapeCard(row: TapeRow, onTake: (() -> Unit)?, onReturn: (() -> Unit)?) {
    val allUrls = (row.imageUrls + row.imageUrl).filter { it.isNotBlank() }.distinct()
    var showViewer by remember { mutableStateOf(false) }
    if (showViewer) ImageViewerDialog(urls = allUrls, onDismiss = { showViewer = false })

    ElevatedCard(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp)) {
            if (allUrls.isNotEmpty()) {
                AsyncImage(
                    model = allUrls.first(), contentDescription = "بزرگ‌نمایی عکس",
                    modifier = Modifier.size(72.dp).clickable { showViewer = true }
                )
                Spacer(Modifier.width(12.dp))
            }
            Column(Modifier.weight(1f)) {
                Text("تپه: ${row.tape}", style = MaterialTheme.typography.titleMedium)
                if (row.box.isNotBlank()) Text("باکس: ${row.box}")
                Text("وضعیت: ${row.status}")
                if (row.status == "در حال استفاده" && row.holderName.isNotBlank()) {
                    Text("در اختیار: ${row.holderName}")
                }
            }
            Column {
                if (row.status != "در حال استفاده" && onTake != null) {
                    Button(onClick = onTake) { Text("برداشت") }
                }
                if (onReturn != null && (row.isMine || row.status == "در حال استفاده")) {
                    Spacer(Modifier.height(4.dp))
                    OutlinedButton(onClick = onReturn) { Text("برگشت") }
                }
            }
        }
    }
}
