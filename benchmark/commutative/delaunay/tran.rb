require "./graph.rb"

# Return whether pd is in the circumcircle of pa, pb and pc
def encircled(pa, pb, pc, pd, dir)
  if (dir == C_CW)
    tempP = pa
    pa = pc
    pc = tempP
  end
  ax, ay = pa.x.to_f, pa.y.to_f
  bx, by = pb.x.to_f, pb.y.to_f
  cx, cy = pc.x.to_f, pc.y.to_f
  dx, dy = pd.x.to_f, pd.y.to_f
  matrixToDet = Matrix.rows([[ax, ay, ax*ax + ay*ay, 1.0], 
                             [bx, by, bx*bx + by*by, 1.0],
                             [cx, cy, cx*cx + cy*cy, 1.0], 
                             [dx, dy, dx*dx + dy*dy, 1.0]])
  return (matrixToDet.determinant > 0)
end

# Rotate a side
def rotateSide(s, dir)
  # Rotate around s.p to find edges adjacent to Y axis
  if not s.point.firstEdge.nil?
    s.ea = s.point.firstEdge
    #puts "ea0: "+s.ea.pts[0].x.to_s
    #puts "ea1: "+s.ea.pts[1].x.to_s
    s.ai = s.ea.indexOf(s.point)
    s.ap = s.ea.pts[1-s.ai]
    if s.ea.neighbors[s.ai][dir] == s.ea
      # Only one incident edge on the right
      s.eb = s.ea
      s.bi = s.ai
      s.bp = s.ap
    else
      # >= 2 incident edges on the right;
      # need to find correct ones
      loop do
        s.eb = s.ea.neighbors[s.ai][dir]
        s.bi = s.eb.indexOf(s.point)
        s.bp = s.eb.pts[1-s.bi]
       #  puts "ap: "+s.ap.x.to_s if not s.ap.nil?
       #  puts "eb0: "+s.eb.pts[0].x.to_s
       #  puts "eb1: "+s.eb.pts[1].x.to_s
       #  puts "point: "+s.point.x.to_s
        break if externAngle(s.ap, s.point, s.bp, dir)
        s.ea = s.eb
        s.ai = s.bi
        s.ap = s.bp
      end
    end
  end
end

# Find bottom
def findBottomMove(s, dir, o)
  progress = false
  if not s.eb.nil?
    while not externAngle(s.bp, s.point, o, 1-dir) do
      # Move s.p in direction dir
      progress = true
      s.ea = s.eb
      s.ai = 1-s.bi
      s.ap = s.point
      s.point = s.eb.pts[1-s.bi]
      s.eb = s.eb.neighbors[1-s.bi][dir]
      s.bi = s.eb.indexOf(s.point)
      s.bp = s.eb.pts[1-s.bi]
    end
  end
  return progress
end

# Find candidate endpoint
def findCandidate(s, dir, base, o)
  return nil if s.ea == base

  c = s.ea.pts[1-s.ai]
  return nil if externAngle(o, s.point, c, dir)

  loop do
    na = s.ea.neighbors[s.ai][dir]
    # next edge into region
    return c if na == base
    
    nai = na.indexOf(s.point)
    nc = na.pts[1-nai]
    #puts s.point.to_s if nc.nil?
    # next potential candidate
    if encircled(o, c, s.point, nc, dir)
      # have to break an edge
      s.ea.destroy
      s.ea = na
      s.ai = nai
      c = nc
    else
      return c
    end
  end
end

