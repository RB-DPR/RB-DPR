/**********************************************************************
 * task.java -
 * 
 * $Author: Weixing Ji
 * created at: Oct 2012
 * 
 * Copyright (C) 2012
 **********************************************************************/
package org.jruby.race;

import org.jruby.parlib.Scheduler;
import org.jruby.race.AccessSet;
import org.jruby.race.AccessSetHt;
import org.jruby.race.AccessSetList;
import java.util.Hashtable;
import java.util.ArrayList;

public class RaceTask {
    // type of task
    public static final int    RACE_TASK_FINISH   = 0;
    public static final int    RACE_TASK_ASYNC    = 1;
    public static final int    RACE_TASK_STEP     = 2;
    public static final int    RACE_TASK_COBEGIN  = 3;
    public static final int    RACE_TASK_DOTALL   = 4;
    public static final int    RACE_TASK_PIPELINE = 5;
    public static final int    RACE_TASK_FUTURE   = 6;
    public static final int    RACE_TASK_ITR      = 7;
    public static final int    RACE_TASK_DUMMY    = 8;
    public static final int    RACE_TASK_UNKNOWN  = 9;

    public int                 id;                    // id from parLib
    public int                 type;                  // node type in the tree
    public int                 depth;                 // depth from the root,
                                                       // the depth of root is 0
    public int                 scopeDepth;
    public int                 thrdId;                // which thread execute
                                                       // the task
    public AccessSet           set;                   // read/write set for
                                                       // this task
    public ArrayList<RaceTask> children;              // child task
    public RaceTask            parent;                // parent task

    public RaceTask() {
        type = RACE_TASK_UNKNOWN;
        children = new ArrayList();
        if (RaceDetector.setType == RaceDetector.RACE_SET_HT)
            set = new AccessSetHt();
        else if (RaceDetector.setType == RaceDetector.RACE_SET_LIST)
            set = new AccessSetList();
        else {
            System.out.println("[race]error, set type is not specified!");
            System.exit(0);
        }

        parent = null;

        thrdId = -1;
        scopeDepth = 0;
    }

    public RaceTask(int id, int type, int depth, RaceTask parent) {
        this.id = id;
        this.type = type;
        this.depth = depth;
        this.parent = parent;
        // only create log for step node
        if (RaceDetector.raceType == RaceDetector.RACE_TYPE_SET) {
            if (type == RACE_TASK_ASYNC) {
                set = new AccessSetHt();
            } else if (type == RACE_TASK_STEP) {
                if (!RaceDetector.itrOn)
                    set = new AccessSetHt();
                else
                    set = new AccessSetList();
            }
        }
        if (type != RACE_TASK_STEP) {
            children = new ArrayList();
        }
        thrdId = -1;
        scopeDepth = 0;
    }

    public void reAllocaLog() {
        // System.out.println("reallocate log for task!");
        if (type == RACE_TASK_ASYNC) {
            set = new AccessSetHt();
        } else if (type == RACE_TASK_STEP) {
            if (!RaceDetector.itrOn)
                set = new AccessSetHt();
            else
                set = new AccessSetList();
        }
    }

    public void allocateSet() {
        this.set = new AccessSetHt();
    }
}

// Task tree is built dynamically
class RaceTaskTree {
    protected RaceTask            root        = null;
    protected ArrayList<RaceTask> threadTasks = null;
    protected Hashtable           taskHt      = null;

    public RaceTaskTree() {}

    public RaceTaskTree(ArrayList<RaceTask> mapping) {
        // create the root node and the first step node
        this.root = new RaceTask(0, RaceTask.RACE_TASK_FINISH, 0, null);
        root.children.add(new RaceTask(1, RaceTask.RACE_TASK_STEP, 1, root));
        this.threadTasks = mapping;
        this.taskHt = new Hashtable();
        this.taskHt.put(0, root);
        this.taskHt.put(1, root.children.get(0));
    }

    public void print(boolean printLog) {
        print(root, printLog);
    }

