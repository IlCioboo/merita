package ro.merita.app

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/** Ce s-a citit din comanda de pe ecran. Null = nu s-a gasit. */
data class Order(
    val platform: String,
    val earnings: Double?,
    val km: Double?,
    val minutes: Double?,
) {
    /** Destul ca să calculăm: câștigul + km sau măcar minutele (Bolt Food nu arată km). */
    val ready get() = earnings != null && (km != null || (minutes ?: 0.0) > 0)
}

enum class Verdict { GOOD, MEH, BAD }

data class Result(
    val kmTotal: Double,
    val minutes: Int,
    val costPerKm: Double,
    val cost: Double,
    val gross: Double,
    val net: Double,
    val perKm: Double,
    val perHour: Double,
    val verdict: Verdict,
    val estimatedTime: Boolean,
    val estimatedKm: Boolean = false,
    /** Cât ar trebui să plătească comanda ca să treacă ambele praguri (RON/km și RON/oră). */
    val minEarnings: Double = 0.0,
)

/** Citirea textului + calculul. Aceeasi logica ca in core.js din aplicatia web. */
object Core {
    private const val NUM = """(\d{1,4}(?:[.,]\d{1,2})?)"""
    private val MONEY = Regex(
        """(?:\b(?:lei|ron|eur)\b|€)\s*$NUM|$NUM\s*(?:\b(?:lei|ron|eur)\b|€)""",
        RegexOption.IGNORE_CASE,
    )
    private val KM = Regex("""$NUM\s*km\b""", RegexOption.IGNORE_CASE)
    private val METERS = Regex("""(\d{2,4})\s*m\b""", RegexOption.IGNORE_CASE)
    private val TIME = Regex(
        """(?:(\d{1,2})\s*(?:h|ore|oră|ora|hr)\s*)?(\d{1,3})\s*(?:min|minute|mins)\b""",
        RegexOption.IGNORE_CASE,
    )
    private val TOTAL_WORDS = Regex("""(total|în total|in total|overall|traseu|trip)""", RegexOption.IGNORE_CASE)

    // Sume care NU sunt castigul comenzii (castigul zilei, sold etc.)
    private val NOT_ORDER_MONEY = Regex(
        """(azi|astăzi|astazi|today|săptămân|saptaman|week|sold|balance|câștigat|castigat|earned)""",
        RegexOption.IGNORE_CASE,
    )
    const val MAX_ORDER_EARNINGS = 300.0

    // Suma fără monedă: 2 zecimale, nu face parte dintr-o oră și nu e urmată de km/m/min
    private val BARE_MONEY = Regex(
        """(^|[^\d:.,])(\d{1,3}[.,]\d{2})(?![\d:.,])(?!\s*(?:km\b|m\b|min))""",
        RegexOption.IGNORE_CASE,
    )

    // Rândurile din propriile noastre notificări/bule, dacă apar pe ecran
    private val OWN = Regex("""(RON/km|RON/h|MERITĂ|LA LIMITĂ|Am citit|Nu am găsit)""", RegexOption.IGNORE_CASE)

    private data class Hit(val value: Double, val line: Int)

    private fun num(s: String) = s.replace(',', '.').toDoubleOrNull()

    private fun collect(lines: List<String>, re: Regex, map: (MatchResult) -> Double?): List<Hit> =
        lines.flatMapIndexed { i, line ->
            re.findAll(line).mapNotNull { m ->
                val v = map(m)
                if (v != null && v.isFinite() && v > 0) Hit(v, i) else null
            }.toList()
        }

    /**
     * Daca exista o valoare marcata "total" (sau pe acelasi rand cu suma, ca butonul
     * Bolt „24 min, 22,73 RON”), o folosim. Daca cea mai mare e chiar suma celorlalte
     * (11 + 13 = 24), e totalul. Altfel adunam tot (ridicare + livrare).
     */
    private fun totalOrSum(items: List<Hit>, lines: List<String>): Double? {
        if (items.isEmpty()) return null
        val marked = items.filter { h ->
            TOTAL_WORDS.containsMatchIn(lines[h.line]) ||
                MONEY.containsMatchIn(lines[h.line]) ||
                (h.line > 0 && lines[h.line - 1].length < 25 && TOTAL_WORDS.containsMatchIn(lines[h.line - 1]))
        }
        if (marked.isNotEmpty()) return marked.maxOf { it.value }
        if (items.size >= 3) {
            val top = items.maxOf { it.value }
            val rest = items.sumOf { it.value } - top
            if (abs(top - rest) <= max(1.0, top * 0.06)) return top
        }
        return items.sumOf { it.value }
    }

