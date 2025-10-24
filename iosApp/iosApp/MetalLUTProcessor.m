//
//  MetalLUTProcessor.m
//  Film Simulator
//
//  Objective-C implementation of Metal-based LUT processing
//

#import "MetalLUTProcessor.h"
#import <Metal/Metal.h>
#import <MetalKit/MetalKit.h>
#import <CoreGraphics/CoreGraphics.h>
#import <ImageIO/ImageIO.h>
#import <MobileCoreServices/MobileCoreServices.h>

@interface MetalLUTProcessor ()
@property (nonatomic, strong) id<MTLDevice> device;
@property (nonatomic, strong) id<MTLCommandQueue> commandQueue;
@property (nonatomic, strong) id<MTLComputePipelineState> computePipelineState;
@property (nonatomic, strong) id<MTLComputePipelineState> computePipelineStateDownscale;
@property (nonatomic, strong) id<MTLLibrary> library;
@end

@implementation MetalLUTProcessor

- (instancetype)init {
    self = [super init];
    if (self) {
        [self setupMetal];
    }
    return self;
}

- (void)setupMetal {
    self.device = MTLCreateSystemDefaultDevice();
    if (!self.device) {
        return;
    }

    self.commandQueue = [self.device newCommandQueue];

    // Load the Metal library
    NSError *error = nil;

    // Try to load from default library first
    self.library = [self.device newDefaultLibrary];

    if (!self.library) {
        // Try to compile from source
        NSString *metalPath = [[NSBundle mainBundle] pathForResource:@"LUTProcessor" ofType:@"metal"];
        if (metalPath) {
            NSString *source = [NSString stringWithContentsOfFile:metalPath encoding:NSUTF8StringEncoding error:&error];
            if (source) {
                self.library = [self.device newLibraryWithSource:source options:nil error:&error];
            }
        }
    }

    if (!self.library) {
        return;
    }

    // Create compute pipeline states
    id<MTLFunction> applyLUT3DFunction = [self.library newFunctionWithName:@"applyLUT3D"];
    if (applyLUT3DFunction) {
        self.computePipelineState = [self.device newComputePipelineStateWithFunction:applyLUT3DFunction error:&error];
    }

    id<MTLFunction> applyLUT3DDownscaleFunction = [self.library newFunctionWithName:@"applyLUT3DWithDownscale"];
    if (applyLUT3DDownscaleFunction) {
        self.computePipelineStateDownscale = [self.device newComputePipelineStateWithFunction:applyLUT3DDownscaleFunction error:&error];
    }
}

- (BOOL)applyLUTWithInputPath:(NSString *)inputPath
                    outputPath:(NSString *)outputPath
                      lutPath:(NSString *)lutPath
              createThumbnail:(BOOL)createThumbnail {

    // Parse the LUT file
    NSDictionary *lutData = [self parseCubeFile:lutPath];
    if (!lutData) {
        return NO;
    }

    UIImage *inputImage = [UIImage imageWithContentsOfFile:inputPath];
    if (!inputImage) {
        return NO;
    }

    UIImage *outputImage = [self processImage:inputImage
                                       lutData:lutData
                              createThumbnail:createThumbnail];
    if (!outputImage) {
        return NO;
    }

    BOOL saved = [self saveImage:outputImage toPath:outputPath];

    return saved;
}

