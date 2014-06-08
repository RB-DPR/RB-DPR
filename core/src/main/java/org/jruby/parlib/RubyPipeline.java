package org.jruby.parlib;

import static org.jruby.CompatVersion.RUBY1_8;

import org.jruby.anno.JRubyClass;
import org.jruby.anno.JRubyMethod;

import org.jruby.Ruby;
import org.jruby.RubyBoolean;
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
 * Ruby pipeline, for streaming operations
 * operations
 * 
 * @author lilu
 */
@JRubyClass(name="RubyPipeline")
public class RubyPipeline extends RubyObject {
    
    //for race detection
    protected RaceTask parent = null;
    // Member variables
    protected RubyProc[] stages = null;
    // Input stream
    protected RubyStreamable iStream = null;
    // Output stream
    protected RubyStreamable oStream = null;
    // Total number of stages in this pipeline. Note stagePos may not be
    // stages.length - 1
    protected int stagePos = 0;
    // My parallel context
    protected ParallelContext parallelContext = null;
    // RubyPipeline class singleton, this object is used by other classes to 
    // create pipeline instances
    protected static RubyClass myClass = null;
    
    public RaceTask getParent(){
        return parent;
    }
    public void setParent(RaceTask t){
        parent = t;
    }
    
    private RubyPipeline(Ruby runtime, RubyClass type) {
        super(runtime, type);
    }
    
    // Copy constructor
    private RubyPipeline(RubyPipeline pipe, Ruby runtime, RubyClass type) {
        super(runtime, type);
        this.stages = new RubyProc[pipe.stages.length];
        System.arraycopy(pipe.stages, 0, this.stages, 0, pipe.stages.length);
        this.stagePos = pipe.stagePos;
        this.iStream = pipe.iStream;
        this.oStream = pipe.oStream;
        this.parallelContext = pipe.parallelContext;
    }
    
    private boolean isRunnable() {
        return (this.stages != null && this.iStream != null && this.oStream != null);
    }
    
    // Called by a pipeline object, initialize
    @JRubyMethod(name = {"new", "init"}, meta = true)
    public static IRubyObject newInstance(ThreadContext context, IRubyObject recv, Block block) {
        RubyPipeline r = (RubyPipeline)((RubyClass) recv).allocate();
        System.out.println("Pipeline init");
        /*
        //Notify the race detector to create a new pipeline node
        if(!RaceDetector.turnOff){
        	Scheduler currManager = context.getRuntime().getThreadService()
                    .getParLibThreadMgr();
            Thread th = Thread.currentThread();
            int pipeId = (currManager.getThId(th) << 26) + currManager.getSeqNo(th);
            RaceDetector.notifyStartAll(pipeId, RaceTask.RACE_TASK_PIPELINE);
            r.setParent(RaceDetector.getRaceTask(pipeId));
        }
		*/
        return r;
    }
    
    // Called by other types of objects to initialize a pipeline
    public static IRubyObject newInstance(ThreadContext context) {
        RubyPipeline r = (RubyPipeline)myClass.allocate();
        System.out.println("Pipeline implicit init");
        
        /*//Notify the race detector to create a new pipeline node
        if(!RaceDetector.turnOff){
        	Scheduler currManager = context.getRuntime().getThreadService()
                    .getParLibThreadMgr();
            Thread th = Thread.currentThread();
            int pipeId = (currManager.getThId(th) << 26) + currManager.getSeqNo(th);
            RaceDetector.notifyStartAll(pipeId, RaceTask.RACE_TASK_PIPELINE);
            r.setParent(RaceDetector.getRaceTask(pipeId));
        }
        */
        return r;
    }
    
