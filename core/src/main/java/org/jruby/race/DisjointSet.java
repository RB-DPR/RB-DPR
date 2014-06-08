package org.jruby.race;

import org.jruby.race.ProcNode;

// Top level node to describe the type of a bag
class GuardNode extends DisjointSetNode {

    protected int type;
    protected ProcNode child;
    
    public GuardNode(int type) {
        this.type = type;
        this.parent = null;
        this.child = null;
    }
    
    public void setChild(ProcNode child) {
        this.child = child;
    }
    
    public ProcNode getChild() {
        return this.child;
    }
    
    public int getType() {
        return this.type;
    }
    
    public boolean isSBag() {
        return (this.type == S_BAG_TYPE);
    }
    
    public boolean isPBag() {
        return (this.type == P_BAG_TYPE);
    }
    
    public void toSBag() {
        this.type = S_BAG_TYPE;
    }
    
    public void toPBag() {
        this.type = P_BAG_TYPE;
    }
}

public class DisjointSet {
    
    // Keep the node head for union
    protected GuardNode nodeHead;
    
    // Make set
    public DisjointSet(ProcNode n, int type) {
        // Set up a guard node
        GuardNode bagGuard = new GuardNode(type);
        // Connect two nodes
        bagGuard.setChild(n);
        n.setParent(bagGuard);
        // Set rank for the new proc node
        n.setRank(0);
        // Set the set's begin node to the guard node
        this.nodeHead = bagGuard;
    }
    public DisjointSet(int type) {
        // Set up a guard node
        GuardNode bagGuard = new GuardNode(type);
        // Set the set's begin node to the guard node
        this.nodeHead = bagGuard;
    }
    
    // Union with another set
    // After the union operation, all elements will be in the current set
    public void unionWith(DisjointSet s) {
        //System.out.println("Union!");
        // Get the head (proc) node for both sets
        ProcNode headProcSelf = this.nodeHead.getChild();
        ProcNode headProcS = s.nodeHead.getChild();
        if (headProcS == null) {
            // If the incoming set is empty, simply return
            return;
        } else if (headProcSelf == null) {
            // If my own set is empty, and the incoming set is not empty, copy
            // the incoming set
            nodeHead.setChild(headProcS);
            headProcS.setParent(nodeHead);
        } else {
            // Add one set under the tree of another
            if (headProcSelf.getRank() > headProcS.getRank()) {
                headProcS.setParent(headProcSelf);
            } else {
                headProcSelf.setParent(headProcS);
                // Change S's rank if needed
                if (headProcSelf.getRank() == headProcS.getRank()) {
                    headProcS.setRank(headProcS.getRank() + 1);
                }
                // Switch guard pointer since we've changed the first proc node
                nodeHead.setChild(headProcS);
                headProcS.setParent(nodeHead);
            }
        }
    }
    
    // Static method for a ProcNode to find its guard node
    public static GuardNode findBag(ProcNode f) {
        ProcNode topMostProc = compressPathAndReturnTop(f);
        return (GuardNode)topMostProc.getParent();
    }
    
    // Make sure a bag is a s-bag
    public void toSBag() {
        this.nodeHead.toSBag();
    }
    
    // Make sure a bag is a p-bag
    public void toPBag() {
        this.nodeHead.toPBag();
    }
    
    // Compress path for a set, then return its topmost proc node
    private static ProcNode compressPathAndReturnTop(DisjointSetNode f) {
        // If a proc node is the top level proc node, its parent shoud be a 
        // guard node, whose parent should be null
        if (f.parent.parent != null) {
            ((ProcNode)f).setParent(compressPathAndReturnTop(f.parent));
        } else {
            return ((ProcNode) f);
        }
        return (ProcNode)f.getParent();
    }
}
