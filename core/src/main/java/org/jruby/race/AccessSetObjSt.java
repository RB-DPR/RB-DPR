/**********************************************************************

  SpaceTree.java -

  $Author: Weixing Ji 
  created at: Dec  2012

  Copyright (C) 2012  

**********************************************************************/
package org.jruby.race;

import java.util.ArrayList;
import java.util.Stack;
/**
 * Space tree representation of access-set
 * @author Weixing Ji
 *
 */
class SpaceTree{
	public int low;
	public int high;
	public int depth;
	public Boolean continuous;
	public SpaceTree left;
	public SpaceTree right;
 
	SpaceTree(){
	}
	SpaceTree(int iL, int iR, int d, SpaceTree lL, SpaceTree lR){
		low = iL;
		high = iR;
		depth = d;
		left = lL;
		right = lR;
		continuous = false;
	}
	SpaceTree(int iL, int iR, int d, SpaceTree lL, SpaceTree lR, Boolean bC){
		low = iL;
		high = iR;
		depth = d;
		left = lL;
		right = lR;
		continuous = bC;
	}

	public void print() {
		// TODO Auto-generated method stub
		System.out.print("depth=" + depth);

		System.out.println("[" + low + "," + high + "]");
		if(left != null) {
			left.print();
		}
		if(right != null){
			right.print();
		}
	}
	public SpaceTree insert(int offset){
		// TODO Auto-generated method stub
		if(this.continuous){
			if(offset == low - 1){
				low--;
				return this;
			}
			else if(offset == high + 1){
				high++;
				return this;
			}
			else if(offset >= low && offset <= high){
				return this;
			}
			else if(offset > high){
				SpaceTree newRight = new SpaceTree(offset, offset, depth + 1, null, null, true);
				SpaceTree newParent = new SpaceTree(low, offset, depth, this, newRight, false);
				this.depth++;
				return newParent;
			}
			else if(offset < low){
				SpaceTree newLeft = new SpaceTree(offset, offset, depth + 1, null, null, true);
				SpaceTree newParent = new SpaceTree(offset, high, depth, newLeft, this, false);
				this.depth++;
				return newParent;
			}
		}
		else{
			if(offset > high){//out of right
				high = offset;
				right.insert(offset);
			}
			else if(offset < low){//out of left
				low = offset;
				left.insert(offset);
			}
			else if(offset <= left.high){//in the left
				left.insert(offset);
			}
			else if(offset >= right.low){//in the right
				right.insert(offset);
			}
			else{//in between the two region
				if(offset - left.high < right.low - offset){
					left.insert(offset);
				}
				else
					right.insert(offset);
				
				if(left.continuous && right.continuous && left.high + 1 >= right.low){
					this.low = left.low < right.low ? left.low : right.low;
					this.high = left.high > right.high ? left.high : right.high;
					this.continuous = true;
					this.left = null;
					this.right = null;
				}
			}
		}
		
		return this;
	}
	
	public SpaceTree insert(int offset, int len){
		// TODO Auto-generated method stub
		if(this.continuous){
			if(offset + len == low - 1){
				low -= len;
				return this;
			}
			else if(offset == high + 1){
				high += len;
				return this;
			}
			else if(offset >= low && offset + len - 1 <= high){
				return this;
			}
			else if(offset > high){
				SpaceTree newRight = new SpaceTree(offset, offset + len -1, depth + 1, null, null, true);
				SpaceTree newParent = new SpaceTree(low, offset + len - 1, depth, this, newRight, false);
				this.depth++;
				return newParent;
			}
			else if(offset + len - 1 < low){
				SpaceTree newLeft = new SpaceTree(offset, offset + len - 1, depth + 1, null, null, true);
				SpaceTree newParent = new SpaceTree(offset, high, depth, newLeft, this, false);
				this.depth++;
				return newParent;
			}
			else{
				for(int i = 0; i < len; i++)
					insert(offset + i);
			}
		}
		else{
			for(int i = 0; i < len; i++)
				insert(offset + i);
		}
		
		return this;
	}

