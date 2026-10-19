package fr.tpsarena.companion.data

import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

// fr-FR formatting, same output as the PWA's lib/format.ts. java.text rather than java.time:
// minSdk 24 has no java.time without desugaring.

object Format {
    private val FR = Locale.FRANCE

    // Not thread-safe: SimpleDateFormat and NumberFormat are created per call.
    private fun dateFormat(pattern: String) = SimpleDateFormat(pattern, FR).apply { timeZone = TimeZone.getDefault() }

    /** 12 345 with a narrow no-break space, like Intl.NumberFormat('fr-FR') (older Android uses U+00A0). */
    fun number(n: Long): String = NumberFormat.getIntegerInstance(FR).format(n).replace('\u00A0', '\u202F')

    fun number(n: Int): String = number(n.toLong())

    /** K/D ratio: up to two decimals, comma separator. */
    fun ratio(value: Double): String =
        NumberFormat.getNumberInstance(FR).apply { maximumFractionDigits = 2 }.format(value).replace('\u00A0', '\u202F')

    /** 16/10/2026 */
    fun date(iso: String): String = parseIso(iso)?.let { dateFormat("dd/MM/yyyy").format(it) } ?: iso

    /** 16/10 à 14:05 */
    fun dateTime(iso: String): String = parseIso(iso)?.let { dateTime(it.time) } ?: iso

    fun dateTime(epochMillis: Long): String = dateFormat("dd/MM 'à' HH:mm").format(Date(epochMillis))

    /** 1er, 2e, 3e… */
    fun ordinal(n: Int): String = if (n == 1) "1er" else "${n}e"

    /** 7 min 05 s */
    fun duration(startIso: String, endIso: String): String {
        val start = parseIso(startIso)?.time ?: return ""
        val end = parseIso(endIso)?.time ?: return ""
        val seconds = maxOf(0L, Math.round((end - start) / 1000.0))
        return "${seconds / 60} min ${(seconds % 60).toString().padStart(2, '0')} s"
    }

    /**
     * ISO 8601 timestamps as the backend sends them (Python isoformat): optional fraction of
     * up to 6 digits, `Z` or `±hh:mm`. Null if it doesn't parse.
     */
    fun parseIso(iso: String): Date? {
        val m = ISO.matchEntire(iso.trim()) ?: return null
        val (base, fraction, zone) = m.destructured
        val millis = fraction.padEnd(3, '0').take(3)
        val offset = when {
            zone.isEmpty() || zone == "Z" || zone == "z" -> "+0000"
            else -> zone.replace(":", "")
        }
        return try {
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.ROOT).parse("${base.replace(' ', 'T')}.$millis$offset")
        } catch (_: java.text.ParseException) {
            null
        }
    }

    /** Epoch millis of an ISO timestamp, 0 if it doesn't parse (for sorting). */
    fun epochMillis(iso: String): Long = parseIso(iso)?.time ?: 0L

    private val ISO = Regex("""(\d{4}-\d{2}-\d{2}[T ]\d{2}:\d{2}:\d{2})(?:\.(\d{1,9}))?(Z|z|[+-]\d{2}:?\d{2})?""")
}
