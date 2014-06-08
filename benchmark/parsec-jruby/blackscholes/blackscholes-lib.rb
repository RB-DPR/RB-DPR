
# Copyright (c) 2012 

# Black-Scholes
# Analytical method for calculating European Options
#
# 
# Reference Source 1: Options, Futures, and Other Derivatives, 3rd Edition, Prentice 
# Hall, John C. Hull,
# Reference Source 2: blackscholes in PARSEC Benchmark Suite



require "../common/BenchHarness"
#require 'thread'
#require 'rdoc/rdoc'
#define NUM_RUNS 100
require 'detpar'

puts "Num of threads?"
th_num = ARGV[0]
ParLib.init(th_num.to_i)

class OptionData
    attr_accessor :s                # spot price
    attr_accessor :strike           # strike price
    attr_accessor :r                # risk-free inerest rate
    attr_accessor :divq             # dividend rate
    attr_accessor :v                # volatility
    attr_accessor :t                # time to maturity or option exiration in years (1yr = 1.0, 6mos = 0.5, 3mos = 0.25, ..., etc)
    attr_accessor :OptionType       # Option type. "P"=PUT, "C"=CALL
    attr_accessor :divs             # dividend vals
    attr_accessor :DGrefval         # DerivaGem Reference Value
    
    
    def initialize(s, strike, r, divq, v, t, optionType, divs, dGrefval)
        @s, @strike, @r, @divq, @v, @t, @OptionType, @divs, @DGrefval = s, strike, r, divq, v, t, optionType, divs, dGrefval
    end
end

=begin
typedef struct OptionData_ {
        fptype s;          // spot price
        fptype strike;     // strike price
        fptype r;          // risk-free interest rate
        fptype divq;       // dividend rate
        fptype v;          // volatility
        fptype t;          // time to maturity or option expiration in years 
                           //     (1yr = 1.0, 6mos = 0.5, 3mos = 0.25, ..., etc)  
        char OptionType;   // Option type.  "P"=PUT, "C"=CALL
        fptype divs;       // dividend vals (not used in this test)
        fptype DGrefval;   // DerivaGem Reference Value
} OptionData;
=end

