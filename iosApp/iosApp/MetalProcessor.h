//
//  MetalProcessor.h
//  Bridging header for Kotlin Native cinterop
//

#import <Foundation/Foundation.h>

NS_ASSUME_NONNULL_BEGIN

// Simplified interface for Kotlin interop
@interface MetalProcessorBridge : NSObject

+ (instancetype)sharedInstance;
- (BOOL)processImageWithInputPath:(NSString *)inputPath
                        outputPath:(NSString *)outputPath
                          lutPath:(NSString *)lutPath
                  createThumbnail:(BOOL)createThumbnail;
- (BOOL)isMetalAvailable;

@end

NS_ASSUME_NONNULL_END