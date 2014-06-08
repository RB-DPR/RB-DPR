/**********************************************************************

  RaceAnalyzer.java -

  $Author: Weixing Ji 
  created at: Oct  2012

  Copyright (C) 2012  

**********************************************************************/

package org.jruby.race;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.Stack;
import java.util.TreeSet;

import org.jruby.ast.*;
import org.jruby.ast.visitor.NodeVisitor;
import org.jruby.parser.BlockStaticScope;
import org.jruby.parser.StaticScope;

class VarInfo{
	
	private Object		obj;			//static scope or object 
	private int			offset;			//which field?
	private Node		node;			//node in the tree
	
	public VarInfo(Object obj, int offset, /*String name, Boolean readonly, Boolean isScope,*/ Node node){
		this.obj = obj;
		this.offset = offset;
		this.node = node;
	}
	public Object getObj() {
		return obj;
	}
	
	public void setObj(Object obj) {
		this.obj = obj;
	}
	
	public int getOffset() {
		return offset;
	}
	
	public void setOffset(int offset) {
		this.offset = offset;
	}
	
	public Node getNode() {
		return node;
	}
	
	public void setNode(Node node) {
		this.node = node;
	}
}

public class RaceAnalyzer{

	private int parallelCnt = 1;

	// Traverse the AST tree and find the races on local variables
	public void staticAnalyze(Node n) {
		TreeSet<Integer> rdVarSet = new TreeSet<Integer>();
		TreeSet<Integer> wrtVarSet = new TreeSet<Integer>();
		ArrayList<Node> nodeStack = new ArrayList<Node>();
		//printNode(n, 0);
		nodeStack.add(n);
		staticAnalyze2(n, rdVarSet, wrtVarSet, nodeStack);
		nodeStack.remove(0);
	}
	private boolean branchCheck(ArrayList<Node> nodeStack, int depth){
		int i = depth;
		int index = nodeStack.size() - 1;
		boolean branch = false;
		//track back to the declaring node
		while(i >= 0 && index >= 0){
			Node n = nodeStack.get(index);
			NodeType type = n.getNodeType();
			
			//System.out.println(n.toString());
			//this variable is enclosed in a branch statement
			if(type == NodeType.IFNODE ||
			   type == NodeType.FORNODE ||
			   type == NodeType.CASENODE) 
				branch = true;
			//in dotall
			if(type == NodeType.CALLNODE){
    			if(((CallNode)n).getName() == "all"){
    				if(branch) return true;
    			}
			}
			//in cobegin
			if(type == NodeType.FCALLNODE){
    			if(((FCallNode)n).getName() == "co"){
    				if(branch) return true;
    			}
			}
			//trackdepth
			if(type == NodeType.CLASSNODE ||
				       type == NodeType.DEFNNODE ||
				       type == NodeType.DEFSNODE ||
				       type == NodeType.FORNODE ||
				       type == NodeType.ITERNODE ||
				       type == NodeType.LAMBDANODE || 
				       type == NodeType.MODULENODE ||
				       type == NodeType.PREEXENODE ||
				       type == NodeType.SCLASSNODE){
				i--;
			}
			index--;
			
		}
		return false;
	}
	private void staticAnalyze2(Node n, TreeSet<Integer> rdVars, 
		TreeSet<Integer> wrtVars, ArrayList<Node> nodeStack){
		
		ArrayList<TreeSet<Integer>> rdVarArr = new ArrayList<TreeSet<Integer>>();
		ArrayList<TreeSet<Integer>> wrtVarArr = new ArrayList<TreeSet<Integer>>();
		
		Node parent = (Node)nodeStack.get(nodeStack.size() - 1);
		//get all the variables accessed in child node
		//push current node into the nodeStack
		nodeStack.add(n);
		List<Node> children = n.childNodes();
	    for(Node child : children) {
	    	TreeSet<Integer> rdVarTemp = new TreeSet<Integer>();
			TreeSet<Integer> wrtVarTemp = new TreeSet<Integer>();
	    	staticAnalyze2(child, rdVarTemp, wrtVarTemp, nodeStack);
	    	rdVarArr.add(rdVarTemp);
	    	wrtVarArr.add(wrtVarTemp);
	    	//System.out.println(rdVarTemp);
	    	//System.out.println(wrtVarTemp);
	    }
	    //pop current node from the nodeStack
	    nodeStack.remove(nodeStack.size() - 1);
	    
	    NodeType type = n.getNodeType();
	    
	    if(type == NodeType.LOCALASGNNODE){
	    	LocalAsgnNode local = (LocalAsgnNode)n;
	    	if(branchCheck(nodeStack, local.getDepth())){
	    		local.setDynTrack(true);
	    		System.out.println("[RACE]Static Analyzer: variable " + local.getName() + " is dynamically tracked!");
	    	}
	    	else
	    		wrtVars.add((local.getDepth() << 16)+ local.getIndex());
	    }
	    else if(type == NodeType.LOCALVARNODE){
	    	LocalVarNode local = (LocalVarNode)n;
	    	if(branchCheck(nodeStack, local.getDepth())){
	    		local.setDynTrack(true);
	    		System.out.println("[RACE]Static Analyzer: variable " + local.getName() + " is dynamically tracked!");
	    	}
	    	else //if it is not dynamically checked
	    		rdVars.add((local.getDepth() << 16)+ local.getIndex());
	    }
	    if(type == NodeType.DASGNNODE){
	    	DAsgnNode local = (DAsgnNode)n;
	    	if(branchCheck(nodeStack, local.getDepth())){
	    		local.setDynTrack(true);
	    		System.out.println("[RACE]Static Analyzer: variable " + local.getName() + " is dynamically tracked!");
	    	}
	    	else wrtVars.add((local.getDepth() << 16)+ local.getIndex());
	    }
	    else if(type == NodeType.DVARNODE){
	    	DVarNode local = (DVarNode)n;
	    	if(branchCheck(nodeStack, local.getDepth())){
	    		local.setDynTrack(true);
	    		System.out.println("[RACE]Static Analyzer: variable " + local.getName() + " is dynamically tracked!");
	    	}
	    	else
	    		rdVars.add((local.getDepth() << 16)+ local.getIndex());
	    }
	    
	    //remove all the variables in current depth
	    if(type == NodeType.CLASSNODE ||
	       type == NodeType.DEFNNODE ||
	       type == NodeType.DEFSNODE ||
	       type == NodeType.FORNODE ||
	       type == NodeType.ITERNODE ||
	       type == NodeType.LAMBDANODE || 
	       type == NodeType.MODULENODE ||
	       type == NodeType.PREEXENODE ||
	       type == NodeType.SCLASSNODE){
	    	for(int i = 0; i < rdVarArr.size(); i++){
	    		TreeSet<Integer> rdVarTemp = rdVarArr.get(i);
				TreeSet<Integer> wrtVarTemp = wrtVarArr.get(i);
				Iterator it = rdVarTemp.iterator();
                while (it.hasNext()) {
                	int var = (Integer)it.next();
					if(((var >> 16) & 0xFFFF) > 0){
						int var2 = (((var >> 16) - 1) << 16) + (var & 0xFFFF);
						rdVars.add(var2);
					}
                } 
                it = wrtVarTemp.iterator();
                while (it.hasNext()) {
                	int var = (Integer)it.next();
                	//System.out.println(var);
					if(((var >> 16) & 0xFFFF) > 0){
						int var2 = (((var >> 16) - 1) << 16) + (var & 0xFFFF);
						wrtVars.add(var2);
						//System.out.print(n.getNodeType().toString() + var2);
					}
                } 
	    	}
	    }
	    else{
	    	for(int i = 0; i < rdVarArr.size(); i++){
	    		TreeSet<Integer> rdVarTemp = rdVarArr.get(i);
				TreeSet<Integer> wrtVarTemp = wrtVarArr.get(i);
				Iterator it = rdVarTemp.iterator();
                while (it.hasNext()) {
                	int var = (Integer)it.next();
					rdVars.add(var);
				} 
                it = wrtVarTemp.iterator();
                while (it.hasNext()) {
                	int var = (Integer)it.next();
					wrtVars.add(var);
                } 
	    	}
	    }
	    
	    //intersect variable lists for some special nodes
	    //check the existence of .all call
	    if(type == NodeType.ITERNODE){
	    	if(nodeStack.size() > 1){
	        	Node depth1Node = (Node)nodeStack.get(nodeStack.size() - 1);
	        	if(depth1Node.getNodeType() == NodeType.CALLNODE){
	    			if(((CallNode)depth1Node).getName() == "all"){
	    				//System.out.println("all node");
	    				//System.out.println("all node :" + wrtVars.size());
	    				if(wrtVars.size() > 0) printRaces(wrtVars, ((IterNode)n).getScope(), n);
	    				//System.out.println(rdVarArr.toString());
	    				//System.out.println(wrtVarArr.toString());
	    			}
	    		}
	    	}
	    }
	    //check the existence of .all call
	    if(type == NodeType.ARRAYNODE){
	    	if(nodeStack.size() > 1){
	    		Node depth1Node = (Node)nodeStack.get(nodeStack.size() - 1);
	    		if(depth1Node.getNodeType() == NodeType.FCALLNODE){
	    			if(((FCallNode)depth1Node).getName() == "co"){
	    				//System.out.println("co node");
	    				intersectAndPrint(rdVarArr, wrtVarArr, nodeStack);
	    			}
	    		}
	    	}
	    }
	}
	private void printRaces(TreeSet set, StaticScope scope, Node n){
		Iterator it = set.iterator();
		StaticScope s = scope;
        while (it.hasNext()) {
        	int var = (Integer)it.next();
        	//System.out.print(s.toString());
			int depth = (var >> 16) & 0xffff;
			s = scope.getEnclosingScope(depth);
			if(s instanceof BlockStaticScope){
				s = s.getLocalScope();
			}
			System.out.println("[race]Data race found by static analysis!");
        	System.out.println("\tvariable=" + s.getVariable(var & 0xffff));
        	System.out.println("\tposition=" + n.getPosition().toString());
        	System.out.println("\tsource= write-write conflict in doAll");
		}
	}
	
