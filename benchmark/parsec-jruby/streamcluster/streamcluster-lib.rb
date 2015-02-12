#!/usr/bin/env ruby

# Copyright (c) 2012 

# streamcluster - Online clustering algorithm
#
# 
# Reference Source: streamcluster in PARSEC Benchmark Suite

require "../common/BenchHarness"
require 'detpar'

class Point 
    attr_accessor :weight
    attr_accessor :coord
    attr_accessor :assign
    attr_accessor :cost
    
    def initialize
        @coord = Array.new
    end 
end

class Points
    attr_accessor :num, :dim
    attr_accessor :p
    
    def initialize(num = 0, dim = 1)
        @num = num
        @dim = dim
        @p = Array.new(num) { 
            Point.new
        }
    end
end

class PStream < PipeStream
    def read(dest, dim, num)
    end
    def ferror
    end
    def feof
    end
end


class SimStream < PStream 
    attr_accessor :r
    attr_reader   :pos, :n, :dim, :chunkSize
    def initialize(num, dim, chunkSize)
        @n = num
        @pos = 0
        @dim = dim
        @chunkSize = chunkSize
    end
    def read(chunkSize, dim, points)
        count = 0
        (0...chunkSize).each {|i|
            break if pos >= n
            points.p[i].weight = 1.0
            points.p[i].coord = Array.new
            (0...dim).each { |j|
                points.p[i].coord.push(Float((i * (j+1) * 1.0) / (@n * @dim))) 
            }
            count += 1
        }
        @pos += count
        return count
    end
    def getAndMove
      if not feof
        points = Points.new(@chunkSize, @dim)
        numRead = read(@chunkSize, @dim, points)
        points.num = numRead
        return points
      else
        return nil
      end
    end
    
    def ferror
        return false
    end
    def feof
        if @pos >= @n
            return true
        else
            return false
        end
    end      
end

class FileStream < PStream 
    attr :file
    def initialize(filename)
        @file = File.open(@filename) if File::exists?(filename)
        if @file == nil
            puts "ERROR: unable to open file", filename, ".\n"
            exit
        end
        ObjectSpace.define_finalizer(self, self.class.method(:finalize).to_proc)
    end
    def read(points, dim, num)
        count = 0
        (0...num).each {|i|
            points.p[i].weight = 1.0
            points.p[i].coord = Array.new
            (0...dim).each { |j|
                points.p[i].coord.push(@file.read(4).unpack("F")) 
            }
            count += 1
        }
    rescue
        return count
    end
    def ferror
        return @file.ferror
    end
    def feof
        return @file.eof
    end
    
    def FileStream.finalize(id)
       @file.close
    end
end

class FakeOStream < PipeStream
    def setAndMove(obj)
        return
    end
end