# Delaunay solver
# Solve recursively, then stitch back. Dim0 ranges from low0..high0, Dim1
# ranges from low1..high1. Partition based on Dim0
# Base case: 1, 2, or 3 points
def solver(l, r, low0, high0, low1, high1, parity, g)
  # Base case: 1 point
  return if l == r

  dim0, dim1 = 0, 0
  dir0, dir1 = 0, 0
  if (parity == 0)
    dim0, dim1 = C_XDIM, C_YDIM
    dir0, dir1 = C_CCW, C_CW
  else
    dim0, dim1 = C_YDIM, C_XDIM
    dir0, dir1 = C_CW, C_CCW
  end
  
  # Base case: 2 points
  if (l == r - 1)
    e = Edge.new(g.points[l], g.points[r], nil, nil, dir1, g)
    return
  end

  # Base case: 3 points
  if (l == r - 2)
    e2 = Edge.new(g.points[l+1], g.points[r], nil, nil, dir1, g)
    e1 = Edge.new(g.points[l], g.points[l+1], nil, e2, dir1, g)
    if (externAngle(g.points[l], g.points[l+1], g.points[r], dir0))
      e3 = Edge.new(g.points[l], g.points[r], e1, e2, dir0, g)
    else
      e3 = Edge.new(g.points[l], g.points[r], e1, e2, dir1, g)
    end
    #puts "Done"
    return
  end

  # Not a base case, recursively execute
  mid = low0/2 + high0/2
  i, j = l, r
  lp, rp = g.points[l], g.points[r]
  lp0, rp0 = -INF, INF

  loop do
    i0, j0 = 0, 0
    while i < j do
      i0 = g.points[i].getCoord(dim0)
      if (i0 > mid)
        if i0 < rp0
          rp0 = i0 
          rp = g.points[i]
        end
        break
      else
        if i0 > lp0
          lp0 = i0
          lp = g.points[i]
        end
      end
      i += 1
    end

    while i < j do
      j0 = g.points[j].getCoord(dim0)
      if (j0 <= mid)
        if j0 > lp0
          lp0 = j0
          lp = g.points[j] 
        end
        break
      else
        if j0 < rp0
          rp0 = j0
          rp = g.points[j]
        end
      end
      j -= 1
    end

    # 3 cases are possible here: 
    # 1. i == j, the only unexamined point left
    # 2. i < j, some points need to be swapped
    # 3. i = j+1, all points in order
    if (i == j)
      i0 = g.points[i].getCoord(dim0)
      if (i0 > mid)
        if i0 < rp0
          rp0 = i0
          rp = g.points[i]
        end
        i -= 1
      else
        if i0 > lp0
          lp0 = i0
          lp = g.points[i]
        end
        j += 1
      end
      break
    end
    if (i > j)
      i -= 1
      j += 1
      break
    end
    # If we reached here, that's case 2
    g.swap(i, j)
    i += 1
    j -= 1
  end
  
  # Left partition: l..i, right partition: j..r. Either can be empty
  if (i < l) 
    # Empty left half
    solver(j, r, low1, high1, mid, high0, 1-parity, g)
  elsif (j > r) 
    # Empty right half
    solver(l, i, low1, high1, low0, mid, 1-parity, g)
  else
    # g.points.each do |p|
    #   puts p.x.to_s+" "+p.y.to_s
    # end
    # puts "l: "+l.to_s+" i: "+i.to_s+" j: "+j.to_s+" r: "+r.to_s+" mid: "+mid.to_s
    # puts "==="
    # Divide and conquer
    if (i-l > 5 && r-j > 5)
      # Both subproblems are big; get help
      # TODO: use cobegin here
      co lambda {
        solver(l, i, low1, high1, low0, mid, 1-parity, g)
      }, lambda {
        solver(j, r, low1, high1, mid, high0, 1-parity, g)
      }
    else
      # At least one subproblem is small; do them both myself
      solver(l, i, low1, high1, low0, mid, 1-parity, g)
      solver(j, r, low1, high1, mid, high0, 1-parity, g)
    end
    
    # Stitch
    leftSide = Side.new
    rightSide = Side.new
    leftSide.point = lp
    rightSide.point = rp
    
    #puts "Begin rotate side"
    #puts "lp: "+lp.x.to_s
    #puts "rp: "+rp.x.to_s
    rotateSide(leftSide, dir1)
    rotateSide(rightSide, dir0)
    
    # Find endpoint of bottom edge of seam, by moving around border
    # as far as possible without going around a corner.
    while findBottomMove(leftSide, dir1, rightSide.point) or 
          findBottomMove(rightSide, dir0, leftSide.point)
    end
    
    tempLeft = nil
    tempRight = nil
    if not leftSide.ea.nil?
      tempLeft = leftSide.ea
    else
      tempLeft = leftSide.eb
    end
    if not rightSide.ea.nil?
      tempRight = rightSide.ea
    else
      tempRight = rightSide.eb
    end
    baseEdge = Edge.new(leftSide.point, rightSide.point,
                        tempLeft, tempRight, dir1, g)
    bottom = baseEdge
    leftSide.ea = bottom if leftSide.ea.nil?
    rightSide.ea = bottom if rightSide.ea.nil?
    
    # Work up the seam creating new edges and deleting old
    # edges where necessary.  Note that {left,right}.{b,bi,bp}
    # are no longer needed.
    loop do
      lc = findCandidate(leftSide, dir0, bottom, rightSide.point)
      rc = findCandidate(rightSide, dir1, bottom, leftSide.point)
      #puts "found lc: "+lc.x.to_s if not lc.nil?
      #puts "found rc: "+rc.x.to_s if not rc.nil?
      break if (lc.nil? and rc.nil?)
      # Choose between candidates
      if (not lc.nil?) and (not rc.nil?) and encircled(rightSide.point, lc, leftSide.point, rc, dir0)
        lc = nil
      end
      if lc.nil?
        # use right candidate
        rightSide.ea = rightSide.ea.neighbors[1-rightSide.ai][dir1]
        rightSide.ai = rightSide.ea.indexOf(rc)
        rightSide.ap = rightSide.ea.pts[1-rightSide.ai]
        rightSide.point = rc
        base = Edge.new(leftSide.point, rc, leftSide.ea, rightSide.ea, dir1, g)
      else
        # use left candidate
        leftSide.ea = leftSide.ea.neighbors[1-leftSide.ai][dir0]
        leftSide.ai = leftSide.ea.indexOf(lc)
        leftSide.ap = leftSide.ea.pts[1-leftSide.ai]
        leftSide.point = lc
        base = Edge.new(lc, rightSide.point, leftSide.ea, rightSide.ea, dir1, g)
      end
    end
    # puts "Done"
  end
end

srand(1)
size = 16384
g = Graph.new(size)
ParLib.init(ARGV[0].to_i)

initEndTime = Time.now
solver(0, g.n - 1, g.minX, g.maxX, g.minY, g.maxY, 0, g)
computeEndTime = Time.now

computeTime = computeEndTime - initEndTime

puts sprintf("computing time=%.6f", computeTime)
exit
