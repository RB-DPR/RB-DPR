package org.jruby.parlib;

import org.jruby.runtime.ThreadContext;
import org.jruby.runtime.builtin.IRubyObject;
import org.jruby.RubyProc;
import org.jruby.race.RaceDetector;
import org.jruby.race.RaceTask;

public class CoBegin {
    public static void start(ThreadContext context, IRubyObject recv,
            IRubyObject[] args) {
        // Get thread manager
        Scheduler currManager = context.getRuntime().getThreadService()
                .getParLibThreadMgr();
        // Set up a parallel context
        ParallelContext coContext = new ParallelContext(args.length);
        Thread th = Thread.currentThread();

        //start a new finish
        int finishId = (currManager.getThId(th) << 26) + currManager.getSeqNo(th);
        //System.out.println("start finish, finishid = " + finishId + ", threadid = " + currManager.getThId(rt));
        if(!RaceDetector.turnOff){
            RaceDetector.notifyStartAll(finishId, RaceTask.RACE_TASK_COBEGIN);
        }

        // args should be a series of lambda operations
        // Traverse this list to add tasks to task pool
        for (int i = 0; i < args.length; i++) {
            // TODO: manager.getDepth
            PLibTaskSeries task = new 
                    PLibTaskSeries(currManager.getThId(th),
                            currManager.getSeqNo(th),
                            currManager.getCurrentLevel(th) + 1,
                            (RubyProc) args[i], currManager, coContext);
            //add task to task tree before enqueue
            
            if(!RaceDetector.turnOff) {
                task.setScopeDepth(context.getCurrentScope().getDepth());
                RaceDetector.notifyTaskFork(task.getGuid(), 1);
            }
            currManager.enqueueTask(th, task);
        }

        // Wait till all tasks are finished
        coContext.join();

        //ending a finish
        if(!RaceDetector.turnOff) {
            RaceDetector.notifyEndAll(finishId, finishId + 2);
        }
    }
}
