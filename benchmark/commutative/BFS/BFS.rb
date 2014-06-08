#!/usr/bin/ruby

require 'detpar'
require 'thread'

require './Graph.rb'

class Vertex
  attr_accessor :edgesToRead
end

def buildGraphFromFile(fileName)
  dataRoot = File.expand_path(File.dirname(__FILE__))
  gFile = File.open(dataRoot + "/" + fileName, "r")
  # Read Adjacent tag
  tag = gFile.gets.chomp
  if (tag != "AdjacencyGraph")
    puts "Wrong format!"
    return
  end
  
  # Read nV and nE
  nV = Integer(gFile.gets)
  nE = Integer(gFile.gets)
  vList = Array.new()
  
  puts "nV = "+nV.to_s+", nE = "+nE.to_s
  
  # Read an offset 0
  lastOffset = Integer(gFile.gets)
  
  # Read out all offsets
  (0...nV).each do |i|
    currVertex = Vertex.new(i)
    if (i < nV - 1)
      currOffset = Integer(gFile.gets)
      currVertex.edgesToRead = currOffset - lastOffset
      lastOffset = currOffset
    else
      currVertex.edgesToRead = nE - lastOffset
    end
    # puts i.to_s+": "+currVertex.edgesToRead.to_s
    vList.push(currVertex)
  end
  
  # Read out edges for each vertex
  vList.each do |v|
    nList = Array.new()
    (0...v.edgesToRead).each do |i|
      vIndex = Integer(gFile.gets)
      nList.push(vList[vIndex])
    end
    v.setNList(nList)
  end
  
  # Initialize a graph
  g = Graph.new(vList, nV, nE)
  return g
end

fileName = ARGV[0]
g = buildGraphFromFile(fileName)
# g.outputSimple("result_before.txt")
ParLib.init(ARGV[1].to_i)

init_end_time = Time.now
g.BFS(0)
compute_end_time = Time.now
puts "=========================="
g.output("result_after.txt")

compute_time = compute_end_time - init_end_time

puts sprintf("computing time=%.6f", compute_time)
exit
