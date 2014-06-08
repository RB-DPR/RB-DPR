import java.util.ArrayList;
import java.io.FileReader;
import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;

public class Graph{
	private ArrayList<Integer> vertices;
	private ArrayList<ArrayList<Integer>> adjacentTable;
	
	public Graph(String file){
		this.vertices = new ArrayList<Integer>();
		this.adjacentTable = new ArrayList<ArrayList<Integer>>();
		try{
			System.out.println(file);
			FileReader reader = new FileReader(file);
	        BufferedReader br = new BufferedReader(reader);
	        String str = null;
	       
	        while((str = br.readLine()) != null) {
	        	System.out.println(str);
	        	if(str.length() <= 0) continue;
	        	int index = str.indexOf(",");
	        	if(index >= 0){
	        		int from = Integer.parseInt(str.substring(0, index));
	        		addVertex(from);
	        		index = str.indexOf(" ");
	        		if(index >= 0){
	        			str = str.substring(index + 1).trim();
		        		String[] strTos = str.split(" ");
		        		for(String to : strTos){
		        			System.out.println("add edge : " + from + "-->" + to);
		        			addEdge(from, Integer.parseInt(to));
		        		}
	        		}
	        	}
	        }
	        br.close();
	        reader.close();
		}catch (FileNotFoundException e) {
			e.printStackTrace();
	    }
	    catch(IOException e) {
	    	e.printStackTrace();
	    }
	    /*
	    System.out.println(getFromList(2));
	    System.out.println(getToList(2));
	    ArrayList<Integer> poList = new ArrayList<Integer>();
	    ArrayList<Integer> vList = new ArrayList<Integer>();
	    getPostOrder(this, 0, poList, vList);
	    System.out.println(poList);*/
	}
	
	public ArrayList<Integer> getVertices(){
		return this.vertices;
	}
	public ArrayList<ArrayList<Integer>> getTable(){
		return this.adjacentTable;
	}
	public Graph(Graph graph) {
		this.vertices = new ArrayList<Integer>();
		this.adjacentTable = new ArrayList<ArrayList<Integer>>();
		this.vertices.addAll(graph.getVertices());
		for(int i = 0; i < this.vertices.size(); i++){
			ArrayList<Integer> list = new ArrayList<Integer>();
			list.addAll(graph.getTable().get(i));
			this.adjacentTable.add(list);
		}
	}

	public void print(){
		System.out.println("There are " + vertices.size() + " nodes in total");
		int index = 0;
		for(ArrayList<Integer> list : adjacentTable){
			System.out.println(vertices.get(index) + ":" + list);
			index++;
		}
	}
	public void addVertex(int v){
		if(vertices.indexOf(v) < 0){
			vertices.add(v);
			adjacentTable.add(new ArrayList<Integer>());
		}
	}
	
	public void addEdge(int f, int t){
		int index = vertices.indexOf(f);
		if(index < 0){
			System.out.println("addEdge: cant find the source vertex!");
			System.exit(1);
		}
		
		ArrayList<Integer> targets = adjacentTable.get(index);
		if(targets.indexOf(t) < 0){
			targets.add(t);
		}
	}
	
	public ArrayList<Integer> getFromList(int v){
		ArrayList<Integer> froms = new ArrayList<Integer>();
		for(int i = 0; i < adjacentTable.size(); i++){
			ArrayList<Integer> l = adjacentTable.get(i);
			int index = l.indexOf(v);
			if(index >= 0){
				froms.add(vertices.get(i));
			}
		}
		return froms;
	}
	
	public ArrayList<Integer> getToList(int v){
		ArrayList<Integer> list = new ArrayList<Integer>();
		int index = vertices.indexOf(v);
		if(index >= 0){
			list.addAll(adjacentTable.get(index));
		}
		return list;
	}
	
	public void removeEdge(int from, int to){
		int index = vertices.indexOf(from);
		if(index >= 0){
			ArrayList<Integer> list = adjacentTable.get(index);
			index = list.indexOf(to);
			list.remove(index);
		}
	}
	
	public void removeVertex(int v){
		int index = vertices.indexOf(v);
		if(index >= 0){
			vertices.remove(index);
			adjacentTable.remove(index);
		}
	}
	
	public int outDegree(int v){
		int index = vertices.indexOf(v);
		if(index >= 0){
			return adjacentTable.get(index).size();
		}
		
		return 0;
	}
	
	public boolean hasEdge(int from, int to){
		int index = vertices.indexOf(from);
		if(index >= 0){
			if(adjacentTable.get(index).contains(to)){
				return true;
			}
			else{
				return false;
			}
		}
		else{
			return false;
		}
	}
	
