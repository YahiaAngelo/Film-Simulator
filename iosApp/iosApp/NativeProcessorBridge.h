//
//  NativeProcessorBridge.h
//  Film Simulator
//
//  Objective-C header for NativeProcessorBridge Swift class
//  This allows Kotlin Native to call the Swift bridge via cinterop
//

#import <Foundation/Foundation.h>

NS_ASSUME_NONNULL_BEGIN

@interface NativeProcessorBridge : NSObject

+ (instancetype)shared;

- (BOOL)processImageIfNeededWithInputPath:(NSString *)inputPath
                                outputPath:(NSString *)outputPath
                                   lutPath:(NSString *)lutPath
                           createThumbnail:(BOOL)createThumbnail;

- (BOOL)isMetalAvailable;

@end

NS_ASSUME_NONNULL_END
