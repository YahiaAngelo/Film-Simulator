package io.github.yahiaangelo.filmsimulator.util

/**
 * Feature flags to enable/disable experimental features
 */
object FeatureFlags {
    /**
     * Enable real-time native image processing
     * When true: Uses RealtimeImageProcessor with native memory persistence
     * When false: Uses existing file-based processing
     */
    const val USE_REALTIME_PROCESSING = false

    /**
     * Enable native surface rendering
     * When true: Renders directly from native memory to surface
     * When false: Uses Coil to load images from disk
     */
    const val USE_NATIVE_SURFACE_RENDERING = false
}