class Blackscholes < Benchmark
    attr_accessor :OptionData       #OptionData *data;
    attr_accessor :prices           #fptype *prices;
    attr_accessor :numOptions       #int numOptions;
    attr_accessor :otype            #int    * otype;
    attr_accessor :sptprice         #fptype * sptprice;
    attr_accessor :strike           #fptype * strike;
    attr_accessor :rate             #fptype * rate;
    attr_accessor :volatility       #fptype * volatility;
    attr_accessor :otime            #fptype * otime;
    attr_accessor :numError         #int numError = 0;
                                    #int nThreads;   
    attr_accessor :inv_sqrt_2xPI    #define inv_sqrt_2xPI 0.39894228040143270286
    attr_accessor :NUM_RUNS         #define NUM_RUNS 100 
    
    attr_accessor :inputFile
    attr_accessor :outputFile
    
    #attr_accessor :mutex
       
    def initialize(thread_num, check, input, output)
        super("Blackscholes", thread_num, check)
        
        @OptionData = Array.new
        @prices = Array.new
        @numOptions = 0
        @otype = Array.new
        @sptprice = Array.new
        @strike = Array.new
        @rate = Array.new
        @volatility = Array.new
        @otime = Array.new
        @numError = 0
        @inv_sqrt_2xPI = 0.39894228040143270286
        @NUM_RUNS = 100
        @inputFile = input
        @outputFile = output
        #@mutex = Mutex.new
    end
    
    def cndf( inputX )
        # Check for negative value of inputX
        if inputX < 0.0 
            inputX = -inputX
            sign = 1
        else 
            sign = 0
        end
        
        xInput = inputX

        # Compute NPrimeX term common to both four & six decimal accuracy calcs
        expValues = Math.exp(-0.5 * inputX * inputX)
        xNPrimeofX = expValues
        xNPrimeofX = xNPrimeofX * inv_sqrt_2xPI

        xK2 = 0.2316419 * xInput
        xK2 = 1.0 + xK2
        xK2 = 1.0 / xK2
        xK2_2 = xK2 * xK2
        xK2_3 = xK2_2 * xK2
        xK2_4 = xK2_3 * xK2
        xK2_5 = xK2_4 * xK2

        xLocal_1 = xK2 * 0.319381530
        xLocal_2 = xK2_2 * (-0.356563782)
        xLocal_3 = xK2_3 * 1.781477937
        xLocal_2 = xLocal_2 + xLocal_3
        xLocal_3 = xK2_4 * (-1.821255978)
        xLocal_2 = xLocal_2 + xLocal_3
        xLocal_3 = xK2_5 * 1.330274429
        xLocal_2 = xLocal_2 + xLocal_3

        xLocal_1 = xLocal_2 + xLocal_1
        xLocal   = xLocal_1 * xNPrimeofX
        xLocal   = 1.0 - xLocal;

        outputX  = xLocal;

        if sign == 1
            outputX = 1.0 - outputX
        end

        outputX
    end
    # For debugging    
    def print_xmm(inn, s)
        puts s.to_s + ": " + "% .6f" %inn
    end
    
    def blkSchlsEqEuroNoDiv(sptprice, strike, rate, volatility, time, otype, timet)
        xStockPrice = sptprice
        xStrikePrice = strike
        xRiskFreeRate = rate
        xVolatility = volatility

        xTime = time
        xSqrtTime = Math.sqrt(xTime)

        logValues = Math.log( sptprice / strike )

        xLogTerm = logValues


        xPowerTerm = xVolatility * xVolatility
        xPowerTerm = xPowerTerm * 0.5

        xD1 = xRiskFreeRate + xPowerTerm
        xD1 = xD1 * xTime
        xD1 = xD1 + xLogTerm

        xDen = xVolatility * xSqrtTime
        xD1 = xD1 / xDen
        xD2 = xD1 -  xDen

        d1 = xD1
        d2 = xD2
        #print "d1=", d1 , " "
        #print "d2=", d2, "\n"
        nofXd1 = cndf( d1 )
        nofXd2 = cndf( d2 )
        #print "nofXd1=", nofXd2 , " "
        #print "nofXd1=", nofXd2, "\n"
        futureValueX = strike * ( Math.exp( -(rate)*(time) ) )        
        if otype == false            
            optionPrice = (sptprice * nofXd1) - (futureValueX * nofXd2)
        else  
            negNofXd1 = (1.0 - nofXd1)
            negNofXd2 = (1.0 - nofXd2)
            optionPrice = (futureValueX * negNofXd2) - (sptprice * negNofXd1)
        end
        optionPrice
    end
    
    def start()
        (0...@NUM_RUNS).each{ |j|
            (0...@numOptions).all{ |i|
                # Calling main function to calculate option value based on 
                # Black & Sholes's equation.
                price = blkSchlsEqEuroNoDiv( @sptprice[i], @strike[i], @rate[i], @volatility[i], @otime[i], @otype[i], 0)
                @prices[i] = price
                
                if cfg_check
                    priceDelta = @OptionData[i].DGrefval - price
                    if Math.abs(priceDelta) >= 1e-4 
                        puts sprintf("Error on %d. Computed=%.5f, Ref=%.5f, Delta=%.5f",i, price %@OptionData[i].DGrefval, priceDelta)
                        @numError += 1
                    end
                end
            }
            #print "run ", j, "\n"
        }

        return 0           #return 0
    end
    
    
    ############################################################################################
    ## start 
    ############################################################################################
    def init
        inFile = File.open(@inputFile) if File::exists?(@inputFile)
        if inFile == nil
            puts "ERROR: unable to open file", @inputFile, ".\n"
            return
        end
        #outFile = File.open(outputFile, "w")
        line = inFile.gets
        if line.size == 0
            puts "ERROR: unable to read from file +", @inputFile, ".\n"
            inFile.close
            return
        end    
        @numOptions = Integer(line)
        if @cfg_tnum > @numOptions
            puts "WARNING: Not enough work, reducing number of threads to match number of options.\n"
            @cfg_tnum = @numOptions
        end
        
        #read data from the file
        (0...@numOptions).each { |l|
            line = inFile.gets
            numbers = line.split(/ /)#
            
            if numbers.size != 9
                puts sprintf("ERROR: Unable to read from file `%s'.\n", inputFile)
                inFile.close
                return
            end      
            option = OptionData.new(Float(numbers[0]), Float(numbers[1]), Float(numbers[2]),
                                    Float(numbers[3]), Float(numbers[4]), Float(numbers[5]),
                                    numbers[6], Float(numbers[7]), Float(numbers[8]))
            
            @OptionData.push(option)
            @otype.push(option.OptionType == "P"? true : false)
            @sptprice.push option.s
            @strike.push option.strike
            @rate.push option.r
            @volatility.push option.v
            @otime.push option.t
        }
        inFile.close
        
        puts sprintf("Num of Options: %d\n", @numOptions);
        puts sprintf("Num of Runs: %d\n", @NUM_RUNS); 
   
    end
    
    def statis
        #puts @prices
        outFile = File.open(@outputFile, "w")
        outFile.puts @numOptions
        (0...@numOptions).each {|i|
            outFile.puts sprintf("%.18f", @prices[i])
        }
        outFile.close
        
        if @cfg_check
            puts sprintf("Num Errors: %d\n", @numError)
        end
    end
end

#RACE::Detector.start
#GC::Profiler.enable
#bench = Blackscholes.new(1, true, "in_4.txt", "out_4.txt")
#1 thread
bench = Blackscholes.new(1, true, "in_64K.txt", "out_64K.txt")
#2 thread
#bench = Blackscholes.new(1, true, "in_4K.txt", "out_4K.txt")
bench.run
exit
#puts GC::Profiler.result
#puts GC::stat
#GC::Profiler.disable

