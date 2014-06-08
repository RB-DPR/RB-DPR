

/**********************************************************************
 *
 *   race.java -
 *
 *     $Author: Weixing Ji
 *       created at: Oct  2012
 *
 *         Copyright (C) 2012
 *
 **********************************************************************/

package org.jruby.race;

//each ruby thread has such a sub-object
public class RaceEvent{
        public int eventId;
        public long objId;
	    public int offset;
        public int thrdId;
        public int line;
        public int file;
        public Object info;
	    public int depth;
        //the task the thread is executing
        public RaceTask task;
}