	public void intersect(SpaceTree rTree, long obj, Object staticInfo) {
		if(rTree == null) return;
		//quick paths to compare the two trees
		if(rTree.low > this.high || rTree.high < this.low)
			return;
		if(rTree.continuous && this.continuous){
			//find the overlapped regions
			int start = (rTree.low < this.low)?this.low:rTree.low;
			int end = (rTree.high < this.high)?rTree.high:this.high;
			//report races
			for(int i = start; i < end; i++){
				RaceDetector.recordRace(obj, staticInfo, i);
			}
			return;
		}
		//slow-path to compare the two trees
		Stack<SpaceTree> lstack = new Stack<SpaceTree>();
		Stack<SpaceTree> rstack = new Stack<SpaceTree>();
		lstack.push(this);
		rstack.push(rTree);
		SpaceTree currLeft = null;
		SpaceTree currRight = null;

		while(!(lstack.isEmpty()) && !(rstack.isEmpty())){
			currLeft = lstack.peek();
			//depth first 
			if(currLeft.continuous == false){
				lstack.pop();
				//push the right child first
				if(currLeft.right != null)
					lstack.push(currLeft.right);
				if(currLeft.left != null)
					lstack.push(currLeft.left);
				continue;
			}
			currRight = rstack.peek();
			//depth first 
			if(currRight.continuous == false){
				rstack.pop();
				//push the right child first
				if(currRight.right != null)
					rstack.push(currRight.right);
				if(currRight.left != null)
					rstack.push(currRight.left);
				continue;
			}
			//currLeft is to be the left of currRight
			if(currLeft.high < currRight.low){
				lstack.pop();
				continue;
			}
			//currLeft is to be the right of currRight
			if(currLeft.low > currRight.high){
				rstack.pop();
				continue;
			}
			//find the overlapped regions
			int start = (currRight.low < currLeft.low)?currLeft.low:currRight.low;
			int end = (currRight.high < currLeft.high)?currRight.high:currLeft.high;
			//report races
			for(int i = start; i < end; i++){
				RaceDetector.recordRace(obj, staticInfo, i);
			}
		}//end while
	}
	private void update(int depth){
		this.depth = depth;
		if(this.left != null)
			this.left.update(depth + 1);
		if(this.right != null)
			this.right.update(depth + 1);
		
		if(this.left.continuous && this.right.continuous && left.high + 1 >= right.low){
			this.low = left.low < right.low ? left.low : right.low;
			this.high = left.high > right.high ? left.high : right.high;
			this.continuous = true;
			this.left = null;
			this.right = null;
		}
	}
	public SpaceTree merge(SpaceTree rTree) {
		//fast paths to merge two trees
		if(rTree == null) return this;
		if(this.continuous && rTree.continuous && 
				(this.high + 1 >= rTree.low || rTree.high + 1 >=  this.low)){
			this.high = this.high > rTree.high ? this.high : rTree.high;
			this.low = this.low < rTree.low ? this.low : rTree.low;
			return this;
		}
		
		if(rTree.low > this.high){
			SpaceTree newParent = new SpaceTree(this.low, rTree.high, 0, this, rTree, false);
			newParent.update(0);
			return newParent;
		}
		else if(rTree.high < this.low){
			SpaceTree newParent = new SpaceTree(rTree.low, this.high, 0, rTree, this, false);
			newParent.update(0);
			return newParent;
		}
		
		//slow path to merge two trees
		Stack<SpaceTree> lstack = new Stack<SpaceTree>();
		Stack<SpaceTree> rstack = new Stack<SpaceTree>();
		ArrayList<SpaceTree> llist = new ArrayList<SpaceTree>();
		ArrayList<SpaceTree> rlist = new ArrayList<SpaceTree>();
		
		lstack.push(this);
		rstack.push(rTree);
		//store all the leaves of each tree in a list
		SpaceTree temp;
		while(!lstack.isEmpty()){
			temp = lstack.pop();
			if(temp.continuous){
				llist.add(temp);
			}
			else{
				if(temp.right != null)
					lstack.push(temp.right);
				if(temp.left != null)
					lstack.push(temp.left);
			}
		}
		
		while(!rstack.isEmpty()){
			temp = rstack.pop();
			if(temp.continuous){
				rlist.add(temp);
			}
			else{
				if(temp.right != null)
					rstack.push(temp.right);
				if(temp.left != null)
					rstack.push(temp.left);
			}
		}
		//merge range by range
		SpaceTree currLeft = null;
		SpaceTree currRight = null;
		int lindex = 0;
		for(int i = 0; i < rlist.size(); i++){
			currRight = rlist.get(i);
			if(lindex >= llist.size()){
				llist.add(currRight);
				lindex++;
			}
			else{
				currLeft = llist.get(lindex);
				if(currRight.high + 1 < currLeft.low){
					llist.add(lindex, currRight);
					lindex++;
				}
				else if(currRight.high + 1 == currLeft.low){
					currLeft.low = currRight.low;
				}
				else if(currRight.high <= currLeft.high){
					currLeft.low = currRight.low < currLeft.low ? currRight.low : currLeft.low;
				}
				else if(currLeft.high + 1 >= currRight.low){
					currLeft.high = currRight.high;
				}
				else{
					lindex++;
					i--;
				}
			}
		}
		
		//merge the overlapped ranges
		SpaceTree range1;
		SpaceTree range2;
		for(int i = 0; i < llist.size() - 1; i++){
			 range1 = llist.get(i);
			 range2 = llist.get(i + 1);
			 if(range1.high + 1 >= range2.low){
				 range1.high = range2.high;
				 llist.remove(i+1);
				 i--;
			 }
		}
		
		//generate the new tree according to llist
		while(llist.size() > 1){
			for(int i = 0; i < llist.size() - 1; i++){
				range1 = llist.get(i);
				range2 = llist.get(i + 1);
				SpaceTree parent = new SpaceTree(range1.low, range2.high, 0, range1, range2, false);
				llist.remove(i);
				llist.remove(i + 1);
				llist.add(i, parent);
			}
		}
		//return the final result
		llist.get(0).update(0);
		return llist.get(0);
	}
	public boolean contains(int offset){
		if(this.continuous){
			if(offset >= this.low && offset <= this.high)
				return true;
			else return false;
		}
		else{
			if(this.left != null){
				if(this.left.contains(offset)) return true;
			}
			if(this.right != null){
				if(this.right.contains(offset)) return true;
			}
		}
		return false;
	}
}

