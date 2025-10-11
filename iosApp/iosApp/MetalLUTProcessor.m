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
    NSLog(@"[MetalLUTProcessor] Starting Metal setup...");

    self.device = MTLCreateSystemDefaultDevice();
    if (!self.device) {
        NSLog(@"[MetalLUTProcessor] ERROR: Metal device not available - Metal is not supported on this device");
        return;
    }
    NSLog(@"[MetalLUTProcessor] Metal device created successfully: %@", self.device.name);

    self.commandQueue = [self.device newCommandQueue];
    NSLog(@"[MetalLUTProcessor] Command queue created");

    // Load the Metal library
    NSError *error = nil;

    // Try to load from default library first
    NSLog(@"[MetalLUTProcessor] Attempting to load from default library...");
    self.library = [self.device newDefaultLibrary];

    if (self.library) {
        NSLog(@"[MetalLUTProcessor] Successfully loaded Metal library from default.metallib");
    } else {
        NSLog(@"[MetalLUTProcessor] Default library not found, trying to compile from source...");

        // Try to compile from source
        NSString *metalPath = [[NSBundle mainBundle] pathForResource:@"LUTProcessor" ofType:@"metal"];
        if (metalPath) {
            NSLog(@"[MetalLUTProcessor] Found LUTProcessor.metal at: %@", metalPath);
            NSString *source = [NSString stringWithContentsOfFile:metalPath encoding:NSUTF8StringEncoding error:&error];
            if (source) {
                NSLog(@"[MetalLUTProcessor] Compiling Metal shader from source...");
                self.library = [self.device newLibraryWithSource:source options:nil error:&error];
                if (error) {
                    NSLog(@"[MetalLUTProcessor] ERROR: Metal compilation failed: %@", error.localizedDescription);
                } else if (self.library) {
                    NSLog(@"[MetalLUTProcessor] Successfully compiled Metal shader from source");
                }
            } else {
                NSLog(@"[MetalLUTProcessor] ERROR: Failed to read Metal source file: %@", error ? error.localizedDescription : @"Unknown error");
            }
        } else {
            NSLog(@"[MetalLUTProcessor] ERROR: LUTProcessor.metal not found in bundle");
        }
    }

    if (!self.library) {
        NSLog(@"[MetalLUTProcessor] ERROR: Failed to load Metal library from any source - Metal processing will not be available");
        return;
    }

    // List all functions in the library for debugging
    NSArray<NSString *> *functionNames = [self.library functionNames];
    NSLog(@"[MetalLUTProcessor] Available functions in Metal library: %@", functionNames);

    // Create compute pipeline states
    id<MTLFunction> applyLUT3DFunction = [self.library newFunctionWithName:@"applyLUT3D"];
    if (applyLUT3DFunction) {
        NSLog(@"[MetalLUTProcessor] Found 'applyLUT3D' function");
        self.computePipelineState = [self.device newComputePipelineStateWithFunction:applyLUT3DFunction error:&error];
        if (error) {
            NSLog(@"[MetalLUTProcessor] ERROR: Failed to create applyLUT3D pipeline: %@", error.localizedDescription);
        } else {
            NSLog(@"[MetalLUTProcessor] Successfully created applyLUT3D pipeline state");
        }
    } else {
        NSLog(@"[MetalLUTProcessor] ERROR: 'applyLUT3D' function not found in Metal library");
    }

    id<MTLFunction> applyLUT3DDownscaleFunction = [self.library newFunctionWithName:@"applyLUT3DWithDownscale"];
    if (applyLUT3DDownscaleFunction) {
        NSLog(@"[MetalLUTProcessor] Found 'applyLUT3DWithDownscale' function");
        self.computePipelineStateDownscale = [self.device newComputePipelineStateWithFunction:applyLUT3DDownscaleFunction error:&error];
        if (error) {
            NSLog(@"[MetalLUTProcessor] ERROR: Failed to create applyLUT3DWithDownscale pipeline: %@", error.localizedDescription);
        } else {
            NSLog(@"[MetalLUTProcessor] Successfully created applyLUT3DWithDownscale pipeline state");
        }
    } else {
        NSLog(@"[MetalLUTProcessor] ERROR: 'applyLUT3DWithDownscale' function not found in Metal library");
    }

    NSLog(@"[MetalLUTProcessor] Metal setup completed. Ready: %@", (self.computePipelineState != nil) ? @"YES" : @"NO");
}

