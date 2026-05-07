package io.github.yahiaangelo.filmsimulator.view

import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import io.github.yahiaangelo.filmsimulator.util.RealtimeImageProcessor
import kotlinx.coroutines.launch

/**
 * Android implementation of native surface rendering
 * Displays images directly from native memory without file I/O
 */
@Composable
actual fun NativeSurfaceView(
    modifier: Modifier,
    processor: RealtimeImageProcessor,
    imageHandle: Long,
    onSurfaceReady: (Any) -> Unit
) {
    val scope = rememberCoroutineScope()
    var currentSurface by remember { mutableStateOf<Surface?>(null) }

    DisposableEffect(imageHandle) {
        onDispose {
            // Clean up when composable leaves composition
            if (imageHandle != 0L) {
                processor.releaseImage(imageHandle)
            }
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { context ->
            SurfaceView(context).apply {
                holder.addCallback(object : SurfaceHolder.Callback {
                    override fun surfaceCreated(holder: SurfaceHolder) {
                        val surface = holder.surface
                        currentSurface = surface
                        onSurfaceReady(surface)

                        // Initial render
                        if (imageHandle != 0L) {
                            scope.launch {
                                processor.renderToSurface(imageHandle, surface)
                            }
                        }
                    }

                    override fun surfaceChanged(
                        holder: SurfaceHolder,
                        format: Int,
                        width: Int,
                        height: Int
                    ) {
                        // Re-render on surface change
                        if (imageHandle != 0L) {
                            scope.launch {
                                processor.renderToSurface(imageHandle, holder.surface)
                            }
                        }
                    }

                    override fun surfaceDestroyed(holder: SurfaceHolder) {
                        currentSurface = null
                    }
                })
            }
        },
        update = { surfaceView ->
            // Re-render when image handle changes
            currentSurface?.let { surface ->
                if (imageHandle != 0L) {
                    scope.launch {
                        processor.renderToSurface(imageHandle, surface)
                    }
                }
            }
        }
    )
}