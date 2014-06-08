require 'rubygems'
#require 'bundler/setup'

require 'benchmark'
require 'chunky_png'

require 'detpar'

nCores = ARGV[0]
puts "init parlib, threads: "+nCores.to_s
ParLib.init(nCores.to_i)

image = ChunkyPNG::Image.new(240, 1800, ChunkyPNG::Color::TRANSPARENT)

# set some random pixels
image[10, 20] = ChunkyPNG::Color.rgba(255,   0,   0, 255)
image[11, 20] = ChunkyPNG::Color.rgba(255,   0,   0, 255)
image[12, 20] = ChunkyPNG::Color.rgba(255,   0,   0, 255)
image[13, 20] = ChunkyPNG::Color.rgba(255,   0,   0, 255)
image[50, 87] = ChunkyPNG::Color.rgba(  0, 255,   0, 255)
image[33, 99] = ChunkyPNG::Color.rgba(  0,   0, 255, 255)

n = (ENV['N'] || '5').to_i

puts "---------------------------------------------"
puts "ChunkyPNG (#{ChunkyPNG::VERSION}) encoding benchmark (n=#{n})"
puts "---------------------------------------------"
puts

init_end_time = Time.now

image.save("no_compression.png", :no_compression)
image.save("fast_rgba.png", :fast_rgba)
image.save("fast_rgb.png", :fast_rgb)
image.save("good_compression.png", :good_compression)
image.save("best_compression.png", :best_compression)

image.save("rgb.png", :color_mode => ChunkyPNG::COLOR_TRUECOLOR)
image.save("rgba.png", :color_mode => ChunkyPNG::COLOR_TRUECOLOR_ALPHA)
image.save("indexed.png", :color_mode => ChunkyPNG::COLOR_INDEXED)
image.save("interlaced.png", :interlaced => true)

Benchmark.bmbm do |x|
  x.report('Autodetect (indexed)')  { n.times { image.to_blob } }

  # Presets
  x.report(':no_compression')    { n.times { image.to_blob(:no_compression) } }
  x.report(':fast_rgba')         { n.times { image.to_blob(:fast_rgba) } }
  x.report(':fast_rgb')          { n.times { image.to_blob(:fast_rgb) } }
  x.report(':good_compression')  { n.times { image.to_blob(:good_compression) } }
  x.report(':best_compression')  { n.times { image.to_blob(:best_compression) } }
  
  # Some options
  x.report(':rgb')        { n.times { image.to_blob(:color_mode => ChunkyPNG::COLOR_TRUECOLOR) } }
  x.report(':rgba')       { n.times { image.to_blob(:color_mode => ChunkyPNG::COLOR_TRUECOLOR_ALPHA) } }
  x.report(':indexed')    { n.times { image.to_blob(:color_mode => ChunkyPNG::COLOR_INDEXED) } }
  x.report(':interlaced') { n.times { image.to_blob(:interlaced => true) } }
  
  # Exports
  x.report('to RGBA pixelstream') { n.times { image.to_rgba_stream } }
  x.report('to RGB pixelstream')  { n.times { image.to_rgb_stream } }
end

compute_end_time = Time.now
compute_time = compute_end_time - init_end_time
puts sprintf("computing time=%.6f", compute_time)



exit
