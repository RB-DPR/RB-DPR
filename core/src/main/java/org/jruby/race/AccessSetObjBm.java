package org.jruby.race;

import org.jruby.race.AccessSet;
import org.jruby.race.RaceDetector;
import org.jruby.race.AccessSetObj;

class AccessSetObjBm extends AccessSetObj {
    public int[]       values;          // array to hold the bitmap
    public int         begin       = 0; // start position
    public final static int INT_BIT_NUM = 32; // bit number for a integer
    
    public AccessSetObjBm() {
        this.accessInfo = null;
    }

    public AccessSetObjBm(Object info, int offset, int line, int file, int flag) {
        int index = offset / INT_BIT_NUM * 2;
        values = new int[2];
        begin = index;
        insert(offset, flag);
        this.accessInfo = info;
    }

    public AccessSetObjBm(Object info, int offset, int flag, int len) {
        int index = offset / INT_BIT_NUM * 2;
        values = new int[len * 2 + 2];
        begin = index;
        insert(offset, flag, len);
        this.accessInfo = info;
    }

    // public static long[] expandNum = new long[1024];
    private void expand(int index) {
        // expandNum[ThreadManager.getCurrThId() * 16]++;
        int[] newValues = null;
        int newBegin = 0;
        int orgsize = values.length;
        // index is smaller than begin
        if (index < begin) {
            int extendedLength = values.length;
            int gap = begin - index;
            // Make sure we always have enough space for incoming index
            if (extendedLength < gap) {
                extendedLength = gap;
            }
            // Make sure we won't reach anywhere below addr 0
            if (extendedLength > begin) {
                extendedLength = begin;
            }
            // Alloc new array and move old values back
            newValues = new int[extendedLength + values.length];
            System.arraycopy(values, 0, newValues, extendedLength,
                    values.length);
            // Update head pointer
            begin -= extendedLength;
            values = newValues;
        } else if (index >= begin + values.length) {
            if (index >= begin + values.length * 2) {
                newValues = new int[index + 2 - begin];
                System.arraycopy(values, 0, newValues, 0, values.length);
                values = newValues;
            } else {
                newValues = new int[values.length * 2];
                System.arraycopy(values, 0, newValues, 0, values.length);
                values = newValues;
            }
        }
        // System.out.println(this.toString() + "expand from " + orgsize +
        // " to : " + values.length);
    }

    @Override
    public boolean isIn(int offset, int flag) {
        int index = offset / INT_BIT_NUM * 2;
        // expand the values array
        if (index < begin || index >= begin + values.length)
            return false;
        // set the bit
        if (flag == AccessSet.RD_FLAG)
            return ((values[index - begin] & (1 << (offset % INT_BIT_NUM))) != 0);
        else
            return ((values[index + 1 - begin] & (1 << (offset % INT_BIT_NUM))) != 0);
    }

    // public static long[] setBitNum = new long[1024];
    @Override
    public int insert(int offset, int flag) {
        // setBitNum[ThreadManager.getCurrThId() * 16]++;
        int increased = 0;
        int index = offset / INT_BIT_NUM * 2;
        // expand the values array
        if (index < begin || index >= begin + values.length) {
            increased = values.length;
            expand(index);
            increased = values.length - increased;
        }

        // set the bit
        if (flag == AccessSet.RD_FLAG)
            values[index - begin] |= (1 << (offset % INT_BIT_NUM));
        else
            values[index + 1 - begin] |= (1 << (offset % INT_BIT_NUM));

        return increased;
    }

    @Override
    public int insert(int offset, int flag, int len) {
        // setBitNum[ThreadManager.getCurrThId() * 16]++;
        int increased = 0;
        int index = offset / INT_BIT_NUM * 2;
        // expand the values array
        if (index < begin || index >= begin + values.length)
            expand(index);
        index = (offset + len) / INT_BIT_NUM * 2;
        if (index < begin || index >= begin + values.length) {
            increased = values.length;
            expand(index);
            increased = values.length - increased;
        }
        // System.out.println("offset=" + offset + ", len=" + len);
        // set the bit
        // TODO:needs a more efficient algorithm here
        for (int i = 0; i < len; i++) {
            if (flag == AccessSet.RD_FLAG)
                values[index - begin] |= (1 << ((offset + i) % INT_BIT_NUM));
            else
                values[index + 1 - begin] |= (1 << ((offset + i) % INT_BIT_NUM));
        }

        return increased;
    }

    // public static long[] intersectNum = new long[1024];
    @Override
    public void intersect(AccessSetObj entry, long obj) {
        // intersectNum[ThreadManager.getCurrThId() * 16]++;
        int start = 0;
        int end = 0;
        AccessSetObjBm bm = (AccessSetObjBm) entry;

        // low bound of intersection
        if (this.begin < bm.begin)
            start = bm.begin;
        else
            start = this.begin;
        // up bound of intersection
        if (this.begin + values.length < bm.begin + bm.values.length)
            end = this.begin + values.length;
        else
            end = bm.begin + bm.values.length;

        int index1 = start - begin; // start index for this bitmap
        int index2 = start - bm.begin; // start index for the incoming bitmap
        int result = 0;
        int offset = 0;

        // System.out.println("intersect: start=" + start + ",end=" + end);
        for (int i = start; i < end; i += 2) {
            // read-write
            result = (values[index1]) & (bm.values[index2 + 1]);
            // write-read
            result |= ((values[index1 + 1]) & (bm.values[index2]));
            // write-write
            result |= ((values[index1 + 1]) & (bm.values[index2 + 1]));
            offset = start * INT_BIT_NUM;

            while (result != 0) {
                offset += Integer.numberOfTrailingZeros(result) + 1;
                RaceDetector.recordRace(obj, accessInfo, offset - 1);
                // Note: >>> 32 may cause problems...
                result = result >>> (Integer.numberOfTrailingZeros(result));
                result = result >>> 1;
                // System.out.println("while");
            }
            index1 += 2;
            index2 += 2;
        }
    }

    // public static long[] mergeNum = new long[1024];
    @Override
    public int merge(AccessSetObj entry) {
        // mergeNum[ThreadManager.getCurrThId() * 16]++;
        int orgSize = values.length;
        AccessSetObjBm bm = (AccessSetObjBm) entry;
        // expand
        if (this.begin > bm.begin)
            expand(bm.begin);
        // expand
        if (this.begin + values.length < bm.begin + bm.values.length)
            expand(bm.begin + bm.values.length);
        // merge
        // System.out.println("merge: start=" + bm.begin + ",end=" + (bm.begin +
        // values.length));
        for (int i = bm.begin; i < bm.begin + bm.values.length; i++) {
            values[i - begin] |= bm.values[i - bm.begin];
        }
        return values.length - orgSize;
    }

    @Override
    public void print() {
        System.out.print("start position=" + begin + ":");
        for (int i = 0; i < values.length; i++)
            System.out.print(Integer.toBinaryString(values[i]) + ",");
        System.out.println("");
    }

    @Override
    public void and(AccessSetObj entry) {
        // TODO Auto-generated method stub

    }

    @Override
    public void clear() {
        // TODO Auto-generated method stub

    }

    @Override
    public int size() {
        // TODO Auto-generated method stub
        return values.length;
    }

}
