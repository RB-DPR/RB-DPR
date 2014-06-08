/**
 * 
 */
package org.jruby.parlib;

import org.jruby.RubyProc;
import org.jruby.runtime.builtin.IRubyObject;

/**
 * @author lilu
 *
 */
public class FutureTask extends PLibTaskSeries {
    protected IRubyObject[] args;
    protected IRubyObject result;
    protected ParallelContext pContext;
    /**
     * @param thid
     * @param seqNo
     * @param depth
     * @param entryPoint
     * @param manager
     * @param pContext
     */
    public FutureTask(int thid, int seqNo, int depth, RubyProc entryPoint, 
            IRubyObject[] args, Scheduler manager, ParallelContext pContext) {
        super(thid, seqNo, depth, entryPoint, manager, pContext);
        this.args = args;
        this.pContext = pContext;
    }
    
    // Get result
    public IRubyObject getResult() {
        return this.result;
    }
    
    // Get my parallel context
    public ParallelContext getPContext() {
        return this.pContext;
    }
    
    // Execute the task
    public void exec() {
        this.result = entryPoint.call(manager.getContext(Thread.currentThread()), this.args);
       //pContext.arrive();
    }

}
