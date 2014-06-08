package org.jruby.race;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.jruby.RubyArray;
import org.jruby.RubyBasicObject;
import org.jruby.internal.runtime.methods.DynamicMethod;
import org.jruby.parlib.RubyReduction;
import org.jruby.parlib.Scheduler;
import org.jruby.runtime.DynamicScope;
import org.jruby.runtime.builtin.IRubyObject;

class RaceDetectorSPD3 extends RaceDetector{
	@Override
	void onRead(Object obj, Object info, int offset) {
		//if(obj instanceof RubyBasicObject){
			rdWrtCheck((RubyBasicObject)obj, offset, 0);
		//}
	}

	@Override
	void onWrite(Object obj, Object info, int offset) {
		//if(obj instanceof RubyBasicObject){
			rdWrtCheck((RubyBasicObject)obj, offset, 1);
		//}
	}

	@Override
	void onRead(Object obj, Object info, int offset, int len) {
		// TODO Auto-generated method stub
		for(int i = 0; i < len; i++){
			rdWrtCheck((RubyBasicObject)obj, offset + i, 0);
		}
	}

	@Override
	void onWrite(Object obj, Object info, int offset, int len) {
		// TODO Auto-generated method stub
		for(int i = 0; i < len; i++){
			rdWrtCheck((RubyBasicObject)obj, offset + i, 1);
		}
	}

	@Override
	void onReadCheck(Object obj, Object info, int offset, int DynamicScopeDepth) {
		//if(obj instanceof DynamicScope){
			rdWrtCheckScope((DynamicScope)obj, offset, 0);
		//}
		
	}

	@Override
	void onWriteCheck(Object obj, Object info, int offset, int DynamicScopeDepth) {
		//if(obj instanceof DynamicScope){
			rdWrtCheckScope((DynamicScope)obj, offset, 1);
		//}
		
	}

	@Override
	void onReStart() {
		thrdTasks = new ArrayList<RaceTask>(thrdNum);
		thrdObjIds = new long[thrdNum];
		globVarIds = new ConcurrentHashMap();
		
	        thrdInCommutativeCall = new int[thrdNum];
	        thrdCommScopes = new LinkedList[thrdNum];
	        
		for(int i = 0; i < thrdNum; i++){
		    thrdTasks.add(null);
		    thrdObjIds[i] = 0;
	            thrdInCommutativeCall[i] = 0;
	            if (thrdCommScopes[i] == null) {
	                thrdCommScopes[i] = new LinkedList<IRubyObject>();
	            } else if (!thrdCommScopes[i].isEmpty()) {
	                thrdCommScopes[i].clear();
	            }
		}
		taskTree = new RaceTaskTree(thrdTasks);
		taskTree.taskMap(Scheduler.getCurrThId(), 1);
		
	}
	