	private void intersectAndPrint(ArrayList<TreeSet<Integer>> rdVarArr, 
			ArrayList<TreeSet<Integer>> wrtVarArr, ArrayList<Node> nodeStack){
		TreeSet reads = new TreeSet();
		TreeSet writes = new TreeSet();
		StaticScope scope = null;
		Node n = null;
		for(int i = nodeStack.size() - 1; i>= 0; i--){
			n = nodeStack.get(i);
			NodeType type = n.getNodeType();
			if(type == NodeType.CLASSNODE)
				scope = ((ClassNode)n).getScope();
			else if(type == NodeType.DEFNNODE)
				scope = ((DefnNode)n).getScope();
			else if(type == NodeType.DEFSNODE)
				scope = ((DefsNode)n).getScope();
			else if(type == NodeType.FORNODE)
				scope = ((ForNode)n).getScope();
			else if(type == NodeType.ITERNODE)
				scope = ((IterNode)n).getScope();
			else if(type == NodeType.LAMBDANODE)
				scope = ((LambdaNode)n).getScope();
			else if(type == NodeType.MODULENODE)
				scope = ((ModuleNode)n).getScope();
			else if(type == NodeType.PREEXENODE)
				scope = ((PreExeNode)n).getScope();
			else if(type == NodeType.SCLASSNODE)
				scope = ((SClassNode)n).getScope();
			else if(type == NodeType.ROOTNODE)
				scope = ((RootNode)n).getStaticScope();
			if(scope != null) break;
		}
		if(scope == null){
			System.out.println("failed to find outer static scope!");
		}
		for(int i = 0; i < rdVarArr.size(); i++){
			TreeSet rd = rdVarArr.get(i);
			Iterator it = rd.iterator();
	        while (it.hasNext()) {
	        	int var = (Integer)it.next();
	        	if(writes.contains(var)){
	        		int depth = (var >> 16);
	        		StaticScope s = scope.getEnclosingScope(depth);
	        		String name = s.getVariable(var & 0xffff);
	        		System.out.println("[race]Data race found by static analysis!");
	            	System.out.println("\tvariable=" + name);
	            	if(n != null)
	            		System.out.println("\tposition=" + n.getPosition().toString());
	            	System.out.println("\t source= write-write/read-write conflict in coBegin");
	        	}
			}
	        TreeSet wrt = wrtVarArr.get(i);
	        it = rd.iterator();
	        while (it.hasNext()) {
	        	int var = (Integer)it.next();
	        	if(writes.contains(var) || reads.contains(var)){
	        		int depth = (var >> 16);
	        		StaticScope s = scope.getEnclosingScope(depth);
	        		String name = s.getVariable(var & 0xffff);
	        		System.out.println("[race]Data race found by static analysis!");
	            	System.out.println("\tvariable=" + name);
	            	if(n != null)
	            		System.out.println("\tposition=" + n.getPosition().toString());
	            	System.out.println("\tsource= write-write/read-write conflict in coBegin");
	        	}
			}
	        reads.addAll(rd);
	        writes.addAll(wrt);
		}
		
	}
	private void printNode(Node n, int depth) {
		for (int i = 0; i < depth; i++) {
			System.out.print("  ");
		}
		System.out.print(n.getNodeType().toString() + ":");
		if (n.getNodeType() == NodeType.DEFNNODE
				|| n.getNodeType() == NodeType.DEFSNODE) {
			System.out.print(((MethodDefNode) n).getName());
			if(((MethodDefNode) n).getScope() != null){
				System.out.print("," + ((MethodDefNode) n).getScope().toString());
			}
		} else if (n instanceof FCallNode) {
			System.out.print(((FCallNode) n).getName());
		} else if (n instanceof CallNode) {
			System.out.print(((CallNode) n).getName());
			
		}
		
		if(n.getNodeType() == NodeType.ARGUMENTNODE){
			System.out.print(((ArgumentNode)n).getName());
		}
		
		if (n instanceof IterNode) {
			System.out.print(((IterNode) n).getScope().toString());
		}

		if(n instanceof RefNode){
			if(((RefNode)n).getbInstrumented()){
				System.out.print(",binstrumented=true,");
			}
			else{
				System.out.print(",binstrumented=false,");
			}
		}
		else if(n instanceof AssignableNode){
			if(((AssignableNode)n).getbInstrumented()){
				System.out.print(",binstrumented=true,");
			}
			else{
				System.out.print(",binstrumented=false,");
			}
		}
		
		String strPos = n.getPosition().toString();
		int index = strPos.lastIndexOf('/');
		if (index < 0)
			index = 0;
		strPos = strPos.substring(index);
		System.out.println(", pos:" + strPos);

		List<Node> children = n.childNodes();
		for (Node child : children) {
			printNode(child, depth + 1);
		}
	}
	
