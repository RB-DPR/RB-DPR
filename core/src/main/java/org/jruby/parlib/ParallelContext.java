package org.jruby.parlib;

// For controlling task joins
public class ParallelContext {
    // Total number of tasks
    protected int numTasks = 0;
    // Total number of tasks arrived
    protected int arrived = 0;
    
    public ParallelContext(int numTasks) {
        this.numTasks = numTasks;
    }
    
    public ParallelContext() {}
    
    // Add a new task to this context (task dispatch has to be sequential)
    public void addTask() {
        ++(this.numTasks);
    }
    
    // Another arrived task (synchronized)
    public void arrive() {
        synchronized(this) {
            arrived++;
        }
    }
    
    public boolean finished() {
        return (numTasks == arrived);
    }
    
    public int numTasks() {
        return this.numTasks;
    }
    
    public int arrived() {
        return this.arrived;
    }
    
    // Block the current task execution and wait for all tasks arrive
    public void join() {
        int batchNo = 0;
        while (!this.finished()) {
            ++batchNo;
            batchNo %= WorkerThread.CHANGE_INTERVAL;
            WorkerThread.getAndExecuteTask(batchNo);
        }
    }
    
    // Reset this context in case of integer overflow (has to be synchronized
    // if necessary)
    public void reset() {
        this.numTasks = 0;
        this.arrived = 0;
    }
}