- (UIImage *)processImage:(UIImage *)inputImage
                  lutData:(NSDictionary *)lutData
         createThumbnail:(BOOL)createThumbnail {

    if (!self.device || !self.commandQueue) {
        return nil;
    }

    id<MTLComputePipelineState> pipelineState = createThumbnail ? self.computePipelineStateDownscale : self.computePipelineState;
    if (!pipelineState) {
        return nil;
    }

    // Create textures
    id<MTLTexture> inputTexture = [self createTextureFromImage:inputImage];
    if (!inputTexture) {
        return nil;
    }

    id<MTLTexture> lutTexture = [self create3DTextureFromLUT:lutData];
    if (!lutTexture) {
        return nil;
    }

    // Calculate output size
    NSInteger outputWidth, outputHeight;
    if (createThumbnail) {
        CGFloat aspectRatio = inputImage.size.height / inputImage.size.width;
        outputWidth = 320;
        outputHeight = 320 * aspectRatio;
    } else {
        outputWidth = inputImage.size.width;
        outputHeight = inputImage.size.height;
    }

    id<MTLTexture> outputTexture = [self createOutputTextureWithWidth:outputWidth height:outputHeight];
    if (!outputTexture) {
        return nil;
    }

    // Create command buffer and encoder
    id<MTLCommandBuffer> commandBuffer = [self.commandQueue commandBuffer];
    id<MTLComputeCommandEncoder> computeEncoder = [commandBuffer computeCommandEncoder];

    // Set up compute command
    [computeEncoder setComputePipelineState:pipelineState];
    [computeEncoder setTexture:inputTexture atIndex:0];
    [computeEncoder setTexture:outputTexture atIndex:1];
    [computeEncoder setTexture:lutTexture atIndex:2];

    float lutStrength = 1.0f;

    if (createThumbnail) {
        // Set scale factor for downscaling
        float scaleFactorData[2] = {
            inputImage.size.width / outputWidth,
            inputImage.size.height / outputHeight
        };
        [computeEncoder setBytes:scaleFactorData length:sizeof(scaleFactorData) atIndex:0];
        [computeEncoder setBytes:&lutStrength length:sizeof(lutStrength) atIndex:1];
    } else {
        // Set LUT strength for regular processing
        [computeEncoder setBytes:&lutStrength length:sizeof(lutStrength) atIndex:0];
    }

    // Calculate thread groups
    MTLSize threadgroupSize = MTLSizeMake(16, 16, 1);
    MTLSize threadgroupCount = MTLSizeMake((outputWidth + 15) / 16,
                                           (outputHeight + 15) / 16,
                                           1);

    [computeEncoder dispatchThreadgroups:threadgroupCount threadsPerThreadgroup:threadgroupSize];
    [computeEncoder endEncoding];

    // Commit and wait
    [commandBuffer commit];
    [commandBuffer waitUntilCompleted];

    // Convert output texture to UIImage
    return [self createImageFromTexture:outputTexture];
}

- (id<MTLTexture>)createTextureFromImage:(UIImage *)image {
    CGImageRef cgImage = image.CGImage;
    if (!cgImage) {
        return nil;
    }

    NSUInteger width = CGImageGetWidth(cgImage);
    NSUInteger height = CGImageGetHeight(cgImage);

    MTLTextureDescriptor *textureDescriptor = [[MTLTextureDescriptor alloc] init];
    textureDescriptor.pixelFormat = MTLPixelFormatRGBA8Unorm;
    textureDescriptor.width = width;
    textureDescriptor.height = height;
    textureDescriptor.usage = MTLTextureUsageShaderRead;
    textureDescriptor.storageMode = MTLStorageModeShared;

    id<MTLTexture> texture = [self.device newTextureWithDescriptor:textureDescriptor];

    NSUInteger bytesPerRow = width * 4;
    CGColorSpaceRef colorSpace = CGColorSpaceCreateWithName(kCGColorSpaceSRGB);
    void *rawData = malloc(height * bytesPerRow);

    CGBitmapInfo inputByteOrder = CGImageGetBitmapInfo(cgImage) & kCGBitmapByteOrderMask;
    CGBitmapInfo bitmapInfo;
    if (inputByteOrder == 0 || inputByteOrder == kCGBitmapByteOrder32Little) {
        bitmapInfo = kCGImageAlphaNoneSkipFirst | kCGBitmapByteOrder32Little;
    } else {
        bitmapInfo = kCGImageAlphaNoneSkipLast | kCGBitmapByteOrder32Big;
    }

    CGContextRef context = CGBitmapContextCreate(rawData,
                                                 width,
                                                 height,
                                                 8,
                                                 bytesPerRow,
                                                 colorSpace,
                                                 bitmapInfo);

    if (!context) {
        CGColorSpaceRelease(colorSpace);
        free(rawData);
        return nil;
    }

    CGContextDrawImage(context, CGRectMake(0, 0, width, height), cgImage);

    // If we loaded as BGRA, convert to RGBA for Metal
    if (inputByteOrder == 0 || inputByteOrder == kCGBitmapByteOrder32Little) {
        unsigned char *pixels = (unsigned char *)rawData;
        for (NSUInteger i = 0; i < width * height; i++) {
            NSUInteger offset = i * 4;
            unsigned char b = pixels[offset + 0];
            unsigned char r = pixels[offset + 2];
            pixels[offset + 0] = r;
            pixels[offset + 2] = b;
        }
    }

    [texture replaceRegion:MTLRegionMake2D(0, 0, width, height)
               mipmapLevel:0
                 withBytes:rawData
               bytesPerRow:bytesPerRow];

    CGContextRelease(context);
    CGColorSpaceRelease(colorSpace);
    free(rawData);

    return texture;
}