class Streamcluster < Benchmark
    #constant
    attr_accessor :MAXNAMESIZE          ##define MAXNAMESIZE 1024 // max filename length
    attr_accessor :SEED                 ##define SEED 1
    attr_accessor :SP                   ##define SP 1 // number of repetitions of speedy must be >=1
    attr_accessor :ITER                 ##define ITER 3 // iterate ITER* k log k times; ITER >= 1
    
    attr_accessor :switch_membership    #static bool *switch_membership; //whether to switch membership in pgain
    attr_accessor :is_center            #static bool* is_center; //whether a point is a center
    attr_accessor :center_table         #static int* center_table; //index table of centers
                                        #static int nproc; //# of threads
                                        
    attr_accessor :centers
    attr_accessor :centerIDs
                                        
    attr_accessor :inputFile
    attr_accessor :outputFile
    attr_accessor :kmin
    attr_accessor :kmax
    attr_accessor :dim
    attr_accessor :n
    attr_accessor :chunksize
    attr_accessor :clustersize
    
    attr_accessor :kfinal
    attr_accessor :numcenters
    
    def initialize(thread_num, kmin, kmax, dim, n, chunksize, clustersize, input, output)
        super("Streamcluster", thread_num, false)
        @MAXNAMESIZE = 1024
        @SEED = 1
        @SP = 1
        @ITER = 3
        @switch_membership = Array.new
        @is_center = Array.new
        @center_table = Array.new
        @inputFile = input
        @outputFile = output
        @kmin = kmin
        @kmax = kmax
        @dim = dim
        @n = n
        @chunksize = chunksize
        @clustersize = clustersize
        @kfinal = 0
    end     
    def prt (points)
        points.p.each {|p|
            
            p.coord.each{|c|
                print c, ","
            }
            print "\n"
        }
    end
    def isIndentical (i, j, d)
        a = 0
        equal = 1
        while equal == 1 and a < d
            if i[a] != j[a]
                equal = 0 
            else
                a += 1
            end
        end
        return equal
    end
    
    #/* comparator for floating point numbers */
    def floatcomp(i, j)
        if a > b
            return 1
        elsif a < b
            return -1
        else
            return 0
        end
    end

   #/* compute Euclidean distance squared between two points */
    def dist (p1, p2, dim)
        result = 0.0

        (0...dim).each { |i|
            result += (p1.coord[i] - p2.coord[i]) ** 2
        }

        return result
    end
    
    #--------------------------------------------------------------------
    def pspeedy(points, z)
        open = false
        costs = 0.0
        #r = Random.new
        
        #/* create center at first point, send it to itself */
        (0...points.num).all {|i|
            distance = dist(points.p[i], points.p[0], points.dim)
            points.p[i].cost = distance * points.p[i].weight
            points.p[i].assign = 0
        }

        @numcenters = 1
        (0...points.num).each {|i|
            to_open = i * 1.0 / points.num < (points.p[i].cost / z)
            if to_open
                @numcenters += 1
                open = true
                (0...points.num).all {|j|
                    distance = dist(points.p[i], points.p[j], points.dim)
                    if distance * points.p[j].weight < points.p[j].cost
                        points.p[j].cost = distance * points.p[j].weight
                        points.p[j].assign = i
                    end
                }
                open = false
            end
        }
        open = false;
        
        costs = z * @numcenters
        total_cost = Reduction.new(Reduction::ADD)
        (0...points.num).all {|i|
            total_cost.push(points.p[i].cost)
        }
        costs += total_cost.get
        
        return costs
    end
    
    ###########################################################################
    def pgain(x, points, z)
        pid = 0
        bsize = points.num/@cfg_tnum
        k1 = bsize * pid
        k2 = k1 + bsize
        k2 = points.num if pid == @cfg_tnum - 1

        i = 0
        number_of_centers_to_close = 0

        gl_cost_of_opening_x = 0.0
        gl_number_of_centers_to_close = 0.0

        stride = @numcenters + 2
        myK = stride -2  

        cost_of_opening_x = 0.0

        if pid == 0 
            work_mem = Array.new(stride* (@cfg_tnum + 1))
            gl_cost_of_opening_x = 0
            gl_number_of_centers_to_close = 0
        end

        count = 0
        (k1...k2).each{|i|
            if @is_center[i] 
                @center_table[i] = count
                count += 1
            end 
        }
        work_mem[pid*stride] = count

        if pid == 0 
            accum = 0
            (0...@cfg_tnum).each { |p|
                tmp = Integer(work_mem[p * stride])
                work_mem[p * stride] = accum
                accum += tmp
            }
        end

        (k1...k2).each{|i|
            @center_table[i] += Integer(work_mem[pid*stride]) if  @is_center[i] 
            @switch_membership[i] = false
        }
        
        (0...stride * (@cfg_tnum + 1)).each{|i|
            work_mem[i] = 0.0
        }  

        lower = pid*stride
        gl_lower = @cfg_tnum*stride
        
        (k1...k2).each{|i|
            x_cost = dist(points.p[i], points.p[x], points.dim)*points.p[i].weight
            current_cost = points.p[i].cost
            if  x_cost < current_cost 
                @switch_membership[i] = true
                cost_of_opening_x += x_cost - current_cost
            else
                assign = points.p[i].assign
                work_mem[lower + @center_table[assign]] += current_cost - x_cost
            end
        }

        (k1...k2).each {|i|
            if @is_center[i]  
                low = z
                (0...@cfg_tnum).each{|p|
                    low += work_mem[@center_table[i]+p*stride]
                    
                }

                work_mem[gl_lower+@center_table[i]] = low
                if  low > 0 
                    number_of_centers_to_close += 1  
                    cost_of_opening_x -= low
                end
           end
        }

        work_mem[pid*stride + myK] = number_of_centers_to_close
        work_mem[pid*stride + myK+1] = cost_of_opening_x

        gl_cost_of_opening_x = z

        (0...@cfg_tnum).each{|p|
            gl_number_of_centers_to_close += Integer(work_mem[p*stride + myK])
            gl_cost_of_opening_x += work_mem[p*stride+myK+1]
        }
        
        if  gl_cost_of_opening_x < 0 
            (k1...k2).each{|i|
                close_center = work_mem[gl_lower + @center_table[points.p[i].assign]] > 0 
                if  @switch_membership[i] or close_center 
                    points.p[i].cost = points.p[i].weight * dist(points.p[i], points.p[x], points.dim)
                    points.p[i].assign = x
                end
            }
            
            (k1...k2).each{|i|
                if @is_center[i] and work_mem[gl_lower + @center_table[i]] > 0 
                    @is_center[i] = false
                end
            }
            if x >= k1 and x < k2 
                @is_center[x] = true
            end

            @numcenters = @numcenters + 1 - gl_number_of_centers_to_close
        else 
            gl_cost_of_opening_x = 0  
        end
        return -gl_cost_of_opening_x
       
    end
    
    #/* facility location on the points using local search */
    #/* z is the facility cost, returns the total cost and # of centers */
    #/* assumes we are seeded with a reasonable solution */
    #/* cost should represent this solution's cost */
    #/* halt if there is < e improvement after iter calls to gain */
    #/* feasible is an array of numfeasible points which may be centers */
    def pFL(points, feasible, numfeasible, z, cost, iter, e)
        change = cost;
        #/* continue until we run iter iterations without improvement */
        #/* stop instead if improvement is less than e */
        while change/cost > 1.0 * e 
            change = 0.0;
            numberOfPoints = points.num;

            (0...iter).each{|i|
              x = i % numfeasible
              change += pgain(feasible[x], points, z)
            }
            cost -= change;

        end
        return cost
    end
  
    ###########################################################################
    def selectfeasible_fast(points, feasible, kmin)
        numfeasible = points.num;
        if numfeasible > (@ITER * kmin * Math.log(Float(kmin)))
            numfeasible = Integer(@ITER * kmin * Math.log(Float(kmin)));
        end
        
        numfeasible.times{feasible.push(0)}

        accumweight = Array.new
        k1 = 0;
        k2 = numfeasible;

        #/* not many points, all will be feasible */
        if numfeasible == points.num
            (0...numfeasible).each { |i|
                feasible[i] = i
            }
            return numfeasible;
        end
        
        accumweight= Array.new(points.num);

        accumweight[0] = points.p[0].weight;
        totalweight=0;
        (1...points.num).each{ |i|
            accumweight[i] = accumweight[i-1] + points.p[i].weight
        }
        totalweight=accumweight[points.num-1];
        
        (k1...k2).each { |i|
            w = (k2-i)*1.0/k2
            l = 0
            r = points.num-1;
            if accumweight[0] > w 
                feasible[i] = 0
                next 
            end
            
            while l + 1 < r 
                k = (l + r) / 2
                if accumweight[k] > w 
                    r = k
                else
                    l = k
                end
            end
            feasible[i] = r
        }
        return numfeasible;
    end
    
    ###########################################################################
    #/* compute approximate kmedian on the points */
    def pkmedian(points, kmin, kmax)
        feasible = Array.new
        hiz = 0.0
        loz = 0.0
        numberOfPoints = points.num
        ptDimension = points.dim
        
        bsize = points.num
        k1 = 0
        k2 = points.num

        r = Reduction.new(Reduction::ADD)
        (k1...k2).all{ |kk|
            r.push(dist(points.p[kk], points.p[0], ptDimension) * points.p[kk].weight)
        }
        hiz = r.get;
        
        loz = 0.0
        z = (hiz + loz) / 2.0
        #/* NEW: Check whether more centers than points! */
        if points.num <= kmax
            #/* just return all points as facilities */
            (k1...k2).all{ |kk|
                points.p[kk].assign = kk
                points.p[kk].cost = 0.0
            }
            cost = 0.0
            @kfinal = @numcenters
            return cost
        end

        cost = pspeedy(points, z)
       
        i=0;
        #/* give speedy SP chances to get at least kmin/2 facilities */
        while (@numcenters < kmin) and (i < @SP)
            cost = pspeedy(points, z)
            i += 1
        end
       
        #/* if still not enough facilities, assume z is too high */
        while @numcenters < kmin
            if i >= @SP
                hiz = z
                z= ( hiz + loz ) /2.0
                i=0
            end
            cost = pspeedy(points, z)
            i += 1
        end
        #/* now we begin the binary search for real */
        #/* must designate some points as feasible centers */
        #/* this creates more consistancy between FL runs */
        #/* helps to guarantee correct # of centers at the end */
          #puts "printf pint"
        numfeasible = selectfeasible_fast(points,feasible, kmin)
        (0...points.num).each { |i|
            is_center[points.p[i].assign]= true
        }
        
        
        while true
            #/* first get a rough estimate on the FL solution */
            lastcost = cost
            cost = pFL(points, feasible, numfeasible, z, cost, Integer(@ITER* kmax * Math.log(Float(kmax))), 0.1)
            #puts cost
            
            #/* if number of centers seems good, try a more accurate FL */
            if ((@numcenters <= (1.1)* kmax) and (@numcenters >= (0.9) * kmin)) or ((@numcenters <= kmax + 2) and (@numcenters >= kmin - 2))
                #/* may need to run a little longer here before halting without         improvement */
                cost = pFL(points, feasible, numfeasible, z, cost, Integer(@ITER * kmax * Math.log(Float(kmax))), 0.001)
            end

            if @numcenters > kmax
                #/* facilities too cheap */
                #/* increase facility cost and up the cost accordingly */
                loz = z.to_f
                puts "loz = "+loz.to_s
                z = (hiz + loz) / 2.0
                cost += (z - loz) * @numcenters
            end
            if @numcenters < kmin
              #/* facilities too expensive */
              #/* decrease facility cost and reduce the cost accordingly */
              hiz = z
              z = (hiz + loz) / 2.0
              cost += (z - hiz) * @numcenters
            end

            #/* if k is good, return the result */
            #/* if we're stuck, just give up and return what we have */
            if ((@numcenters <= kmax) and (@numcenters >= kmin)) or ((loz >= (0.999) * hiz)) or (loz == 0.0) # added loz == 0.0
                break 
            end
        end

        #//clean up...
        @kfinal = @numcenters
        puts "done"
        return cost
    end
    
    ###########################################################################
    #/* compute the means for the k clusters */
    def contcenters(points)
        (0...points.num).each{|i|
            #/* compute relative weight of this point to the cluster */
            if points.p[i].assign != i
                relweight = points.p[points.p[i].assign].weight + points.p[i].weight
                relweight = points.p[i].weight / relweight
                (0...points.dim).each{|ii|
                    points.p[points.p[i].assign].coord[ii] *= 1.0 - relweight
	                points.p[points.p[i].assign].coord[ii]+= points.p[i].coord[ii] * relweight
	            }
                points.p[points.p[i].assign].weight += points.p[i].weight
            end
        }
        return 0
    end
    ###########################################################################
    #/* copy centers from points to centers */
    def copycenters(points, centers, centerIDs, offset)
        is_a_median = Array.new(points.num, false);

        #/* mark the centers */
        (0...points.num).each { |i|
            is_a_median[points.p[i].assign] = 1
        }

        k = centers.num

        #/* count how many  */
        (0...points.num).each{|i|
            if is_a_median[i]
                #new pints here?
                centers.p[k].coord = points.p[i].coord.clone
                centers.p[k].weight = points.p[i].weight
                centerIDs[k] = i + offset
                k += 1
            end
        }

        centers.num = k
    end
    
    ###########################################################################
    def localSearch(points, kmin, kmax)
        pkmedian(points, kmin, kmax)
    end      

    def statis
        outFile = File.open(@outputFile, "w")
        
        is_a_median = Array.new(@centers.num)
        (0...@centers.num).each {|i|
            is_a_median[@centers.p[i].assign] = 1
        }
        
        (0...@centers.num).each {|i|
            if is_a_median[i] == 1
                outFile.puts @centerIDs[i]
                outFile.puts @centers.p[i].weight
                (0...@centers.dim).each{|k|
                    outFile.print @centers.p[i].coord[k], " "
                }
                outFile.puts "\n\n"
            end
        }
        outFile.close
    end
    
    ###########################################################################
    def start
        @centerIDs = Array.new(@clustersize * @dim, 0)
        
        # points.p = Array.new
        # @chunksize.times{points.p.push(Point.new)}
        
        if @n > 0
            stream = SimStream.new(@n, @dim, @chunksize)
        else
            stream = FileStream.new(@inputFile)
        end
           
        @centers = Points.new
        @centers.dim = @dim
        @centers.p = Array.new
        @clustersize.times{@centers.p.push(Point.new)}
        
        @centers.num = 0
        #(0...@clustersize).each{|i|
        (0...@clustersize).all{|i|
            @centers.p[i].coord = Array.new(@dim, 0.0)
            @centers.p[i].weight = 1.0
        }
        
        iDoffset = 0
        stage1 = lambda { |points|
          puts "feed: " + points.num.to_s
            @switch_membership = Array.new(points.num, false)
            @is_center = Array.new(points.num, false)
            @center_table = Array.new(points.num, 0)

            localSearch(points,kmin, kmax)
            
            
            puts "local search finished"
            contcenters(points)
            return points
        }
        
        stage2 = lambda { |points|
            copycenters(points, @centers, @centerIDs, iDoffset)
            iDoffset += points.num
        }
        pipe = stage1 >> stage2
        
        pipe.setIStream(stream)
        pipe.setOStream(FakeOStream.new)
        pipe.run
        
        @switch_membership = Array.new(@centers.num, false)
        @is_center = Array.new(@centers.num, false)
        @center_table = Array.new(@centers.num, 0)
        
        localSearch(@centers, kmin, kmax)
        
        #puts "local search on centers are finished"
        contcenters(@centers)
        
    end
end

th_num = ARGV[0]
ParLib.init(th_num.to_i)

#2 5 1 10 10 5 none output.txt
#thread_num, kmin, kmax, dim, n, chunksize, clustersize, input, output
#printf(stderr,"usage: %s k1 k2 d n chunksize clustersize infile outfile nproc\n",
#test
#bench = Streamcluster.new(1, 2, 5, 1, 10, 10, 5, "none", "output.txt")
#simlarge 10 20 128 16384 16384 1000 none output.txt
#bench = Streamcluster.new(1, 10, 20, 128, 16384, 16384, 1000, "none", "output.txt")
#simsmall 10 20 32 4096 4096 1000
#bench = Streamcluster.new(1, 10, 20, 32, 4096, 2048, 1000, "none", "output.txt")

bench = Streamcluster.new(1, 10, 20, 16, 4096, 1024, 1000, "none", "output.txt")
bench.run
exit