	public static void readCheck(long id, Object[] metaData, int offset, RaceTask currTask){
		//System.out.println("readCheck: id=" + id + ",offset=" + offset + ",ml=" + metaData.length);
		int index = offset * RaceMetaDataSPD3.shadowSize;
		
		//raceActions[RACE_EVENT_READ]++; 
		//RubyObject rubyObj = (RubyObject)obj;
		while(true){//big loop to try
			//step1: read the startVersion in metadata into local variable
			int startVer = ((AtomicInteger)(metaData[index + RaceMetaDataSPD3.METADATA_OFFSET_STARTVER])).get();
			//step2: read all the data to local variables
			RaceTask rd1 = (RaceTask)(metaData[index + RaceMetaDataSPD3.METADATA_OFFSET_RD1]);
			RaceTask rd2 = (RaceTask)(metaData[index + RaceMetaDataSPD3.METADATA_OFFSET_RD2]);
			RaceTask wrt = (RaceTask)(metaData[index + RaceMetaDataSPD3.METADATA_OFFSET_WRT]);
			
			if(rd1 == currTask || rd2 == currTask) return;
			
			//step2: perform a memory fence to ensure all operations above are completed
			// startVersion is AtomicInteger in Java, reading performs like a memory fence
			//step3: read the endVersion in metadata into local variable
			int endVer = ((AtomicInteger)(metaData[index +  RaceMetaDataSPD3.METADATA_OFFSET_ENDVER])).get();
			//step4: compare the two version numbers, restart if they are different
			if(startVer != endVer) continue;
			
			//step5: perform the computation on the local variables
			RaceTask newRd1 = rd1;
			RaceTask newRd2 = rd2;
			RaceTask newWrt = wrt;
			if(currTask != wrt && wrt != null){
				if(taskTree.taskDmhp(currTask, wrt)){
					recordRace(id, null, offset);
				}
			}
			
			boolean bChanged = false;
			
			if(!(taskTree.taskDmhp(rd1, currTask)) && !(taskTree.taskDmhp(rd2, currTask)) || 
				rd1 == null && rd2 == null){
				newRd1 = currTask;
				newRd2 = null;
				bChanged = true;
			}
			
			if(taskTree.taskDmhp(rd1, currTask) && taskTree.taskDmhp(rd2, currTask)){
				if(rd1 == null){
					newRd1 = currTask;
					bChanged = true;
				}
				else if(rd2 == null){
					newRd2 = currTask;
					bChanged = true;
				}
				else{
					RaceTask lca12 = taskTree.lca(rd1, rd2);
					RaceTask lca1s = taskTree.lca(rd1, currTask);
					RaceTask lca2s = taskTree.lca(rd2, currTask);
					if(lca1s.depth < lca12.depth || lca2s.depth < lca12.depth){
						newRd1 = currTask;
						bChanged = true;
					}
				}
			}
			
			if(!bChanged) break;
			//step6: perform a CAS on the endVersion number
			boolean casResult = ((AtomicInteger)(metaData[index +  RaceMetaDataSPD3.METADATA_OFFSET_ENDVER])).compareAndSet(endVer, endVer + 1);
			if(!(casResult)){
				//if fails, continue;
				continue;
			}
			else{
				//update the metaData
				metaData[index + RaceMetaDataSPD3.METADATA_OFFSET_RD1] = newRd1;
				metaData[index + RaceMetaDataSPD3.METADATA_OFFSET_RD2] = newRd2;
				metaData[index + RaceMetaDataSPD3.METADATA_OFFSET_WRT] = newWrt;
			}
			//increase the startVersion
			((AtomicInteger)(metaData[index + RaceMetaDataSPD3.METADATA_OFFSET_STARTVER])).incrementAndGet();
			break;
		}
	}
	public static void writeCheck(long id, Object[] metaData, int offset, RaceTask currTask){
		//if(!(enabled && started)) return;
		//System.out.println("writeCheck: id=" + id + ",offset=" + offset + ",ml=" + metaData.length);
		int index = offset * RaceMetaDataSPD3.shadowSize;
		
		//raceActions[RACE_EVENT_WRITE]++; 
		while(true){
			//step1: read the startVersion in metadata into local variable
			int startVer = ((AtomicInteger)(metaData[index + RaceMetaDataSPD3.METADATA_OFFSET_STARTVER])).get();
			//step2: read all the data to local variables
			RaceTask rd1 = (RaceTask)(metaData[index + RaceMetaDataSPD3.METADATA_OFFSET_RD1]);
			RaceTask rd2 = (RaceTask)(metaData[index + RaceMetaDataSPD3.METADATA_OFFSET_RD2]);
			RaceTask wrt = (RaceTask)(metaData[index + RaceMetaDataSPD3.METADATA_OFFSET_WRT]);
			
			if(currTask == wrt) return;
			//step2: perform a memory fence to ensure all operations above are completed
			// startVersion is AtomicInteger in Java, reading performs like a memory fence
			//step3: read the endVersion in metadata into local variable
			int endVer = ((AtomicInteger)(metaData[index +  RaceMetaDataSPD3.METADATA_OFFSET_ENDVER])).get();
			//step4: compare the two version numbers, restart if they are different
			if(startVer != endVer) continue;
			
			//step5: perform the computation on the local variables
			if(rd1 != null && rd1 != currTask){
				if(taskTree.taskDmhp(rd1, currTask)){
					recordRace(id, null, offset);
				}
			}
			
			if(rd2 != null && rd2 != currTask){
				if(taskTree.taskDmhp(rd2, currTask)){
					recordRace(id, null, offset);
				}
			}
			
			if(wrt != null && taskTree.taskDmhp(wrt, currTask)){
				recordRace(id, null, offset);
			}
			
			//step6: perform a CAS on the endVersion number
			boolean casResult = ((AtomicInteger)(metaData[index +  RaceMetaDataSPD3.METADATA_OFFSET_ENDVER])).compareAndSet(endVer, endVer + 1);
			if(!(casResult)){
				//if fails, continue;
				continue;
			}
			else{
				//update the metaData
				metaData[index + RaceMetaDataSPD3.METADATA_OFFSET_WRT] = currTask;
			}
			//TODO: need a fence here?
			//increase the startVersion
			((AtomicInteger)(metaData[index + RaceMetaDataSPD3.METADATA_OFFSET_STARTVER])).incrementAndGet();
			break;
		}
	}
	
