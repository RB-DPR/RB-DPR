package org.jruby.parlib;

import org.jruby.parlib.PLibTaskSeries;
import org.jruby.RubyArray;
import org.jruby.RubyProc;
import org.jruby.race.RaceDetector;
import org.jruby.runtime.ThreadContext;
import org.jruby.runtime.builtin.IRubyObject;
import org.jruby.parlib.ParallelContext;


public class SomeTrueTask extends PLibTaskSeries {
    private RubyProc predicate;
    private long begin;
    private long end;
    private RubyArray array;
    private boolean found = false;

    // Initialize a task
    public SomeTrueTask(int thid, int seqNo, int depth, RubyProc predicate, 
                       RubyArray array, long begin, long end,
                       Scheduler manager, ParallelContext pContext) {
        this.guid = (thid << 26) + seqNo;
        this.depth = depth;
        // Get a copy of all block, rather than using the original one
        this.predicate = predicate;
        this.pContext = pContext;
        this.array = array;
        this.begin = begin;
        this.end = end;
        this.manager = manager;
    }

    // Execute the task
    public void exec() {
        ThreadContext context = manager.getContext(Thread.currentThread());
        for (long i = begin; i < end; i++) {
            IRubyObject[] paras = {array.entry(i)};
            // System.out.println("execute "+i);
            IRubyObject result = this.predicate.call(context, paras);
            // If we found something, we report and kill all other tasks
            if (result.isTrue()) {
                // System.out.println(this.guid+" Found something! "+array.entry(i));
                // Mark this task as a done one
                this.found = true;
                ((SomeTrueContext) this.pContext).arrive(array.entry(i));
            }
            // For race detection
            if(!RaceDetector.turnOff) {
                if (RaceDetector.raceType != RaceDetector.RACE_TYPE_CILK) {
                    RaceDetector.notifyStartNewItr(i - begin);
                }
            }
        }
    }
    
    // Task arrive, only called when nothing found
    public void arrive() {
        // Only losers have to report
        if (!this.found) {
            // System.out.println(this.guid + "Nothing found");
            ((SomeTrueContext)this.pContext).arrive(null);
        }
    }
}
