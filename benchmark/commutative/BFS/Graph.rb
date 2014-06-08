#!/usr/bin/ruby

require 'detpar'
require 'thread'

class Vertex
  attr_accessor :neighbors, :vid, :parent, :color
  # @@called = 0
    
  def initialize(id)
    @vid = id
    @parent = nil
    @mutex = Mutex.new
    @color = 0
  end
  
  def setNList(nList)
    @degree = nList.size
    @neighbors = nList
  end
  
  def setMinParent(newParent)
    # @@called += 1
    # puts "called: "+@@called.to_s
    @mutex.lock
    if @parent.nil? || (newParent.vid <= @parent.vid)
      @parent = newParent
      @mutex.unlock
      return true
    end
    @mutex.unlock
    return false
  end
  
  setACOps :setMinParent
  #unchecked :setMinParent
end

class Graph
  attr_accessor :vList, :numV, :numE
  
  def initialize(vertexList, v, e)
    @vList = vertexList
    @numV = v
    @numE = e
  end
  
  def BFS(startNode)
    # For loop control and frontier info
    frontier = Array.new()
    startNode = @vList[startNode]
    startNode.color = 1
    startNode.parent = nil
    frontier.push(startNode)

    # For execution info
    round = 0
    totalVisited = 0
    
    while (frontier.size > 0) do
      totalVisited += frontier.size
      round += 1
      
      #puts "Round "+round.to_s
      
      offsetList = Array.new(@numV)
      # For each vertex in frontier, try to extend its unvisited neighbors. We may set a node to multiple other node's neighbors, but we get deterministic parent for each node
      frontier.all do |v|
        if (not v.nil?)
          newNeighbors = Array.new()
          v.neighbors.each do |ngh|
            if ngh.setMinParent(v)
              newNeighbors.push(ngh) 
              #puts "set "+ngh.vid.to_s+" points to "+ngh.parent.to_s
            end
          end
          v.neighbors = newNeighbors
          offsetList[v.vid] = v.neighbors.size
        end
      end
   
      # Collect offsets for each vertex
      (0...offsetList.size).each do |i|
        offsetList[i] = 0 if offsetList[i].nil?
      end

      
      # Collect offsets for each vertex
      (0...offsetList.size).each do |i|
        if (i != 0)
          offsetList[i] = offsetList[i - 1] + offsetList[i]
        end
      end
      
      # Move all neighbors to next frontier, according to their parents
      frontierNext = Array.new(@numV)
      frontier.all do |v|
        myBase = offsetList[v.vid] - v.neighbors.size
        newNeighbors = Array.new()
        currIndex = 0     
        v.neighbors.each do |ngh|
          if (ngh.parent == v) && (ngh.color == 0)
            frontierNext[myBase + currIndex] = ngh
            newNeighbors.push(ngh)
            #puts "get "+ngh.vid.to_s+" points to "+v.vid.to_s
          end
          currIndex += 1
        end
        v.neighbors = newNeighbors
      end
      # Update color for old frontier
      frontier.each do |v|
        v.color = 2
      end
      # Set new frontier
      frontier = frontierNext.compact
      # Update color for new frontier
      frontier.each do |v|
        v.color = 1
      end
    end
    
    puts "Round: "+round.to_s+", visited: "+totalVisited.to_s
    return round
  end
  
  def outputSimple(fileName)
    dataRoot = File.expand_path(File.dirname(__FILE__))
    outputF = File.open(dataRoot + "/" + fileName, "w")
    # Print out edges for each vertex
    @vList.each do |v|
      v.neighbors.each do |n|
        outputF.write v.vid.to_s+" connects to "+n.vid.to_s+"\n"
      end
    end
  end
  
  def output(fileName)
    dataRoot = File.expand_path(File.dirname(__FILE__))
    outputF = File.open(dataRoot + "/" + fileName, "w")
    puts "output to "+fileName
    # Print out file header
    outputF.write "AdjacencyGraph\n"
    outputF.write @numV.to_s + "\n"
    # Count how many edges in this graph
    newNumE = 0
    @vList.each do |v|
      newNumE += v.neighbors.size
    end
    outputF.puts newNumE
    # Print out offset list
    sumOffset = 0
    @vList.each do |v|
      outputF.puts sumOffset.to_s
      sumOffset += v.neighbors.size
    end
    # Print out all edges
    @vList.each do |v|
      v.neighbors.each do |n|
        outputF.puts n.vid.to_s
      end
    end
  end
  
end # Class Graph
