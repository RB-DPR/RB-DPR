require './adjacency.rb'
require './dot.rb'
require './transitivity'
require 'detpar'

require 'java'
require './Graph.jar'
include_class 'Graph'

=begin
class MutableDirectedAdjacencyGraph < RGL::DirectedAdjacencyGraph
	def init filePath
		file = open(filePath)
		arr = file.readlines
		for i in 0...arr.size do
			line = arr[i]
			next if line.size <= 0 
			index = line.index(",")
			if(not index.nil?) then
				from = line[0, index]
				tos = line[index + 1, line.size - 1]
				from = from.to_i
				#puts "from" + from.to_s
				index = tos.index(" ")
				next if index.nil?
				tos = tos[index + 1, tos.size - 1]
				#puts "tos" + tos
				tos = tos.split(" ").map{|s| s.to_i}
				#puts tos
				tos.each{|to|
					add_edge(from, to)
				}
			end
		end
	end
	
	def getFromList(v)
		list = Array.new
		each_edge {|f, t|
			if(t == v) and not list.include?(f) then
				list.push(f)
			end
		}
		list
	end
	
	def getToList(v)
		list = Array.new
		each_edge {|f, t|
			if(f == v) and not list.include?(t) then
				list.push(t)
			end
		}
		list
	end
	
	def postOrder(g, v, vlist, visited)
		visited.push(v)
		g.each_adjacent(v) do |w|
			if not visited.include?(w) then
				postOrder(g, w, vlist, visited)
			end
		end
		vlist.push(v)
	end
	
	def reduce(g)
		nodeId = 1000
		visited = Array.new
		vlist = Array.new
		
		currPos = 0
		changed = true
		while(g.vertices.size > 1)
			if changed then
				visited.clear
				vlist.clear
				postOrder(g, g.vertices[0], vlist, visited)
				currPos = 0
				changed = false
			elsif currPos >= vlist.size and not changed then
				break
			end
			
			v = vlist[currPos]
			toList = g.getToList(v)

			#test if it is a sequential path
			if toList.size == 1 then
				fromList = g.getFromList(toList[0])
				if fromList.size == 1 then
					gCopy = g.makeACopy
					g.each_edge{|f, t|
						if f == toList[0] then
							gCopy.remove_edge(f, t)
							gCopy.add_edge(v, t)
						end
					}
					gCopy.remove_edge(v, toList[0])
					gCopy.remove_vertex(toList[0])
					changed = true
					g = gCopy
					#puts "a seq found"
					next
				end
			#test if it is an "if"
			elsif toList.size == 2 then
				#test if it is a "if-else"
				fromList0 = g.getFromList(toList[0])
				fromList1 = g.getFromList(toList[1])
				if fromList0.size == 1 and fromList1.size == 1 then
					toList0 = g.getToList(toList[0])
					toList1 = g.getToList(toList[1])
					if toList0.size == 1 and toList1.size == 1 then
						if toList0[0] == toList1[0] then
							g.remove_edge(v, toList[0])
							g.remove_edge(v, toList[1])
							g.remove_edge(toList[0], toList0[0])
							g.remove_edge(toList[1], toList0[0])
							g.add_edge(v, toList0[0])
							g.remove_vertex(toList[0])
							g.remove_vertex(toList[1])
							changed = true
							#puts "a if else found"
							next
						end
					elsif toList0.size == 0 and toList1.size == 0 then
						g.remove_edge(v, toList[0])
						g.remove_edge(v, toList[1])
						g.remove_vertex(toList[0])
						g.remove_vertex(toList[1])
						changed = true
						#puts "a if else found"
						next
					end
				#test if it is a if 
				elsif fromList0.size == 1 then
					toList0 = g.getToList(toList[0])
					if toList0.size == 1 and toList0[0] == toList[1] then
						g.remove_edge(v, toList[0])
						g.remove_edge(toList[0], toList[1])
						g.remove_vertex(toList[0])
						changed = true
						#puts "a if found"
						next
					end
				#test if it is a if
				elsif fromList1.size == 1 then
					toList1 = g.getToList(toList[1])
					if toList1.size == 1 and toList1[0] == toList[0] then
						g.remove_edge(v, toList[1])
						g.remove_edge(toList[1], toList[0])
						g.remove_vertex(toList[1])
						changed = true
						#puts "a if found"
						next
					end
				end
			end
			
			fromList = g.getFromList(v)
			#test if it is a "loop"
			if fromList.size == 1 then
				if g.has_edge?(fromList[0], v) and g.has_edge?(v, fromList[0]) then
					if g.out_degree(fromList[0]) == 2 and g.out_degree(v) == 1 then
						g.remove_edge(v, fromList[0])
						g.remove_edge(fromList[0], v)
						g.remove_vertex(v)
						changed = true
						#puts " a loop 1 found"
						next
					elsif g.out_degree(v) == 2 and g.out_degree(fromList[0]) == 1 then
						g.remove_edge(v, fromList[0])
						changed = true
						#puts "a loop 2 found"
						next
					end
				end
			end
			
			
			currPos += 1
			
		end
		g
	end

	def makeACopy
		copy = self.class.new
		each_vertex{ |v|
			copy.add_vertex(v)
		}
		each_edge{ |f, t|
			copy.add_edge(f,t)
		}
		copy
	end
