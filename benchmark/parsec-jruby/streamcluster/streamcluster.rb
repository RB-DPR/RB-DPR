#!/usr/bin/env ruby

# Copyright (c) 2012 

# streamcluster - Online clustering algorithm
#
# 
# Reference Source: blackscholes in PARSEC Benchmark Suite

require "../common/BenchHarness"
require 'rdoc/rdoc'

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
    attr_accessor :num
    attr_accessor :dim
    attr_accessor :p
    
    def initialize
        @num = 0
        @dim = 1
        @p = Array.new
    end
end
=begin
class Pkmedian_arg_t
    attr_accessor :points
    attr_accessor :kmin
    attr_accessor :kmax
    attr_accessor :kfinal
end
=end


class PStream 
    def read(dest, dim, num)
    end
    def ferror
    end
    def feof
    end
end


=begin
    size_t count = 0;
    for( int i = 0; i < num && n > 0; i++ ) {
      for( int k = 0; k < dim; k++ ) {
	dest[i*dim + k] = lrand48()/(float)INT_MAX;
      }
      n--;
      count++;
=end      
#synthetic stream
class SimStream < PStream 
    attr_accessor :n
    attr_accessor :r
    def initialize(num)
        @n = num
        @r = Random.new
    end
    def read(points, dim, num)
        count = 0
        (0...num).each {|i|
            break if @n <= 0
            points.p[i].weight = 1.0
            points.p[i].coord = Array.new
            (0...dim).each { |j|
                #points.p[i].coord.push(Float(Float(@r.rand(0...65536)) / Float(65536)) )
                points.p[i].coord.push(Float((i * (j+1) * 1.0) / (num * dim))) 
                #puts points.p[i].coord
            }
            @n -= 1
            count += 1
        }
        return count
    end
    def ferror
        return false
    end
    def feof
        return @n <= 0
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
        #puts p1.coord[0]
        #puts p2.coord[0]
        (0...dim).each { |i|
            #print p1.coord[i], ",", p2.coord[i], "\n"
            result += (p1.coord[i] - p2.coord[i]) ** 2
        }
        #puts result
        return result
    end
    
    #--------------------------------------------------------------------
    def pspeedy(points, z)
        open = false
        costs = 0.0
        r = Random.new
        
        #/* create center at first point, send it to itself */
        (0...points.num).each {|i|
            distance = dist(points.p[i], points.p[0], points.dim)
            points.p[i].cost = distance * points.p[i].weight
            points.p[i].assign = 0
        }

        @numcenters = 1
        (0...points.num).each {|i|
            #to_open = (Float(r.rand(0...65536)) / Float(65536)) < (points.p[i].cost / z)
            to_open = i * 1.0 / points.num < (points.p[i].cost / z)
            if to_open
                @numcenters += 1
                open = true
                (0...points.num).each {|j|
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
        (0...points.num).each {|i|
            costs += points.p[i].cost
        }
        
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

        #work_mem = Array.new
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
        
        #print "x=", x, "\n"
        (k1...k2).each{|i|
            x_cost = dist(points.p[i], points.p[x], points.dim)*points.p[i].weight
            current_cost = points.p[i].cost
            #print "x_cost=", x_cost, ",c_cost=", current_cost, "\n"
            #puts points.p[i].coord
            #puts points.p[x].coord
            if  x_cost < current_cost 
                @switch_membership[i] = 1
                cost_of_opening_x += x_cost - current_cost
            else
                assign = points.p[i].assign
                work_mem[lower + @center_table[assign]] += current_cost - x_cost
            end
        }

        #print "z=", z, "\n"
        #puts @is_center
        (k1...k2).each {|i|
            if @is_center[i]  
                low = z
                (0...@cfg_tnum).each{|p|
                    low += work_mem[@center_table[i]+p*stride]
                    
                }
                #puts work_mem[@center_table[i]]
                #print "low=", low, "\n"
                work_mem[gl_lower+@center_table[i]] = low
                if  low > 0 
                    number_of_centers_to_close += 1  
                    cost_of_opening_x -= low
                end
           end
        }

        work_mem[pid*stride + myK] = number_of_centers_to_close
        work_mem[pid*stride + myK+1] = cost_of_opening_x
        #puts number_of_centers_to_close
        #puts cost_of_opening_x

        if pid==0 
            gl_cost_of_opening_x = z

            (0...@cfg_tnum).each{|p|
                gl_number_of_centers_to_close += Integer(work_mem[p*stride + myK])
                gl_cost_of_opening_x += work_mem[p*stride+myK+1]
            }
        end
        #puts gl_cost_of_opening_x
        
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

            if  pid==0 
                @numcenters = @numcenters + 1 - gl_number_of_centers_to_close
            end
        else 
            if pid==0 
                gl_cost_of_opening_x = 0  
            end
        end
        return -gl_cost_of_opening_x
       
    end
=begin
    def pgain(x, points, z, numcenters)
        puts "pgain begin\n"
        
        number_of_centers_to_close = 0;
        stride = numcenters  + 2

        work_mem = Array.new(stride*2, 0.0)
        myK = numcenters          #K==*numcenters
        cost_of_opening_x = 0   #//my own cost of opening x
        
        count = 0
        (0...points.num).each {|i|
            if @is_center[i]
                @center_table[i] += count 
                count += 1
            end
        }
        work_mem[0] = count
        
        accum = work_mem[0]
        work_mem[0] = 0.0
        
        #  //my *lower* fields
        #double* lower = &work_mem[pid*stride];
        #//global *lower* fields
        #double* gl_lower = &work_mem[nproc*stride];

        (0...points.num).each { |i|
            x_cost = dist(points.p[i], points.p[x], points.dim) * points.p[i].weight
            current_cost = points.p[i].cost
            if x_cost < current_cost
                @switch_membership[i] = true
                cost_of_opening_x += x_cost - current_cost
            else
                assign = points.p[i].assign
                puts assign
                puts @center_table[assign]
                puts @center_table
                work_mem[@center_table[assign]] += current_cost - x_cost
            end
        }                                   
        
        (0...points.num).each { |i|
            if @is_center[i]
                #puts i
                #puts center_table[i]
                low = z + work_mem[center_table[i]]
                if low > 0
                    number_of_centers_to_close += 1
                    cost_of_opening_x -= low
                end
            end
        }
        #use the rest of working memory to store the following
        work_mem[myK] = number_of_centers_to_close
        work_mem[myK+1] = cost_of_opening_x
      
        puts "Cost complete\n"
        
        #// Now, check whether opening x would save cost; if so, do it, and
        #// otherwise do nothing      
        if cost_of_opening_x < 0
            (0...points.num).each { |i|
                close_center = work_mem[center_table[points.p[i].assign]] > 0
                if @switch_membership[i] or close_center
                    #// Either i's median (which may be i itself) is closing,
	                #// or i is closer to x than to its current median  
	                points.p[i].cost = points.p[i].weight * dist(points.p[i], points.p[x], points.dim)
	                points.p[i].assign = x
	            end
	        }
	        (0...points.num).each { |i|
                if @is_center[i] and work_mem[center_table[i]] > 0
                    @is_center[i] = false
                end
            }
            
            is_center[x] = true if  x >= 0 and x < points.num
            numcenters = numcenters + 1 - number_of_centers_to_close
        else
            cost_of_opening_x = 0
        end
        return -cost_of_opening_x
    end
=end   
    
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

            #feasible.shuffle!
            (0...iter).each{|i|
                x = i % numfeasible
                #print "pgain=", pgain(feasible[x], points, z)
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
        #feasible = Array.new(numfeasible, 0)

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
        
        rd = Random.new
        (k1...k2).each { |i|
            ####w = rd.rand(0...65536)/Float(65536)*totalweight;
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
        hiz = loz = 0.0
        numberOfPoints = points.num
        ptDimension = points.dim
        
        bsize = points.num
        k1 = 0
        k2 = points.num

        myhiz = 0
        
        (k1...k2).each{ |kk|
            myhiz += dist(points.p[kk], points.p[0], ptDimension) * points.p[kk].weight
        }
        hiz = myhiz;
        
        loz = 0.0
        z = (hiz + loz) / 2.0
        #/* NEW: Check whether more centers than points! */
        if points.num <= kmax
            #/* just return all points as facilities */
            (k1...k2).each{ |kk|
                points.p[kk].assign = kk
                points.p[kk].cost = 0
            }
            cost = 0
            @kfinal = @numcenters;
            return cost
        end

        #points.p.shuffle!
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
            #points.p.shuffle!
            #print @numcenters,",", kmin, ",", z ,"\n"
            cost = pspeedy(points, z)
            i += 1
        end
        #/* now we begin the binary search for real */
        #/* must designate some points as feasible centers */
        #/* this creates more consistancy between FL runs */
        #/* helps to guarantee correct # of centers at the end */
          #puts "printf pint"
  #prt points
        numfeasible = selectfeasible_fast(points,feasible, kmin)
        (0...points.num).each { |i|
            is_center[points.p[i].assign]= true
        }
        while true
            #/* first get a rough estimate on the FL solution */
            lastcost = cost
            #puts Integer(@ITER* kmax * Math.log(Float(kmax)))
            #puts cost
            #puts z
            #print "fenum=", feasible , "\n"
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
                loz = z
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
            break if ((@numcenters <= kmax) and (@numcenters >= kmin)) or ((loz >= (0.999) * hiz)) 
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
=begin    
    ###########################################################################
    def generateSimData(n, dest, dim, num)
        count = 0
        r = Random.new
        
        (0...num).each {|i|
            break if n <= 0
            (0...dim).times { dest.push(Float(r.rand(0...65536)) / Float(65536)) }
            n -= 1
            count += 1
        }
        return count
    end
    ###########################################################################
    def readSimData(file, dest, dim, num)
        
        open(filename, "rb") do |file|
            num = file.read(4).unpack("F")
            dest.push(num)
        end
        return dest.size
    end
=end    
    ###########################################################################
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
        if @n > 0
            stream = SimStream.new(@n)
        else
            stream = FileStream.new(@inputFile)
        end
        
        #centersize = @clustersize
        #centerBlock = Array.new(centersize * @dim, 0.0)
        @centerIDs = Array.new(@clustersize * @dim, 0)
        
        points = Points.new
        points.dim = @dim
        points.num = @chunksize
        points.p = Array.new
        @chunksize.times{points.p.push(Point.new)}

           
        @centers = Points.new
        @centers.dim = @dim
        @centers.p = Array.new
        @clustersize.times{@centers.p.push(Point.new)}
        
        @centers.num = 0
        (0...@clustersize).each{|i|
            @centers.p[i].coord = Array.new(@dim, 0.0)
            @centers.p[i].weight = 1.0
        }
  
        iDoffset = 0
        
        while true
            
            numRead  = stream.read(points, @dim, @chunksize) 
            #puts sprintf("read %d points\n",numRead)
            #puts block
            
            if stream.ferror or (numRead < @chunksize and (not stream.feof)) 
                puts sprintf("error reading data!\n")
                exit
            end

            points.num = numRead;
            @switch_membership = Array.new(points.num, false)
            @is_center = Array.new(points.num, false)
            @center_table = Array.new(points.num, 0)

            localSearch(points,kmin, kmax)
            
            #puts @switch_membership
            #puts @is_center
            
            #puts @center_table

            
            #puts "local search finished"
            contcenters(points)
            if  @kfinal + @centers.num > @clustersize
                puts "oops! no more space for centers\n"
                exit
            end
            #print @centerIDs, iDoffset, "\n"
            copycenters(points, @centers, @centerIDs, iDoffset)
            iDoffset += numRead;

            break if  stream.feof
        end
        
        @switch_membership = Array.new(@centers.num, false)
        @is_center = Array.new(@centers.num, false)
        @center_table = Array.new(@centers.num, 0)

        localSearch(@centers, kmin, kmax)
        
        #puts "local search on centers are finished"
        contcenters(@centers)
    end
end
#GC::Profiler.enable
#2 5 1 10 10 5 none output.txt
#thread_num, kmin, kmax, dim, n, chunksize, clustersize, input, output
#printf(stderr,"usage: %s k1 k2 d n chunksize clustersize infile outfile nproc\n",
#test
#bench = Streamcluster.new(1, 2, 5, 1, 10, 10, 5, "none", "output.txt")
#simlarge 10 20 128 16384 16384 1000 none output.txt
#bench = Streamcluster.new(1, 10, 20, 128, 16384, 16384, 1000, "none", "output.txt")
#simsmall 10 20 32 4096 4096 1000
#RACE::Detector.start
bench = Streamcluster.new(1, 10, 20, 32, 4096, 4096, 1000, "none", "output.txt")
bench.run

#puts GC::Profiler.total_time
#puts GC::Profiler.result
#GC::Profiler.disable

