require './adjacency.rb'
require './dot.rb'
require './transitivity'
require 'detpar'

require 'java'
require './Graph.jar'
include_class 'Graph'

def reducible(g, gene, leftMin)
	#make a copy
	graphCopy = g.makeACopy
	#remove all the edges according to the gene
	removedNum = 0

	for i in 0...gene.size
		if(gene[i] == 1) then
			oneEdge = g.getEdge(i)
			#puts oneEdge[0].to_s + "," + oneEdge[1].to_s
			graphCopy.removeEdge(oneEdge[0], oneEdge[1])
			removedNum += 1
		end
	end
	#remove all the isolated nodes
	
	#is it a connected graph
	nodeList = Array.new
	visited = Array.new
	nodeLeft = graphCopy.vertexNum
	if graphCopy.connected then
		graphCopy = graphCopy.reduce(graphCopy)
		nodeLeft = graphCopy.vertexNum
	end
	
	if nodeLeft != 1 and nodeLeft == g.vertexNum then
		nodeLeft -= 1
	end

	leftMin.push(nodeLeft)
	fitness = (g.edgeNum - removedNum) * (g.vertexNum - nodeLeft) * (g.vertexNum - nodeLeft)
end

GeneNum = 32
CopyNum = 8

def search(dg)
	genes = Array.new
	fitnesses = Array.new

	dg = dg.reduce(dg)
	dg.print

	puts "edge num: " + dg.edgeNum.to_s
	puts "vertex num: " + dg.vertexNum.to_s

	#init the firt generation
	(1..4).each{|i|
		gene = Array.new
		(0...dg.edgeNum).each{|j|
			if(j % (i + 1) == 0) then
				gene.push(1)
			else
				gene.push(0)
			end
		}
		genes.push(gene)
	}
	gene = Array.new(dg.edgeNum, 0)
	genes.push(gene)
	#start to search
	genNum = 1
	srand
	while(true)
		#calculate the fitness for each gene
		totalFitness = 0.0
		fitnesses.clear
		r = Reduction.new(Reduction::ADD)
		leftMin = Reduction.new(Reduction::MIN)
		(0...genes.size).all{|g|
			fitness = reducible(dg, genes[g], leftMin)
			fitnesses[g] = fitness
			r.push(fitness)
		}
		
		totalFitness = r.get
		
		#sort genes according to the fitnesses
		for i in 0...fitnesses.size
			for j in i+1...fitnesses.size
				if fitnesses[i] < fitnesses[j] then
					tempFit = fitnesses[j]
					fitnesses[j] = fitnesses[i]
					fitnesses[i] = tempFit
					tempGene = genes[j]
					genes[j] = genes[i]
					genes[i] = tempGene
				end
			end
		end
		
		puts "generation " + genNum.to_s + " : node left=" + leftMin.get.to_s 

		#quit if ...
		if genNum >= dg.edgeNum * 5 then
			break
		end
		#generate the next generation genes
		newGenes = Array.new
		for i in 0...CopyNum
			if not genes[i].nil? then
				newGenes.push(genes[i])
			else
				break
			end
		end

		#select other genes for mutation
		for i in CopyNum...genes.size
			rdn = rand(1000) 

			if rdn < (fitnesses[i] * 1.0 / totalFitness) * 1000 then
				newGenes.push(genes[i])
			end	
		end

		#crossover
		i = 0
		currNum = newGenes.size
		while(i < currNum)
			index1 = rand(currNum)
			index2 = rand(currNum)
			gene1 = newGenes[index1]
			gene2 = newGenes[index2]
			if(index1 == 0) then
				temp = gene1.clone
				newGenes.push(temp)
				gene1 = temp
			elsif(index2 == 0) then
				temp = gene2.clone
				newGenes.push(temp)
				gene2 = temp
			end
			
			for j in 1..4
				index1 = rand(gene1.size)
				index2 = rand(gene2.size)
				temp = gene1[index1]
				gene1[index1] = gene2[index2]
				gene2[index2] = temp
			end
			i += 2
		end
		#mutation
		currNum = newGenes.size
		while(newGenes.size < GeneNum)
			index = 0#rand(currNum) 
			gene = Array.new(newGenes[index])
			index = rand(gene.size)
			gene[index] =  1 -  gene[index]
			newGenes.push(gene)
		end
		#replace genes with newGenes
		genes = newGenes
		#increase the generation number
		genNum += 1
	end
	puts dg.print
	#puts genes
end

dg = Graph.new(ARGV[0])
ParLib.init(ARGV[1].to_i)
dg.print

puts "edge num: " + dg.edgeNum.to_s
puts "vertex num: " + dg.vertexNum.to_s
init_end_time = Time.now
search(dg)
compute_end_time = Time.now
compute_time = compute_end_time - init_end_time
puts sprintf("computing time=%.6f", compute_time)
exit
