//
//  MetalProcessorBridge.m
//  Bridge implementation for Kotlin Native cinterop
//

#import <Foundation/Foundation.h>

@interface MetalProcessorBridge : NSObject
+ (instancetype)sharedInstance;
- (BOOL)processImageWithInputPath:(NSString *)inputPath
                        outputPath:(NSString *)outputPath
                          lutPath:(NSString *)lutPath
                  createThumbnail:(BOOL)createThumbnail;
- (BOOL)isMetalAvailable;
@end
#import "MetalLUTProcessor.h"
#import <Metal/Metal.h>

@interface MetalProcessorBridge ()
@property (nonatomic, strong) MetalLUTProcessor *processor;
@end

@implementation MetalProcessorBridge

+ (instancetype)sharedInstance {
    static MetalProcessorBridge *sharedInstance = nil;
    static dispatch_once_t onceToken;
    dispatch_once(&onceToken, ^{
        sharedInstance = [[self alloc] init];
    });
    return sharedInstance;
}

- (instancetype)init {
    self = [super init];
    if (self) {
        NSLog(@"[MetalProcessorBridge] Initializing Metal processor bridge");
        self.processor = [[MetalLUTProcessor alloc] init];
        if (self.processor) {
            NSLog(@"[MetalProcessorBridge] MetalLUTProcessor created successfully");
        } else {
            NSLog(@"[MetalProcessorBridge] Failed to create MetalLUTProcessor");
        }
    }
    return self;
}

- (BOOL)processImageWithInputPath:(NSString *)inputPath
                        outputPath:(NSString *)outputPath
                          lutPath:(NSString *)lutPath
                  createThumbnail:(BOOL)createThumbnail {

    NSLog(@"[MetalProcessorBridge] Processing image with Metal");
    NSLog(@"[MetalProcessorBridge] Input: %@", inputPath);
    NSLog(@"[MetalProcessorBridge] Output: %@", outputPath);
    NSLog(@"[MetalProcessorBridge] LUT: %@", lutPath);
    NSLog(@"[MetalProcessorBridge] Thumbnail: %@", createThumbnail ? @"YES" : @"NO");

    if (!self.processor) {
        NSLog(@"[MetalProcessorBridge] ERROR: MetalLUTProcessor is nil");
        return NO;
    }

    BOOL result = [self.processor applyLUTWithInputPath:inputPath
                                            outputPath:outputPath
                                              lutPath:lutPath
                                      createThumbnail:createThumbnail];

    NSLog(@"[MetalProcessorBridge] Processing result: %@", result ? @"SUCCESS" : @"FAILED");

    return result;
}

- (BOOL)isMetalAvailable {
    id<MTLDevice> device = MTLCreateSystemDefaultDevice();
    BOOL available = (device != nil);

    if (available) {
        NSLog(@"[MetalProcessorBridge] Metal is available on device: %@", device.name);
    } else {
        NSLog(@"[MetalProcessorBridge] Metal is not available on this device");
    }

    return available;
}

@end