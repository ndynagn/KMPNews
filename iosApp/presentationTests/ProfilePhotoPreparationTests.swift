import Foundation
import ImageIO
import UIKit
import UniformTypeIdentifiers

@main
struct ProfilePhotoPreparationTests {
    static func main() {
        let format = UIGraphicsImageRendererFormat()
        format.scale = 1
        let input = UIGraphicsImageRenderer(size: CGSize(width: 2400, height: 1600), format: format).image { context in
            UIColor.blue.setFill()
            context.fill(CGRect(x: 0, y: 0, width: 2400, height: 1600))
        }

        guard let cgImage = input.cgImage else { preconditionFailure("Fixture image creation failed") }

        let source = NSMutableData()

        guard let writer = CGImageDestinationCreateWithData(source, UTType.jpeg.identifier as CFString, 1, nil) else {
            preconditionFailure("Fixture encoder creation failed")
        }

        CGImageDestinationAddImage(
            writer, cgImage,
            [
                kCGImagePropertyOrientation: 6,
                kCGImagePropertyGPSDictionary: [kCGImagePropertyGPSLatitude: 10, kCGImagePropertyGPSLatitudeRef: "N"],
            ] as CFDictionary)

        precondition(CGImageDestinationFinalize(writer))

        guard let jpeg = ProfilePhotoProcessor.prepare(source as Data),
            let decoded = CGImageSourceCreateWithData(jpeg as CFData, nil),
            let properties = CGImageSourceCopyPropertiesAtIndex(decoded, 0, nil) as? [CFString: Any]
        else { preconditionFailure("Photo preparation failed") }

        precondition(properties[kCGImagePropertyPixelWidth] as? Int == 1024)
        precondition(properties[kCGImagePropertyPixelHeight] as? Int == 1024)
        precondition(properties[kCGImagePropertyGPSDictionary] == nil)
        precondition(jpeg.count <= 2_097_152)

        precondition(ProfilePhotoProcessor.prepare(Data([1, 2, 3])) == nil)

        print("PASS: square output dimensions, JPEG size limit, GPS removal and invalid image rejection")
    }
}