- (BOOL)applyLUTWithInputPath:(NSString *)inputPath
                    outputPath:(NSString *)outputPath
                      lutPath:(NSString *)lutPath
              createThumbnail:(BOOL)createThumbnail {

    NSLog(@"[MetalLUTProcessor] Starting LUT application - Thumbnail: %@", createThumbnail ? @"YES" : @"NO");
    NSLog(@"[MetalLUTProcessor] Input: %@", inputPath);
    NSLog(@"[MetalLUTProcessor] Output: %@", outputPath);
    NSLog(@"[MetalLUTProcessor] LUT: %@", lutPath);

    // Parse the LUT file
    NSLog(@"[MetalLUTProcessor] Parsing CUBE file...");
    NSDictionary *lutData = [self parseCubeFile:lutPath];
    if (!lutData) {
        NSLog(@"[MetalLUTProcessor] ERROR: Failed to parse CUBE file");
        return NO;
    }
    NSLog(@"[MetalLUTProcessor] Successfully parsed CUBE file with size: %@", lutData[@"size"]);

    // Load input image
    NSLog(@"[MetalLUTProcessor] Loading input image...");
    UIImage *inputImage = [UIImage imageWithContentsOfFile:inputPath];
    if (!inputImage) {
        NSLog(@"[MetalLUTProcessor] ERROR: Failed to load input image");
        return NO;
    }
    NSLog(@"[MetalLUTProcessor] Input image loaded: %.0fx%.0f", inputImage.size.width, inputImage.size.height);

    // Process image using Metal
    NSLog(@"[MetalLUTProcessor] Processing image with Metal...");
    UIImage *outputImage = [self processImage:inputImage
                                       lutData:lutData
                              createThumbnail:createThumbnail];
    if (!outputImage) {
        NSLog(@"[MetalLUTProcessor] ERROR: Failed to process image with Metal");
        return NO;
    }
    NSLog(@"[MetalLUTProcessor] Image processing successful: %.0fx%.0f", outputImage.size.width, outputImage.size.height);

    // Save output image
    NSLog(@"[MetalLUTProcessor] Saving output image...");
    BOOL saved = [self saveImage:outputImage toPath:outputPath];
    if (saved) {
        NSLog(@"[MetalLUTProcessor] Successfully saved output image");
    } else {
        NSLog(@"[MetalLUTProcessor] ERROR: Failed to save output image");
    }

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

    if (createThumbnail) {
        // Set scale factor for downscaling
        float scaleFactorData[2] = {
            inputImage.size.width / outputWidth,
            inputImage.size.height / outputHeight
        };
        [computeEncoder setBytes:scaleFactorData length:sizeof(scaleFactorData) atIndex:0];

        float lutStrength = 1.0f;
        [computeEncoder setBytes:&lutStrength length:sizeof(lutStrength) atIndex:1];
    } else {
        // Just set LUT strength for regular processing
        float lutStrength = 1.0f;
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
    NSLog(@"[MetalLUTProcessor] 📸 Creating texture from UIImage...");
    NSLog(@"[MetalLUTProcessor]   Input image size: %.0fx%.0f", image.size.width, image.size.height);

    CGImageRef cgImage = image.CGImage;
    if (cgImage) {
        NSLog(@"[MetalLUTProcessor]   CGImage info:");
        NSLog(@"[MetalLUTProcessor]     - Size: %zux%zu", CGImageGetWidth(cgImage), CGImageGetHeight(cgImage));
        NSLog(@"[MetalLUTProcessor]     - Bits per pixel: %zu", CGImageGetBitsPerPixel(cgImage));
        NSLog(@"[MetalLUTProcessor]     - Bits per component: %zu", CGImageGetBitsPerComponent(cgImage));
        NSLog(@"[MetalLUTProcessor]     - Bytes per row: %zu", CGImageGetBytesPerRow(cgImage));

        CGBitmapInfo bitmapInfo = CGImageGetBitmapInfo(cgImage);
        CGImageAlphaInfo alphaInfo = bitmapInfo & kCGBitmapAlphaInfoMask;
        NSLog(@"[MetalLUTProcessor]     - Alpha info: %u", alphaInfo);
        NSLog(@"[MetalLUTProcessor]     - Byte order: %s",
              (bitmapInfo & kCGBitmapByteOrderMask) == kCGBitmapByteOrder32Little ? "Little (BGRA)" :
              (bitmapInfo & kCGBitmapByteOrderMask) == kCGBitmapByteOrder32Big ? "Big (RGBA)" : "Unknown");
    }

    // Use MTKTextureLoader which handles all pixel format conversions correctly
    MTKTextureLoader *textureLoader = [[MTKTextureLoader alloc] initWithDevice:self.device];

    NSDictionary *options = @{
        MTKTextureLoaderOptionSRGB: @NO,  // Don't apply sRGB conversion
        MTKTextureLoaderOptionTextureUsage: @(MTLTextureUsageShaderRead),
        MTKTextureLoaderOptionTextureStorageMode: @(MTLStorageModeShared)
    };

    NSError *error = nil;
    id<MTLTexture> texture = [textureLoader newTextureWithCGImage:cgImage
                                                          options:options
                                                            error:&error];

    if (error) {
        NSLog(@"[MetalLUTProcessor] ❌ ERROR: Failed to create texture from image: %@", error.localizedDescription);
        return nil;
    }

    if (texture) {
        NSString *formatName;
        switch (texture.pixelFormat) {
            case MTLPixelFormatBGRA8Unorm: formatName = @"BGRA8Unorm"; break;
            case MTLPixelFormatRGBA8Unorm: formatName = @"RGBA8Unorm"; break;
            case MTLPixelFormatBGRA8Unorm_sRGB: formatName = @"BGRA8Unorm_sRGB"; break;
            case MTLPixelFormatRGBA8Unorm_sRGB: formatName = @"RGBA8Unorm_sRGB"; break;
            default: formatName = [NSString stringWithFormat:@"Unknown (%lu)", (unsigned long)texture.pixelFormat]; break;
        }
        NSLog(@"[MetalLUTProcessor] ✅ Created texture:");
        NSLog(@"[MetalLUTProcessor]   Format: %@ (%lu)", formatName, (unsigned long)texture.pixelFormat);
        NSLog(@"[MetalLUTProcessor]   Size: %lux%lu", (unsigned long)texture.width, (unsigned long)texture.height);

        // Read first few pixels to verify colors
        NSUInteger bytesPerRow = texture.width * 4;
        void *testData = malloc(bytesPerRow * 4); // First 4 rows
        [texture getBytes:testData
              bytesPerRow:bytesPerRow
               fromRegion:MTLRegionMake2D(0, 0, texture.width, 4)
              mipmapLevel:0];

        unsigned char *pixels = (unsigned char *)testData;
        NSLog(@"[MetalLUTProcessor]   Sample pixels (first pixel):");
        NSLog(@"[MetalLUTProcessor]     Byte 0: %d, Byte 1: %d, Byte 2: %d, Byte 3: %d",
              pixels[0], pixels[1], pixels[2], pixels[3]);

        free(testData);
    }

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

    // Fill 3D texture in the order that Metal expects for sampling
    // Metal's 3D texture coordinates are (R, G, B) when sampling
    // The data must be laid out so that when we sample with coords (r, g, b),
    // we get the correct LUT value.
    //
    // Metal 3D textures are laid out with the first dimension (width/R) changing fastest,
    // then second (height/G), then third (depth/B).
    // This matches our LUT data which is indexed as: r + g * size + b * size * size

    NSUInteger index = 0;
    for (NSUInteger b = 0; b < size; b++) {        // Depth (blue)
        for (NSUInteger g = 0; g < size; g++) {    // Height (green)
            for (NSUInteger r = 0; r < size; r++) {    // Width (red) - fastest changing
                // LUT data is stored in R-major order: r + g * size + b * size * size
                NSUInteger lutIndex = r + g * size + b * size * size;
                NSArray *rgb = data[lutIndex];

                rawData[index++] = [rgb[0] floatValue];  // R
                rawData[index++] = [rgb[1] floatValue];  // G
                rawData[index++] = [rgb[2] floatValue];  // B
                rawData[index++] = 1.0f; // Alpha
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
    // Use BGRA8Unorm which is the default format MTKTextureLoader uses
    textureDescriptor.pixelFormat = MTLPixelFormatBGRA8Unorm;
    textureDescriptor.width = width;
    textureDescriptor.height = height;
    textureDescriptor.usage = MTLTextureUsageShaderWrite | MTLTextureUsageShaderRead;
    textureDescriptor.storageMode = MTLStorageModeShared;

    return [self.device newTextureWithDescriptor:textureDescriptor];
}

- (UIImage *)createImageFromTexture:(id<MTLTexture>)texture {
    NSLog(@"[MetalLUTProcessor] 🖼️  Converting texture to UIImage...");

    NSUInteger width = texture.width;
    NSUInteger height = texture.height;
    NSUInteger bytesPerRow = width * 4;

    void *rawData = malloc(height * bytesPerRow);

    [texture getBytes:rawData
          bytesPerRow:bytesPerRow
           fromRegion:MTLRegionMake2D(0, 0, width, height)
          mipmapLevel:0];

    // Log sample pixels from output texture
    unsigned char *pixels = (unsigned char *)rawData;
    NSLog(@"[MetalLUTProcessor]   Output texture sample (first pixel):");
    NSLog(@"[MetalLUTProcessor]     Byte 0: %d, Byte 1: %d, Byte 2: %d, Byte 3: %d",
          pixels[0], pixels[1], pixels[2], pixels[3]);

    CGColorSpaceRef colorSpace = CGColorSpaceCreateDeviceRGB();

    // BGRA8Unorm texture data
    // Use kCGImageAlphaPremultipliedFirst with kCGBitmapByteOrder32Little for BGRA
    CGBitmapInfo bitmapInfo = kCGImageAlphaPremultipliedFirst | kCGBitmapByteOrder32Little;

    NSLog(@"[MetalLUTProcessor]   Creating CGContext with:");
    NSLog(@"[MetalLUTProcessor]     Size: %lux%lu", (unsigned long)width, (unsigned long)height);
    NSLog(@"[MetalLUTProcessor]     Bitmap info: 0x%x (PremultipliedFirst + LittleEndian)", bitmapInfo);

    CGContextRef context = CGBitmapContextCreate(rawData,
                                                 width,
                                                 height,
                                                 8,  // bits per component
                                                 bytesPerRow,
                                                 colorSpace,
                                                 bitmapInfo);

    if (!context) {
        NSLog(@"[MetalLUTProcessor] ❌ ERROR: Failed to create CGContext");
        CGColorSpaceRelease(colorSpace);
        free(rawData);
        return nil;
    }

    CGImageRef cgImage = CGBitmapContextCreateImage(context);
    UIImage *image = [UIImage imageWithCGImage:cgImage];

    NSLog(@"[MetalLUTProcessor] ✅ Successfully created UIImage from texture");

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

        // Parse LUT size
        if ([trimmedLine hasPrefix:@"LUT_3D_SIZE"]) {
            NSArray *components = [trimmedLine componentsSeparatedByString:@" "];
            if (components.count > 1) {
                lutSize = [components.lastObject integerValue];
                if (lutSize <= 0 || lutSize > 256) {
                    NSLog(@"Invalid LUT size: %lu", (unsigned long)lutSize);
                    return nil;
                }
            }
        }
        // Parse RGB values
        else if (lutSize > 0 && ![trimmedLine containsString:@"="]) {
            NSArray *components = [trimmedLine componentsSeparatedByCharactersInSet:[NSCharacterSet whitespaceCharacterSet]];
            NSMutableArray *cleanComponents = [NSMutableArray array];

            for (NSString *comp in components) {
                if (comp.length > 0) {
                    [cleanComponents addObject:comp];
                }
            }

            if (cleanComponents.count >= 3) {
                CGFloat r = [cleanComponents[0] floatValue];
                CGFloat g = [cleanComponents[1] floatValue];
                CGFloat b = [cleanComponents[2] floatValue];

                [lutData addObject:@[@(r), @(g), @(b)]];
            }
        }
    }

    // Verify we have at least the correct amount of data
    // Some CUBE files may have extra entries, so we allow more data than expected
    // but we'll only use the first expectedSize entries
    NSUInteger expectedSize = lutSize * lutSize * lutSize;
    if (lutData.count < expectedSize) {
        NSLog(@"LUT data size insufficient. Expected at least: %lu, Got: %lu", (unsigned long)expectedSize, (unsigned long)lutData.count);
        return nil;
    }

    // If we have more data than expected, trim it to the expected size
    if (lutData.count > expectedSize) {
        NSLog(@"LUT data has extra entries. Expected: %lu, Got: %lu - using first %lu entries",
              (unsigned long)expectedSize, (unsigned long)lutData.count, (unsigned long)expectedSize);
        [lutData removeObjectsInRange:NSMakeRange(expectedSize, lutData.count - expectedSize)];
    }

    return @{
        @"size": @(lutSize),
        @"data": lutData
    };
}

- (BOOL)saveImage:(UIImage *)image toPath:(NSString *)path {
    NSData *imageData;

    if ([path hasSuffix:@".jpg"] || [path hasSuffix:@".jpeg"]) {
        imageData = UIImageJPEGRepresentation(image, 0.9);
    } else {
        imageData = UIImagePNGRepresentation(image);
    }

    return [imageData writeToFile:path atomically:YES];
}

@end