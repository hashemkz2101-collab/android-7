package ir.mahroch.tapekhash.data

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

data class AppUser(
    val id: Int,
    val username: String,
    val displayName: String,
    val role: String,
    val apps: List<String>
) {
    val isAdmin get() = role == "admin"
    fun hasApp(app: String) = isAdmin || apps.contains(app)

    companion object {
        fun fromJson(o: JSONObject): AppUser {
            val apps = mutableListOf<String>()
            val arr: JSONArray? = o.optJSONArray("apps")
            if (arr != null) for (i in 0 until arr.length()) apps.add(arr.getString(i))
            return AppUser(
                id = o.optInt("id"),
                username = o.optString("username"),
                displayName = o.optString("display_name"),
                role = o.optString("role"),
                apps = apps
            )
        }
    }
}

object Session {
    private const val PREFS = "tapekhash_session"
    private lateinit var prefs: SharedPreferences

    // آدرس پیش‌فرض بک‌اند - اگر پوشه‌ی روی هاست را عوض کردید همین‌جا (یا در صفحه‌ی ورود) تغییر دهید
    const val DEFAULT_BASE_URL = "https://mahroch.ir/tape-khash-api/index.php"

    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    }

    var baseUrl: String
        get() = prefs.getString("base_url", DEFAULT_BASE_URL) ?: DEFAULT_BASE_URL
        set(value) = prefs.edit().putString("base_url", value).apply()

    var token: String?
        get() = prefs.getString("token", null)
        set(value) = prefs.edit().putString("token", value).apply()

    var rememberToken: String?
        get() = prefs.getString("remember_token", null)
        set(value) = prefs.edit().putString("remember_token", value).apply()

    var user: AppUser? = null

    fun saveUser(o: JSONObject) {
        user = AppUser.fromJson(o)
        prefs.edit().putString("user_json", o.toString()).apply()
    }

    fun loadCachedUser(): AppUser? {
        if (user != null) return user
        val s = prefs.getString("user_json", null) ?: return null
        return try {
            user = AppUser.fromJson(JSONObject(s))
            user
        } catch (e: Exception) { null }
    }

    fun clear() {
        token = null
        rememberToken = null
        user = null
        prefs.edit().remove("user_json").apply()
    }

    val isLoggedIn get() = token != null
}
