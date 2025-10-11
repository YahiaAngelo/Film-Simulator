//
//  NativeProcessorBridge.m
//  Stub implementation for cinterop
//
//  This provides the symbols needed for linking and dynamically
//  calls the iOS app's Swift implementation at runtime
//

#import <Foundation/Foundation.h>
#import <objc/runtime.h>
#import "NativeProcessorBridge.h"

@implementation NativeProcessorBridge

+ (instancetype)shared {
    // Try to find the iOS app's Swift implementation at runtime
    Class swiftClass = NSClassFromString(@"iosApp.NativeProcessorBridge");
    if (swiftClass != nil) {
        NSLog(@"[NativeProcessorBridge.m] ✓ Found Swift implementation via runtime");
        SEL sharedSelector = NSSelectorFromString(@"shared");
        if ([swiftClass respondsToSelector:sharedSelector]) {
            #pragma clang diagnostic push
            #pragma clang diagnostic ignored "-Warc-performSelector-leaks"
            id swiftInstance = [swiftClass performSelector:sharedSelector];
            #pragma clang diagnostic pop
            if (swiftInstance != nil) {
                NSLog(@"[NativeProcessorBridge.m] ✓ Got Swift instance, returning it");
                return swiftInstance;
            }
        }
    }

    // Fallback: return stub instance
    NSLog(@"[NativeProcessorBridge.m] Swift implementation not found, using stub");
    static NativeProcessorBridge *_stubInstance = nil;
    static dispatch_once_t onceToken;
    dispatch_once(&onceToken, ^{
        _stubInstance = [[NativeProcessorBridge alloc] init];
    });
    return _stubInstance;
}

- (BOOL)processImageIfNeededWithInputPath:(NSString *)inputPath
                                outputPath:(NSString *)outputPath
                                   lutPath:(NSString *)lutPath
                           createThumbnail:(BOOL)createThumbnail {
    // Try to find and call the Swift implementation dynamically
    Class swiftClass = NSClassFromString(@"iosApp.NativeProcessorBridge");
    if (swiftClass != nil) {
        SEL sharedSelector = NSSelectorFromString(@"shared");
        if ([swiftClass respondsToSelector:sharedSelector]) {
            #pragma clang diagnostic push
            #pragma clang diagnostic ignored "-Warc-performSelector-leaks"
            id swiftInstance = [swiftClass performSelector:sharedSelector];
            #pragma clang diagnostic pop

            if (swiftInstance != nil) {
                SEL processSelector = NSSelectorFromString(@"processImageIfNeededWithInputPath:outputPath:lutPath:createThumbnail:");
                if ([swiftInstance respondsToSelector:processSelector]) {
                    NSLog(@"[NativeProcessorBridge.m] ✓ Calling Swift implementation dynamically");

                    // Use NSInvocation to call the method with multiple parameters
                    NSMethodSignature *signature = [swiftInstance methodSignatureForSelector:processSelector];
                    NSInvocation *invocation = [NSInvocation invocationWithMethodSignature:signature];
                    [invocation setTarget:swiftInstance];
                    [invocation setSelector:processSelector];
                    [invocation setArgument:&inputPath atIndex:2];
                    [invocation setArgument:&outputPath atIndex:3];
                    [invocation setArgument:&lutPath atIndex:4];
                    [invocation setArgument:&createThumbnail atIndex:5];
                    [invocation invoke];

                    BOOL result;
                    [invocation getReturnValue:&result];
                    return result;
                }
            }
        }
    }

    NSLog(@"[NativeProcessorBridge.m] Swift implementation not available - returning NO");
    return NO;
}

- (BOOL)isMetalAvailable {
    // Try to find and call the Swift implementation dynamically
    Class swiftClass = NSClassFromString(@"iosApp.NativeProcessorBridge");
    if (swiftClass != nil) {
        SEL sharedSelector = NSSelectorFromString(@"shared");
        if ([swiftClass respondsToSelector:sharedSelector]) {
            #pragma clang diagnostic push
            #pragma clang diagnostic ignored "-Warc-performSelector-leaks"
            id swiftInstance = [swiftClass performSelector:sharedSelector];
            #pragma clang diagnostic pop

            if (swiftInstance != nil) {
                SEL isMetalAvailableSelector = NSSelectorFromString(@"isMetalAvailable");
                if ([swiftInstance respondsToSelector:isMetalAvailableSelector]) {
                    #pragma clang diagnostic push
                    #pragma clang diagnostic ignored "-Warc-performSelector-leaks"
                    NSNumber *result = [swiftInstance performSelector:isMetalAvailableSelector];
                    #pragma clang diagnostic pop
                    return [result boolValue];
                }
            }
        }
    }

    return NO;
}

@end