	public static void rdWrtCheck(RubyBasicObject obj, int offset, int type){
		int opr = 0;
		long startNum;
		long endNum;
		Object[] meta;
		int len = 0;
		
		if(!(enabled && started)) return;
		RaceTask currTask = thrdTasks.get(Scheduler.getCurrThId());
		if(currTask.depth < 2) return;
		//System.out.println("obj = " + obj.getObjectId() + ", offset=" + offset + ", thrd = " +
		//	Scheduler.getCurrThId());		
		while(true){
			while(true){
                opr = 0;
				//read the seq num
				startNum = obj.getSeqNum();
				if(startNum % 2 == 1) continue;
				//check the metadata
				meta = obj.getSPD3Shadow();
				if(meta == null) opr = 1;
				else if(meta.length < (offset + 1) * RaceMetaDataSPD3.shadowSize){
					opr = 2;
				}
				if(opr > 0){
					//compute the new length of shadow area
					if(obj instanceof RubyArray){
						len = ((RubyArray)obj).getLength();
						if(len < offset + 1) len = offset + 1;
						//read the end number
					}
					else//object instance
						len = offset + 1;
					
					endNum = obj.getSeqNum();
					if(startNum == endNum){
						//try to increase the seq num
						if(!obj.casSeqNum(endNum, endNum + 1)) continue;
						else break;
					}
				}
				else{
					endNum = obj.getSeqNum();
					if(startNum == endNum)
						break;
				}
			}
			
			if(opr > 0){
				if(opr == 1){
					//System.out.println("thread " + Scheduler.getCurrThId() + " start to allocate on " + obj.getObjectId());
					 meta = RaceMetaDataSPD3.mallocMetaData(len);
					//System.out.println("obj = " + obj.getObjectId() + " new size" + meta.length);
				}
				else if(opr == 2){
					//System.out.println("thread " + Scheduler.getCurrThId() + "start to allocate on " + obj.getObjectId());
					meta = RaceMetaDataSPD3.reMallocMetaData(meta, len);
					//System.out.println("obj = " + obj.getObjectId() + " extend size" + meta.length);
				}
				obj.setSPD3Shadow(meta);
			}
			
			//do check anyway
			if(type == 0)
				readCheck(obj.getObjectId(), meta, offset, currTask);
			else 
				writeCheck(obj.getObjectId(), meta, offset, currTask);
			
			if(opr == 0){//check the seqnum again for read only
				endNum = obj.getSeqNum();
				if(startNum != endNum) continue;
				else break;
			}
			else{//increase the seq num
				obj.insSeqNum();
				break;
			}
		}
	}
	
