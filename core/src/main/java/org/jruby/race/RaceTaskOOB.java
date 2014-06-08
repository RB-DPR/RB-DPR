/**********************************************************************

  RaceTaskOOB.java -

  $Author: Weixing Ji 
  created at: Jan.  2013

  Copyright (C) 2013  

 **********************************************************************/
package org.jruby.race;

import org.jruby.parlib.PLibLogTask;
import org.jruby.parlib.Scheduler;
import org.jruby.race.AccessSet;
import java.util.Hashtable;
import java.util.ArrayList;

//Race task for out-of-band
public class RaceTaskOOB extends RaceTask
{
    public ArrayList<AccessSet> pendingSets; //for log intersection and merge
    public boolean asyncJoined = false;
    public boolean delayed = false;
    public int asyncItrNum = 0;
    public int delayedNum = 0;
    //accumulated merge number in pendingSets
    public int accMergeNum = 0;
    
    public static final int BATCH_SETS_NUM = 1024;
    
    public RaceTaskOOB(){
    	super();
    	//create a pending list to hold all the sets for background processing
    	if(type == RACE_TASK_COBEGIN || 
    			type == RACE_TASK_DOTALL || 
    			type == RACE_TASK_ASYNC){
        	pendingSets = new ArrayList<AccessSet>();
        }
    }

    public RaceTaskOOB(int id, int type, int depth, RaceTask parent)
    {
        super(id, type, depth, parent);
        if(type != RACE_TASK_STEP) {
			pendingSets = new ArrayList<AccessSet>();
		}
    }
     public void pushSet(AccessSet set){
    	int size = Integer.MAX_VALUE;
 		if(type == RACE_TASK_ASYNC){
     		if(this.parent.type == RACE_TASK_DOTALL && RaceDetector.itrOn)
     			size = this.asyncItrNum;
     		else if(this.parent.type == RACE_TASK_DOTALL || this.parent.type == RACE_TASK_COBEGIN)
     			size = this.children.size();
     		else if(this.parent.type == RACE_TASK_ASYNC && RaceDetector.itrOn)
     			size = this.asyncItrNum;
 		}
 		else if(type == RACE_TASK_COBEGIN || type == RACE_TASK_DOTALL) {
 			size = this.children.size();
 		}
 		
    	if(type == RACE_TASK_ASYNC){
    		if(size == set.getMergeNum()){
    			//reset type and merge num.
	    		set.setWorkType(AccessSet.LOG_WORK_HYBRID);
				set.setMergeNum(1);
				//push up
				((RaceTaskOOB)(this.parent)).pushSet(set);
				this.children.clear();
				return;
    		}
    	}
    	//intersection and merge are completed if the merge num. is equal
    	//to its children number
		else if((type == RACE_TASK_COBEGIN || type == RACE_TASK_DOTALL) 
				&& set.getMergeNum() == size){
			//reset type and merge num.
			set.setWorkType(AccessSet.LOG_WORK_MERGE);
			set.setMergeNum(1);
			//push up if necessary
			if(this.depth > 1)
				((RaceTaskOOB)(this.parent)).pushSet(set);
			this.children.clear();
			this.set = null;
			return;
    	}
    	//the intersection and merges are not yet completed
    	//add the new set to the pending list and
    	//generate a new set-task if we have enough sets in the
    	//pending list
		synchronized(this.pendingSets){
    		this.pendingSets.add(set);
    		this.accMergeNum += set.getMergeNum();
    		//compute the maximum sets number for different tasks
    		//check if we need to post a new task
    		if(this.pendingSets.size() >= BATCH_SETS_NUM || this.accMergeNum >= size){
    			ArrayList setList = new ArrayList<AccessSet>();
    			setList.addAll(this.pendingSets);
				PLibLogTask bgTask = new PLibLogTask(0, 
						PLibLogTask.LOG_TASK_PRIO_MEDIUM,
						setList,
						this);
				Scheduler.enqueueLogTask(Thread.currentThread(), 
						bgTask, 
						PLibLogTask.LOG_TASK_PRIO_MEDIUM);
				this.pendingSets.clear();
				this.accMergeNum = 0;
    		}
		}
    }
     //nom atter how many sets there are in the pending list, 
     //generate a new set-task and push them up 
     public void pushAndPost(AccessSet set){
    	 synchronized(this.pendingSets){
     		this.pendingSets.add(set);
     		ArrayList setList = new ArrayList<AccessSet>();
 			setList.addAll(this.pendingSets);
 			
			PLibLogTask bgTask = new PLibLogTask(0, 
					PLibLogTask.LOG_TASK_PRIO_MEDIUM,
					setList,
					this);
			Scheduler.enqueueLogTask(Thread.currentThread(), 
					bgTask, 
					PLibLogTask.LOG_TASK_PRIO_MEDIUM);
			this.pendingSets.clear();
    	 }
     }
}

