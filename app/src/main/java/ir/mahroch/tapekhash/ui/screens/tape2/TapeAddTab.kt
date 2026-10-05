package ir.mahroch.tapekhash.ui.screens.tape2

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.compose.rememberAsyncImagePainter
import ir.mahroch.tapekhash.data.ApiClient
import ir.mahroch.tapekhash.data.ApiException
import ir.mahroch.tapekhash.data.Session
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File

@Composable
fun TapeAddTab() {
    var boxCode by remember { mutableStateOf("") }
    var tapeCode by remember { mutableStateOf("") }
    var imageUri by remember { mutableStateOf<Uri?>(null) }
    var loading by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var messageIsWarning by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var previewUrl by remember { mutableStateOf("") }
    var previewLoading by remember { mutableStateOf(false) }
    var previewExistingBox by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        imageUri = uri
    }

    // با تایپ کد تپه، بعد از کمی مکث، عکسش را از سرور (GOL/BON یا تپه‌ی از قبل ثبت‌شده) پیش‌نمایش بده
    // تا قبل از ثبت باکس اشتباه، کاربر مطمئن شود این همان تپه‌ی مدنظرش است.
    LaunchedEffect(tapeCode) {
        val code = tapeCode.trim()
        previewUrl = ""; previewExistingBox = ""
        if (!Regex("^[BGbg][0-9]+$").matches(code)) return@LaunchedEffect
        delay(400) // منتظر بمان تا کاربر تایپش تمام شود
        previewLoading = true
        try {
            val res = ApiClient.call("getTapeRegistrationInfo2", JSONObject().put("tapeCode", code))
            previewUrl = res.optString("imageUrl")
            if (res.optBoolean("exists")) previewExistingBox = res.optString("box")
        } catch (e: Exception) {
            // پیش‌نمایش اختیاری است؛ خطایش مانع فرم اصلی نشود
        }
        previewLoading = false
    }

    fun submit() {
        if (boxCode.isBlank() || tapeCode.isBlank()) {
            error = "کد باکس و کد تپه را وارد کنید."
            return
        }
        if (!Regex("^[BGbg][0-9]+$").matches(tapeCode.trim())) {
            error = "کد تپه باید مانند G1 یا B22 باشد."
            return
        }
        loading = true; error = null; message = null

        scope.launch {
            try {
                var imageCode = ""

                if (imageUri != null) {
                    // ۱. فایل انتخاب‌شده را به یک فایل موقت کپی کن
                    val mime = context.contentResolver.getType(imageUri!!) ?: "image/jpeg"
                    val ext = when {
                        mime.contains("png") -> "png"
                        mime.contains("webp") -> "webp"
                        else -> "jpg"
                    }
                    val tempFile = File(context.cacheDir, "upload_${System.currentTimeMillis()}.$ext")
                    context.contentResolver.openInputStream(imageUri!!)?.use { input ->
                        tempFile.outputStream().use { output -> input.copyTo(output) }
                    }

                    // ۲. آپلود از طریق API اصلی (با توکن نشست کاربر، نه کلید مخفی جدا)
                    val uploadRes = ApiClient.uploadMultipart(
                        url = Session.baseUrl + "?action=uploadTapeImage2",
                        fileField = "file",
                        file = tempFile,
                        mimeType = mime,
                        auth = true
                    )
                    imageCode = uploadRes.optString("name")
                    tempFile.delete()
                }

                // ۴. ثبت/به‌روزرسانی تپه با کد عکس (اگر عکسی انتخاب نشده بود، امکان دارد سرور
                // به‌صورت خودکار عکس موجود در هاست GOL/BON را بعداً پیدا کند)
                val body = JSONObject()
                    .put("boxCode", boxCode.trim())
                    .put("tapeCode", tapeCode.trim())
                if (imageCode.isNotBlank()) body.put("imageCode", imageCode)
                else body.put("imageCode", tapeCode.trim()) // برای Gxxx/Bxxx خودش کد عکس معتبر است

                val res = ApiClient.call("addTape2", body)
                val boxMismatch = res.optString("mode") == "image-added" && res.optBoolean("boxMismatch")
                messageIsWarning = boxMismatch
                message = when {
                    boxMismatch ->
                        "این تپه (${res.optString("tape")}) قبلاً در باکس «${res.optString("box")}» ثبت شده بود، " +
                            "نه در باکس «${res.optString("enteredBox")}» که وارد کردید. " +
                            "عکس جدید به همین تپه در باکس «${res.optString("box")}» اضافه شد؛ برای تغییر باکس این تپه از بخش «ویرایش تپه» در پنل مدیر استفاده کنید."
                    res.optString("mode") == "image-added" ->
                        "عکس جدید به تپه‌ی موجود اضافه شد."
                    else ->
                        "تپه با موفقیت ثبت شد."
                }

                boxCode = ""; tapeCode = ""; imageUri = null
            } catch (e: ApiException) {
                error = e.message
            } catch (e: Exception) {
                error = "خطا در ارتباط با سرور."
            } finally {
                loading = false
            }
        }
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("ثبت تپه‌ی جدید یا افزودن عکس", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value = boxCode, onValueChange = { boxCode = it },
            label = { Text("کد باکس") }, modifier = Modifier.fillMaxWidth(), singleLine = true
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = tapeCode, onValueChange = { tapeCode = it },
            label = { Text("کد تپه (مثل G12 یا B34)") }, modifier = Modifier.fillMaxWidth(), singleLine = true
        )

        if (previewLoading) {
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(Modifier.fillMaxWidth())
        }
        var showPreviewViewer by remember { mutableStateOf(false) }
        if (showPreviewViewer && previewUrl.isNotBlank()) {
            ImageViewerDialog(urls = listOf(previewUrl), onDismiss = { showPreviewViewer = false })
        }
        if (previewUrl.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                AsyncImage(
                    model = previewUrl,
                    contentDescription = "پیش‌نمایش عکس تپه‌ی «$tapeCode»",
                    modifier = Modifier.size(90.dp).clickable { showPreviewViewer = true }
                )
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("این عکسِ کد «${tapeCode.trim()}» روی هاست است — قبل از ثبت مطمئن شوید همین تپه‌ی مدنظرتان است.")
                    if (previewExistingBox.isNotBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "این تپه قبلاً در باکس «$previewExistingBox» ثبت شده است.",
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))

        OutlinedButton(onClick = { pickImage.launch("image/*") }, modifier = Modifier.fillMaxWidth()) {
            Text(if (imageUri == null) "انتخاب عکس از گالری (اختیاری)" else "تغییر عکس انتخاب‌شده")
        }

        imageUri?.let { uri ->
            Spacer(Modifier.height(8.dp))
            Image(
                painter = rememberAsyncImagePainter(uri),
                contentDescription = null,
                modifier = Modifier.size(140.dp)
            )
        }

        if (error != null) {
            Spacer(Modifier.height(8.dp))
            Text(error!!, color = MaterialTheme.colorScheme.error)
        }
        if (message != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                message!!,
                color = if (messageIsWarning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
            )
        }

        Spacer(Modifier.height(16.dp))
        Button(onClick = { submit() }, enabled = !loading, modifier = Modifier.fillMaxWidth()) {
            if (loading) CircularProgressIndicator(modifier = Modifier.size(20.dp)) else Text("ثبت")
        }
    }
}
