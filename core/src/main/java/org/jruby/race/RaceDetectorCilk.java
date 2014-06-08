package org.jruby.race;

import java.util.ArrayList;
import java.util.LinkedList;

import org.jruby.RubyBasicObject;
import org.jruby.internal.runtime.methods.DynamicMethod;
import org.jruby.parlib.RubyReduction;
import org.jruby.parlib.Scheduler;
import org.jruby.race.DisjointSet;
import org.jruby.race.ProcNode;
import org.jruby.runtime.DynamicScope;
import org.jruby.runtime.builtin.IRubyObject;

public class RaceDetectorCilk extends RaceDetector {

    public final static int shadowSize   = 2;
    public final static int readerOffset = 0;
    public final static int writerOffset = 1;

    protected CilkProc      currProc;

    public void RaceDetectorCilk() {
        this.currProc = null;
    }

    public void onReStart() {
        // Initialize metadata
        thrdInstrumentOn = new int[thrdNum];
        // Set up the initial (global parent) task, who has no parent
        // Make currProc point to it
        this.currProc = new CilkProc(null);
        // System.out.println("Cilk detector started! "+currProc.id);
        for (int i = 0; i < thrdNum; i++) {
            thrdInstrumentOn[i] = 0;
        }
    }

    public void onSplit() {
        // Cilk spawn
        // Create the first iteration and set current proc to it
        CilkProc newProc = new CilkProc(currProc);
        currProc = newProc;
        // Initialize the proc
        currProc.spawn();
        // System.out.println("Split! "+currProc.id);
    }

    public void afterMerge() {
        // Cilk sync
        // System.out.println("Merged!");
        // Return to parent and sync
        // currProc = currProc.returnToParent();
        currProc.sync();
    }

    public void onReturn() {
        // System.out.println("Return!");
        currProc = currProc.returnToParent();
    }

    public void onNewItr() {
        // Cilk return
        // System.out.println("new itr "+currProc.id);
        // Return to parent
        currProc = currProc.returnToParent();
        // Then start a new iteration from the current proc
        CilkProc newProc = new CilkProc(currProc);
        currProc = newProc;
        currProc.spawn();
    }

    public void readCheckCilk(long id, ProcNode[] shadow, int offset) {
        int index = offset * shadowSize;
        if (shadow == null || index >= shadow.length) {
            return;
        }
        // Get the proc nodes
        ProcNode prevRead = shadow[index + readerOffset];
        ProcNode prevWrite = shadow[index + writerOffset];
        // If we're the first read, set the shadow
        if (prevWrite == null) {
            shadow[index + readerOffset] = currProc.getNode();
            return;
        }
        if (DisjointSet.findBag(prevWrite).isPBag()) {
            // Report a race
            System.out.println("WR Race!");
        }
        if (prevRead == null || DisjointSet.findBag(prevRead).isSBag()) {
            shadow[index + readerOffset] = currProc.getNode();
        }
    }

    public void writeCheckCilk(long id, ProcNode[] shadow, int offset) {
        int index = offset * shadowSize;
        if (shadow == null || index >= shadow.length) {
            return;
        }
        // Get the proc nodes
        ProcNode prevRead = shadow[index + readerOffset];
        ProcNode prevWrite = shadow[index + writerOffset];

        if (prevRead != null && DisjointSet.findBag(prevRead).isPBag()) {
            // Report a race
            System.out.println("RW Race!");
        } else if (prevWrite != null && DisjointSet.findBag(prevWrite).isPBag()) {
            System.out.println("WW Race!");
        }
        // Set new writer
        shadow[index + writerOffset] = currProc.getNode();
    }

    public static ProcNode[] mallocCilkData(int length) {
        ProcNode[] shadow = new ProcNode[length * shadowSize];
        return shadow;
    }

