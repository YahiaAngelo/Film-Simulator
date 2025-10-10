//
//  MetalProcessorStub.m
//  Stub implementation for cinterop compilation
//

#import "MetalProcessor.h"
#import <Foundation/Foundation.h>

@implementation MetalProcessorBridge

static MetalProcessorBridge *_sharedInstance = nil;

+ (instancetype)sharedInstance {
    static dispatch_once_t onceToken;
    dispatch_once(&onceToken, ^{
        _sharedInstance = [[self alloc] init];
    });
    return _sharedInstance;
}

- (instancetype)init {
    self = [super init];
    if (self) {
        NSLog(@"[MetalProcessorBridge] Initialized (cinterop stub)");
    }
    return self;
}

- (BOOL)processImageWithInputPath:(NSString *)inputPath
                        outputPath:(NSString *)outputPath
                          lutPath:(NSString *)lutPath
                  createThumbnail:(BOOL)createThumbnail {

    NSLog(@"[MetalProcessorBridge] Processing via cinterop stub - this should be replaced at runtime");

    // This stub just returns NO
    // The actual implementation will be provided by the iOS app
    return NO;
}

- (BOOL)isMetalAvailable {
    return NO; // Stub implementation
}

@end