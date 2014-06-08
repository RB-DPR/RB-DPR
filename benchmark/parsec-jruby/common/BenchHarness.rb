#!/usr/bin/env ruby

class Benchmark
    attr_accessor :bench_name
    attr_accessor :start_time
    attr_accessor :init_time
    attr_accessor :end_time
    
    attr_accessor :cfg_tnum
    attr_accessor :cfg_check
    
    def initialize(name, tnum, check)
        @bench_name = name
        @cfg_tnum = tnum
        @cfg_check = false
    end
    
    def init
    end
    
    def start()
    end 
    
    def statis
    end
    
    def run()
        puts "Start to run " + @bench_name #+ " with p = " + cfg_tnum.to_s
        
         
        program_start_time = Time.now
        init
        
        init_end_time = Time.now

        start() 
        
        compute_end_time = Time.now
        
        statis()
        
        program_end_time = Time.now
        
        total_time = program_end_time - program_start_time
        init_time = init_end_time - program_start_time
        compute_time = compute_end_time - init_end_time
        
        puts sprintf("Program terminates in %.6f, init time=%.6f(%2.f%%), computing time=%.6f(%2.f%%)", 
            total_time, init_time, init_time/total_time*100, compute_time, compute_time/total_time*100)
    end
end

#bench = Benchmark.new("test", 1)
#bench.run

