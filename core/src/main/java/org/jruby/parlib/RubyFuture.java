package org.jruby.parlib;

import org.jruby.anno.JRubyClass;
import org.jruby.anno.JRubyMethod;

import org.jruby.Ruby;
import org.jruby.RubyObject;
import org.jruby.RubyClass;
import org.jruby.RubyProc;
import org.jruby.race.RaceDetector;
import org.jruby.race.RaceTask;
import org.jruby.runtime.ObjectAllocator;
import org.jruby.runtime.ThreadContext;
import org.jruby.runtime.builtin.IRubyObject;

/**
 * Ruby future
 * 
 * @author lilu
 */
@JRubyClass(name = "RubyFuture")
public class RubyFuture extends RubyObject {
    private RubyProc proc;
    private IRubyObject[] args;
    private IRubyObject result;
    private FutureTask task;
    private int futureId;

    private RubyFuture(Ruby runtime, RubyClass type) {
        super(runtime, type);
    }

    private int getTaskId() {
        return this.futureId;
    }

    private void setTaskId(int id) {
        this.futureId = id;
    }

    // Called by a future object, initialize
    @JRubyMethod(name = { "new", "init" }, meta = true, required = 1, rest = true)
    public static IRubyObject newInstance(ThreadContext context, IRubyObject recv,
            IRubyObject[] args) {
        // Check errors
        if (args.length < 1) {
            System.out.println("Error: Future needs to be initialized with a Proc and arguments");
            return context.nil;
        }

        // Init future
        RubyFuture f = (RubyFuture) ((RubyClass) recv).allocate();
        // Set the operation of this future
        f.proc = (RubyProc) args[0];
        // Store all arguments
        f.args = new IRubyObject[args.length - 1];
        System.arraycopy(args, 1, f.args, 0, f.args.length);
        // Post a task according to this object
        // Set up thread manager, current thread and parallel context
        Scheduler currManager = context.getRuntime().getThreadService().getParLibThreadMgr();
        Thread th = Thread.currentThread();
        ParallelContext futureContext = new ParallelContext();
        // Set up a new task
        FutureTask task = new FutureTask(Scheduler.getThId(th), currManager.getSeqNo(th),
                currManager.getCurrentLevel(th) + 1, f.proc, f.args, currManager, futureContext);
        futureContext.addTask();

        // Notify the race detector to create a new future node
        if (!RaceDetector.turnOff) {
            int futureId = (Scheduler.getThId(th) << 26) + currManager.getSeqNo(th);
            RaceDetector.notifyStartAll(futureId, RaceTask.RACE_TASK_FUTURE);
            // the futureId is used when the caller tries to retrieve the result
            f.setTaskId(futureId);
            // create the only async task for the future
            task.setScopeDepth(context.getCurrentScope().getDepth());
            RaceDetector.notifyTaskFork(futureId, task.getGuid(), 1);
        }
        Scheduler.enqueueTask(th, task);
        f.task = task;
        System.out.println("Future init");

        return f;
    }

    // Caller to retrieve the result of the future
    @JRubyMethod(name = "get")
    public IRubyObject getResult(ThreadContext context) {
        // Now we need to join
        ParallelContext pContext = this.task.getParallelContext();
        pContext.join();

        // ending a finish
        if (!RaceDetector.turnOff) {
            RaceDetector.notifyEndAll(this.futureId, this.futureId + 2);
        }

        return this.task.getResult();
    }

    public static RubyClass createDPRFutureClass(Ruby runtime) {
        RubyClass cFuture = runtime.defineClass("Future", runtime.getObject(),
                new ObjectAllocator() {
                    public IRubyObject allocate(Ruby runtime, RubyClass klass) {
                        return new RubyFuture(runtime, klass);
                    }
                });
        cFuture.setReifiedClass(RubyFuture.class);
        cFuture.defineAnnotatedMethods(RubyFuture.class);
        return cFuture;
    }
}
