package analyser;

import java.io.File;
import java.util.ArrayList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * <h1>Knowledge</h1>
 * This class represents a knowledge pool used to store various problems.
 * It handles management, save and load of problems and their relations.
 * It can also create a rationalized copy of all problems that creates a
 * correct variable management.
 * @author Manios Krasanakis
 */
public class Knowledge {
	//The emptyProblem is a stub used when a problem is missing. It is used in place of null for problems.
	public static Problem emptyProblem = new Problem("Implemented");
	
	//An ArrayList that stores all problems
	private ArrayList<Problem> problems;
	
	/**
	 * <h1>Knowledge</h1>
	 * The constructor for the <code>Knowledge</code> class. It initializes
	 * the knowledge pool and inserts the empty problem into it.
	 */
	public Knowledge(){
		problems = new ArrayList<Problem>();
		problems.add(emptyProblem);
	}
	
	/**
	 * <h1>save</h1>
	 * Saves the knowledge pool to a designated file. The file has an XML structure.
	 * @param path : the path of the file in which the knowledge pool is saved
	 */
	public void save(String path){
		try{
			//create the XML documents
			DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
			DocumentBuilder docBuilder = docFactory.newDocumentBuilder();
			Document doc = docBuilder.newDocument();
			Element rootElement = doc.createElement("root");
			doc.appendChild(rootElement);
			//create XML nodes for problems and save individual problems (ommit the empty problem)
			for(Problem p : problems){
				if(p==emptyProblem)
					continue;
				Element prob = doc.createElement("problem");
				p.save(prob, doc);
				rootElement.appendChild(prob);
			}
			//save relations between problems
			for(int p1=0;p1<problems.size();p1++)
				if(problems.get(p1).getResult()!=null){
					int p2 = p1;
					for(int i=0;i<problems.size();i++)
						if(problems.get(i)==problems.get(p1).getResult())
							p2 = i;
					if(p2!=p1){
						Element imp = doc.createElement("implement");
						Element e1= doc.createElement("source");
						e1.appendChild(doc.createTextNode(""+p1));
						imp.appendChild(e1);
						Element e2= doc.createElement("result");
						e2.appendChild(doc.createTextNode(""+p2));
						imp.appendChild(e2);
						rootElement.appendChild(imp);
					}
				}
			//create file
			TransformerFactory transformerFactory = TransformerFactory.newInstance();
			Transformer transformer = transformerFactory.newTransformer();
			DOMSource source = new DOMSource(doc);
			StreamResult result = new StreamResult(new File(path));
			transformer.transform(source, result);
		}
		catch(Exception e){
			//error message on fail
			System.err.println("Could not save file '"+path+"':"+e.toString());
		}
	}
	
	/**
	 * <h1>load</h1>
	 * Loads the knowledge pool from a designated XML file.
	 * @param path : the path of the file from which the knowledge pool will be loaded
	 */
	public void load(String path){
		try{
			//load XML file
			File fXmlFile = new File(path);
			DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
			DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
			Document doc = dBuilder.parse(fXmlFile);
			doc.getDocumentElement().normalize();
			//clear knowledge pool and add the empty problem
			problems.clear();
			problems.add(emptyProblem);
			//load problems and add them to the knowledge pool
			NodeList problemList = doc.getElementsByTagName("problem");
			for(int i=0;i<problemList.getLength();i++)
				problems.add(new Problem((Element)problemList.item(i)));
			//load relations between problems
			NodeList implementList = doc.getElementsByTagName("implement");
			for(int i=0;i<implementList.getLength();i++){
				int p1 = Integer.parseInt(((Element)implementList.item(i)).getElementsByTagName("source").item(0).getTextContent());
				int p2 = Integer.parseInt(((Element)implementList.item(i)).getElementsByTagName("result").item(0).getTextContent());
				problems.get(p1).setResult(problems.get(p2));
			}
		}
		catch(Exception e){
			//error message on fail
			System.out.println("Could not load file '"+path+"': "+e.toString());
		}
	}
	
	/**
	 * <h1>getProblems</h1>
	 * Returns all problems in the knowledge pool (including the empty problem).
	 * @return an ArrayList that contains all problems in the knowledge pool
	 */
	public ArrayList<Problem> getProblems(){
		return problems;
	}

	/**
	 * <h1>getSolvedProblems</h1>
	 * Returns all problems in the knowledge pool that are designated as solved by a resulting problem.
	 * @return an ArrayList that contains all problems in the knowledge pool that have a resulting problem
	 */
	public ArrayList<Problem> getSolvedProblems(){
		ArrayList<Problem> ret = new ArrayList<Problem>();
		for(Problem pr : problems)
			if(pr.getResult()!=null && pr.getResult()!=pr)
				ret.add(pr);
		return ret;	
	}
	