	private void hashMapMerge(HashMap<String, ArrayList<Node>> left, HashMap<String, ArrayList<Node>> right){
		for(String key: right.keySet()){
			if(left.get(key) != null){
				left.get(key).addAll(right.get(key));
			}
			else{
				left.put(key, right.get(key));
			}
		}
	}
	
	private String getVarName(Node n){
		String name = "";
		if(n.getNodeType() == NodeType.LOCALASGNNODE){
			name = ((LocalAsgnNode)n).getName();
		}
		else if(n.getNodeType() == NodeType.LOCALVARNODE){
			name = ((LocalVarNode)n).getName();
		}
		else if(n.getNodeType() == NodeType.DASGNNODE){
			name = ((DAsgnNode)n).getName();
		}
		else if(n.getNodeType() == NodeType.DVARNODE){
			name = ((DVarNode)n).getName();
		}
		else if(n.getNodeType() == NodeType.GLOBALASGNNODE){
			name = ((GlobalAsgnNode)n).getName();
		}
		else if(n.getNodeType() == NodeType.GLOBALVARNODE){
			name = ((GlobalVarNode)n).getName();
		}
		else if(n.getNodeType() == NodeType.INSTASGNNODE){
			name = ((InstAsgnNode)n).getName();
		}
		else if(n.getNodeType() == NodeType.INSTVARNODE){
			name = ((InstVarNode)n).getName();
		}
		else if(n.getNodeType() == NodeType.CLASSVARASGNNODE){
			name = ((ClassVarAsgnNode)n).getName();
		}
		else if(n.getNodeType() == NodeType.CLASSVARNODE){
			name = ((ClassVarNode)n).getName();
		}
		return name;
	}
	
