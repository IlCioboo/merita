import Foundation
import UserNotifications

/// Notificarea cu verdictul, cu butoanele „Am luat-o” și „Am refuzat-o”.
enum Notifier {
    static let category = "OFFER"

    static func registerCategories() {
        let take = UNNotificationAction(identifier: "TAKE", title: "✓ Am luat-o", options: [])
        let skip = UNNotificationAction(identifier: "SKIP", title: "✖ Am refuzat-o", options: [])
        let cat = UNNotificationCategory(identifier: category, actions: [take, skip], intentIdentifiers: [], options: [])
        UNUserNotificationCenter.current().setNotificationCategories([cat])
    }

    static func requestPermission() {
        UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .sound]) { _, _ in }
    }

    static func isAllowed() async -> Bool {
        let s = await UNUserNotificationCenter.current().notificationSettings()
        return s.authorizationStatus == .authorized || s.authorizationStatus == .provisional
    }

    /// O singură notificare de ofertă: cea nouă o înlocuiește pe cea veche.
    static func show(title: String, body: String, tripId: Int64?) {
        registerCategories()
        let c = UNMutableNotificationContent()
        c.title = title
        c.body = body
        c.sound = .default
        if let id = tripId {
            c.categoryIdentifier = category
            c.userInfo = ["tripId": NSNumber(value: id)]
        }
        let req = UNNotificationRequest(identifier: "offer", content: c, trigger: nil)
        UNUserNotificationCenter.current().add(req)
    }

    static func handle(_ response: UNNotificationResponse) {
        guard let id = (response.notification.request.content.userInfo["tripId"] as? NSNumber)?.int64Value else { return }
        switch response.actionIdentifier {
        case "TAKE":
            Store.addStatus(id: id, status: "accepted")
            confirm("✓ Salvată în calendar")
        case "SKIP":
            Store.addStatus(id: id, status: "rejected")
            UNUserNotificationCenter.current().removeDeliveredNotifications(withIdentifiers: ["offer"])
        default:
            break
        }
    }

    private static func confirm(_ text: String) {
        let c = UNMutableNotificationContent()
        c.title = text
        let req = UNNotificationRequest(identifier: "offer", content: c, trigger: nil)
        UNUserNotificationCenter.current().add(req)
        DispatchQueue.main.asyncAfter(deadline: .now() + 3) {
            UNUserNotificationCenter.current().removeDeliveredNotifications(withIdentifiers: ["offer"])
        }
    }
}
