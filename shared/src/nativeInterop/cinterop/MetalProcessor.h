//
//  MetalProcessor.h
//  Bridging header for Kotlin Native cinterop
//

#import <Foundation/Foundation.h>

// Forward declaration for cinterop
@interface MetalProcessorBridge : NSObject

+ (instancetype)sharedInstance;
- (BOOL)processImageWithInputPath:(NSString *)inputPath
                        outputPath:(NSString *)outputPath
                          lutPath:(NSString *)lutPath
                  createThumbnail:(BOOL)createThumbnail;
- (BOOL)isMetalAvailable;

@end