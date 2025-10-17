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

@end