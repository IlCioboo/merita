package ro.merita.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar
import kotlin.math.max
import kotlin.math.min

/** O tură: de când ai început până când ai terminat (milisecunde). */
data class Shift(val start: Long, val end: Long)

/**
 * Turele de lucru: „Încep tura” / „Termin tura”. Din ele calculăm câștigul real pe oră,
 * cu tot cu așteptarea dintre comenzi (nu doar timpul curselor).
 */
object Shifts {
    private const val FILE = "merita_shifts"
    /** O tură uitată pornită nu numără mai mult de atât. */
    const val MAX_SHIFT_MS = 16 * 3_600_000L

    fun current(ctx: Context): Long? =
        sp(ctx).getLong("current", 0L).takeIf { it > 0 }

    fun start(ctx: Context, now: Long = System.currentTimeMillis()) {
        if (current(ctx) == null) sp(ctx).edit().putLong("current", now).apply()
    }

    fun stop(ctx: Context, now: Long = System.currentTimeMillis()) {
        val s = current(ctx) ?: return
        val list = all(ctx).toMutableList()
        if (now - s >= 60_000) list.add(Shift(s, min(now, s + MAX_SHIFT_MS)))
        save(ctx, list)
        sp(ctx).edit().remove("current").apply()
    }

    /** Turele terminate + cea în curs (până acum). */
    fun withRunning(ctx: Context, now: Long = System.currentTimeMillis()): List<Shift> {
        val cur = current(ctx) ?: return all(ctx)
        return all(ctx) + Shift(cur, min(now, cur + MAX_SHIFT_MS))
    }

    fun all(ctx: Context): List<Shift> {
        val raw = sp(ctx).getString("list", "[]") ?: "[]"
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                Shift(o.getLong("s"), o.getLong("e"))
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun save(ctx: Context, list: List<Shift>) {
        sp(ctx).edit().putString("list", toJson(list.sortedBy { it.start }.distinct()).toString()).apply()
    }

    /** Șterge turele care încep în intervalul dat (ex. o zi). */
    fun deleteStartingIn(ctx: Context, from: Long, to: Long) {
        save(ctx, all(ctx).filterNot { it.start in from until to })
        current(ctx)?.let { if (it in from until to) sp(ctx).edit().remove("current").apply() }
    }

    fun toJson(list: List<Shift>): JSONArray {
        val arr = JSONArray()
        list.forEach { arr.put(JSONObject().put("s", it.start).put("e", it.end)) }
        return arr
    }

    /** Minutele de tură care cad în intervalul [from, to). */
    fun minutesIn(shifts: List<Shift>, from: Long, to: Long): Int {
        var ms = 0L
        shifts.forEach { s ->
            val a = max(s.start, from)
            val b = min(s.end, to)
            if (b > a) ms += b - a
        }
        return (ms / 60_000).toInt()
    }

    /** Începutul și sfârșitul unei zile (pentru calendar). */
    fun dayRange(year: Int, month: Int, day: Int): Pair<Long, Long> {
        val c = Calendar.getInstance()
        c.clear()
        c.set(year, month, day, 0, 0, 0)
        val from = c.timeInMillis
        c.add(Calendar.DAY_OF_MONTH, 1)
        return from to c.timeInMillis
    }

    fun monthRange(year: Int, month: Int): Pair<Long, Long> {
        val c = Calendar.getInstance()
        c.clear()
        c.set(year, month, 1, 0, 0, 0)
        val from = c.timeInMillis
        c.add(Calendar.MONTH, 1)
        return from to c.timeInMillis
    }

    private fun sp(ctx: Context) = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)
}
