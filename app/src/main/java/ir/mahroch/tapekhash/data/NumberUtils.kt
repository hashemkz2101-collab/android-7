package ir.mahroch.tapekhash.data

import java.math.BigDecimal

/**
 * تبدیل ارقام فارسی (۰-۹) و عربی (٠-٩) به انگلیسی، حذف جداکننده‌ی هزارگان (, ٬ ، و فاصله)
 * و تبدیل ممیز فارسی (٫) به نقطه. خروجی همیشه رشته‌ای قابل‌تبدیل به عدد است.
 */
fun String.normalizeDigits(): String {
    val sb = StringBuilder(length)
    for (c in this) {
        when (c) {
            in '۰'..'۹' -> sb.append('0' + (c - '۰'))
            in '٠'..'٩' -> sb.append('0' + (c - '٠'))
            ',', '٬', '،', ' ', '\u00A0', '\u200C', '\u200F', '\u200E' -> Unit
            '٫' -> sb.append('.')
            else -> sb.append(c)
        }
    }
    return sb.toString()
}

/** تبدیل امن متن ورودی (با هر نوع ارقام) به عدد؛ اگر نامعتبر باشد null برمی‌گرداند. */
fun String.toNumberOrNull(): Double? = normalizeDigits().trim().toDoubleOrNull()

/** مثل [toNumberOrNull] ولی در صورت نامعتبر بودن استثنا می‌دهد. */
fun String.toNumber(): Double = normalizeDigits().trim().toDouble()

/** عدد را بدون جداکننده‌ی هزارگان و با ارقام انگلیسی برای نمایش داخل فیلد ورودی برمی‌گرداند. */
fun plainNumber(v: Double): String {
    if (v.isNaN() || v.isInfinite()) return ""
    return BigDecimal.valueOf(v).stripTrailingZeros().toPlainString()
}
