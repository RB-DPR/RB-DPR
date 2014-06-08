/**********************************************************************

*/

package org.jruby.race;

import org.jruby.race.RaceTask;
import org.jruby.race.RaceEvent;
import org.jruby.runtime.DynamicScope;
import org.jruby.runtime.builtin.IRubyObject;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.Vector;
import java.util.ArrayList;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;

import org.jruby.RubyBasicObject;
import org.jruby.RubyClass;
import org.jruby.RubyObject;
import org.jruby.ast.Node;
import org.jruby.internal.runtime.methods.DynamicMethod;
import org.jruby.parlib.RubyReduction;
import org.jruby.parlib.Scheduler;
import org.jruby.parlib.WorkerThread;
import org.jruby.parser.StaticScope;

/**
 * the base class of race detector
 * 
 * @author
 */
abstract public class RaceDetector {
    // notification event
    public static final int RACE_EVENT_READ = 0;
    public static final int RACE_EVENT_WRITE = 1;
    public static final int RACE_EVENT_EXIT = 2;
    public static final int RACE_EVENT_META = 3;
    public static final int RACE_EVENT_TOTAL = 4;
    // access-set representation
    public static final int RACE_SET_HT = 0;
    public static final int RACE_SET_LIST = 1;
    public static final int RACE_BF1 = 0;
    public static final int RACE_BF2 = 1;
    public static final int RACE_BF_DEFAULT_SIZE = 2048;
    // race detection algorithms
    public static final int RACE_TYPE_SET = 0;
    public static final int RACE_TYPE_SPD3 = 1;
    public static final int RACE_TYPE_CILK = 2;
    public static final int RACE_TYPE_FAST = 3;

    // switch race detector on/off
    public static boolean turnOff = true;
    public static int setType = RACE_SET_HT;
    public static int bfSize = RACE_BF_DEFAULT_SIZE;
    public static int bfType = RACE_BF1;
    // switch static analyzer on/off
    public static boolean staticAnaOn = false;
    //
    public static boolean itrOn = true;
    public static int raceType = RACE_TYPE_SET;
    public static boolean spaceTreeOn = false;
    public static boolean outOfBand = false;
    public static boolean acopOn = true;
    public static boolean detailMode = false;

    protected static final int MAX_RACES = 64;
    protected static final int MAX_THREADS = 64;
    protected static boolean enabled = true;
    protected static boolean started = true;
    protected static int thrdNum = 1;
    protected static int dbgLevel = 0;
    protected static int raceIndex = 0;
    protected static Vector raceInfo;
    protected static Vector<CommutativeInfo> commInfo;
    protected static int[] raceActions;
    protected static RaceTaskTree taskTree;
    protected static ArrayList<RaceTask> thrdTasks;
    protected static int[] thrdInstrumentOn;
    protected static int[] thrdInCommutativeCall;
    protected static LinkedList<IRubyObject>[] thrdCommScopes;
    protected static long[] thrdObjIds;
    protected static long OBJ_ID_BLOCK = 0x1000000l;
    protected static long scopeIds = 0x1000000000000l;
    protected static Object syncObj = new Object();
    protected static ConcurrentHashMap globVarIds;
    protected static long golbVarId = scopeIds - OBJ_ID_BLOCK + 1;
    static RaceDetector myRD = null;
    // For detail mode
    // Current file and line, these two arrays are update whenever a new line code is executed
    protected static MemoryOprInfo[] thrdMemOprInfo;
    // Each thread has a hash<String, id>, which maps a file path to an integer
    protected static ArrayList<HashMap<String, Integer>> thrdPathHashList;

    public static void createRaceDetector() {
        if (raceType == RACE_TYPE_SET) {
            if (outOfBand)
                myRD = new RaceDetectorSetOOB();
            else
                myRD = new RaceDetectorSet();
        } else if (raceType == RACE_TYPE_SPD3) {
            myRD = new RaceDetectorSPD3();
        } else if (raceType == RACE_TYPE_CILK) {
            myRD = new RaceDetectorCilk();
        } else if (raceType == RACE_TYPE_FAST) {
        }
    }

    public static RaceTask getCurrTask() {
        return thrdTasks.get(Scheduler.getCurrThId());
    }

    public static void setInstrumentOn() {
        int thrdId = Scheduler.getCurrThId();
        if (thrdId < thrdNum) {
            thrdInstrumentOn[thrdId]--;
        }
    }

