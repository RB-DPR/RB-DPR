/*
 **** BEGIN LICENSE BLOCK *****
 * Version: EPL 1.0/GPL 2.0/LGPL 2.1
 *
 * The contents of this file are subject to the Eclipse Public
 * License Version 1.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of
 * the License at http://www.eclipse.org/legal/epl-v10.html
 *
 * Software distributed under the License is distributed on an "AS
 * IS" basis, WITHOUT WARRANTY OF ANY KIND, either express or
 * implied. See the License for the specific language governing
 * rights and limitations under the License.
 *
 * Alternatively, the contents of this file may be used under the terms of
 * either of the GNU General Public License Version 2 or later (the "GPL"),
 * or the GNU Lesser General Public License Version 2.1 or later (the "LGPL"),
 * in which case the provisions of the GPL or the LGPL are applicable instead
 * of those above. If you wish to allow use of your version of this file only
 * under the terms of either the GPL or the LGPL, and not to allow others to
 * use your version of this file under the terms of the EPL, indicate your
 * decision by deleting the provisions above and replace them with the notice
 * and other provisions required by the GPL or the LGPL. If you do not delete
 * the provisions above, a recipient may use your version of this file under
 * the terms of any one of the EPL, the GPL or the LGPL.
 ***** END LICENSE BLOCK *****/
package org.jruby.race;

/**
 * Extended access set bitmap representation for detail mode
 */
public class AccessSetObjDetailBm extends AccessSetObjBm {
    
    private MemoryOprInfo[] debugInfo;       // Debug information
    
    public AccessSetObjDetailBm(int offset, int flag, MemoryOprInfo info) {
        int index = offset / INT_BIT_NUM * 2;
        values = new int[2];
        debugInfo = new MemoryOprInfo[values.length * INT_BIT_NUM]; //
        begin = index;
        insert(offset, flag, info);
        this.accessInfo = info;
    }

    public AccessSetObjDetailBm(int offset, int flag, int len, MemoryOprInfo info) {
        int index = offset / INT_BIT_NUM * 2;
        values = new int[len * 2 + 2];
        debugInfo = new MemoryOprInfo[values.length * INT_BIT_NUM]; //
        begin = index;
        insert(offset, flag, len, info);
        this.accessInfo = info;
    }
    
    public MemoryOprInfo getMemOprInfo(int offset, int flag){
        int index = offset / INT_BIT_NUM * 2;
        // expand the values array
        if (index < begin || index >= begin + values.length)
            return null;
        if(flag == AccessSet.RD_FLAG){
            return debugInfo[(offset - begin / 2 * INT_BIT_NUM) * 2];
        } else{
            return debugInfo[(offset - begin / 2 * INT_BIT_NUM) * 2 + 1];
        }
    }
    
