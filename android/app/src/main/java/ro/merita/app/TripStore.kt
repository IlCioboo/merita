package ro.merita.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Calendar

/**
 * O ofertă/cursă salvată. Valorile (cost, net) sunt calculate în momentul ofertei,
 * cu prețul la carburant de atunci, ca istoricul să nu se schimbe când modifici setările.
 */
data class Trip(
    val id: Long,
    val time: Long,
    val platform: String,
    val earnings: Double,
    val km: Double,
    val minutes: Int,
    val commission: Double,
    val cost: Double,
    val net: Double,
    val verdict: String,
    var status: String, // STATUS_*
) {
    val accepted get() = status == TripStore.ACCEPTED

    fun toJson(): JSONObject = JSONObject()
        .put("id", id).put("time", time).put("platform", platform).put("earnings", earnings)
        .put("km", km).put("minutes", minutes).put("commission", commission).put("cost", cost)
        .put("net", net).put("verdict", verdict).put("status", status)

    companion object {
        fun fromJson(o: JSONObject) = Trip(
            o.getLong("id"), o.getLong("time"), o.optString("platform"), o.optDouble("earnings", 0.0),
            o.optDouble("km", 0.0), o.optInt("minutes", 0), o.optDouble("commission", 0.0),
            o.optDouble("cost", 0.0), o.optDouble("net", 0.0), o.optString("verdict"),
            o.optString("status", TripStore.OFFER),
        )

        fun from(order: Order, r: Result, time: Long = System.currentTimeMillis()) = Trip(
            id = time, time = time, platform = order.platform, earnings = order.earnings ?: 0.0,
            km = r.kmTotal, minutes = r.minutes, commission = (order.earnings ?: 0.0) - r.gross,
            cost = r.cost, net = r.net, verdict = r.verdict.name, status = TripStore.OFFER,
        )
    }
}

data class Totals(
    val count: Int,
    val offers: Int,
    val earnings: Double,
    val commission: Double,
    val cost: Double,
    val net: Double,
    val km: Double,
    val minutes: Int,
) {
    val expenses get() = commission + cost
    val perHour get() = if (minutes > 0) net / (minutes / 60.0) else 0.0
}

/** Salvează cursele într-un fișier JSON în memoria aplicației (nu pleacă de pe telefon). */
object TripStore {
    const val OFFER = "offer"       // văzută, nu știm dacă ai luat-o
    const val ACCEPTED = "accepted" // ai luat-o
    const val REJECTED = "rejected" // ai refuzat-o

    private const val FILE = "trips.json"
    private const val KEEP_OFFERS_DAYS = 60
    private var cache: MutableList<Trip>? = null

    @Synchronized
    fun all(ctx: Context): List<Trip> = load(ctx).toList()

    @Synchronized
    fun add(ctx: Context, t: Trip) {
        val list = load(ctx)
        list.add(t)
        // Ofertele neconfirmate mai vechi de 60 de zile nu mai contează
        val limit = System.currentTimeMillis() - KEEP_OFFERS_DAYS * 86_400_000L
        list.removeAll { !it.accepted && it.time < limit }
        save(ctx, list)
    }

    @Synchronized
    fun setStatus(ctx: Context, id: Long, status: String) {
        val list = load(ctx)
        list.find { it.id == id }?.let { it.status = status; save(ctx, list) }
    }

    @Synchronized
    fun delete(ctx: Context, id: Long) {
        val list = load(ctx)
        if (list.removeAll { it.id == id }) save(ctx, list)
    }

    @Synchronized
    fun deleteMany(ctx: Context, ids: Set<Long>) {
        val list = load(ctx)
        if (list.removeAll { it.id in ids }) save(ctx, list)
    }

    /** Restaurare din backup: adaugă cursele lipsă, cele cu același id le înlocuiește. Întoarce câte erau noi. */
    @Synchronized
    fun importTrips(ctx: Context, trips: List<Trip>): Int {
        val list = load(ctx)
        val ids = list.map { it.id }.toSet()
        val fresh = trips.count { it.id !in ids }
        val incoming = trips.associateBy { it.id }
        list.removeAll { it.id in incoming }
        list.addAll(incoming.values)
        list.sortBy { it.time }
        save(ctx, list)
        return fresh
    }

    fun forDay(ctx: Context, year: Int, month: Int, day: Int): List<Trip> {
        val c = Calendar.getInstance()
        return all(ctx).filter {
            c.timeInMillis = it.time
            c.get(Calendar.YEAR) == year && c.get(Calendar.MONTH) == month && c.get(Calendar.DAY_OF_MONTH) == day
        }.sortedBy { it.time }
    }

    fun forMonth(ctx: Context, year: Int, month: Int): List<Trip> {
        val c = Calendar.getInstance()
        return all(ctx).filter {
            c.timeInMillis = it.time
            c.get(Calendar.YEAR) == year && c.get(Calendar.MONTH) == month
        }
    }

    /** Totalurile se fac doar din cursele acceptate. */
    fun totals(trips: List<Trip>): Totals {
        val acc = trips.filter { it.accepted }
        return Totals(
            count = acc.size,
            offers = trips.size,
            earnings = acc.sumOf { it.earnings },
            commission = acc.sumOf { it.commission },
            cost = acc.sumOf { it.cost },
            net = acc.sumOf { it.net },
            km = acc.sumOf { it.km },
            minutes = acc.sumOf { it.minutes },
        )
    }

    private fun load(ctx: Context): MutableList<Trip> {
        cache?.let { return it }
        val f = File(ctx.filesDir, FILE)
        val list = mutableListOf<Trip>()
        if (f.exists()) {
            try {
                val arr = JSONArray(f.readText())
                for (i in 0 until arr.length()) list.add(Trip.fromJson(arr.getJSONObject(i)))
            } catch (_: Exception) {
            }
        }
        cache = list
        return list
    }

    private fun save(ctx: Context, list: List<Trip>) {
        val arr = JSONArray()
        list.forEach { arr.put(it.toJson()) }
        val tmp = File(ctx.filesDir, "$FILE.tmp")
        tmp.writeText(arr.toString())
        tmp.renameTo(File(ctx.filesDir, FILE))
    }
}
