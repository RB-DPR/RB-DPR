package org.jruby.race;

// This class is used in detail mode
public class MemoryOprInfo {
	public MemoryOprInfo(){
		this.file = 0;
		this.line = 0;
		this.info = null;
	}
	
	public MemoryOprInfo(int file, int line, Object info){
		this.file = file;
		this.line = line;
		this.info = info;
	}
	public int file;
	public int line;
	public Object info;
	
	public MemoryOprInfo clone(){
		return new MemoryOprInfo(file, line, info);
	}
	
	public void print(){
		System.out.print("file=" + file +",line=" + line + ",");
		if(info != null){
			System.out.println(info.toString());
		}
		else{
			System.out.println();
		}
	}
}
