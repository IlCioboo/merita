package ro.merita.app

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.abs

/**
 * Citeste textul de pe ecran cand e deschisa o aplicatie de curier si,
 * daca gaseste o comanda (bani + km), arata o bula colorata cu verdictul.
 */
class OrderReaderService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private var pendingPkg = ""
    private var overlay: View? = null
    private var lastKey = ""
    private var lastKeyTime = 0L

    private val readRunnable = Runnable { readScreen(pendingPkg) }
    private val hideRunnable = Runnable { hideOverlay() }

    override fun onServiceConnected() {
        instance = this
        refreshFloatButton()
    }

    override fun onDestroy() {
        instance = null
        hideOverlay()
        removeFloatButton()
        super.onDestroy()
    }

    override fun onInterrupt() {}

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val pkg = event?.packageName?.toString() ?: return
        if (pkg == packageName) return
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            Debug.addRecentPackage(this, pkg)
        }
        val settings = Settings.load(this)
        if (pkg !in settings.packageSet()) return
        if (event.eventType == AccessibilityEvent.TYPE_VIEW_CLICKED) {
            onCourierClick(event)
            return
        }
        if (!settings.autoRead) return
        // Asteptam putin sa se termine de desenat ecranul, apoi citim o singura data
        pendingPkg = pkg
        handler.removeCallbacks(readRunnable)
        handler.postDelayed(readRunnable, 350)
    }

    /**
     * Citește ecranul. Automat: doar aplicațiile de curier urmărite.
     * Manual (butonul plutitor): orice aplicație e deschisă în față.
     */
    private fun readScreen(pkg: String, manual: Boolean = false) {
        val settings = Settings.load(this)
        val watched = settings.packageSet()
        val sb = StringBuilder()
        var readPkg = pkg

        // Toate ferestrele aplicației (oferta poate fi într-un pop-up separat)
        try {
            for (w in windows) {
                if (w.type == AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY) continue
                if (manual && w.type != AccessibilityWindowInfo.TYPE_APPLICATION) continue
                val root = w.root ?: continue
                val p = root.packageName?.toString() ?: continue
                if (p == packageName) continue
                if (manual || p in watched) {
                    if (readPkg.isEmpty()) readPkg = p
                    collectText(root, sb, 0)
                }
            }
        } catch (_: Exception) {
        }
        if (sb.isEmpty()) rootInActiveWindow?.let { root ->
            val p = root.packageName?.toString() ?: ""
            if (p != packageName && (manual || p in watched)) {
                if (readPkg.isEmpty()) readPkg = p
                collectText(root, sb, 0)
            }
        }
        val text = sb.toString()
        if (text.isBlank()) {
            if (manual) showNotice("❓ Nu pot citi ecranul ăsta")
            return
        }

        Debug.saveLastText(this, readPkg, text)
        val order = Core.parseOrder(text, readPkg)
        val now = System.currentTimeMillis()
        if (!order.ready) {
            // Oferta a dispărut și aplicația arată navigarea spre restaurant → ai acceptat-o
            if (!manual && order.earnings == null && pendingIsFresh(now) && PICKUP_WORDS.containsMatchIn(text)) {
                resolvePending(TripStore.ACCEPTED)
            }
            if (manual) {
                val miss = listOfNotNull(
                    if (order.earnings == null) "câștigul" else null,
                    if (order.km == null && (order.minutes ?: 0.0) <= 0) "km sau minutele" else null,
                ).joinToString(" și ")
                showNotice("❓ Nu găsesc $miss pe ecran")
            }
            return
        }

        val key = "${order.earnings}|${order.km}|${order.minutes}"
        val r = Core.calculate(order, settings)
        // Aceeași comandă văzută recent: nu o salvăm de două ori
        if (key == lastKey && now - lastKeyTime < 10 * 60_000) {
            if (manual) showResult(order, r, settings, lastTripId)
            return
        }
        lastKey = key
        lastKeyTime = now

        val trip = Trip.from(order, r, now)
        TripStore.add(this, trip)
        lastTripId = trip.id
        pendingId = trip.id
        pendingTime = now
        showResult(order, r, settings, trip.id)
    }

    private var lastTripId = 0L

    // ---- Butonul plutitor de scanare ----
    private var floatBtn: View? = null

    /** Apelat la pornire și când comuți butonul din aplicație. */
    fun refreshFloatButton() {
        handler.post {
            if (Settings.load(this).floatButton) showFloatButton() else removeFloatButton()
        }
    }

    private fun removeFloatButton() {
        floatBtn?.let {
            try {
                (getSystemService(WINDOW_SERVICE) as WindowManager).removeView(it)
            } catch (_: Exception) {
            }
        }
        floatBtn = null
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun showFloatButton() {
        if (floatBtn != null) return
        val wm = getSystemService(WINDOW_SERVICE) as WindowManager
        val size = dp(58f).toInt()
        val btn = TextView(this).apply {
            text = "RON\n?"
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(0xCC1F9D55.toInt())
                setStroke(dp(2f).toInt(), 0x88FFFFFF.toInt())
            }
        }
        val params = WindowManager.LayoutParams(
            size, size,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        )
        params.gravity = Gravity.TOP or Gravity.START
        val dm = resources.displayMetrics
        val (sx, sy) = Debug.floatPos(this)
        params.x = if (sx >= 0) sx else dm.widthPixels - size - dp(8f).toInt()
        params.y = if (sy >= 0) sy else (dm.heightPixels * 0.45).toInt()

        var startX = 0
        var startY = 0
        var touchX = 0f
        var touchY = 0f
        var moved = false
        btn.setOnTouchListener { v, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = params.x; startY = params.y; touchX = e.rawX; touchY = e.rawY; moved = false
                    v.alpha = 0.6f
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (e.rawX - touchX).toInt()
                    val dy = (e.rawY - touchY).toInt()
                    if (abs(dx) > 12 || abs(dy) > 12) moved = true
                    if (moved) {
                        params.x = (startX + dx).coerceIn(0, dm.widthPixels - size)
                        params.y = (startY + dy).coerceIn(0, dm.heightPixels - size)
                        try { wm.updateViewLayout(v, params) } catch (_: Exception) {}
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.alpha = 1f
                    if (moved) {
                        Debug.saveFloatPos(this, params.x, params.y)
                    } else if (e.action == MotionEvent.ACTION_UP) {
                        // Scanare la cerere: ascundem bula veche ca să nu o citim și pe ea
                        hideOverlay()
                        handler.removeCallbacks(readRunnable)
                        handler.postDelayed({ readScreen("", manual = true) }, 150)
                    }
                    true
                }
                else -> false
            }
        }
        try {
            wm.addView(btn, params)
            floatBtn = btn
        } catch (_: Exception) {
        }
    }

    /** Mesaj scurt (fără verdict) care dispare singur. */
    private fun showNotice(msg: String) {
        handler.post {
            hideOverlay()
            val wm = getSystemService(WINDOW_SERVICE) as WindowManager
            val view = TextView(this).apply {
                text = msg
                setTextColor(Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                val p = dp(12f).toInt()
                setPadding(p, p / 2, p, p / 2)
                background = GradientDrawable().apply {
                    setColor(0xEE333333.toInt())
                    cornerRadius = dp(14f)
                }
                setOnClickListener { hideOverlay() }
            }
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT,
            )
            params.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            params.y = Debug.overlayY(this)
            try {
                wm.addView(view, params)
                overlay = view
            } catch (_: Exception) {
            }
            handler.removeCallbacks(hideRunnable)
            handler.postDelayed(hideRunnable, 3000)
        }
    }

    // ---- Cursa acceptată / refuzată ----
    private var pendingId = 0L
    private var pendingTime = 0L

    private fun pendingIsFresh(now: Long) = pendingId != 0L && now - pendingTime < 3 * 60_000

    private fun resolvePending(status: String) {
        if (pendingId == 0L) return
        TripStore.setStatus(this, pendingId, status)
        pendingId = 0L
        hideOverlay()
    }

    /** Ai apăsat un buton în aplicația de curier: dacă e „Acceptă”/„Refuză”, notăm. */
    private fun onCourierClick(event: AccessibilityEvent) {
        if (!pendingIsFresh(System.currentTimeMillis())) return
        val label = buildString {
            event.text?.forEach { append(it).append(' ') }
            event.contentDescription?.let { append(it).append(' ') }
            try {
                event.source?.let { s ->
                    s.text?.let { append(it).append(' ') }
                    s.contentDescription?.let { append(it) }
                }
            } catch (_: Exception) {
            }
        }
        when {
            REJECT_WORDS.containsMatchIn(label) -> resolvePending(TripStore.REJECTED)
            ACCEPT_WORDS.containsMatchIn(label) -> resolvePending(TripStore.ACCEPTED)
            // Bolt: butonul verde de acceptare e chiar „24 min, 22,73 RON”
            "bolt" in (event.packageName?.toString() ?: "") && Core.hasMoney(label) ->
                resolvePending(TripStore.ACCEPTED)
        }
    }

    private fun collectText(node: AccessibilityNodeInfo?, sb: StringBuilder, depth: Int) {
        if (node == null || depth > 50) return
        val t = node.text?.toString()?.trim()
        if (!t.isNullOrEmpty()) sb.append(t).append('\n')
        val d = node.contentDescription?.toString()?.trim()
        if (!d.isNullOrEmpty() && d != t) sb.append(d).append('\n')
        for (i in 0 until node.childCount) collectText(node.getChild(i), sb, depth + 1)
    }

    /** Folosit si de butonul "Arată bula de test" din aplicatie. */
    fun showResult(order: Order, r: Result, s: Settings, tripId: Long = 0L) {
        handler.post {
            hideOverlay()
            val wm = getSystemService(WINDOW_SERVICE) as WindowManager
            val view = buildBubble(order, r, tripId)
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT,
            )
            params.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            params.y = Debug.overlayY(this)
            makeDraggable(view, params, wm)
            try {
                wm.addView(view, params)
                overlay = view
            } catch (_: Exception) {
            }
            handler.removeCallbacks(hideRunnable)
            if (s.autoHideSec > 0) handler.postDelayed(hideRunnable, (s.autoHideSec * 1000).toLong())
        }
    }

    private fun hideOverlay() {
        overlay?.let {
            try {
                (getSystemService(WINDOW_SERVICE) as WindowManager).removeView(it)
            } catch (_: Exception) {
            }
        }
        overlay = null
    }

    private fun dp(v: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, resources.displayMetrics)

    private fun buildBubble(order: Order, r: Result, tripId: Long): View {
        val (bg, fg, title) = when (r.verdict) {
            Verdict.GOOD -> Triple(0xEE1F9D55.toInt(), Color.WHITE, "MERITĂ ✅")
            Verdict.MEH -> Triple(0xEED69E2E.toInt(), Color.BLACK, "LA LIMITĂ ⚠️")
            Verdict.BAD -> Triple(0xEEC53030.toInt(), Color.WHITE, "NU MERITĂ ❌")
        }
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = dp(12f).toInt()
            setPadding(pad, pad / 2, pad, pad / 2)
            background = GradientDrawable().apply {
                setColor(bg)
                cornerRadius = dp(14f)
            }
        }
        fun line(text: String, size: Float, bold: Boolean = false) = TextView(this).apply {
            this.text = text
            setTextColor(fg)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, size)
            if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
            gravity = Gravity.CENTER_HORIZONTAL
        }
        box.addView(line(title + (if (order.platform.isNotEmpty()) "  · ${order.platform}" else ""), 20f, true))
        box.addView(line("net ${fmt(r.net)} RON  ·  ${fmt(r.perKm)} RON/km  ·  ${r.perHour.toInt()} RON/h", 15f, true))
        box.addView(
            line(
                "${fmt(order.earnings ?: 0.0)} RON · " +
                    (if (r.estimatedKm) "~${fmt(r.kmTotal)} km (est.)" else "${fmt(r.kmTotal)} km") +
                    " · ~${r.minutes} min" +
                    (if (r.estimatedTime) " (est.)" else "") + " · cost ${fmt(r.cost)}",
                12f,
            ),
        )
        val diff = (order.earnings ?: 0.0) - r.minEarnings
        box.addView(
            line(
                "minim ca să merite: ${fmt(r.minEarnings)} RON  (" +
                    (if (diff >= 0) "+${fmt(diff)} peste" else "${fmt(-diff)} lipsă") + ")",
                14f, true,
            ),
        )
        if (tripId != 0L) {
            // Dacă aplicația de curier nu ne-a anunțat acceptarea, o poți marca tu dintr-o atingere
            val btn = line("✓ Am luat-o", 14f, true).apply {
                val p = dp(6f).toInt()
                setPadding(p * 2, p, p * 2, p)
                background = GradientDrawable().apply {
                    setColor(0x33000000)
                    cornerRadius = dp(10f)
                }
                setOnClickListener {
                    TripStore.setStatus(this@OrderReaderService, tripId, TripStore.ACCEPTED)
                    if (pendingId == tripId) pendingId = 0L
                    text = "✓ Salvată în calendar"
                    handler.removeCallbacks(hideRunnable)
                    handler.postDelayed(hideRunnable, 1200)
                }
            }
            box.addView(btn, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                topMargin = dp(6f).toInt()
            })
        }
        return box
    }

    /** Tragi bula sus/jos ca s-o muti; atingere scurta = inchide. */
    @SuppressLint("ClickableViewAccessibility")
    private fun makeDraggable(view: View, params: WindowManager.LayoutParams, wm: WindowManager) {
        var startY = 0
        var touchY = 0f
        var moved = false
        view.setOnTouchListener { v, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    startY = params.y; touchY = e.rawY; moved = false; true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dy = (e.rawY - touchY).toInt()
                    if (abs(dy) > 10) moved = true
                    if (moved) {
                        params.y = (startY + dy).coerceAtLeast(0)
                        try { wm.updateViewLayout(v, params) } catch (_: Exception) {}
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (moved) Debug.saveOverlayY(this, params.y) else hideOverlay()
                    true
                }
                else -> false
            }
        }
    }

    companion object {
        @Volatile
        var instance: OrderReaderService? = null

        fun fmt(v: Double) = String.format(java.util.Locale.US, "%.2f", v)

        private val ACCEPT_WORDS = Regex("""(accept|confirm|preia|preiau|iau comanda|take order|i'll take)""", RegexOption.IGNORE_CASE)
        private val REJECT_WORDS = Regex("""(refuz|respinge|decline|reject|skip|ignor|nu acum|not now|pass\b)""", RegexOption.IGNORE_CASE)
        private val PICKUP_WORDS = Regex(
            """(navigheaz|navigate|am ajuns|i've arrived|arrived at|confirm pickup|confirmă ridicarea|mergi la restaurant|go to restaurant|head to)""",
            RegexOption.IGNORE_CASE,
        )
    }
}
