package org.jruby.parlib;

import java.util.ArrayList;

import org.jruby.Ruby;
import org.jruby.RubyBoolean;
import org.jruby.RubyProc;
import org.jruby.runtime.ThreadContext;
import org.jruby.runtime.builtin.IRubyObject;

/* Defines an element in a commutative table
 * 
 */
public class ComTableItem {
    // Type of such item
    protected int type;
    // Depends on type, the content can be a boolean value, a predefined expr, or a lambda function
    protected Object content;
    
    public static final int COM_TABLE_TYPE_BOOL = 0;
    public static final int COM_TABLE_TYPE_LAMBDA = 1;
    
    public int getType() {
        return this.type;
    }
    
    public Object getContent() {
        return this.content;
    }
    
    public boolean needEval() {
        return this.type == COM_TABLE_TYPE_LAMBDA;
    }
    
    // Constructor
    public ComTableItem(int type, Object content) {
        this.type = type;
        this.content = content;
    }
    
    // Evaluate whether the incoming method calls are commutative
    public boolean evaluate(IRubyObject arg0, IRubyObject arg1,
                            IRubyObject result0, IRubyObject result1) {
        switch (this.type) {
            case COM_TABLE_TYPE_BOOL:
                return (Boolean) content;
            case COM_TABLE_TYPE_LAMBDA:
                ThreadContext context = ((RubyProc) content).getRuntime().getCurrentContext();
                IRubyObject[] args = {arg0, arg1, result0, result1};
                return ((RubyProc) content).call(context, args).isTrue();
        }
        return false;
    }
    
    // Evaluate whether the incoming method calls are commutative, for multiple arguments
    public boolean evaluate(IRubyObject[] args0, IRubyObject[] args1,
                            IRubyObject result0, IRubyObject result1) {
        switch (this.type) {
            case COM_TABLE_TYPE_BOOL:
                return (Boolean) content;
            case COM_TABLE_TYPE_LAMBDA:
                ThreadContext context = ((RubyProc) content).getRuntime().getCurrentContext();
                // Set up a new information array
                ArrayList<IRubyObject> args = new ArrayList<IRubyObject>(args0.length + args1.length + 2);
                for (int i = 0; i < args0.length; i++) {
                    args.add(args0[i]);
                }
                for (int i = 0; i < args1.length; i++) {
                    args.add(args1[i]);
                }
                args.add(result0);
                args.add(result1);
                
                IRubyObject[] flattenedArgs = (IRubyObject[])args.toArray();
                return ((RubyProc) content).call(context, flattenedArgs).isTrue();
        }
        return false;
    }
}