//Task tree is built dynamically
class RaceTaskTreeOOB extends RaceTaskTree
{

    public RaceTaskTreeOOB(ArrayList<RaceTask> mapping)
    {
        //create the root node and the first step node
        this.root = new RaceTaskOOB(0, RaceTask.RACE_TASK_FINISH, 0, null);
        root.children.add(new RaceTaskOOB(1, RaceTask.RACE_TASK_STEP, 1, root));
        this.threadTasks = mapping;
        this.taskHt = new Hashtable();
        this.taskHt.put(0, root);
        this.taskHt.put(1, root.children.get(0));
    }
  
    @Override
    public void taskJoin(int thrdId, int taskId)
    {
        RaceTask task = lookup2(root, taskId);
        RaceTaskOOB taskOOB = (RaceTaskOOB)task;
        if(taskOOB == null)
        {
            System.out.println("[race]join:can not find the child task in the tree!");
            print(root, false);
            System.exit(-1);
        }
        
        //print(task, false);
        if(!(RaceDetector.itrOn && taskOOB.parent.type == RaceTask.RACE_TASK_DOTALL)){
	        for(int i = 0; i < task.children.size(); i++)
	        {
	            RaceTaskOOB right = (RaceTaskOOB) task.children.get(i);
	            if(right.type == RaceTask.RACE_TASK_STEP){
	            	right.set.setMergeNum(1);
	            	if(right.set instanceof AccessSetList){
	            		taskOOB.set = taskOOB.set.merge(right.set);
	            	}
	            	else{
		            	right.set.setTaskId(AccessSet.LOG_WORK_MERGE);
		            	taskOOB.pushSet(right.set);
	            	}
	            }
	            right.set = null;
	        }
        }
        //push the local set to the pending list
        if(taskOOB.set.getMergeNum() > 0){
    		taskOOB.set.setWorkType(AccessSet.LOG_WORK_HYBRID);
    		taskOOB.pushSet(taskOOB.set);
    	}
        taskOOB.asyncJoined = true; 
        threadTasks.set(thrdId, null);
    }