    private static int offCounter = 0;

    public static void setInstrumentOff() {
        int thrdId = Scheduler.getCurrThId();
        if (thrdId < thrdNum) {
            thrdInstrumentOn[thrdId]++;
            offCounter++;
        }
    }

    public static void setCommutativeIn(IRubyObject commScope) {
        // TODO: store commutative object info
        int thrdId = Scheduler.getCurrThId();
        if (thrdId < thrdNum) {
            if (thrdInCommutativeCall == null) {
            }
            thrdInCommutativeCall[thrdId]++;
            thrdCommScopes[thrdId].addFirst(commScope);
        }
    }

    // Switch commutative call status to out
    public static void setCommutativeOut() {
        // TODO: restore commutative object info
        int thrdId = Scheduler.getCurrThId();
        if (thrdId < thrdNum) {
            thrdInCommutativeCall[thrdId]--;
            thrdCommScopes[thrdId].removeFirst();
        }
    }
    
    // Update current file and line information under detail mode
    public static void setFileAndLine(String file, int line) {
        if (thrdMemOprInfo != null) {
            // Get the file id by string
            HashMap<String, Integer> pathHash = thrdPathHashList.get(Scheduler.getCurrThId());
            Integer fileId = pathHash.get(file);
            if (fileId == null) {
                fileId = (Scheduler.getCurrThId() << 16) + (pathHash.size() + 1);
                pathHash.put(file, fileId);
            }
            thrdMemOprInfo[Scheduler.getCurrThId()].file = fileId;
            // 
            thrdMemOprInfo[Scheduler.getCurrThId()].line = line + 1;
        }
    }

    public static void notifyRd(Object obj, Object info, int offset) {
        if (enabled && started) {
            RubyObject objRead = ((RubyObject) obj);
            if (objRead.isReadOnly()) {
                // System.out.println("skip!");
                return;
            }
            myRD.onRead(obj, info, offset);
        }
    }

    public static void notifyWrt(Object obj, Object info, int offset) {
        if (enabled && started) {
            RubyObject objRead = ((RubyObject) obj);
            if (objRead.isReadOnly()) {
                System.out.println("Write on readonly obj!");
            }
            myRD.onWrite(obj, info, offset);
        }
    }

    public static void notifyRd(Object obj, Object info, int offset, int len) {
        if (enabled && started) {
            RubyObject objRead = ((RubyObject) obj);
            if (objRead.isReadOnly()) {
                return;
            }
            myRD.onRead(obj, info, offset, len);
        }
    }

    public static void notifyWrt(Object obj, Object info, int offset, int len) {
        if (enabled && started) {
            RubyObject objRead = ((RubyObject) obj);
            if (objRead.isReadOnly()) {
                System.out.println("Write on readonly obj!");
            }
            myRD.onWrite(obj, info, offset, len);
        }
    }

    public static void notifyRdCheck(Object obj, Object info, int offset, int DynamicScopeDepth) {
        if (enabled && started) {
            myRD.onReadCheck(obj, info, offset, DynamicScopeDepth);
        }
    }

    public static void notifyWrtCheck(Object obj, Object info, int offset, int DynamicScopeDepth) {
        if (enabled && started) {
            myRD.onWriteCheck(obj, info, offset, DynamicScopeDepth);
        }
    }

    public static void notifyTaskFork(int ctid, int itrNum) {
        myRD.onTaskFork(ctid, itrNum);
    }

    public static void notifyTaskFork(int ptid, int ctid, int itrNum) {
        myRD.onTaskFork(ptid, ctid, itrNum);
    }

    public static void notifyTaskJoin(int taskId) {
        myRD.onTaskJoin(taskId);
    }

    public static void notifyTaskExec(int taskId, int depth) {
        myRD.onTaskExec(taskId, depth);
    }

    public static void notifyTaskReMap(int taskId) {
        myRD.onTaskReMap(taskId);
    }

    public static void notifyStartAll(int taskId, int type) {
        myRD.onStartAll(taskId, type);
    }

    public static void notifyTaskDone(int taskId) {
        myRD.onTaskDone(taskId);
    }

    public static void notifyEndAll(int finishId, int taskId) {
        myRD.onEndAll(finishId, taskId);
    }

    public static void notifyStartNewItr(long i) {
        myRD.onStartNewItr(i);
    }

