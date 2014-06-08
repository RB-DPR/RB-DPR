#!/usr/bin/env ruby
# Copyright (c) 2012 
# IDEA
# produced by Weixing Ji
# email: bitjwx@gmail.com
# 
# Reference Source 1: Java Grande Forum Benchmark Suite - Thread Version 1.0

require "../../parsec-jruby/common/BenchHarness"
require 'rdoc/rdoc'

class Crypt < Benchmark
    attr :size
    attr :datasizes
    attr :array_rows
    attr :plain1        # Buffer for plaintext data.
    attr :crypt1        # Buffer for encrypted data.
    attr :plain2        # Buffer for decrypted data.
    attr :userkey       # Key for encryption/decryption.
    attr :Z             # Encryption subkey (userkey derived).
    attr :DK            # Decryption subkey (userkey derived).
    
    
    def initialize(thread_num, size)
        super("Crypt", thread_num, false)
        @size = size
        @datasizes=[3000000,20000000,50000000]
    end
    
    def javaint2byte(num)
        carry = 0
        if (num & 0x80) == 0 then
            return (num & 0xff)
        else
           num = num & 0x7f
           for i in 0...7
                num = num ^ ( 1 << i) 
           end
           num += 1
           num = -num
           return num
       end
    end
    
    def IDEARunner(text1, text2, key)
        ilow = 0
        iupper = text1.length
        i1 = ilow
        i2 = ilow
        
        i = ilow
        while i < iupper do
            ik = 0
            r = 8
            x1 = text1[i1] & 0xff
            i1 += 1          
            x1 |= (text1[i1] & 0xff) << 8
            i1 += 1  
            x2 = text1[i1] & 0xff
            i1 += 1
            x2 |= (text1[i1] & 0xff) << 8
            i1 += 1
            x3 = text1[i1] & 0xff
            i1 += 1
            x3 |= (text1[i1] & 0xff) << 8
            i1 += 1
            x4 = text1[i1] & 0xff
            i1 += 1
            x4 |= (text1[i1] & 0xff) << 8
            i1 += 1
         
           
            begin
                x1 = ( x1 * key[ik] % 0x10001 & 0xffff)
                ik += 1
                x2 = x2 + key[ik] & 0xffff
                ik += 1
                x3 = x3 + key[ik] & 0xffff
                ik += 1
                x4 = (x4 * key[ik] % 0x10001 & 0xffff)
                ik += 1
                t2 = x1 ^ x3
                t2 = (t2 * key[ik] % 0x10001 & 0xffff)
                ik += 1
                t1 = t2 + (x2 ^ x4) & 0xffff
                t1 = ( t1 * key[ik] % 0x10001 & 0xffff)
                ik += 1
                t2 = t1 + t2 & 0xffff
                x1 ^= t1
                x4 ^= t2
                t2 ^= x2
                x2 = x3 ^ t1

                x3 = t2
                
                r -= 1
            end while  r != 0   
            
            x1 = (x1 * key[ik] % 0x10001 & 0xffff)
            ik += 1
            x3 = x3 + key[ik] & 0xffff
            ik += 1
            x2 = x2 + key[ik] & 0xffff
            ik += 1
            x4 =(x4 * key[ik] % 0x10001 & 0xffff)
            ik += 1        
            
            text2[i2] = javaint2byte(x1)
            i2 += 1
            text2[i2] = javaint2byte(x1 >> 8)
            i2 += 1
            text2[i2] = javaint2byte(x3)
            i2 += 1
            text2[i2] = javaint2byte(x3 >> 8)
            i2 += 1
            text2[i2] = javaint2byte(x2)
            i2 += 1
            text2[i2] = javaint2byte(x2 >> 8)
            i2 += 1
            text2[i2] =  javaint2byte(x4)
            i2 += 1
            text2[i2] =  javaint2byte(x4 >> 8)
            i2 += 1
            
            i += 8                       
        end
    end
    
    def mul(a, b)
        result = 0
        if a != 0 then
            if b != 0 then
                p = a * b
                b = p & 0xFFFF
                a = p >> 16
                result = (b - a + (b < a ? 1 : 0) & 0xFFFF)
            else
                result = ((1 - a) & 0xFFFF)
            end
        else
            result = ((1 - b) & 0xFFFF)
        end
        result
    end

    def inv(x)
        result = 0 
        if x <= 1 then
            return x
        end
        
        t1 = 0x10001 / x
        y = 0x10001 % x
        if y == 1 then
            return ((1 - t1) & 0xffff)
        end
        
        t0 = 1
        begin 
            q = x / y
            x = x % y
            t0 += q * t1
            return t0 if x == 1
            q = y / x
            y = y % x
            t1 += q * t0
        end while y != 1
        
        return ((1 - t1) & 0xffff)
    end

    def calcEncryptKey()
        for i in 0...52 
            @Z[i] = 0
        end
        for i in 0...8 
            @Z[i] = @userkey[i] & 0xffff
        end
        
        for i in 8...52
            j = i % 8
            if j < 6 then
                @Z[i] = ((@Z[i - 7] >> 9) | (@Z[i - 6] << 7)) & 0xffff
                next
            end
            
            if j == 6 then
                @Z[i] = ((@Z[i - 7] >> 9) | (@Z[i - 14] << 7)) & 0xffff
                next
            end
            
            @Z[i] = ((@Z[i - 15] >> 9) | (@Z[i - 14] << 7)) & 0xffff            
        end
    end
    
    def calcDecryptKey
        t1 = inv(@Z[0])
        t2 = -@Z[1] & 0xffff
        t3 = -@Z[2] & 0xffff
        
        @DK[51] = inv(@Z[3])
        @DK[50] = t3
        @DK[49] = t2
        @DK[48] = t1
        
        j = 47
        k = 4
        
        for i in 0...7
            t1 = @Z[k] & 0xffff
            k += 1
            @DK[j] = @Z[k] & 0xffff
            j -= 1
            k += 1
            @DK[j] = t1
            j -= 1
            t1 = inv(@Z[k])            
            k += 1
            t2 = -@Z[k] & 0xffff
            k += 1
            t3 = -@Z[k] & 0xffff
            k += 1
            @DK[j] = inv(@Z[k])
            k += 1
            j -= 1
            @DK[j] = t2
            j -= 1
            @DK[j] = t3
            j -= 1
            @DK[j] = t1
            j -= 1
        end
        
        t1 = @Z[k]
        k += 1
        @DK[j] = @Z[k]
        j -= 1
        k += 1
        @DK[j] = t1
        j -= 1
        t1 = inv(@Z[k])
        k += 1
        t2 = -@Z[k] & 0xffff
        k += 1
        t3 = -@Z[k] & 0xffff
        k += 1
        @DK[j] = inv(@Z[k])
        j -= 1
        k += 1
        @DK[j] = t3
        j -= 1
        @DK[j] = t2
        j -= 1
        @DK[j] = t1
        j -= 1
    end
    
    def buildTestData
        @plain1 = Array.new(@array_rows)
        @crypt1 = Array.new(@array_rows)
        @plain2 = Array.new(@array_rows)

        rndnum = Random.new(136506717)
        @userkey = Array.new(8)
        @Z = Array.new(52)
        @DK = Array.new(52)
        
        for i in 0...8
            @userkey[i] = rndnum.rand(65536)
            #@userkey[i] = i
        end
        
        calcEncryptKey()
        calcDecryptKey()

        for i in 0...@array_rows
            @plain1[i] = javaint2byte(i)
        end
    end
    
    
    
    def init
        @array_rows = @datasizes[@size];
        buildTestData()
    end
    
    def start
        IDEARunner(@plain1,@crypt1,@Z)
#        for i in 0...10
#            puts @plain1[i]
#        end
#        exit
        IDEARunner(@crypt1,@plain2,@DK);
#        for i in 0...10
#            puts @plain2[i]
#        end

begin        
        for i in 0...@plain1.length
            #puts "#{@plain1[i]},  #{@plain2[i]}"
            if @plain1[i] != @plain2[i] then
                puts "Validation failed"
                puts "#{@plain1[i]},  #{@plain2[i]}"
                exit
            end
        end
end
    end
end    

#simall size(A in jgf)
bench = Crypt.new(1, 0)

bench.run
