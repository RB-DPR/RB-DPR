/**********************************************************************
 * $Author: Weixing Ji
 * created at: Oct 2012
 * 
 * Copyright (C) 2012
 **********************************************************************/

package org.jruby.race;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Set;

import org.jruby.parlib.RubyReduction;
import org.jruby.RubyBasicObject;
import org.jruby.race.AccessSet;
import org.jruby.race.RaceDetector;
import org.jruby.race.AccessSetObj;
import org.jruby.runtime.builtin.IRubyObject;

class AccessSetHt extends AccessSet {
    // Normal per-object reference table
    private HashMap<Long, AccessSetObj> ht;
    // Flattened atomic table for faster atomic-nonatomic intersection
    private HashMap<Long, AccessSetObj> flattenedATable;

    // constructor for AccessSetHt
    public AccessSetHt() {
        ht = new HashMap<Long, AccessSetObj>();
        flattenedATable = new HashMap<Long, AccessSetObj>();
    }

    public void setHt(HashMap<Long, AccessSetObj> hm) {
        this.ht = hm;
    }

    public HashMap<Long, AccessSetObj> getHt() {
        return ht;
    }
    public HashMap<Long, AccessSetObj> getFlattenedATable() {
        return this.flattenedATable;
    }

    /**
     * Hashmap print
     */
    @Override
    public void print(){
        Long obj;
        AccessSetObj entry;
        System.out.println("Normal accesses");
        System.out.print("{");
        Set addrSet = this.ht.keySet();
        Iterator itr = addrSet.iterator();
        while (itr.hasNext()) {
            obj = (Long) itr.next();
            System.out.print(obj);
            entry = (AccessSetObj) this.ht.get(obj);
            if (entry != null) {
                System.out.print("(");
                entry.print();
                System.out.print("),");
            }

        }
        System.out.println("}");
        System.out.println("Atomic accesses");
    }

