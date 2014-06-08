#!/usr/bin/env ruby
# Copyright (c) 2012 
# SparseMatmult
# produced by Weixing Ji
# email: bitjwx@gmail.com
# 
# Reference Source 1: Java Grande Forum Benchmark Suite - Thread Version 1.0

require "../../parsec-jruby/common/BenchHarness"
#require "D:/research/jruby-1.6.7.2/src/org/jruby/benchmark/parsec-jruby/common/BenchHarness"

require 'detpar'

puts "Thread Num.:"
th = ARGV[0]
ParLib.init(th.to_i)


class Sparse < Benchmark
    attr_accessor :RANDOM_SEED
    attr_accessor :datasizes_M
    attr_accessor :datasizes_N
    attr_accessor :datasizes_nz
    attr_accessor :SPARSE_NUM_ITER
    attr_accessor :R
    attr_accessor :x
    attr_accessor :y
    attr_accessor :val
    attr_accessor :col
    attr_accessor :row
    attr_accessor :lowsum
    attr_accessor :highsum  
    attr_accessor :ytotal
    attr_accessor :rowarray
    def initialize(thread_num, size)
        super("Sparse", thread_num, false)
        @size = size
        @datasizes_M = [50000,100000,500000]
        @datasizes_N = [50000,100000,500000]
        @datasizes_nz = [250000,500000,2500000]
        @RANDOM_SEED = 10101010
        @SPARSE_NUM_ITER = 200
        @SPARSE_NUM_ITER = 50
        #@R = Random.new(@RANDOM_SEED)
        @ytotal = 0.0
    end
    
    def RandomVector(size, r)
        a = Array.new(size)
        for i in 0...size
            #a[i] = r.rand() * 1e-6
            a[i] = rand() * 1e-6
            #a[i] = i * 1e-6
        end
        a        
    end
    
    
    def init
        @x = RandomVector(@datasizes_N[@size], @R)
        @y = Array.new(@datasizes_M[@size], 0.0)
        @val = Array.new(@datasizes_nz[@size])
        @col = Array.new(@datasizes_nz[@size])
        @row = Array.new(@datasizes_nz[@size])
        @rowarray = Array.new(@datasizes_M[@size])
        
        for i in 0...@datasizes_M[@size]
            @rowarray[i] = Array.new()
        end
        
        for i in 0...@datasizes_nz[@size]
            #@row[i] = @R.rand(@datasizes_M[@size])
            @row[i] = rand(@datasizes_M[@size])
            #@col[i] = @R.rand(@datasizes_M[@size])
            @col[i] = rand(@datasizes_M[@size])
            #@val[i] = @R.rand() 
            @val[i] = rand()   
            
            #@row[i] = i % (@datasizes_M[@size])
            #@col[i] = i % (@datasizes_M[@size])
            #@val[i] = 1    
            
            @rowarray[@row[i]].push(i)
        end
        @x.markReadOnly
        @val.markReadOnly
        @col.markReadOnly
        @rowarray.markReadOnly
        self.markReadOnly
    end
    
    def start
        for reps in 0...@SPARSE_NUM_ITER
            #for i in 0...@datasizes_M[@size]
            (0...@datasizes_M[@size]).all{|i|
                currrow = @rowarray[i]
                #puts currrow.length
                for j in 0...currrow.length
                    index = currrow[j]
                    @y[i] += @x[@col[index]] * @val[index]      
                end
            }
            #end
=begin
           #for i in 0...@datasizes_M[@size]
                for j in 0...@datasizes_nz[@size]
                    #if @row[j] == i then
                        @y[@row[j]] += @x[@col[j]] * @val[j]
                    #end
                end
           #end             
=end
            #puts reps
        end
        
        return
        nz = @val.length
        for i in 0...@datasizes_nz[@size]
            @ytotal += @y[@row[i]]
        end
        
        for i in 0...10 
            puts @y[i]
        end
        refval = [75.02484945753453,150.0130719633895,749.5245870753752]
        dev = (@ytotal - refval[@size]).abs
        if dev > 1.0e-10 then
            puts "Validation failed"
            puts "ytotal = #{@ytotal}, dev = #{dev}, size = #{@size}"     
        end
    end
end

#RACE::Detector.start
#simall size(A in jgf)
bench = Sparse.new(1, 0)

bench.run
exit
