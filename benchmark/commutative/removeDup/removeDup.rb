#!/usr/bin/ruby
require 'detpar'
require 'thread'

class Bucket
  attr_accessor :itemList, :mutex

  def initialize
    @itemList = Array.new
    @mutex = Mutex.new
  end
end

class SequenceItem
  attr_accessor :key, :data

  def initialize(key, data)
    @key = key
    @data = data
  end

end

class DetHash
  # Three comparison functions required by the specification
  # hashFunction(k, s): get hash value for k on a table with size s
  # keyComp(k1, k2): true iff. k1 == k2
  # dataComp(d1, d2): true iff. d1 > d2 in a partial order
  attr_accessor :hashFunction, :keyComp, :dataComp
  @@called = 0
  
  def initialize(size, hashF)
    @size = size
    @hashFunction = hashF
    @rawData = Array.new(size) do
      Bucket.new
    end
    @temp = 0
  end

  def insert(item)
    # puts "called: "+@@called.to_s
    # puts "insert "+item.key.to_s
    return nil if @keyComp.nil? or @dataComp.nil?
    key = item.key
    data = item.data
    index = @hashFunction.call(key, @size)
    bucket = @rawData[index]
    # Get mutual exclusion
    foundKey = false
    bucket.mutex.lock
    bucket.itemList.each do |i|
      if (@keyComp.call(key, i.key))
        i.data = data if (@dataComp.call(data, i.data))
        foundKey = true
      end
    end
    if not foundKey
      # item.key = Digest::SHA1.hexdigest item.key
      bucket.itemList.push(item)
    end
    bucket.mutex.unlock
  end

  def lookup()
  end

  def entries()
    result = Array.new
    @rawData.each do |bucket|
      bucket.itemList.each do |item|
        result.push(item)
      end
    end
    return result
  end

  setACOps :insert
end

def myIntIndex(key, size)
  key = (key+0x7ed55d16) + (key<<12);
  key = (key^0xc761c23c) ^ (key>>19);
  key = (key+0x165667b1) + (key<<5);
  key = (key+0xd3a2646c) ^ (key<<9);
  key = (key+0xfd7046c5) + (key<<3);
  key = (key^0xb55a4f09) ^ (key>>16);

  return (key % size)
end

def myStringIndex(key, size)
  keyEnc = key#Digest::SHA1.hexdigest key
  return (keyEnc.hash % size)
end

def encStringEql(incomingStr, storedStr)
  # encIncoming = Digest::SHA1.hexdigest incomingStr
  return incomingStr.eql?(storedStr)
end

def intEql(a, b)
  return (a == b)
end

def numGreater(a, b)
  return a > b
end

def removeDup(incomingSeq)
  myTable = DetHash.new(5000, method(:myStringIndex))
  myTable.keyComp = method(:encStringEql)
  myTable.dataComp = method(:numGreater)
  incomingSeq.all do |element|
    myTable.insert(element)
  end
  newSeq = myTable.entries
  return newSeq
end


thNum = ARGV[1]
fileName = ARGV[0]
puts "RemoveDup on file "+fileName
puts "With "+thNum.to_s+" threads"
ParLib.init(thNum.to_i)
srand(1)

file = File.new(fileName, "r")
seqArray = Array.new
file.each do |line|
  line.upcase!
  line.gsub!(/[.,"]/, ' ')
  # puts line
  myArray = line.split()
  myArray.each do |str|
    newVal = rand
    element = SequenceItem.new(str, newVal)
    # puts "key: "+str+" weight: "+newVal.to_s
    seqArray.push(element)
  end
end

# puts "threads?"
puts "Begin"
puts "Sequence length: "+seqArray.length.to_s
init_end_time = Time.now
repeat = 0
while repeat < 50 do
  result = removeDup(seqArray)
  repeat += 1
end
compute_end_time = Time.now

result.each do |i|
  #puts i.key.to_s + " -> " + i.data.to_s
end

compute_time = compute_end_time - init_end_time

puts sprintf("computing time=%.6f", compute_time)
exit