class AccessSetObjSt extends AccessSetObj{
	private SpaceTree rdTree;
	private SpaceTree wrtTree;
	
	public AccessSetObjSt(){
		rdTree = null;
		rdTree = null;
	}
	public SpaceTree getRdTree(){
		return rdTree;
	}
	public SpaceTree getWrtTree(){
		return wrtTree;
	}
	@Override
	public void print() {
		// TODO Auto-generated method stub
		System.out.println("read tree:");
		if(rdTree != null)
			rdTree.print();
		System.out.println("write tree:");
		if(wrtTree != null)
			wrtTree.print();
	}

	@Override
	public int insert(int offset, int flag) {
		if(flag == AccessSet.RD_FLAG){
			if(rdTree == null)
				rdTree = new SpaceTree(offset, offset, 0, null, null, true);
			else
				rdTree.insert(offset);
		}
		else{ 
			if(wrtTree == null)
				wrtTree = new SpaceTree(offset, offset, 0, null, null, true);
			else
				wrtTree.insert(offset);
		}
		return 0;
	}
	
	@Override
	public int insert(int offset, int flag, int len) {
		if(flag == AccessSet.RD_FLAG){
			if(rdTree == null)
				rdTree = new SpaceTree(offset, offset, 0, null, null, true);
			else
				rdTree.insert(offset + 1, len - 1);
		}
		else{ 
			if(wrtTree == null)
				wrtTree = new SpaceTree(offset, offset, 0, null, null, true);
			else
				wrtTree.insert(offset + 1, len - 1);
		}
		return 0;
	}

	@Override
	public void intersect(AccessSetObj entry, long obj) {
		// TODO Auto-generated method stub
		AccessSetObjSt set = (AccessSetObjSt)entry;
		//if(set == null) System.out.println("set is null!");
		if(this.rdTree != null)
			this.rdTree.intersect(set.getWrtTree(), obj, this.accessInfo);
		if(this.wrtTree != null){
			this.wrtTree.intersect(set.getRdTree(), obj, this.accessInfo);
			this.wrtTree.intersect(set.getWrtTree(), obj, this.accessInfo);
		}
	}

	@Override
	public int merge(AccessSetObj entry) {
		// TODO Auto-generated method stub
		AccessSetObjSt set = (AccessSetObjSt)entry;
		if(this.rdTree == null)
			this.rdTree = set.getRdTree();
		else
			this.rdTree = this.rdTree.merge(set.getRdTree());
		if(this.wrtTree == null)
			this.wrtTree = set.getWrtTree();
		else
			this.wrtTree = this.wrtTree.merge(set.getWrtTree());
		return 0;	
	}

	@Override
	public void and(AccessSetObj entry) {
		// TODO Auto-generated method stub
		System.out.println("SpaceTreeSet: program should not run to this point");
	}

	@Override
	public void clear() {
		// TODO Auto-generated method stub
		this.rdTree = null;
		this.wrtTree = null;
	}

	@Override
	public int size() {
		// TODO Auto-generated method stub
		return 0;
	}
	@Override
	public boolean isIn(int offset, int flag) {
		if(flag == AccessSet.RD_FLAG && rdTree != null){
			return rdTree.contains(offset);
		}
		else if(flag == AccessSet.WRT_FLAG && wrtTree != null){
			return wrtTree.contains(offset);
		}
		else
			return false;
	}
	
}