    @Override
    void onRead(Object obj, Object info, int offset) {
        if (offset < 0) {
            return;
        }
        int thrdId = Scheduler.getCurrThId();
        if (thrdInstrumentOn[thrdId] > 0)
            return;
        // System.out.println("On read");
        RubyBasicObject bo = (RubyBasicObject) obj;
        ProcNode[] cilkShadow = bo.getCilkShadow();
        if (cilkShadow == null
                || cilkShadow.length < (offset + 1)
                        * RaceDetectorCilk.shadowSize) {
            int len = offset + 1;
            if (cilkShadow != null && (offset + 1) * shadowSize <= cilkShadow.length * 2) {
                len =  cilkShadow.length * 2;
            }
            cilkShadow = RaceDetectorCilk.mallocCilkData(len);
            bo.setCilkShadow(cilkShadow);
        }

        readCheckCilk(bo.getObjectId(), cilkShadow, offset);
        // readCilk(bo.getObjectId(), (ProcNode[])(bo.getMetadata()), offset);
         
    }

    @Override
    void onWrite(Object obj, Object info, int offset) {
        int thrdId = Scheduler.getCurrThId();
        if (thrdInstrumentOn[thrdId] > 0)
            return;
        RubyBasicObject bo = (RubyBasicObject) obj;

        ProcNode[] cilkShadow = bo.getCilkShadow();
        if (cilkShadow == null
                || cilkShadow.length < (offset + 1)
                        * RaceDetectorCilk.shadowSize) {

            int len = offset + 1;
            if (cilkShadow != null && (offset + 1) * shadowSize <= cilkShadow.length * 2) {
                len =  cilkShadow.length * 2;
            }
            cilkShadow = RaceDetectorCilk.mallocCilkData(len);
            bo.setCilkShadow(cilkShadow);
        }

        writeCheckCilk(bo.getObjectId(), cilkShadow, offset);
        // writeCilk(bo.getObjectId(), (ProcNode[])(bo.getMetadata()), offset);
         
    }

    @Override
    void onRead(Object obj, Object info, int offset, int len) {
        // TODO Auto-generated method stub

    }

    @Override
    void onWrite(Object obj, Object info, int offset, int len) {
        // TODO Auto-generated method stub

    }

    @Override
    void onReadCheck(Object obj, Object info, int offset, int DynamicScopeDepth) {
        DynamicScope ds = (DynamicScope) obj;

        ProcNode[] cilkShadow = ds.getCilkShadow();
        if (cilkShadow == null
                || cilkShadow.length < (offset + 1)
                        * RaceDetectorCilk.shadowSize) {
            // int len = (offset + 1 >= values.length) ? offset + 1
            // : values.length;
            cilkShadow = RaceDetectorCilk.mallocCilkData(offset + 1);
            ds.setCilkShadow(cilkShadow);
        }

        readCilkOnDS(ds.getObjectId(), ds, offset, ds.getLength());
        
    }

    @Override
    void onWriteCheck(Object obj, Object info, int offset, int DynamicScopeDepth) {
        // TODO Auto-generated method stub
        DynamicScope ds = (DynamicScope) obj;

        ProcNode[] cilkShadow = ds.getCilkShadow();
        if (cilkShadow == null
                || cilkShadow.length < (offset + 1)
                        * RaceDetectorCilk.shadowSize) {
            // int len = (offset + 1 >= values.length) ? offset + 1
            // : values.length;
            cilkShadow = RaceDetectorCilk.mallocCilkData(offset + 1);
            ds.setCilkShadow(cilkShadow);
        }

        writeCilkOnDS(ds.getObjectId(), ds, offset, ds.getLength());
        
    }

    public void writeCilkOnDS(long id, DynamicScope s, int offset, int length) {
        checkCilkShadowForDS(s, offset, length);
        int index = offset * RaceDetectorCilk.shadowSize;
        ProcNode[] shadow = s.getCilkShadow();
        if (index >= shadow.length) {
            return;
        }
        writeCheckCilk(id, shadow, offset);
        // writeCilk(id, shadow, offset);
    }

    public void readCilkOnDS(long id, DynamicScope s, int offset, int length) {
        checkCilkShadowForDS(s, offset, length);
        int index = offset * RaceDetectorCilk.shadowSize;
        ProcNode[] shadow = s.getCilkShadow();
        if (index >= shadow.length) {
            System.out.println("out!");
            return;
        }
        // readCilk(id, shadow, offset);
        readCheckCilk(id, shadow, offset);
    }

