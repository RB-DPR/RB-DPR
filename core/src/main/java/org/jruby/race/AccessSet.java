/**********************************************************************
 * AccessSet.java -
 * 
 * 
 * Copyright (C) 2012
 **********************************************************************/
package org.jruby.race;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Set;

import org.jruby.RubyBasicObject;
import org.jruby.internal.runtime.methods.DynamicMethod;
import org.jruby.runtime.builtin.IRubyObject;

public abstract class AccessSet {
    private static final int LOG_TYPE_HT = 0; // hash table
    private static final int LOG_TYPE_LIST = 1; // hash table
    private static int bfBitNum = 2048; // bloom filter
    private static int bfHashFuncNum = 1; // bloom filter

    private static int logType = LOG_TYPE_HT;

    // OOB race detection
    public static final int LOG_WORK_MERGE = 0;
    public static final int LOG_WORK_HYBRID = 1;
    public static final int LOG_WORK_CHECK = 2;

    // Operation type
    public static final int RD_FLAG = 0x1;
    public static final int WRT_FLAG = 0x2;
    public static final int ATOMIC_OP_OFFSET = 0x10;
    public static final int ATOMIC_RD_FLAG = ATOMIC_OP_OFFSET + RD_FLAG;
    public static final int ATOMIC_WRT_FLAT = ATOMIC_OP_OFFSET + WRT_FLAG;

    public static final int INT_BIT = 32;

    protected HashMap<Long, IMethodCallHistory> htMethod;

    protected int mergeNum = 0;
    protected int workType = LOG_WORK_HYBRID;
    protected int taskId = 0;
    protected int size = 0;

    public int getMergeNum() {
        return mergeNum;
    }

    public void setMergeNum(int num) {
        mergeNum = num;
    }

    public void setWorkType(int type) {
        workType = type;
    }

    public int getWorkType() {
        return workType;
    }

    protected int getTaskId() {
        return taskId;
    }

    protected void setTaskId(int id) {
        taskId = id;
    }

    public void setHtMethod(HashMap<Long, IMethodCallHistory> hm) {
        this.htMethod = hm;
    }

    public HashMap<Long, IMethodCallHistory> getHtMethod() {
        return this.htMethod;
    }

    public AccessSet() {
        htMethod = new HashMap<Long, IMethodCallHistory>();
    }

    abstract public void intersect(AccessSet log, int taskType);

    abstract public AccessSet merge(AccessSet log);

    abstract public AccessSet intersectAndMerge(AccessSet log, int taskType);

    public void clear() {
        htMethod.clear();
    }

    abstract public AccessSet insert(long obj, int offset, int flag, int file, int line,
            Object info, IRubyObject commScope);

    abstract public AccessSet insert(long obj, int offset, int flag, int len, Object info,
            IRubyObject commScope);

    abstract public void print();

    abstract public void printStatis();

    abstract public void calComSet(AccessSet log);

    abstract public boolean isLarge();

    abstract public long size();

    /**
     * Retrieve the hash entry in current set according to object id and method
     * 
     * @param id
     * @param method
     * @return a call history (never null)
     */
    private IMethodCallHistory getEntryInHtMethod(long id, DynamicMethod method) {
        IMethodCallHistory entry = (IMethodCallHistory) this.htMethod.get(id);
        if (entry == null) {
            if (method.getImplementationClass().isSimpleTable()) {
                if (RaceDetector.detailMode)
                    entry = new AccessSetDetailSimpleMethod();
                else
                    entry = new AccessSetSimpleMethod();
            } else {
                entry = new AccessSetMethod();
            }
            this.htMethod.put(id, entry);
            // update the set size;
            size += 2;
        }
        // Entry should never be null!
        return entry;
    }

    /**
     * insert a call into current set
     * 
     * @param id
     * @param method
     * @param rtn
     */
    public void insertCall(long id, DynamicMethod method, IRubyObject rtn) {
        IMethodCallHistory entry = this.getEntryInHtMethod(id, method);
        entry.addMethodCall(method, rtn);
    }

