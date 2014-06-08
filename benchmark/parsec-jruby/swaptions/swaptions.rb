# Copyright (c) 2012 
# swaptions
# Routines to compute various security prices using HJM framework (via Simulation).
# 
# 
# Reference Source 1: swaptions in PARSEC Benchmark Suite

require 'matrix'
require "../common/BenchHarness"
require 'rdoc/rdoc'

class Parm
    attr_accessor :Id
    attr_accessor :dSimSwaptionMeanPrice
    attr_accessor :dSimSwaptionStdError
    attr_accessor :dStrike
    attr_accessor :dCompounding
    attr_accessor :dMaturity
    attr_accessor :dTenor
    attr_accessor :dPaymentInterval
    attr_accessor :iN
    attr_accessor :dYears
    attr_accessor :iFactors
    attr_accessor :pdYield
    attr_accessor :ppdFactors
end



A = [2.50662823884, -18.61500062529, 41.39119773534, -25.44106049637]
B = [-8.47351093090, 23.08336743743, -21.06224101826, 3.13082909833]
C = [0.3374754822726147, 0.9761690190917186, 0.1607979714918209,
     0.0276438810333863, 0.0038405729373609, 0.0003951896511919,
     0.0000321767881768, 0.0000002888167364, 0.0000003960315187]
                
def CumNormalInv(u)
    x = u - 0.5
    if (x).abs < 0.42 
        r = x * x
        r = x * ((( A[3]*r + A[2]) * r + A[1]) * r + A[0])/
          ((((B[3] * r+ B[2]) * r + B[1]) * r + B[0]) * r + 1.0)
        return r
    end
    
    r = u
    r = 1.0 - u if x > 0.0  
    r = Math.log(-Math.log(r))
    r = C[0] + r * (C[1] + r * 
       (C[2] + r * (C[3] + r * 
       (C[4] + r * (C[5] + r * (C[6] + r * (C[7] + r*C[8])))))));
    r = -r if x < 0.0
    
    return r
end



def Discount_Factors_Blocking(pdDiscountFactors, iN, dYears, pdRatePath, bs)
    ddelt = Float(dYears/iN)
	pdexpRes = Array.new((iN-1) * bs, 0.0)
	(0..(iN-1)*bs-1).each{|j|
	    pdexpRes[j] = -pdRatePath[j]*ddelt
	}
	(0..(iN-1)*bs-1).each{|j|
	    pdexpRes[j] = Math.exp(pdexpRes[j])
	}
	
	(0...(iN)*bs).each{|i|
	    pdDiscountFactors[i] = 1.0
	}
	
	(1..iN-1).each{|i|
	    (0...bs).each{|b|
	        (0..i-1).each{|j|
	            pdDiscountFactors[i*bs + b] *= pdexpRes[j*bs + b]
	        }
	    }
	}
    
    return 1
end

class Swaptions < Benchmark
    attr :swaptionNum
    attr :simulationNum
    attr :iN
    attr :dYears
    attr :iFactors   
    attr :swaptions
      
    def initialize(thread_num, swaptions, simulations)
        super("Swaptions", thread_num, false)
        @swaptionNum = swaptions
        @simulationNum = simulations
        
        @iN = 11
        @dYears = 5.5
        @iFactors = 3
        
    end
    def init
        factors = [Array.new(10, 0.01), 
            [0.009048, 0.008187, 0.007408, 0.006703, 0.006065, 0.005488, 0.004966, 0.004493, 0.004066, 0.003679],
            [0.001000, 0.000750, 0.000500, 0.000250, 0.000000, -0.000250, -0.000500, -0.000750, -0.001000, -0.001250]]
        
        @swaptions = Array.new
        @swaptionNum.times { @swaptions.push(Parm.new)}    
        
        
        (0...@swaptionNum).each {|i|
            s = @swaptions[i]
            s.Id = i
            s.iN = @iN
            s.iFactors = @iFactors
            s.dYears = @dYears
            s.dStrike = 0.1
            s.dCompounding = 0
            s.dMaturity = 1
            s.dTenor = 2.0
            s.dPaymentInterval = 1.0 
            s.pdYield = Array.new(@iN, 0.0)
            s.pdYield[0] = 0.1
            (1...s.iN).each{|j|
                s.pdYield[j] = s.pdYield[j - 1] + 0.005
            }
            s.ppdFactors = Array.new
            s.iFactors.times{ s.ppdFactors.push(Array.new(s.iN - 1, 0.0)) }
            (0...s.iFactors).each{|k|
                (0...s.iN-1).each{|j|
                    s.ppdFactors[k][j] = factors[k][j]
                    #puts s.ppdFactors[k][j]
                }
            }
        }
        
