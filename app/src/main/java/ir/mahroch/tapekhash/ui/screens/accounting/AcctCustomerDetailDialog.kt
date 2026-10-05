package ir.mahroch.tapekhash.ui.screens.accounting

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import ir.mahroch.tapekhash.data.ApiClient
import ir.mahroch.tapekhash.data.normalizeDigits
import ir.mahroch.tapekhash.data.toNumberOrNull
import ir.mahroch.tapekhash.data.toNumber
import ir.mahroch.tapekhash.data.ApiException
import ir.mahroch.tapekhash.ui.screens.khash.fmt
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * جزئیات کامل یک مشتری حسابداری: مانده‌ی حساب، همه‌ی ثبت‌های چاپش با تاریخ، پرداخت‌های قبلی،
 * و اگر بدهکار باشد، فرم ثبت پرداخت جدید (که از مانده‌ی کل حسابش کم می‌شود).
 */
@Composable
fun AcctCustomerDetailDialog(customerId: Int, onDismiss: () -> Unit, onChanged: () -> Unit) {
    var data by remember { mutableStateOf<JSONObject?>(null) }
    var loading by remember { mutableStateOf(true) }
    var payAmount by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    suspend fun load() {
        loading = true
        try {
            data = ApiClient.call("getAcctCustomerDetail", JSONObject().put("customerId", customerId))
        } catch (e: Exception) { }
        loading = false
    }
    LaunchedEffect(customerId) { load() }

    val balance = data?.optDouble("balance") ?: 0.0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(data?.optJSONObject("customer")?.optString("name") ?: "جزئیات مشتری") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                data?.let { d ->
                    val phone = d.optJSONObject("customer")?.optString("phone") ?: ""
                    if (phone.isNotBlank()) Text(phone, style = MaterialTheme.typography.bodySmall)

                    Spacer(Modifier.height(8.dp))
                    Text("ثبت‌های چاپ", style = MaterialTheme.typography.titleSmall)
                    val invoices = d.optJSONArray("invoices")
                    if (invoices == null || invoices.length() == 0) {
                        Text("موردی ثبت نشده.", style = MaterialTheme.typography.bodySmall)
                    } else {
                        for (i in 0 until invoices.length()) {
                            val inv = invoices.getJSONObject(i)
                            Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                Column(Modifier.padding(8.dp)) {
                                    Text(inv.optString("jalaliDate"), style = MaterialTheme.typography.bodySmall)
                                    val items = inv.optJSONArray("items")
                                    if (items != null) for (j in 0 until items.length()) {
                                        val it = items.getJSONObject(j)
                                        Text(
                                            "${it.optString("printType")}: ${fmt(it.optDouble("qty"))} × ${fmt(it.optDouble("unitPrice"))} = ${fmt(it.optDouble("rowTotal"))}",
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(8.dp))
                    Text("پرداخت‌ها", style = MaterialTheme.typography.titleSmall)
                    val pays = d.optJSONArray("payments")
                    if (pays == null || pays.length() == 0) {
                        Text("پرداختی ثبت نشده.", style = MaterialTheme.typography.bodySmall)
                    } else {
                        for (i in 0 until pays.length()) {
                            val p = pays.getJSONObject(i)
                            Text(
                                "${fmt(p.optDouble("amount"))} — ${p.optString("jalaliDate")}",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }

                    Spacer(Modifier.height(8.dp))
                    Text(
                        "جمع ثبت‌ها: ${fmt(d.optDouble("totalAmount"))} — پرداختی: ${fmt(d.optDouble("totalPaid"))} — مانده: ${fmt(balance)}",
                        color = if (balance > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodyMedium
                    )

                    if (balance > 0) {
                        Spacer(Modifier.height(12.dp))
                        Divider()
                        Spacer(Modifier.height(8.dp))
                        Text("ثبت پرداخت جدید", style = MaterialTheme.typography.titleSmall)
                        OutlinedTextField(
                            value = payAmount, onValueChange = { payAmount = it.normalizeDigits() },
                            label = { Text("مبلغ") }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                        )
                        Spacer(Modifier.height(8.dp))
                        Button(
                            enabled = !saving,
                            onClick = {
                                val amt = payAmount.toNumberOrNull()
                                if (amt == null || amt <= 0) { error = "مبلغ نامعتبر است."; return@Button }
                                if (amt > balance + 0.001) { error = "مبلغ از مانده‌ی حساب بیشتر است."; return@Button }
                                saving = true; error = null
                                scope.launch {
                                    try {
                                        ApiClient.call(
                                            "addAcctPayment",
                                            JSONObject().put("customerId", customerId).put("amount", amt)
                                        )
                                        payAmount = ""
                                        load()
                                        onChanged()
                                    } catch (e: ApiException) {
                                        error = e.message
                                    } catch (e: Exception) {
                                        error = "خطا در ارتباط با سرور."
                                    }
                                    saving = false
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("ثبت پرداخت") }
                    }
                }
                error?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("بستن") } }
    )
}
