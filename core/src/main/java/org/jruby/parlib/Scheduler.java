/*****
 * BEGIN LICENSE BLOCK ***** Version: CPL 1.0/GPL 2.0/LGPL 2.1
 * 
 * The contents of this file are subject to the Common Public License Version
 * 1.0 (the "License"); you may not use this file except in compliance with the
 * License. You may obtain a copy of the License at
 * http://www.eclipse.org/legal/cpl-v10.html
 * 
 * Software distributed under the License is distributed on an "AS IS" basis,
 * WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License for
 * the specific language governing rights and limitations under the License.
 * 
 * Alternatively, the contents of this file may be used under the terms of
 * either of the GNU General Public License Version 2 or later (the "GPL"), or
 * the GNU Lesser General Public License Version 2.1 or later (the "LGPL"), in
 * which case the provisions of the GPL or the LGPL are applicable instead of
 * those above. If you wish to allow use of your version of this file only under
 * the terms of either the GPL or the LGPL, and not to allow others to use your
 * version of this file under the terms of the CPL, indicate your decision by
 * deleting the provisions above and replace them with the notice and other
 * provisions required by the GPL or the LGPL. If you do not delete the
 * provisions above, a recipient may use your version of this file under the
 * terms of any one of the CPL, the GPL or the LGPL. END LICENSE BLOCK
 *****/

package org.jruby.parlib;

import java.io.IOException;
import java.util.LinkedList;
import java.util.Vector;
import org.jruby.internal.runtime.ThreadService;
import org.jruby.runtime.ThreadContext;
import java.util.Random;
import org.jruby.Ruby;
import org.jruby.RubyThread;
import org.jruby.parlib.PLibLogTask;
import java.util.concurrent.ConcurrentLinkedQueue;
//import org.jruby.race.RaceDetector;
//import org.jruby.race.RaceTask;

/**
 * Manager class for WorkerThreads
 */
public class Scheduler {

    private static Scheduler thManager = null;
    private Vector<Thread> threadPool = null;
    private int poolSize = 0;
    // Task queue
    // Note that thread manager should also maintain task queue for the main 
    // thread, so tq[0] is reserved for main thread
    private Vector<Vector<LinkedList<PLibTaskSeries>>> taskQueues;
    // Log queue for all pending logs
    // Main thread's log queue should not be executed by itself, since it's
    // always on the critical path of the program
    private Vector<Vector<LinkedList<PLibLogTask>>> logQueues;
    // For urgent log related tasks
    private ConcurrentLinkedQueue<PLibLogTask> urgentQueue;
    private Thread mainThread = null;
    private int mainThreadSeqNo = 2;
    private ThreadService service;
    private ThreadContext mainContext = null;
    private LinkedList<PLibTaskSeries> mainTaskStack;
    // Memory consumption by logs
    private volatile long[] memInUse;
    // Released logs
    private volatile long[] memReleased;
    //private RaceEvent mainRe = null;
    
    // Public constants
    // Now: 16M
    public static final long MAX_MEM_IN_USE = 16777216;

    private Scheduler() {
        this.threadPool = new Vector<Thread>(4);
        this.taskQueues = new Vector<Vector<LinkedList<PLibTaskSeries>>>();
        this.logQueues = new Vector<Vector<LinkedList<PLibLogTask>>>();
        this.urgentQueue = new ConcurrentLinkedQueue<PLibLogTask>();
    }
    
    private void init(int nCores, ThreadService service, Ruby runtime) {
        this.poolSize = nCores;
        // Accomodate the task queue for main thread
        this.taskQueues.add(new Vector<LinkedList<PLibTaskSeries>>());
        // Set up the log queue for main thread, which will only be executed by someone else
        this.logQueues.add(new Vector<LinkedList<PLibLogTask>>());
        // Set up memory information
        this.memInUse = new long[nCores];
        this.memReleased = new long[nCores];
        this.memInUse[0] = 0;
        this.memReleased[0] = 0;
        // Set up worker threads, let them work
        // Note that thread 0 is reserved for main thread
        // So in the thread pool, pool[i] is the thread with id i+1
        for (int i = 1; i < nCores; i++) {
            // Initialize memory usage
            this.memInUse[i] = 0;
            this.memReleased[i] = 0;
            // Set up worker thread
            WorkerThread th = new WorkerThread(i, this);
            // Set up task queue
            this.taskQueues.add(new Vector<LinkedList<PLibTaskSeries>>());
            // Set up log queue
            this.logQueues.add(new Vector<LinkedList<PLibLogTask>>());
            // Add thread into thread pool
            threadPool.add(th);
        }
        // Set up fields before we start all threads
        this.mainThread = Thread.currentThread();
        this.service = service;
        this.mainContext = service.getCurrentContext();
        this.mainTaskStack = new LinkedList<PLibTaskSeries>();
        service.setParLibThreadMgr(this);
        // Start all threads when we setup all of them
        // We don't need to start main thread
        for (int i = 0; i < nCores - 1; i++) {
            threadPool.get(i).start();
        }
        
        //set RaceEvent for main thread
        //this.mainRe = service.getCurrentContext().getThread().re;
        //this.mainRe.thrdId = 0;
//        System.out.println("ThreadManager: all " + poolSize 
//                  + " threads started! ");
    }
     // Singleton constructor
    public static Scheduler initThreadManager(int nCores,
                                                  ThreadService service, 
                                                  Ruby runtime) {
        if (thManager == null) {
            thManager = new Scheduler();
            thManager.init(nCores, service, runtime);
        }
        return thManager;
    }
    