- (id<MTLTexture>)create3DTextureFromLUT:(NSDictionary *)lutData {
    NSNumber *sizeNum = lutData[@"size"];
    NSArray *data = lutData[@"data"];

    if (!sizeNum || !data) {
        return nil;
    }

    NSUInteger size = [sizeNum unsignedIntegerValue];

    MTLTextureDescriptor *textureDescriptor = [[MTLTextureDescriptor alloc] init];
    textureDescriptor.textureType = MTLTextureType3D;
    textureDescriptor.pixelFormat = MTLPixelFormatRGBA32Float;
    textureDescriptor.width = size;
    textureDescriptor.height = size;
    textureDescriptor.depth = size;
    textureDescriptor.usage = MTLTextureUsageShaderRead;

    id<MTLTexture> texture = [self.device newTextureWithDescriptor:textureDescriptor];

    NSUInteger dataSize = size * size * size * 4 * sizeof(float);
    float *rawData = malloc(dataSize);

    NSUInteger index = 0;
    for (NSUInteger b = 0; b < size; b++) {
        for (NSUInteger g = 0; g < size; g++) {
            for (NSUInteger r = 0; r < size; r++) {
                NSUInteger lutIndex = r + g * size + b * size * size;
                NSArray *rgb = data[lutIndex];

                rawData[index++] = [rgb[0] floatValue];
                rawData[index++] = [rgb[1] floatValue];
                rawData[index++] = [rgb[2] floatValue];
                rawData[index++] = 1.0f;
            }
        }
    }

    [texture replaceRegion:MTLRegionMake3D(0, 0, 0, size, size, size)
               mipmapLevel:0
                     slice:0
                 withBytes:rawData
               bytesPerRow:size * 4 * sizeof(float)
             bytesPerImage:size * size * 4 * sizeof(float)];

    free(rawData);

    return texture;
}

- (id<MTLTexture>)createOutputTextureWithWidth:(NSUInteger)width height:(NSUInteger)height {
    MTLTextureDescriptor *textureDescriptor = [[MTLTextureDescriptor alloc] init];
    // Use RGBA8Unorm to match input format
    textureDescriptor.pixelFormat = MTLPixelFormatRGBA8Unorm;
    textureDescriptor.width = width;
    textureDescriptor.height = height;
    textureDescriptor.usage = MTLTextureUsageShaderWrite | MTLTextureUsageShaderRead;
    textureDescriptor.storageMode = MTLStorageModeShared;

    return [self.device newTextureWithDescriptor:textureDescriptor];
}

