import SwiftUI

enum TroopTheme {
    static let forest = Color(hex: "#1F3A24")
    static let khaki = Color(hex: "#D9C79A")
    static let red = Color(hex: "#B5332A")
    static let paper = Color(hex: "#F4EEDF")
    static let card = Color(hex: "#FFFDF7")
    static let ink = Color(hex: "#23251F")
    static let muted = Color(hex: "#6B6A5E")
    static let line = Color(hex: "#E3DAC4")
}

extension Color {
    init(hex: String) {
        let value = UInt64(hex.trimmingCharacters(in: CharacterSet(charactersIn: "#")), radix: 16) ?? 0
        self.init(.sRGB, red: Double((value >> 16) & 255) / 255, green: Double((value >> 8) & 255) / 255, blue: Double(value & 255) / 255, opacity: 1)
    }
}

struct CardStyle: ViewModifier {
    func body(content: Content) -> some View {
        content.padding(16).background(TroopTheme.card, in: RoundedRectangle(cornerRadius: 16))
            .overlay(RoundedRectangle(cornerRadius: 16).stroke(TroopTheme.line, lineWidth: 1))
    }
}
extension View { func troopCard() -> some View { modifier(CardStyle()) } }

struct TroopEmblem: View {
    var body: some View {
        Canvas { context, size in
            let scale = min(size.width / 100, size.height / 115)
            context.translateBy(x: (size.width - 100 * scale) / 2, y: (size.height - 115 * scale) / 2)
            context.scaleBy(x: scale, y: scale)
            func shield(_ inset: CGFloat) -> Path {
                Path { path in
                    path.move(to: CGPoint(x: 6 + inset, y: 4 + inset)); path.addLine(to: CGPoint(x: 94 - inset, y: 4 + inset))
                    path.addLine(to: CGPoint(x: 94 - inset, y: 62))
                    path.addCurve(to: CGPoint(x: 50, y: 111 - inset * 1.4), control1: CGPoint(x: 94 - inset, y: 88 - inset / 2), control2: CGPoint(x: 72 - inset / 2, y: 101 - inset))
                    path.addCurve(to: CGPoint(x: 6 + inset, y: 62), control1: CGPoint(x: 28 + inset / 2, y: 101 - inset), control2: CGPoint(x: 6 + inset, y: 88 - inset / 2))
                    path.closeSubpath()
                }
            }
            context.fill(shield(0), with: .color(TroopTheme.khaki))
            context.fill(shield(5), with: .color(TroopTheme.forest))
            var clipped = context; clipped.clip(to: shield(5))
            clipped.fill(Path(CGRect(x: 0, y: 0, width: 100, height: 31)), with: .color(TroopTheme.red))
            context.draw(Text("TROOP").font(.system(size: 15, weight: .bold)).foregroundColor(TroopTheme.paper), at: CGPoint(x: 50, y: 21))
            context.draw(Text("53").font(.system(size: 44, weight: .bold)).foregroundColor(TroopTheme.khaki), at: CGPoint(x: 50, y: 58))
            let mountains = Path { path in
                path.move(to: CGPoint(x: 0, y: 100)); path.addLines([CGPoint(x: 30, y: 80), CGPoint(x: 44, y: 90), CGPoint(x: 64, y: 74), CGPoint(x: 100, y: 98), CGPoint(x: 100, y: 120), CGPoint(x: 0, y: 120)]); path.closeSubpath()
            }
            clipped.fill(mountains, with: .color(Color(hex: "#2E5232")))
        }
        .accessibilityLabel("Troop 53 emblem")
    }
}
