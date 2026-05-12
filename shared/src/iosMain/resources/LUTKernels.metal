#include <metal_stdlib>
#include <simd/simd.h>
using namespace metal;

// Trilinear interpolation for 3D LUT sampling
float3 sampleLUT3D(texture3d<float, access::sample> lutTexture,
                   float3 color,
                   sampler lutSampler) {
    // Sample the 3D texture with hardware trilinear interpolation
    return lutTexture.sample(lutSampler, color).rgb;
}

// Main LUT processing kernel
kernel void lutKernel(texture2d<float, access::read> inputTexture [[texture(0)]],
                      texture2d<float, access::write> outputTexture [[texture(1)]],
                      texture3d<float, access::sample> lutTexture [[texture(2)]],
                      constant float &lutSize [[buffer(0)]],
                      uint2 gid [[thread_position_in_grid]]) {

    // Check bounds
    if (gid.x >= outputTexture.get_width() || gid.y >= outputTexture.get_height()) {
        return;
    }

    // Create sampler for LUT with trilinear filtering
    constexpr sampler lutSampler(coord::normalized,
                                  address::clamp_to_edge,
                                  filter::linear);

    // Read input pixel
    float4 inputColor = inputTexture.read(gid);

    // Apply 3D LUT
    float3 outputColor = sampleLUT3D(lutTexture, inputColor.rgb, lutSampler);

    // Write output with preserved alpha
    outputTexture.write(float4(outputColor, inputColor.a), gid);
}

// Film grain kernel
kernel void grainKernel(texture2d<float, access::read> inputTexture [[texture(0)]],
                        texture2d<float, access::write> outputTexture [[texture(1)]],
                        constant float &intensity [[buffer(0)]],
                        constant float &seed [[buffer(1)]],
                        uint2 gid [[thread_position_in_grid]]) {

    // Check bounds
    if (gid.x >= outputTexture.get_width() || gid.y >= outputTexture.get_height()) {
        return;
    }

    // Read input pixel
    float4 inputColor = inputTexture.read(gid);

    // Generate pseudo-random noise based on position and seed
    float2 coord = float2(gid) * 0.001 + seed;
    float noise = fract(sin(dot(coord, float2(12.9898, 78.233))) * 43758.5453);
    noise = (noise - 0.5) * intensity * 0.2;

    // Apply grain to RGB channels
    float3 grainedColor = inputColor.rgb + noise;
    grainedColor = clamp(grainedColor, 0.0, 1.0);

    // Write output with preserved alpha
    outputTexture.write(float4(grainedColor, inputColor.a), gid);
}

// Combined LUT + Grain kernel for better performance
kernel void lutGrainKernel(texture2d<float, access::read> inputTexture [[texture(0)]],
                           texture2d<float, access::write> outputTexture [[texture(1)]],
                           texture3d<float, access::sample> lutTexture [[texture(2)]],
                           constant float &lutSize [[buffer(0)]],
                           constant float &grainIntensity [[buffer(1)]],
                           constant float &seed [[buffer(2)]],
                           uint2 gid [[thread_position_in_grid]]) {

    // Check bounds
    if (gid.x >= outputTexture.get_width() || gid.y >= outputTexture.get_height()) {
        return;
    }

    // Create sampler for LUT
    constexpr sampler lutSampler(coord::normalized,
                                  address::clamp_to_edge,
                                  filter::linear);

    // Read input pixel
    float4 inputColor = inputTexture.read(gid);

    // Apply 3D LUT
    float3 outputColor = sampleLUT3D(lutTexture, inputColor.rgb, lutSampler);

    // Apply grain if intensity > 0
    if (grainIntensity > 0.0) {
        float2 coord = float2(gid) * 0.001 + seed;
        float noise = fract(sin(dot(coord, float2(12.9898, 78.233))) * 43758.5453);
        noise = (noise - 0.5) * grainIntensity * 0.2;
        outputColor = clamp(outputColor + noise, 0.0, 1.0);
    }

    // Write output with preserved alpha
    outputTexture.write(float4(outputColor, inputColor.a), gid);
}

// Fast thumbnail generation with built-in downsampling
kernel void thumbnailLutKernel(texture2d<float, access::sample> inputTexture [[texture(0)]],
                                texture2d<float, access::write> outputTexture [[texture(1)]],
                                texture3d<float, access::sample> lutTexture [[texture(2)]],
                                constant float2 &scaleFactor [[buffer(0)]],
                                uint2 gid [[thread_position_in_grid]]) {

    // Check bounds
    if (gid.x >= outputTexture.get_width() || gid.y >= outputTexture.get_height()) {
        return;
    }

    // Create samplers
    constexpr sampler inputSampler(coord::normalized,
                                    address::clamp_to_edge,
                                    filter::linear);

    constexpr sampler lutSampler(coord::normalized,
                                  address::clamp_to_edge,
                                  filter::linear);

    // Calculate normalized coordinates for sampling
    float2 texCoord = (float2(gid) + 0.5) / float2(outputTexture.get_width(), outputTexture.get_height());

    // Sample input with bilinear filtering for smooth downsampling
    float4 inputColor = inputTexture.sample(inputSampler, texCoord);

    // Apply 3D LUT
    float3 outputColor = sampleLUT3D(lutTexture, inputColor.rgb, lutSampler);

    // Write output with preserved alpha
    outputTexture.write(float4(outputColor, inputColor.a), gid);
}