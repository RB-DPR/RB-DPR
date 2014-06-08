/**********************************************************************
 * AccessSetList.java -
 * 
 * $Author: Weixing Ji
 * created at: Oct 2012
 * 
 * Copyright (C) 2012
 **********************************************************************/
package org.jruby.race;

import java.util.ArrayList;

import org.jruby.internal.runtime.methods.DynamicMethod;
import org.jruby.race.AccessSet;
import org.jruby.runtime.builtin.IRubyObject;
/**
 * List presentation of access set
 * @author 
 *
 */
class AccessSetList extends AccessSet {
    private static final int BLOCK_SIZE = 1024;
    private long[]           readBlock;         // current read block
    private long[]           writeBlock;        // current write block
    private Object[]         readInfo;          // class/scope 
    private Object[]         writeInfo;         // class/scope
    private IRubyObject[]    readCommScope;     // whether this read is atomic
    private IRubyObject[]    writeCommScope;    // whether this write is atomic
    private int              readIndex  = 0;    // current read index
    private int              writeIndex = 0;    // current write index

    public AccessSetList() {
        // every read/write has two fields: id and offset
        readBlock = new long[BLOCK_SIZE * 2];
        writeBlock = new long[BLOCK_SIZE * 2];
        if (RaceDetector.detailMode) {
            readInfo = new MemoryOprInfo[BLOCK_SIZE];
            writeInfo = new MemoryOprInfo[BLOCK_SIZE];
        } else {
            readInfo = new Object[BLOCK_SIZE];
            writeInfo = new Object[BLOCK_SIZE];
        }
        // Commutative scopes for read and write ops. null means non-atomic
        readCommScope = new IRubyObject[BLOCK_SIZE];
        writeCommScope = new IRubyObject[BLOCK_SIZE];
    }

    public int getReadSize() {
        return readIndex;
    }

    public int getWriteSize() {
        return writeIndex;
    }

    public long[] getReads() {
        return readBlock;
    }

    public long[] getWrites() {
        return writeBlock;
    }

    public Object[] getRdInfo() {
        return readInfo;
    }

    public Object[] getWrtInfo() {
        return writeInfo;
    }
    
    public IRubyObject[] getReadCommScope() {
        return readCommScope;
    }
    
    public IRubyObject[] getWriteCommScope() {
        return writeCommScope;
    }

    /**
     * Convert a list presentation ot a hash presentation
     * @return
     */
    public AccessSetHt convertToHt() {
        AccessSetHt set = new AccessSetHt();
        // insert all reads into the hash table
        for (int j = 0; j < readIndex; j += 2) {
            // Check whether this operation is an atomic op
            int atomicModifier = 0;
            if (readCommScope[j / 2] != null) {
                atomicModifier = ATOMIC_OP_OFFSET;
            }
            set.insert(readBlock[j], (int) readBlock[j + 1], RD_FLAG + atomicModifier,
                    0, 0, readInfo[j / 2], readCommScope[j / 2]);
        }
        // insert all writes into the hash table
        for (int j = 0; j < writeIndex; j += 2) {
            // Check whether this operation is an atomic op
            int atomicModifier = 0;
            if (writeCommScope[j / 2] != null) {
                atomicModifier = ATOMIC_OP_OFFSET;
            }
            set.insert(writeBlock[j], (int) writeBlock[j + 1],
                    WRT_FLAG + atomicModifier, 0, 0, writeInfo[j / 2], writeCommScope[j / 2]);
        }
        set.mergeNum = this.mergeNum;
        set.setHtMethod(this.htMethod);
        return set;
    }

    
    /**
     * List intersection
     */
    @Override
    public void intersect(AccessSet set, int taskType) {
        // it is impossible to intersect two list logs
        System.out.println("List::intersect:Program should not run to this point!");
        System.exit(0);
    }

