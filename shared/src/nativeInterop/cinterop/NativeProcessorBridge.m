//
//  NativeProcessorBridge.m
//  Stub implementation for cinterop
//
//  This provides the symbols needed for linking, but the actual
//  implementation will come from the iOS app at runtime
//

#import <Foundation/Foundation.h>
#import "NativeProcessorBridge.h"

@implementation NativeProcessorBridge

// Stub implementation - will be replaced by Swift version at runtime
static NativeProcessorBridge *_sharedInstance = nil;

+ (instancetype)shared {
    // At runtime, if the Swift version is loaded, it will override this
    // For now, return nil to indicate the real implementation isn't available
    if (_sharedInstance == nil) {
        _sharedInstance = [[NativeProcessorBridge alloc] init];
    }
    return _sharedInstance;
}

- (BOOL)processImageIfNeededWithInputPath:(NSString *)inputPath
                                outputPath:(NSString *)outputPath
                                   lutPath:(NSString *)lutPath
                           createThumbnail:(BOOL)createThumbnail {
    // Stub - will be overridden by Swift implementation
    NSLog(@"[NativeProcessorBridge.m] Stub called - Swift implementation not loaded");
    return NO;
}

- (BOOL)isMetalAvailable {
    // Stub - will be overridden by Swift implementation
    return NO;
}

@end
