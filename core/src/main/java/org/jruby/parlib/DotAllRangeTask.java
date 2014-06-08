package org.jruby.parlib;

import org.jruby.RubyProc;
import org.jruby.runtime.Block;
import org.jruby.runtime.BlockBody;
import org.jruby.runtime.ThreadContext;
import org.jruby.runtime.builtin.IRubyObject;
import org.jruby.RubyFixnum;
import org.jruby.parlib.ParallelContext;
import org.jruby.Ruby;
import org.jruby.runtime.Block;
import org.jruby.runtime.BlockBody;
import org.jruby.race.RaceDetector;

public class DotAllRangeTask extends PLibTaskSeries {
    private Block taskBlock;
    private long begin;
    private long end;
    
    // initialize a task
    public DotAllRangeTask(int thid, int seqNo, int depth, Block allBlock, 
                           long begin, long end, Scheduler manager, 
                           ParallelContext pContext) {
        this.guid = (thid << 26) + seqNo;
        this.depth = depth;
        // Get a copy of all block, rather than using the original one
        this.taskBlock = allBlock.cloneBlock();
        this.pContext = pContext;
        this.begin = begin;
        this.end = end;
        this.manager = manager;
    }
    
    // Execute the task
    public void exec() {
        ThreadContext context = manager.getContext(Thread.currentThread());
        Ruby runtime = context.getRuntime();
        if (taskBlock.getBody().getArgumentType() == BlockBody.ZERO_ARGS) {
            final IRubyObject nil = runtime.getNil();
            for (long i = begin; i < end; i++) {
                taskBlock.yield(context, nil);
                // For race detection
                if(!RaceDetector.turnOff) {
                    if (RaceDetector.raceType != RaceDetector.RACE_TYPE_CILK) {
                        RaceDetector.notifyStartNewItr(i - begin);
                    }
                }
            }
        } else {
            for (long i = begin; i < end; i++) {
                // System.out.println("execute "+i);
                taskBlock.yield(context, RubyFixnum.newFixnum(runtime, i));
                // For race detection
                if(!RaceDetector.turnOff) {
                    if (RaceDetector.raceType != RaceDetector.RACE_TYPE_CILK) {
                        RaceDetector.notifyStartNewItr(i - begin);
                    }
                }
            }
        }
        //pContext.arrive();
    }
}
