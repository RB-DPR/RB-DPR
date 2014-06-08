package org.jruby.race;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.concurrent.ConcurrentHashMap;

import org.jruby.RubyBasicObject;
import org.jruby.internal.runtime.methods.DynamicMethod;
import org.jruby.parlib.RubyReduction;
import org.jruby.parlib.Scheduler;
import org.jruby.runtime.DynamicScope;
import org.jruby.runtime.builtin.IRubyObject;

class RaceDetectorSet extends RaceDetector {

    @Override
    void onRead(Object obj, Object info, int offset) {
        long objId = ((RubyBasicObject) obj).getObjectId();
        int thrdId = Scheduler.getCurrThId();

        RaceTask task = thrdTasks.get(thrdId);
        if (objId == 0 || task == null || thrdInstrumentOn[thrdId] > 0)
            return;
        if (task.depth < 2 || task.depth == ((RubyBasicObject) obj).getDepth())
            return;
        // If this operation is within an ac op, add a tag in its flag
        int atomicityModifier = 0;
        IRubyObject commScope = null;
        if (thrdInCommutativeCall[thrdId] > 0) {
            atomicityModifier = AccessSet.ATOMIC_OP_OFFSET;
            commScope = thrdCommScopes[thrdId].peekFirst();
            // return;
        }
        if (RaceDetector.detailMode) {
            thrdMemOprInfo[thrdId].info = info;
            task.set = task.set.insert(objId, offset, AccessSet.RD_FLAG + atomicityModifier, 0, 0,
                    thrdMemOprInfo[thrdId], commScope);
        } else {
            task.set = task.set.insert(objId, offset, AccessSet.RD_FLAG + atomicityModifier, 0, 0,
                    info, commScope);
        }
    }

    @Override
    void onWrite(Object obj, Object info, int offset) {
        long objId = ((RubyBasicObject) obj).getObjectId();
        int thrdId = Scheduler.getCurrThId();
        RaceTask task = thrdTasks.get(thrdId);
        if (objId == 0 || task == null || thrdInstrumentOn[thrdId] > 0)
            return;
        if (task.depth < 2 || task.depth == ((RubyBasicObject) obj).getDepth())
            return;
        // If this operation is within an ac op, add a tag in its flag
        int atomicityModifier = 0;
        IRubyObject commScope = null;
        if (thrdInCommutativeCall[thrdId] > 0) {
            atomicityModifier = AccessSet.ATOMIC_OP_OFFSET;
            commScope = thrdCommScopes[thrdId].peekFirst();
            // return;
        }
        if (RaceDetector.detailMode) {
            thrdMemOprInfo[thrdId].info = info;
            task.set = task.set.insert(objId, offset, AccessSet.WRT_FLAG + atomicityModifier, 0, 0,
                    thrdMemOprInfo[thrdId], commScope);
        } else {
            task.set = task.set.insert(objId, offset, AccessSet.WRT_FLAG + atomicityModifier, 0, 0,
                    info, commScope);
        }
    }

    @Override
    void onRead(Object obj, Object info, int offset, int len) {
        long objId = ((RubyBasicObject) obj).getObjectId();
        int thrdId = Scheduler.getCurrThId();

        RaceTask task = thrdTasks.get(thrdId);
        if (objId == 0 || task == null || thrdInstrumentOn[thrdId] > 0)
            return;
        if (task.depth < 2 || task.depth == ((RubyBasicObject) obj).getDepth())
            return;
        // If this operation is within an ac op, add a tag in its flag
        int atomicityModifier = 0;
        IRubyObject commScope = null;
        if (thrdInCommutativeCall[thrdId] > 0) {
            atomicityModifier = AccessSet.ATOMIC_OP_OFFSET;
            commScope = thrdCommScopes[thrdId].peekFirst();
            // return;
        }
        if (RaceDetector.detailMode) {
            thrdMemOprInfo[thrdId].info = info;
            task.set = task.set.insert(objId, offset, AccessSet.RD_FLAG + atomicityModifier, len,
                    thrdMemOprInfo[thrdId], commScope);
        } else {
            task.set = task.set.insert(objId, offset, AccessSet.RD_FLAG + atomicityModifier, len,
                    info, commScope);
        }
    }

