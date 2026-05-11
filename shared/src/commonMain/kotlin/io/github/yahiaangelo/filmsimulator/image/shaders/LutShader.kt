package io.github.yahiaangelo.filmsimulator.image.shaders

/**
 * SkSL shader that applies a 3D LUT to a source image using a 2D LUT texture.
 *
 * The LUT texture is laid out as a vertical stack of `lutSize` slices: width = lutSize,
 * height = lutSize * lutSize. Slice `bi` occupies the rows `bi*lutSize .. bi*lutSize + (lutSize-1)`,
 * where each row indexes green and each column indexes red.
 *
 * Sampling happens on integer pixel centers (the eight enclosing LUT cells) and trilinear
 * interpolation is performed manually so the result matches the CPU reference (lut_processor.cpp /
 * CoreImageLUTProcessor) exactly. This avoids the slice-bleeding that bilinear filtering across
 * row boundaries would otherwise introduce.
 */
internal object LutShader {

    const val SHADER: String = """
        uniform shader image;
        uniform shader lut;
        uniform float lutSize;
        // Multiplier mapping the output (fragCoord) space into source-image pixel space.
        // Use (1, 1) when output and source share dimensions; (srcW/outW, srcH/outH) when
        // downscaling so the source shader can be sampled with linear filtering.
        uniform float2 imageScale;

        half3 sampleLut(float r, float g, float b) {
            float x = r + 0.5;
            float y = b * lutSize + g + 0.5;
            return lut.eval(float2(x, y)).rgb;
        }

        half4 main(float2 fragCoord) {
            half4 src = image.eval(fragCoord * imageScale);
            float scale = lutSize - 1.0;

            float rs = clamp(src.r, 0.0, 1.0) * scale;
            float gs = clamp(src.g, 0.0, 1.0) * scale;
            float bs = clamp(src.b, 0.0, 1.0) * scale;

            float r0 = floor(rs);
            float g0 = floor(gs);
            float b0 = floor(bs);

            float r1 = min(r0 + 1.0, scale);
            float g1 = min(g0 + 1.0, scale);
            float b1 = min(b0 + 1.0, scale);

            float rd = rs - r0;
            float gd = gs - g0;
            float bd = bs - b0;

            half3 c000 = sampleLut(r0, g0, b0);
            half3 c100 = sampleLut(r1, g0, b0);
            half3 c010 = sampleLut(r0, g1, b0);
            half3 c110 = sampleLut(r1, g1, b0);
            half3 c001 = sampleLut(r0, g0, b1);
            half3 c101 = sampleLut(r1, g0, b1);
            half3 c011 = sampleLut(r0, g1, b1);
            half3 c111 = sampleLut(r1, g1, b1);

            half3 c00 = mix(c000, c100, half(rd));
            half3 c10 = mix(c010, c110, half(rd));
            half3 c01 = mix(c001, c101, half(rd));
            half3 c11 = mix(c011, c111, half(rd));

            half3 c0 = mix(c00, c10, half(gd));
            half3 c1 = mix(c01, c11, half(gd));

            half3 result = mix(c0, c1, half(bd));

            return half4(result, src.a);
        }
    """
}