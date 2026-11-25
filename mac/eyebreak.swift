// 20-20-20 menu bar app: every 20 min, a 1-minute full-screen break to look ~20 ft (6 m) away.
// Install (build + start at login): ./install.sh    Quick test: ./eyebreak [workSeconds]
import Cocoa

let work = Int(CommandLine.arguments.dropFirst().first ?? "") ?? 20 * 60
let rest = 60
// ../messages.txt next to this folder; re-read every break so edits apply without a rebuild
let messagesFile = Bundle.main.executableURL!.deletingLastPathComponent()
    .deletingLastPathComponent().appendingPathComponent("messages.txt")

// Non-activating panel: takes clicks + keys without stealing app focus (macOS 26 blocks activate())
class Overlay: NSPanel {
    override var canBecomeKey: Bool { true }
    // swallow typing mid-break; only Esc (key code 53) reaches the Skip button
    override func keyDown(with event: NSEvent) { if event.keyCode == 53 { super.keyDown(with: event) } }
}

class App: NSObject, NSApplicationDelegate {
    let item = NSStatusBar.system.statusItem(withLength: NSStatusItem.variableLength)
    var left = work, paused = false, lastMessage = ""
    var windows: [NSWindow] = [], counts: [NSTextField] = []

    func applicationDidFinishLaunching(_ n: Notification) {
        item.button?.image = NSImage(systemSymbolName: "eye", accessibilityDescription: "Eye break")
        item.button?.imagePosition = .imageLeading
        let menu = NSMenu()
        menu.addItem(withTitle: "Break now", action: #selector(startBreak), keyEquivalent: "b").target = self
        menu.addItem(withTitle: "Pause", action: #selector(togglePause), keyEquivalent: "p").target = self
        menu.addItem(.separator())
        menu.addItem(withTitle: "Quit", action: #selector(NSApplication.terminate), keyEquivalent: "q")
        item.menu = menu
        // .common so the countdown keeps running while the menu is open
        RunLoop.main.add(Timer(timeInterval: 1, target: self, selector: #selector(tick), userInfo: nil, repeats: true), forMode: .common)
        render()
    }

    @objc func tick() {
        if !windows.isEmpty {
            left -= 1
            counts.forEach { $0.stringValue = "\(left)" }
            if left <= 0 { endBreak() }
        } else if !paused {
            left -= 1
            if left <= 0 { startBreak() }
        }
        render()
    }

    func render() {
        item.button?.title = paused ? " paused" : !windows.isEmpty ? " rest" : " \((left + 59) / 60)m"
    }

    @objc func togglePause(_ sender: NSMenuItem) {
        paused.toggle()
        sender.title = paused ? "Resume" : "Pause"
        render()
    }

    @objc func startBreak() {
        guard windows.isEmpty else { return }
        left = rest
        let lines = ((try? String(contentsOf: messagesFile, encoding: .utf8)) ?? "")
            .split(separator: "\n").map { $0.trimmingCharacters(in: .whitespaces) }
            .filter { !$0.isEmpty && !$0.hasPrefix("#") && $0 != lastMessage }
        lastMessage = lines.randomElement() ?? "👀 Blink slowly a few times."
        for screen in NSScreen.screens {
            let w = Overlay(contentRect: screen.frame, styleMask: [.borderless, .nonactivatingPanel], backing: .buffered, defer: false)
            w.level = .screenSaver
            w.isOpaque = false
            w.backgroundColor = NSColor.black.withAlphaComponent(0.88)
            w.collectionBehavior = [.canJoinAllSpaces, .fullScreenAuxiliary]
            w.isReleasedWhenClosed = false
            w.hidesOnDeactivate = false

            let count = label("\(rest)", NSFont.monospacedDigitSystemFont(ofSize: 140, weight: .bold))
            let skip = NSButton(title: "Skip (Esc)", target: self, action: #selector(endBreak))
            skip.keyEquivalent = "\u{1b}"
            skip.refusesFirstResponder = true  // so a stray Space while typing doesn't skip
            let stack = NSStackView(views: [
                label("Look at something 20 feet away", .systemFont(ofSize: 44, weight: .semibold)),
                count,
                label(lastMessage, .systemFont(ofSize: 26)),
                skip,
            ])
            stack.orientation = .vertical
            stack.spacing = 24
            stack.translatesAutoresizingMaskIntoConstraints = false
            w.contentView!.addSubview(stack)
            NSLayoutConstraint.activate([
                stack.centerXAnchor.constraint(equalTo: w.contentView!.centerXAnchor),
                stack.centerYAnchor.constraint(equalTo: w.contentView!.centerYAnchor),
            ])
            w.orderFrontRegardless()
            windows.append(w)
            counts.append(count)
        }
        windows.first?.makeKey()
        render()
    }

    @objc func endBreak() {
        if left <= 0 { NSSound(named: "Glass")?.play() }  // audible cue: your eyes are off-screen
        windows.forEach { $0.orderOut(nil) }
        windows = []
        counts = []
        left = work
        render()
    }

    func label(_ s: String, _ font: NSFont) -> NSTextField {
        let l = NSTextField(wrappingLabelWithString: s)
        l.alignment = .center
        l.preferredMaxLayoutWidth = 900
        l.font = font
        l.textColor = .white
        return l
    }
}

let app = NSApplication.shared
let delegate = App()
app.delegate = delegate
app.setActivationPolicy(.accessory)
app.run()
