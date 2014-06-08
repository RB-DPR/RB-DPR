package org.jruby.parlib;

import org.jruby.parlib.PLibTaskSeries;
import org.jruby.RubyArray;
import org.jruby.RubyProc;
import org.jruby.race.RaceDetector;
import org.jruby.runtime.Block;
import org.jruby.runtime.BlockBody;
import org.jruby.runtime.ThreadContext;
import org.jruby.runtime.builtin.IRubyObject;
import org.jruby.parlib.ParallelContext;
import org.jruby.Ruby;
import org.jruby.runtime.Block;
import org.jruby.runtime.BlockBody;

public class DotAllArrayTask extends PLibTaskSeries {
    private Block taskBlock;
    private long begin;
    private long end;
    private RubyArray array;

    // initialize a task
    public DotAllArrayTask(int thid, int seqNo, int depth, Block allBlock, 
                       RubyArray array, long begin, long end,
                       Scheduler manager, ParallelContext pContext) {
        this.guid = (thid << 26) + seqNo;
        this.depth = depth;
        // Get a copy of all block, rather than using the original one
        this.taskBlock = allBlock.cloneBlock();
        this.pContext = pContext;
        this.array = array;
        this.begin = begin;
        this.end = end;
        this.manager = manager;
    }

    // Execute the task
    public void exec() {
        ThreadContext context = manager.getContext(Thread.currentThread());
        Ruby runtime = context.getRuntime();
        for (long i = begin; i < end; i++) {
            // System.out.println("execute "+i);
            taskBlock.yield(context, array.entry(i));
            // For race detection
            if(!RaceDetector.turnOff) {
                if (RaceDetector.raceType != RaceDetector.RACE_TYPE_CILK) {
                    RaceDetector.notifyStartNewItr(i - begin);
                }
            }
        }
        //pContext.arrive();
    }
}
