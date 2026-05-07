package io.github.yahiaangelo.filmsimulator.view

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.interop.UIKitView
import io.github.yahiaangelo.filmsimulator.util.RealtimeImageProcessor
import kotlinx.cinterop.ExperimentalForeignApi
import platform.UIKit.UIView

/**
 * iOS implementation of native surface rendering
 * Will use Metal for GPU-based rendering
 *
 * TODO: Implement MTKView for Metal rendering
 * Currently provides stub implementation
 */
@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun NativeSurfaceView(
    modifier: Modifier,
    processor: RealtimeImageProcessor,
    imageHandle: Long,
    onSurfaceReady: (Any) -> Unit
) {
    val scope = rememberCoroutineScope()

    DisposableEffect(imageHandle) {
        onDispose {
            if (imageHandle != 0L) {
                processor.releaseImage(imageHandle)
            }
        }
    }

    UIKitView(
        modifier = modifier,
        factory = {
            // TODO: Create MTKView for Metal rendering
            val view = UIView()
            onSurfaceReady(view)
            view
        },
        update = { view ->
            // TODO: Update Metal rendering when handle changes
            println("NativeSurfaceView: Update called for handle $imageHandle")
        }
    )
}