- (UIImage *)createImageFromTexture:(id<MTLTexture>)texture {
    NSUInteger width = texture.width;
    NSUInteger height = texture.height;
    NSUInteger bytesPerRow = width * 4;

    void *rawData = malloc(height * bytesPerRow);

    [texture getBytes:rawData
          bytesPerRow:bytesPerRow
           fromRegion:MTLRegionMake2D(0, 0, width, height)
          mipmapLevel:0];

    // Use sRGB color space
    CGColorSpaceRef colorSpace = CGColorSpaceCreateWithName(kCGColorSpaceSRGB);

    CGBitmapInfo bitmapInfo = kCGImageAlphaNoneSkipLast | kCGBitmapByteOrderDefault;

    CGContextRef context = CGBitmapContextCreate(rawData,
                                                 width,
                                                 height,
                                                 8,
                                                 bytesPerRow,
                                                 colorSpace,
                                                 bitmapInfo);

    if (!context) {
        CGColorSpaceRelease(colorSpace);
        free(rawData);
        return nil;
    }

    CGImageRef cgImage = CGBitmapContextCreateImage(context);
    UIImage *image = [UIImage imageWithCGImage:cgImage];

    CGImageRelease(cgImage);
    CGContextRelease(context);
    CGColorSpaceRelease(colorSpace);
    free(rawData);

    return image;
}

- (NSDictionary *)parseCubeFile:(NSString *)filePath {
    NSError *error = nil;
    NSString *fileContent = [NSString stringWithContentsOfFile:filePath encoding:NSUTF8StringEncoding error:&error];

    if (!fileContent) {
        return nil;
    }

    NSArray *lines = [fileContent componentsSeparatedByString:@"\n"];
    NSMutableArray *lutData = [NSMutableArray array];
    NSUInteger lutSize = 0;

    for (NSString *line in lines) {
        NSString *trimmedLine = [line stringByTrimmingCharactersInSet:[NSCharacterSet whitespaceAndNewlineCharacterSet]];

        // Skip empty lines and comments
        if (trimmedLine.length == 0 || [trimmedLine hasPrefix:@"#"]) {
            continue;
        }

        // Skip DOMAIN and TITLE lines FIRST before any other parsing
        if ([trimmedLine hasPrefix:@"DOMAIN_MIN"] ||
            [trimmedLine hasPrefix:@"DOMAIN_MAX"] ||
            [trimmedLine hasPrefix:@"TITLE"]) {
            continue;
        }

        // Parse LUT size
        if ([trimmedLine hasPrefix:@"LUT_3D_SIZE"]) {
            NSArray *components = [trimmedLine componentsSeparatedByString:@" "];
            if (components.count > 1) {
                lutSize = [components.lastObject integerValue];
                if (lutSize <= 0 || lutSize > 256) {
                    return nil;
                }
            }
        }
        // Parse RGB values - only if we have found the LUT size and the line contains decimal points (likely float values)
        else if (lutSize > 0 && [trimmedLine rangeOfString:@"."].location != NSNotFound) {
            NSArray *components = [trimmedLine componentsSeparatedByCharactersInSet:[NSCharacterSet whitespaceCharacterSet]];
            NSMutableArray *cleanComponents = [NSMutableArray array];

            for (NSString *comp in components) {
                if (comp.length > 0) {
                    [cleanComponents addObject:comp];
                }
            }

            if (cleanComponents.count >= 3) {
                // Try to parse the values
                NSScanner *scanner = [NSScanner scannerWithString:cleanComponents[0]];
                float r, g, b;

                BOOL validR = [scanner scanFloat:&r];
                scanner = [NSScanner scannerWithString:cleanComponents[1]];
                BOOL validG = [scanner scanFloat:&g];
                scanner = [NSScanner scannerWithString:cleanComponents[2]];
                BOOL validB = [scanner scanFloat:&b];

                if (validR && validG && validB) {
                    [lutData addObject:@[@(r), @(g), @(b)]];
                }
            }
        }
    }

    // Verify we have at least the correct amount of data
    // Some CUBE files may have extra entries, so we allow more data than expected
    // but we'll only use the first expectedSize entries
    NSUInteger expectedSize = lutSize * lutSize * lutSize;
    if (lutData.count < expectedSize) {
        return nil;
    }

    // If we have more data than expected, trim it to the expected size
    if (lutData.count > expectedSize) {
        [lutData removeObjectsInRange:NSMakeRange(expectedSize, lutData.count - expectedSize)];
    }

    return @{
        @"size": @(lutSize),
        @"data": lutData
    };
}

