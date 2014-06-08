package org.jruby.race;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import org.jruby.internal.runtime.methods.DynamicMethod;
import org.jruby.runtime.builtin.IRubyObject;

public class AccessSetDetailSimpleMethod implements IMethodCallHistory {

    // Fast path for simple methods
    private HashMap<DynamicMethod, MemoryOprInfo> methodsCalled;

    public AccessSetDetailSimpleMethod() {
        this.methodsCalled = new HashMap<DynamicMethod, MemoryOprInfo>();
    }

    private void addCall(DynamicMethod method, MemoryOprInfo info) {
        methodsCalled.put(method, info);
    }

    // a call with no parameter
    public void addMethodCall(DynamicMethod method, IRubyObject rtn, MemoryOprInfo info) {
        this.addCall(method, info);
    }

    // a call with one parameter
    public void addMethodCall(DynamicMethod method, IRubyObject para, IRubyObject rtn,
            MemoryOprInfo info) {
        this.addCall(method, info);
    }

    // a call with a parameter list
    public void addMethodCall(DynamicMethod method, IRubyObject[] paras, IRubyObject rtn,
            MemoryOprInfo info) {
        this.addCall(method, info);
    }

    public HashMap<DynamicMethod, MemoryOprInfo> getMethodsCalled() {
        return this.methodsCalled;
    }

    /**
     * Intersecting with another simple method history under detail mode
     */
    public void intersect(IMethodCallHistory incomingRight) {
        AccessSetDetailSimpleMethod right = (AccessSetDetailSimpleMethod) incomingRight;
        Set<DynamicMethod> rKeys = right.methodsCalled.keySet();
        Iterator<DynamicMethod> itrRight = rKeys.iterator();
        while (itrRight.hasNext()) {
            DynamicMethod rMethod = (DynamicMethod) itrRight.next();
            MemoryOprInfo rInfo = right.methodsCalled.get(rMethod);
            Set<DynamicMethod> lKeys = this.methodsCalled.keySet();
            Iterator<DynamicMethod> itrLeft = lKeys.iterator();
            // Keep iterating on all receivers
            while (itrLeft.hasNext()) {
                DynamicMethod lMethod = (DynamicMethod) itrLeft.next();
                MemoryOprInfo lInfo = this.methodsCalled.get(lMethod);
                if (!rMethod.isCommutativeAnnotated() || !lMethod.isCommutativeAnnotated()) {
                    continue;
                }
                // Check commutativity, and mark down
                if (!lMethod.getImplementationClass().isCommutative(lMethod, rMethod)) {
                    RaceDetector.commRecord(lMethod, rMethod, lInfo, rInfo);
                }
            }
        }
    }

    // Merge two lists together
    public void merge(IMethodCallHistory incomingRight) {
        AccessSetDetailSimpleMethod right = (AccessSetDetailSimpleMethod) incomingRight;
        Set<DynamicMethod> rKeys = right.methodsCalled.keySet();
        Iterator<DynamicMethod> itrRight = rKeys.iterator();
        while (itrRight.hasNext()) {
            DynamicMethod rMethod = (DynamicMethod) itrRight.next();
            MemoryOprInfo rInfo = right.methodsCalled.get(rMethod);
            if (this.methodsCalled.get(rMethod) == null) {
                this.methodsCalled.put(rMethod, rInfo);
            }
        }
    }

    // Unimplemented default methods
    @Override
    public void addMethodCall(DynamicMethod method, IRubyObject rtn) {
        System.out.println("ERROR: Wrong method called for AccessSetDetailSimple");
    }

    // Unimplemented default methods
    @Override
    public void addMethodCall(DynamicMethod method, IRubyObject para, IRubyObject rtn) {
        System.out.println("ERROR: Wrong method called for AccessSetDetailSimple");
    }

    // Unimplemented default methods
    @Override
    public void addMethodCall(DynamicMethod method, IRubyObject[] paras, IRubyObject rtn) {
        System.out.println("ERROR: Wrong method called for AccessSetDetailSimple");
    }
}
