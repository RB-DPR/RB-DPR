Usage

jruby mutableadjacency.rb 07df.txt 2

"07df.txt" is the input file and "2" is the thread number. 

There are 32 genes in each generation, which are evaluated in a .all. 
-----------------------------------------------------------------------------------------------------------
(0...genes.size).all{|g|
      fitness = reducible(dg, genes[g], leftMin)
      #totalFitness += fitness
      fitnesses[g] = fitness
      r.push(fitness)
    }
---------------------------------------------------------------------------------------------------------
Therefore, this program is expected to scale well with 32-thread. 