- (BOOL)saveImage:(UIImage *)image toPath:(NSString *)path {
    // Use CGImageDestination for better format control
    NSURL *fileURL = [NSURL fileURLWithPath:path];

    // Determine image type from extension
    CFStringRef imageType;
    if ([path hasSuffix:@".jpg"] || [path hasSuffix:@".jpeg"]) {
        imageType = kUTTypeJPEG;
    } else if ([path hasSuffix:@".png"]) {
        imageType = kUTTypePNG;
    } else {
        imageType = kUTTypeJPEG; // Default to JPEG
    }

    // Create image destination
    CGImageDestinationRef destination = CGImageDestinationCreateWithURL(
        (__bridge CFURLRef)fileURL,
        imageType,
        1,
        NULL
    );

    if (!destination) {
        return NO;
    }

    // Get the CGImage
    CGImageRef cgImage = image.CGImage;

    // Create properties dictionary with explicit color profile
    NSMutableDictionary *properties = [NSMutableDictionary dictionary];

    if (imageType == kUTTypeJPEG) {
        // JPEG quality
        properties[(NSString *)kCGImageDestinationLossyCompressionQuality] = @0.95;
    }

    // Explicitly set sRGB color profile
    CGColorSpaceRef srgbColorSpace = CGColorSpaceCreateWithName(kCGColorSpaceSRGB);
    properties[(NSString *)kCGImagePropertyColorModel] = (NSString *)kCGImagePropertyColorModelRGB;

    // Add the image with properties
    CGImageDestinationAddImage(destination, cgImage, (__bridge CFDictionaryRef)properties);

    // Finalize the image file
    BOOL success = CGImageDestinationFinalize(destination);

    CFRelease(destination);
    CGColorSpaceRelease(srgbColorSpace);

    return success;
}

#pragma mark - Image Adjustments

- (UIImage *)downscaleImageForPreview:(UIImage *)image maxDimension:(CGFloat)maxDimension {
    CGFloat width = image.size.width;
    CGFloat height = image.size.height;

    // If image is already smaller than max dimension, return as-is
    if (width <= maxDimension && height <= maxDimension) {
        return image;
    }

    // Calculate scale factor
    CGFloat scale = maxDimension / MAX(width, height);
    CGSize newSize = CGSizeMake(width * scale, height * scale);

    // Create scaled image
    UIGraphicsBeginImageContextWithOptions(newSize, NO, 1.0);
    [image drawInRect:CGRectMake(0, 0, newSize.width, newSize.height)];
    UIImage *scaledImage = UIGraphicsGetImageFromCurrentImageContext();
    UIGraphicsEndImageContext();

    return scaledImage;
}

- (BOOL)applyAdjustmentsWithInputPath:(NSString *)inputPath
                            outputPath:(NSString *)outputPath
                              contrast:(float)contrast
                            brightness:(float)brightness
                            saturation:(float)saturation
                           temperature:(float)temperature
                              exposure:(float)exposure
                                 grain:(float)grain
                   chromaticAberration:(float)chromaticAberration
                             forExport:(BOOL)forExport {

    UIImage *inputImage = [UIImage imageWithContentsOfFile:inputPath];
    if (!inputImage) {
        return NO;
    }

    // Downscale for preview only (not for export)
    UIImage *processImage = forExport ? inputImage : [self downscaleImageForPreview:inputImage maxDimension:1920.0];

    UIImage *outputImage = [self applyAdjustmentsToImage:processImage
                                                contrast:contrast
                                              brightness:brightness
                                              saturation:saturation
                                             temperature:temperature
                                                exposure:exposure
                                                   grain:grain
                                     chromaticAberration:chromaticAberration];

    if (!outputImage) {
        return NO;
    }

    return [self saveImage:outputImage toPath:outputPath];
}