    public void print(RaceTask rt, boolean printLog) {
        if (rt == null) {
            System.out.println("task print == null");
            return;
        }
        for (int i = 0; i < rt.depth; i++)
            System.out.print("    ");

        if (rt.type == RaceTask.RACE_TASK_FINISH)
            System.out.print("FINISH");
        else if (rt.type == RaceTask.RACE_TASK_STEP)
            System.out.print("STEP");
        else if (rt.type == RaceTask.RACE_TASK_ASYNC)
            System.out.print("ASYNC");
        else if (rt.type == RaceTask.RACE_TASK_COBEGIN)
            System.out.print("COBEGIN");
        else if (rt.type == RaceTask.RACE_TASK_DOTALL)
            System.out.print("DOTALL");
        else if (rt.type == RaceTask.RACE_TASK_PIPELINE)
            System.out.print("PIPELINE");
        else if (rt.type == RaceTask.RACE_TASK_FUTURE)
            System.out.print("FUTURE");
        else
            System.out.print("UNKNOWN");
        System.out.println("(id=" + rt.id + ",depth=" + rt.depth + ",thread="
                + rt.thrdId + ")");

        if (printLog && rt.set != null) {
            for (int i = 0; i < rt.depth; i++)
                System.out.print("    ");
            rt.set.print();
            System.out.println("");
        } else if (rt.set == null) {
            for (int i = 0; i < rt.depth; i++)
                System.out.print("    ");
            System.out.println("set=null");
        }
        if (rt.type != RaceTask.RACE_TASK_STEP)
            for (int j = 0; j < rt.children.size(); j++)
                print((RaceTask) (rt.children.get(j)), printLog);
    }

    // hash may be used to later for fast lookup
    public RaceTask lookup(int tid) {
        return (RaceTask) this.taskHt.get(tid);
    }

    protected RaceTask lookup2(RaceTask tree, int tid) {
        return (RaceTask) this.taskHt.get(tid);
    }

    // Ruby thread starts to execute a task
    public void taskExec(int thrdId, int taskId, int scopeDepth) {
        RaceTask task = lookup2(root, taskId);
        if (task == null) {
            System.out.println("[race]taskExec: thread " + thrdId
                    + "failed to find to task " + taskId);
            print(root, false);
            System.exit(-1);
        }

        if (task.type != RaceTask.RACE_TASK_ASYNC) {
            System.out.println("[race]taskExec: task " + taskId
                    + " is not AYNSC");
            print(root, false);
            System.exit(-1);
        }

        task.thrdId = thrdId;
        task.scopeDepth = scopeDepth;
        ((RaceTask) task.children.get(0)).scopeDepth = scopeDepth;

        taskMap(thrdId, (RaceTask) task.children.get(0));
    }

    // map Ruby thread to a task
    public void taskMap(int thrdId, RaceTask task) {
        task.thrdId = thrdId;
        threadTasks.set(thrdId, task);
    }

    public void taskMap(int thrdId, int taskId) {
        RaceTask task = lookup2(root, taskId);
        if (task != null)
            taskMap(thrdId, task);
        else {
            System.out.println("[race]taskmap: failed to find task " + taskId);
            print(root, false);
            System.exit(-1);
        }

    }

    // remap the thread back to its previous task
    public void taskReMap(int thrdId, int taskId) {
        RaceTask task = lookup2(root, taskId);
        if (task != null) {
            // thread should be mapped to last step node under the
            // correspongding async node
            for (int i = task.children.size() - 1; i >= 0; i--) {
                RaceTask child = (RaceTask) task.children.get(i);
                if (child.type == RaceTask.RACE_TASK_STEP) {
                    taskMap(thrdId, child);
                    return;
                }
            }
            // should not run to this point
            System.out
                    .println("[race]taskremap: failed to find step node under task "
                            + taskId);
            print(root, false);
            System.exit(-1);
        } else {
            System.out.println("[race]taskmap: failed to find task " + taskId);
            print(root, false);
            System.exit(-1);
        }
    }

    // parent task generate a new task
    // this version only create one step node
    public void taskFork2(int thrdId, int child, int itrNum) {
        RaceTask async = null;
        RaceTask step1 = null;
        RaceTask temp = null;

        RaceTask currTask = threadTasks.get(thrdId);
        RaceTask parentTask = currTask.parent;
        if (parentTask == null) {
            System.out
                    .println("[race]fork:can not find the parent task in the tree!");
            print(root, false);
            System.exit(-1);
        }
        // there should one finish node at the right most
        parentTask = (RaceTask) (parentTask.children.get(parentTask.children
                .size() - 1));
        // async node
        async = new RaceTask(child, RaceTask.RACE_TASK_ASYNC,
                parentTask.depth + 1, parentTask);
        // step node under the async node
        step1 = new RaceTask(child + 2, RaceTask.RACE_TASK_STEP,
                parentTask.depth + 2, async);
        parentTask.children.add(async);
        async.children.add(step1);

        this.taskHt.put(child, async);
        this.taskHt.put(child + 2, step1);
    }

