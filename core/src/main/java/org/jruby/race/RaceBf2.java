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

import org.jruby.race.RaceBf;
import org.jruby.race.RaceDetector;


//bloom filter with 2 hash functions
public class RaceBf2 extends RaceBf{
	public RaceBf2(){}
	public RaceBf2(int bitNum){
		super(bitNum);
	}
	@Override
	public void setBit(long obj){
		bits.set((int)(obj % bitNum));
		//exchange the LSB-16-bit with MSB-48-bit
		bits.set((int)((((obj & 0xFFFF) << 48) + (obj >>> 16)) % bitNum));			
	}
	@Override
	public void setBit(long obj, long offset){
	    //shift obj id left, suppose 48-bit is enough for obj id
		bits.set((int)(((obj << 16) + offset) % bitNum));
		//exchange offset 
		bits.set((int)(((offset << 48) + obj) % bitNum));
	}
	@Override
	public boolean isSet(long obj){
		return (bits.get((int)(((obj & 0xFFFF) << 48) + (obj >>> 16)) % bitNum) &&
                         bits.get((int)(obj % bitNum)));
	}	
	@Override
	public boolean isSet(long obj, long offset){
		return (bits.get((int)(((obj << 16) + offset) % bitNum)) &&
			bits.get((int)(((offset << 48) + offset) % bitNum)));
	}
}

