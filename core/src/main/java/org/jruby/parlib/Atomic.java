package org.jruby.parlib;

import org.jruby.runtime.ThreadContext;
import org.jruby.runtime.builtin.IRubyObject;
import org.jruby.RubyProc;

// import org.jruby.race.RaceDetector;
// import org.jruby.race.RaceTask;


public class Atomic {
    public static void atomicStart(ThreadContext context, IRubyObject recv,
        IRubyObject arg) {
        // Get thread manager
        Scheduler currManager = context.getRuntime().getThreadService()
                .getParLibThreadMgr();
        // ParallelContext pContext = currManager.getCurrentTask(Thread.currentThread()).getParallelContext();
        // Potential race detector code here?
        
        // arg should be a lambda operation. Execute it with synchronization
        synchronized (currManager) {
            ((RubyProc) arg).call(context, IRubyObject.NULL_ARRAY);
        }
    }
}