    /**
     * set intersection.
     */
    @Override
    public void intersect(AccessSet set, int taskType) {
        // perform intersection on reads/writes
        Long obj;
        AccessSetObj entry2;
        AccessSetObj entry;
        
        // if incoming set is a hashtable
        if (set instanceof AccessSetHt) {
            AccessSetHt setHt = (AccessSetHt) set;
            HashMap<Long, AccessSetObj> ht2 = setHt.getHt();
            Set addrSet = ht2.keySet();
            Iterator itr = addrSet.iterator();
            while (itr.hasNext()) {
                obj = (Long) itr.next();
                entry = (AccessSetObj) this.ht.get(obj);
                if (entry != null) {
                    // skip the reduction object on S node
                    if (entry.accessInfo instanceof RubyReduction
                            && taskType != RaceTask.RACE_TASK_FINISH) {
                        continue;
                    }
                    entry2 = (AccessSetObj) ht2.get(obj);
                    entry.intersect(entry2, obj);
                }
            }
            // Intersect my atomic part with incoming non-atomic part
            // Note: no merge!
            // Note: use flattened atomic table
            for (Long objId : ht2.keySet()) {
                AccessSetObj incomingEntry = (AccessSetObj) ht2.get(objId);
                AccessSetObj myEntry = (AccessSetObj) this.flattenedATable.get(objId);

                if (myEntry != null) {
                    // skip the reduction object on S node
                    if (!(myEntry.accessInfo instanceof RubyReduction && taskType != RaceTask.RACE_TASK_FINISH)) {
                        myEntry.intersect(incomingEntry, objId);
                    }
                }
            }
            // Intersect my non-atomic part with incoming atomic part
            // Note: no merge!
            HashMap<Long, AccessSetObj> flattenedAT2 = setHt.getFlattenedATable();
            for (Long objId : flattenedAT2.keySet()) {
                AccessSetObj incomingEntry = (AccessSetObj) (flattenedAT2.get(objId));
                AccessSetObj myEntry = (AccessSetObj) this.ht.get(objId);

                if (myEntry != null) {
                    // skip the reduction object on S node
                    if (!(myEntry.accessInfo instanceof RubyReduction && taskType != RaceTask.RACE_TASK_FINISH)) {
                        myEntry.intersect(incomingEntry, objId);
                    }
                }
            }
        }
        // the incoming log is a list
        else {
            AccessSetList setList = (AccessSetList) set;
            long[] reads = setList.getReads();
            long writes[] = setList.getWrites();
            Object[] readInfo = setList.getRdInfo();
            Object[] writeInfo = setList.getWrtInfo();
            IRubyObject[] readCommScope = setList.getReadCommScope();
            IRubyObject[] writeCommScope = setList.getWriteCommScope();

            for (int i = 0; i < setList.getReadSize(); i += 2) {
                obj = reads[i];
                // detect read-write conflicts
                // Incoming ops vs. atomic ops
                HashMap<Long, AccessSetObj> workingTable = this.flattenedATable;
                entry = (AccessSetObj) (workingTable.get(obj));
                // Only check for non-atomic ops
                if (readCommScope[i / 2] == null && entry != null) {
                    if (entry.isIn((int) reads[i + 1], WRT_FLAG)) {
                        // only find reduction problems on P node
                        if (readInfo[i / 2] instanceof RubyReduction
                                && taskType != RaceTask.RACE_TASK_FINISH) {
                            continue;
                        }
                        if (RaceDetector.detailMode) {
                            RaceDetector.recordRace(obj, (int) reads[i + 1], (MemoryOprInfo) readInfo[i / 2],
                                    ((AccessSetObjDetailBm) entry).getMemOprInfo(
                                            (int) reads[i + 1], WRT_FLAG));
                        } else {
                            RaceDetector.recordRace(obj, readInfo[i / 2], (int) reads[i + 1]);
                        }
                    }
                }
                // Incoming ops vs. non-atomic ops
                workingTable = this.ht;
                entry = (AccessSetObj) (workingTable.get(obj));
                if (entry != null) {
                    if (entry.isIn((int) reads[i + 1], WRT_FLAG)) {
                        // only find reduction problems on P node
                        if (readInfo[i / 2] instanceof RubyReduction
                                && taskType != RaceTask.RACE_TASK_FINISH) {
                            continue;
                        }
                        if (RaceDetector.detailMode) {
                            RaceDetector.recordRace(obj, (int) reads[i + 1],
                                    (MemoryOprInfo) readInfo[i / 2], ((AccessSetObjDetailBm) entry)
                                            .getMemOprInfo((int) reads[i + 1], WRT_FLAG));
                        } else {
                            RaceDetector.recordRace(obj, readInfo[i / 2], (int) reads[i + 1]);
                        }
                    }
                }
            }
            for (int i = 0; i < setList.getWriteSize(); i += 2) {
                obj = writes[i];
                // detect write-write and read-write conflicts
                // Incoming ops vs. atomic ops
                HashMap<Long, AccessSetObj> workingTable = this.flattenedATable;
                entry = (AccessSetObj) (workingTable.get(obj));
                // Only check for non-atomic ops
                if (writeCommScope[i / 2] == null && entry != null) {
                    if (entry.isIn((int) writes[i + 1], WRT_FLAG)
                        || entry.isIn((int) writes[i + 1], RD_FLAG)) {
                        // only find reduction problems on P node
                        if (writeInfo[i / 2] instanceof RubyReduction
                                && taskType != RaceTask.RACE_TASK_FINISH) {
                            continue;
                        }
                        if (RaceDetector.detailMode) {
                            if (entry.isIn((int) writes[i + 1], WRT_FLAG))
                                RaceDetector.recordRace(obj, (int) writes[i + 1],
                                        (MemoryOprInfo) writeInfo[i / 2],
                                        ((AccessSetObjDetailBm) entry).getMemOprInfo(
                                                (int) writes[i + 1], WRT_FLAG));
                            else
                                RaceDetector.recordRace(obj, (int) writes[i + 1],
                                        (MemoryOprInfo) writeInfo[i / 2],
                                        ((AccessSetObjDetailBm) entry).getMemOprInfo(
                                                (int) writes[i + 1], RD_FLAG));
                        } else {
                            RaceDetector.recordRace(obj, writeInfo[i / 2], (int) writes[i + 1]);
                        }
                    }
                }
                // Incoming ops vs. non-atomic ops
                workingTable = this.ht;
                entry = (AccessSetObj) (workingTable.get(obj));
                if (entry != null) {
                    if (entry.isIn((int) writes[i + 1], WRT_FLAG)
                            || entry.isIn((int) writes[i + 1], RD_FLAG)) {
                        // only find reduction problems on P node
                        if (readInfo[i / 2] instanceof RubyReduction
                                && taskType != RaceTask.RACE_TASK_FINISH) {
                            continue;
                        }
                        if (RaceDetector.detailMode) {
                            if (entry.isIn((int) writes[i + 1], WRT_FLAG))
                                RaceDetector.recordRace(obj, (int) writes[i + 1],
                                        (MemoryOprInfo) writeInfo[i / 2],
                                        ((AccessSetObjDetailBm) entry).getMemOprInfo(
                                                (int) writes[i + 1], WRT_FLAG));
                            else
                                RaceDetector.recordRace(obj, (int) writes[i + 1],
                                        (MemoryOprInfo) writeInfo[i / 2],
                                        ((AccessSetObjDetailBm) entry).getMemOprInfo(
                                                (int) writes[i + 1], RD_FLAG));
                        } else {
                            RaceDetector.recordRace(obj, writeInfo[i / 2],
                                    (int) writes[i + 1]);
                        }
                    }
                }
            }
        }
        // Perform intersection on methods
        intersectCall(set);
    }

