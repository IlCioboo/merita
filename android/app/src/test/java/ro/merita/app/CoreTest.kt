package ro.merita.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CoreTest {
    private val s = Settings()

    @Test fun glovoSumsPickupAndDelivery() {
        val o = Core.parseOrder("Glovo\nComandă nouă\n14,50 RON\nRidicare\n1,8 km\nLivrare\n2,6 km\nAcceptă")
        assertEquals("Glovo", o.platform)
        assertEquals(14.5, o.earnings!!, 0.001)
        assertEquals(4.4, o.km!!, 0.001)
        assertNull(o.minutes)
        assertEquals(Verdict.GOOD, Core.calculate(o, s).verdict)
    }

    @Test fun woltLeiAndMinutes() {
        val o = Core.parseOrder("Wolt\nEstimated earnings\n17.20 lei\nPickup 1.2 km away\nDropoff 3.4 km\n~22 min")
        assertEquals(17.2, o.earnings!!, 0.001)
        assertEquals(4.6, o.km!!, 0.001)
        assertEquals(22.0, o.minutes!!, 0.001)
    }

    @Test fun uberUsesTotalLine() {
        val o = Core.parseOrder("RON 18,45\nIncludes expected tip\n24 min (5.2 km) total\nKFC", "com.ubercab.driver")
        assertEquals("Uber Eats", o.platform)
        assertEquals(18.45, o.earnings!!, 0.001)
        assertEquals(5.2, o.km!!, 0.001)
        assertEquals(24.0, o.minutes!!, 0.001)
    }

    @Test fun metersAndHours() {
        val a = Core.parseOrder("9,00 lei\n800 m\n1,1 km\n12 min")
        assertEquals(1.9, a.km!!, 0.001)
        val b = Core.parseOrder("42,00 lei\n1 h 5 min\n14 km")
        assertEquals(65.0, b.minutes!!, 0.001)
    }

    @Test fun ignoresDailyEarnings() {
        val o = Core.parseOrder("Azi: 245,30 lei\nComandă nouă\n15,00 lei\n3 km")
        assertEquals(15.0, o.earnings!!, 0.001)
    }

    @Test fun amountWithoutCurrency() {
        // Exact ce a citit iPhone-ul din Notițe (fără "RON")
        val o = Core.parseOrder("00:51\n<\n:!!\n...\n954\nGlovo\n14,50\nRidicare 1,6 Km\nLivrare 2,6 Km\nAa\nShow Notification\n123\nD")
        assertEquals(14.5, o.earnings!!, 0.001)
        assertEquals(4.2, o.km!!, 0.001)
    }

    @Test fun ignoresOwnNotification() {
        val o = Core.parseOrder("MERITĂ ✅ · Glovo\nnet 11.53 RON · 2.62 RON/km · 45 RON/h\nGlovo\n16,00 lei\n3 km")
        assertEquals(16.0, o.earnings!!, 0.001)
        assertEquals(3.0, o.km!!, 0.001)
    }

    @Test fun boltFoodWithoutKm() {
        // Textul de pe ecranul Bolt Courier (captura din 10.11): fără km, doar minute
        val text = "Decline\nShow map\nShaorma New York\nStr. Oltului 19, Galati 800444\n~11 min\n~13 min\n24 min, 22,73 RON"
        val o = Core.parseOrder(text, "com.bolt.deliverycourier")
        assertEquals("Bolt Food", o.platform)
        assertEquals(22.73, o.earnings!!, 0.001)
        assertNull(o.km)
        assertEquals(24.0, o.minutes!!, 0.001) // nu 11 + 13 + 24 = 48
        assertTrue(o.ready)
        val r = Core.calculate(o, s)
        assertTrue(r.estimatedKm)
        assertEquals(10.0, r.kmTotal, 0.01) // 24 min la 25 km/h
        assertEquals(Verdict.GOOD, r.verdict)
    }

    @Test fun boltTimesInSeparateLines() {
        // Dacă suma și minutele totale vin pe rânduri diferite: 11 + 13 = 24 → totalul e 24
        val o = Core.parseOrder("~11 min\n~13 min\n24 min\n22,73 RON", "com.bolt.deliverycourier")
        assertEquals(24.0, o.minutes!!, 0.001)
    }

    @Test fun pickupDropoffAndTotalKm() {
        val o = Core.parseOrder("Glovo\n15,00 lei\n1,2 km\n3,4 km\n4,6 km")
        assertEquals(4.6, o.km!!, 0.001)
    }

    @Test fun minimumPrice() {
        // Bolt: ~10 km, 24 + 5 min. Prag 1,5 RON/km → 15 net; 30 RON/h → 14,5 net. + cost 6,75 = 21,75
        val r = Core.calculate(Order("Bolt Food", 22.73, null, 24.0), s)
        assertEquals(21.75, r.minEarnings, 0.01)
        // La minim exact, verdictul trebuie să fie MERITĂ
        assertEquals(Verdict.GOOD, Core.calculate(Order("", r.minEarnings + 0.01, null, 24.0), s).verdict)
        assertTrue(Core.calculate(Order("", r.minEarnings - 0.5, null, 24.0), s).verdict != Verdict.GOOD)
    }

    @Test fun minimumPriceWithCommission() {
        val c = Settings(commissionPct = 20.0)
        val r = Core.calculate(Order("", 20.0, 5.0, 20.0), c)
        // net cerut max(1,5*5=7,5 ; 30*25/60=12,5)=12,5 + cost 3,375 = 15,875 / 0,8 = 19,84
        assertEquals(19.84, r.minEarnings, 0.01)
    }

    @Test fun shiftMinutesInsideDay() {
        val h = 3_600_000L
        val day = 100 * 24 * h
        val shifts = listOf(Shift(day - 2 * h, day + 3 * h), Shift(day + 10 * h, day + 12 * h))
        // 3 h din prima (după miezul nopții) + 2 h din a doua
        assertEquals(300, Shifts.minutesIn(shifts, day, day + 24 * h))
        assertEquals(120, Shifts.minutesIn(shifts, day - 24 * h, day))
    }

    @Test fun statsReport() {
        val a = Trip(1, 1, "Glovo", 20.0, 5.0, 30, 0.0, 3.0, 17.0, "GOOD", TripStore.ACCEPTED)
        val b = Trip(2, 2, "Bolt Food", 22.0, 10.0, 29, 0.0, 6.0, 16.0, "GOOD", TripStore.ACCEPTED)
        val c = Trip(3, 3, "Bolt Food", 9.0, 8.0, 30, 0.0, 5.0, 4.0, "BAD", TripStore.OFFER)
        val txt = Stats.report("Test", listOf(a, b, c), 120)
        assertTrue(txt.contains("Curse luate: 2 din 3"))
        assertTrue(txt.contains("Profit net: 33.00 RON"))
        assertTrue(txt.contains("Pe tură, cu așteptarea: 17 RON/oră")) // 33 net / 2 h
        assertTrue(txt.contains("luate 1/2")) // Bolt: 1 luată din 2 văzute
    }

    @Test fun csvForExcel() {
        val a = Trip(1, 1, "Glovo", 20.5, 5.0, 30, 0.0, 3.0, 17.5, "GOOD", TripStore.ACCEPTED)
        val lines = Backup.csv(listOf(a)).trim().split("\r\n")
        assertEquals(2, lines.size)
        assertTrue(lines[0].startsWith("﻿Data;Ora;Aplicație"))
        assertTrue(lines[1].contains(";Glovo;luată;20,50;5,00;30;0,00;3,00;17,50;merită"))
    }

    @Test fun dayTotalsCountOnlyAcceptedTrips() {
        val a = Trip(1, 1, "Glovo", 20.0, 5.0, 25, 2.0, 3.0, 15.0, "GOOD", TripStore.ACCEPTED)
        val b = Trip(2, 2, "Wolt", 30.0, 10.0, 35, 0.0, 6.0, 24.0, "GOOD", TripStore.ACCEPTED)
        val c = Trip(3, 3, "Uber Eats", 99.0, 9.0, 99, 0.0, 9.0, 90.0, "GOOD", TripStore.OFFER)
        val t = TripStore.totals(listOf(a, b, c))
        assertEquals(2, t.count)
        assertEquals(3, t.offers)
        assertEquals(50.0, t.earnings, 0.001)
        assertEquals(11.0, t.expenses, 0.001)
        assertEquals(39.0, t.net, 0.001)
        assertEquals(60, t.minutes)
        assertEquals(39.0, t.perHour, 0.001)
    }

    @Test fun calculation() {
        val r = Core.calculate(Order("", 20.0, 5.0, 20.0), s)
        // cost/km = 7/100*7.5 + 0.15 = 0.675 -> 3.375 RON; net 16.625; 25 min
        assertEquals(0.675, r.costPerKm, 0.006)
        assertEquals(16.63, r.net, 0.01)
        assertEquals(25, r.minutes)
    }
}
