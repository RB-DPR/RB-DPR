package org.jruby.race;

import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;

import org.jruby.parlib.Scheduler;

class RaceDetectorSetOOB extends RaceDetectorSet{

	@Override
	void onReStart() {
		thrdTasks = new ArrayList<RaceTask>(thrdNum);
		thrdObjIds = new long[thrdNum];
		globVarIds = new ConcurrentHashMap();
		for(int i = 0; i < thrdNum; i++){
		    thrdTasks.add(null);
		    thrdObjIds[i] = 0;
		}
		taskTree = new RaceTaskTreeOOB(thrdTasks);
		taskTree.taskMap(Scheduler.getCurrThId(), 1);
	}
}