    // Execute the pipeline
    @JRubyMethod(name = "run")
    public IRubyObject run(ThreadContext context) {
    	// Make sure the pipeline is actually runnable before execute it. 
        if (!this.isRunnable()) {
            return RubyBoolean.newBoolean(context.getRuntime(), false);
        }
        // Set up the parallel context
        this.parallelContext = new ParallelContext();
        // Set up thread manager
        Scheduler currManager = context.getRuntime().getThreadService()
                .getParLibThreadMgr();
        // Get one element from the stream
        IRubyObject currElement = this.iStream.callMethod("getAndMove");
        // Allocate "latches" that stores result for each stage
        IRubyObject[] latches = new IRubyObject[this.stagePos];
        // Tasks
        PipelineStageTask[] tasks = new PipelineStageTask[this.stagePos];
        Thread th = Thread.currentThread();
        // The pipeline should run sizeof(iStream) + nStages - 1 rounds
        int emptyPipeCounter = 0;
        
    	//Notify the race detector to create a new pipeline node
        if(!RaceDetector.turnOff){
            int pipeId = (currManager.getThId(th) << 26) + currManager.getSeqNo(th);
            RaceDetector.notifyStartAll(pipeId, RaceTask.RACE_TASK_PIPELINE);
            this.setParent(RaceDetector.getRaceTask(pipeId));
        }
        
        // Iterate through the input stream
        while (emptyPipeCounter < this.stagePos - 1) {
            if (currElement.eql(context.nil)) {
                emptyPipeCounter++;
            }
            // Move arguments forward, this has to be sequential
            for (int i = this.stagePos - 1; i > 0; i--) {
                latches[i] = latches[i - 1];
            }
            // Add new parameters in
            latches[0] = currElement;
            // Dispatch tasks w.r.t their stages
            for (int i = 0; i < this.stagePos; i++) {
                // If the input from the stream is a nil, stop feeding into the
                // pipe
                if (i == 0 && latches[0].eql(context.nil)) {
                    continue;
                }
                // Set up a new task
                tasks[i] = new PipelineStageTask(currManager.getThId(th),
                        currManager.getSeqNo(th),
                        currManager.getCurrentLevel(th) + 1,
                        this.stages[i], currManager, this.parallelContext, latches[i]);
                this.parallelContext.addTask();
                //add task to task tree before enqueue
                if(!RaceDetector.turnOff) {
                    if (RaceDetector.raceType != RaceDetector.RACE_TYPE_CILK) {
                        tasks[i].setScopeDepth(context.getCurrentScope().getDepth());
                        RaceDetector.notifyTaskFork(parent.id, tasks[i].getGuid(), 1);
                    }
                    // Cilk does not need this notification. Tasks info will be
                    // sended on pre-execution time
                }
                // Let the new task go!
                currManager.enqueueTask(th, tasks[i]);
                // System.out.println("enqueue task "+i);
            }
            // Move to next element, concurrent to all pipeline stages
            currElement = this.iStream.callMethod("getAndMove");
            // Wait for all tasks to finish
            this.parallelContext.join();
            
            //move access-sets forward and perform intersection for the last one
            if(!RaceDetector.turnOff) {
                if (RaceDetector.raceType != RaceDetector.RACE_TYPE_CILK) {
                	if(RaceDetector.raceType == RaceDetector.RACE_TYPE_SPD3){
                		RaceDetector.notifyEndAll(this.parent.id, this.parent.id + 2);
                	}
                	else{
                		RaceDetector.notifyNextStage(parent.id, this.stagePos);
                	}
                } else {
                    // Note: Cilk does not mark pipeline id or stage number
                    RaceDetector.notifyNextStage(0, 0);
                }
            }
            //System.out.println("context: "+this.parallelContext.numTasks()+"/"+this.parallelContext.arrived());
            // Copy results
            for (int i = 0; i < this.stagePos; i++) {
                // For the last stage, move the result out to oStream
                if (i == this.stagePos - 1) {
                    IRubyObject result = tasks[i].getResult();
                    // Result may be null as the first few rounds may not generate
                    // real results
                    if (result != null) {
                        oStream.callMethod("setAndMove", result);
                    }
                    break;
                }
                latches[i] = tasks[i].getResult();
            }
          //Notify the race detector to create a new pipeline node
            if(!RaceDetector.turnOff){
            	if(RaceDetector.raceType == RaceDetector.RACE_TYPE_SPD3){
	                int pipeId = (currManager.getThId(th) << 26) + currManager.getSeqNo(th);
	                RaceDetector.notifyStartAll(pipeId, RaceTask.RACE_TASK_PIPELINE);
	                this.setParent(RaceDetector.getRaceTask(pipeId));
            	}
            }
        }
        
        //ending a finish
        if(!RaceDetector.turnOff) {
            if (RaceDetector.raceType != RaceDetector.RACE_TYPE_CILK) {
                RaceDetector.notifyEndAll(this.parent.id, this.parent.id + 2);
            } else {
                // Note: Cilk does not mark pipeline id or stage number
                RaceDetector.notifyEndAll(0, 0);
            }
        }
        return RubyBoolean.newBoolean(context.getRuntime(), true);
    }
    
