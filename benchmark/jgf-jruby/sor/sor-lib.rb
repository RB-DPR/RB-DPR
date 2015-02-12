#!/usr/bin/env ruby
# Copyright (c) 2012 
# SOR
# produced by Weixing Ji
# email: bitjwx@gmail.com
# 
# Reference Source 1: Java Grande Forum Benchmark Suite - Thread Version 1.0

require "../../parsec-jruby/common/BenchHarness"

require 'detpar'

puts "threads?"
th = ARGV[0]

ParLib.init(th.to_i)


class SOR < Benchmark
    attr_accessor :size
    attr_accessor :Gtotal
    attr_accessor :G
    attr_accessor :datasizes
    attr_accessor :JACOBI_NUM_ITER
    attr_accessor :RANDOM_SEED
    attr_accessor :R
    attr_accessor :refval
       
    def initialize(thread_num, size)
        super("SOR", thread_num, false)
        @Gtotal = 0.0
        @size = size
        @datasizes = [1000,1500,2000]
        @JACOBI_NUM_ITER = 100
        @RADOM_SEED = 10101010
        @refval = [0.498574406322512,1.1234778980135105,1.9954895063582696]
        puts "after initialize"
    end
    
    def RandomMatrix(m, n)
        @G = Array.new(m)
        puts @G.length
        
        for i in 0...@G.length
            @G[i] = Array.new(n)
        end 
        
        for i in 0...@G.length
            for j in 0...@G[i].length
                @G[i][j] = rand(1000000) * 1e-6
            end
        end
    end
    
    def init
        
        RandomMatrix(@datasizes[size], @datasizes[size])
    end
    
    def start
        omega = 1.25
        m = @G.length
        n = @G[0].length
        
        omega_over_four = omega * 0.25
        one_minus_omega = 1.0 - omega
        
        mm1 = m - 1
        nm1 = n - 1
        ilow = 1
        iupper = m;

        for p in 0...(2*@JACOBI_NUM_ITER)
            ((ilow+(p%2))...iupper).all{|i|
                next if p%2 == i%2
                gi = @G[i]
                gim1 = @G[i - 1]
                
                if i == 1 then
                    gip1 = @G[i + 1]
                    
                   j = 1
                    while j < nm1 do
                        gi[j] = omega_over_four * (gim1[j] + gip1[j] + gi[j - 1] + gi[j + 1]) + one_minus_omega * gi[j]
                        j += 2
                    end

                elsif i == mm1 then
                    gim2 = @G[i - 2]
                    j = 1
                    while j < nm1 do
                        if (j+1) != nm1 then
                            gim1[j+1] = omega_over_four * (gim2[j + 1] + gi[j + 1] + gim1[j] + gim1[j + 2]) + one_minus_omega * gim1[j+1]
                        end
                        
                        j += 2
                    end
                   
                else
                    gip1 = @G[i+1]
                    gim2 = @G[i - 2]
                    
                    j = 1
                    while j < nm1 do
                        gi[j] = omega_over_four * (gim1[j] + gip1[j] + gi[j-1] + gi[j+1]) + one_minus_omega * gi[j]
                        
                        if (j + 1) != nm1 then
                            gim1[j + 1] = omega_over_four * (gim2[j+1] + gi[j+1] + gim1[j] + gim1[j+2]) + one_minus_omega * gim1[j+1]
                        end
                        j += 2   
                    end
                end
            }
        end
        
        for i in 1...nm1 
            for j in 1...nm1
                @Gtotal += @G[i][j]
            end
        end
        
        dev = (@Gtotal - refval[size]).abs;
        if dev > 1.0e-12 then
            puts "Validation failed"
            puts "Gtotal = #{@Gtotal}  #{dev}  #{@size}"
        end
    end
end

#simall size(A in jgf)
bench = SOR.new(1, 0)

bench.run
exit