	private StaticScope getBlockScope(Node n){
		StaticScope scope = null;
		NodeType type = n.getNodeType();
		if(type == NodeType.CLASSNODE){			// class
			scope = ((ClassNode)n).getScope();
		}
		else if(type == NodeType.DEFNNODE){		// method definition
			scope = ((DefnNode)n).getScope();
		}
		else if(type == NodeType.DEFSNODE){		// singleton method definition
			scope = ((DefsNode)n).getScope();
		}
		else if(type == NodeType.FORNODE){		// for loop
			scope = ((ForNode)n).getScope();
		}
		else if(type == NodeType.ITERNODE){		// block
			scope = ((IterNode)n).getScope();
		}
		else if(type == NodeType.LAMBDANODE){	// lambda
			scope = ((LambdaNode)n).getScope();
		}
		else if(type == NodeType.MODULENODE){	// module
			scope = ((ModuleNode)n).getScope();
		}
		else if(type == NodeType.PREEXENODE){	// BEGIN { ... }
			scope = ((PreExeNode)n).getScope();
		}
		else if(type == NodeType.SCLASSNODE){	// Singleton class definition
			scope = ((SClassNode)n).getScope();
		}
		else if(type == NodeType.ROOTNODE){		// root
			scope = ((RootNode)n).getStaticScope();
		}
		return scope;
	}
	private boolean isVarNode(Node n){
		boolean isVar = false;
		NodeType type = n.getNodeType();

		if(	NodeType.LOCALASGNNODE 	== type || 	// local write
	    	NodeType.LOCALVARNODE 	== type || 	// local read
	    	NodeType.DASGNNODE 		== type || 	// dynamic write
	    	NodeType.DVARNODE		== type || 	// dynamic read
	    	NodeType.GLOBALASGNNODE == type || 	// global write
	    	NodeType.GLOBALVARNODE 	== type || 	// global read
	    	NodeType.INSTASGNNODE	== type || 	// object write
	    	NodeType.INSTVARNODE	== type || 	// object read 
	    	NodeType.CLASSVARASGNNODE == type ||// class write
	    	NodeType.CLASSVARNODE == type){    	// class read
	    	isVar = true;
	    }
		
		return isVar;
	}
	
