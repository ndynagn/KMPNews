import Foundation
import ImageIO
import UIKit

/// Downsamples before decoding, crops centrally and re-encodes pixels without source metadata.
enum ProfilePhotoProcessor {
    nonisolated static func prepare(_ data: Data) -> Data? {
        guard let source = CGImageSourceCreateWithData(data as CFData, nil),
            let image = CGImageSourceCreateThumbnailAtIndex(
                source, 0,
                [
                    kCGImageSourceCreateThumbnailFromImageAlways: true,
                    kCGImageSourceCreateThumbnailWithTransform: true,
                    kCGImageSourceThumbnailMaxPixelSize: 2048,
                    kCGImageSourceShouldCacheImmediately: true,
                ] as CFDictionary)
        else { return nil }

        let side = min(image.width, image.height)

        guard
            let cropped = image.cropping(
                to: CGRect(
                    x: (image.width - side) / 2, y: (image.height - side) / 2,
                    width: side, height: side))
        else { return nil }

        let size = min(side, 1024)
        let format = UIGraphicsImageRendererFormat()
        format.scale = 1
        format.opaque = true

        let rendered = UIGraphicsImageRenderer(size: CGSize(width: size, height: size), format: format).image {
            context in
            UIColor.white.setFill()
            context.fill(CGRect(x: 0, y: 0, width: size, height: size))
            UIImage(cgImage: cropped).draw(in: CGRect(x: 0, y: 0, width: size, height: size))
        }

        guard let jpeg = rendered.jpegData(compressionQuality: 0.85), jpeg.count <= 2_097_152 else { return nil }

        return jpeg
    }
}
