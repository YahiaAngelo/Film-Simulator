#include <metal_stdlib>
#include <simd/simd.h>
using namespace metal;

// Vertex shader structure
struct VertexIn {
    float2 position [[attribute(0)]];
    float2 texCoord [[attribute(1)]];
};

struct VertexOut {
    float4 position [[position]];
    float2 texCoord;
};

// Simple vertex shader for full-screen quad
vertex VertexOut vertexShader(VertexIn in [[stage_in]]) {
    VertexOut out;
    out.position = float4(in.position, 0.0, 1.0);
    out.texCoord = in.texCoord;
    return out;
}

// 3D LUT application fragment shader
fragment float4 lutFragment(VertexOut in [[stage_in]],
                            texture2d<float> inputTexture [[texture(0)]],
                            texture3d<float> lutTexture [[texture(1)]],
                            constant float &lutSize [[buffer(0)]]) {

    constexpr sampler textureSampler(mag_filter::linear,
                                      min_filter::linear,
                                      address::clamp_to_edge);

    constexpr sampler lutSampler(mag_filter::linear,
                                  min_filter::linear,
                                  address::clamp_to_edge);

    // Sample the input image
    float4 inputColor = inputTexture.sample(textureSampler, in.texCoord);

    // Preserve alpha
    float alpha = inputColor.a;

    // Scale RGB values to LUT coordinates (0-1 range)
    float3 lutCoord = inputColor.rgb;

    // Sample the 3D LUT texture with trilinear interpolation
    float3 outputColor = lutTexture.sample(lutSampler, lutCoord).rgb;

    return float4(outputColor, alpha);
}

// Film grain shader
fragment float4 grainFragment(VertexOut in [[stage_in]],
                              texture2d<float> inputTexture [[texture(0)]],
                              constant float &intensity [[buffer(0)]],
                              constant float2 &seed [[buffer(1)]]) {

    constexpr sampler textureSampler(mag_filter::linear,
                                      min_filter::linear,
                                      address::clamp_to_edge);

    // Sample the input image
    float4 inputColor = inputTexture.sample(textureSampler, in.texCoord);

    // Generate pseudo-random noise
    float2 coord = in.texCoord + seed;
    float noise = fract(sin(dot(coord, float2(12.9898, 78.233))) * 43758.5453);
    noise = (noise - 0.5) * intensity * 0.2; // Scale intensity

    // Apply grain to RGB channels
    float3 grainedColor = inputColor.rgb + noise;
    grainedColor = clamp(grainedColor, 0.0, 1.0);

    return float4(grainedColor, inputColor.a);
}

// Combined LUT + Grain shader for better performance
fragment float4 lutGrainFragment(VertexOut in [[stage_in]],
                                 texture2d<float> inputTexture [[texture(0)]],
                                 texture3d<float> lutTexture [[texture(1)]],
                                 constant float &lutSize [[buffer(0)]],
                                 constant float &grainIntensity [[buffer(1)]],
                                 constant float2 &seed [[buffer(2)]]) {

    constexpr sampler textureSampler(mag_filter::linear,
                                      min_filter::linear,
                                      address::clamp_to_edge);

    constexpr sampler lutSampler(mag_filter::linear,
                                  min_filter::linear,
                                  address::clamp_to_edge);

    // Sample the input image
    float4 inputColor = inputTexture.sample(textureSampler, in.texCoord);
    float alpha = inputColor.a;

    // Apply 3D LUT
    float3 lutCoord = inputColor.rgb;
    float3 outputColor = lutTexture.sample(lutSampler, lutCoord).rgb;

    // Apply grain if intensity > 0
    if (grainIntensity > 0.0) {
        float2 coord = in.texCoord + seed;
        float noise = fract(sin(dot(coord, float2(12.9898, 78.233))) * 43758.5453);
        noise = (noise - 0.5) * grainIntensity * 0.2;
        outputColor = clamp(outputColor + noise, 0.0, 1.0);
    }

    return float4(outputColor, alpha);
}