    private void expand(int index) {
        int[] newValues = null;
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
            System.arraycopy(values, 0, newValues, extendedLength, values.length);
            // Update head pointer
            begin -= extendedLength;
            values = newValues;
            // expand the debugInfor
            MemoryOprInfo[] newDebugInfo = new MemoryOprInfo[values.length
                    * INT_BIT_NUM];
            System.arraycopy(debugInfo, 0, newDebugInfo, extendedLength
                    * INT_BIT_NUM, debugInfo.length);
            debugInfo = newDebugInfo;
        } else if (index >= begin + values.length) {
            if (index >= begin + values.length * 2) {
                newValues = new int[index + 2 - begin];
                System.arraycopy(values, 0, newValues, 0, values.length);
                values = newValues;
                // expand the debugInfor
                MemoryOprInfo[] newDebugInfo = new MemoryOprInfo[values.length
                        * INT_BIT_NUM];
                System.arraycopy(debugInfo, 0, newDebugInfo, 0,
                        debugInfo.length);
                debugInfo = newDebugInfo;
            } else {
                newValues = new int[values.length * 2];
                System.arraycopy(values, 0, newValues, 0, values.length);
                values = newValues;
                // expand the debugInfor
                MemoryOprInfo[] newDebugInfo = new MemoryOprInfo[values.length
                        * INT_BIT_NUM];
                System.arraycopy(debugInfo, 0, newDebugInfo, 0,
                        debugInfo.length);
                debugInfo = newDebugInfo;
            }
        }
    }
    
    public int insert(int offset, int flag, MemoryOprInfo info) {
        int increased = 0;
        int index = offset / INT_BIT_NUM * 2;
        // expand the values array
        if (index < begin || index >= begin + values.length) {
            increased = values.length;
            expand(index);
            increased = values.length - increased;
        }
        // set the bit
        if (flag == AccessSet.RD_FLAG) {
            values[index - begin] |= (1 << (offset % INT_BIT_NUM));
            debugInfo[(offset - begin / 2 * INT_BIT_NUM) * 2] = info;
        } else {
            values[index + 1 - begin] |= (1 << (offset % INT_BIT_NUM));
            debugInfo[(offset - begin / 2 * INT_BIT_NUM) * 2 + 1] = info;
        }
        return increased;
    }

    public int insert(int offset, int flag, int len, MemoryOprInfo info) {
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
        // set the bit
        for (int i = 0; i < len; i++) {
            if (flag == AccessSet.RD_FLAG) {
                values[index - begin] |= (1 << ((offset + i) % INT_BIT_NUM));
                debugInfo[(offset + i - begin / 2 * INT_BIT_NUM) * 2] = info.clone();
            } else {
                values[index + 1 - begin] |= (1 << ((offset + i) % INT_BIT_NUM));
                debugInfo[(offset + i - begin / 2 * INT_BIT_NUM) * 2 + 1] = info.clone();
            }
        }
        return increased;
    }
    
    @Override
    public int insert(int offset, int flag) {
        System.out.println("Detailed obj info, wrong method called! ");
        return 0;
    }

    @Override
    public int insert(int offset, int flag, int len) {
        System.out.println("Detailed obj info, wrong method called! ");
        return 0;
    }
    
    @Override
    public int merge(AccessSetObj entry) {
        // mergeNum[ThreadManager.getCurrThId() * 16]++;
        int orgSize = values.length;
        AccessSetObjDetailBm bm = (AccessSetObjDetailBm) entry;
        // expand
        if (this.begin > bm.begin)
            expand(bm.begin);
        // expand
        if (this.begin + values.length < bm.begin + bm.values.length)
            expand(bm.begin + bm.values.length);
        // merge
        for (int i = bm.begin; i < bm.begin + bm.values.length; i++) {
            values[i - begin] |= bm.values[i - bm.begin];
            int myIndex = (i - begin) * INT_BIT_NUM;
            for (int j = 0; j < INT_BIT_NUM; j++) {
                if (debugInfo[myIndex + j] == null) {
                    MemoryOprInfo temp = bm.debugInfo[(i - bm.begin)
                            * INT_BIT_NUM + j];
                    debugInfo[myIndex + j] = temp;
                }
            }
        }
        return values.length - orgSize;
    }
    
    @Override
    public void intersect(AccessSetObj entry, long obj) {
        int start = 0;
        int end = 0;
        AccessSetObjDetailBm bm = (AccessSetObjDetailBm) entry;
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
        for (int i = start; i < end; i += 2) {
            // read-write
            offset = i / 2 * INT_BIT_NUM;
            result = (values[index1]) & (bm.values[index2 + 1]);
            while (result != 0) {
                int trailingZero = Integer.numberOfTrailingZeros(result);
                RaceDetector.recordRace(obj, offset + trailingZero, debugInfo[(index1 / 2 * INT_BIT_NUM + trailingZero)  * 2],
                        bm.debugInfo[(index2 / 2 * INT_BIT_NUM + trailingZero)  * 2 + 1]);
                // Note: >>> 32 may cause problems...
                result = result >>> (Integer.numberOfTrailingZeros(result));
                result = result >>> 1;
                // System.out.println("while rw");
            }
            // write-read
            //offset = i * INT_BIT_NUM;
            result = ((values[index1 + 1]) & (bm.values[index2]));
            while (result != 0) {
                int trailingZero = Integer.numberOfTrailingZeros(result);
                RaceDetector.recordRace(obj, offset + trailingZero, debugInfo[(index1 / 2 * INT_BIT_NUM + trailingZero)  * 2 + 1],
                        bm.debugInfo[(index2 / 2 * INT_BIT_NUM + trailingZero)  * 2]);
                // Note: >>> 32 may cause problems...
                result = result >>> (Integer.numberOfTrailingZeros(result));
                result = result >>> 1;
            }
            // write-write
            result = ((values[index1 + 1]) & (bm.values[index2 + 1]));
            while (result != 0) {
                int trailingZero = Integer.numberOfTrailingZeros(result);
                RaceDetector.recordRace(obj, offset + trailingZero, debugInfo[(index1 / 2 * INT_BIT_NUM + trailingZero)  * 2 + 1],
                        bm.debugInfo[(index2 / 2 * INT_BIT_NUM + trailingZero)  * 2 + 1]);
                // Note: >>> 32 may cause problems...
                result = result >>> (Integer.numberOfTrailingZeros(result));
                result = result >>> 1;
            }
            index1 += 2;
            index2 += 2;
        }
    }
}