    // create a new task for the "parent" task
    // used by piepeline
    public void taskFork(int thrdId, int parent, int child) {
        RaceTask async = null;
        RaceTask step1 = null;

        RaceTask parentTask = lookup(parent);
        if (parentTask == null) {
            System.out
                    .println("[race]fork:can not find the parent task in the tree!");
            print(root, false);
            System.exit(-1);
        }
        // async node
        async = new RaceTask(child, RaceTask.RACE_TASK_ASYNC,
                parentTask.depth + 1, parentTask);
        // step node under the async node
        step1 = new RaceTask(child + 2, RaceTask.RACE_TASK_STEP,
                parentTask.depth + 2, async);
        parentTask.children.add(async);
        async.children.add(step1);

        this.taskHt.put(child, async);
        this.taskHt.put(child + 2, step1);
    }

    // The execution of a task finishes
    public void taskJoin(int thrdId, int taskId) {
        if (RaceDetector.raceType != RaceDetector.RACE_TYPE_SPD3) {
            RaceTask task = lookup2(root, taskId);
            if (task == null) {
                System.out
                        .println("[race]join:can not find the child task in the tree!");
                print(root, false);
                System.exit(-1);
            }

            // merge all the logs for child at this moment
            // we do not need to merge logs for iterations, because all the logs
            // have
            // already be merged up at the end of iterations
            if (!(RaceDetector.itrOn && task.parent.type == RaceTask.RACE_TASK_DOTALL)) {
                AccessSet local = task.children.get(0).set;
                for (int i = 1; i < task.children.size(); i++) {
                    RaceTask right = (RaceTask) task.children.get(i);
                    //right.set.print();
                    //local.print();
                    local = local.merge(right.set);
                    right.set = null;
                }
                task.set = local;
            }
            task.children.clear();
        }
        threadTasks.set(thrdId, null);
    }

    // start a new doall,cobegin...
    // this version does not create step node
    public void startFinish2(int thrdId, int taskId, int type) {
        RaceTask finish = null;
        RaceTask parentTask = threadTasks.get(thrdId).parent;
        if (parentTask == null) {
            System.out
                    .println("[race]startFinish: can not find the parent task!");
            print(root, false);
            System.exit(-1);
        }

        // finish node
        if (type == RaceTask.RACE_TASK_FUTURE) {
            // Future starts a new enclosed space and no read/write sets are
            // maintained
            // for the top level tasks in this enclosed space.
            finish = new RaceTask(taskId, type, 0, parentTask);
        } else {
            finish = new RaceTask(taskId, type, parentTask.depth + 1,
                    parentTask);
        }

        parentTask.children.add(finish);
        finish.thrdId = thrdId;
        this.taskHt.put(taskId, finish);
    }

    // ending a finish
    public void endFinish(int thrdId, int finishId, int taskId) {
        RaceTask finish = lookup2(root, finishId);
        if (RaceDetector.raceType != RaceDetector.RACE_TYPE_SPD3
                && finish.type != RaceTask.RACE_TASK_PIPELINE) {
            // intersect and merge log
            AccessSet local = finish.children.get(0).set;
            //local.print();
            for (int i = 1; i < finish.children.size(); i++) {
                RaceTask right = (RaceTask) finish.children.get(i);
                //right.set.print();
                local = local.intersectAndMerge(right.set,
                        RaceTask.RACE_TASK_FINISH);
                right.set = null;
            }
            if (finish.depth <= 1) {
                local.clear();
            } else
                finish.set = local;

            finish.children.clear();
        }

        RaceTask step = null;
        if (finishId == 0) {
            step = new RaceTask(taskId, RaceTask.RACE_TASK_STEP, finish.depth,
                    root);
            // TODO: max nested depth
            step.scopeDepth = 8192;
            root.children.add(step);
        } else {
            step = new RaceTask(taskId, RaceTask.RACE_TASK_STEP, finish.depth,
                    finish.parent);
            step.scopeDepth = finish.parent.scopeDepth;
            finish.parent.children.add(step);
        }

        // TODO:remove all the children
        taskMap(thrdId, step);
    }

