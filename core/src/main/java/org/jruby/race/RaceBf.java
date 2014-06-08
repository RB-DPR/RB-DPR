/**********************************************************************

  log.java -

  $Author: Weixing Ji 
  created at: Oct  2012

  Copyright (C) 2012  

**********************************************************************/
package org.jruby.race;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Set;
import java.util.Vector;
import java.util.BitSet;
import java.util.Arrays;
import org.jruby.parser.StaticScope;

//bloom filter with one hash function

public class RaceBf{
	protected final int DEFAULT_BIT_NUM = 2048;
	protected int bitNum = DEFAULT_BIT_NUM;
	protected BitSet bits;

	public RaceBf()	{
		bits = new BitSet(bitNum);
	}
	
	public RaceBf(int bitNum)	{
	    this.bitNum = bitNum;
		bits = new BitSet(bitNum);
	}
	public BitSet getBits(){
		return bits;
	}
	public void setBit(long obj)	{
		bits.set((int)(obj % bitNum));
	}
	//suppose 48-bit is enough for obj id
	public void setBit(long obj, long offset){
		bits.set((int)(((obj << 16) + offset) % bitNum));
	}
	public boolean isSet(long obj){
		return bits.get((int)(obj % bitNum));
	}
	public boolean isSet(long obj, long offset){
		return bits.get((int)(((obj << 16) + offset) % bitNum));
	}
	public boolean intersect(RaceBf bf){
		BitSet bits2 = (BitSet)(bits.clone());
		bits2.and(bf.getBits());
		return (!bits2.isEmpty());
	}
	public void and(RaceBf bf){
		bits.and(bf.getBits());
	}
	public void or(RaceBf bf){
		bits.or(bf.getBits());
	}
	public String toString(){
	    return bits.toString();
	}
	public void clear(){
		bits.clear();
	}
}

