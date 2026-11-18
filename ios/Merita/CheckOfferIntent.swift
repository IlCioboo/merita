import AppIntents
import Foundation

/// Acțiunea din aplicația Scurtături:
///   Fă o captură de ecran → Extrage textul din imagine → Verifică oferta (Merită?)
/// Rulează în fundal (nu deschide aplicația) și arată verdictul ca notificare peste Bolt/Glovo/Wolt.
struct CheckOfferIntent: AppIntent {
    static var title: LocalizedStringResource = "Verifică oferta"
    static var description = IntentDescription("Spune dacă merită oferta, din textul citit de pe ecran. Pune înainte „Extrage textul din imagine”.")
    static var openAppWhenRun: Bool = false

    @Parameter(title: "Text")
    var text: String

    static var parameterSummary: some ParameterSummary {
        Summary("Verifică oferta din \(\.$text)")
    }

    func perform() async throws -> some IntentResult & ReturnsValue<String> {
        let now = Date()
        let r = Engine.check(text, now: now)
        var tripId: Int64? = nil

        if r.ok, let trip = r.trip {
            if let old = Store.recentTrip(key: r.key, now: now) {
                tripId = old // aceeași ofertă, citită din nou: n-o mai salvăm o dată
            } else if let id = (trip["id"] as? NSNumber)?.int64Value {
                Store.addTrip(trip)
                Store.rememberTrip(key: r.key, id: id, now: now)
                tripId = id
            }
        }

        Notifier.show(title: r.title, body: r.body, tripId: tripId)
        return .result(value: r.body.isEmpty ? r.title : r.title + "\n" + r.body)
    }
}
