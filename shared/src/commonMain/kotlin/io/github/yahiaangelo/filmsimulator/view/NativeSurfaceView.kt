package io.github.yahiaangelo.filmsimulator.view

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.yahiaangelo.filmsimulator.util.RealtimeImageProcessor

/**
 * Native surface view for zero-copy image rendering
 *
 * This composable displays images directly from native/GPU memory
 * without any file I/O or memory copies.
 *
 * Platform implementations:
 * - Android: SurfaceView with direct native rendering
 * - iOS: MTKView or UIView with Metal rendering
 */
@Composable
expect fun NativeSurfaceView(
    modifier: Modifier = Modifier,
    processor: RealtimeImageProcessor,
    imageHandle: Long,
    onSurfaceReady: (Any) -> Unit = {}
)