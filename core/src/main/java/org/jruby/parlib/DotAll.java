package org.jruby.parlib;

import org.jruby.Ruby;
import org.jruby.RubyFixnum;
import org.jruby.RubyArray;
import org.jruby.RubyProc;
import org.jruby.runtime.Block;
import org.jruby.runtime.BlockBody;
import org.jruby.runtime.ThreadContext;
import org.jruby.runtime.builtin.IRubyObject;
import org.jruby.parlib.DotAllRangeTask;
import org.jruby.parlib.DotAllArrayTask;

import org.jruby.Ruby;
import org.jruby.RubyThread;
import org.jruby.race.RaceDetector;
import org.jruby.race.RaceEvent;
import org.jruby.race.RaceTask;

public class DotAll {
    public static void fixnumRangeDotAll(Block block, ThreadContext context, 
            RubyFixnum begin, RubyFixnum end,
            boolean isExclusive) {
        // Check whether the end value is included in this range
        long lim = end.getLongValue();
        if (!isExclusive) {
            lim++;
        }
        long beginNum = begin.getLongValue();

        // Get thread manager
        Scheduler currManager = context.getRuntime().getThreadService()
                .getParLibThreadMgr();
        // Set up a parallel context
        ParallelContext allContext = new ParallelContext();
        // See how many chunks we need to split
        // TODO: any possible optimizations?
        int numSeries = currManager.getPoolSize() * 2;
        long taskSize = lim - beginNum;
        long taskPerTh = (long)Math.ceil((double) taskSize / (double) numSeries);
        //        System.out.println("Task begins from: "+beginNum + 
        //                           ", ends at: "+lim+", size = "+taskSize+
        //                           " task per thread: "+taskPerTh);
        Thread th = Thread.currentThread();

        //Notify the race detector to create a new finish node
        int finishId = (currManager.getThId(th) << 26) + currManager.getSeqNo(th);
        if(!RaceDetector.turnOff){
            RaceDetector.notifyStartAll(finishId, RaceTask.RACE_TASK_DOTALL);
        }

        // Traverse the whole range to distribute tasks
        // Now we distribute tasks statically among threads
        for (long i = 0; i < numSeries; i++) {
            // Get the lowerbond of a task
            long lowerBond = i * taskPerTh;
            // If the lowerbond has already exceeded the range, cut the loop
            if (lowerBond >= lim) {
                break;
            }
            // Get the upperbond of a task
            long upperBond = lowerBond + taskPerTh;
            // if the upperbond exceeds the range (but lowerbond doesn't), cut 
            // it by the range
            if (upperBond > lim) {
                upperBond = lim;
            }
            //            System.out.println("Task: "+lowerBond+" to "+(upperBond - 1)+", level " + (currManager.getCurrentLevel(th) + 1));
            DotAllRangeTask task = new 
                    DotAllRangeTask(currManager.getThId(th), 
                            currManager.getSeqNo(th), 
                            currManager.getCurrentLevel(th) + 1, block,
                            lowerBond, upperBond, currManager,
                            allContext);
            allContext.addTask();
            //add task to task tree before enqueue
            if(!RaceDetector.turnOff){
                // FIXME: Temporarily disabled
                // task.setScopeDepth(context.getCurrentScope().getDepth());
                //System.out.println("task forked with depth " + task.getScopeDepth());
                RaceDetector.notifyTaskFork(task.getGuid(), (int) (upperBond - lowerBond));
            }
            if(!RaceDetector.turnOff && RaceDetector.raceType == RaceDetector.RACE_TYPE_SPD3 &&
                    RaceDetector.itrOn){
                //long thrdId = currManager.getThId(th);
                //long seqNo = currManager.getSeqNo(th);
                for(long l = lowerBond + 1; l < upperBond; l++){
                    RaceDetector.notifyTaskFork(task.guid + 1, 1);
                }
            }
            currManager.enqueueTask(th, task);
        }
        // Wait till all tasks are finished
        allContext.join();
        //ending a finish
        if(!RaceDetector.turnOff) {
            RaceDetector.notifyEndAll(finishId, finishId + 2);
        }
    }

    public static void arrayDotAll(Block block, ThreadContext context,
            RubyArray array) {
        long beginNum = 0;
        long endNum = (long) array.getLength();

        // Get thread manager
        Scheduler currManager = context.getRuntime().getThreadService()
                .getParLibThreadMgr();
        // Set up a parallel context
        ParallelContext allContext = new ParallelContext();
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
        int finishId = (currManager.getThId(th) << 26) + currManager.getSeqNo(th);
        if(!RaceDetector.turnOff){
            RaceDetector.notifyStartAll(finishId, RaceTask.RACE_TASK_DOTALL);
        }


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
            // if the upperbond exceeds the range (but lowerbond doesn't), cut 
            // it by the range
            if (upperBond > endNum) {
                upperBond = endNum;
            }
            //            System.out.println("Task: "+lowerBond+" to "+(upperBond - 1)+", level " + (currManager.getCurrentLevel(th) + 1));

            DotAllArrayTask task = new
                    DotAllArrayTask(currManager.getThId(th),
                            currManager.getSeqNo(th),
                            currManager.getCurrentLevel(th) + 1, block,
                            array, lowerBond, upperBond, currManager,
                            allContext);
            allContext.addTask();
            //add task to task tree before enqueue
            if(!RaceDetector.turnOff){
                task.setScopeDepth(context.getCurrentScope().getDepth());
                //System.out.println("task forked with depth " + task.getScopeDepth());
                RaceDetector.notifyTaskFork(task.getGuid(), (int) (upperBond - lowerBond));
            }
            if(!RaceDetector.turnOff && RaceDetector.raceType == RaceDetector.RACE_TYPE_SPD3 &&
                    RaceDetector.itrOn){
                //long thrdId = currManager.getThId(th);
                //long seqNo = currManager.getSeqNo(th);
                for(long l = lowerBond + 1; l < upperBond; l++){
                    RaceDetector.notifyTaskFork(task.guid + 1, 1);
                }
            }
            currManager.enqueueTask(th, task);
        }
        // Wait till all tasks are finished
        allContext.join();
        //ending a finish
        if(!RaceDetector.turnOff) {
            RaceDetector.notifyEndAll(finishId, finishId + 2);
        }
    }
}
