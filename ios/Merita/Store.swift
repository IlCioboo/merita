import Foundation

/// Ce trebuie să ajungă în calendar (care stă în pagină) și setările trimise de pagină.
/// Acțiunea din Scurtături și butoanele de pe notificare scriu aici; pagina le preia când o deschizi.
enum Store {
    private static let defaults = UserDefaults.standard
    private static let lock = NSLock()

    // MARK: Setări (vehicul, praguri), copiate din pagină

    static var settingsJSON: String { defaults.string(forKey: "settings") ?? "{}" }

    static func setSettings(_ json: String) {
        defaults.set(json, forKey: "settings")
    }

    // MARK: Evenimente pentru calendar

    static func pendingEvents() -> [[String: Any]] {
        lock.lock(); defer { lock.unlock() }
        return read()
    }

    static func addTrip(_ trip: [String: Any]) {
        append(["type": "add", "trip": trip])
    }

    static func addStatus(id: Int64, status: String) {
        append(["type": "status", "id": NSNumber(value: id), "status": status])
    }

    /// Scoate primele `count` evenimente, după ce pagina le-a preluat.
    static func drop(_ count: Int) {
        lock.lock(); defer { lock.unlock() }
        var all = read()
        all.removeFirst(min(count, all.count))
        write(all)
    }

    private static func append(_ event: [String: Any]) {
        lock.lock(); defer { lock.unlock() }
        var all = read()
        all.append(event)
        write(Array(all.suffix(500)))
    }

    private static func read() -> [[String: Any]] {
        guard let data = defaults.data(forKey: "outbox"),
              let arr = try? JSONSerialization.jsonObject(with: data) as? [[String: Any]]
        else { return [] }
        return arr
    }

    private static func write(_ arr: [[String: Any]]) {
        if let data = try? JSONSerialization.data(withJSONObject: arr) {
            defaults.set(data, forKey: "outbox")
        }
    }

    // MARK: Aceeași ofertă citită de două ori nu se salvează de două ori

    static func recentTrip(key: String, now: Date) -> Int64? {
        guard defaults.string(forKey: "lastKey") == key else { return nil }
        let t = defaults.double(forKey: "lastKeyTime")
        guard now.timeIntervalSince1970 - t < 10 * 60 else { return nil }
        let id = defaults.object(forKey: "lastTripId") as? NSNumber
        return id?.int64Value
    }

    static func rememberTrip(key: String, id: Int64, now: Date) {
        defaults.set(key, forKey: "lastKey")
        defaults.set(now.timeIntervalSince1970, forKey: "lastKeyTime")
        defaults.set(NSNumber(value: id), forKey: "lastTripId")
    }
}
