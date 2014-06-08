package org.jruby.parlib;

import java.util.ArrayList;

import org.jruby.race.AccessSet;
import org.jruby.race.RaceTask;
import org.jruby.race.RaceTaskOOB;

public class PLibLogTask {
    // Basic information
    protected int guid;
    protected int prio;
    protected AccessSet left;
    protected AccessSet right;
    protected ArrayList<AccessSet> list;
    protected RaceTask dest;
    protected long size = 0;

    // Priority values
    // Priority order: urgent > local task > high > other's task > medium > low
    // low priority task cannot be stolen
    public static final int LOG_TASK_PRIO_LOW = 0;
    public static final int LOG_TASK_PRIO_MEDIUM = 1;
    public static final int LOG_TASK_PRIO_HIGH = 2;
    public static final int LOG_TASK_PRIO_MAX = 2;
    // Urgent should not be abused. This level of tasks will be pushed to a
    // central queue
    public static final int LOG_TASK_PRIO_URGENT = LOG_TASK_PRIO_MAX + 1;

    protected PLibLogTask() {
    }

    public PLibLogTask(int id) {
        this(id, LOG_TASK_PRIO_MEDIUM, null, null, null);
    }

    public PLibLogTask(int id, int prio, AccessSet logLeft, AccessSet logRight,
            RaceTask dest) {
        this.guid = id;
        this.prio = prio;
        this.left = logLeft;
        this.size += logLeft.size();
        this.right = logRight;
        this.size += logRight.size();
        this.dest = dest;
    }

    public PLibLogTask(int id, int prio, ArrayList<AccessSet> list,
            RaceTask dest) {
        this.guid = id;
        this.prio = prio;
        this.list = list;
        this.dest = dest;
        for (AccessSet s : list) {
            this.size += s.size();
        }
    }

    public int getGuid() {
        return this.guid;
    }

    public int getPriority() {
        return this.prio;
    }

    public long getSize() {
        return this.size;
    }

    // Execute the task
    public void exec() {
        // Mark memory as release
        Scheduler currScheduler = Scheduler.getScheduler();
        currScheduler.releaseLog(Thread.currentThread(), this.size);
        // System.out.println("log tasks are executed!");
        AccessSet result = null;
        if (left != null && right != null) {
            // setLeft.print();
            // setRight.print();
            if (right.getWorkType() == AccessSet.LOG_WORK_MERGE) {
                // System.out.println("work merge");
                result = left.merge(right);
            } else if (right.getWorkType() == AccessSet.LOG_WORK_HYBRID) {
                // System.out.println("work i&m");
                result = left.intersectAndMerge(right, dest.type);
            } else {
                System.out.println("Unknown set task type !");
                System.exit(0);
            }

            result.setMergeNum(left.getMergeNum() + right.getMergeNum());
            ((RaceTaskOOB) dest).pushSet(result);
        } else if (list != null && list.size() > 0) {
        	//batch processing
            
            result = list.get(0);
            AccessSet temp = null;
            int total = result.getMergeNum();
            for (int i = 1; i < list.size(); i++) {
                temp = list.get(i);
                total += temp.getMergeNum();
                if (temp.getWorkType() == AccessSet.LOG_WORK_MERGE) {
                    // System.out.println("work merge");
                    result = result.merge(temp);
                } else if (temp.getWorkType() == AccessSet.LOG_WORK_HYBRID) {
                    // System.out.println("work i&m");
                    result = result.intersectAndMerge(temp, dest.type);
                } else {
                    System.out.println("Unknown set task type !");
                    System.exit(0);
                }

                // System.out.println("exe num=" + temp.getMergeNum());
            }
            result.setMergeNum(total);
            ((RaceTaskOOB) dest).pushSet(result);
        }
    }
}