=end	

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
	#g.getPostOrder(graphCopy, graphCopy.vertices[0], nodeList.to_java, visited.to_java)
	#if nodeList.size == graphCopy.vertexNum then
	#	graphCopy = graphCopy.reduce(graphCopy)
	#	nodeLeft = graphCopy.vertexNum
	#end
	#puts "before connected"
	#graphCopy.print
	if graphCopy.connected then
		graphCopy = graphCopy.reduce(graphCopy)
		nodeLeft = graphCopy.vertexNum
	end
	
	#puts "nodeLeft=" + nodeLeft.to_s
	if nodeLeft != 1 and nodeLeft == g.vertexNum then
		nodeLeft -= 1
	end
	#if(nodeLeft < leftMin[0]) then
	#	leftMin[0] = nodeLeft
	#end
	leftMin.push(nodeLeft)
	fitness = (g.edgeNum - removedNum) * (g.vertexNum - nodeLeft) * (g.vertexNum - nodeLeft)
end

GeneNum = 32
CopyNum = 8

def search(dg)
	genes = Array.new
	fitnesses = Array.new
	#leftMin = Array.new
	#leftMin.push(dg.edgeNum)

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
		#(0...genes.size).each{|g|
		r = Reduction.new(Reduction::ADD)
		leftMin = Reduction.new(Reduction::MIN)
		(0...genes.size).all{|g|
			fitness = reducible(dg, genes[g], leftMin)
			#totalFitness += fitness
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
		#puts "(" + fitnesses[0].to_s + ")" + genes[0].to_s.delete(",")
		#(0...fitnesses.size).each{|i|
		#	puts "[" + i.to_s + "](" + fitnesses[i].to_s + ")" + genes[i].to_s.delete(", ")
		#}

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
		#puts "copy:" + newGenes.size.to_s
		#select other genes for mutation
		for i in CopyNum...genes.size
			rdn = rand(1000) 
			#puts rdn
			#puts (fitnesses[i] * 1.0 / totalFitness) * 1000
			if rdn < (fitnesses[i] * 1.0 / totalFitness) * 1000 then
				newGenes.push(genes[i])
			end	
		end
		#puts "select:" + newGenes.size.to_s
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
			#puts index
			gene = Array.new(newGenes[index])
			index = rand(gene.size)
			#puts index
			gene[index] =  1 -  gene[index]
			#index = rand(gene.size)
			#gene[index] =  1 -  gene[index]
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
#dg = MutableDirectedAdjacencyGraph.new
dg = Graph.new(ARGV[0])
#dg.init ARGV[0]
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