- (BOOL)applyLUTAndAdjustmentsWithInputPath:(NSString *)inputPath
                                  outputPath:(NSString *)outputPath
                                     lutPath:(NSString *)lutPath
                                    contrast:(float)contrast
                                  brightness:(float)brightness
                                  saturation:(float)saturation
                                 temperature:(float)temperature
                                    exposure:(float)exposure
                                       grain:(float)grain
                         chromaticAberration:(float)chromaticAberration
                             createThumbnail:(BOOL)createThumbnail {

    // Parse the LUT file
    NSDictionary *lutData = [self parseCubeFile:lutPath];
    if (!lutData) {
        return NO;
    }

    UIImage *inputImage = [UIImage imageWithContentsOfFile:inputPath];
    if (!inputImage) {
        return NO;
    }

    // First apply LUT using Metal
    UIImage *lutImage = [self processImage:inputImage
                                   lutData:lutData
                          createThumbnail:createThumbnail];
    if (!lutImage) {
        return NO;
    }

    // Then apply adjustments using Core Graphics
    UIImage *outputImage = [self applyAdjustmentsToImage:lutImage
                                                contrast:contrast
                                              brightness:brightness
                                              saturation:saturation
                                             temperature:temperature
                                                exposure:exposure
                                                   grain:grain
                                     chromaticAberration:chromaticAberration];

    if (!outputImage) {
        return NO;
    }

    return [self saveImage:outputImage toPath:outputPath];
}

- (UIImage *)applyAdjustmentsToImage:(UIImage *)image
                            contrast:(float)contrast
                          brightness:(float)brightness
                          saturation:(float)saturation
                         temperature:(float)temperature
                            exposure:(float)exposure
                               grain:(float)grain
                 chromaticAberration:(float)chromaticAberration {

    CGImageRef cgImage = image.CGImage;
    if (!cgImage) {
        return nil;
    }

    NSUInteger width = CGImageGetWidth(cgImage);
    NSUInteger height = CGImageGetHeight(cgImage);
    NSUInteger bytesPerRow = width * 4;

    CGColorSpaceRef colorSpace = CGColorSpaceCreateWithName(kCGColorSpaceSRGB);

    // Allocate pixel buffer
    unsigned char *pixels = malloc(height * bytesPerRow);

    CGContextRef context = CGBitmapContextCreate(pixels,
                                                 width,
                                                 height,
                                                 8,
                                                 bytesPerRow,
                                                 colorSpace,
                                                 kCGImageAlphaNoneSkipLast | kCGBitmapByteOrderDefault);

    if (!context) {
        CGColorSpaceRelease(colorSpace);
        free(pixels);
        return nil;
    }

    CGContextDrawImage(context, CGRectMake(0, 0, width, height), cgImage);

    // Process pixels
    [self processPixels:pixels
                  width:width
                 height:height
               contrast:contrast
             brightness:brightness
             saturation:saturation
            temperature:temperature
               exposure:exposure
                  grain:grain
    chromaticAberration:chromaticAberration];

    // Create output image
    CGImageRef outputCGImage = CGBitmapContextCreateImage(context);
    UIImage *outputImage = [UIImage imageWithCGImage:outputCGImage];

    CGImageRelease(outputCGImage);
    CGContextRelease(context);
    CGColorSpaceRelease(colorSpace);
    free(pixels);

    return outputImage;
}