    /**
     * set merge
     */
    @Override
    public AccessSet merge(AccessSet set) {
        AccessSetObj entry;
        Long obj;
        AccessSetObj entry2;
        // the incoming log is a hash table
        if (set instanceof AccessSetHt) {
            // Merge non-atomic part
            AccessSetHt setHt = (AccessSetHt) set;
            HashMap ht2 = setHt.getHt();
            Set addrSet = ht2.keySet();
            Iterator itr = addrSet.iterator();
            while (itr.hasNext()) {
                obj = (Long) itr.next();
                entry = (AccessSetObj) this.ht.get(obj);
                entry2 = (AccessSetObj) ht2.get(obj);
                if (entry != null) {
                    size += entry.merge(entry2);
                } else {
                    ht.put(obj, entry2);
                    size += entry2.size();
                }
            }
            // Merge flattened table
            // Intersect my non-atomic part with incoming atomic part
            // Note: no merge!
            HashMap<Long, AccessSetObj> flattenedAT2 = setHt.getFlattenedATable();
            HashMap<Long, AccessSetObj> myAT = this.flattenedATable;
            for (Long objId : flattenedAT2.keySet()) {
                AccessSetObj incomingEntry = (AccessSetObj) (flattenedAT2.get(objId));
                AccessSetObj myEntry = (AccessSetObj) this.flattenedATable.get(objId);
                if (myEntry != null) {
                    myEntry.merge(incomingEntry);
                } else {
                    myAT.put(objId, incomingEntry);
                }
            }
        } else { // the incoming log is a list
            AccessSetList setList = (AccessSetList) set;
            long[] reads = setList.getReads();
            long writes[] = setList.getWrites();
            Object[] readInfo = setList.getRdInfo();
            Object[] writeInfo = setList.getWrtInfo();
            IRubyObject[] readCommScope = setList.getReadCommScope();
            IRubyObject[] writeCommScope = setList.getWriteCommScope();
            for (int i = 0; i < setList.getReadSize(); i += 2) {
                obj = reads[i];
                // Check for atomic ops
                HashMap<Long, AccessSetObj> workingTable = this.ht;
                if (readCommScope[i / 2] != null) {
                    workingTable = this.flattenedATable;
                }
                entry = (AccessSetObj) (workingTable.get(obj));
                // merge reads
                if (entry == null) {
                    if (RaceDetector.spaceTreeOn
                            && readInfo[i / 2] instanceof String
                            && ((String) readInfo[i / 2]).equals("ARRAY")) {
                        entry = new AccessSetObjSt();
                        entry.insert((int) (reads[i + 1]), RD_FLAG);
                    } else if (RaceDetector.detailMode) {
                        entry = new AccessSetObjDetailBm((int) (reads[i + 1]),
                                RD_FLAG, (MemoryOprInfo) readInfo[i / 2]);
                        size += 2;
                    } else {
                        entry = new AccessSetObjBm(readInfo[i / 2],
                                (int) (reads[i + 1]), 0, 0, RD_FLAG);
                        size += 2;
                    }
                    workingTable.put(obj, entry);
                } else {
                    if (RaceDetector.detailMode) {
                        size += ((AccessSetObjDetailBm) entry).insert((int) reads[i + 1], RD_FLAG,
                                (MemoryOprInfo) readInfo[i / 2]);
                    } else {
                        size += entry.insert((int) reads[i + 1], RD_FLAG);
                    }
                }
            }
            for (int i = 0; i < setList.getWriteSize(); i += 2) {
                obj = writes[i];
                // For atomic ops
                HashMap<Long, AccessSetObj> workingTable = this.ht;
                if (writeCommScope[i / 2] != null) {
                    workingTable = this.flattenedATable;
                }
                entry = (AccessSetObj) (workingTable.get(obj));
                // merge writes
                if (entry == null) {
                    if (RaceDetector.spaceTreeOn
                            && writeInfo[i / 2] instanceof String
                            && ((String) writeInfo[i / 2]).equals("ARRAY")) {

                        entry = new AccessSetObjSt();
                        entry.insert((int) (writes[i + 1]), WRT_FLAG);
                    } else if (RaceDetector.detailMode) {
                        entry = new AccessSetObjDetailBm((int) (writes[i + 1]),
                                WRT_FLAG, (MemoryOprInfo) writeInfo[i / 2]);
                        size += 2;
                    } else {
                        entry = new AccessSetObjBm(writeInfo[i / 2],
                                (int) (writes[i + 1]), 0, 0, WRT_FLAG);
                        size += 2;
                    }
                    workingTable.put(obj, entry);
                } else {
                    if (RaceDetector.detailMode) {
                        size += ((AccessSetObjDetailBm) entry).insert((int) writes[i + 1], WRT_FLAG,
                                (MemoryOprInfo) writeInfo[i / 2]);
                    } else {
                        size += entry.insert((int) writes[i + 1], WRT_FLAG);
                    }
                }
            }
        }

        this.mergeNum += set.getMergeNum();

        // for commutative
        mergeCall(set);
        set.clear();
        return this;
    }

