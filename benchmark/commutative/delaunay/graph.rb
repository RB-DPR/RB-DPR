require 'matrix'
require 'set'
require 'thread'
require 'detpar'

C_CCW = 0
C_CW = 1
C_XDIM = 0
C_YDIM = 1
INF = 1.0 / 0.0

# class Set
#   setCommute :add, :delete
#   selfCommute :delete, :delete
# end
# 
# class Hash
#   selfCommute :delete
#   setCommute :delete, :store
# end

class Point
  attr_reader :x, :y
  attr_accessor :firstEdge
  
  def initialize(x, y)
    @x = x
    @y = y
    @firstEdge = nil
  end
  
  # Override hash function for point comparison
  def hash()
    return @x ^ @y
  end

  # Get coordinate according to index
  def getCoord(d)
    return @x if d == C_XDIM
    return @y if d == C_YDIM
    return -1
  end
end

class Edge
  attr_reader :pts
  attr_accessor :neighbors
  
  # Get the index of a point in an edge
  def indexOf(p)
    # return -1 if pts.nil?
    return 0 if pts[0] == p
    return 1 if pts[1] == p
    return -1
  end
  
  # Utility method for constructor
  def initEnd(p, e, endFlag, dir)
    if e.nil?
      @neighbors[endFlag][dir] = self
      @neighbors[endFlag][1-dir] = self
      p.firstEdge = self
    else
      puts "Something wrong: p is not a point in e" if e.indexOf(p) < 0
      i = e.indexOf(p)
      @neighbors[endFlag][1-dir] = e
      @neighbors[endFlag][dir] = e.neighbors[i][dir]
      e.neighbors[i][dir] = self
      i = neighbors[endFlag][dir].indexOf(p)
      # puts "!!!" if i < 0
      @neighbors[endFlag][dir].neighbors[i][1-dir] = self
    end
  end
  
  # Constructor: connect points pa and pb, inserting dir (CW or CCW)
  # of edge ea at the pa end and 1-dir of edge eb at the pb end.
  # Either or both of pa and pb may be null.
  def initialize(pa, pb, ea, eb, dir, g)
    @pts = Array.new(2) if @pts.nil?
    @neighbors = Array.new(2) if @neighbors.nil?
    @neighbors[0] = Array.new(2) if @neighbors[0].nil?
    @neighbors[1] = Array.new(2) if @neighbors[1].nil?
    
    @pts[0] = pa
    @pts[1] = pb
    
    initEnd(pa, ea, 0, dir)
    initEnd(pb, eb, 1, 1-dir)
    
    # Add this edge to the graph
    @myGraph = g
    @myGraph.addEdge(self)
    # puts "Connect "+pa.x.to_s+" and "+pb.x.to_s
  end
  
  def destroy()
    # Remove myself out from the graph
    # Then update infomation for my neighbors
    (0...2).each do |i|
      cw_index = @neighbors[i][C_CW].indexOf(@pts[i])
      ccw_index = @neighbors[i][C_CCW].indexOf(@pts[i])
      @neighbors[i][C_CW].neighbors[cw_index][C_CCW] = @neighbors[i][C_CCW]
      @neighbors[i][C_CCW].neighbors[ccw_index][C_CW] = @neighbors[i][C_CW]
      @pts[i].firstEdge = @neighbors[i][C_CCW] if (@pts[i].firstEdge == self)
    end
    @myGraph.deleteEdge(self)
    # puts "Destory "+pts[0].x.to_s+" and "+pts[1].x.to_s
  end
end

# Main data structure for the graph
class Graph
  attr_reader :points, :n, :minX, :minY, :maxX, :maxY, :edgeSet, :mutex
  
  def addEdge(e)
    @mutex.lock
    @edgeSet.add(e)
    @mutex.unlock
  end
  
  def deleteEdge(e)
    @mutex.lock
    @edgeSet.delete(e)
    @test = 120
    @mutex.unlock
  end
  
  def initialize(n)
    @n = n
    @points = Array.new(n)
    @pointSet = Set.new
    @edgeSet = Set.new
    
    @minX = INF
    @minY = INF
    @maxX = -INF
    @maxY = -INF
    @mutex = Mutex.new
    @test = 0
    (0...n).each do |i|
      x = 0
      y = 0
      point = nil
      # Generate a new point and make sure it's unique
      loop do
        x = rand(10000) + 1
        y = rand(10000) + 1
        point = Point.new(x, y)
        break if not @pointSet.include?(point)
      end
      # Add point into graph
      @pointSet.add(point)
      @minX = x if x < @minX
      @minY = y if y < @minY
      @maxX = x if x > @maxX
      @maxY = y if y > @maxY
      @points[i] = point
    end
  end

  # Swap points at index i and j
  def swap(i, j)
    tempP = points[i]
    points[i] = points[j]
    points[j] = tempP
  end
  
  setACOps :addEdge, :deleteEdge
end

class Side
  attr_accessor :point, :ea, :eb, :ap, :bp, :ai, :bi
end

# Return angle pa,pb,pc >= 180 deg, in direction dir
def externAngle(pa, pb, pc, dir)
  if dir == C_CW
    tempP = pa
    pa = pc
    pc = tempP
  end
  x1 = pa.x
  x2 = pb.x
  x3 = pc.x
  y1 = pa.y
  y2 = pb.y
  y3 = pc.y
  if (x1 == x2)
    if (y1 > y2)
      return x3 >= x2
    else
      return x3 <= x2
    end
  else
    m = (y2.to_f - y1.to_f) / (x2.to_f - x1.to_f)
    # puts "m = "+m.to_s
    if (x1 > x2)
      return (y3 <= (m * (x3.to_f - x1.to_f) + y1.to_f))
    else
      return (y3 >= (m * (x3.to_f - x1.to_f) + y1.to_f))
    end
  end
end 
