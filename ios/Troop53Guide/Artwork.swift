import SwiftUI
import PDFKit
import ImageIO

enum ArtworkResources {
    static func url(_ path: String, bundle: Bundle = .main) -> URL? {
        guard GuideParser.validImagePath(path) else { return nil }
        let original = path as NSString
        let ext = original.pathExtension == "xml" ? "pdf" : original.pathExtension == "webp" ? "jpg" : original.pathExtension
        let folder = original.deletingLastPathComponent
        return bundle.url(forResource: (original.lastPathComponent as NSString).deletingPathExtension,
                          withExtension: ext, subdirectory: "Illustrations/\(folder)")
    }

    static func thumbnail(_ url: URL, maxPixels: Int) -> UIImage? {
        if url.pathExtension != "pdf" {
            guard let source = CGImageSourceCreateWithURL(url as CFURL, nil),
                  let image = CGImageSourceCreateThumbnailAtIndex(source, 0, [
                    kCGImageSourceCreateThumbnailFromImageAlways: true,
                    kCGImageSourceCreateThumbnailWithTransform: true,
                    kCGImageSourceThumbnailMaxPixelSize: maxPixels,
                    kCGImageSourceShouldCacheImmediately: true
                  ] as CFDictionary) else { return nil }
            return UIImage(cgImage: image)
        }
        guard let document = CGPDFDocument(url as CFURL), let page = document.page(at: 1) else { return nil }
        let box = page.getBoxRect(.mediaBox)
        let ratio = CGFloat(maxPixels) / max(box.width, box.height)
        let width = max(1, Int(box.width * ratio)), height = max(1, Int(box.height * ratio))
        guard let context = CGContext(data: nil, width: width, height: height, bitsPerComponent: 8, bytesPerRow: width * 4,
                                      space: CGColorSpaceCreateDeviceRGB(), bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue) else { return nil }
        context.setFillColor(UIColor(red: 244/255, green: 238/255, blue: 223/255, alpha: 1).cgColor)
        context.fill(CGRect(x: 0, y: 0, width: width, height: height))
        context.concatenate(page.getDrawingTransform(.mediaBox, rect: CGRect(x: 0, y: 0, width: width, height: height), rotate: 0, preserveAspectRatio: true))
        context.drawPDFPage(page)
        return context.makeImage().map { UIImage(cgImage: $0) }
    }
}

private final class ThumbnailCache: @unchecked Sendable {
    static let shared = ThumbnailCache()
    let images = NSCache<NSURL, UIImage>()
    private init() { images.totalCostLimit = 12 * 1024 * 1024; images.countLimit = 24 }
    func load(_ url: URL) -> UIImage? {
        if let image = images.object(forKey: url as NSURL) { return image }
        guard let image = ArtworkResources.thumbnail(url, maxPixels: 1080) else { return nil }
        images.setObject(image, forKey: url as NSURL, cost: (image.cgImage?.bytesPerRow ?? 0) * (image.cgImage?.height ?? 0))
        return image
    }
}

struct ArtworkSelection: Identifiable {
    let path: String
    let caption: String
    var id: String { path }
}

struct ArtworkCard: View {
    let path: String
    let caption: String
    @State private var image: UIImage?
    @State private var unavailable = false
    @State private var selected: ArtworkSelection?
    var body: some View {
        Button { selected = ArtworkSelection(path: path, caption: caption) } label: {
            VStack(alignment: .leading, spacing: 10) {
                ZStack {
                    TroopTheme.paper
                    if let image { Image(uiImage: image).resizable().scaledToFit() }
                    else if unavailable { Label("Illustration unavailable", systemImage: "photo").foregroundStyle(TroopTheme.muted) }
                    else { ProgressView() }
                }
                .aspectRatio(1.6, contentMode: .fit).clipShape(RoundedRectangle(cornerRadius: 12))
                Text(caption).font(.caption).foregroundStyle(TroopTheme.muted).multilineTextAlignment(.leading)
                Label("Tap to enlarge", systemImage: "arrow.up.left.and.arrow.down.right").font(.caption2).foregroundStyle(TroopTheme.forest)
            }.troopCard()
        }
        .buttonStyle(.plain).accessibilityLabel(caption).accessibilityHint("Opens a full screen illustration with zoom controls")
        .accessibilityIdentifier("image-\(path)")
        .task(id: path) {
            image = nil; unavailable = false
            guard let url = ArtworkResources.url(path) else { unavailable = true; return }
            let thumbnail = await Task.detached(priority: .userInitiated) { ThumbnailCache.shared.load(url) }.value
            guard !Task.isCancelled else { return }
            image = thumbnail; unavailable = thumbnail == nil
        }
        .fullScreenCover(item: $selected) { ArtworkViewer(selection: $0) }
    }
}

