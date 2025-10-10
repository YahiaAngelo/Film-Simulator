//
//  MetalLUTProcessorWrapper.swift
//  Film Simulator
//
//  Swift wrapper to make Metal processor accessible from shared Kotlin code
//

import Foundation
import UIKit
import Metal

// Global function that can be called from AppDelegate
@_cdecl("setupMetalProcessor")
public func setupMetalProcessor() {
    NSLog("[MetalLUTProcessorWrapper] Setting up Metal processor globally")
    MetalLUTProcessorWrapper.shared.setup()
}

// Global function that can be called from Kotlin through runtime
@_cdecl("processImageWithMetal")
public func processImageWithMetal(inputPath: UnsafePointer<CChar>, outputPath: UnsafePointer<CChar>, lutPath: UnsafePointer<CChar>, createThumbnail: Bool) -> Bool {
    let input = String(cString: inputPath)
    let output = String(cString: outputPath)
    let lut = String(cString: lutPath)

    NSLog("[MetalLUTProcessorWrapper] Global function called - Processing with Metal")
    return MetalLUTProcessorWrapper.shared.applyLUT(
        inputPath: input,
        outputPath: output,
        lutPath: lut,
        createThumbnail: createThumbnail
    )
}

@objc(MetalLUTProcessorWrapper)
public class MetalLUTProcessorWrapper: NSObject {
    private var metalProcessor: MetalLUTProcessor?

    @objc public static let shared = MetalLUTProcessorWrapper()

    private override init() {
        super.init()
        setup()
    }

    @objc public func setup() {
        NSLog("[MetalLUTProcessorWrapper] Initializing Metal processor")
        self.metalProcessor = MetalLUTProcessor()

        if self.metalProcessor != nil {
            NSLog("[MetalLUTProcessorWrapper] Metal processor initialized successfully")
        } else {
            NSLog("[MetalLUTProcessorWrapper] Failed to initialize Metal processor")
        }
    }

    @objc public func applyLUT(inputPath: String, outputPath: String, lutPath: String, createThumbnail: Bool) -> Bool {
        NSLog("[MetalLUTProcessorWrapper] applyLUT called")
        NSLog("[MetalLUTProcessorWrapper] Input: \(inputPath)")
        NSLog("[MetalLUTProcessorWrapper] Output: \(outputPath)")
        NSLog("[MetalLUTProcessorWrapper] LUT: \(lutPath)")
        NSLog("[MetalLUTProcessorWrapper] Thumbnail: \(createThumbnail)")

        guard let processor = metalProcessor else {
            NSLog("[MetalLUTProcessorWrapper] ERROR: Metal processor is nil")
            return false
        }

        let result = processor.applyLUT(
            withInputPath: inputPath,
            outputPath: outputPath,
            lutPath: lutPath,
            createThumbnail: createThumbnail
        )

        NSLog("[MetalLUTProcessorWrapper] Processing completed with result: \(result)")
        return result
    }

    @objc public func processImage(inputPath: NSString, outputPath: NSString, lutPath: NSString, createThumbnail: Bool) -> Bool {
        return applyLUT(
            inputPath: inputPath as String,
            outputPath: outputPath as String,
            lutPath: lutPath as String,
            createThumbnail: createThumbnail
        )
    }

    @objc public class func isMetalAvailable() -> Bool {
        // Check if Metal is available on this device
        if let device = MTLCreateSystemDefaultDevice() {
            NSLog("[MetalLUTProcessorWrapper] Metal is available on device: \(device.name)")
            return true
        } else {
            NSLog("[MetalLUTProcessorWrapper] Metal is not available on this device")
            return false
        }
    }
}