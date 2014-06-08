package org.jruby.parlib;

import org.jruby.RubyProc;
import org.jruby.runtime.builtin.IRubyObject;

public class PipelineStageTask extends PLibTaskSeries {
    // The incoming argument of this stage
    private IRubyObject arg = null;
    // Execution result of this stage. Note that this field will be filled
    // during the pipeline execution, so any access to it must happens after 
    // its execution. 
    private volatile IRubyObject result = null;
    
    // Build one stage of one entity in a pipeline
    public PipelineStageTask(int thid, int seqNo, int depth, RubyProc entryPoint,
                             Scheduler manager, ParallelContext pContext,
                             IRubyObject myArgs) {
        super(thid, seqNo, depth, entryPoint, manager, pContext);
        this.arg = myArgs;
    }
    
    // Execute one stage of a pipeline for one entity
    public void exec() {
        // If arg is null (in Java), simply return
        // However, if arg is nil (in Ruby), we still need to execute the whole
        // stage!
        if (arg == null) {
            return;
        }
        IRubyObject[] packedArgs = {arg};
        this.result = entryPoint.call(manager.getContext(Thread.currentThread()), packedArgs);
    }
    
    // Check whether this stage is done (redundant?)
    public boolean isFinished() {
        return (this.result != null);
    }
    
    // Return the result of this execution
    public IRubyObject getResult() {
        return this.result;
    }
}