	private void markNode(Node node, boolean value){
		if(node instanceof AssignableNode){
			((AssignableNode)node).setbInstrumented(value);
			if(!value){
				System.out.println("WRT:" + this.getVarName(node) 
						+ "[file:" + node.getPosition().getFile() + ", line:" + (node.getPosition().getLine() + 1) + "]"
						+ " uninstrumented!");
			}
		}
		else{
			((RefNode)node).setbInstrumented(value);
			if(!value){
				System.out.println("RD:" + this.getVarName(node) 
						+ "[file:" + node.getPosition().getFile() + ", line:" + (node.getPosition().getLine() + 1) + "]"
						+ " uninstrumented!");
			}
		}

	}
	private void markNodeList(ArrayList<Node> list, boolean value){
		for(Node node: list){
			markNode(node, value);
		}
	}
	private void add2HashMap(HashMap<String, ArrayList<Node>> hashMap, Node n){
		String name = getVarName(n);
		if(!name.equals("")){
			ArrayList<Node> nodeList = hashMap.get(name);
			if(nodeList == null){
				nodeList = new ArrayList<Node>();
				hashMap.put(name, nodeList);
			}
			nodeList.add(n);
		}
	}
	
	private void testAndSetInBranch(HashMap<String, ArrayList<Node>> hashMap, Node n){
		NodeType type = n.getNodeType();
		if(type == NodeType.IFNODE ||
				type == NodeType.FORNODE ||
				type == NodeType.CASENODE){
			for(String name : hashMap.keySet()){
				ArrayList<Node> nodeList = hashMap.get(name);
				for(Node node: nodeList){
					node.setInBranch(true);
				}
			}
		}
	}
	
	private boolean isParallelNode(Node n, Stack<Node> stack){
		return (isDotAllNode(n, stack) || isCobeginNode(n, stack));
	}
	
	private boolean isDotAllNode(Node n, Stack<Node> stack){
		//CALLNODE:all, pos:test_static.rb:11           <----------------- we are here
	    //  LOCALVARNODE:,binstrumented=false,, pos:test_static.rb:11
	    //  ITERNODE:BlockScope: [i]
		boolean b = false;
		if(n.getNodeType() == NodeType.CALLNODE){
			if((((CallNode)n).getName().equals("all"))){
				b = true;
			}
		}
		return b;
	}
	
	private boolean isCobeginNode(Node n, Stack<Node> stack){
		//  FCALLNODE:co, pos:test_static.rb:25
	    //    ARRAYNODE:, pos:test_static.rb:25           <----------------- we are here
	    //      FCALLNODE:lambda, pos:test_static.rb:25
	    //      FCALLNODE:lambda, pos:test_static.rb:29
		
		boolean b = false;
		if(n.getNodeType() == NodeType.ARRAYNODE){
			//System.out.println("-------" + ((FCallNode)n).getName());
			if(stack.size() > 0){
				Node parent = stack.get(stack.size() - 2);
				if(parent instanceof FCallNode){
					if(((FCallNode)parent).getName().equals("co")){
						//System.out.println("---------------------" + parent.getPosition().toString() + "------------------");
						b = true;
					}
				}
			}
		}
		return b;
	}
	
