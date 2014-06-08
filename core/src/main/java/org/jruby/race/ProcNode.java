package org.jruby.race;

//Base class for disjoint set node
abstract class DisjointSetNode {

public static final int P_BAG_TYPE = 0;
public static final int S_BAG_TYPE = 1;
 protected DisjointSetNode parent;
 
 public DisjointSetNode() {
     this.parent = null;
 }
 
 public DisjointSetNode getParent() {
     return this.parent;
 }
 
 // Set it to private for better access control
 private void setParent(DisjointSetNode parent) {
     this.parent = parent;
 }
 
}

//Normal nodes in a disjoint set. Each proc owns a node. 
public class ProcNode extends DisjointSetNode {
 protected int fid;
 protected int rank;
 
 public ProcNode(int fid) {
     this.parent = null;
     this.fid = fid;
     this.rank = 0;
 }
 
 public int getRank() {
     return this.rank;
 }
 
 public void setRank(int rank) {
     this.rank = rank;
 }
 
 public void setParent(DisjointSetNode parent) {
     this.parent = parent;
 }
}