    /**
     * insert a call into current set
     * 
     * @param id
     * @param method
     * @param para
     * @param rtn
     */
    public void insertCall(long id, DynamicMethod method, IRubyObject para, IRubyObject rtn) {
        IMethodCallHistory entry = this.getEntryInHtMethod(id, method);
        entry.addMethodCall(method, para, rtn);
    }

    /**
     * insert a call into current set
     * 
     * @param id
     * @param method
     * @param paras
     * @param rtn
     */
    public void insertCall(long id, DynamicMethod method, IRubyObject[] paras, IRubyObject rtn) {
        IMethodCallHistory entry = this.getEntryInHtMethod(id, method);
        entry.addMethodCall(method, paras, rtn);
    }
    
    /**
     * Insert a call into current set, under detail mode
     * @param id
     * @param method
     * @param rtn
     * @param info
     */
    public void insertCall(long id, DynamicMethod method, IRubyObject rtn, MemoryOprInfo info) {
        AccessSetDetailSimpleMethod entry = (AccessSetDetailSimpleMethod) this.getEntryInHtMethod(id, method);
        entry.addMethodCall(method, rtn, info.clone());
    }

    /**
     * Insert a call into current set, under detail mode
     * @param id
     * @param method
     * @param para
     * @param rtn
     * @param info
     */
    public void insertCall(long id, DynamicMethod method, IRubyObject para,
                           IRubyObject rtn, MemoryOprInfo info) {
        AccessSetDetailSimpleMethod entry = (AccessSetDetailSimpleMethod) this.getEntryInHtMethod(id, method);
        entry.addMethodCall(method, para, rtn, info.clone());
    }

    /**
     * Insert a call into current set, under detail mode
     * @param id
     * @param method
     * @param paras
     * @param rtn
     * @param info
     */
    public void insertCall(long id, DynamicMethod method, IRubyObject[] paras,
            IRubyObject rtn, MemoryOprInfo info) {
        AccessSetDetailSimpleMethod entry = (AccessSetDetailSimpleMethod) this.getEntryInHtMethod(id, method);
        entry.addMethodCall(method, paras, rtn, info.clone());
    }

    /**
     * intersect incoming call set with current call set
     * 
     * @param setRight
     */
    public void intersectCall(AccessSet setRight) {
        Long id;
        IMethodCallHistory entryRight;
        IMethodCallHistory entryLeft;
        HashMap<Long, IMethodCallHistory> htRight = setRight.getHtMethod();
        Set<Long> idSetRight = htRight.keySet();
        Iterator<Long> itrRight = idSetRight.iterator();
        while (itrRight.hasNext()) {
            id = (Long) itrRight.next();
            entryRight = htRight.get(id);
            entryLeft = this.htMethod.get(id);
            if (entryLeft != null) {
                entryLeft.intersect(entryRight);
            }
        }
    }

    /**
     * Merge incoming call set into current call set
     * 
     * @param setRight
     */
    public void mergeCall(AccessSet setRight) {
        Long id;
        IMethodCallHistory entryRight;
        IMethodCallHistory entryLeft;
        HashMap<Long, IMethodCallHistory> htRight = setRight.getHtMethod();
        Set<Long> idSetRight = htRight.keySet();
        Iterator<Long> itrRight = idSetRight.iterator();
        while (itrRight.hasNext()) {
            id = (Long) itrRight.next();
            entryRight = (IMethodCallHistory) htRight.get(id);
            entryLeft = (IMethodCallHistory) this.htMethod.get(id);
            if (entryLeft != null) {
                entryLeft.merge(entryRight);
            } else {
                this.htMethod.put(id, entryRight);
            }
        }
    }

    /**
     * Intersection and merge method call sets
     * 
     * @param set
     */
    public void intersectAndMergeCall(AccessSet set) {
        intersectCall(set);
        mergeCall(set);
    }
}