    /**
     * set intersection and merge
     */
    @Override
    public AccessSet intersectAndMerge(AccessSet set, int taskType) {
        long obj;
        AccessSetObj entry;
        AccessSetObj entry2;

        // TODO: intersect and merge with atomicTable
        // the incoming log is a hash table
        if (set instanceof AccessSetHt) {
             // Intersect and merge non-atomic part
            AccessSetHt setHt = (AccessSetHt) set;
            HashMap<Long, AccessSetObj> ht2 = setHt.getHt();

            Set addrSet = ht2.keySet();
            Iterator itr = addrSet.iterator();
            while (itr.hasNext()) {
                obj = (Long) itr.next();
                entry = (AccessSetObj) this.ht.get(obj);
                if (entry != null) {
                    entry2 = (AccessSetObj) ht2.get(obj);
                    // skip the reduction object on S node
                    if (!(entry.accessInfo instanceof RubyReduction && taskType != RaceTask.RACE_TASK_FINISH)) {
                        entry.intersect(entry2, obj);
                    }
                    size += entry.merge(entry2);

                } else {// merge
                    entry2 = (AccessSetObj) ht2.get(obj);
                    this.ht.put(obj, entry2);
                    size += entry2.size();
                }
            }
            this.mergeNum += set.getMergeNum();
            // Intersect my atomic part with incoming non-atomic part
            // Note: no merge!
            // Note: use flattened atomic table
            for (Long objId : ht2.keySet()) {
                AccessSetObj incomingEntry = (AccessSetObj) ht2.get(objId);
                AccessSetObj myEntry = (AccessSetObj) this.flattenedATable.get(objId);
                if (myEntry != null) {
                    // skip the reduction object on S node
                    if (!(myEntry.accessInfo instanceof RubyReduction && taskType != RaceTask.RACE_TASK_FINISH)) {
                        myEntry.intersect(incomingEntry, objId);
                    }
                }
            }
            // Intersect my non-atomic part with incoming atomic part
            // Note: no merge!
            HashMap<Long, AccessSetObj> flattenedAT2 = setHt.getFlattenedATable();
            for (Long objId : flattenedAT2.keySet()) {
                AccessSetObj incomingEntry = (AccessSetObj) (flattenedAT2.get(objId));
                AccessSetObj myEntry = (AccessSetObj) this.ht.get(objId);
                if (myEntry != null) {
                    // skip the reduction object on S node
                    if (!(myEntry.accessInfo instanceof RubyReduction && taskType != RaceTask.RACE_TASK_FINISH)) {
                        myEntry.intersect(incomingEntry, objId);
                    }
                }
            }
            // Merge flattened table
            HashMap<Long, AccessSetObj> myAT = this.flattenedATable;
            for (Long objId : flattenedAT2.keySet()) {
                AccessSetObj incomingEntry = (AccessSetObj) (flattenedAT2.get(objId));
                AccessSetObj myEntry = (AccessSetObj) this.flattenedATable.get(objId);
                if (myEntry != null) {
                    myEntry.merge(incomingEntry);
                } else {
                    myAT.put(objId, incomingEntry);
                }
            }
            // Deal with ac-ops
            intersectCall(set);
            mergeCall(set);
        }
        // the incoming log is a list
        else {
            this.intersect(set, taskType);
            this.merge(set);
        }

        set.clear();
        return this;
    }