    // Called in a split
    @JRubyMethod(name = "addStage")
    public void push(ThreadContext context, IRubyObject arg) {
        // Move addStage pointer
        int currPos = this.stagePos;
        this.stagePos++;
        // Check new pipeline size
        if (this.stages == null || this.stagePos >= this.stages.length) {
            // Allocate a new array
            RubyProc[] newStages = new RubyProc[this.stagePos << 1];
            if (this.stages != null) {
                System.arraycopy(this.stages, 0, newStages, 0, this.stages.length);
            }
            this.stages = newStages;
        }
        this.stages[currPos] = (RubyProc)arg;
        //System.out.println("stage: "+currPos+" = "+arg.toString()+"currPos: "+currPos);
        //System.out.println("New stage pos: "+this.stagePos);
    }
    
    // Combine two pipelines
    @JRubyMethod(name = ">>", required = 1)
    public IRubyObject combine(ThreadContext context, IRubyObject newStage) {
        // Build a new pipeline and add the new stage in
        RubyPipeline newPipe = new RubyPipeline(this, context.getRuntime(), this.getMetaClass());
        
        // The incoming newStage object may either be one stage, or another pipeline
        if (newStage instanceof RubyProc) {
            newPipe.push(context, newStage);
            return newPipe;
        } else if (newStage instanceof RubyPipeline) {
            RubyPipeline incomingPipe = (RubyPipeline) newStage;
            for (RubyProc p : incomingPipe.stages) {
                newPipe.push(context, p);
            }
            return newPipe;
        }
        //TODO: increase pos?
        return context.getRuntime().getNil();
    }
    
    // Get input stream
    @JRubyMethod(name = "iStream")
    public IRubyObject returnIStream(ThreadContext context) {
        return this.iStream;
    }
    
    // Get output stream
    @JRubyMethod(name = "oStream")
    public IRubyObject returnOStream(ThreadContext context) {
        return this.oStream;
    }
    
    // Set input stream
    @JRubyMethod(name = "setIStream")
    public IRubyObject setIStream(ThreadContext context, IRubyObject arg) {
        this.iStream = (RubyStreamable) arg;
        return arg;
    }
    
    // Set output stream
    @JRubyMethod(name = "setOStream")
    public IRubyObject setOStream(ThreadContext context, IRubyObject arg) {
        this.oStream = (RubyStreamable) arg;
        return arg;
    }
    
    // Return whether the pipeline is runnable
    @JRubyMethod(name = "runnable?")
    public IRubyObject runnable(ThreadContext context) {
        if (this.isRunnable()) {
            return RubyBoolean.newBoolean(context.getRuntime(), true);
        }
        return RubyBoolean.newBoolean(context.getRuntime(), false);
    }
    
    public static RubyClass createDPRPipelineClass(Ruby runtime) {
        RubyClass cPipe = runtime.defineClass("Pipeline", runtime.getObject(), new ObjectAllocator() {
            public IRubyObject allocate(Ruby runtime, RubyClass klass) {
                return new RubyPipeline(runtime, klass);
            }
        });
        cPipe.setReifiedClass(RubyPipeline.class);
        cPipe.defineAnnotatedMethods(RubyPipeline.class);
        myClass = cPipe;
        return cPipe;
    }
    
    public static RubyClass getPipelineClass() {
        return myClass;
    }
}
