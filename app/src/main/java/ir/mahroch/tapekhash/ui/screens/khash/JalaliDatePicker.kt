package ir.mahroch.tapekhash.ui.screens.khash

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

private val PERSIAN_MONTHS = listOf(
    "فروردین", "اردیبهشت", "خرداد", "تیر", "مرداد", "شهریور",
    "مهر", "آبان", "آذر", "دی", "بهمن", "اسفند"
)
private val JALALI_YEARS = (1395..1410).toList()

/** یک تاریخ شمسی که فقط با انتخاب از اسپینر ساخته می‌شود، نه تایپ. */
data class JalaliDate(val year: Int, val month: Int, val day: Int) {
    /** فرمتی که سرور می‌فهمد، مثل «1405/07/03». */
    fun toApiString(): String = "$year/${month.toString().padStart(2, '0')}/${day.toString().padStart(2, '0')}"
    fun toDisplayString(): String = "$day ${PERSIAN_MONTHS[month - 1]} $year"
}

/** دیالوگ انتخاب تاریخ شمسی با سه اسپینر روز/ماه/سال؛ هیچ فیلد تایپی ندارد. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JalaliDatePickerDialog(
    initial: JalaliDate?,
    onDismiss: () -> Unit,
    onConfirm: (JalaliDate) -> Unit
) {
    var year by remember { mutableStateOf(initial?.year ?: JALALI_YEARS[JALALI_YEARS.size - 2]) }
    var month by remember { mutableStateOf(initial?.month ?: 1) }
    var day by remember { mutableStateOf(initial?.day ?: 1) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("انتخاب تاریخ") },
        text = {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                DropdownSpinner(
                    label = "روز", value = day.toString(), options = (1..31).map { it.toString() },
                    onSelect = { day = it.toInt() }, modifier = Modifier.weight(0.8f)
                )
                DropdownSpinner(
                    label = "ماه", value = PERSIAN_MONTHS[month - 1], options = PERSIAN_MONTHS,
                    onSelect = { month = PERSIAN_MONTHS.indexOf(it) + 1 }, modifier = Modifier.weight(1.4f)
                )
                DropdownSpinner(
                    label = "سال", value = year.toString(), options = JALALI_YEARS.map { it.toString() },
                    onSelect = { year = it.toInt() }, modifier = Modifier.weight(1f)
                )
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(JalaliDate(year, month, day)) }) { Text("تأیید") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DropdownSpinner(
    label: String, value: String, options: List<String>, onSelect: (String) -> Unit, modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }, modifier = modifier) {
        OutlinedTextField(
            value = value, onValueChange = {}, readOnly = true,
            label = { Text(label) }, modifier = Modifier.menuAnchor(), singleLine = true
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { opt ->
                DropdownMenuItem(text = { Text(opt) }, onClick = { onSelect(opt); expanded = false })
            }
        }
    }
}

/**
 * ردیف «از تاریخ / تا تاریخ». با لمس هرکدام، دیالوگ انتخاب تاریخ باز می‌شود (بدون تایپ).
 * fromValue/toValue با فرمت API («1405/07/03») هستند یا خالی؛ onFromChange/onToChange با همین فرمت صدا زده می‌شوند.
 */
@Composable
fun JalaliDateRangeRow(
    fromValue: String,
    toValue: String,
    onFromChange: (String) -> Unit,
    onToChange: (String) -> Unit,
    onClear: () -> Unit
) {
    var pickingFrom by remember { mutableStateOf(false) }
    var pickingTo by remember { mutableStateOf(false) }

    if (pickingFrom) {
        JalaliDatePickerDialog(
            initial = null,
            onDismiss = { pickingFrom = false },
            onConfirm = { onFromChange(it.toApiString()); pickingFrom = false }
        )
    }
    if (pickingTo) {
        JalaliDatePickerDialog(
            initial = null,
            onDismiss = { pickingTo = false },
            onConfirm = { onToChange(it.toApiString()); pickingTo = false }
        )
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedButton(onClick = { pickingFrom = true }, modifier = Modifier.weight(1f)) {
            Text(if (fromValue.isBlank()) "از تاریخ" else fromValue)
        }
        Spacer(Modifier.width(8.dp))
        OutlinedButton(onClick = { pickingTo = true }, modifier = Modifier.weight(1f)) {
            Text(if (toValue.isBlank()) "تا تاریخ" else toValue)
        }
        if (fromValue.isNotBlank() || toValue.isNotBlank()) {
            IconButton(onClick = onClear) { Icon(Icons.Default.Close, contentDescription = "پاک کردن فیلتر تاریخ") }
        }
    }
}
