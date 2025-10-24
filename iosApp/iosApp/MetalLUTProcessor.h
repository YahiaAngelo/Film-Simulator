//
//  MetalLUTProcessor.h
//  Film Simulator
//
//  Objective-C bridge for Metal-based LUT processing
//

#import <Foundation/Foundation.h>
#import <UIKit/UIKit.h>

NS_ASSUME_NONNULL_BEGIN

@interface MetalLUTProcessor : NSObject

- (instancetype)init;
- (BOOL)applyLUTWithInputPath:(NSString *)inputPath
                    outputPath:(NSString *)outputPath
                      lutPath:(NSString *)lutPath
              createThumbnail:(BOOL)createThumbnail;

- (BOOL)applyAdjustmentsWithInputPath:(NSString *)inputPath
                            outputPath:(NSString *)outputPath
                              contrast:(float)contrast
                            brightness:(float)brightness
                            saturation:(float)saturation
                           temperature:(float)temperature
                              exposure:(float)exposure
                                 grain:(float)grain
                   chromaticAberration:(float)chromaticAberration
                             forExport:(BOOL)forExport;

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
                             createThumbnail:(BOOL)createThumbnail;

@end

NS_ASSUME_NONNULL_END