    public static Object[] initMetaData(int size) {
        return myRD.onInitMetaData(size);
    }

    public static void notifyReductionPush(RubyReduction obj) {
        myRD.onReductionPush(obj);
    }

    public static void notifyReductionGet(RubyReduction obj) {
        myRD.onReductionGet(obj);
    }

    public static void notifyCall(IRubyObject obj, DynamicMethod method, IRubyObject para,
            IRubyObject rtn) {
        myRD.onCall(obj, method, para, rtn);
    }

    public static void notifyCall(IRubyObject obj, DynamicMethod method, IRubyObject[] args,
            IRubyObject rtn) {
        myRD.onCall(obj, method, args, rtn);
    }

    public static void notifyNextStage(long pipelineId, int stageNum) {
        myRD.onNextStage(pipelineId, stageNum);
    }

    public static RaceTask getRaceTask(int tid) {
        return myRD.getTask(tid);
    }

    abstract void onRead(Object obj, Object info, int offset);

    abstract void onWrite(Object obj, Object info, int offset);

    abstract void onRead(Object obj, Object info, int offset, int len);

    abstract void onWrite(Object obj, Object info, int offset, int len);

    abstract void onReadCheck(Object obj, Object info, int offset, int DynamicScopeDepth);

    abstract void onWriteCheck(Object obj, Object info, int offset, int DynamicScopeDepth);

    abstract void onCall(Object obj, DynamicMethod method, IRubyObject rtn);

    abstract void onCall(Object obj, DynamicMethod method, IRubyObject para, IRubyObject rtn);

    abstract void onCall(Object obj, DynamicMethod method, IRubyObject[] args, IRubyObject rtn);

    abstract void onReStart();

    abstract void onReductionPush(RubyReduction obj);

    abstract void onReductionGet(RubyReduction obj);

    abstract void onNextStage(long pipelineId, int stageNum);

    abstract Object[] onInitMetaData(int size);

    abstract void onTaskFork(int ctid, int itrNum);

    abstract void onTaskFork(int ptid, int ctid, int itrNum);

    abstract void onTaskJoin(int taskId);

    abstract void onTaskExec(int taskId, int depth);

    abstract void onTaskReMap(int taskId);

    abstract void onStartAll(int taskId, int type);

    abstract void onTaskDone(int taskId);

    abstract void onEndAll(int finishId, int taskId);

    abstract void onStartNewItr(long i);

    abstract RaceTask getTask(int taskId);

    // the interface between race detector and Ruby VM
    // todo: change to multiple inline functions
    public static void notifySlow(RaceEvent re) {
        if (!(enabled && started))
            return;
        switch (re.eventId) {
        case RACE_EVENT_EXIT:
            //System.out.println("exit here");
            if (enabled) {
                while (WorkerThread.getAndExecuteLogTask());
                printRaces();
                // What is this for? 
                // printActions();
            }
            break;
        }
        re.offset = 0;
        re.objId = 0;
        re.info = null;
        re.depth = 0;
    }