    /**
     * List merge
     */
    @Override
    public AccessSet merge(AccessSet set) {
        if (set instanceof AccessSetHt) {
            AccessSetHt ht = this.convertToHt();
            ht.merge(set);
            set.clear();
            return ht;
        } else {
            AccessSetList logList = (AccessSetList) set;
            if (this.readIndex + logList.getReadSize() <= BLOCK_SIZE * 2
                    && this.writeIndex + logList.getWriteSize() <= BLOCK_SIZE * 2) {
                System.arraycopy(logList.getReads(), 0, readBlock, readIndex,
                        logList.getReadSize());
                System.arraycopy(logList.getWrites(), 0, writeBlock,
                        writeIndex, logList.getWriteSize());
                System.arraycopy(logList.getRdInfo(), 0, readInfo,
                        readIndex / 2, logList.getReadSize() / 2);
                System.arraycopy(logList.getWrtInfo(), 0, writeInfo,
                        writeIndex / 2, logList.getWriteSize() / 2);
                // For read and write atomic arrays
                System.arraycopy(logList.getReadCommScope(), 0, readCommScope,
                        readIndex / 2, logList.getReadSize() / 2);
                System.arraycopy(logList.getWriteCommScope(), 0, writeCommScope,
                        writeIndex / 2, logList.getWriteSize() / 2);
                readIndex += logList.getReadSize();
                writeIndex += logList.getWriteSize();
                this.mergeNum += logList.mergeNum;
                return this;
            } else {
                AccessSetHt ht = this.convertToHt();
                ht.merge(set);
                return ht;
            }
        }
    }

    /**
     * List intersection and merge
     */
    @Override
    public AccessSet intersectAndMerge(AccessSet set, int taskType) {
        // convert myself to hash table
        AccessSet ht = this.convertToHt();
        ht.intersectAndMerge(set, taskType);
        return ht;
    }

    public void clear() {
        this.mergeNum = 0;
        this.readIndex = 0;
        this.writeIndex = 0;
        super.clear();
    }

    @Override
    public long size() {
        return readIndex + writeIndex;
    }

    /**
     * Insert a log into a list
     */
    @Override
    public AccessSet insert(long obj, int offset, int flag, int file,
            int line, Object info, IRubyObject commScope) {
        if (offset < 0)
            return this;
        // read
        boolean opIsAtomic = false;
        // If the operation is atomic, change the atomicity flag
        if (flag >= ATOMIC_OP_OFFSET) {
            opIsAtomic = true;
            flag -= ATOMIC_OP_OFFSET;
        }
        if (flag == RD_FLAG) {
            // expand the array if necessary
            if (readIndex >= BLOCK_SIZE * 2) {
                AccessSetHt ht = this.convertToHt();
                // Since we modified the flag for atomic ops, we need to add it
                // back
                if (opIsAtomic) {
                    flag += ATOMIC_OP_OFFSET;
                }
                ht.insert(obj, offset, flag, file, line, info, commScope);
                return ht;
            }
            // append id and offset to the read block
            readBlock[readIndex] = obj;
            readBlock[readIndex + 1] = offset;
            readInfo[readIndex / 2] = info;
            // mark atomic op information
            readCommScope[readIndex / 2] = commScope;
            readIndex += 2;
        } else if (flag == WRT_FLAG) { // Write
            // expand the array if necessary
            if (writeIndex >= BLOCK_SIZE * 2) {
                AccessSetHt ht = this.convertToHt();
                // Since we modified the flag for atomic ops, we need to add it
                // back
                if (opIsAtomic) {
                    flag += ATOMIC_OP_OFFSET;
                }
                ht.insert(obj, offset, flag, file, line, info, commScope);
                return ht;
            }
            // append id and offset to the read block
            writeBlock[writeIndex] = obj;
            writeBlock[writeIndex + 1] = offset;
            writeInfo[writeIndex / 2] = info;
            // mark atomic op information
            writeCommScope[writeIndex / 2] = commScope;
            writeIndex += 2;
        }

        return this;
    }

    /**
     * Insert a batch of accesses into current list
     */
    @Override
    public AccessSet insert(long obj, int offset, int flag, int len, Object info, IRubyObject commScope) {
        AccessSet result = this;
        for (int i = 0; i < len; i++) {
            result = result.insert(obj, offset + i, flag, 0, 0, info, commScope);
        }
        return result;
    }

    public void dedup() {}

    @Override
    public void calComSet(AccessSet log) {}

    @Override
    public boolean isLarge() {
        // TODO: change 4096
        return (readBlock.length > 8192 || writeBlock.length > 8192);
    }

    @Override
    public void print() {
        System.out.print("reads=(");
        for (int i = 0; i < readIndex; i += 2)
            System.out.print("[" + readBlock[i] + "," + readBlock[i + 1] + "]");
        System.out.print("),writes=(");
        for (int i = 0; i < writeIndex; i += 2)
            System.out.print("[" + writeBlock[i] + "," + writeBlock[i + 1]
                    + "]");
        System.out.println(")");
    }

    @Override
    public void printStatis() {
        System.out.println("read vector:" + readIndex);
        System.out.println("Write vector:" + writeIndex);
    }
}
