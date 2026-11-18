package ro.merita.app

import java.util.Calendar
import java.util.Locale

/** Statistici din curse: pe aplicații, pe zilele săptămânii și pe ore. Doar cursele luate contează la bani. */
object Stats {

    private data class Group(val count: Int, val net: Double, val minutes: Int, val km: Double) {
        val perHour get() = if (minutes > 0) net / (minutes / 60.0) else 0.0
        val perKm get() = if (km > 0) net / km else 0.0
    }

    private fun group(trips: List<Trip>) = Group(
        trips.size, trips.sumOf { it.net }, trips.sumOf { it.minutes }, trips.sumOf { it.km },
    )

    private fun f0(v: Double) = String.format(Locale.US, "%.0f", v)
    private fun f2(v: Double) = String.format(Locale.US, "%.2f", v)

    private fun line(label: String, g: Group) =
        String.format(Locale.US, "%-9s %3d curse · net %5s · %3s RON/h · %s RON/km", label, g.count, f0(g.net), f0(g.perHour), f2(g.perKm))

    /**
     * @param trips toate ofertele din perioadă (luate și nu)
     * @param shiftMinutes minutele de tură din perioadă (0 = nu ai pornit tura)
     */
    fun report(title: String, trips: List<Trip>, shiftMinutes: Int): String {
        val acc = trips.filter { it.accepted }
        if (acc.isEmpty()) return "$title\n\nNicio cursă luată în perioada asta."
        val all = group(acc)
        val sb = StringBuilder()
        sb.append(title).append("\n\n")
        sb.append("Curse luate: ${acc.size} din ${trips.size} oferte văzute\n")
        sb.append("Profit net: ${f2(all.net)} RON · ${f2(all.perKm)} RON/km\n")
        sb.append("Pe timpul curselor: ${f0(all.perHour)} RON/oră\n")
        if (shiftMinutes > 0) {
            sb.append("Pe tură, cu așteptarea: ${f0(all.net / (shiftMinutes / 60.0))} RON/oră")
            sb.append(" (${shiftMinutes / 60} h ${shiftMinutes % 60} min de tură)\n")
        }

        sb.append("\nPE APLICAȚII\n")
        acc.groupBy { it.platform.ifEmpty { "?" } }
            .map { (k, v) -> k to group(v) }
            .sortedByDescending { it.second.perHour }
            .forEach { (k, g) ->
                val seen = trips.count { it.platform.ifEmpty { "?" } == k }
                sb.append(line(k.take(9), g)).append(" · luate ${g.count}/$seen\n")
            }

        val c = Calendar.getInstance()
        sb.append("\nPE ZILE\n")
        val byDay = acc.groupBy { c.timeInMillis = it.time; (c.get(Calendar.DAY_OF_WEEK) + 5) % 7 }
        (0..6).forEach { d -> byDay[d]?.let { sb.append(line(WEEKDAYS[d], group(it))).append('\n') } }

        sb.append("\nPE ORE\n")
        val byHour = acc.groupBy { c.timeInMillis = it.time; c.get(Calendar.HOUR_OF_DAY) / 2 * 2 }
        byHour.keys.sorted().forEach { h ->
            sb.append(line(String.format(Locale.US, "%02d–%02d", h, h + 2), group(byHour.getValue(h)))).append('\n')
        }
        val best = byHour.filter { it.value.size >= 2 }.maxByOrNull { group(it.value).perHour }
        if (best != null) {
            sb.append(String.format(Locale.US, "→ Cele mai bune ore: %02d–%02d (%s RON/h)\n", best.key, best.key + 2, f0(group(best.value).perHour)))
        }

        val top = acc.maxByOrNull { it.net }!!
        val low = acc.minByOrNull { it.net }!!
        sb.append("\nCea mai bună cursă: ${top.platform} net ${f2(top.net)} RON (${f2(top.earnings)} RON, ${f2(top.km)} km)\n")
        sb.append("Cea mai slabă cursă: ${low.platform} net ${f2(low.net)} RON (${f2(low.earnings)} RON, ${f2(low.km)} km)")
        return sb.toString()
    }

    private val WEEKDAYS = listOf("Luni", "Marți", "Miercuri", "Joi", "Vineri", "Sâmbătă", "Duminică")
}
