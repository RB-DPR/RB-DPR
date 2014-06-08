/**********************************************************************
 * 
 * 
 * Copyright (C) 2013
 **********************************************************************/
package org.jruby.race;

import java.util.HashSet;

import org.jruby.internal.runtime.methods.DynamicMethod;
import org.jruby.runtime.builtin.IRubyObject;

public class AccessSetSimpleMethod implements IMethodCallHistory {

    // Fast path for simple methods, only mark down methods called in a task
    private HashSet<DynamicMethod> methodsCalled;

    public AccessSetSimpleMethod() {
        this.methodsCalled = new HashSet<DynamicMethod>();
    }

    private void addCall(DynamicMethod method) {
        methodsCalled.add(method);
    }

    // a call with no parameter
    public void addMethodCall(DynamicMethod method, IRubyObject rtn) {
        this.addCall(method);
    }

    // a call with one parameter
    public void addMethodCall(DynamicMethod method, IRubyObject para,
            IRubyObject rtn) {
        this.addCall(method);
    }

    // a call with a parameter list
    public void addMethodCall(DynamicMethod method, IRubyObject[] paras,
            IRubyObject rtn) {
        this.addCall(method);
    }

    public HashSet<DynamicMethod> getMethodsCalled() {
        return this.methodsCalled;
    }

    public void intersect(IMethodCallHistory incomingRight) {
        AccessSetSimpleMethod right = (AccessSetSimpleMethod) incomingRight;
        for (DynamicMethod methodL : this.methodsCalled) {
            for (DynamicMethod methodR : right.getMethodsCalled()) {
                if (!methodL.isCommutativeAnnotated() || !methodR.isCommutativeAnnotated()) {
                    continue;
                }
                if (!methodL.getImplementationClass().isCommutative(methodL,
                        methodR)) {
                    RaceDetector.commRecord(methodL, methodR);
                }
            }
        }
    }

    // Merge two lists together
    public void merge(IMethodCallHistory incomingRight) {
        AccessSetSimpleMethod right = (AccessSetSimpleMethod) incomingRight;
        this.methodsCalled.addAll(right.getMethodsCalled());
    }
}
