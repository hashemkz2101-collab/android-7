package ir.mahroch.tapekhash.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

class ApiException(message: String) : Exception(message)

object ApiClient {
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val JSON: MediaType = "application/json; charset=utf-8".toMediaType()

    /** فراخوانی یک اکشن با بدنه‌ی JSON. اگر auth=true باشد، توکن نشست به هدر اضافه می‌شود. */
    suspend fun call(action: String, body: JSONObject = JSONObject(), auth: Boolean = true): JSONObject =
        withContext(Dispatchers.IO) {
            val url = Session.baseUrl + "?action=" + action
            val reqBuilder = Request.Builder()
                .url(url)
                .post(body.toString().toRequestBody(JSON))

            if (auth) {
                Session.token?.let { reqBuilder.addHeader("Authorization", "Bearer $it") }
            }

            client.newCall(reqBuilder.build()).execute().use { resp ->
                val text = resp.body?.string() ?: "{}"
                val json = try { JSONObject(text) } catch (e: Exception) {
                    throw ApiException("پاسخ سرور نامعتبر بود (کد ${resp.code}).")
                }
                if (!json.optBoolean("ok", false)) {
                    throw ApiException(json.optString("error", "خطای نامشخص از سرور."))
                }
                json
            }
        }

    /** آپلود فایل چندبخشی (عکس تپه/کاتالوگ یا اکسل ایمپورت) به یک آدرس مطلق */
    suspend fun uploadMultipart(
        url: String,
        fileField: String,
        file: File,
        mimeType: String,
        extraFields: Map<String, String> = emptyMap(),
        auth: Boolean = false
    ): JSONObject = withContext(Dispatchers.IO) {
        val bodyBuilder = MultipartBody.Builder().setType(MultipartBody.FORM)
        extraFields.forEach { (k, v) -> bodyBuilder.addFormDataPart(k, v) }
        val fileBody: RequestBody = file.asRequestBody(mimeType.toMediaType())
        bodyBuilder.addFormDataPart(fileField, file.name, fileBody)

        val reqBuilder = Request.Builder().url(url).post(bodyBuilder.build())
        if (auth) Session.token?.let { reqBuilder.addHeader("Authorization", "Bearer $it") }

        client.newCall(reqBuilder.build()).execute().use { resp ->
            val text = resp.body?.string() ?: "{}"
            val json = try { JSONObject(text) } catch (e: Exception) {
                throw ApiException("پاسخ سرور نامعتبر بود (کد ${resp.code}).")
            }
            val ok = json.optBoolean("ok", json.optBoolean("success", json.optString("status") == "success"))
            if (!ok) {
                throw ApiException(json.optString("error", json.optString("message", "خطا در آپلود فایل.")))
            }
            json
        }
    }
}
