//
//  NativeProcessorBridge.swift
//  Film Simulator
//
//  Swift bridge that actually calls Metal processor
//  This will be called from the shared framework via Objective-C runtime
//

import Foundation
import UIKit
import shared

// IMPORTANT: This class will be discoverable via NSClassFromString
// The runtime name will be "iosApp.NativeProcessorBridge" automatically
@objc public class NativeProcessorBridge: NSObject {

    @objc public static let shared = NativeProcessorBridge()

    private let metalProcessor = MetalLUTProcessorWrapper.shared

    private override init() {
        super.init()
        NSLog("[NativeProcessorBridge.swift] ✓ Swift bridge initialized and ready")
    }

    /// Process image with Metal (called dynamically from shared framework)
    /// Method signature MUST match the Objective-C header exactly
    @objc public func processImageIfNeededWithInputPath(
        _ inputPath: String,
        outputPath: String,
        lutPath: String,
        createThumbnail: Bool
    ) -> Bool {
        NSLog("[NativeProcessorBridge.swift] ✓✓✓ Swift implementation called via runtime! ✓✓✓")
        NSLog("[NativeProcessorBridge.swift] Processing image with Metal...")
        NSLog("[NativeProcessorBridge.swift]   Input: \(inputPath)")
        NSLog("[NativeProcessorBridge.swift]   Output: \(outputPath)")
        NSLog("[NativeProcessorBridge.swift]   LUT: \(lutPath)")

        let result = metalProcessor.applyLUT(
            inputPath: inputPath,
            outputPath: outputPath,
            lutPath: lutPath,
            createThumbnail: createThumbnail
        )

        if result {
            NSLog("[NativeProcessorBridge.swift] ✓✓✓ Metal processing SUCCEEDED! ✓✓✓")
        } else {
            NSLog("[NativeProcessorBridge.swift] ✗ Metal processing failed")
        }

        return result
    }

    @objc public func isMetalAvailable() -> Bool {
        let available = MetalLUTProcessorWrapper.isMetalAvailable()
        NSLog("[NativeProcessorBridge.swift] Metal available: \(available)")
        return available
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

    return NativeProcessorBridge.shared.processImageIfNeededWithInputPath(
        inputPath,
        outputPath: outputPath,
        lutPath: lutPath,
        createThumbnail: createThumbnail
    )
}