	public void Analyze(Node n){
		n.setNestedParallelContext(0);
		
		Stack<Node> treeStack = new Stack<Node>();
		treeStack.push(n);
		List<Node> children = n.childNodes();
	    for(Node child : children) {
	    	if(isParallelNode(n, treeStack)){// asign a unique parallel context id to .all and co
	    		child.setNestedParallelContext(n.getNestedParallelContext() + parallelCnt++);
	    	}
	    	else{
	    		child.setNestedParallelContext(n.getNestedParallelContext());
	    	}
	    	HashMap<String, ArrayList<Node>> nameHash = new HashMap<String, ArrayList<Node>>();
	    	Analyze2(child, nameHash, treeStack);
	    }
	    printNode(n, 0);
	}
	
	public void setParallelScopeId(Node n, Stack<Node> stack){
		List<Node> children = n.childNodes();
		for(Node child : children) {
	    	if(isParallelNode(n, stack)) {	// asign a unique parallel context id to .all and co
	    		child.setNestedParallelContext(n.getNestedParallelContext() + parallelCnt++);
	    	}
	    	else{
	    		child.setNestedParallelContext(n.getNestedParallelContext());
	    	}
	    }
	}
	
	public void AnalyzeDotAll(Node n, HashMap<String, ArrayList<Node>> nameHash, Stack<Node> stack){
	    //NEWLINENODE:, pos:test_static.rb:11
	    //  CALLNODE:all, pos:test_static.rb:11
	    //    LOCALVARNODE:,binstrumented=false,, pos:test_static.rb:11
	    //    ITERNODE:BlockScope: [i]
		List<Node> children = n.childNodes();
		for(Node child : children) {
	    	HashMap<String, ArrayList<Node>> subNameHash = new HashMap<String, ArrayList<Node>>();
	    	Analyze2(child, subNameHash, stack);
	    	hashMapMerge(nameHash, subNameHash);
	    }
	    
	    // find dominated nodes and remove duplicated
		Set<String> keySet = new HashSet<String>();
		keySet.addAll(nameHash.keySet());
		for(String name : keySet){
			ArrayList<Node> nodeList = nameHash.get(name);
			if(nodeList.size() > 1){
				Node mustReadNode = null;
				Node mustWriteNode = null;
				for(Node e: nodeList){
					if(mustReadNode == null && !e.isInBranch() && e instanceof RefNode){
						mustReadNode = e;
					}
					if(mustWriteNode == null && !e.isInBranch() && e instanceof AssignableNode){
						mustWriteNode = e;
					}
				}
				// remove all others, except mustReadNode and mustWriteNode
				for(int i = 0; i < nodeList.size(); i++){
					Node e = nodeList.get(i);
					if(mustReadNode != null &&
							e != mustReadNode &&
							e instanceof RefNode &&
							e.getNestedParallelContext() == mustReadNode.getNestedParallelContext()){
						markNode(e, false);
						nodeList.remove(i);	
						i--;
					}
					else if(mustWriteNode != null &&
							e != mustWriteNode &&
							e instanceof AssignableNode &&
							e.getNestedParallelContext() == mustWriteNode.getNestedParallelContext()){
						markNode(e, false);
						nodeList.remove(i);
						i--;
					}
				}
				// remove mustReadOnly, if mustWriteOnly dominates mustReadOnly
				if(mustWriteNode != null && mustReadNode != null && 
						mustReadNode.getNestedParallelContext() == mustWriteNode.getNestedParallelContext()){
						markNode(mustReadNode, false);
						nodeList.remove(mustReadNode);
				}
			}
		}
 		
 		// find read only nodes and remove them all
		keySet = new HashSet<String>();
		keySet.addAll(nameHash.keySet());
		for(String name : keySet){
			ArrayList<Node> nodeList = nameHash.get(name);
			boolean isWrite = false;
			for(Node node: nodeList){
				if(node instanceof AssignableNode){
					isWrite = true;
				}
			}
			if(!isWrite){
				// mark and remove dynamic variables
				markNodeList(nodeList, false);
				nameHash.remove(name);
			}
		}
	}
	