- (void)processPixels:(unsigned char *)pixels
                width:(NSUInteger)width
               height:(NSUInteger)height
             contrast:(float)contrast
           brightness:(float)brightness
           saturation:(float)saturation
          temperature:(float)temperature
             exposure:(float)exposure
                grain:(float)grain
  chromaticAberration:(float)chromaticAberration {

    // Use fixed seed for consistent grain (matches Android implementation)
    uint32_t seed = 12345;

    // First pass: Apply all adjustments except chromatic aberration
    for (NSUInteger y = 0; y < height; y++) {
        for (NSUInteger x = 0; x < width; x++) {
            NSUInteger offset = (y * width + x) * 4;

            // Convert to float [0, 1]
            float r = pixels[offset + 0] / 255.0f;
            float g = pixels[offset + 1] / 255.0f;
            float b = pixels[offset + 2] / 255.0f;

            // Apply adjustments in order
            [self applyExposure:&r g:&g b:&b value:exposure];
            [self applyContrast:&r g:&g b:&b value:contrast];
            [self applyBrightness:&r g:&g b:&b value:brightness];
            [self applySaturation:&r g:&g b:&b value:saturation];
            [self applyTemperature:&r g:&g b:&b value:temperature];
            [self applyGrain:&r g:&g b:&b value:grain x:x y:y width:width height:height seed:seed];

            // Clamp and convert back to uint8
            pixels[offset + 0] = (unsigned char)fminf(fmaxf(r * 255.0f, 0.0f), 255.0f);
            pixels[offset + 1] = (unsigned char)fminf(fmaxf(g * 255.0f, 0.0f), 255.0f);
            pixels[offset + 2] = (unsigned char)fminf(fmaxf(b * 255.0f, 0.0f), 255.0f);
        }
    }

    // Second pass: Apply chromatic aberration if needed
    // (Requires sampling from the adjusted image)
    if (chromaticAberration > 0.0f) {
        // Create a copy of the buffer for sampling
        unsigned char *tempBuffer = malloc(height * width * 4);
        memcpy(tempBuffer, pixels, height * width * 4);

        for (NSUInteger y = 0; y < height; y++) {
            for (NSUInteger x = 0; x < width; x++) {
                NSUInteger offset = (y * width + x) * 4;

                float r = pixels[offset + 0] / 255.0f;
                float g = pixels[offset + 1] / 255.0f;
                float b = pixels[offset + 2] / 255.0f;

                [self applyChromaticAberration:tempBuffer width:width height:height x:x y:y value:chromaticAberration r:&r g:&g b:&b];

                pixels[offset + 0] = (unsigned char)fminf(fmaxf(r * 255.0f, 0.0f), 255.0f);
                pixels[offset + 1] = (unsigned char)fminf(fmaxf(g * 255.0f, 0.0f), 255.0f);
                pixels[offset + 2] = (unsigned char)fminf(fmaxf(b * 255.0f, 0.0f), 255.0f);
            }
        }

        free(tempBuffer);
    }
}

- (void)applyExposure:(float *)r g:(float *)g b:(float *)b value:(float)exposure {
    if (exposure == 0.0f) return;
    float multiplier = powf(2.0f, exposure);
    *r *= multiplier;
    *g *= multiplier;
    *b *= multiplier;
}

- (void)applyContrast:(float *)r g:(float *)g b:(float *)b value:(float)contrast {
    if (contrast == 0.0f) return;
    float multiplier = 1.0f + contrast;
    *r = (*r - 0.5f) * multiplier + 0.5f;
    *g = (*g - 0.5f) * multiplier + 0.5f;
    *b = (*b - 0.5f) * multiplier + 0.5f;
}

- (void)applyBrightness:(float *)r g:(float *)g b:(float *)b value:(float)brightness {
    if (brightness == 0.0f) return;
    *r += brightness;
    *g += brightness;
    *b += brightness;
}

