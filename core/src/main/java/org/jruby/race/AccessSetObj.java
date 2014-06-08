/**********************************************************************

  AccessSetObj.java -

  $Author: Weixing Ji 
  created at: Oct  2012

  Copyright (C) 2012  

**********************************************************************/
package org.jruby.race;

// log entry for each address(object id, offset)
public abstract class AccessSetObj {
    // help to find the name of the variable
    // static scope or metaclass
    public Object accessInfo;
    public long accessNum;

    public AccessSetObj() {
        accessInfo = null;
        accessNum = 0;
    }

    abstract public void print();

    abstract public int insert(int offset, int flag);

    abstract public int insert(int offset, int flag, int len);

    abstract public void intersect(AccessSetObj entry, long obj);

    abstract public int merge(AccessSetObj entry);

    abstract public void and(AccessSetObj entry);

    abstract public void clear();

    abstract public int size();

    abstract public boolean isIn(int offset, int flag);
}