#        puts "-------------------"
    end
    
    def start
        pdSwaptionPrice = Array.new(2, 0.0)
        (0...@swaptionNum).each {|i|
            iSuccess = HJM_Swaption_Blocking(pdSwaptionPrice,  @swaptions[i].dStrike, 
                                       @swaptions[i].dCompounding, @swaptions[i].dMaturity, 
                                       @swaptions[i].dTenor, @swaptions[i].dPaymentInterval,
                                       @swaptions[i].iN, @swaptions[i].iFactors, @swaptions[i].dYears, 
                                       @swaptions[i].pdYield, @swaptions[i].ppdFactors,
                                       100, @simulationNum, 16)
            if not iSuccess
                puts "Failed for HJM_Swaption_Blocking"
            end
            @swaptions[i].dSimSwaptionMeanPrice = pdSwaptionPrice[0]
            @swaptions[i].dSimSwaptionStdError = pdSwaptionPrice[1]
        }
    end
   
    
    def HJM_Swaption_Blocking(pdSwaptionPrice, dStrike, dCompounding,dMaturity, 
        dTenor, dPaymentInterval, iN, iFactors, dYears, pdYield, ppdFactors, iRndSeed, lTrials, bs)
        iSuccess = 0
        ddelt = Float(dYears/iN)
        iFreqRatio = Integer(dPaymentInterval/ddelt + 0.5)
        dStrikeCont = 0
        if dCompounding == 0
            dStrikeCont = dStrike
        else
            dStrikeCont = (1/dCompounding) * Math.log(1 + dStrike * dCompounding)
        end
        
        iSwapVectorLength = 0

        hJMPath = Array.new
        iN.times{hJMPath.push(Array.new(iN, 0.0))}
        #hJMPath = Array.new(iN, Array.new(iN, 0.0))
        ppdHJMPath = Array.new
        iN.times{ppdHJMPath.push(Array.new(iN*bs, 0.0))}
        #ppdHJMPath = Array.new(iN, Array.new(iN*bs, 0.0))
        pdForward = Array.new(iN, 0.0)
        ppdDrifts = Array.new
        iFactors.times{ppdDrifts.push(Array.new(iN-1, 0.0))}
        #ppdDrifts = Array.new(iFactors, Array.new(iN-1, 0.0))
        pdTotalDrift = Array.new(iN - 1, 0.0)
  
        pdPayoffDiscountFactors = Array.new(iN * bs, 0.0)
        pdDiscountingRatePath = Array.new(iN * bs, 0.0)
        iSwapVectorLength = Integer(iN - dMaturity/ddelt + 0.5)
        pdSwapRatePath = Array.new(iSwapVectorLength * bs, 0.0)
        pdSwapDiscountFactors  = Array.new(iSwapVectorLength * bs, 0.0)
        pdSwapPayoffs = Array.new(iSwapVectorLength, 0.0)
        iSwapStartTimeIndex = Integer(dMaturity/ddelt + 0.5)
        iSwapTimePoints = Integer(dTenor/ddelt + 0.5)
        dSwapVectorYears = Float(iSwapVectorLength * ddelt)
        
        (0...iSwapVectorLength).each {|i|
            pdSwapPayoffs[i] = 0.0
        }
        i = iFreqRatio
        while i <=iSwapTimePoints
            pdSwapPayoffs[i] = Math.exp(dStrikeCont*dPaymentInterval) - 1 if i != iSwapTimePoints
            pdSwapPayoffs[i] = Math.exp(dStrikeCont*dPaymentInterval) if i == iSwapTimePoints
            i += iFreqRatio
        end
        
        iSuccess = HJM_Yield_to_Forward(pdForward, iN, pdYield)
        return iSuccess if iSuccess != 1
        
                
        iSuccess = HJM_Drifts(pdTotalDrift, ppdDrifts, iN, iFactors, dYears, ppdFactors)
        return iSuccess if iSuccess != 1
        dSumSimSwaptionPrice = 0.0
        dSumSquareSimSwaptionPrice = 0.0
        
        
        l = 0
        lRndSeed = Array.new(1, iRndSeed)
        while l < lTrials
            iSuccess = HJM_SimPath_Forward_Blocking(ppdHJMPath, iN, iFactors, dYears, pdForward, pdTotalDrift,ppdFactors, lRndSeed, bs)
            return iSuccess if iSuccess != 1
            (0...iN).each {|i|
                (0...bs).each{|b|
                    pdDiscountingRatePath[bs * i + b] = ppdHJMPath[i][0 + b]
                }
            }              
            
            iSuccess = Discount_Factors_Blocking(pdPayoffDiscountFactors, iN, dYears, pdDiscountingRatePath, bs)
            return iSuccess if iSuccess != 1
            
            (0...iSwapVectorLength).each {|i|
                (0...bs).each { |b|
	                pdSwapRatePath[i * bs + b] = ppdHJMPath[iSwapStartTimeIndex][i * bs + b]
	            }
	        }
	        iSuccess = Discount_Factors_Blocking(pdSwapDiscountFactors, iSwapVectorLength, dSwapVectorYears, pdSwapRatePath, bs)
	        return iSuccess if iSuccess != 1
	        
	        (0...bs).each {|b|
	            dFixedLegValue = 0.0
	            (0...iSwapVectorLength).each {|i|
	                dFixedLegValue += pdSwapPayoffs[i] * pdSwapDiscountFactors[i*bs + b]
	            }
		        dSwaptionPayoff = [dFixedLegValue - 1.0, 0].max
                dDiscSwaptionPayoff = dSwaptionPayoff*pdPayoffDiscountFactors[iSwapStartTimeIndex * bs + b]
                dSumSimSwaptionPrice += dDiscSwaptionPayoff
                dSumSquareSimSwaptionPrice += dDiscSwaptionPayoff * dDiscSwaptionPayoff
           }
           
           #puts dSumSimSwaptionPrice
           
           l += bs
        end
        
        dSimSwaptionMeanPrice = dSumSimSwaptionPrice/lTrials

        dSimSwaptionStdError = Math.sqrt((dSumSquareSimSwaptionPrice - dSumSimSwaptionPrice * dSumSimSwaptionPrice / lTrials)/
			      (lTrials-1.0))/Math.sqrt((Float(lTrials)))
		
        pdSwaptionPrice[0] = dSimSwaptionMeanPrice
        pdSwaptionPrice[1] = dSimSwaptionStdError
        #puts pdSwaptionPrice
        iSuccess = 1
        return iSuccess
    end
    
    def HJM_Yield_to_Forward(pdForward, iN,	pdYield)
        pdForward[0] = pdYield[0]
        
        (1...iN).each{|i|
            pdForward[i] = (i+1)*pdYield[i] - i*pdYield[i-1]
        }
        iSuccess=1;
	    return iSuccess;
    end
    
    def HJM_Drifts(pdTotalDrift, ppdDrifts,	iN, iFactors, dYears, ppdFactors)
        ddelt = Float(dYears/iN)
        
        (0...iFactors).each { |i|
            ppdDrifts[i][0] = Float(0.5 * ddelt * (ppdFactors[i][0])*(ppdFactors[i][0]))
            #puts   (ppdDrifts[i][0]).class
             #print ppdDrifts[i][0],",", i, ",0\n"
        }
        
          
        
        (0...iFactors).each {|i|
            (1...iN - 1).each {|j|
                ppdDrifts[i][j] = 0
                (0...j).each {|l|
                    ppdDrifts[i][j] -= ppdDrifts[i][l]
                }
                
			    dSumVol=0
			    (0..j).each {|l|
			        dSumVol += ppdFactors[i][l]
			    }
			    ppdDrifts[i][j] += 0.5*ddelt*(dSumVol)*(dSumVol)
			    
			    #print  ppdDrifts[i][j], ",", i, ",", j, "\n"
			    
		    }
		}
		