- (void)applySaturation:(float *)r g:(float *)g b:(float *)b value:(float)saturation {
    if (saturation == 0.0f) return;

    // Calculate luminance
    float luminance = 0.299f * (*r) + 0.587f * (*g) + 0.114f * (*b);

    // Interpolate between grayscale and original color
    float multiplier = 1.0f + saturation;
    *r = luminance + ((*r) - luminance) * multiplier;
    *g = luminance + ((*g) - luminance) * multiplier;
    *b = luminance + ((*b) - luminance) * multiplier;
}

- (void)applyTemperature:(float *)r g:(float *)g b:(float *)b value:(float)temperature {
    if (temperature == 0.0f) return;

    if (temperature > 0.0f) {
        // Warm (orange tint)
        *r += temperature * 0.3f;
        *g += temperature * 0.1f;
    } else {
        // Cool (blue tint)
        *b += -temperature * 0.3f;
        *g += -temperature * 0.1f;
    }
}

- (void)applyGrain:(float *)r g:(float *)g b:(float *)b value:(float)grain
                 x:(NSUInteger)x y:(NSUInteger)y width:(NSUInteger)width height:(NSUInteger)height seed:(uint32_t)seed {
    if (grain == 0.0f) return;

    // Proper pseudo-random noise generator
    // Based on: fract(sin(dot(co.xy, vec2(12.9898, 78.233))) * 43758.5453)
    float fx = (float)x + (float)(seed % 1000) * 0.001f;
    float fy = (float)y + (float)(seed / 1000) * 0.001f;

    // Dot product with magic numbers
    float dot = fx * 12.9898f + fy * 78.233f;

    // Sin and scale
    float sinVal = sinf(dot) * 43758.5453f;

    // Fract (get fractional part)
    float noise = sinVal - floorf(sinVal);  // Range [0, 1]

    // Convert to [-0.5, 0.5] range and scale by grain amount
    float diff = (noise - 0.5f) * grain;

    *r += diff;
    *g += diff;
    *b += diff;
}

- (void)applyChromaticAberration:(unsigned char *)pixels width:(NSUInteger)width height:(NSUInteger)height
                               x:(NSUInteger)x y:(NSUInteger)y value:(float)aberration
                               r:(float *)r g:(float *)g b:(float *)b {
    if (aberration == 0.0f) return;

    // Calculate normalized UV coordinates [0, 1]
    float u = ((float)x + 0.5f) / (float)width;
    float v = ((float)y + 0.5f) / (float)height;

    // Calculate distance from center
    float dx = u - 0.5f;
    float dy = v - 0.5f;
    float d = sqrtf(dx * dx + dy * dy);

    // Scale strength - use much larger multiplier for visible effect
    // aberration is in [0, 1] range, multiply by 100 for visible aberration
    float offset = d * aberration * 100.0f;

    // Red channel - offset outward from center
    float rU = u + dx * offset / (float)width;
    float rV = v + dy * offset / (float)height;
    NSInteger rX = (NSInteger)fminf(fmaxf(rU * width, 0.0f), (float)(width - 1));
    NSInteger rY = (NSInteger)fminf(fmaxf(rV * height, 0.0f), (float)(height - 1));
    NSUInteger rIndex = (rY * width + rX) * 4;
    *r = pixels[rIndex] / 255.0f;

    // Green channel stays unchanged (no offset)

    // Blue channel - offset inward toward center
    float bU = u - dx * offset / (float)width;
    float bV = v - dy * offset / (float)height;
    NSInteger bX = (NSInteger)fminf(fmaxf(bU * width, 0.0f), (float)(width - 1));
    NSInteger bY = (NSInteger)fminf(fmaxf(bV * height, 0.0f), (float)(height - 1));
    NSUInteger bIndex = (bY * width + bX) * 4;
    *b = pixels[bIndex + 2] / 255.0f;
}

@end