    // public static long[] itrNum = new long[1024];
    // private RaceSet preItrSet = null;
    public void startNewItr(long itr) {
        // this.print(root, false);
        if (!RaceDetector.itrOn)
            return;
        int thrdId = Scheduler.getCurrThId();
        // itrNum[thrdId * 16]++;
        RaceTask step = threadTasks.get(thrdId);

        if (step == null) {
            System.out
                    .println("[race]startNewItr:can not find the child task in the tree!");
            print(root, false);
            System.exit(-1);
        }
        RaceTask async = step.parent;
        if (async == null) {
            System.out
                    .println("[race]startNewItr:can not find the parent task in the tree!");
            print(root, false);

        }
        if (RaceDetector.raceType != RaceDetector.RACE_TYPE_SPD3) {
            // merge all the log for current iteration
            AccessSet local = async.children.get(0).set;
            for (int i = 1; i < async.children.size(); i++) {
                RaceTask right = (RaceTask) async.children.get(i);
                local = local.merge(right.set);
                right.set.clear();
                right.set = null;
            }

            async.set = async.set.intersectAndMerge(local,
                    RaceTask.RACE_TASK_ASYNC);
            step = async.children.get(0);
            async.children.clear();
            step.set.clear();
            async.children.add(step);
            taskMap(Scheduler.getCurrThId(), step);
        } else {
            RaceTask finish = async.parent;
            if (itr >= finish.children.size()) {
                print(false);
                System.exit(0);
            }
            RaceTask newAsync = finish.children.get((int) itr);
            newAsync.scopeDepth = async.scopeDepth;
            newAsync.children.get(0).scopeDepth = async.scopeDepth;
            taskMap(Scheduler.getCurrThId(), newAsync.children.get(0));
        }
    }

    private void printArray(ArrayList l) {
        for (Object e : l) {
            System.out.println(e);
        }
        System.out.println();
    }

    // public static long[] itrNum = new long[1024];
    // private RaceSet preItrSet = null;
    public void nextStage(long pipelineId, int stageNum) {
        RaceTask p = this.lookup((int) pipelineId);
        if (p == null) {
            System.out
                    .println("[race]nextStage:can not find the parent task in the tree!");
            print(root, false);
            System.exit(-1);
        }
        // make sure this is a pipeline
        if (p.type != RaceTask.RACE_TASK_PIPELINE) {
            System.out
                    .println("[race]nextStage:can not find the parent task in the tree!");
            print(root, false);
            System.exit(-1);
        }

        if (RaceDetector.raceType == RaceDetector.RACE_TYPE_SET) {
            // linear intersect and merge
            if (p.set == null) {
                p.allocateSet();
            }
            AccessSet local = new AccessSetHt();
            for (RaceTask c : p.children) {
                local.intersectAndMerge(c.set, c.type);
            }
            p.set.merge(local);
            // remove the children of current stage
            p.children.clear();
            
            //there must one step node before pipeline, 
            //find it and map to it
            RaceTask parent = p.parent;
            if(parent != null){
                if(parent.children.size() >= 2){
                    RaceTask step = parent.children.get(parent.children.size() - 2);
                    taskMap(Scheduler.getCurrThId(), step);
                }
            }
        }
        else if(RaceDetector.raceType == RaceDetector.RACE_TYPE_SPD3){
        	System.out.println("SPD3 will not run to this point!!!!!");
        }
    }

    // dynamic may happen parallel relation
    public boolean taskDmhp(RaceTask left, RaceTask right) {
        // recheck
        if (left == null || right == null)
            return true;

        if (left.depth > right.depth) {
            while (left != null && left.depth > right.depth)
                left = left.parent;
        }
        if (right.depth > left.depth) {
            while (right != null && right.depth > left.depth)
                right = right.parent;
        }

        while (left.parent != right.parent) {
            left = left.parent;
            right = right.parent;
        }
        // no need to check which one is on left

        if (left.type == RaceTask.RACE_TASK_ASYNC
                && right.type == RaceTask.RACE_TASK_ASYNC)
            return true;
        else
            return false;
    }

    public RaceTask lca(RaceTask left, RaceTask right) {
        if (left == null || right == null) {
            System.out.println("program should not run to this point!");
            System.exit(-1);
        }

        if (left.depth > right.depth) {
            while (left != null && left.depth > right.depth)
                left = left.parent;
        }
        if (right.depth > left.depth) {
            while (right != null && right.depth > left.depth)
                right = right.parent;
        }

        while (left.parent != right.parent) {
            left = left.parent;
            right = right.parent;
        }
        // decides which is left, which is right
        RaceTask parent = left.parent;

        return parent;
    }

    public int lca2(RaceTask left, RaceTask right) {
        if (left == null || right == null) {
            System.out.println("program should not run to this point!");
            System.exit(-1);
        }

        if (left.depth > right.depth) {
            while (left != null && left.depth > right.depth)
                left = left.parent;
        }
        if (right.depth > left.depth) {
            while (right != null && right.depth > left.depth)
                right = right.parent;
        }

        while (left.parent != right.parent) {
            left = left.parent;
            right = right.parent;
        }
        // decides which is left, which is right
        RaceTask parent = left.parent;

        return parent.depth;
    }
}