    public Vector<Vector<LinkedList<PLibTaskSeries>>> getTqs() {
        return this.taskQueues;
    }
    
    public Vector<Vector<LinkedList<PLibLogTask>>> getLogTqs() {
        return this.logQueues;
    }
    
    public static int getCurrThId(){
        if(thManager == null) return 0;
        else
            return thManager.getThId(Thread.currentThread());
    }
    /*
    public RaceEvent getRaceEvent(Thread th){
        if(th.equals(mainThread))
            return mainRe;
        else 
            return ((WorkerThread)th).getRaceEvent();
    }*/
    /*
    public static void setRaceTask(RaceTask task)
    {
        Thread th = Thread.currentThread();
        ThreadManager theManager = getThreadManager();
        if (thManager != null) {
            the
    }
    public static RaceTask getRaceTask(){
        Thread th = Thread.currentThread();
        ThreadManager theManager = getThreadManager();
        if (thManager != null) {
            return theManager.getRaceEvent(th).task;
        }
        else
            return null;
    }*/    
    
    public static Scheduler getScheduler(){
        return thManager;
    }
    
    public ThreadService getService() {
        return this.service;
    }
    
    public ThreadContext getContext(Thread th) {
        if (th.equals(mainThread)) {
            return mainContext;
        } else {
            return ((WorkerThread) th).getContext();
        }
    }

    public int getSeqNo(Thread th) {
        int seqNo = 0;
        // The manager itself keeps the sequence number of the main thread
        if (th.equals(mainThread)) {
            seqNo = mainThreadSeqNo;
            mainThreadSeqNo += 3;
        } else {
            seqNo = ((WorkerThread) th).getSeqNo();
        }
        return seqNo;
    }
    
    public int getPoolSize() {
        return this.poolSize;
    }
    
    // Get the current running task for a thread
    public PLibTaskSeries getCurrentTask(Thread th) {
        if (th.equals(this.mainThread)) {
            return this.mainTaskStack.peek();
        } else {
            return ((WorkerThread) th).getCurrentTask();
        }
    }
    
    // Get the current running task level for a thread
    public int getCurrentLevel(Thread th) {
        if (th.equals(this.mainThread)) {
            PLibTaskSeries task = this.mainTaskStack.peek();
            if (task == null) {
                return -1;
            }
            return task.getDepth();
        } else {
            PLibTaskSeries task = ((WorkerThread) th).getCurrentTask();
            if (task == null) {
                return -1;
            }
            return task.getDepth();
        }
    }
    
    // Mark a log task's memory consumption as released
    public void releaseLog(Thread th, long size) {
        // Note that main thread may call this method
        int idx = getThId(th);
        this.memReleased[idx] += size;
    }
    
    // For main thread
    public static boolean isMain(Thread th) {
        return thManager.getMainThread().equals(th);
    }
    
    public Thread getMainThread() {
        return this.mainThread;
    }
    
    /* Static Methods */
    
    public static int getThId(Thread th) {
        // We reserve id 0 for main thread
        if (th.equals(thManager.mainThread)) {
            return 0;
        } else {
            // Else, the thread should be a WorkerThread
            return ((WorkerThread) th).getWorkerId();
        }
    }
    
    public static void pushTaskForMain(PLibTaskSeries task) {
        thManager.mainTaskStack.push(task);
    }
    
    public static void popTaskForMain() {
        thManager.mainTaskStack.pop();
    }
    
    // Operations for task queues
    public static void enqueueTask(Thread th, PLibTaskSeries task) {
        int idx = Scheduler.getThId(th);
        Vector<LinkedList<PLibTaskSeries>> selfTq = thManager.getTqs().get(idx);
        if (selfTq == null) {
            selfTq = new Vector<LinkedList<PLibTaskSeries>>();
        }
        synchronized (selfTq) {
            // Add task queues iteratively until we reach the requested level
            while (selfTq.size() < task.getDepth() + 1) {
                selfTq.add(new LinkedList<PLibTaskSeries>());
            }
            selfTq.get(task.getDepth()).addLast(task);
        }
    }
    
    public static void enqueueLogTask(Thread th, PLibLogTask task, int priority) {
        // Note that this function *may* be called by main thread
        int idx = Scheduler.getThId(th);
        Vector<LinkedList<PLibLogTask>> selfLogTq = thManager.getLogTqs().get(idx);
        if (selfLogTq == null) {
            selfLogTq = new Vector<LinkedList<PLibLogTask>>();
        }
        synchronized (selfLogTq) {
            // Add task queues iteratively until we reach the requested level
            while (selfLogTq.size() < priority + 1) {
                selfLogTq.add(new LinkedList<PLibLogTask>());
            }
            selfLogTq.get(priority).addLast(task);
        }
        // Add memory consumption
        thManager.memInUse[idx] += task.getSize();
    }
    