	/**
	 * <h1>getRationalized</h1>
	 * This function generates a list of all rationalized transformations of all subproblems
	 * in the knowledge pool. The rationalized transformation basically generates the
	 * exact same problem but with renamed variables such that no two problems have a common
	 * variable. For this transformation to always have this property, it is mandatory that
	 * <b>no variable name began with an underscore (<code>_</code>) beforehand</b>.<br/>
	 * This function also takes an argument, which omits itself and any problem that results
	 * from the results (if the argument is <code>null</code>, nothing will be omitted.<br/>
	 * It is reminded that subproblem detection is performed by calling the
	 * <code>getSubproblem</code> function for all problems in the knowledge pool. If a
	 * problem cannot be split into more than one subproblems, the rationalization will
	 * be performed on it directly.<br/>
	 * Finally, this function calls the <code>getNonClassProblem</code> and if non-class code
	 * is generated from class code then it also adds the 'de-classed' to the above process.
	 * @param exclude : the problem to be excluded from the search
	 * @param ignoredLibraries : libraries that this function is requested to ignore
	 * @param threads : the number of threads used for parallel processing (if <code>threads>1</code>
	 * then the knowledge pool is split into smaller knowledge pools and the result is the merge
	 * of calling <code>getRationalized</code> for each of these new pools with <code>threads=1</code>)
	 * @return an ArrayList that contains the rationalized transformation of all valid problems
	 */
	public ArrayList<Problem> getRationalized(final Problem exclude, final ArrayList<String> ignoredLibraries, int threads){
		///Multi-threaded split to subproblems (only if threads>1).
		final ArrayList<Problem> prob = new ArrayList<Problem>();
		if(threads>1){
			final Knowledge [] kn = new Knowledge[threads]; 
			Thread [] th = new Thread[threads];
			int threadSize = problems.size()/threads;
			for(int i=0;i<threads;i++){
				kn[i] = new Knowledge();
				final int threadId = i;
				kn[i].problems.addAll(problems.subList(threadId*threadSize, threadId<threads-1?(threadId+1)*threadSize: problems.size()));
				th[i] = new Thread(){
					@Override
					public void run(){
						prob.addAll(kn[threadId].getRationalized(exclude, ignoredLibraries, 1));
					}
				};
			}
			for(int i=0;i<threads;i++)
				th[i].start();
			for(int i=0;i<threads;)
				try{
					th[i].join();
					i++;
				}
				catch(InterruptedException e){
					try{
						Thread.sleep(1);
					}
					catch (InterruptedException e1){
						e1.printStackTrace();
					}
				}
			return prob;
		}
		///Actual rationalization (only if threads<=1)
		for(Problem p : problems){
			if(p.getResult()!=null && p!=exclude && p.getResult()!=exclude){
				boolean ignored = false;
				if(ignoredLibraries!=null)
					for(String ign : ignoredLibraries)
						if(ign!=null && p.getLibrary().compareTo(ign)==0){
							ignored = true;
							break;
						}
				if(!ignored){
					ArrayList<Problem> subproblems = p.getSubproblems();
					if(subproblems.size()>1){
						for(Problem subproblem : subproblems)
							if(subproblem.getResult()!=null)
								prob.add(new Problem(subproblem, "_"+subproblem.getName()+"_"));
					}
					else
						prob.add(new Problem(p, "_"+p.getName()+"_"));
					Problem nonclass = p.getNonClassProblem();
					if(nonclass!=null){
						subproblems = nonclass.getSubproblems();
						if(subproblems.size()>1){
							for(Problem subproblem : subproblems)
								if(subproblem.getResult()!=null)
									prob.add(new Problem(subproblem, "_"+subproblem.getName()+"_"));
						}
						else
							prob.add(new Problem(nonclass, "_"+nonclass.getName()+"_"));
					}
				}
			}
		}
		return prob;
	}
	
	/**
	 * <h1>add</h1>
	 * Adds a problem to the knowledge pool. If the problem already was a member
	 * of the knowledge pool, this function does nothing.
	 * @param p : a problem to be added to the knowledge pool
	 */
	public void add(Problem p){
		for(Problem pr : problems)
			if(pr==p)
				return;
		problems.add(p);
	}
	
	/**
	 * <h1>replace</h1>
	 * Replaces a problem with a new one, while retaining order.
	 * @param original : the original problem
	 * @param p : the problem to add
	 */
	public void replace(Problem original, Problem p){
		int i = problems.indexOf(original);
		if(i!=-1)
			problems.remove(i);
		else
			i = problems.size();
		if(p!=null)
			problems.add(i, p);
	}
	
	/**
	 * <h1>remove</h1>
	 * Removes a problem from the knowledge pool. This function also clears
	 * all references to that problem (i.e. as their result) from other members
	 * of the knowledge pool. 
	 * @param p : a problem to be removed from the knowledge pool
	 */
	public void remove(Problem p){
		for(Problem pr : problems){
			if(pr.getResult()==p)
				pr.setResult(null);
		}
		problems.remove(p);
	}

	/**
	 * <h1>isImplementation</h1>
	 * @param p : the given problem
	 * @return <code>true</code> if the given problem is an implementation of another problem
	 */
	public boolean isImplementation(Problem p) {
		for(Problem pr : problems)
			if(pr.getResult()==p)
				return true;
		return false;
	}

	/**
	 * <h1>getOriginalProblem</h1>
	 * @param p : the given problem
	 * @return a problem that has the given problem as its implementation. If more than one problems
	 * have the given problem as an implementation, which one will be returned is unpredictable. If
	 * the given problem is not an implementation of another problem in this knowledge pool, then the
	 * given problem is returned.
	 */
	public Problem getOriginalProblem(Problem p) {
		for(Problem pr : problems)
			if(pr.getResult()==p)
				return pr;
		return p;
	}
	
	/**
	 * <h1>getLibraryList</h1>
	 * @return a list of all libraries as defined in problems that are not implementations
	 */
	public ArrayList<String> getLibraryList(){
		ArrayList<String> ret = new ArrayList<String>();
		for(Problem pr : problems){
			if(!isImplementation(pr) && !pr.getLibrary().isEmpty()){
				boolean exists = false;
				for(String str : ret)
					if(str.compareTo(pr.getLibrary())==0){
						exists = true;
						break;
					}
				if(!exists)
					ret.add(pr.getLibrary());
			}
		}
		return ret;
	}

	/**
	 * <h1>refresh</h1>
	 * This calls <code>Problem.refresh</code> for all problems in the knowledge pool.
	 */
	public void refresh() {
		for(Problem p : problems)
			p.refresh();
	}
}
