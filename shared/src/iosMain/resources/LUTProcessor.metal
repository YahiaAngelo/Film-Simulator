//
//  LUTProcessor.metal
//  Film Simulator
//
//  Metal shader for GPU-accelerated 3D LUT color transformation
//

#include <metal_stdlib>
using namespace metal;

// Kernel function for applying 3D LUT to an image
kernel void applyLUT3D(
    texture2d<float, access::read> inputTexture [[texture(0)]],
    texture2d<float, access::write> outputTexture [[texture(1)]],
    texture3d<float, access::sample> lutTexture [[texture(2)]],
    constant float& lutStrength [[buffer(0)]],
    uint2 gid [[thread_position_in_grid]]
) {
    // Check bounds
    if (gid.x >= outputTexture.get_width() || gid.y >= outputTexture.get_height()) {
        return;
    }

    // Read input pixel
    float4 inputColor = inputTexture.read(gid);

    // LUT size is encoded in the texture dimensions
    float lutSize = float(lutTexture.get_width());

    // Calculate normalized coordinates for LUT sampling
    // The formula matches the Android trilinear interpolation
    float3 coords = (inputColor.rgb * (lutSize - 1.0) + 0.5) / lutSize;

    // Clamp coordinates to valid range [0, 1]
    coords = clamp(coords, 0.0, 1.0);

    // Create sampler with linear filtering for hardware trilinear interpolation
    constexpr sampler lutSampler(
        filter::linear,
        address::clamp_to_edge
    );

    // Sample the 3D LUT texture with trilinear interpolation
    float4 lutColor = lutTexture.sample(lutSampler, coords);

    // Mix based on strength parameter (1.0 = full LUT, 0.0 = original)
    float3 outputRGB = mix(inputColor.rgb, lutColor.rgb, lutStrength);

    // Write output pixel, preserving original alpha
    outputTexture.write(float4(outputRGB, inputColor.a), gid);
}

// Optimized kernel for thumbnail generation with simultaneous downscaling
kernel void applyLUT3DWithDownscale(
    texture2d<float, access::sample> inputTexture [[texture(0)]],
    texture2d<float, access::write> outputTexture [[texture(1)]],
    texture3d<float, access::sample> lutTexture [[texture(2)]],
    constant float2& scaleFactor [[buffer(0)]],
    constant float& lutStrength [[buffer(1)]],
    uint2 gid [[thread_position_in_grid]]
) {
    // Check bounds
    if (gid.x >= outputTexture.get_width() || gid.y >= outputTexture.get_height()) {
        return;
    }

    // Calculate source coordinates with scaling
    float2 sourceCoord = float2(gid) * scaleFactor;

    // Use a sampler for the input texture to get bilinear filtering during downscale
    constexpr sampler inputSampler(
        filter::linear,
        address::clamp_to_edge
    );

    // Sample input with bilinear filtering for better quality when downscaling
    float4 inputColor = inputTexture.sample(
        inputSampler,
        (sourceCoord + 0.5) / float2(inputTexture.get_width(), inputTexture.get_height())
    );

    // LUT size from texture dimensions
    float lutSize = float(lutTexture.get_width());

    // Calculate LUT coordinates
    float3 coords = (inputColor.rgb * (lutSize - 1.0) + 0.5) / lutSize;
    coords = clamp(coords, 0.0, 1.0);

    // Sample LUT with trilinear interpolation
    constexpr sampler lutSampler(
        filter::linear,
        address::clamp_to_edge
    );

    float4 lutColor = lutTexture.sample(lutSampler, coords);

    // Apply LUT with strength
    float3 outputRGB = mix(inputColor.rgb, lutColor.rgb, lutStrength);

    // Write output
    outputTexture.write(float4(outputRGB, inputColor.a), gid);
}

// Simple pass-through kernel for testing
kernel void passthrough(
    texture2d<float, access::read> inputTexture [[texture(0)]],
    texture2d<float, access::write> outputTexture [[texture(1)]],
    uint2 gid [[thread_position_in_grid]]
) {
    if (gid.x >= outputTexture.get_width() || gid.y >= outputTexture.get_height()) {
        return;
    }

    float4 color = inputTexture.read(gid);
    outputTexture.write(color, gid);
}