/**
 * DPR NOTE: This class is designed for ACOp commutativity checks on function
 * call levels. Currently DPR does not support annotations on this level,
 * therefore this class is never used.
 */
package org.jruby.race;

import java.util.ArrayList;
import org.jruby.internal.runtime.methods.DynamicMethod;
import org.jruby.runtime.builtin.IRubyObject;

public class AccessSetMethod implements IMethodCallHistory {
    // the methods that are executed
    private ArrayList<DynamicMethod> methods;
    // One method may be invoked many times.
    // Each element in the array args is one of the following types:
    // null: the method is called with no parameter
    // object: the method is called with one parameter
    // array: the method is called with more than one parameters.
    private ArrayList<Object> args;

    // return value of the method
    private ArrayList<IRubyObject> rtns;

    // Fast path for simple methods
    private long methodMask = 0;

    public AccessSetMethod() {
        System.out.println("ERROR: AccessSetMethod created! ");
        this.methods = new ArrayList<DynamicMethod>();
        this.args = new ArrayList<Object>();
        this.rtns = new ArrayList<IRubyObject>();
    }

    // constructor for a method with a parameter list
    public AccessSetMethod(DynamicMethod method, IRubyObject[] args, IRubyObject rtn) {
        System.out.println("ERROR: AccessSetMethod created! ");
        this.methods = new ArrayList<DynamicMethod>();
        this.methods.add(method);
        this.args = new ArrayList<Object>();
        this.args.add(args);
        this.rtns = new ArrayList<IRubyObject>();
        this.rtns.add(rtn);
    }

    // constructor for a method with no parameter
    public AccessSetMethod(DynamicMethod method, IRubyObject rtn) {
        System.out.println("ERROR: AccessSetMethod created! ");
        this.methods = new ArrayList<DynamicMethod>();
        this.methods.add(method);
        this.args = new ArrayList<Object>();
        this.args.add(null);
        this.rtns = new ArrayList<IRubyObject>();
        this.rtns.add(rtn);
    }

    // constructor for a method with one parameter
    public AccessSetMethod(DynamicMethod method, IRubyObject para, IRubyObject rtn) {
        System.out.println("ERROR: AccessSetMethod created! ");
        this.methods = new ArrayList<DynamicMethod>();
        this.methods.add(method);
        this.args = new ArrayList<Object>();
        this.args.add(para);
        this.rtns = new ArrayList<IRubyObject>();
        this.rtns.add(rtn);
    }

    public ArrayList<DynamicMethod> getMethods() {
        return this.methods;
    }

    public void setArgs(ArrayList<Object> args) {
        this.args = args;
    }

    public ArrayList<Object> getArgs() {
        return this.args;
    }

    public ArrayList<IRubyObject> getRtns() {
        return this.rtns;
    }

    // a call with no parameter
    public void addMethodCall(DynamicMethod method, IRubyObject rtn) {
        this.methods.add(method);
        this.args.add(null);
        this.rtns.add(rtn);
        // If there's no arguments, we don't need to make any implicit calls
    }

    // a call with one parameter
    public void addMethodCall(DynamicMethod method, IRubyObject para, IRubyObject rtn) {
        this.methods.add(method);
        this.args.add(para);
        this.rtns.add(rtn);
    }

    // a call with a parameter list
    public void addMethodCall(DynamicMethod method, IRubyObject[] paras, IRubyObject rtn) {
        // make sure that there is no duplicated parameter list in args
        /*
         * if(!(methods.get(i).equals(method))) continue; //check the size of
         */
        this.methods.add(method);
        this.args.add(paras);
        this.rtns.add(rtn);
    }

    // a call with one parameter
    // this is for merge operation
    private void addMethodCall(DynamicMethod method, Object arg, IRubyObject rtn) {
        // make sure that there is no duplicated parameter list in args
        this.methods.add(method);
        this.args.add(arg);
        this.rtns.add(rtn);
    }

    public long getMethodMask() {
        return this.methodMask;
    }

    // Note: AccessSetMethod can only work with other AccessSetMethods
    public void intersect(IMethodCallHistory incomingRight) {
        AccessSetMethod right = (AccessSetMethod) incomingRight;
        // System.out.println("Commutative intersect:");
        // this.print();
        // right.print();
        System.out.println("AccessSetMehod::intersect: num1=" + this.methods.size() + ", num2="
                + right.getMethods().size());
        for (int i = 0; i < right.methods.size(); i++) {
            DynamicMethod methodR = right.methods.get(i);
            Object argsR = right.args.get(i);
            IRubyObject rtnR = right.rtns.get(i);
            for (int j = 0; j < this.methods.size(); j++) {
                DynamicMethod methodL = this.methods.get(j);
                // if (methodL.equals(methodR)) {
                Object argsL = this.args.get(j);
                IRubyObject rtnL = this.rtns.get(j);
                // check if the two are
                if (argsL instanceof IRubyObject[] && argsR instanceof IRubyObject[]) {
                    if (!(methodL.getImplementationClass().isCommutative(methodL, methodR,
                            (IRubyObject[]) argsL, (IRubyObject[]) argsR, rtnL, rtnR))) {
                        RaceDetector.commRecord(methodL, methodR);
                    }
                } else if (argsR instanceof IRubyObject[]) {
                    IRubyObject[] temp = new IRubyObject[1];
                    temp[0] = (IRubyObject) argsL;
                    if (!(methodL.getImplementationClass().isCommutative(methodL, methodR,
                            (IRubyObject[]) temp, (IRubyObject[]) argsR, rtnL, rtnR))) {
                        RaceDetector.commRecord(methodL, methodR);
                    }
                } else if (argsL instanceof IRubyObject[]) {
                    IRubyObject[] temp = new IRubyObject[1];
                    temp[0] = (IRubyObject) argsR;
                    if (!(methodL.getImplementationClass().isCommutative(methodL, methodR,
                            (IRubyObject[]) argsL, (IRubyObject[]) argsL, rtnL, rtnR))) {
                        RaceDetector.commRecord(methodL, methodR);
                    }
                } else {
                    if (!(methodL.getImplementationClass().isCommutative(methodL, methodR,
                            (IRubyObject) argsL, (IRubyObject) argsR, rtnL, rtnR))) {
                        RaceDetector.commRecord(methodL, methodR);
                    }
                }
                // }
            }
        }
    }

    // merge two lists together
    // Note: AccessSetMethod can only work with other AccessSetMethods
    public void merge(IMethodCallHistory incomingRight) {
        AccessSetMethod right = (AccessSetMethod) incomingRight;
        System.out.println("AccessSetMehod::merge: num1=" + this.methods.size() + ", num2="
                + right.getMethods().size());
        for (int i = 0; i < right.methods.size(); i++) {
            this.addMethodCall(right.methods.get(i), right.args.get(i), right.rtns.get(i));
        }
    }

    public void print() {
        for (int i = 0; i < this.methods.size(); i++) {
            System.out.println("From method print: " + this.methods.get(i).getName());
        }
    }

}
