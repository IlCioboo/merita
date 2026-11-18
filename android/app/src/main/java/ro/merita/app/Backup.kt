package ro.merita.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar
import java.util.Locale

/** Backup (JSON cu tot: curse, ture, setări) și export pentru Excel (CSV). */
object Backup {

    fun toJson(ctx: Context): String {
        val trips = JSONArray()
        TripStore.all(ctx).forEach { trips.put(it.toJson()) }
        val s = Settings.load(ctx)
        val settings = JSONObject()
            .put("vehicle", s.vehicle).put("packages", s.packages)
            .put("autoRead", s.autoRead).put("floatButton", s.floatButton)
        Settings.NUMERIC.forEach { settings.put(it, s.get(it)) }
        return JSONObject()
            .put("app", "merita")
            .put("version", 1)
            .put("created", System.currentTimeMillis())
            .put("trips", trips)
            .put("shifts", Shifts.toJson(Shifts.all(ctx)))
            .put("settings", settings)
            .toString(1)
    }

    /** Ce s-a restaurat, pentru mesajul de confirmare. */
    data class Restored(val trips: Int, val newTrips: Int, val shifts: Int)

    /** Unește backup-ul cu ce e pe telefon (nu șterge nimic) și pune setările din backup. */
    fun restore(ctx: Context, text: String): Restored {
        val o = JSONObject(text)
        require(o.optString("app") == "merita") { "Fișierul nu e un backup Merită" }

        val arr = o.optJSONArray("trips") ?: JSONArray()
        val trips = (0 until arr.length()).map { Trip.fromJson(arr.getJSONObject(it)) }
        val newTrips = TripStore.importTrips(ctx, trips)

        val sa = o.optJSONArray("shifts") ?: JSONArray()
        val shifts = (0 until sa.length()).map {
            val x = sa.getJSONObject(it)
            Shift(x.getLong("s"), x.getLong("e"))
        }
        Shifts.save(ctx, Shifts.all(ctx) + shifts)

        o.optJSONObject("settings")?.let { j ->
            val s = Settings.load(ctx)
            s.vehicle = j.optString("vehicle", s.vehicle)
            s.packages = j.optString("packages", s.packages)
            s.autoRead = j.optBoolean("autoRead", s.autoRead)
            s.floatButton = j.optBoolean("floatButton", s.floatButton)
            Settings.NUMERIC.forEach { k -> if (j.has(k)) s.set(k, j.optDouble(k, s.get(k))) }
            Settings.save(ctx, s)
        }
        return Restored(trips.size, newTrips, shifts.size)
    }

    /**
     * Tabel pentru Excel: separator „;” și virgulă zecimală (cum îl deschide Excel în română),
     * cu BOM ca diacriticele să apară corect.
     */
    fun csv(trips: List<Trip>): String {
        val c = Calendar.getInstance()
        fun n(v: Double) = String.format(Locale.US, "%.2f", v).replace('.', ',')
        val sb = StringBuilder("﻿")
        sb.append("Data;Ora;Aplicație;Stare;Câștig RON;Km;Minute;Comision RON;Cost drum RON;Profit net RON;Verdict\r\n")
        trips.sortedBy { it.time }.forEach { t ->
            c.timeInMillis = t.time
            val stare = when (t.status) {
                TripStore.ACCEPTED -> "luată"
                TripStore.REJECTED -> "refuzată"
                else -> "doar văzută"
            }
            val verdict = when (t.verdict) {
                "GOOD" -> "merită"; "MEH" -> "la limită"; "BAD" -> "nu merită"; else -> t.verdict
            }
            sb.append(
                String.format(
                    Locale.US, "%04d-%02d-%02d;%02d:%02d;",
                    c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH),
                    c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE),
                ),
            )
            sb.append(t.platform.replace(";", ",")).append(';').append(stare).append(';')
                .append(n(t.earnings)).append(';').append(n(t.km)).append(';').append(t.minutes).append(';')
                .append(n(t.commission)).append(';').append(n(t.cost)).append(';').append(n(t.net)).append(';')
                .append(verdict).append("\r\n")
        }
        return sb.toString()
    }

    /** Nume de fișier cu data de azi, ex. merita-backup-2026-11-10.json */
    fun fileName(prefix: String, ext: String): String {
        val c = Calendar.getInstance()
        return String.format(
            Locale.US, "%s-%04d-%02d-%02d.%s",
            prefix, c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH), ext,
        )
    }
}
