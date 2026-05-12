package io.github.yahiaangelo.filmsimulator.image.shaders

/**
 * Single SkSL shader that runs the full image-processing pipeline in one pass:
 *
 *   source → (optional) 3D LUT → exposure → contrast → shadows/highlights
 *          → saturation → temperature → grain → chromatic aberration
 *
 * Tonal adjustments (exposure, contrast) are performed in linear light to avoid
 * the highlight crush you get when scaling sRGB-encoded values directly. Spatial
 * effects (CA, grain) work in normalized output coordinates so the preview at any
 * resolution matches the full-resolution export.
 *
 * Uniform conventions:
 *   - All scalar adjustments are pre-normalized by the caller to the ranges noted
 *     beside each uniform. A value of 0 (or 0 for `useLut`/`grain`) is a no-op.
 *   - `imageScale` maps `fragCoord` (output pixel space) to source-image pixel
 *     space so a single shader instance can render either full-res or a downscaled
 *     preview without an extra resize pass.
 *   - The LUT texture is laid out as `lutSize` wide × `lutSize * lutSize` tall.
 */
internal object ImageProcessingShader {

    const val SHADER: String = """
        uniform shader image;
        uniform shader lut;
        uniform float useLut;        // 0 = bypass LUT, 1 = apply
        uniform float lutIntensity;  // 0..1 mix between source and LUT-processed
        uniform float lutSize;       // e.g. 33 for a 33^3 LUT
        uniform float2 imageScale;   // (srcW/outW, srcH/outH)
        uniform float2 resolution;   // output dimensions in pixels

        // Tonal — all pre-normalized by the caller.
        uniform float exposure;      // stops; ~[-2, 2]
        uniform float contrast;      // multiplier offset; ~[-1, 1] (negative = flatter)
        uniform float shadows;       // lifts (positive) / crushes (negative) shadow tones; ~[-0.5, 0.5]
        uniform float highlights;    // boosts (positive) / pulls down (negative) highlights; ~[-0.5, 0.5]
        uniform float saturation;    // ~[-1, 1]
        uniform float temperature;   // ~[-1, 1] (warm positive, cool negative)
        uniform float grain;         // 0..1 amplitude
        uniform float grainSeed;     // changes per export to vary noise pattern
        uniform float chromaticAberration; // 0..1 normalized strength

        // ---- sRGB <-> linear ----
        // Piecewise sRGB curve. Operates per channel.
        half3 srgbToLinear(half3 c) {
            return half3(
                c.r <= half(0.04045) ? c.r / half(12.92) : pow((c.r + half(0.055)) / half(1.055), half(2.4)),
                c.g <= half(0.04045) ? c.g / half(12.92) : pow((c.g + half(0.055)) / half(1.055), half(2.4)),
                c.b <= half(0.04045) ? c.b / half(12.92) : pow((c.b + half(0.055)) / half(1.055), half(2.4))
            );
        }

        half3 linearToSrgb(half3 c) {
            c = clamp(c, half3(0.0), half3(1.0));
            return half3(
                c.r <= half(0.0031308) ? c.r * half(12.92) : half(1.055) * pow(c.r, half(1.0 / 2.4)) - half(0.055),
                c.g <= half(0.0031308) ? c.g * half(12.92) : half(1.055) * pow(c.g, half(1.0 / 2.4)) - half(0.055),
                c.b <= half(0.0031308) ? c.b * half(12.92) : half(1.055) * pow(c.b, half(1.0 / 2.4)) - half(0.055)
            );
        }

        // ---- LUT sampling (trilinear, matches CPU reference) ----
        half3 sampleLut(float r, float g, float b) {
            float x = r + 0.5;
            float y = b * lutSize + g + 0.5;
            return lut.eval(float2(x, y)).rgb;
        }

        half3 applyLut(half3 src) {
            float scale = lutSize - 1.0;
            float rs = clamp(src.r, half(0.0), half(1.0)) * scale;
            float gs = clamp(src.g, half(0.0), half(1.0)) * scale;
            float bs = clamp(src.b, half(0.0), half(1.0)) * scale;

            float r0 = floor(rs); float g0 = floor(gs); float b0 = floor(bs);
            float r1 = min(r0 + 1.0, scale);
            float g1 = min(g0 + 1.0, scale);
            float b1 = min(b0 + 1.0, scale);

            float rd = rs - r0; float gd = gs - g0; float bd = bs - b0;

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
            return mix(c0, c1, half(bd));
        }

        // Inigo Quilez 2D→1D hash. Lifts the input into 3D, scrambles via fract
        // and dot products, and folds back to a single float in [0, 1). Extremely
        // well distributed even at large input coordinates — no visible periodicity
        // at typical image sizes, which is what makes it suitable for sharp grain
        // noise instead of structured-noise functions like value or gradient
        // noise. grainSeed shifts the input plane between renders.
        float hash12(float2 p) {
            float3 p3 = fract(float3(p.x, p.y, p.x) * 0.1031);
            p3 += dot(p3, p3.yzx + 33.33);
            return fract((p3.x + p3.y) * p3.z);
        }

        // Sample the source LUT-applied color at a normalized offset for CA.
        half3 sampleProcessed(float2 fragCoord, float2 offset) {
            float2 sampleCoord = (fragCoord + offset * resolution) * imageScale;
            half4 src = image.eval(sampleCoord);
            half3 rgb = src.rgb;
            if (useLut > 0.5) {
                rgb = mix(rgb, applyLut(rgb), half(lutIntensity));
            }
            return rgb;
        }

        half4 main(float2 fragCoord) {
            // Chromatic aberration samples each channel from a slightly different
            // radial offset, in normalized image coords so the visual scale is
            // resolution-independent.
            half3 color;
            half alpha;
            if (chromaticAberration > 0.0) {
                float2 uv = fragCoord / resolution;
                float2 dir = uv - float2(0.5);
                float strength = chromaticAberration * 0.01; // ~1% of frame at max
                float3 r3 = float3(sampleProcessed(fragCoord, dir *  strength));
                float3 g3 = float3(sampleProcessed(fragCoord, float2(0.0)));
                float3 b3 = float3(sampleProcessed(fragCoord, dir * -strength));
                color = half3(half(r3.r), half(g3.g), half(b3.b));
                alpha = image.eval(fragCoord * imageScale).a;
            } else {
                half4 raw = image.eval(fragCoord * imageScale);
                color = raw.rgb;
                if (useLut > 0.5) {
                    color = mix(color, applyLut(color), half(lutIntensity));
                }
                alpha = raw.a;
            }

            // Move to linear light for tonal ops.
            half3 lin = srgbToLinear(color);

            // Exposure: stops (2^exposure multiplier in linear light).
            if (exposure != 0.0) {
                lin *= half(exp2(exposure));
            }

            // Contrast: scale around perceptual mid-grey (0.5 in sRGB ≈ 0.2140 linear).
            if (contrast != 0.0) {
                half factor = contrast > 0.0
                    ? half(1.0 + contrast)
                    : half(1.0 / (1.0 - contrast));
                lin = (lin - half(0.2140)) * factor + half(0.2140);
            }

            // Shadows / highlights: tonal mask in linear light. A luma-derived
            // mask isolates the dark or bright regions and the adjustment is
            // added there. Curves (pow > 1) make the falloff soft so transitions
            // between affected and untouched tones stay clean.
            if (shadows != 0.0 || highlights != 0.0) {
                half lumaTone = clamp(
                    dot(lin, half3(0.2126, 0.7152, 0.0722)),
                    half(0.0),
                    half(1.0)
                );
                if (shadows != 0.0) {
                    half mask = pow(half(1.0) - lumaTone, half(2.2));
                    lin += half3(half(shadows)) * mask;
                }
                if (highlights != 0.0) {
                    half mask = pow(lumaTone, half(2.2));
                    lin += half3(half(highlights)) * mask;
                }
            }

            // Saturation: mix toward luminance (Rec.709 in linear).
            if (saturation != 0.0) {
                half luma = dot(lin, half3(0.2126, 0.7152, 0.0722));
                lin = mix(half3(luma), lin, half(1.0 + saturation));
            }

            // Back to sRGB for the rest.
            half3 srgb = linearToSrgb(lin);

            // Temperature: simple warm/cool tilt in sRGB. Positive = warmer.
            // Push R and (slightly) G up while pulling B down for warm, inverse for cool.
            if (temperature != 0.0) {
                srgb.r = clamp(srgb.r + half(temperature * 0.08), half(0.0), half(1.0));
                srgb.g = clamp(srgb.g + half(temperature * 0.02), half(0.0), half(1.0));
                srgb.b = clamp(srgb.b - half(temperature * 0.08), half(0.0), half(1.0));
            }

            // Grain: sharp per-pixel hash, one tap per channel. Channels use
            // large, mutually irrational offsets so the noise is completely
            // decorrelated and reads as chromatic silver-halide grain instead of
            // a luminance overlay. grainSeed shifts the entire field between
            // renders. Coordinate is in output-pixel space so the noise is at the
            // finest possible scale the render target can resolve — that
            // high-frequency look is what reads as "random film grain" rather
            // than the structured shimmer of interpolated noise.
            if (grain > 0.0) {
                float2 gp = fragCoord + float2(grainSeed * 137.7, grainSeed * 311.3);
                float nR = hash12(gp + float2(  17.31,   91.07));
                float nG = hash12(gp + float2(1313.71,  717.13));
                float nB = hash12(gp + float2(2731.37, 4297.53));
                half3 noise = half3(half(nR), half(nG), half(nB)) - half3(0.5);

                half luma2 = dot(srgb, half3(0.2126, 0.7152, 0.0722));
                half midtone = half(1.0) - abs(luma2 - half(0.5)) * half(1.4);
                midtone = max(midtone, half(0.35));

                srgb += noise * half(grain) * midtone;
                srgb = clamp(srgb, half3(0.0), half3(1.0));
            }

            return half4(srgb, alpha);
        }
    """
}