    @Override
    public void clear() {
        this.mergeNum = 0;
        ht.clear();
        flattenedATable.clear();
        super.clear();
    }

    @Override
    public long size() {
        return this.size;
    }

    /**
     * Insert a log into current access-set 
     */
    @Override
    public AccessSet insert(long obj, int offset, int flag, int file,
            int line, Object info, IRubyObject commScope) {
        if (offset < 0){
            return this;
        }
        
        HashMap<Long, AccessSetObj> operatingTable = ht;
        int atomicModifier = 0;
        // If the incoming flag indicates this is an op in atomic method, change
        if (flag >= AccessSet.ATOMIC_OP_OFFSET) {
            atomicModifier = AccessSet.ATOMIC_OP_OFFSET;
            // Add this operation to flattened table
            AccessSetObj flattenedEntry = (AccessSetObj) this.flattenedATable.get(obj);
            if (flattenedEntry == null) {
                if (RaceDetector.detailMode) {
                    flattenedEntry = new AccessSetObjDetailBm(offset, flag - atomicModifier,
                            ((MemoryOprInfo) info).clone());
                } else {
                    flattenedEntry = new AccessSetObjBm(info, offset, line, file, flag
                            - atomicModifier);
                }
                this.flattenedATable.put(obj, flattenedEntry);
            } else {
                if (RaceDetector.detailMode) {
                    ((AccessSetObjDetailBm) flattenedEntry).insert(offset, flag - atomicModifier,
                            ((MemoryOprInfo) info).clone());
                } else {
                    flattenedEntry.insert(offset, flag - atomicModifier);
                }
            }
            return this;
        } 
        AccessSetObj entry = (AccessSetObj) operatingTable.get(obj);
        if (entry == null) {
            if (RaceDetector.spaceTreeOn && info instanceof String
                    && ((String) info).equals("ARRAY")) {
                entry = new AccessSetObjSt();
            } else if (RaceDetector.detailMode) {
                entry = new AccessSetObjDetailBm(offset, flag - atomicModifier,
                        ((MemoryOprInfo) info).clone());
            } else {
                entry = new AccessSetObjBm(info, offset, line, file, flag - atomicModifier);
            }
            operatingTable.put(obj, entry);
            size += 2;
        } else {
            if (RaceDetector.detailMode) {
                size += ((AccessSetObjDetailBm) entry).insert(offset, flag - atomicModifier,
                        ((MemoryOprInfo) info).clone());
            } else {
                size += entry.insert(offset, flag - atomicModifier);
            }
        }
        return this;
    }