    //ending a finish
    @Override
    public void endFinish(int thrdId, int finishId, int taskId)
    {
        RaceTask finish = lookup2(root, finishId);
        RaceTaskOOB taskCoS = (RaceTaskOOB)finish;
        
        RaceTask step = null;
        if(finishId == 0)
        {
            step = new RaceTaskOOB(taskId, RaceTask.RACE_TASK_STEP, finish.depth, root);
            //TODO: max nested depth 
            step.scopeDepth = 8192;
            root.children.add(step);
        }
        else	
        {
            step = new RaceTaskOOB(taskId, RaceTask.RACE_TASK_STEP, finish.depth, finish.parent);
            step.scopeDepth = finish.parent.scopeDepth;
            finish.parent.children.add(step);
        }

        taskMap(thrdId, step);
    }
    //public static long[] itrNum = new long[1024];
    //private RaceSet preItrSet = null;
    @Override
    public void startNewItr(long itr){
    	
    	if(!RaceDetector.itrOn) return;
    	
    	int thrdId = Scheduler.getCurrThId();
    	//itrNum[thrdId * 16]++;
        RaceTask step = threadTasks.get(thrdId);

        
        if(step == null){
            System.out.println("[race]startNewItr:can not find the child task in the tree!");
            print(root, false);
            System.exit(-1);
        }
        RaceTaskOOB async = (RaceTaskOOB)step.parent;
        if(async == null){
            System.out.println("[race]startNewItr:can not find the parent task in the tree!");
            print(root, false);
            
        }
        
        //async.asyncItrNum++;
        
        //if the async node has only one child
        if(async.children.size() - async.delayedNum == 1){
        	//step.set.print();
        	step.set.setMergeNum(1);
        	//if it is a list set, then we perform intersection and merge 
        	//immediately using current thread
        	if(step.set instanceof AccessSetList){
        		async.set = async.set.intersectAndMerge(step.set, RaceTask.RACE_TASK_ASYNC);
        		step.set.clear();
        	}
        	else{
	        	step.set.setWorkType(AccessSet.LOG_WORK_HYBRID);
	        	async.pushSet(step.set);
        		step.reAllocaLog();
	        	taskMap(Scheduler.getCurrThId(), step);
        	}
        }
        else{
        	//System.out.println("new created");
        	//create a new asyn node to hold all the children and sets of
        	//current iteration
        	RaceTaskOOB newAsync = new RaceTaskOOB(async.id, RaceTask.RACE_TASK_ASYNC, async.depth + 1, async);
        	//set the asyncItrNum, so that newAsync when all the logs are merged together
        	newAsync.asyncItrNum = async.children.size() - async.delayedNum;
        	//add newAsync as one of the child of async
        	async.children.add((int) async.delayedNum, newAsync);
        	//remeber how many iterations are delayed for async
        	async.delayedNum++;
        	//post all sets of step nodes to newAsync
        	//add all the finish children of current iteration to newAsync
        	for(int i = async.delayedNum; i < async.children.size(); i++){
        		//newAsync.asyncItrNum++;
        		RaceTaskOOB bgTask = (RaceTaskOOB) async.children.get(i); 
        		if(bgTask.type == RaceTask.RACE_TASK_STEP){
        			bgTask.set.setMergeNum(1);
        			//if it is a list set, intersection and merge are performed 
        			//immediately by current thread
        			if(bgTask.set instanceof AccessSetList){
        				newAsync.set = newAsync.set.merge(bgTask.set);
        			}
        			else{
	        			bgTask.set.setWorkType(AccessSet.LOG_WORK_MERGE);
	        			newAsync.pushSet(bgTask.set);
        			}
        			bgTask.set = null;
        		}
        		else{
        			newAsync.children.add(bgTask);
        			bgTask.parent = newAsync;
        			bgTask.depth = newAsync.depth + 1;
        		}
        	}
        	//push itself into the pending queue
        	if(newAsync.set.getMergeNum() > 0){
        		newAsync.pushSet(newAsync.set);
        	}
        	//remove all the children of current iteration from async
        	while(async.children.size() - async.delayedNum > 0)
        		async.children.remove(async.children.size() - 1);
        	//init the first step node for next iteration
        	
    		if(step.set instanceof AccessSetList){
    			step.set.clear();
    		}
    		else{
    			step.reAllocaLog();
    		}
        	async.children.add(step);
        	taskMap(Scheduler.getCurrThId(), step);
        }
    }
    @Override
    public void taskFork2(int thrdId, int child, int itrNum)
    {
    	RaceTaskOOB async = null;
    	RaceTaskOOB step1 = null;
    	RaceTaskOOB temp = null;

    	RaceTaskOOB currTask = (RaceTaskOOB) threadTasks.get(thrdId);
    	RaceTaskOOB parentTask = (RaceTaskOOB) currTask.parent;
        if(parentTask == null)
        {
            System.out.println("[race]fork:can not find the parent task in the tree!");
            print(root, false);
            System.exit(-1);
        }
        //there should one finish node at the right most
        parentTask = (RaceTaskOOB)(parentTask.children.get(parentTask.children.size() - 1));
        //async node
        async = new RaceTaskOOB(child, RaceTask.RACE_TASK_ASYNC, parentTask.depth + 1, parentTask);
        //step node under the async node
        step1 = new RaceTaskOOB(child + 2, RaceTask.RACE_TASK_STEP, parentTask.depth + 2, async);
        parentTask.children.add(async);
        async.children.add(step1);
        async.asyncItrNum = itrNum;
        
        this.taskHt.put(child, async);
        this.taskHt.put(child + 2, step1);
    }
    
    //start a new doall,cobegin...
    // this version does not create step node
    @Override
    public void startFinish2(int thrdId, int taskId, int type)
    {
    	RaceTask parentTask = threadTasks.get(thrdId).parent;
        if(parentTask == null)
        {
            System.out.println("[race]startFinish: can not find the parent task!");
            print(root, false);
            System.exit(-1);
        }

        //finish node			
        RaceTask finish = new RaceTaskOOB(taskId, type, parentTask.depth + 1, parentTask);
        parentTask.children.add(finish);
        finish.thrdId = thrdId;
        this.taskHt.put(taskId, finish);
    }
}
