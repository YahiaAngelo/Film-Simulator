package io.github.yahiaangelo.filmsimulator.image

/**
 * Slider-domain values driving the image pipeline. Ranges here match the UI
 * (sliders surface -20..20 or 0..10); [SkiaImageProcessor] normalizes them to the
 * shader's working ranges, so don't pre-scale on the producer side.
 */
data class ImageAdjustments(
    val contrast: Float = 0f,
    val shadows: Float = 0f,
    val highlights: Float = 0f,
    val saturation: Float = 0f,
    val temperature: Float = 0f,
    val exposure: Float = 0f,
    val grain: Float = 0f,
    val chromaticAberration: Float = 0f,
    /**
     * LUT mix amount in 0..200 (percent). 0 = pure source, 100 = full LUT,
     * values above 100 extrapolate past the LUT result to amplify subtle film
     * stocks. The mix is clamped back into displayable range in the shader.
     */
    val lutIntensity: Float = 100f,
) {

    fun isDefault(): Boolean =
        contrast == 0f &&
            shadows == 0f &&
            highlights == 0f &&
            saturation == 0f &&
            temperature == 0f &&
            exposure == 0f &&
            grain == 0f &&
            chromaticAberration == 0f &&
            lutIntensity == 100f

    fun hasAdjustments(): Boolean = !isDefault()
}