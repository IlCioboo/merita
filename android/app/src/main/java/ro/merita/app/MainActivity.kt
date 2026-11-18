package ro.merita.app

import android.app.Activity
import android.app.AlertDialog
import android.content.ComponentName
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.provider.Settings as SysSettings
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import java.text.DateFormat
import java.util.Date

/** Ecranul aplicatiei: activare serviciu, setari vehicul, test, depanare. */
class MainActivity : Activity() {

    private lateinit var settings: Settings
    private lateinit var root: LinearLayout
    private lateinit var status: TextView
    private lateinit var recentBox: LinearLayout
    private lateinit var lastTextView: TextView
    private lateinit var packagesEdit: EditText
    private val numberEdits = mutableMapOf<String, EditText>()
    private var filling = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settings = Settings.load(this)

        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val p = dp(16)
            setPadding(p, p, p, p * 3)
        }
        setContentView(ScrollView(this).apply { addView(root) })

        title("Merită cursa?", 26f)
        status = text("", 15f)
        shiftBtn = Button(this).apply {
            isAllCaps = false
            textSize = 16f
            setOnClickListener { toggleShift() }
        }
        root.addView(shiftBtn)
        button("📅 Calendar: încasări, cheltuieli, profit, statistici") {
            startActivity(Intent(this, CalendarActivity::class.java))
        }
        root.addView(Switch(this).apply {
            text = "Buton plutitor: apeși și scanează ecranul"
            textSize = 15f
            isChecked = settings.floatButton
            setPadding(0, dp(10), 0, dp(4))
            setOnCheckedChangeListener { _, on ->
                settings.floatButton = on
                Settings.save(this@MainActivity, settings)
                val svc = OrderReaderService.instance
                if (svc != null) svc.refreshFloatButton()
                else if (on) Toast.makeText(this@MainActivity, "Activează întâi citirea (pasul 1)", Toast.LENGTH_LONG).show()
            }
        })
        root.addView(Switch(this).apply {
            text = "Citire automată (bula apare singură la oferte)"
            textSize = 15f
            isChecked = settings.autoRead
            setPadding(0, dp(4), 0, dp(4))
            setOnCheckedChangeListener { _, on ->
                settings.autoRead = on
                Settings.save(this@MainActivity, settings)
            }
        })
        button("1. Activează citirea automată") {
            startActivity(Intent(SysSettings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        text(
            "În listă alege „Merită? – citire comenzi” și pornește-l. Dacă butonul e gri " +
                "(„Setare restricționată”): Setări → Aplicații → Merită? → meniul ⋮ → " +
                "„Permite setările restricționate”, apoi încearcă din nou.",
            13f, muted = true,
        )

        // ---- Vehicul ----
        title("Vehicul și praguri", 18f)
        val spinner = Spinner(this)
        spinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, VEHICLES.map { it.label })
        val savedIndex = VEHICLES.indexOfFirst { it.key == settings.vehicle }.coerceAtLeast(0)
        spinner.setSelection(savedIndex, false)
        // Lista poate anunța singură „benzină” la deschidere; reacționăm doar la alegerea ta
        var userPicked = false
        spinner.setOnTouchListener { _, _ -> userPicked = true; false }
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                val veh = VEHICLES[pos]
                if (!userPicked) {
                    val want = VEHICLES.indexOfFirst { it.key == settings.vehicle }.coerceAtLeast(0)
                    if (pos != want) spinner.setSelection(want, false)
                    return
                }
                if (veh.key == settings.vehicle) return
                settings.vehicle = veh.key
                settings.consumption = veh.consumption
                settings.price = veh.price
                settings.maint = veh.maint
                settings.speed = veh.speed
                Settings.save(this@MainActivity, settings)
                fillNumbers()
            }

            override fun onNothingSelected(p: AdapterView<*>?) {}
        }
        root.addView(spinner)

        numberField("consumption", "Consum la 100 km (litri sau kWh)")
        numberField("price", "Preț carburant / kWh (RON)")
        numberField("maint", "Uzură: anvelope, revizii (RON/km)")
        numberField("speed", "Viteză medie, dacă nu apare timpul (km/h)")
        numberField("waitMin", "Așteptare la restaurant (min)")
        numberField("returnPct", "Km retur în plus (%)")
        numberField("commissionPct", "Comision flotă / taxe (%)")
        numberField("minPerKm", "Minim RON net / km")
        numberField("minPerHour", "Minim RON net / oră")
        numberField("autoHideSec", "Bula dispare după (secunde, 0 = niciodată)")
        fillNumbers()

        // ---- Test ----
        title("Test", 18f)
        val testInput = EditText(this).apply {
            hint = "Lipește aici textul unei comenzi"
            minLines = 3
            setText("Glovo\n16,80 RON\nRidicare 2,1 km\nLivrare 3,5 km")
        }
        root.addView(testInput)
        val testResult = text("", 14f)
        button("Calculează") {
            val o = Core.parseOrder(testInput.text.toString())
            testResult.text = describe(o)
        }
        button("Arată bula de test peste ecran") {
            val o = Core.parseOrder(testInput.text.toString())
            val svc = OrderReaderService.instance
            when {
                svc == null -> testResult.text = "Mai întâi activează citirea automată (pasul 1)."
                !o.ready -> testResult.text = describe(o)
                else -> svc.showResult(o, Core.calculate(o, settings), settings)
            }
        }

        // ---- Aplicatii urmarite ----
        title("Aplicații urmărite", 18f)
        packagesEdit = EditText(this).apply {
            setText(settings.packages)
            minLines = 2
            textSize = 13f
        }
        packagesEdit.addTextChangedListener(watcher {
            settings.packages = packagesEdit.text.toString()
            Settings.save(this, settings)
        })
        root.addView(packagesEdit)
        text("Deschise recent (atinge ca să adaugi):", 13f, muted = true)
        recentBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(recentBox)

        // ---- Backup ----
        title("Backup și export", 18f)
        text(
            "Cursele stau doar pe telefon. Salvează din când în când un backup " +
                "(pe Drive, în Descărcări) ca să nu pierzi istoricul dacă schimbi telefonul.",
            13f, muted = true,
        )
        button("💾 Salvează backup") {
            createFile(REQ_BACKUP, "application/json", Backup.fileName("merita-backup", "json"))
        }
        button("♻️ Restaurează din backup") {
            startActivityForResult(
                Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*"),
                REQ_RESTORE,
            )
        }
        button("📤 Export Excel (toate cursele)") {
            createFile(REQ_CSV, "text/csv", Backup.fileName("merita-curse", "csv"))
        }

        // ---- Depanare ----
        title("Ultimul text citit", 18f)
        text("Dacă o comandă e citită greșit, trimite-mi textul ăsta ca să ajustez citirea.", 13f, muted = true)
        lastTextView = text("", 12f)
        button("Trimite textul citit") {
            val (pkg, t, _) = Debug.lastText(this)
            startActivity(
                Intent.createChooser(
                    Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, "[$pkg]\n$t"),
                    "Trimite textul",
                ),
            )
        }
    }

    // ---- Tura ----
    private lateinit var shiftBtn: Button

    private fun toggleShift() {
        if (Shifts.current(this) == null) Shifts.start(this) else Shifts.stop(this)
        updateShiftButton()
    }

    private fun updateShiftButton() {
        val cur = Shifts.current(this)
        if (cur == null) {
            shiftBtn.text = "▶ Încep tura"
            shiftBtn.setTextColor(0xFF2ECC71.toInt())
        } else {
            val min = ((System.currentTimeMillis() - cur) / 60_000).toInt()
            shiftBtn.text = "⏹ Termin tura (pornită la ${DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(cur))}, " +
                "${min / 60} h ${min % 60} min)"
            shiftBtn.setTextColor(0xFFE74C3C.toInt())
        }
    }

    // ---- Backup / export ----
    private fun createFile(req: Int, mime: String, name: String) {
        startActivityForResult(
            Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
                .setType(mime).putExtra(Intent.EXTRA_TITLE, name),
            req,
        )
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val uri = data?.data ?: return
        if (resultCode != RESULT_OK) return
        try {
            when (requestCode) {
                REQ_BACKUP -> {
                    contentResolver.openOutputStream(uri)!!.use { it.write(Backup.toJson(this).toByteArray()) }
                    toast("Backup salvat (${TripStore.all(this).size} curse)")
                }
                REQ_CSV -> {
                    contentResolver.openOutputStream(uri)!!.use { it.write(Backup.csv(TripStore.all(this)).toByteArray()) }
                    toast("Tabel salvat. Deschide-l cu Excel sau Google Sheets.")
                }
                REQ_RESTORE -> {
                    val text = contentResolver.openInputStream(uri)!!.use { it.readBytes().toString(Charsets.UTF_8) }
                    AlertDialog.Builder(this)
                        .setTitle("Restaurezi backup-ul?")
                        .setMessage(
                            "Cursele și turele din backup se adaugă la cele de pe telefon (nu se șterge nimic). " +
                                "Setările (vehicul, praguri) se iau din backup.",
                        )
                        .setPositiveButton("Restaurează") { _, _ ->
                            try {
                                val r = Backup.restore(this, text)
                                toast("Restaurat: ${r.trips} curse (${r.newTrips} noi), ${r.shifts} ture")
                                recreate()
                            } catch (e: Exception) {
                                toast("Nu pot citi backup-ul: ${e.message}")
                            }
                        }
                        .setNegativeButton("Renunță", null)
                        .show()
                }
            }
        } catch (e: Exception) {
            toast("Eroare: ${e.message}")
        }
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_LONG).show()

    override fun onResume() {
        super.onResume()
        updateShiftButton()
        val on = isServiceEnabled()
        status.text = if (on) "● Citirea automată e PORNITĂ" else "● Citirea automată e OPRITĂ"
        status.setTextColor(if (on) 0xFF2ECC71.toInt() else 0xFFE74C3C.toInt())

        recentBox.removeAllViews()
        Debug.recentPackages(this).filter { it !in settings.packageSet() }.take(8).forEach { pkg ->
            recentBox.addView(Button(this).apply {
                text = "+ $pkg"
                isAllCaps = false
                setOnClickListener {
                    val cur = packagesEdit.text.toString().trim().trimEnd(',')
                    packagesEdit.setText(if (cur.isEmpty()) pkg else "$cur, $pkg")
                    recentBox.removeView(this)
                }
            })
        }

        val (pkg, t, time) = Debug.lastText(this)
        lastTextView.text = if (t.isEmpty()) "Nimic citit încă." else
            "[$pkg, ${DateFormat.getTimeInstance().format(Date(time))}]\n" + t.take(1500)
    }

    private fun isServiceEnabled(): Boolean {
        val enabled = SysSettings.Secure.getString(contentResolver, SysSettings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
        val me = ComponentName(this, OrderReaderService::class.java)
        return enabled.split(':').any { ComponentName.unflattenFromString(it) == me }
    }

    private fun describe(o: Order): String {
        if (!o.ready) {
            return "Nu am găsit: " + listOfNotNull(
                if (o.earnings == null) "câștigul (lei/RON)" else null,
                if (o.km == null && (o.minutes ?: 0.0) <= 0) "distanța (km) sau minutele" else null,
            ).joinToString(", ")
        }
        val r = Core.calculate(o, settings)
        val v = when (r.verdict) { Verdict.GOOD -> "MERITĂ ✅"; Verdict.MEH -> "LA LIMITĂ ⚠️"; Verdict.BAD -> "NU MERITĂ ❌" }
        return "$v ${o.platform}\nCâștig ${o.earnings} RON, ${r.kmTotal} km" +
            (if (r.estimatedKm) " (estimat din minute)" else "") + ", ~${r.minutes} min" +
            (if (r.estimatedTime) " (estimat)" else "") +
            "\nCost drum ${r.cost} RON (${r.costPerKm} RON/km)\nProfit net ${r.net} RON · ${r.perKm} RON/km · ${r.perHour} RON/oră" +
            "\nMinim ca să merite: ${r.minEarnings} RON"
    }

    // ---- mici ajutoare pentru interfata ----

    private fun fillNumbers() {
        filling = true
        numberEdits.forEach { (k, e) -> e.setText(trimNum(settings.get(k))) }
        filling = false
    }

    private fun trimNum(v: Double) = if (v == v.toLong().toDouble()) v.toLong().toString() else v.toString()

    private fun numberField(key: String, label: String) {
        text(label, 13f, muted = true)
        val e = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            setSingleLine()
        }
        e.addTextChangedListener(watcher {
            if (filling) return@watcher
            val v = e.text.toString().replace(',', '.').toDoubleOrNull() ?: return@watcher
            settings.set(key, v)
            Settings.save(this, settings)
        })
        numberEdits[key] = e
        root.addView(e)
    }

    private fun watcher(onChange: () -> Unit) = object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        override fun afterTextChanged(s: Editable?) = onChange()
    }

    private fun dp(v: Int) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics).toInt()

    private fun title(t: String, size: Float) = text(t, size).apply {
        setTypeface(typeface, android.graphics.Typeface.BOLD)
        setPadding(0, dp(18), 0, dp(4))
    }

    private fun text(t: String, size: Float, muted: Boolean = false): TextView {
        val v = TextView(this).apply {
            text = t
            textSize = size
            if (muted) setTextColor(Color.GRAY)
        }
        root.addView(v, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        return v
    }

    private fun button(t: String, onClick: () -> Unit) {
        root.addView(Button(this).apply {
            text = t
            isAllCaps = false
            setOnClickListener { onClick() }
        })
    }

    companion object {
        private const val REQ_BACKUP = 41
        private const val REQ_RESTORE = 42
        private const val REQ_CSV = 43
    }
}
