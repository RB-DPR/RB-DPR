package org.jruby.parlib;

import static org.jruby.CompatVersion.RUBY1_8;

import org.jruby.anno.JRubyClass;
import org.jruby.anno.JRubyMethod;

import org.jruby.Ruby;
import org.jruby.RubyObject;
import org.jruby.RubyClass;
import org.jruby.runtime.ObjectAllocator;
import org.jruby.runtime.ThreadContext;
import org.jruby.runtime.builtin.IRubyObject;

/**
 * The predefined module to operate a stream
 * @author lilu
 *
 */
@JRubyClass(name="RubyStreamable")
public class RubyStreamable extends RubyObject {
    
    private RubyStreamable(Ruby runtime, RubyClass type) {
        super(runtime, type);
    }
    
    @JRubyMethod(name = {"getAndMove"})
    public IRubyObject getCurrentAndMoveNext(ThreadContext context) {
        return context.getRuntime().getNil();
    }
    
    @JRubyMethod(name = {"setAndMove"}, required = 1)
    public IRubyObject setCurrentAndMoveNext(ThreadContext context, IRubyObject arg) {
        return context.getRuntime().getNil();
    }
    
    public static RubyClass createDPRStreamClass(Ruby runtime) {
        RubyClass mStreamable = runtime.defineClass("PipeStream", runtime.getObject(), new ObjectAllocator() {
            public IRubyObject allocate(Ruby runtime, RubyClass klass) {
                return new RubyStreamable(runtime, klass);
            }
        });
        mStreamable.defineAnnotatedMethods(RubyStreamable.class);
        mStreamable.setReifiedClass(RubyStreamable.class);
        return mStreamable;
    }
    
}
