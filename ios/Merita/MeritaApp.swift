import SwiftUI
import UserNotifications

/// Aplicația: un ecran care arată pagina „Merită cursa?” (aceeași ca varianta web),
/// plus acțiunea „Verifică oferta” pentru Scurtături și notificarea cu verdictul.
@main
struct MeritaApp: App {
    @UIApplicationDelegateAdaptor(AppDelegate.self) var appDelegate

    var body: some Scene {
        WindowGroup {
            WebScreen()
                .ignoresSafeArea()
                .background(Color.black)
                .preferredColorScheme(.dark)
        }
    }
}

final class AppDelegate: NSObject, UIApplicationDelegate, UNUserNotificationCenterDelegate {
    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil
    ) -> Bool {
        Notifier.registerCategories()
        UNUserNotificationCenter.current().delegate = self
        return true
    }

    /// Arătăm notificarea chiar dacă aplicația e deschisă.
    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        willPresent notification: UNNotification,
        withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void
    ) {
        completionHandler([.banner, .list, .sound])
    }

    /// Ai apăsat „Am luat-o” / „Am refuzat-o” pe notificare.
    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        didReceive response: UNNotificationResponse,
        withCompletionHandler completionHandler: @escaping () -> Void
    ) {
        Notifier.handle(response)
        completionHandler()
    }
}