    public static void init() {
        scopeIds = scopeIds - OBJ_ID_BLOCK;
        raceActions = new int[RACE_EVENT_TOTAL];
        for (int i = 0; i < RACE_EVENT_TOTAL; i++) {
            raceActions[i] = 0;
        }
        String myvar = System.getenv("RACE");
        if (myvar != null) {
            if (myvar.compareTo("Y") == 0) {
                turnOff = false;
            }
        }
        myvar = System.getenv("RACE_TYPE");
        if (myvar != null) {
            if (myvar.compareTo("SET") == 0) {
                raceType = RACE_TYPE_SET;
            } else if (myvar.compareTo("SPD3") == 0) {
                raceType = RACE_TYPE_SPD3;
            } else if (myvar.compareTo("CILK") == 0) {
                raceType = RACE_TYPE_CILK;
            }
        }
        myvar = System.getenv("DETAIL_MODE");
        if (myvar != null) {
            if (myvar.compareTo("Y") == 0) {
                detailMode = true;
            }
        }
        myvar = System.getenv("RACE_SET");
        if (myvar != null) {
            if (myvar.compareTo("HT") == 0) {
                setType = RACE_SET_HT;
            } else if (myvar.compareTo("LIST") == 0) {
                setType = RACE_SET_LIST;
            }
        }
        myvar = System.getenv("RACE_BF");
        if (myvar != null) {
            if (myvar.compareTo("1") == 0) {
                bfType = RACE_BF1;
            } else if (myvar.compareTo("2") == 0) {
                bfType = RACE_BF2;
            }
        }
        myvar = System.getenv("RACE_ITR");
        if (myvar != null) {
            if (myvar.compareTo("N") == 0) {
                itrOn = false;
            }
        }
        myvar = System.getenv("RACE_ACOP");
        if (myvar != null) {
            if (myvar.compareTo("N") == 0) {
                acopOn = false;
            }
        }
        myvar = System.getenv("RACE_BF_SIZE");
        if (myvar != null) {
            bfSize = Integer.parseInt(myvar);
        } else
            bfSize = RACE_BF_DEFAULT_SIZE;

        myvar = System.getenv("SPACE_TREE");
        if (myvar != null) {
            if (myvar.compareTo("Y") == 0) {
                spaceTreeOn = true;
            }
        }
        myvar = System.getenv("RACE_OOB");
        if (myvar != null) {
            if (myvar.compareTo("Y") == 0) {
                outOfBand = true;
            } else
                outOfBand = false;
        }
        if (!turnOff) {
            System.out.print("[DPR] Race detector is enabled(");
            if (raceType == RACE_TYPE_SET) {
                if (setType == RACE_SET_HT)
                    System.out.print("Set:HashTable)");
                else {
                    System.out.print("Log:List + BloomFiliter(");
                    if (bfType == RACE_BF1)
                        System.out.print("1-hash");
                    else if (bfType == RACE_BF2)
                        System.out.print("2-hash");
                    System.out.print(", " + bfSize + "-bit))");
                }
            } else if (raceType == RACE_TYPE_SPD3) {
                System.out.print("SPD3)");
            } else if (raceType == RACE_TYPE_CILK) {
                System.out.print("CILK)");
            }
            System.out.println("");
            if (staticAnaOn)
                System.out.println("[DPR] Static analyzer is on!");
            if (detailMode)
                System.out.println("[DPR] Detail mode on");
            if (itrOn)
                System.out.println("[DPR] Race detector is working on iterations!");
            if (spaceTreeOn)
                System.out.println("[DPR] Space tree optimization for arrays is enabled!");
            if (outOfBand)
                System.out.println("[DPR] Out-of-band set computation is enabled!");
            if (acopOn) {
                System.out.println("[DPR] ACOps enabled");
            } else {
                System.out.println("[DPR] ACOps disabled");
            }
        } else {
            System.out.println("[DPR] Race detector disabled!");
        }
    }

    public static void enable() {
        thrdNum = MAX_THREADS;
        dbgLevel = 0;
        enabled = true;
        started = false;
        raceInfo = new Vector();
        commInfo = new Vector<CommutativeInfo>();
    }

    public static void config(int num, int level) {
        thrdNum = num;
        dbgLevel = level;
    }

    public static void start() {
        System.out.println("[DPR]race detector started with " + thrdNum + "-thread!");
        myRD.onReStart();
        started = true;
    }

    public static void stop() {
        started = false;
    }
    
    public static String getFileById(int id){
        int thrdId = id >>> 16;
        HashMap<String, Integer> pathHash = thrdPathHashList.get(thrdId);
        for(Entry<String, Integer> entry : pathHash.entrySet()) {
            String key = entry.getKey();
            int value = entry.getValue();
            if(value == id){
                return key;
            }
        }
        return "";
    }

    public static void printActions() {
        int i = 0;
        System.out.println("[DPR]----------------actions-----------------");
        for (i = 0; i < RACE_EVENT_TOTAL; i++) {
            System.out.println("[DPR]" + raceActions[i]);
        }
    }
    
    public static void printRaces() {
        if (RaceDetector.detailMode) {
            printRacesDetail();
        } else {
            printRacesNormal();
        }
    }