=begin
		(0...3).each{|i|
		    (0...10).each{|j|
		        print i,",", j, "=", ppdDrifts[i][j], "\n"
		    }
		}
=end		
		#puts"---------------------------"
		(0..iN-2).each {|i|
		    pdTotalDrift[i]=0
		    (0...iFactors).each {|j|
		        pdTotalDrift[i]+= ppdDrifts[j][i]
		        #print pdTotalDrift[i], ",[",j,",",i,"]=", ppdDrifts[j][i], "\n"
		    }
		}
		
		#puts"---------------------------"
	    return 1
    end
    
    def HJM_SimPath_Forward_Blocking(ppdHJMPath, iN, iFactors, dYears, pdForward, pdTotalDrift, ppdFactors,	lRndSeed, bs)
	    ddelt = Float(dYears/iN)
	    sqrt_ddelt = Math.sqrt(ddelt)
	    pdZ = Array.new
	    iFactors.times{pdZ.push(Array.new(iN * bs, 0.0))}
	    #pdZ = Array.new(iFactors, Array.new(iN * bs, 0.0))
	    randZ = Array.new
	    iFactors.times{randZ.push(Array.new(iN * bs, 0.0))}
	    #randZ = Array.new(iFactors, Array.new(iN * bs, 0.0))
	    
	    (0...bs).each{|b|
	        (0...iN).each{|j|
	            ppdHJMPath[0][bs * j + b] = pdForward[j] 
                (1..iN - 1).each {|i|
                    ppdHJMPath[i][bs*j + b]=0
                }
            }
        }
        
        (0...bs).each{|b|
            (0...1).each{|s|
                (1...iN).each{|j|
                    (0...iFactors).each{|l|
                        randZ[l][bs*j + b + s] = RanUnif(lRndSeed)
                    }
                }
            }
        }
        serialB(pdZ, randZ, bs, iN, iFactors)
        
        (0...bs).each {|b|
            (1...iN).each {|j|
                (0..iN-(j+1)).each{|l|
                    dTotalShock = 0
                    (0...iFactors).each{|i|
                        dTotalShock += ppdFactors[i][l]* pdZ[i][bs*j + b]
                    }
                    ppdHJMPath[j][bs*l+b] = ppdHJMPath[j-1][bs*(l+1)+b]+ pdTotalDrift[l]*ddelt + sqrt_ddelt*dTotalShock
                }
            }
        }
	    return 1
    end

    def RanUnif(s)
        ix = s[0]
        k1 = ix / 127773
        ix = 16807 * ( ix - k1 * 127773 ) - k1 * 2836
        ix = ix + 2147483647 if ix < 0 
        s[0] = ix
        dRes = ix * 4.656612875e-10
        
        return dRes
    end
    
    def serialB(pdZ, randZ, bs, iN, iFactors)
        (0...iFactors).each{|l|
            (0...bs).each {|b|
                (1...iN).each {|j|
                    pdZ[l][bs*j + b]= CumNormalInv(randZ[l][bs*j + b])
                }
            }
        }
    end
end
#RACE::Detector.config(8, 0, 3, 4)
#RACE::Detector.start
#RACE::Profiler.enable

#GC::Profiler.enable
#paras:thread num, number of swaptions, number of simulations
#test input
#bench = Swaptions.new(1, 1, 5)
#simsmall input
bench = Swaptions.new(1, 16, 5000)
#simlarge input
#bench = Swaptions.new(1, 64, 20000)
bench.run

#RACE::Profiler.disable
#RACE::Detector.stop
#RACE::Profiler.report


#puts GC::Profiler.total_time
#puts GC::Profiler.result
#GC::Profiler.disable


