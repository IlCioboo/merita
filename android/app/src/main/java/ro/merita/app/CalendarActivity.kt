package ro.merita.app

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.util.Calendar
import java.util.Locale
import kotlin.math.abs

/** Calendar lunar: profitul pe fiecare zi; atingi o zi ca să vezi încasări, cheltuieli, timp și cursele. */
class CalendarActivity : Activity() {

    private lateinit var root: LinearLayout
    private lateinit var monthTitle: TextView
    private lateinit var monthSummary: TextView
    private lateinit var grid: LinearLayout
    private lateinit var dayBox: LinearLayout

    private val cal = Calendar.getInstance()
    private var year = cal.get(Calendar.YEAR)
    private var month = cal.get(Calendar.MONTH)
    private var selDay = cal.get(Calendar.DAY_OF_MONTH)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(16), dp(12), dp(40))
        }
        setContentView(ScrollView(this).apply { addView(root) })

        val header = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        header.addView(Button(this).apply { text = "◀"; setOnClickListener { shiftMonth(-1) } })
        monthTitle = TextView(this).apply {
            textSize = 20f
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
        }
        header.addView(monthTitle, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        header.addView(Button(this).apply { text = "▶"; setOnClickListener { shiftMonth(1) } })
        root.addView(header)

        monthSummary = TextView(this).apply { textSize = 14f; setPadding(0, dp(6), 0, dp(10)) }
        root.addView(monthSummary)

        val tools = LinearLayout(this)
        tools.addView(Button(this).apply {
            text = "📊 Statistici"
            isAllCaps = false
            setOnClickListener { showStats() }
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        tools.addView(Button(this).apply {
            text = "📤 Excel luna"
            isAllCaps = false
            setOnClickListener {
                startActivityForResult(
                    Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("text/csv")
                        .putExtra(Intent.EXTRA_TITLE, String.format(Locale.US, "merita-curse-%04d-%02d.csv", year, month + 1)),
                    REQ_CSV,
                )
            }
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        root.addView(tools)

        grid = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(grid)

        dayBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, dp(14), 0, 0) }
        root.addView(dayBox)
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    private fun shiftMonth(delta: Int) {
        val c = Calendar.getInstance()
        c.set(year, month, 1)
        c.add(Calendar.MONTH, delta)
        year = c.get(Calendar.YEAR)
        month = c.get(Calendar.MONTH)
        selDay = 1
        val today = Calendar.getInstance()
        if (today.get(Calendar.YEAR) == year && today.get(Calendar.MONTH) == month) selDay = today.get(Calendar.DAY_OF_MONTH)
        render()
    }

    private fun render() {
        monthTitle.text = "${MONTHS[month]} $year"
        val monthTrips = TripStore.forMonth(this, year, month)
        val mt = TripStore.totals(monthTrips)
        val (mFrom, mTo) = Shifts.monthRange(year, month)
        val shiftMin = Shifts.minutesIn(Shifts.withRunning(this), mFrom, mTo)
        monthSummary.text = "Luna: încasat ${f(mt.earnings)} · cheltuieli ${f(mt.expenses)} · profit ${f(mt.net)} RON\n" +
            "${mt.count} curse · ${hm(mt.minutes)} · ${f(mt.km)} km" +
            (if (shiftMin > 0) "\nTură ${hm(shiftMin)} · real ${f(mt.net / (shiftMin / 60.0))} RON/oră" else "")
        renderGrid(monthTrips)
        renderDay()
    }

    private fun renderGrid(monthTrips: List<Trip>) {
        grid.removeAllViews()
        val head = LinearLayout(this)
        listOf("L", "Ma", "Mi", "J", "V", "S", "D").forEach {
            head.addView(TextView(this).apply {
                text = it; gravity = Gravity.CENTER; setTextColor(Color.GRAY); textSize = 12f
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }
        grid.addView(head)

        // Profit net pe fiecare zi
        val c = Calendar.getInstance()
        val netByDay = HashMap<Int, Double>()
        monthTrips.filter { it.accepted }.forEach {
            c.timeInMillis = it.time
            val d = c.get(Calendar.DAY_OF_MONTH)
            netByDay[d] = (netByDay[d] ?: 0.0) + it.net
        }

        c.set(year, month, 1)
        val offset = (c.get(Calendar.DAY_OF_WEEK) + 5) % 7 // luni = 0
        val days = c.getActualMaximum(Calendar.DAY_OF_MONTH)
        val today = Calendar.getInstance()
        val isThisMonth = today.get(Calendar.YEAR) == year && today.get(Calendar.MONTH) == month

        var cell = 0
        var row: LinearLayout? = null
        val total = ((offset + days + 6) / 7) * 7
        while (cell < total) {
            if (cell % 7 == 0) {
                row = LinearLayout(this)
                grid.addView(row)
            }
            val day = cell - offset + 1
            val v = TextView(this).apply {
                gravity = Gravity.CENTER
                textSize = 12f
                setPadding(0, dp(6), 0, dp(6))
            }
            if (day in 1..days) {
                val net = netByDay[day]
                v.text = if (net != null) "$day\n${net.toInt()}" else "$day\n "
                val bg = GradientDrawable().apply { cornerRadius = dp(8).toFloat() }
                when {
                    net == null -> bg.setColor(0x00000000)
                    net >= 0 -> bg.setColor(0x551F9D55)
                    else -> bg.setColor(0x55C53030)
                }
                if (day == selDay) bg.setStroke(dp(2), 0xFF3182CE.toInt())
                else if (isThisMonth && day == today.get(Calendar.DAY_OF_MONTH)) bg.setStroke(dp(1), Color.GRAY)
                v.background = bg
                v.setOnClickListener { selDay = day; render() }
            }
            row!!.addView(v, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                setMargins(dp(2), dp(2), dp(2), dp(2))
            })
            cell++
        }
    }

    private fun renderDay() {
        dayBox.removeAllViews()
        val c = Calendar.getInstance()
        c.set(year, month, selDay)
        val trips = TripStore.forDay(this, year, month, selDay)
        val t = TripStore.totals(trips)

        dayBox.addView(TextView(this).apply {
            text = "${WEEKDAYS[(c.get(Calendar.DAY_OF_WEEK) + 5) % 7]} $selDay ${MONTHS[month].lowercase()}"
            textSize = 18f
            setTypeface(typeface, Typeface.BOLD)
        })
        dayBox.addView(TextView(this).apply {
            textSize = 15f
            setPadding(0, dp(6), 0, dp(6))
            text = "Încasat:  ${f(t.earnings)} RON\n" +
                "Cheltuieli:  ${f(t.expenses)} RON\n" +
                "     carburant + uzură ${f(t.cost)} · comision ${f(t.commission)}\n" +
                "Profit:  ${f(t.net)} RON\n" +
                "Timp:  ${hm(t.minutes)} (estimat) · ${f(t.km)} km · ${f(t.perHour)} RON/oră\n" +
                "Curse:  ${t.count} acceptate din ${t.offers} oferte văzute"
        })

        // Tura: câștigul real pe oră, cu tot cu așteptarea dintre comenzi
        val (dFrom, dTo) = Shifts.dayRange(year, month, selDay)
        val shiftMin = Shifts.minutesIn(Shifts.withRunning(this), dFrom, dTo)
        if (shiftMin > 0) {
            dayBox.addView(TextView(this).apply {
                textSize = 15f
                setTypeface(typeface, Typeface.BOLD)
                setPadding(0, 0, 0, dp(6))
                text = "Tură:  ${hm(shiftMin)} · real ${f(t.net / (shiftMin / 60.0))} RON/oră (cu așteptarea)"
                setOnClickListener {
                    AlertDialog.Builder(this@CalendarActivity)
                        .setTitle("Ștergi turele din ziua asta?")
                        .setMessage("Dacă ai uitat tura pornită sau ai pornit-o din greșeală. Cursele rămân.")
                        .setPositiveButton("Șterge turele") { _, _ -> Shifts.deleteStartingIn(this@CalendarActivity, dFrom, dTo); render() }
                        .setNegativeButton("Renunță", null)
                        .show()
                }
            })
        }

        dayBox.addView(Button(this).apply {
            text = "+ Adaugă cursă manual"
            isAllCaps = false
            setOnClickListener { addManual() }
        })

        if (trips.isEmpty()) {
            dayBox.addView(TextView(this).apply { text = "Nicio ofertă în ziua asta."; setTextColor(Color.GRAY) })
        }
        trips.forEach { trip ->
            c.timeInMillis = trip.time
            val icon = when (trip.status) {
                TripStore.ACCEPTED -> "✅"
                TripStore.REJECTED -> "✖"
                else -> "◻"
            }
            val row = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
            row.addView(TextView(this).apply {
                text = String.format(
                    Locale.US, "%s %02d:%02d %s\n     %s RON · %s km · ~%d min · net %s",
                    icon, c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), trip.platform,
                    f(trip.earnings), f(trip.km), trip.minutes, f(trip.net),
                )
                textSize = 14f
                setPadding(0, dp(8), 0, dp(8))
                if (!trip.accepted) setTextColor(Color.GRAY)
                setOnClickListener { editTrip(trip) }
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            row.addView(Button(this).apply {
                text = "🗑"
                setOnClickListener { confirmDelete(trip) }
            }, LinearLayout.LayoutParams(dp(52), ViewGroup.LayoutParams.WRAP_CONTENT))
            dayBox.addView(row)
        }
        if (trips.isNotEmpty()) {
            dayBox.addView(Button(this).apply {
                text = "🗑 Șterge toate cursele din ziua asta"
                isAllCaps = false
                setTextColor(0xFFC53030.toInt())
                setOnClickListener { confirmDeleteDay(trips) }
            })
        }
        dayBox.addView(TextView(this).apply {
            text = "✅ = luată (intră în totaluri) · ◻ = doar văzută · ✖ = refuzată. Atinge o cursă ca s-o schimbi, 🗑 ca s-o ștergi."
            textSize = 12f
            setTextColor(Color.GRAY)
            setPadding(0, dp(10), 0, 0)
        })
    }

    private fun editTrip(trip: Trip) {
        val opts = arrayOf("✅ Am luat-o", "◻ N-am luat-o", "🗑 Șterge")
        AlertDialog.Builder(this)
            .setTitle("${trip.platform} ${f(trip.earnings)} RON · ${f(trip.km)} km")
            .setItems(opts) { _, which ->
                when (which) {
                    0 -> TripStore.setStatus(this, trip.id, TripStore.ACCEPTED)
                    1 -> TripStore.setStatus(this, trip.id, TripStore.REJECTED)
                    2 -> { confirmDelete(trip); return@setItems }
                }
                render()
            }
            .setNegativeButton("Închide", null)
            .show()
    }

    private fun showStats() {
        val (mFrom, mTo) = Shifts.monthRange(year, month)
        val shifts = Shifts.withRunning(this)
        val monthText = Stats.report("${MONTHS[month]} $year", TripStore.forMonth(this, year, month), Shifts.minutesIn(shifts, mFrom, mTo))
        val allText = Stats.report("Tot istoricul", TripStore.all(this), Shifts.minutesIn(shifts, 0, Long.MAX_VALUE))
        val view = TextView(this).apply {
            typeface = Typeface.MONOSPACE
            textSize = 11f
            setTextIsSelectable(true)
            setPadding(dp(16), dp(12), dp(16), dp(12))
            text = monthText
        }
        val scroll = ScrollView(this).apply {
            addView(android.widget.HorizontalScrollView(this@CalendarActivity).apply { addView(view) })
        }
        var showingAll = false
        val dlg = AlertDialog.Builder(this)
            .setView(scroll)
            .setPositiveButton("Închide", null)
            .setNeutralButton("Tot istoricul", null)
            .show()
        dlg.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener { b ->
            showingAll = !showingAll
            view.text = if (showingAll) allText else monthText
            (b as Button).text = if (showingAll) "Doar luna" else "Tot istoricul"
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val uri = data?.data ?: return
        if (requestCode != REQ_CSV || resultCode != RESULT_OK) return
        try {
            contentResolver.openOutputStream(uri)!!.use {
                it.write(Backup.csv(TripStore.forMonth(this, year, month)).toByteArray())
            }
            Toast.makeText(this, "Tabel salvat. Deschide-l cu Excel sau Google Sheets.", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Eroare: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun confirmDelete(trip: Trip) {
        AlertDialog.Builder(this)
            .setTitle("Ștergi cursa?")
            .setMessage("${trip.platform} ${f(trip.earnings)} RON · ${f(trip.km)} km\nNu se mai poate recupera.")
            .setPositiveButton("Șterge") { _, _ -> TripStore.delete(this, trip.id); render() }
            .setNegativeButton("Renunță", null)
            .show()
    }

    private fun confirmDeleteDay(trips: List<Trip>) {
        AlertDialog.Builder(this)
            .setTitle("Ștergi toate cursele din $selDay ${MONTHS[month].lowercase()}?")
            .setMessage("${trips.size} curse/oferte. Nu se mai pot recupera.")
            .setPositiveButton("Șterge tot") { _, _ ->
                TripStore.deleteMany(this, trips.map { it.id }.toSet())
                render()
            }
            .setNegativeButton("Renunță", null)
            .show()
    }

    private fun addManual() {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(8), dp(20), 0) }
        fun field(hint: String) = EditText(this).apply {
            this.hint = hint
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            box.addView(this)
        }
        val e = field("Câștig (RON)")
        val k = field("Distanță (km)")
        val m = field("Timp (min, opțional)")
        AlertDialog.Builder(this)
            .setTitle("Cursă nouă pe $selDay ${MONTHS[month].lowercase()}")
            .setView(box)
            .setPositiveButton("Salvează") { _, _ ->
                val earn = e.text.toString().replace(',', '.').toDoubleOrNull()
                val km = k.text.toString().replace(',', '.').toDoubleOrNull()
                if (earn == null || km == null) return@setPositiveButton
                val order = Order("Manual", earn, km, m.text.toString().toDoubleOrNull())
                val r = Core.calculate(order, Settings.load(this))
                // Ora: acum dacă e azi, altfel ora 12 în ziua aleasă
                val now = Calendar.getInstance()
                val c = Calendar.getInstance()
                c.set(year, month, selDay, 12, 0, 0)
                if (now.get(Calendar.YEAR) == year && now.get(Calendar.MONTH) == month && now.get(Calendar.DAY_OF_MONTH) == selDay) {
                    c.timeInMillis = now.timeInMillis
                }
                val trip = Trip.from(order, r, c.timeInMillis + (abs(System.nanoTime()) % 1000))
                trip.status = TripStore.ACCEPTED
                TripStore.add(this, trip)
                render()
            }
            .setNegativeButton("Renunță", null)
            .show()
    }

    private fun dp(v: Int) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics).toInt()
    private fun f(v: Double) = String.format(Locale.US, "%.2f", v)
    private fun hm(min: Int) = if (min >= 60) "${min / 60} h ${min % 60} min" else "$min min"

    companion object {
        private const val REQ_CSV = 51
        val MONTHS = listOf(
            "Ianuarie", "Februarie", "Martie", "Aprilie", "Mai", "Iunie",
            "Iulie", "August", "Septembrie", "Octombrie", "Noiembrie", "Decembrie",
        )
        val WEEKDAYS = listOf("Luni", "Marți", "Miercuri", "Joi", "Vineri", "Sâmbătă", "Duminică")
    }
}