    // Decide whether should execute a log task
    public static boolean shouldExecLog() {
        // return false;
        long alloced = 0;
        for (int i = 0; i < thManager.memInUse.length; i++) {
            alloced += thManager.memInUse[i];
        }
        long released = 0;
        for (int i = 0; i < thManager.memReleased.length; i++) {
            released += thManager.memReleased[i];
        }
        // System.out.println("alloced - released: " + (alloced - released));
        return (alloced - released > MAX_MEM_IN_USE);
    }
    
    // Get an urgent log task from the urgent queue
    public static PLibLogTask pollUrgent() {
        // Main thread is *allowed* to deal with urgent log tasks
        if (thManager.urgentQueue.isEmpty()) {
            return null;
        } else {
            return thManager.urgentQueue.poll();
        }
    }
    
    // Get a log task from a thread's log tq
    public static PLibLogTask selfGetLogTask(Thread th, int priority) {
        // Note that main thread may call this method
        int idx = Scheduler.getThId(th);
        Vector<LinkedList<PLibLogTask>> selfLogTq = thManager.getLogTqs().get(idx);
        PLibLogTask logTask = null;
        synchronized (selfLogTq) {
            // Dequeue from the given priority queue
            LinkedList<PLibLogTask> currList = null;
            if (selfLogTq.size() > priority) {
                currList = selfLogTq.get(priority);
            }
            if (currList != null && !currList.isEmpty()) {
                logTask = currList.poll();
                return logTask;
            }
        }
        return logTask;
    }
    
    //Added by Weixing for completing the log tasks before program terminating
    //
    // Get a log task from a thread's log tq
    public static PLibLogTask getLogTask(Thread th, int priority) {
        // Note that main thread may call this method
        int idx = Scheduler.getThId(th);
        Vector<LinkedList<PLibLogTask>> selfLogTq = thManager.getLogTqs().get(idx);
        PLibLogTask logTask = null;
        synchronized (selfLogTq) {
            // Dequeue from the given priority queue
            LinkedList<PLibLogTask> currList = null;
            if (selfLogTq.size() > priority) {
                currList = selfLogTq.get(priority);
            }
            if (currList != null && !currList.isEmpty()) {
                logTask = currList.poll();
                return logTask;
            }
        }
        return logTask;
    }
    
    // Steal a log task from other threads
    public static PLibLogTask stealLogTask(int priority) {
        int randThid = -1;
        // Generate a random number
        Random generator = new Random();
        randThid = generator.nextInt(thManager.getPoolSize());
        PLibLogTask logTask = null;
        // Get the victim's tq
        Vector<LinkedList<PLibLogTask>> victimLogTq = thManager.getLogTqs().get(randThid);
        if (victimLogTq.size() == 0) {
            return logTask;
        }
        synchronized (victimLogTq) {
            // Find the first level of task queue that is not empty
            LinkedList<PLibLogTask> currList = null;
            if (victimLogTq.size() > priority) {
                currList = victimLogTq.get(priority);
            }
            if (currList != null && !currList.isEmpty()) {
                logTask = currList.poll();
                return logTask;
            }
        }
        return logTask;
    }
    
    // Get a task from a thread's own tq, only called by the thread itself
    public static PLibTaskSeries selfGetTask(Thread th) {
        int idx = Scheduler.getThId(th);
        Vector<LinkedList<PLibTaskSeries>> selfTq = thManager.getTqs().get(idx);
        PLibTaskSeries returnedTask = null;
        synchronized (selfTq) {
            // Find the last level of task that is not empty
            for (int i = selfTq.size() - 1; i > 0; i--) {
                LinkedList<PLibTaskSeries> currList = selfTq.get(i);
                if (currList != null && !currList.isEmpty()) {
                    returnedTask = currList.poll();
                    return returnedTask;
                }
            }
        }
        return returnedTask;
    }
    
    private static Random generator = new Random();
    
    // Steal a task from a thread
    public static PLibTaskSeries stealTask() {
        int randThid = -1;
        // Generate a random number
        randThid = generator.nextInt(thManager.getPoolSize());
        PLibTaskSeries returnedTask = null;
        // Get the victim's tq
        Vector<LinkedList<PLibTaskSeries>> victimTq = thManager.getTqs().get(randThid);
        if (victimTq.size() == 0) {
            return returnedTask;
        }
        synchronized (victimTq) {
            // Find the first level of task queue that is not empty
            for (LinkedList<PLibTaskSeries> l : victimTq) {
                if (l != null && !l.isEmpty()) {
                    returnedTask = l.poll();
                    return returnedTask;
                }
            }
        }
        return returnedTask;
    }
}
