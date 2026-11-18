import PhotosUI
import SwiftUI
import UIKit
import Vision
import WebKit

/// Ecranul aplicației: pagina „Merită cursa?” din pachet (index.html + core.js + journal.js).
struct WebScreen: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> WebController { WebController() }
    func updateUIViewController(_ controller: WebController, context: Context) {}
}

/// Pagina vorbește cu aplicația prin window.webkit.messageHandlers.merita.postMessage({type, ...}):
///   settings  → păstrăm setările, ca acțiunea din Scurtături să calculeze la fel
///   saveFile  → foaia de partajare (Salvează în Fișiere) pentru backup / Excel
///   paste     → textul din clipboard
///   pickImage → alegi o captură din Poze și o citim cu Vision (în telefon, fără internet)
/// Aplicația îi trimite paginii: MeritaNative.receive([...]) cu cursele din notificări.
final class WebController: UIViewController, WKScriptMessageHandler, WKUIDelegate, WKNavigationDelegate,
    PHPickerViewControllerDelegate {

    private var web: WKWebView!
    private var loaded = false

    override func loadView() {
        let config = WKWebViewConfiguration()
        config.websiteDataStore = .default()
        config.userContentController.add(WeakHandler(self), name: "merita")
        web = WKWebView(frame: .zero, configuration: config)
        web.uiDelegate = self
        web.navigationDelegate = self
        web.isOpaque = false
        web.backgroundColor = .black
        web.scrollView.backgroundColor = .black
        web.scrollView.contentInsetAdjustmentBehavior = .never // pagina folosește env(safe-area-inset-*)
        view = web
    }

    override func viewDidLoad() {
        super.viewDidLoad()
        if let url = Bundle.main.url(forResource: "index", withExtension: "html", subdirectory: "web") {
            web.loadFileURL(url, allowingReadAccessTo: url.deletingLastPathComponent())
        }
        NotificationCenter.default.addObserver(
            self, selector: #selector(becameActive), name: UIApplication.didBecomeActiveNotification, object: nil)
        Notifier.requestPermission()
    }

    override var preferredStatusBarStyle: UIStatusBarStyle { .lightContent }

    @objc private func becameActive() { deliverOutbox() }

    func webView(_ webView: WKWebView, didFinish navigation: WKNavigation!) {
        loaded = true
        deliverOutbox()
    }

    /// Cursele salvate din notificări intră în calendarul din pagină.
    private func deliverOutbox() {
        guard loaded else { return }
        let events = Store.pendingEvents()
        guard !events.isEmpty,
              let data = try? JSONSerialization.data(withJSONObject: events),
              let json = String(data: data, encoding: .utf8)
        else { return }
        web.evaluateJavaScript("window.MeritaNative ? MeritaNative.receive(\(json)) : -1") { result, error in
            if error == nil, let n = (result as? NSNumber)?.intValue, n >= 0 {
                Store.drop(events.count)
            }
        }
    }

    // MARK: Mesaje din pagină

    func userContentController(_ userContentController: WKUserContentController, didReceive message: WKScriptMessage) {
        guard let body = message.body as? [String: Any], let type = body["type"] as? String else { return }
        switch type {
        case "settings":
            if let s = body["settings"] as? String { Store.setSettings(s) }
        case "saveFile":
            if let name = body["name"] as? String, let text = body["text"] as? String { share(name: name, text: text) }
        case "paste":
            callJS("MeritaNative.pasted", UIPasteboard.general.string ?? "")
        case "pickImage":
            pickImage()
        default:
            break
        }
    }

    /// Cheamă o funcție din pagină cu un text (codificat sigur ca JSON).
    private func callJS(_ function: String, _ argument: String) {
        guard let data = try? JSONSerialization.data(withJSONObject: [argument]),
              let arr = String(data: data, encoding: .utf8)
        else { return }
        let literal = String(arr.dropFirst().dropLast())
        web.evaluateJavaScript("\(function)(\(literal))", completionHandler: nil)
    }

    private func share(name: String, text: String) {
        let url = FileManager.default.temporaryDirectory.appendingPathComponent(name)
        do {
            try text.write(to: url, atomically: true, encoding: .utf8)
        } catch {
            return
        }
        let vc = UIActivityViewController(activityItems: [url], applicationActivities: nil)
        vc.popoverPresentationController?.sourceView = view
        topController().present(vc, animated: true)
    }

    // MARK: Captură din Poze → text (Vision)

    private func pickImage() {
        var config = PHPickerConfiguration()
        config.filter = .images
        config.selectionLimit = 1
        let picker = PHPickerViewController(configuration: config)
        picker.delegate = self
        topController().present(picker, animated: true)
    }

    func picker(_ picker: PHPickerViewController, didFinishPicking results: [PHPickerResult]) {
        picker.dismiss(animated: true)
        guard let provider = results.first?.itemProvider, provider.canLoadObject(ofClass: UIImage.self) else { return }
        callJS("MeritaNative.status", "Citesc imaginea…")
        provider.loadObject(ofClass: UIImage.self) { [weak self] object, _ in
            guard let image = object as? UIImage, let cg = image.cgImage else {
                DispatchQueue.main.async { self?.callJS("MeritaNative.status", "Nu pot deschide imaginea.") }
                return
            }
            let request = VNRecognizeTextRequest { request, _ in
                let lines = (request.results as? [VNRecognizedTextObservation] ?? [])
                    .sorted { $0.boundingBox.maxY > $1.boundingBox.maxY } // de sus în jos
                    .compactMap { $0.topCandidates(1).first?.string }
                DispatchQueue.main.async { self?.callJS("MeritaNative.ocr", lines.joined(separator: "\n")) }
            }
            request.recognitionLevel = .accurate
            request.usesLanguageCorrection = false
            DispatchQueue.global(qos: .userInitiated).async {
                try? VNImageRequestHandler(cgImage: cg, options: [:]).perform([request])
            }
        }
    }

    // MARK: alert() și confirm() din pagină

    func webView(_ webView: WKWebView, runJavaScriptAlertPanelWithMessage message: String,
                 initiatedByFrame frame: WKFrameInfo, completionHandler: @escaping () -> Void) {
        let a = UIAlertController(title: nil, message: message, preferredStyle: .alert)
        a.addAction(UIAlertAction(title: "OK", style: .default) { _ in completionHandler() })
        presentOr(a) { completionHandler() }
    }

    func webView(_ webView: WKWebView, runJavaScriptConfirmPanelWithMessage message: String,
                 initiatedByFrame frame: WKFrameInfo, completionHandler: @escaping (Bool) -> Void) {
        let a = UIAlertController(title: nil, message: message, preferredStyle: .alert)
        a.addAction(UIAlertAction(title: "Anulează", style: .cancel) { _ in completionHandler(false) })
        a.addAction(UIAlertAction(title: "Da", style: .default) { _ in completionHandler(true) })
        presentOr(a) { completionHandler(false) }
    }

    private func presentOr(_ alert: UIAlertController, fallback: () -> Void) {
        let top = topController()
        if top is UIAlertController || top.isBeingPresented || top.isBeingDismissed {
            fallback()
        } else {
            top.present(alert, animated: true)
        }
    }

    private func topController() -> UIViewController {
        var top: UIViewController = self
        while let next = top.presentedViewController { top = next }
        return top
    }
}

/// Ca WKWebView să nu țină ecranul în memorie pentru totdeauna.
private final class WeakHandler: NSObject, WKScriptMessageHandler {
    weak var target: WKScriptMessageHandler?
    init(_ target: WKScriptMessageHandler) { self.target = target }
    func userContentController(_ userContentController: WKUserContentController, didReceive message: WKScriptMessage) {
        target?.userContentController(userContentController, didReceive: message)
    }
}
