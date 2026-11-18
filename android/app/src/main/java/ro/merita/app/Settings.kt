package ro.merita.app

import android.content.Context

data class Vehicle(
    val key: String,
    val label: String,
    val unit: String,
    val consumption: Double,
    val price: Double,
    val maint: Double,
    val speed: Double,
)

val VEHICLES = listOf(
    Vehicle("benzina", "Mașină benzină", "l", 7.0, 7.5, 0.15, 25.0),
    Vehicle("motorina", "Mașină motorină", "l", 6.0, 7.6, 0.15, 25.0),
    Vehicle("gpl", "Mașină GPL", "l", 9.0, 3.6, 0.15, 25.0),
    Vehicle("electrica", "Mașină electrică", "kWh", 16.0, 1.5, 0.08, 25.0),
    Vehicle("scuter", "Scuter / moto", "l", 3.0, 7.5, 0.08, 25.0),
    Vehicle("ebike", "Bicicletă electrică", "kWh", 1.5, 1.2, 0.03, 18.0),
    Vehicle("bicicleta", "Bicicletă", "l", 0.0, 0.0, 0.02, 15.0),
)

/** Aplicatiile de curier urmarite implicit (Glovo Couriers, Wolt Courier Partner, Uber Driver, Bolt Courier). */
const val BOLT_PACKAGE = "com.bolt.deliverycourier"
const val DEFAULT_PACKAGES = "com.glovoapp.courier, com.wolt.courierapp, com.ubercab.driver, $BOLT_PACKAGE"

data class Settings(
    var vehicle: String = "benzina",
    var consumption: Double = 7.0,   // l sau kWh / 100 km
    var price: Double = 7.5,         // RON / l sau kWh
    var maint: Double = 0.15,        // RON / km uzura
    var speed: Double = 25.0,        // km/h, cand comanda nu arata timpul
    var waitMin: Double = 5.0,       // minute asteptare la restaurant
    var returnPct: Double = 0.0,     // % km in plus pentru intoarcere
    var commissionPct: Double = 0.0, // % comision flota / taxe
    var minPerKm: Double = 1.5,      // prag RON net / km
    var minPerHour: Double = 30.0,   // prag RON net / ora
    var autoHideSec: Double = 25.0,  // dupa cate secunde dispare bula
    var packages: String = DEFAULT_PACKAGES,
    var autoRead: Boolean = true,      // citește singur ofertele din aplicațiile urmărite
    var floatButton: Boolean = false,  // buton plutitor de scanare la cerere
) {
    fun packageSet(): Set<String> =
        packages.split(',', ' ', '\n', ';').map { it.trim() }.filter { it.isNotEmpty() }.toSet()

    companion object {
        private const val FILE = "merita"
        val NUMERIC = listOf(
            "consumption", "price", "maint", "speed", "waitMin", "returnPct",
            "commissionPct", "minPerKm", "minPerHour", "autoHideSec",
        )

        fun load(ctx: Context): Settings {
            val sp = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            val s = Settings()
            s.vehicle = sp.getString("vehicle", s.vehicle) ?: s.vehicle
            s.packages = sp.getString("packages", s.packages) ?: s.packages
            s.autoRead = sp.getBoolean("autoRead", s.autoRead)
            s.floatButton = sp.getBoolean("floatButton", s.floatButton)
            NUMERIC.forEach { k -> s.set(k, sp.getFloat(k, s.get(k).toFloat()).toDouble()) }
            // O singură dată: adăugăm Bolt Courier și la lista salvată de versiunea veche
            if (!sp.getBoolean("boltAdded", false)) {
                if (BOLT_PACKAGE !in s.packageSet()) {
                    val cur = s.packages.trim().trimEnd(',')
                    s.packages = if (cur.isEmpty()) BOLT_PACKAGE else "$cur, $BOLT_PACKAGE"
                }
                sp.edit().putString("packages", s.packages).putBoolean("boltAdded", true).apply()
            }
            return s
        }

        fun save(ctx: Context, s: Settings) {
            val e = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
            e.putString("vehicle", s.vehicle).putString("packages", s.packages)
                .putBoolean("autoRead", s.autoRead).putBoolean("floatButton", s.floatButton)
            NUMERIC.forEach { k -> e.putFloat(k, s.get(k).toFloat()) }
            e.apply()
        }
    }

    fun get(k: String): Double = when (k) {
        "consumption" -> consumption; "price" -> price; "maint" -> maint; "speed" -> speed
        "waitMin" -> waitMin; "returnPct" -> returnPct; "commissionPct" -> commissionPct
        "minPerKm" -> minPerKm; "minPerHour" -> minPerHour; "autoHideSec" -> autoHideSec
        else -> 0.0
    }

    fun set(k: String, v: Double) {
        when (k) {
            "consumption" -> consumption = v; "price" -> price = v; "maint" -> maint = v; "speed" -> speed = v
            "waitMin" -> waitMin = v; "returnPct" -> returnPct = v; "commissionPct" -> commissionPct = v
            "minPerKm" -> minPerKm = v; "minPerHour" -> minPerHour = v; "autoHideSec" -> autoHideSec = v
        }
    }
}

/** Mici date de lucru: ultimul text citit (pentru depanare) si aplicatiile vazute recent. */
object Debug {
    private const val FILE = "merita_debug"

    fun saveLastText(ctx: Context, pkg: String, text: String) {
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
            .putString("lastPkg", pkg).putString("lastText", text.take(6000))
            .putLong("lastTime", System.currentTimeMillis()).apply()
    }

    fun lastText(ctx: Context): Triple<String, String, Long> {
        val sp = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        return Triple(sp.getString("lastPkg", "") ?: "", sp.getString("lastText", "") ?: "", sp.getLong("lastTime", 0))
    }

    fun addRecentPackage(ctx: Context, pkg: String) {
        val sp = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        val list = (sp.getString("recent", "") ?: "").split(',').filter { it.isNotBlank() }.toMutableList()
        if (list.firstOrNull() == pkg) return
        list.remove(pkg)
        list.add(0, pkg)
        sp.edit().putString("recent", list.take(12).joinToString(",")).apply()
    }

    fun recentPackages(ctx: Context): List<String> =
        (ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString("recent", "") ?: "")
            .split(',').filter { it.isNotBlank() }

    fun overlayY(ctx: Context): Int =
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).getInt("overlayY", 120)

    fun saveOverlayY(ctx: Context, y: Int) {
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putInt("overlayY", y).apply()
    }

    /** Poziția butonului plutitor; -1 = încă nemutat (folosim locul implicit). */
    fun floatPos(ctx: Context): Pair<Int, Int> {
        val sp = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        return sp.getInt("floatX", -1) to sp.getInt("floatY", -1)
    }

    fun saveFloatPos(ctx: Context, x: Int, y: Int) {
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putInt("floatX", x).putInt("floatY", y).apply()
    }
}
