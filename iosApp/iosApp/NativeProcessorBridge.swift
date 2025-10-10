//
//  NativeProcessorBridge.swift
//  Film Simulator
//
//  Swift bridge that actually calls Metal processor
//  This will be called from the iOS app layer
//

import Foundation
import UIKit
import shared

@objc public class NativeProcessorBridge: NSObject {

    @objc public static let shared = NativeProcessorBridge()

    private let metalProcessor = MetalLUTProcessorWrapper.shared

    private override init() {
        super.init()
        NSLog("[NativeProcessorBridge] Swift bridge initialized")
    }

    /// Intercept and process image with Metal before falling back to shared code
    @objc public func processImageIfNeeded(
        inputPath: String,
        outputPath: String,
        lutPath: String,
        createThumbnail: Bool
    ) -> Bool {
        NSLog("[NativeProcessorBridge] Intercepting image processing request")
        NSLog("[NativeProcessorBridge] Attempting to use Metal processor...")

        let result = metalProcessor.applyLUT(
            inputPath: inputPath,
            outputPath: outputPath,
            lutPath: lutPath,
            createThumbnail: createThumbnail
        )

        if result {
            NSLog("[NativeProcessorBridge] ✓✓✓ Successfully processed with Metal! ✓✓✓")
        } else {
            NSLog("[NativeProcessorBridge] ✗ Metal processing failed, will fall back to Core Image")
        }

        return result
    }

    @objc public func isMetalAvailable() -> Bool {
        return MetalLUTProcessorWrapper.isMetalAvailable()
    }
}

/// Global C function that can be called from Kotlin
@_cdecl("nativeProcessImage")
public func nativeProcessImage(
    inputPathPtr: UnsafePointer<CChar>,
    outputPathPtr: UnsafePointer<CChar>,
    lutPathPtr: UnsafePointer<CChar>,
    createThumbnail: Bool
) -> Bool {
    let inputPath = String(cString: inputPathPtr)
    let outputPath = String(cString: outputPathPtr)
    let lutPath = String(cString: lutPathPtr)

    return NativeProcessorBridge.shared.processImageIfNeeded(
        inputPath: inputPath,
        outputPath: outputPath,
        lutPath: lutPath,
        createThumbnail: createThumbnail
    )
}