	public void AnalyzeCobegin(Node n, HashMap<String, ArrayList<Node>> nameHash, Stack<Node> stack){
	    //  FCALLNODE:co, pos:test_static.rb:25
	    //    ARRAYNODE:, pos:test_static.rb:25           <----------------- we are here
	    //      FCALLNODE:lambda, pos:test_static.rb:25
	    //      FCALLNODE:lambda, pos:test_static.rb:29
		ArrayList<HashMap<String, ArrayList<Node>>> nameHashList = new ArrayList<HashMap<String, ArrayList<Node>>>();
		List<Node> children = n.childNodes();
		for(Node child : children) {
	    	HashMap<String, ArrayList<Node>> subNameHash = new HashMap<String, ArrayList<Node>>();
	    	Analyze2(child, subNameHash, stack);
	    	nameHashList.add(subNameHash);
	    }
		
		// build a merged hashmap first
		HashMap<String, ArrayList<Node>> mergedHash = new HashMap<String, ArrayList<Node>>();
		for(HashMap<String, ArrayList<Node>> subNameHash : nameHashList){
			hashMapMerge(mergedHash, subNameHash);
		}
		
		// find must-read-only nodes
		Set<String> keySet = mergedHash.keySet();
		for(String name : keySet){
			Node mustReadNode = null;
			boolean isWrite = false;
			ArrayList<Node> mappedList = mergedHash.get(name);
			for(Node node : mappedList){
				if(node instanceof AssignableNode){
					isWrite = true;
				}
				if(mustReadNode == null && !node.isInBranch() && node instanceof RefNode){
					mustReadNode = node;
				}
			}
			//remove all others if 
			if(!isWrite && mustReadNode != null){
				mappedList.remove(mustReadNode);
				this.markNodeList(mappedList, false);
				mappedList.clear();
				mappedList.add(mustReadNode);
			}
		}
		
		// merge "mergedNameHash" back to the parent nameHash
		hashMapMerge(nameHash, mergedHash);
	}
	
	public void AnalyzeDefNode(Node n, HashMap<String, ArrayList<Node>> nameHash, Stack<Node> stack){
		 //NEWLINENODE:, pos:/test_static.rb:39
		 //  DEFNNODE:try2,LocalScope: [], pos:/test_static.rb:39   <----------------- we are here
		 //    ARGUMENTNODE:try2, pos:/test_static.rb:39
		 //    ARGSNODE:, pos:/test_static.rb:40
		 //    BLOCKNODE:, pos:/test_static.rb:40
		 //      NEWLINENODE:, pos:/test_static.rb:40
		 //        INSTASGNNODE:,binstrumented=false,, pos:/test_static.rb:40
		 //          STRNODE:, pos:/test_static.rb:40
		 //      NEWLINENODE:, pos:/test_static.rb:41
		 //        INSTASGNNODE:,binstrumented=false,, pos:/test_static.rb:41
		 //          STRNODE:, pos:/test_static.rb:41
		
		
		// merge "mergedNameHash" back to the parent nameHash
		//hashMapMerge(nameHash, mergedHash);
		ArrayList<HashMap<String, ArrayList<Node>>> nameHashList = new ArrayList<HashMap<String, ArrayList<Node>>>();
		List<Node> children = n.childNodes();
		for(Node child : children) {
	    	HashMap<String, ArrayList<Node>> subNameHash = new HashMap<String, ArrayList<Node>>();
	    	Analyze2(child, subNameHash, stack);
	    	nameHashList.add(subNameHash);
	    }
		// do nothing for Method definition
	}
	
