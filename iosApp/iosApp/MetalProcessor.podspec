Pod::Spec.new do |s|
  s.name             = 'MetalProcessor'
  s.version          = '1.0.0'
  s.summary          = 'Metal-based LUT processor for Film Simulator'
  s.homepage         = 'https://github.com/YahiaAngelo/Film-Simulator'
  s.license          = { :type => 'MIT' }
  s.author           = { 'Film Simulator' => 'dev@filmsimulator.com' }
  s.source           = { :path => '.' }

  s.ios.deployment_target = '12.0'
  s.swift_version = '5.0'

  s.source_files = [
    'MetalLUTProcessor.h',
    'MetalLUTProcessor.m',
    'MetalProcessor.h',
    'MetalProcessorBridge.m',
    'MetalLUTProcessorWrapper.swift'
  ]

  s.resources = ['LUTProcessor.metal', 'default.metallib']

  s.frameworks = 'Metal', 'MetalKit', 'UIKit', 'CoreGraphics', 'Foundation'

  s.requires_arc = true
end