    @Override
    void onWrite(Object obj, Object info, int offset, int len) {
        long objId = ((RubyBasicObject) obj).getObjectId();
        int thrdId = Scheduler.getCurrThId();

        RaceTask task = thrdTasks.get(thrdId);
        if (objId == 0 || task == null || thrdInstrumentOn[thrdId] > 0)
            return;
        if (task.depth < 2 || task.depth == ((RubyBasicObject) obj).getDepth())
            return;
        // If this operation is within an ac op, add a tag in its flag
        int atomicityModifier = 0;
        IRubyObject commScope = null;
        if (thrdInCommutativeCall[thrdId] > 0) {
            atomicityModifier = AccessSet.ATOMIC_OP_OFFSET;
            commScope = thrdCommScopes[thrdId].peekFirst();
            // return;
        }
        if (RaceDetector.detailMode) {
            thrdMemOprInfo[thrdId].info = info;
            task.set = task.set.insert(objId, offset, AccessSet.WRT_FLAG + atomicityModifier, len,
                    thrdMemOprInfo[thrdId], commScope);
        } else {
            task.set = task.set.insert(objId, offset, AccessSet.WRT_FLAG + atomicityModifier, len,
                    info, commScope);
        }
    }

    @Override
    void onReadCheck(Object obj, Object info, int offset, int DynamicScopeDepth) {
        long objId = ((DynamicScope) obj).getObjectId();
        int thrdId = Scheduler.getCurrThId();

        RaceTask task = thrdTasks.get(thrdId);
        if (objId == 0 || task == null || thrdInstrumentOn[thrdId] > 0)
            return;
        if (task.depth < 2 || task.scopeDepth < DynamicScopeDepth)
            return;
        // If this operation is within an ac op, add a tag in its flag
        int atomicityModifier = 0;
        IRubyObject commScope = null;
        if (thrdInCommutativeCall[thrdId] > 0) {
            atomicityModifier = AccessSet.ATOMIC_OP_OFFSET;
            commScope = thrdCommScopes[thrdId].peekFirst();
            // return;
        }
        if (RaceDetector.detailMode) {
            thrdMemOprInfo[thrdId].info = info;
            task.set = task.set.insert(objId, offset, AccessSet.RD_FLAG + atomicityModifier, 0, 0,
                    thrdMemOprInfo[thrdId], commScope);
        } else {
            task.set = task.set.insert(objId, offset, AccessSet.RD_FLAG + atomicityModifier, 0, 0,
                    info, commScope);
        }
    }

    @Override
    void onWriteCheck(Object obj, Object info, int offset, int DynamicScopeDepth) {
        long objId = ((DynamicScope) obj).getObjectId();
        int thrdId = Scheduler.getCurrThId();
        RaceTask task = thrdTasks.get(thrdId);
        if (objId == 0 || task == null || thrdInstrumentOn[thrdId] > 0)
            return;
        if (task.depth < 2 || task.scopeDepth < DynamicScopeDepth)
            return;
        // If this operation is within an ac op, add a tag in its flag
        int atomicityModifier = 0;
        IRubyObject commScope = null;
        if (thrdInCommutativeCall[thrdId] > 0) {
            atomicityModifier = AccessSet.ATOMIC_OP_OFFSET;
            commScope = thrdCommScopes[thrdId].peekFirst();
            // return;
        }
        if (RaceDetector.detailMode) {
            thrdMemOprInfo[thrdId].info = info;
            task.set = task.set.insert(objId, offset, AccessSet.WRT_FLAG + atomicityModifier, 0, 0,
                    thrdMemOprInfo[thrdId], commScope);
        } else {
            task.set = task.set.insert(objId, offset, AccessSet.WRT_FLAG + atomicityModifier, 0, 0,
                    info, commScope);
        }
    }

    @Override
    void onCall(Object obj, DynamicMethod method, IRubyObject rtn) {
        // After the TODO in AccessSet is done, we can only instrument annotated
        // classes
        if (!((RubyBasicObject) obj).getType().containsACOp()) {
            return;
        }
        long id = ((RubyBasicObject) obj).getObjectId();
        RaceTask task = thrdTasks.get(Scheduler.getCurrThId());
        if (id == 0 || task == null)
            return;
        if (task.depth < 2)
            return;
        if (RaceDetector.detailMode) {
            int thrdId = Scheduler.getCurrThId();
            task.set.insertCall(id, method, rtn, thrdMemOprInfo[thrdId].clone());
        } else {
            task.set.insertCall(id, method, rtn);
        }
    }

    @Override
    void onCall(Object obj, DynamicMethod method, IRubyObject para,
            IRubyObject rtn) {
        // We can only instrument annotated classes
        if (!((RubyBasicObject) obj).getType().containsACOp()) {
            return;
        }
        long id = ((RubyBasicObject) obj).getObjectId();
        RaceTask task = thrdTasks.get(Scheduler.getCurrThId());
        if (id == 0 || task == null)
            return;
        if (task.depth < 2)
            return;
        if (RaceDetector.detailMode) {
            int thrdId = Scheduler.getCurrThId();
            task.set.insertCall(id, method, para, rtn, thrdMemOprInfo[thrdId].clone());
        } else {
            task.set.insertCall(id, method, para, rtn);
        }
    }