    fun detectPlatform(text: String, pkg: String = ""): String {
        val t = (text + " " + pkg).lowercase()
        return when {
            "glovo" in t -> "Glovo"
            "wolt" in t -> "Wolt"
            "uber" in t -> "Uber Eats"
            "bolt" in t -> "Bolt Food"
            else -> ""
        }
    }

    /** Textul conține o sumă cu lei/RON (ex. butonul de acceptare Bolt „24 min, 22,73 RON”). */
    fun hasMoney(text: String) = MONEY.containsMatchIn(text)

    fun parseOrder(raw: String, pkg: String = ""): Order {
        val text = raw.replace(' ', ' ').replace("€", " € ")
        val lines = text.split(Regex("\r?\n")).filterNot { OWN.containsMatchIn(it) }

        val okMoney = { h: Hit -> h.value <= MAX_ORDER_EARNINGS && !NOT_ORDER_MONEY.containsMatchIn(lines[h.line]) }
        var money = collect(lines, MONEY) { m ->
            num(m.groupValues[1].ifEmpty { m.groupValues[2] })
        }.filter(okMoney)
        if (money.isEmpty()) {
            money = collect(lines, BARE_MONEY) { num(it.groupValues[2]) }.filter(okMoney)
        }

        val kms = collect(lines, KM) { num(it.groupValues[1]) } +
            collect(lines, METERS) { m -> num(m.groupValues[1])?.div(1000) }

        val times = collect(lines, TIME) { m ->
            val h = m.groupValues[1].toIntOrNull() ?: 0
            (h * 60 + (m.groupValues[2].toIntOrNull() ?: 0)).toDouble()
        }

        return Order(
            platform = detectPlatform(text, pkg),
            earnings = money.maxOfOrNull { it.value },
            km = totalOrSum(kms, lines)?.let { round(it, 2) },
            minutes = totalOrSum(times, lines),
        )
    }

    fun calculate(order: Order, s: Settings): Result {
        val earnings = order.earnings ?: 0.0
        val hasTime = (order.minutes ?: 0.0) > 0
        // Fără km pe ecran (Bolt Food): îi estimăm din minute cu viteza medie din setări
        val estimatedKm = order.km == null && hasTime
        val km = order.km ?: if (hasTime) order.minutes!! / 60 * max(s.speed, 1.0) else 0.0
        val kmTotal = km * (1 + s.returnPct / 100)
        val costPerKm = s.consumption / 100 * s.price + s.maint
        val driveMin = if (hasTime) order.minutes!! else kmTotal / max(s.speed, 1.0) * 60
        val minutes = driveMin + s.waitMin

        val gross = earnings * (1 - s.commissionPct / 100)
        val cost = kmTotal * costPerKm
        val net = gross - cost
        val perKm = if (kmTotal > 0) net / kmTotal else 0.0
        val perHour = if (minutes > 0) net / (minutes / 60) else 0.0

        // Prețul minim: profitul cerut de praguri + costul drumului, înainte de comision
        val needNet = max(s.minPerKm * kmTotal, s.minPerHour * minutes / 60)
        val keep = 1 - s.commissionPct / 100
        val minEarnings = if (keep > 0.01) (needNet + cost) / keep else 0.0

        val okKm = perKm >= s.minPerKm
        val okHour = perHour >= s.minPerHour
        val verdict = when {
            okKm && okHour -> Verdict.GOOD
            okKm || okHour -> Verdict.MEH
            else -> Verdict.BAD
        }
        return Result(
            kmTotal = round(kmTotal, 2),
            minutes = minutes.roundToInt(),
            costPerKm = round(costPerKm, 2),
            cost = round(cost, 2),
            gross = round(gross, 2),
            net = round(net, 2),
            perKm = round(perKm, 2),
            perHour = round(perHour, 1),
            verdict = verdict,
            estimatedTime = !hasTime,
            estimatedKm = estimatedKm,
            minEarnings = round(minEarnings, 2),
        )
    }

    private fun round(v: Double, d: Int): Double {
        var f = 1.0
        repeat(d) { f *= 10 }
        return (v * f).roundToInt() / f
    }
}
