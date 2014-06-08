package org.jruby.parlib;

import java.util.ArrayList;

import org.jruby.runtime.builtin.IRubyObject;

public class SomeTrueContext extends ParallelContext {
    // All tasks in this some true context
    private ArrayList<SomeTrueTask> tasks;
    // The result of this .someTrue call
    private volatile IRubyObject result = null;

    public SomeTrueContext(int numTasks) {
        super(numTasks);
        this.tasks = new ArrayList<SomeTrueTask>();
    }

    public SomeTrueContext() {
        this.tasks = new ArrayList<SomeTrueTask>();
    }
    
    // Add a task in
    public void addTask(SomeTrueTask task) {
        this.tasks.add(task);
        super.addTask();
    }
    
    // Report an error if this method is called
    public void addTask() {
        System.out.println("Error: Wrong addTask called! ");
    }
    
    // The task is done if a result is found or all tasks are done. 
    public boolean finished() {
        // System.out.println("result: "+this.result);
        return (this.result != null) || super.finished();
    }
    
    public void arrive(IRubyObject result) {
        if (result != null) {
            // We found something
            // TODO: Trying to avoid locks, is this right?
            this.result = result;
            // Disable all tasks
            for (SomeTrueTask t : this.tasks) {
                t.disable();
            }
        }
        // Mark as arrived
        super.arrive();
    }
    
    // Return the result of this context
    public IRubyObject getResult() {
        if (this.result != null) {
            return this.result;
        }
        //System.out.println("SomeTrue results null");
        return null;
    }
    
}
