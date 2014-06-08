package org.jruby.parlib;

import org.jruby.anno.JRubyClass;
import org.jruby.anno.JRubyMethod;

import org.jruby.Ruby;
import org.jruby.RubyFixnum;
import org.jruby.RubyFloat;
import org.jruby.RubyInteger;
import org.jruby.RubyObject;
import org.jruby.RubyClass;
import org.jruby.RubyProc;
import org.jruby.race.RaceDetector;
import org.jruby.race.RaceTask;
import org.jruby.runtime.Block;
import org.jruby.runtime.ObjectAllocator;
import org.jruby.runtime.ThreadContext;
import org.jruby.runtime.builtin.IRubyObject;

/**
 * Ruby reduction, for pre-defined commutative and associative reduction
 * operations
 * 
 * @author lilu
 */
@JRubyClass(name="RubyReduction")
public class RubyReduction extends RubyObject {
    
    public static final int REDUCTION_ADD = 0;
    public static final int REDUCTION_MULTIPLY = 1;
    public static final int REDUCTION_MAX = 2;
    public static final int REDUCTION_MIN = 3;
    //public static final int REDUCTION_MAX_IDX = 4;
    //public static final int REDUCTION_MIN_IDX = 5;
    
    protected double[] valuePerTh;
    //private IRubyObject result;
    protected int operator;
    
    //for race detection
    protected RaceTask parent = null;
    
    public RaceTask getParent(){
    	return parent;
    }
    public void setParent(RaceTask t){
    	parent = t;
    }
    
    
    private RubyReduction(Ruby runtime, RubyClass type) {
        super(runtime, type);
    }
    
    // Called before a split, initialize
    @JRubyMethod(name = {"new", "init"}, meta = true)
    public static IRubyObject newInstance(ThreadContext context, IRubyObject recv, IRubyObject arg, Block block) {
        RubyReduction r = (RubyReduction)((RubyClass) recv).allocate();
        int op = RubyInteger.num2int(arg);
        r.setupReduction(op);
        // System.out.println("Reduction init, size = "+r.valuePerTh.length);
        return r;
    }
    
    // Called in a split
    @JRubyMethod(name = {"push", "add"})
    public void push(ThreadContext context, IRubyObject arg) {
    	if(!RaceDetector.turnOff){
        	RaceDetector.notifyReductionPush(this);
        }
    	int thid = Scheduler.getCurrThId();
        // Ugly impl. for floats and ints
        double incomingVal = 0.0;
        if (arg instanceof RubyFloat) {
            incomingVal = ((RubyFloat) arg).getDoubleValue();
        } else if (arg instanceof RubyFixnum) {
            incomingVal = ((RubyFixnum) arg).getDoubleValue();
        }
        valuePerTh[thid] = updateRubyObject(context, valuePerTh[thid], incomingVal);
    }
    
    // Called after all iterations are done
    @JRubyMethod(name = {"pop", "get"})
    public IRubyObject pop(ThreadContext context) {
    	if(!RaceDetector.turnOff){
        	RaceDetector.notifyReductionGet(this);
        }
    	if (valuePerTh.length == 0) {
            System.out.println("Something wrong: valuePerTh.length == 0!");
        }
        double returned = valuePerTh[0];
        for (int i = 1; i < valuePerTh.length; i++) {
            returned = updateRubyObject(context, returned, valuePerTh[i]);
        }
        return RubyFloat.newFloat(context.runtime, returned);
    }
    
    // Private method to setup an reduction
    private void setupReduction(int op) {
        int poolSize = Scheduler.getScheduler().getPoolSize();
        valuePerTh = new double[poolSize];
        for (int i = 0; i < poolSize; i++) {
            switch(op) {
            case REDUCTION_ADD:
                valuePerTh[i] = 0;
                break;
            case REDUCTION_MULTIPLY:
                valuePerTh[i] = 1;
                break;
            case REDUCTION_MAX:
                valuePerTh[i] = Double.NEGATIVE_INFINITY;
                break;
            case REDUCTION_MIN:
                valuePerTh[i] = Double.POSITIVE_INFINITY;
                break;
            default:
                valuePerTh[i] = 0;
            }
        }
        operator = op;
    }
    
    // Private method to reduce data
    private double updateRubyObject(ThreadContext context, double accu, double incomingVal) {
        double returned;
        switch(operator) {
        case REDUCTION_ADD:
            returned = accu + incomingVal;
            break;
        case REDUCTION_MULTIPLY:
            returned = accu * incomingVal;
            break;
        case REDUCTION_MAX:
            returned = (incomingVal > accu) ? incomingVal : accu;
            break;
        case REDUCTION_MIN:
            returned = (incomingVal < accu) ? incomingVal : accu;
            break;
        default:
            returned = accu;
        }
        return returned;
    }
    
    public static RubyClass createDPRReductionClass(Ruby runtime) {
        RubyClass cReduction = runtime.defineClass("Reduction", runtime.getObject(), new ObjectAllocator() {
            public IRubyObject allocate(Ruby runtime, RubyClass klass) {
                return new RubyReduction(runtime, klass);
            }
        });
        cReduction.setReifiedClass(RubyReduction.class);
        cReduction.defineAnnotatedMethods(RubyReduction.class);
        // Define constants
        cReduction.defineConstant("ADD", RubyFixnum.newFixnum(runtime, REDUCTION_ADD));
        cReduction.defineConstant("MULTIPLY", RubyFixnum.newFixnum(runtime, REDUCTION_MULTIPLY));
        cReduction.defineConstant("MAX", RubyFixnum.newFixnum(runtime, REDUCTION_MAX));
        cReduction.defineConstant("MIN", RubyFixnum.newFixnum(runtime, REDUCTION_MIN));
        return cReduction;
    }
}