    @Override
    void onCall(Object obj, DynamicMethod method, IRubyObject[] args,
            IRubyObject rtn) {
        // After the TODO in AccessSet is done, we can only instrument annotated
        // classes
        if (!((RubyBasicObject) obj).getType().containsACOp()) {
            return;
        }
        long id = ((RubyBasicObject) obj).getObjectId();

        RaceTask task = thrdTasks.get(Scheduler.getCurrThId());
        if (id == 0 || task == null)
            return;
        if (task.depth < 2)
            return;
        if (RaceDetector.detailMode) {
            int thrdId = Scheduler.getCurrThId();
            task.set.insertCall(id, method, (IRubyObject[]) args, rtn,
                    thrdMemOprInfo[thrdId].clone());
        } else {
            task.set.insertCall(id, method, args, rtn);
        }
    }

    @Override
    void onReStart() {
        thrdTasks = new ArrayList<RaceTask>(thrdNum);
        thrdObjIds = new long[thrdNum];
        thrdInstrumentOn = new int[thrdNum];
        thrdInCommutativeCall = new int[thrdNum];
        thrdCommScopes = new LinkedList[thrdNum];
        if (RaceDetector.detailMode) {
            thrdMemOprInfo = new MemoryOprInfo[thrdNum];
            thrdPathHashList = new ArrayList<HashMap<String, Integer>>();
        }

        globVarIds = new ConcurrentHashMap();
        for (int i = 0; i < thrdNum; i++) {
            thrdTasks.add(null);
            thrdObjIds[i] = 0;
            thrdInstrumentOn[i] = 0;
            thrdInCommutativeCall[i] = 0;
            if (thrdCommScopes[i] == null) {
                thrdCommScopes[i] = new LinkedList<IRubyObject>();
            } else if (!thrdCommScopes[i].isEmpty()) {
                thrdCommScopes[i].clear();
            }
            if (RaceDetector.detailMode) {
                thrdMemOprInfo[i] =  new MemoryOprInfo();
                thrdPathHashList.add(new HashMap<String, Integer>());
            }
        }
        taskTree = new RaceTaskTree(thrdTasks);
        taskTree.taskMap(Scheduler.getCurrThId(), 1);
    }

    @Override
    void onTaskJoin(int taskId) {
        taskTree.taskJoin(Scheduler.getCurrThId(), taskId);
    }

    @Override
    void onTaskFork(int ctid, int itrNum) {
        taskTree.taskFork2(Scheduler.getCurrThId(), ctid, itrNum);
    }

    @Override
    void onTaskFork(int ptid, int ctid, int itrNum) {
        taskTree.taskFork(Scheduler.getCurrThId(), ptid, ctid);
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
        return null;
    }

    @Override
    void onReductionPush(RubyReduction obj) {
        long id = obj.getObjectId();
        RaceTask task = thrdTasks.get(Scheduler.getCurrThId());
        if (id == 0 || task == null)
            return;
        if (RaceDetector.detailMode) {
            int thrdId = Scheduler.getCurrThId();
            thrdMemOprInfo[thrdId].info = obj;
            task.set = task.set.insert(id, 0, AccessSet.RD_FLAG, 0, 0, thrdMemOprInfo[thrdId], null);
        } else {
            task.set = task.set.insert(id, 0, AccessSet.RD_FLAG, 0, 0, obj.getClass(), null);
        }
    }

    @Override
    void onReductionGet(RubyReduction obj) {
        long id = obj.getObjectId();

        RaceTask task = thrdTasks.get(Scheduler.getCurrThId());
        if (id == 0 || task == null)
            return;
        if (RaceDetector.detailMode) {
            int thrdId = Scheduler.getCurrThId();
            thrdMemOprInfo[thrdId].info = obj;
            task.set = task.set.insert(id, 0, AccessSet.WRT_FLAG, 0, 0, thrdMemOprInfo[thrdId], null);
        } else {
            task.set = task.set.insert(id, 0, AccessSet.WRT_FLAG, 0, 0, obj.getClass(), null);
        }
    }

    @Override
    RaceTask getTask(int tid) {
        return taskTree.lookup(tid);
    }

    @Override
    void onNextStage(long pipelineId, int stageNum) {
        taskTree.nextStage(pipelineId, stageNum);
    }
}
