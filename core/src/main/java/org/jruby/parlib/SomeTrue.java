package org.jruby.parlib;

import org.jruby.RubyArray;
import org.jruby.RubyProc;
import org.jruby.runtime.ThreadContext;
import org.jruby.runtime.builtin.IRubyObject;
import org.jruby.race.RaceDetector;
import org.jruby.race.RaceEvent;
import org.jruby.race.RaceTask;

public class SomeTrue {

    public static IRubyObject arraySomeTrue(RubyProc predicate, ThreadContext context,
            RubyArray array) {
        long endNum = (long) array.getLength();

        // Get thread manager
        Scheduler currManager = context.getRuntime().getThreadService()
                .getParLibThreadMgr();
        // Set up a parallel context
        SomeTrueContext someTrueContext = new SomeTrueContext();
        // See how many chunks we need to split
        // TODO: any possible optimizations?
        int numSeries = currManager.getPoolSize() * 2;
        long taskSize = endNum;
        long taskPerTh = (long)Math.ceil((double) taskSize / (double) numSeries);
        //        System.out.println("Task begins from: "+beginNum + 
        //                           ", ends at: "+endNum+", size = "+taskSize+
        //                           " task per thread: "+taskPerTh);
        Thread th = Thread.currentThread();

        //Notify the race detector to create a new finish node
//        int finishId = (currManager.getThId(th) << 26) + currManager.getSeqNo(th);
//        if(!RaceDetector.turnOff){
//            RaceDetector.notifyStartAll(finishId, RaceTask.RACE_TASK_DOTALL);
//        }

        // Traverse the whole array to distribute tasks
        // Now we distribute tasks statically among threads
        for (long i = 0; i < numSeries; i++) {
            // Get the lowerbond of a task
            long lowerBond = i * taskPerTh;
            // If the lowerbond has already exceeded the range, cut the loop
            if (lowerBond >= endNum) {
                break;
            }
            // Get the upperbond of a task
            long upperBond = lowerBond + taskPerTh;
            // If the upperbond exceeds the range (but lowerbond doesn't), cut 
            // it by the range
            if (upperBond > endNum) {
                upperBond = endNum;
            }
            // System.out.println("Task: "+lowerBond+" to "+(upperBond - 1)+", level " + (currManager.getCurrentLevel(th) + 1));

            SomeTrueTask task = new
                    SomeTrueTask(currManager.getThId(th),
                            currManager.getSeqNo(th),
                            currManager.getCurrentLevel(th) + 1, predicate,
                            array, lowerBond, upperBond, currManager,
                            someTrueContext);
            someTrueContext.addTask(task);
//            //add task to task tree before enqueue
//            if(!RaceDetector.turnOff){
//                task.setScopeDepth(context.getCurrentScope().getDepth());
//                //System.out.println("task forked with depth " + task.getScopeDepth());
//                RaceDetector.notifyTaskFork(task.getGuid(), (int) (upperBond - lowerBond));
//            }
//            if(!RaceDetector.turnOff && RaceDetector.raceType == RaceDetector.RACE_TYPE_SPD3 &&
//                    RaceDetector.itrOn){
//                //long thrdId = currManager.getThId(th);
//                //long seqNo = currManager.getSeqNo(th);
//                for(long l = lowerBond + 1; l < upperBond; l++){
//                    RaceDetector.notifyTaskFork(task.guid + 1, 1);
//                }
//            }
            currManager.enqueueTask(th, task);
        }
        // Wait till all tasks are finished
        someTrueContext.join();
        //ending a finish
//        if(!RaceDetector.turnOff) {
//            RaceDetector.notifyEndAll(finishId, finishId + 2);
//        }
        IRubyObject result = someTrueContext.getResult();
        if (result == null) {
            return context.getRuntime().getNil();
        }
        return result;
    }
}