    protected static void printRacesNormal() {
        int i = 0;
        System.out.println("\n[DPR]:----------------------------------------------");
        System.out.println("[DPR]: " + raceIndex + " races found!");
        System.out.println("[DPR]: " + commInfo.size() + " commutative conflicts found!");
        System.out.println("[DPR]:------------races in program------------------");
        for (i = 0; i < raceIndex; i++) {
            RaceInfo info = (RaceInfo) raceInfo.get(i);
            System.out.print("[DPR][" + i + "]obj=" + info.id + ",offset=" + info.offset
                    + ", name=");
            if (info.staticInfo != null) {
                if (info.staticInfo instanceof String) {
                    System.out.print(info.staticInfo.toString());
                } else if (info.staticInfo instanceof RubyClass) {
                    System.out.print(((RubyClass) info.staticInfo).getVariableName(info.offset));
                } else if (info.staticInfo instanceof StaticScope) {
                    System.out.print(((StaticScope) info.staticInfo).getVariable(info.offset));
                } else {
                    // RubyReduction is not a subclass of RubyClass
                    System.out.print(info.staticInfo.toString());
                }
            }
            System.out.println("");
        }
        System.out.println("[DPR]:------commutative conflicts in program--------");
        for (i = 0; i < commInfo.size(); i++) {
            DynamicMethod left = commInfo.get(i).getLeft();
            DynamicMethod right = commInfo.get(i).getRight();
            System.out.println("[comm][" + i + "] " + left.getImplementationClass().getName() + "."
                    + left.getName() + " <-->" + right.getImplementationClass().getName() + "."
                    + right.getName());
        }
    }
    
    protected static void printRacesDetail(){
        int i = 0;
        System.out.println("\n[DPR]:----------------------------------------------");
        System.out.println("[DPR]: " + raceIndex + " races found!");
        System.out.println("[DPR]: " + commInfo.size() + " commutative conflicts found!");
        System.out.println("[DPR]:------------races in program------------------");
        for (i = 0; i < raceIndex; i++) {
            RaceDetailInfo race = (RaceDetailInfo) raceInfo.get(i);
            if (race == null)
                continue;
            int fileId1 = race.info1.file;
            int fileId2 = race.info2.file;
            String file1 = getFileById(fileId1);
            String file2 = getFileById(fileId2);
            if (file1 == null)
                file1 = "";
            if (!(file1.indexOf("/parLib/") >= 0 || file1.indexOf("/parLib-race/") >= 0)) {
                System.out.print("[DPR][" + i + "] obj=" + race.id + ",offset=" + race.offset
                        + ", name=");
                if (race.info1 != null) {
                    if (race.info1.info instanceof String)
                        System.out.print(race.info1.toString());
                    else if (race.info1.info instanceof RubyClass)
                        System.out
                                .print(((RubyClass) race.info1.info).getVariableName(race.offset));
                    else if (race.info1.info instanceof StaticScope) {
                        System.out.print(((StaticScope) race.info1.info).getVariable(race.offset));
                    }
                    System.out.println("\n\t location: file=" + file1 + ",line=" + race.info1.line);
                }
                if (race.info2 != null) {
                    System.out.println("\t location: file=" + file2 + ",line=" + race.info2.line);
                }
            }
        }
        System.out.println("[DPR]:------commutative conflicts in program--------");
        for (i = 0; i < commInfo.size(); i++) {
            DynamicMethod left = commInfo.get(i).getLeft();
            DynamicMethod right = commInfo.get(i).getRight();
            System.out.println("[comm][" + i + "] " + left.getImplementationClass().getName() + "."
                    + left.getName() + " <-->" + right.getImplementationClass().getName() + "."
                    + right.getName());
            MemoryOprInfo infoL = ((CommutativeDetailInfo) commInfo.get(i)).getInfoL();
            MemoryOprInfo infoR = ((CommutativeDetailInfo) commInfo.get(i)).getInfoR();
            int fileId1 = infoL.file;
            int fileId2 = infoR.file;
            String file1 = getFileById(fileId1);
            String file2 = getFileById(fileId2);
            if (infoL != null && infoR != null) {
                System.out.println("\t location: file=" + file1 + ",line=" + infoL.line);
                System.out.println("\t location: file=" + file2 + ",line=" + infoR.line);
            } else {
                System.out.println("\n\t info is null!");
            }
        }
    }

    /**
     * Record a race found
     * 
     * @param objId
     * @param staticInfo
     * @param offset
     */
    public synchronized static void recordRace(long objId, Object staticInfo, int offset) {
        boolean find = false;
        RaceInfo info = null;
        if (raceIndex > MAX_RACES)
            return;
        // remove the duplicates
        for (int i = 0; i < raceIndex; i++) {
            info = (RaceInfo) raceInfo.get(i);
            if (info.id == objId && info.offset == offset) {
                if (staticInfo == null) {
                    find = true;
                    break;
                } else if (info.staticInfo.equals(staticInfo)) {
                    find = true;
                    break;
                }
            }
        }
        if (!find) {
            info = new RaceInfo(offset, objId, staticInfo);
            raceInfo.add(info);
            raceIndex++;
        }
    }
    
