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

import org.jruby.runtime.ThreadContext;
import org.jruby.race.RaceEvent;
import org.jruby.race.RaceDetector;

/**
 * Worker thread for parallel library
 */

public class WorkerThread extends Thread {

public static final int TASK_MODE = 0;
public static final int LOG_MODE = 1;
public static final int CHANGE_INTERVAL = 16;

protected int id;
protected int seqNo;
protected int mode = TASK_MODE;
protected Scheduler scheduler = null;
protected ThreadContext context = null;
protected LinkedList<PLibTaskSeries> taskStack;

// private RaceEvent re;

// Constructor
public WorkerThread(int id, Scheduler mgr) {
    this.id = id;
    this.seqNo = 0;
    this.scheduler = mgr;
    this.taskStack = new LinkedList<PLibTaskSeries>();

    // this.re = new RaceEvent();
    // this.re.thrdId = id;
}

// Getter for race event
/*
 * public RaceEvent getRaceEvent() { return this.re; }
 */

// Getter for id
public int getWorkerId() {
    return this.id;
}

// Get seq number
public int getSeqNo() {
    int val = seqNo;
    seqNo += 3;
    return val;
}

// Get current mode
public boolean isInTaskMode() {
    return (this.mode == TASK_MODE);
}

// Change mode
public void setTaskMode() {
    this.mode = TASK_MODE;
}

public void setLogMode() {
    this.mode = LOG_MODE;
}

public ThreadContext getContext() {
    return this.context;
}

public static void getAndExecuteTask(int batchNo) {
    Thread self = Thread.currentThread();
    boolean executedATask = false;
    // Process urgent log tasks
    PLibLogTask logTask = Scheduler.pollUrgent();
    if (logTask != null) {
        logTask.exec();
    }
    if (!Scheduler.isMain(self)) {
        WorkerThread th = (WorkerThread) self;
        // See if we need to change mode
        if (batchNo == 0) {
            if (Scheduler.shouldExecLog()) {
                th.setLogMode();
            } else {
                th.setTaskMode();
            }
        }
        // If the thread is not in task mode, get log tasks. 
        if (!th.isInTaskMode()) {
            //System.out.println("Log task mode");
            // Main thread will not execute log tasks
            // If we still got nothing, try get one log task to merge
            logTask = Scheduler.selfGetLogTask(self,
                    PLibLogTask.LOG_TASK_PRIO_MEDIUM);
            if (logTask != null) {
                logTask.exec();
                executedATask = true;
            } else {
                logTask = Scheduler.stealLogTask(PLibLogTask.LOG_TASK_PRIO_MEDIUM);
                if (logTask != null) {
                    logTask.exec();
                    executedATask = true;
                }
            }
            if (executedATask) {
                th.setTaskMode();
            }
            return;
        }
    }
    
    // Get a normal task
    // Try grab a task from myself
    PLibTaskSeries myTask = Scheduler.selfGetTask(self);
    // If failed, my task queue is empty
    if (myTask == null) {
        // Steal a task from someone else
        myTask = Scheduler.stealTask();
    }
    // If we have something to do, do it
    if (myTask != null && myTask.isEnabled()) {
        preExec(self, myTask);
        myTask.exec();
        postExec(self, myTask);
        // executedATask = true;
    }
    // More possibilities added for IO?
    // } else if (!Scheduler.isMain(self)) {
    // System.out.println("after exe:" + id);
    /* Adding some time gaps */
    /*
     * try { Thread.sleep(1000); } catch (Exception e) {
     * 
     * }
     */
}

// Separate method used when the critical path of the program terminates
public static boolean getAndExecuteLogTask() {
    boolean result = false;
    Thread self = Thread.currentThread();
    // Process urgent log tasks
    PLibLogTask logTask = Scheduler.pollUrgent();
    if (logTask != null) {
        logTask.exec();
        result = true;
    }
    // If we still got nothing, try get one log task to merge
    logTask = Scheduler.getLogTask(self, PLibLogTask.LOG_TASK_PRIO_MEDIUM);
    if (logTask != null) {
        logTask.exec();
        result = true;
    } else {
        logTask = Scheduler.stealLogTask(PLibLogTask.LOG_TASK_PRIO_MEDIUM);
        if (logTask != null) {
            logTask.exec();
            result = true;
        }
    }
    return result;
}

public static void preExec(Thread th, PLibTaskSeries task) {
    if (Scheduler.isMain(th)) {
        // If the thread is main thread, its task stack is in ThreadManager
        Scheduler.pushTaskForMain(task);
        // System.out.println("task "+task.getGuid()+
        // " begin executing by main");
    } else {
        // The thread is a WorkerThread, and has its task stack
        ((WorkerThread) th).pushTask(task);
        // System.out.println("task "+task.getGuid()+
        // " begin executing by thread " +
        // ((WorkerThread) th).getWorkerId());
    }

    // map the task to thread

    if (!RaceDetector.turnOff) {
        RaceDetector.notifyTaskExec(task.getGuid(), task.getScopeDepth());
    }
}

public static void postExec(Thread th, PLibTaskSeries task) {

    if (Scheduler.isMain(th)) {
        // If the thread is main thread, its task stack is in ThreadManager
        Scheduler.popTaskForMain();
        // System.out.println("task "+task.getGuid()+
        // " finished executing by main");
    } else {
        // The thread is a WorkerThread, and has its task stack
        ((WorkerThread) th).popTask();
        // System.out.println("task "+task.getGuid()+
        // " finished executing by thread " +
        // ((WorkerThread) th).getWorkerId());
    }
    // remap thread to its previous task
    if (!RaceDetector.turnOff) {
        RaceDetector.notifyTaskDone(task.getGuid());
        // restore the previous task
        Scheduler manager = Scheduler.getScheduler();
        PLibTaskSeries preTask = manager.getCurrentTask(th);
        if (preTask != null)
            RaceDetector.notifyTaskReMap(preTask.getGuid());
    }
    task.arrive();
}

// WorkerThread task entity
public void run() {
    // Register to the thread service
    context = scheduler.getService().getCurrentContext();
    int batchNo = 0;
    // Then start working
    while (true) {
        ++batchNo;
        batchNo %= WorkerThread.CHANGE_INTERVAL;
        WorkerThread.getAndExecuteTask(batchNo);
    }
}

public void pushTask(PLibTaskSeries task) {
    this.taskStack.push(task);
}

public void popTask() {
    this.taskStack.pop();
}

public PLibTaskSeries getCurrentTask() {
    return this.taskStack.peekFirst();
}
}
