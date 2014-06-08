package org.jruby.race;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**********************************************************************
 * MetaData.java -
 * 
 * $Author: Weixing Ji created at: Jan. 2013
 **********************************************************************/

public abstract class RaceMetaData {
public static final int MIN_SIZE = 16;

public static RaceMetaData createMetaData() {
    // create metadata according to the type of current race detector
    if (RaceDetector.raceType == RaceDetector.RACE_TYPE_SPD3) {
        return new RaceMetaDataSPD3();
    } else if (RaceDetector.raceType == RaceDetector.RACE_TYPE_CILK) {
        return new RaceMetaDataCilk();
    } else {
        System.out
                .println("RaceMetaData.createMetaData: unknow race detector!");
        System.exit(-1);
        return null;
    }
}

public static RaceMetaData createMetaData(int size) {
    // create metadata according to the type of current race detector
    if (RaceDetector.raceType == RaceDetector.RACE_TYPE_SPD3) {
        return new RaceMetaDataSPD3(size);
    } else if (RaceDetector.raceType == RaceDetector.RACE_TYPE_CILK) {
        return new RaceMetaDataCilk(size);
    } else {
        System.out
                .println("RaceMetaData.createMetaData: unknow race detector!");
        System.exit(-1);
        return null;
    }
}

abstract public Object[] getMetaData();

abstract public void setMetaData(Object[] data);

abstract public long getSeqNum();

abstract public void incSeqNum();

abstract public boolean casSeqNum(long oldValue, long newValue);

}

class RaceMetaDataSPD3 extends RaceMetaData {
// shadow memory for this objects
protected transient volatile Object[] varMetaData;
// AtomicLong is used to protect metadata
protected AtomicLong seqNumMd;

public static int shadowSize = 5;
public static final int METADATA_OFFSET_RD1 = 0;
public static final int METADATA_OFFSET_RD2 = 1;
public static final int METADATA_OFFSET_WRT = 2;
public static final int METADATA_OFFSET_STARTVER = 3;
public static final int METADATA_OFFSET_ENDVER = 4;

RaceMetaDataSPD3() {
    varMetaData = mallocMetaData(this.MIN_SIZE);
    seqNumMd = new AtomicLong(0);
}

RaceMetaDataSPD3(int size) {
    varMetaData = mallocMetaData(size);
    seqNumMd = new AtomicLong(0);
}

@Override
public Object[] getMetaData() {
    // TODO Auto-generated method stub
    return varMetaData;
}

@Override
public void setMetaData(Object[] data) {
    varMetaData = data;
}

@Override
public long getSeqNum() {
    return seqNumMd.get();
}

@Override
public void incSeqNum() {
    seqNumMd.incrementAndGet();
}

@Override
public boolean casSeqNum(long oldValue, long newValue) {
    return seqNumMd.compareAndSet(oldValue, newValue);
}

public static Object[] mallocMetaData(int length) {
    Object[] varMetaData = new Object[length * RaceMetaDataSPD3.shadowSize];
    for (int i = 0; i < length * RaceMetaDataSPD3.shadowSize; i += RaceMetaDataSPD3.shadowSize) {
        varMetaData[i + METADATA_OFFSET_STARTVER] = new AtomicInteger();
        varMetaData[i + METADATA_OFFSET_ENDVER] = new AtomicInteger();
    }

    return varMetaData;
}

public static Object[] reMallocMetaData(Object[] old, int length) {
    int index = 0;
    if (old != null)
        index = old.length;
    Object[] varMetaData = new Object[length * RaceMetaDataSPD3.shadowSize];
    for (int i = index; i < length * RaceMetaDataSPD3.shadowSize; i += RaceMetaDataSPD3.shadowSize) {
        varMetaData[i + METADATA_OFFSET_STARTVER] = new AtomicInteger();
        varMetaData[i + METADATA_OFFSET_ENDVER] = new AtomicInteger();
    }
    if (old != null)
        System.arraycopy(old, 0, varMetaData, 0, old.length);

    return varMetaData;
}
}

class RaceMetaDataCilk extends RaceMetaData {
// Shadow memory for cilk
protected transient volatile ProcNode[] cilkShadow;

RaceMetaDataCilk() {
    cilkShadow = new ProcNode[this.MIN_SIZE];
}

RaceMetaDataCilk(int size) {
    cilkShadow = new ProcNode[size];
}

@Override
public Object[] getMetaData() {
    // TODO Auto-generated method stub
    return cilkShadow;
}

@Override
public void setMetaData(Object[] data) {
    cilkShadow = (ProcNode[]) data;
}

@Override
public long getSeqNum() {
    System.out.print("It is imppossible to run to this point");
    System.exit(-1);
    return 0;
}

@Override
public void incSeqNum() {
    System.out.print("It is imppossible to run to this point");
    System.exit(-1);
}

@Override
public boolean casSeqNum(long oldValue, long newValue) {
    System.out.print("It is imppossible to run to this point");
    System.exit(-1);
    return false;
}
}