    /**
     * Insert a batch of accesses into current set
     */
    @Override
    public AccessSet insert(long obj, int offset, int flag, int len, Object info, IRubyObject commScope) {
        if (offset < 0)
            return this;
        HashMap<Long, AccessSetObj> operatingTable = ht;
        int atomicModifier = 0;
        // If the incoming flag indicates this is an op in atomic method, change
        if (flag >= AccessSet.ATOMIC_OP_OFFSET) {
            atomicModifier = AccessSet.ATOMIC_OP_OFFSET;
            // Add this operation to flattened table
            AccessSetObj flattenedEntry = (AccessSetObj) this.flattenedATable.get(obj);
            if (flattenedEntry == null) {
                if (RaceDetector.detailMode) {
                    flattenedEntry = new AccessSetObjDetailBm(offset, flag, len - atomicModifier,
                            ((MemoryOprInfo) info).clone());
                } else {
                    flattenedEntry = new AccessSetObjBm(info, offset, flag - atomicModifier, len);
                }
                this.flattenedATable.put(obj, flattenedEntry);
            } else {
                if (RaceDetector.detailMode) {
                    ((AccessSetObjDetailBm) flattenedEntry).insert(offset, flag, len
                            - atomicModifier, ((MemoryOprInfo) info).clone());
                } else {
                    flattenedEntry.insert(offset, flag - atomicModifier);
                }
            }
            return this;
        }
        
        AccessSetObj entry = (AccessSetObj) operatingTable.get(obj);

        if (entry == null) {
            if (RaceDetector.spaceTreeOn && info instanceof String
                    && ((String) info).equals("ARRAY")) {
                entry = new AccessSetObjSt();
                entry.insert(offset, flag - atomicModifier, len);
            } else {
                if (RaceDetector.detailMode) {
                    entry = new AccessSetObjDetailBm(offset, flag, len - atomicModifier,
                            ((MemoryOprInfo) info).clone());
                } else {
                    entry = new AccessSetObjBm(info, offset, flag - atomicModifier, len);
                }
            }
            operatingTable.put(obj, entry);
            // update the set size;
            size += 2;
        } else {
            if (RaceDetector.detailMode) {
                size += ((AccessSetObjDetailBm) entry).insert(offset, flag, len
                        - atomicModifier, ((MemoryOprInfo) info).clone());
            } else {
                size += entry.insert(offset, flag - atomicModifier, len);
            }
        }
        return this;
    }

    /**
     * Calculate the intersection of two sets
     */
    @Override
    public void calComSet(AccessSet set) {
        HashMap ht3 = new HashMap();
        HashMap ht2 = ((AccessSetHt) set).getHt();
        Set addrSet = ht.keySet();
        Iterator itr = addrSet.iterator();
        while (itr.hasNext()) {
            Long obj = (Long) itr.next();
            AccessSetObj entry = (AccessSetObj) ht2.get(obj);
            if (entry == null) {

            } else {
                AccessSetObj entry2 = (AccessSetObj) ht2.get(obj);
                entry.and(entry2);
                ht3.put(obj, entry2);
            }
        }
        System.out.println("common set size = " + ht3.size());
        addrSet = ht3.keySet();
        itr = addrSet.iterator();
        while (itr.hasNext()) {
            long obj = (Long) itr.next();
            AccessSetObj entry = (AccessSetObj) ht3.get(obj);
            if (entry.accessInfo != null)
                System.out.println(entry.accessInfo.toString() + ":accesses="
                        + entry.accessNum);
            else
                System.out.println(":accesses=" + entry.accessNum);
        }
    }

    @Override
    public void printStatis() {
        Set addrSet = this.ht.keySet();
        Iterator itr = addrSet.iterator();
        long sizeTotal = 0;
        while (itr.hasNext()) {
            long obj = (Long) itr.next();
            AccessSetObj entry = (AccessSetObj) this.ht.get(obj);
            sizeTotal += entry.size();
        }
    }

    @Override
    public boolean isLarge() {
        // TODO: change 4096
        return (this.ht.size() > 4096);
    }

}