	public void getPostOrder(Graph g, int root, ArrayList<Integer> vlist, ArrayList<Integer> visited){
		visited.add(root);
		int index = vertices.indexOf(root);
		if(index >= 0){
			ArrayList<Integer> adjacentList = adjacentTable.get(index);
			for(int i = 0; i < adjacentList.size(); i++){
				if(visited.indexOf(adjacentList.get(i)) < 0){
					getPostOrder(g, adjacentList.get(i), vlist, visited);
				}
			}
		}
		vlist.add(root);
	}
	public boolean connected(){
		ArrayList<Integer> list = new ArrayList<Integer>();
		ArrayList<Integer> visited = new ArrayList<Integer>();
		getPostOrder(this, this.vertices.get(0), list, visited);
		if(list.size() == this.vertices.size()){
			return true;
		}
		else{
			return false;
		}
	}
	
	public int[] getEdge(int offset){
		int index = 0;
		int[] oneEdge = new int[2];
		for(ArrayList<Integer>list:this.adjacentTable){
			if(list.size() > offset){
				oneEdge[0] = this.vertices.get(index);
				oneEdge[1] = list.get(offset);
				//System.out.println(oneEdge[0] + "," + oneEdge[1]);
				return oneEdge;
			}
			else{
				offset -= list.size();
			}
			index++;
		}
		return oneEdge;
	}
	public Graph reduce(Graph g){
		ArrayList<Integer> visited = new ArrayList<Integer>();
		ArrayList<Integer> vlist = new ArrayList<Integer>();
		int currPos = 0;
		boolean changed = true;
		
		while(this.vertices.size() > 1){
			if(changed){
				visited.clear();
				vlist.clear();
				getPostOrder(g, 0, vlist, visited);
				currPos = 0;
				changed = false;
			}
			else if(currPos >= vlist.size() && !changed){
				break;
			}
			int v = vlist.get(currPos);
			ArrayList<Integer> toList = g.getToList(v);
			//test if it is a sequential path
			if(toList.size() == 1){
				ArrayList<Integer> fromList = g.getFromList(toList.get(0));
				if(fromList.size() == 1){
					ArrayList<Integer> toList2 = g.getToList(toList.get(0));
					for(int node: toList2){
						g.addEdge(v, node);
						g.removeEdge(toList.get(0), node);
					}
					g.removeEdge(v, toList.get(0));
					g.removeVertex(toList.get(0));
					changed = true;
				}
			}
			//test if it is an "if"
			else if(toList.size() == 2){
				//if-else ?
				ArrayList<Integer> fromList0 = g.getFromList(toList.get(0));
				ArrayList<Integer> fromList1 = g.getFromList(toList.get(1));
				if(fromList0.size() == 1 && fromList1.size() == 1){
					ArrayList<Integer> toList0 = g.getToList(toList.get(0));
					ArrayList<Integer> toList1 = g.getToList(toList.get(1));
					if(toList0.size() == 1 && toList1.size() == 1){
						if(toList0.get(0) == toList1.get(0)){
							g.removeEdge(v, toList.get(0));
							g.removeEdge(v, toList.get(1));
							g.removeEdge(toList.get(0), toList0.get(0));
							g.removeEdge(toList.get(1), toList1.get(0));
							g.addEdge(v, toList0.get(0));
							g.removeVertex(toList.get(0));
							g.removeVertex(toList.get(1));
							changed = true;
						}
					}
					else if(toList0.size() == 0 && toList1.size() == 0){
						g.removeEdge(v, toList.get(0));
						g.removeEdge(v, toList.get(1));
						g.removeVertex(toList.get(0));
						g.removeVertex(toList.get(1));
						changed = true;
					}
				}
				else if(fromList0.size() == 1){
					ArrayList<Integer> toList0 = g.getToList(toList.get(0));
					if(toList0.size() == 1 && toList0.get(0) == toList.get(1)){
						g.removeEdge(v, toList.get(0));
						g.removeEdge(toList.get(0), toList.get(1));
						g.removeVertex(toList.get(0));
						changed = true;
					}
				}
				else if(fromList1.size() == 1){
					ArrayList<Integer> toList1 = g.getToList(toList.get(1));
					if(toList1.size() == 1 && toList1.get(0) == toList.get(0)){
						g.removeEdge(v, toList.get(1));
						g.removeEdge(toList.get(1), toList.get(0));
						g.removeVertex(toList.get(1));
						changed = true;
					}
				}
			}
			ArrayList<Integer> fromList = g.getFromList(v);
			//test if it is a "loop"
			if(fromList.size() == 1){
				if(g.hasEdge(fromList.get(0), v) && g.hasEdge(v, fromList.get(0))){
					if(g.outDegree(fromList.get(0)) == 2 && g.outDegree(v) == 1){
						g.removeEdge(v, fromList.get(0));
						g.removeEdge(fromList.get(0), v);
						g.removeVertex(v);
						
						changed = true;
					}
					else if(g.outDegree(v) ==  2 && g.outDegree(fromList.get(0)) == 1){
						g.removeEdge(v, fromList.get(0));
						changed = true;
					}
				}
			}
			currPos += 1;
		}
		return this;
	}
	
	public Graph makeACopy(){
		Graph c = new Graph(this);
		return c;
	}
	
	public int vertexNum(){
		return this.vertices.size();
	}
	
	public int edgeNum(){
		int num = 0;
		for(ArrayList<Integer> list: this.adjacentTable){
			num += list.size();
		}
		return num;
	}
}
