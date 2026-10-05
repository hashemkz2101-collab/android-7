package ir.mahroch.tapekhash.ui.screens.tape

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import ir.mahroch.tapekhash.data.ApiClient
import ir.mahroch.tapekhash.data.ApiException
import ir.mahroch.tapekhash.data.HostImage
import ir.mahroch.tapekhash.data.TapeRow
import kotlinx.coroutines.launch
import org.json.JSONObject

private const val STATUS_IN_USE = "در حال استفاده"
private const val HOST_IMAGE_STATUS_OK = "تپه در باکس موجود است"

private enum class TapeListMode(val label: String) {
    REGISTERED("تپه‌های ثبت‌شده"),
    HOST_IMAGES("همه‌ی عکس‌های هاست")
}

/**
 * تب «لیست». با فیلتر بالا بین دو حالت انتخاب می‌شود:
 * - تپه‌های ثبت‌شده: همان لیست قبلی، از جدول تپه‌ها (قابلیت برداشت/برگشت دارد).
 * - همه‌ی عکس‌های هاست: مستقیماً از پوشه‌های GOL/BON/catalog روی هاست خوانده می‌شود؛
 *   شامل فایل‌هایی هم می‌شود که هنوز هیچ تپه‌ای برایشان ثبت نشده (وضعیت «این تپه ناموجود است»).
 */
@Composable
fun TapeListTab() {
    var mode by remember { mutableStateOf(TapeListMode.REGISTERED) }

    Column(Modifier.fillMaxSize().padding(top = 8.dp)) {
        TabRow(selectedTabIndex = mode.ordinal) {
            TapeListMode.values().forEach { m ->
                Tab(selected = mode == m, onClick = { mode = m }, text = { Text(m.label) })
            }
        }
        Box(Modifier.weight(1f).padding(16.dp)) {
            when (mode) {
                TapeListMode.REGISTERED -> RegisteredTapesList()
                TapeListMode.HOST_IMAGES -> HostImagesList()
            }
        }
    }
}

/** لیست تپه‌های ثبت‌شده در دیتابیس، با فیلتر متنی و فیلتر «فقط آزاد». */
@Composable
private fun RegisteredTapesList() {
    var all by remember { mutableStateOf<List<TapeRow>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var filter by remember { mutableStateOf("") }
    var onlyFree by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    suspend fun load() {
        loading = true; error = null
        try {
            val res = ApiClient.call("listTapes", JSONObject())
            val arr = res.getJSONArray("results")
            all = (0 until arr.length()).map { TapeRow.fromJson(arr.getJSONObject(it)) }
        } catch (e: ApiException) {
            error = e.message
        } catch (e: Exception) {
            error = "خطا در ارتباط با سرور."
        }
        loading = false
    }

    fun act(action: String, row: TapeRow) {
        scope.launch {
            try {
                ApiClient.call(action, JSONObject().put("id", row.id))
                load()
            } catch (e: ApiException) { error = e.message }
        }
    }

    LaunchedEffect(Unit) { load() }

    val shown = remember(all, filter, onlyFree) {
        val q = filter.trim()
        all.filter { r ->
            (q.isEmpty() || r.tape.contains(q, ignoreCase = true) || r.box.contains(q, ignoreCase = true)) &&
                (!onlyFree || r.status != STATUS_IN_USE)
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = filter, onValueChange = { filter = it },
                label = { Text("فیلتر بر اساس کد تپه یا باکس") },
                modifier = Modifier.weight(1f), singleLine = true
            )
            Spacer(Modifier.width(8.dp))
            OutlinedButton(onClick = { scope.launch { load() } }) { Text("تازه‌سازی") }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = onlyFree, onCheckedChange = { onlyFree = it })
            Text("فقط تپه‌های آزاد")
        }
        Text("تعداد: ${shown.size} از ${all.size}", style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(4.dp))

        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(shown, key = { it.id }) { row ->
                TapeCard(
                    row = row,
                    onTake = { act("takeTape", row) },
                    onReturn = { act("returnTape", row) }
                )
            }
        }
    }
}

/**
 * لیست همه‌ی فایل‌های عکس داخل GOL، BON و catalog روی هاست، مستقل از این‌که تپه‌ای برایشان
 * ثبت شده یا نه. وضعیت هر بار زنده از سرور خوانده می‌شود، پس با ثبت باکس برای یک تپه،
 * با تازه‌سازی همین‌جا وضعیتش به «تپه در باکس موجود است» تغییر می‌کند.
 */
@Composable
private fun HostImagesList() {
    var all by remember { mutableStateOf<List<HostImage>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var filter by remember { mutableStateOf("") }
    var onlyMissing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    suspend fun load() {
        loading = true; error = null
        try {
            val res = ApiClient.call("listHostImages", JSONObject())
            val arr = res.getJSONArray("images")
            all = (0 until arr.length()).map { HostImage.fromJson(arr.getJSONObject(it)) }
        } catch (e: ApiException) {
            error = e.message
        } catch (e: Exception) {
            error = "خطا در ارتباط با سرور."
        }
        loading = false
    }

    LaunchedEffect(Unit) { load() }

    val shown = remember(all, filter, onlyMissing) {
        val q = filter.trim()
        all.filter { img ->
            (q.isEmpty() || img.tapeCode.contains(q, ignoreCase = true) || img.fileName.contains(q, ignoreCase = true)) &&
                (!onlyMissing || img.status != HOST_IMAGE_STATUS_OK)
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = filter, onValueChange = { filter = it },
                label = { Text("فیلتر بر اساس کد تپه یا نام فایل") },
                modifier = Modifier.weight(1f), singleLine = true
            )
            Spacer(Modifier.width(8.dp))
            OutlinedButton(onClick = { scope.launch { load() } }) { Text("تازه‌سازی") }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = onlyMissing, onCheckedChange = { onlyMissing = it })
            Text("فقط تپه‌های ناموجود")
        }
        Text("تعداد: ${shown.size} از ${all.size}", style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(4.dp))

        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(shown, key = { it.folder + "/" + it.fileName }) { img -> HostImageCard(img) }
        }
    }
}

@Composable
private fun HostImageCard(img: HostImage) {
    val ok = img.status == HOST_IMAGE_STATUS_OK
    var showViewer by remember { mutableStateOf(false) }
    if (showViewer && img.imageUrl.isNotBlank()) {
        ImageViewerDialog(urls = listOf(img.imageUrl), onDismiss = { showViewer = false })
    }

    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(
                model = img.imageUrl,
                contentDescription = img.tapeCode,
                modifier = Modifier.size(64.dp).clickable { showViewer = true }
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(img.tapeCode.ifBlank { img.fileName }, style = MaterialTheme.typography.titleMedium)
                Text("پوشه: ${img.folder} — فایل: ${img.fileName}", style = MaterialTheme.typography.bodySmall)
                if (img.boxCode.isNotBlank()) {
                    Text("باکس: ${img.boxCode}", style = MaterialTheme.typography.bodySmall)
                }
                Text(
                    img.status,
                    color = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}
