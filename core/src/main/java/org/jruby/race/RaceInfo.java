/**********************************************************************

  RaceInfo.java -

  $Author: Weixing Ji 
  created at: Oct  2012

  Copyright (C) 2012  

**********************************************************************/

package org.jruby.race;

// recording the information of data races
public class RaceInfo {
    public int offset; // offset of field
    public long id; // object id
    // public int line; //line number
    // public String file; //source file
    public Object staticInfo; //

    public RaceInfo(int offset, long id, Object staticInfo) {
        this.offset = offset;
        this.id = id;
        // this.line = line;
        // this.file = Integer.toString(file);
        this.staticInfo = staticInfo;
    }
}