    private void checkCilkShadowForDS(DynamicScope s, int offset, int length) {
        ProcNode[] shadow = s.getCilkShadow();
        if ((shadow == null)
                || (shadow.length < (offset + 1) * RaceDetectorCilk.shadowSize)) {
            shadow = RaceDetectorCilk.mallocCilkData(length);
            s.setCilkShadow(shadow);
        }
    }

    @Override
    void onTaskFork(int ctid, int itrNum) {
        // TODO Auto-generated method stub

    }

    @Override
    void onTaskJoin(int taskId) {
        // TODO Auto-generated method stub

    }

    @Override
    void onTaskExec(int taskId, int depth) {
        onSplit();

    }

    @Override
    void onTaskReMap(int taskId) {
        // TODO Auto-generated method stub

    }

    @Override
    void onStartAll(int taskId, int type) {
        // TODO Auto-generated method stub

    }

    @Override
    void onTaskDone(int taskId) {
        onReturn();

    }

    @Override
    void onEndAll(int finishId, int taskId) {
        afterMerge();

    }

    @Override
    void onStartNewItr(long i) {
        onNewItr();

    }

    @Override
    Object[] onInitMetaData(int size) {
        // TODO Auto-generated method stub
        return mallocCilkData(size);
    }

    @Override
    void onReductionPush(RubyReduction obj) {
        // TODO Auto-generated method stub

    }

    @Override
    void onReductionGet(RubyReduction obj) {
        // TODO Auto-generated method stub

    }

    @Override
    void onCall(Object obj, DynamicMethod method, IRubyObject rtn) {
        // TODO Auto-generated method stub

    }

    @Override
    void onCall(Object obj, DynamicMethod method, IRubyObject para,
            IRubyObject rtn) {
        // TODO Auto-generated method stub

    }

    @Override
    void onCall(Object obj, DynamicMethod method, IRubyObject[] args,
            IRubyObject rtn) {
        // TODO Auto-generated method stub

    }

    @Override
    public RaceTask getTask(int tid) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    void onNextStage(long pipelineId, int stageNum) {
        // System.out.println("New Stage!");
        // Restart the detector
        this.afterMerge();

    }

    @Override
    void onTaskFork(int ptid, int ctid, int itrNum) {
        // TODO Auto-generated method stub

    }

}

class CilkProc {
    // Note that Cilk race detector is purely sequential, so we can distribute
    // id sequentially.
    static protected long s_globalId = 0;

    protected long        id;
    protected DisjointSet sBag;
    protected DisjointSet pBag;
    protected CilkProc    parent;
    protected ProcNode    nodeInBags;

    public CilkProc(CilkProc parent) {
        this.id = s_globalId;
        ++s_globalId;
        this.parent = parent;
        // Top level node, set everything ready
        if (parent == null) {
            this.nodeInBags = new ProcNode((int) this.id);
            this.sBag = new DisjointSet(nodeInBags, DisjointSetNode.S_BAG_TYPE);
            this.pBag = new DisjointSet(DisjointSetNode.P_BAG_TYPE);
        }
    }

    public void spawn() {
        ProcNode proc = new ProcNode((int) this.id);
        this.nodeInBags = proc;
        this.sBag = new DisjointSet(proc, DisjointSetNode.S_BAG_TYPE);
        this.pBag = new DisjointSet(DisjointSetNode.P_BAG_TYPE);
    }

    public void sync() {
        if (this.sBag != null) {
            this.sBag.unionWith(this.pBag);
        } else {
            this.sBag = this.pBag;
            this.sBag.toSBag();
        }
        this.pBag = new DisjointSet(DisjointSetNode.P_BAG_TYPE);
    }

    public CilkProc returnToParent() {
        if (this.parent.pBag != null) {
            this.parent.pBag.unionWith(this.sBag);
        } else {
            this.parent.pBag = this.sBag;
            this.parent.pBag.toPBag();
        }
        return this.parent;
    }

    public ProcNode getNode() {
        return this.nodeInBags;
    }
}
