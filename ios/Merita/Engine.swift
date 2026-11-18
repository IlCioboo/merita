import Foundation
import JavaScriptCore

struct CheckResult {
    let ok: Bool
    let title: String
    let body: String
    let key: String
    let trip: [String: Any]?
}

/// Rulează core.js + check.js (aceeași logică ca pagina și Android) ca să dea verdictul.
enum Engine {
    static func check(_ text: String, now: Date = Date()) -> CheckResult {
        guard let ctx = JSContext() else { return fail("Nu pot porni calculul") }
        guard let core = script("core", subdirectory: "web"), let check = script("check", subdirectory: nil) else {
            return fail("Lipsesc fișierele de calcul")
        }
        ctx.evaluateScript(core)
        ctx.evaluateScript(check)

        let ms = (now.timeIntervalSince1970 * 1000).rounded()
        guard let fn = ctx.objectForKeyedSubscript("meritaCheck"),
              let out = fn.call(withArguments: [text, Store.settingsJSON, ms]),
              let json = out.toString(),
              let data = json.data(using: .utf8),
              let o = try? JSONSerialization.jsonObject(with: data) as? [String: Any]
        else {
            return fail("Eroare la calcul")
        }
        return CheckResult(
            ok: o["ok"] as? Bool ?? false,
            title: o["title"] as? String ?? "",
            body: o["body"] as? String ?? "",
            key: o["key"] as? String ?? "",
            trip: o["trip"] as? [String: Any]
        )
    }

    private static func script(_ name: String, subdirectory: String?) -> String? {
        guard let url = Bundle.main.url(forResource: name, withExtension: "js", subdirectory: subdirectory) else { return nil }
        return try? String(contentsOf: url, encoding: .utf8)
    }

    private static func fail(_ message: String) -> CheckResult {
        CheckResult(ok: false, title: "❓ \(message)", body: "", key: "", trip: nil)
    }
}