    /**
     * Record a race found by detail mode
     * @param objId
     * @param offset
     * @param info1
     * @param info2
     */
    public synchronized static void recordRace(long objId, int offset, MemoryOprInfo info1, MemoryOprInfo info2) {
        boolean find = false;
        RaceInfo info = null;
        if(raceIndex > MAX_RACES) return;
        for(int i = 0; i < raceIndex; i++) {
            info = (RaceInfo) raceInfo.get(i);
            if(info.id == objId) {
                if(info1.info instanceof RubyClass && info.offset == offset) {
                    find = true;
                    break;
                } else if(info1.info instanceof StaticScope && info.offset == offset) {
                    find = true;
                    break;
                } else if(info1.info instanceof String) {
                    if(((String)info1.info) == "ARRAY")
                        find = true;
                    else if(info.offset == offset)
                        find = true;
                    break;
                } else if(info1.info == null && info.offset == offset){
                    find = true;
                    break;
                }
            }
        }
        if(!find) {
            if (info2 != null) {
                info = new RaceDetailInfo(offset, objId, info1.clone(),info2.clone());
            } else {
                info = new RaceDetailInfo(offset, objId, info1.clone(), null);
            }
            raceInfo.add(info);
            raceIndex++;
        }
    }

    /**
     * Record a commutative conflict
     * @param left
     * @param right
     */
    public static void commRecord(DynamicMethod left, DynamicMethod right) {
        for (int i = 0; i < commInfo.size(); i++) {
            if (commInfo.get(i).getLeft() == left && commInfo.get(i).getRight() == right
                    || commInfo.get(i).getLeft() == right && commInfo.get(i).getRight() == left) {
                return;
            }
        }
        commInfo.add(new CommutativeInfo(left, right));
    }
    
    /**
     * Record a commutative conflict for detail mode
     * @param left
     * @param right
     * @param infoL
     * @param infoR
     */
    public static void commRecord(DynamicMethod left, DynamicMethod right, MemoryOprInfo infoL,
            MemoryOprInfo infoR) {
        // remove duplicates
        for (int i = 0; i < commInfo.size(); i++) {
            if (commInfo.get(i).getLeft() == left && commInfo.get(i).getRight() == right
                    || commInfo.get(i).getLeft() == right && commInfo.get(i).getRight() == left) {
                return;
            }
        }
        // push a new record into the vector
        commInfo.add(new CommutativeDetailInfo(left, right, infoL, infoR));
    }

    public static long getNextScopeId() {
        int thrdId = Scheduler.getCurrThId();
        if (thrdObjIds == null)
            return 0;
        long id = thrdObjIds[thrdId];
        if (id % OBJ_ID_BLOCK != 0) {
            thrdObjIds[thrdId] = id + 1;
            return id;
        } else {
            synchronized (syncObj) {
                scopeIds -= OBJ_ID_BLOCK;
                // System.out.println("id distribution:" + scopeIds);
                thrdObjIds[thrdId] = scopeIds + 1;
            }
            id = thrdObjIds[thrdId];
            thrdObjIds[thrdId] = id + 1;
            return id;
        }
    }

    public static long getGloVarId(String name) {
        Long id = (Long) globVarIds.get(name);
        if (id == null) {
            synchronized (syncObj) {
                id = golbVarId;
                golbVarId++;
            }
            globVarIds.put(name, id);
        }
        return id;
    }

    public static void staticAnalyze(Node n) {
        String myvar = System.getenv("RACE_STATIC");
        if (myvar != null) {
            if (myvar.compareTo("Y") == 0)
                staticAnaOn = true;
            else
                staticAnaOn = false;
        }
        if (staticAnaOn) {
            RaceAnalyzer ana = new RaceAnalyzer();
            // ana.staticAnalyze(n);
            ana.Analyze(n);
        }
    }

    public static void setDepth(RubyBasicObject obj) {
        if (thrdTasks != null) {
            RaceTask task = thrdTasks.get(Scheduler.getCurrThId());
            obj.setDepth(task.depth);
        }
    }
}
