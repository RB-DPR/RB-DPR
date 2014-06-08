To run the benchmark:
In benchmark directory, run:

jruby ./encoding_benchmark.rb <num_threads>

The benchmark will generate two passes to all encoders. The first round is a
rehearsal, to simply warm up the cache. Evaluation results should be generated
from the second pass. 

It is recommended to use overall speedup as a metric of evaluation. 

Contains ACops, cannot run with SDP3 or Cilk. 
