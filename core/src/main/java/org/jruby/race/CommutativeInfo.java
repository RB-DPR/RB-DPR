/**********************************************************************

  CommutativeInfo.java -

  $Author: Weixing Ji 
  created at: May  2013

  Copyright (C) 2013  

**********************************************************************/

package org.jruby.race;

import org.jruby.internal.runtime.methods.DynamicMethod;

// recording the information of data races
public class CommutativeInfo {
    protected DynamicMethod left;
    protected DynamicMethod right;

    public CommutativeInfo() {
        this.left = null;
        this.right = null;
    }

    public CommutativeInfo(DynamicMethod left, DynamicMethod right) {
        this.left = left;
        this.right = right;
    }

    public DynamicMethod getLeft() {
        return this.left;
    }

    public DynamicMethod getRight() {
        return this.right;
    }

    public void setLeft(DynamicMethod left) {
        this.left = left;
    }

    public void setRight(DynamicMethod right) {
        this.right = right;
    }

    public boolean isEqual(CommutativeInfo info) {
        return (this.left == info.getLeft() && this.right == info.getRight());
    }
}