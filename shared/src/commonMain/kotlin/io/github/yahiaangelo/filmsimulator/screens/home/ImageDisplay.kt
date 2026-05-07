package io.github.yahiaangelo.filmsimulator.screens.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import coil3.compose.LocalPlatformContext
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import com.github.panpf.zoomimage.CoilZoomAsyncImage
import com.github.panpf.zoomimage.rememberCoilZoomState
import io.github.yahiaangelo.filmsimulator.util.FeatureFlags
import io.github.yahiaangelo.filmsimulator.util.RealtimeImageProcessor
import io.github.yahiaangelo.filmsimulator.view.NativeSurfaceView
import kotlinx.coroutines.launch
import okio.FileSystem

/**
 * Composable that displays images using either traditional file-based rendering
 * or optimized native surface rendering based on feature flags
 */
@Composable
fun ImageDisplay(
    modifier: Modifier = Modifier,
    state: HomeUiState,
    processor: RealtimeImageProcessor? = null
) {
    if (FeatureFlags.USE_NATIVE_SURFACE_RENDERING && processor != null && state.imageHandle != 0L) {
        // Use optimized native surface rendering
        NativeSurfaceImageDisplay(
            modifier = modifier,
            processor = processor,
            imageHandle = state.imageHandle
        )
    } else {
        // Use traditional Coil-based file rendering
        CoilImageDisplay(
            modifier = modifier,
            imagePath = state.image
        )
    }
}

/**
 * Native surface rendering - zero copy, real-time updates
 */
@Composable
private fun NativeSurfaceImageDisplay(
    modifier: Modifier,
    processor: RealtimeImageProcessor,
    imageHandle: Long
) {
    val scope = rememberCoroutineScope()
    var surface by remember { mutableStateOf<Any?>(null) }

    Box(modifier = modifier) {
        NativeSurfaceView(
            modifier = Modifier.fillMaxSize(),
            processor = processor,
            imageHandle = imageHandle,
            onSurfaceReady = { newSurface ->
                surface = newSurface
                scope.launch {
                    // Initial render when surface is ready
                    processor.renderToSurface(imageHandle, newSurface)
                }
            }
        )
    }

    // Re-render when image handle changes
    LaunchedEffect(imageHandle, surface) {
        if (imageHandle != 0L && surface != null) {
            processor.renderToSurface(imageHandle, surface!!)
        }
    }
}

/**
 * Traditional file-based rendering with Coil
 */
@Composable
private fun CoilImageDisplay(
    modifier: Modifier,
    imagePath: String?
) {
    imagePath?.let { path ->
        val zoomState = rememberCoilZoomState()

        CoilZoomAsyncImage(
            modifier = modifier,
            zoomState = zoomState,
            model = ImageRequest.Builder(LocalPlatformContext.current)
                .data(
                    "${FileSystem.SYSTEM_TEMPORARY_DIRECTORY}/${
                        path.substringBefore("?")
                    }"
                )
                .memoryCacheKey(path)
                .diskCacheKey(path)
                .memoryCachePolicy(CachePolicy.DISABLED) // Disable memory cache for real-time updates
                .diskCachePolicy(CachePolicy.DISABLED)
                .build(),
            contentDescription = null,
            scrollBar = null
        )
    }
}