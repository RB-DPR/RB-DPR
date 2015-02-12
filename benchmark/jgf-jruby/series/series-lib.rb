#!/usr/bin/env ruby
# Copyright (c) 2012 
# Series
# produced by Weixing Ji
# email: bitjwx@gmail.com
# 
# Reference Source 1: Java Grande Forum Benchmark Suite - Thread Version 1.0

require "../../parsec-jruby/common/BenchHarness"
require 'detpar'

puts "threads?"
th = ARGV[0]

ParLib.init(th.to_i)


class Series < Benchmark
    attr_accessor :size
    attr_accessor :datasizes
    attr_accessor :ref
    attr_accessor :array_rows
    attr_accessor :TestArray
    
    def initialize(thread_num, size)
        super("Series", thread_num, false)
        @size = size
        @datasizes = [10000,100000,1000000]
        @ref = [[2.8729524964837996, 0.0], [1.1161046676147888, -1.8819691893398025],
                [0.34429060398168704, -1.1645642623320958], [0.15238898702519288, -0.8143461113044298]]
        @array_rows = 0
        
    end
=begin
/*
* thefunction
*
* This routine selects the function to be used in the Trapezoid
* integration. x is the independent variable, omegan is omega * n,
* and select chooses which of the sine/cosine functions
* are used. Note the special case for select=0.
*/
=end
    def thefunction(x, omegan, select) #independent variable, omega * term, choose type
        result = case select
        when 0
            result = (x + 1.0) ** x
        when 1
            result = ((x + 1.0) ** x) * Math.cos(omegan * x)
        when 2
            result = ((x + 1.0) ** x) * Math.sin(omegan * x)
        else
            result = 0.0    
        end
        
        result
    end
    
=begin    
/*
* TrapezoidIntegrate
*
* Perform a simple trapezoid integration on the function (x+1)**x.
* x0,x1 set the lower and upper bounds of the integration.
* nsteps indicates num of trapezoidal sections.
* omegan is the fundamental frequency times the series member #.
* select = 0 for the A[0] term, 1 for cosine terms, and 2 for
* sine terms. Returns the value.
*/
=end

    def TrapezoidIntegrate(x0, x1, nsteps, omegan, select) #lower bound, upper bound, number of steps, omega*n, term type
        x = x0
        dx = (x1 - x0) / nsteps
        rvalue = thefunction(x0, omegan, select) / 2.0
        if nsteps != 1 then
            nsteps -= 1
            
            nsteps -= 1
            while nsteps > 0 do
                x += dx
                rvalue += thefunction(x, omegan, select)
                nsteps -= 1
            end
        end
        rvalue = (rvalue + thefunction(x1,omegan,select) / 2.0) * dx
        rvalue    
    end
    def init
        @array_rows = @datasizes[@size]
        @TestArray = [Array.new(@array_rows), Array.new(@array_rows)]
    end
    
    def start
        @TestArray[0][0] = TrapezoidIntegrate(0.0, 2.0, 1000, 0.0, 0) / 2.0
        @TestArray[1][0] = 0.0 

        omega = 3.1415926535897932
        ilow = 1
        iupper = @array_rows
        (ilow...iupper).all{|i|
            @TestArray[0][i] = TrapezoidIntegrate(0.0, 2.0, 1000, omega * i, 1)
            @TestArray[1][i] = TrapezoidIntegrate(0.0, 2.0, 1000, omega * i, 2)
        }

        # validation
		# Validation may fail due to random number generation and floating-point calculation
        for i in 0...4 
            for j in 0...2 
                error = @TestArray[j][i] - @ref[i][j]
                if error > 1.0e-12 then
                    puts "Validation failed for cofficient #{j},#{i}"
                    puts "Computed value = #{@TestArray[j][i]}"
                    puts "Reference value = #{@ref[i][j]}"
                end
           end        
        end
    end
end

#simall size(A in jgf)
bench = Series.new(1, 0)
bench.run

exit