	public void AnalyzeOthers(Node n, HashMap<String, ArrayList<Node>> nameHash, Stack<Node> stack){
		List<Node> children = n.childNodes();
		for(Node child : children) {
	    	if(isParallelNode(n, stack)) {	// asign a unique parallel context id to .all and co
	    		child.setNestedParallelContext(n.getNestedParallelContext() + parallelCnt++);
	    	}
	    	else{
	    		child.setNestedParallelContext(n.getNestedParallelContext());
	    	}
	    	HashMap<String, ArrayList<Node>> subNameHash = new HashMap<String, ArrayList<Node>>();
	    	Analyze2(child, subNameHash, stack);
	    	hashMapMerge(nameHash, subNameHash);
	    }
	    
	    /* collecting define and use sets for this node*/
	    // local assignment
	    NodeType type = n.getNodeType();
	    if(isVarNode(n)){    	// class read
	    	//System.out.println(n.toString());
	    	add2HashMap(nameHash, n);
	    }
	    
	    /* check scope and remove local variables */
	    StaticScope scope = getBlockScope(n);
	    // remove define set
 		if(scope != null){
 			//if(this.isParallelNode(n)){
 				System.out.println(scope.toString());
 			//}
 			
 			String[] allNames = scope.getVariables();
 			for(String m: allNames){
 				System.out.println(m);
 			}
 			
 			// remove all the local variables
 			for(String nameInScope: allNames){
 				ArrayList<Node> nodeList = nameHash.get(nameInScope);
 				if(nodeList == null){
 					System.out.println("name " + nameInScope + " is not referenced!");
 					continue;
 				}
 				else{
 					// unmark and remove local variables
 					markNodeList(nodeList, false);
 					nameHash.remove(nameInScope);
 				}
 			}
 		}
 		
 		// get all non-local variables and create a dummy node for each
	 	
 		// find dominated nodes and remove duplicated
		Set<String> keySet = new HashSet<String>();
		keySet.addAll(nameHash.keySet());
		for(String name : keySet){
			//System.out.println("name=" + name);
			ArrayList<Node> nodeList = nameHash.get(name);
			//System.out.println(nodeList.size());
			if(nodeList.size() > 1){
				Node mustReadNode = null;
				Node mustWriteNode = null;
				//System.out.println(nodeList.toString());
				for(Node e: nodeList){
					if(mustReadNode == null && !e.isInBranch() && e instanceof RefNode){
						mustReadNode = e;
					}
					if(mustWriteNode == null && !e.isInBranch() && e instanceof AssignableNode){
						mustWriteNode = e;
					}
				}
				// remove all others, except mustReadNode and mustWriteNode
				for(int i = 0; i < nodeList.size(); i++){
					Node e = nodeList.get(i);
					if(mustReadNode != null &&
							e != mustReadNode &&
							e instanceof RefNode &&
							e.getNestedParallelContext() == mustReadNode.getNestedParallelContext()){
						markNode(e, false);
						nodeList.remove(i);	
						i--;
					}
					else if(mustWriteNode != null &&
							e != mustWriteNode &&
							e instanceof AssignableNode &&
							e.getNestedParallelContext() == mustWriteNode.getNestedParallelContext()){
						markNode(e, false);
						nodeList.remove(i);
						i--;
					}
				}
				// remove mustReadOnly, if mustWriteOnly dominates mustReadOnly
				if(mustWriteNode != null && mustReadNode != null && 
						mustReadNode.getNestedParallelContext() == mustWriteNode.getNestedParallelContext()){
						markNode(mustReadNode, false);
						nodeList.remove(mustReadNode);
				}
			}
		}
 		
 		// find readonly nodes and remove them all
		if(isDotAllNode(n, stack)){	//.all
			keySet = new HashSet<String>();
			keySet.addAll(nameHash.keySet());
			for(String name : keySet){
				ArrayList<Node> nodeList = nameHash.get(name);
				boolean isWrite = false;
				for(Node node: nodeList){
					if(node instanceof AssignableNode){
						isWrite = true;
					}
				}
				if(!isWrite){
					// mark and remove dynamic variables
					markNodeList(nodeList, false);
					nameHash.remove(name);
				}
			}
		}
	}
	
	public void Analyze2(Node n, HashMap<String, ArrayList<Node>> nameHash, Stack<Node> stack){
		// push current node into the stack
		stack.push(n);
		// assign an unique id to each parallel scope
		setParallelScopeId(n, stack);
		
		// process parallel constructs
		if(this.isDotAllNode(n, stack)){ 			// .all
			AnalyzeDotAll(n, nameHash, stack);
		}
		else if(this.isCobeginNode(n, stack)){		// .co
			AnalyzeCobegin(n, nameHash, stack);
		}
		else if(n.getNodeType() == NodeType.CALLNODE){	// method or operator call node
			// we cant do anything with CallNode, because we do not know which method 
			// will be acturelly invoked
		}
		else if(n.getNodeType() == NodeType.DEFNNODE || // method definition node
			n.getNodeType() == NodeType.DEFSNODE){
			// each method call start a new scope thread, the only thing we could do is to 
			// remove duplicated read & write, remove reads dominated by must-writes
			AnalyzeDefNode(n, nameHash, stack);
		}
		
		else{										// others
			//System.out.println(" processing other node");
			AnalyzeOthers(n, nameHash, stack);
		}
		
		// test if in branch
		this.testAndSetInBranch(nameHash, n);
		
		// pop current node from the stack
		stack.pop();
	}
}