	public static void rdWrtCheckScope(DynamicScope s, int offset, int type){
		int opr = 0;
		long startNum;
		long endNum;
		Object[] meta;
		int len = 0;
		int length = s.getLength();
		
		if(!(enabled && started)) return;
		//check read-write race
		
		RaceTask currTask = thrdTasks.get(Scheduler.getCurrThId());
		//if(length == 6) System.out.println("6:taskdp=" + currTask.scopeDepth + ",sdp=" + s.getDepth());
		if(currTask.depth < 2  || currTask.scopeDepth < s.getDepth()) return;
		
		//System.out.println("readCheck: id=" + id + ",offset=" + offset + ",ml=" + metaData.length);
		int index = offset * RaceMetaDataSPD3.shadowSize;
		
		while(true){
			while(true){
					
				opr = 0;
				//read the seq num
				startNum = s.getSeqNum();
				if(startNum % 2 == 1) continue;
				//check the metadata
				meta = s.getMetaData();
				if(meta == null) opr = 1;
				else if(meta.length < (offset + 1) * RaceMetaDataSPD3.shadowSize){
					opr = 2;
				}
				if(opr > 0){
					//compute the new length of shadow area
					len = offset + 1;
					if(len < length) len = length;
					
					endNum = s.getSeqNum();
					if(startNum == endNum){
						//try to increase the seq num
						if(!s.casSeqNum(endNum, endNum + 1)) continue;
						else break;
					}
				}
				else {
					endNum = s.getSeqNum();
					if(startNum == endNum)
					break;
				}
			}
			//need to allocate memory
			
			if(opr > 0){
				if(opr == 1) meta = RaceMetaDataSPD3.mallocMetaData(len);
				else if(opr == 2) meta = RaceMetaDataSPD3.reMallocMetaData(meta, len);
				s.setMetaData(meta);
			}
			//do check anyway
			if(type == 0)
				readCheck(s.getObjectId(), meta, offset, currTask);
			else 
				writeCheck(s.getObjectId(), meta, offset, currTask);
			
			if(opr == 0){//check the seqnum again for read only
				endNum = s.getSeqNum();
				if(startNum != endNum) continue;
				else break;
			}
			else{//increase the seq num
				s.insSeqNum();
				break;
			}
		}
	}
		
	

	@Override
	void onTaskFork(int ctid, int itrNum) {
		taskTree.taskFork2(Scheduler.getCurrThId(), ctid, itrNum);
	}

	@Override
	void onTaskJoin(int taskId) {
		taskTree.taskJoin(Scheduler.getCurrThId(), taskId);
		
	}

	@Override
	void onTaskExec(int taskId, int depth) {
		taskTree.taskExec(Scheduler.getCurrThId(), taskId, depth);
		
	}

	@Override
	void onTaskReMap(int taskId) {
		taskTree.taskReMap(Scheduler.getCurrThId(), taskId);
		
	}

	@Override
	void onStartAll(int taskId, int type) {
		taskTree.startFinish2(Scheduler.getCurrThId(), taskId, type);
		
	}

	@Override
	void onTaskDone(int taskId) {
		taskTree.taskJoin(Scheduler.getCurrThId(), taskId);
		
	}

	@Override
	void onEndAll(int finishId, int taskId) {
		taskTree.endFinish(Scheduler.getCurrThId(), finishId, taskId);
		
	}

	@Override
	void onStartNewItr(long i) {
		taskTree.startNewItr(i);
		
	}

	@Override
	Object[] onInitMetaData(int size) {
		// TODO Auto-generated method stub
		return RaceMetaDataSPD3.mallocMetaData(size);
	}

	@Override
	void onReductionPush(RubyReduction obj) {
	    // System.out.println("Not supported: onReductionPush!");
		// TODO Auto-generated method stub
		
	}

	@Override
	void onReductionGet(RubyReduction obj) {
	    // System.out.println("Not supported: onReductionGet!");
		// TODO Auto-generated method stub
		
	}

	@Override
	void onCall(Object obj, DynamicMethod method, IRubyObject rtn) {
	    System.out.println("Not supported: onCall! noarg");
		// TODO Auto-generated method stub
		
	}

	@Override
	void onCall(Object obj, DynamicMethod method, IRubyObject para,
			IRubyObject rtn) {
	    System.out.println("Not supported: onCall! para");
		// TODO Auto-generated method stub
		
	}

	@Override
	void onCall(Object obj, DynamicMethod method, IRubyObject[] args,
			IRubyObject rtn) {
	    System.out.println("Not supported: onCall![]");
		// TODO Auto-generated method stub
		
	}

	@Override
	public RaceTask getTask(int tid) {
	    return taskTree.lookup(tid);
	}

	@Override
	void onNextStage(long pipelineId, int stageNum) {
	    taskTree.nextStage(pipelineId, stageNum);
	}

	@Override
	void onTaskFork(int ptid, int ctid, int itrNum) {
	    taskTree.taskFork2(Scheduler.getCurrThId(), ctid, itrNum);
	    // System.out.println("Not supported: onTaskFork!");
		// TODO Auto-generated method stub
		
	}
}