struct ArtworkViewer: View {
    let selection: ArtworkSelection
    @Environment(\.dismiss) private var dismiss
    @State private var document: PDFDocument?
    @State private var unavailable = false
    @State private var zoomCommand = 0
    @State private var reset = false
    var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
                if let document {
                    ZoomableDocument(document: document, caption: selection.caption, command: zoomCommand, reset: reset)
                        .accessibilityIdentifier("zoomable-illustration")
                } else if unavailable { Text("This illustration could not be opened.").frame(maxHeight: .infinity) }
                else { ProgressView().frame(maxHeight: .infinity) }
                Text(selection.caption).font(.callout).padding().frame(maxWidth: .infinity).background(TroopTheme.card)
            }
            .background(TroopTheme.paper).navigationTitle("Illustration").navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarLeading) { Button("Close") { dismiss() }.accessibilityIdentifier("close-illustration") }
                ToolbarItemGroup(placement: .navigationBarTrailing) {
                    Button { reset = false; zoomCommand += 1 } label: { Image(systemName: "plus.magnifyingglass") }.accessibilityLabel("Zoom in")
                    Button("Reset") { reset = true; zoomCommand += 1 }
                }
            }
            .task {
                guard let url = ArtworkResources.url(selection.path) else { unavailable = true; return }
                if url.pathExtension == "pdf" { document = PDFDocument(url: url) }
                else {
                    let image = await Task.detached(priority: .userInitiated) { ArtworkResources.thumbnail(url, maxPixels: 2048) }.value
                    if let image, let page = PDFPage(image: image) { let pdf = PDFDocument(); pdf.insert(page, at: 0); document = pdf }
                }
                unavailable = document == nil
            }
        }
    }
}

private final class FittedPDFView: PDFView {
    private var lastSize = CGSize.zero
    override func layoutSubviews() {
        super.layoutSubviews()
        guard bounds.size != lastSize, bounds.width > 0, bounds.height > 0, document != nil else { return }
        lastSize = bounds.size
        let fit = scaleFactorForSizeToFit
        if fit > 0 { minScaleFactor = fit; maxScaleFactor = fit * 6; scaleFactor = fit }
    }
}

private struct ZoomableDocument: UIViewRepresentable {
    let document: PDFDocument
    let caption: String
    let command: Int
    let reset: Bool
    func makeCoordinator() -> Coordinator { Coordinator() }
    func makeUIView(context: Context) -> PDFView {
        let view = FittedPDFView()
        view.displayMode = .singlePage; view.displayDirection = .vertical; view.displaysPageBreaks = false
        view.backgroundColor = UIColor(TroopTheme.paper); view.autoScales = true
        view.document = document; view.accessibilityLabel = caption
        let doubleTap = UITapGestureRecognizer(target: context.coordinator, action: #selector(Coordinator.doubleTap(_:)))
        doubleTap.numberOfTapsRequired = 2; view.addGestureRecognizer(doubleTap)
        return view
    }
    func updateUIView(_ view: PDFView, context: Context) {
        if context.coordinator.command != command {
            context.coordinator.command = command
            view.scaleFactor = reset ? view.minScaleFactor : min(view.maxScaleFactor, view.scaleFactor * 1.5)
        }
    }
    final class Coordinator: NSObject {
        var command = 0
        @objc func doubleTap(_ gesture: UITapGestureRecognizer) {
            guard let view = gesture.view as? PDFView else { return }
            view.scaleFactor = view.scaleFactor > view.minScaleFactor * 1.1 ? view.minScaleFactor : min(view.maxScaleFactor, view.minScaleFactor * 2)
        }
    }
}
