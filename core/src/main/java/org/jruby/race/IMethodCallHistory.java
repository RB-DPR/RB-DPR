package org.jruby.race;

import org.jruby.internal.runtime.methods.DynamicMethod;
import org.jruby.runtime.builtin.IRubyObject;

public interface IMethodCallHistory {
    //a call with no parameter
    public void addMethodCall(DynamicMethod method, IRubyObject rtn);
    //a call with one parameter
    public void addMethodCall(DynamicMethod method, IRubyObject para, IRubyObject rtn);
    //a call with a parameter list
    public void addMethodCall(DynamicMethod method, IRubyObject[] paras,
         IRubyObject rtn);
    // Intersect two lists
    public void intersect(IMethodCallHistory right);
    // merge two lists together
    public void merge(IMethodCallHistory right);
}
