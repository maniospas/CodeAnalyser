package analyser;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import display.Main;

/**
 * <h1>Problem</h1>
 * This class represents a problem as a collection of statements. Each problem has its own name, as well
 * as a problem result problem that implements it. Result problems are equivalent to original problems
 * but are considered implementable. Considering statements, note that they must be order-independent.<br/>
 * This class also offers functionality for transforming problems and creating implementations out of a
 * knowledge pool.
 * @author Manios Krasanakis
 *
 */
public class Problem {
	//the problem name
	private String name;
	//the problem library
	private String library = "";
	//the problem that implements the current problem
	private Problem result;
	//the set of statements that implement the problem
	public ArrayList<Statement> statements;
	//a log record for the solve function
	volatile private static String logRecord = "";
	volatile private static int logLevel = 3;
	//a text that defines the "this" statement in Python (if "", then ignores classes)
	//this text may change according to class implementation
	private String functionMemberVariable = "this";
	public static boolean breakdownStatements = true;
	public static boolean breakdownAlsoCode = false;
	public static boolean constantsAreVariables = true;
	
	/**
	 * <h1>Problem</h1>
	 * The constructor for the Problem class. It creates an empty problem with particular name,
	 * by initializing the statement list and setting the result problem as null.
	 * @param name : the name of the problem
	 */
	public Problem(String name){
		this.name = name;
		statements = new ArrayList<Statement>();
		result = null;
	}
	

	/**
	 * <h1>Problem</h1>
	 * A constructor for the Problem class that creates a <b>rationalized copy</b> of an original problem,
	 * using a desired prefix.
	 * @param p : the original problem
	 * @param prefix : a desired prefix
	 */
	public Problem(Problem p, String prefix) {
		name = p.name;
		library = p.library;
		statements = new ArrayList<Statement>();
		for(Statement s : p.statements){
			Statement sn = new Statement(s.getVariables(), s.getExpression());
			sn.addVariablePrefix(prefix);
			statements.add(sn);
		}
		result = null;
		if(breakdownStatements)
			expand("", breakdownAlsoCode);
		if(p.result==null)
			result = null;
		else
			result = new Problem(p.result, prefix);
	}
	
	/**
	 * <h1>Problem</h1>
	 * A constructor for the Problem class that loads the problem's characteristics from an XML
	 * element. It directly calls the <code>load</code> function.
	 * @param e : the XML element to load from
	 */
	public Problem(Element e){
		load(e);
	}
	
	/**
	 * <h1>getName</h1>
	 * @return the name of the problem
	 */
	public String getName(){
		return name;
	}

	/**
	 * <h1>getLibrary</h1>
	 * @return the library this project belongs to
	 */
	public String getLibrary(){
		return library;
	}
	

	/**
	 * <h1>getFunctionMemberVariable</h1>
	 * @return the <code>this</code> Python variable for this function
	 */
	public String getFunctionMemberVariable(){
		return functionMemberVariable;
	}

	/**
	 * <h1>setFunctionMemberVariable</h1>
	 * @return set <code>this</code> Python variable for this function
	 */
	public void setFunctionMemberVariable(String thisName){
		functionMemberVariable = thisName;
		if(result!=null)
			result.setFunctionMemberVariable(thisName);
	}

	/**
	 * <h1>setLibrary</h1>
	 * Sets the problem's library name to the given value. Libraries should be shared between many
	 * similar problems. This also sets the library of the result, but not in a manner that can loop.
	 * @param lib : the given library
	 */
	public void setLibrary(String lib){
		library = lib;
		if(result!=null)
			result.library = lib;
	}
	
	/**
	 * <h1>setResult</h1>
	 * Sets the result problem of the current problem. If an invalid problem (i.e. the defined problem in the
	 * knowledge pool or a reference to itself) is given, the result is set as null.
	 * This function <b>does not check for circular reference of results</b>.
	 * The result library is set to the same as the problem library.
	 * @param res : the desired problem to be set as the result of this problem
	 */
	public void setResult(Problem res){
		if(res==Knowledge.emptyProblem || res==this)
			result = null;
		else{
			result = res;
			if(res!=null)
				res.setLibrary(library);
		}
	}
	
	/**
	 * <h1>getResult</h1>
	 * Returns the result problem. The result problem implements this problem. This means that
	 * it is equivalent but all its statements are considered implementable. If a problem does
	 * not have a result it is considered implementable.<br/>
	 * <i>Note: There may be circular reference of results.</i>
	 * @return the result problem
	 */
	public Problem getResult(){
		return result;
	}
	
	@Override
	public String toString(){
		if(library.isEmpty())
			return name;
		return library+": "+name;
	}
	
	/**
	 * <h1>getComments</h1>
	 * Generates <i>Python</i>  comments that describe the problem from its statements. The function's name is the
	 * given function name and the comments' body is a merge of all <code>Statement.toString</code> calls. Those
	 * comments are not tied to anything and usually they should be used together with
	 *  <code>getResult().getCode(getName())</code>.<br/>
	 * @param prefix : begin comments symbol (for python comments it myst be <code>"\"\"\"\n"</code>)
	 * @param suffix : end comments symbol (for python comments it myst be <code>"\"\"\""</code>)
	 */
	public String getComments(String prefix, String suffix){
		String ret = prefix+"Function: "+getName()+"\n";
		
		String inputs = "";
		String outputs = "";
		String variables = "";
		String comments = "";
		for(Statement s : statements){
			if(s.isInput()){
				if(!inputs.isEmpty())
					inputs += ", ";
				inputs += s.getVariables(", ");
			}
			else if(s.isOutput()){
				if(!outputs.isEmpty())
					outputs += ", ";
				outputs += s.getVariables(", ");
			}
			else{
				if(!s.isIterator())
					variables = unionVariables(variables, s.getVariables(", "), ", ");
				boolean source = s.isSourceCode();
				if(source)
					comments += "{\n";
				comments +=  s.toString()+"\n";
				if(source)
					comments += "}\n";
			}
		}
		variables = diffVariables(variables, inputs, ", ");
		variables = diffVariables(variables, outputs, ", ");
		if(!library.isEmpty())
			ret += "Library: "+library+"\n";
		if(!inputs.isEmpty())
			ret += "Inputs: "+inputs+"\n";
		if(!outputs.isEmpty())
			ret += "Outputs: "+outputs+"\n";
		if(!variables.isEmpty())
			ret += "Variables: "+variables+"\n";
		ret += comments;
		
		ret += suffix;
		return ret;
	}
	
	/**
	 * <h1>getCode</h1>
	 * Generates a <i>Python</i> function that describes the problem from its statements. The function's name is
	 * the given function name, and its body is a merge of all <code>Statement.toString</code> calls for non-input,
	 * non-output and non-iterator statements. Inputs and outputs are detected and placed correctly to arguments
	 * and a single return statement respectively. Iterators are all mentioned together at the beginning of the
	 * function's body.<br/>
	 * It must be noted that the <b>generated code will only have the form of a <i>Python</i> function</b>, as not
	 * all statements are necessarily <i>Python</i> commands.<br/>
	 * Also, the predicates <code>is, lt, gt</code> will be replaced with the symbols <code>=, &lt, &gt</code>
	 * respectively.
	 * @param functionName : the generated function's name
	 * @return the problem's <i>Python</i> code as a String
	 */
	public String getCode(String functionName){
		String body = "";
		String inputs = "";
		String outputs = "";
		String init = "";
		int outnum = 0;
		for(Statement s : statements){
			if(s.isInput()){
				if(!inputs.isEmpty())
					inputs += ", ";
				inputs += s.getVariables(", ");
			}
			else if(s.isOutput()){
				if(!outputs.isEmpty())
					outputs += ", ";
				outputs += s.getVariables(", ");
				outnum += s.variables.size();
			}
			else if(s.isIterator()){
				init += "    "+s.toString()+"\n";
			}
			else{
				body += "    "+s.toString().replace("\n", "\n    ")+"\n";
			}
		}
		String str = "def "+functionName+"("+inputs+"):\n"+init+body;
		if(outnum==1)
			str += "    "+"return "+outputs;
		if(outnum>1)
			str += "    "+"return ("+outputs+")";
		return str;
	}
	
	/**
	 * <h1>getCode</h1>
	 * Generates a <i>Python</i> function that describes the problem from its statements. The function's name is
	 * the name of the problem, and its body is a merge of all <code>Statement.toString</code> calls for non-input,
	 * non-output and non-iterator statements. Inputs and outputs are detected and placed correctly to arguments
	 * and a single return statement respectively. Iterators are all mentioned together at the beginning of the
	 * function's body.<br/>
	 * It must be noted that the <b>generated code will only have the form of a <i>Python</i> function</b>, as not
	 * all statements are necessarily <i>Python</i> commands.<br/>
	 * Also, the predicates <code>is, lt, gt</code> will be replaced with the symbols <code>=, &lt, &gt</code>
	 * respectively.
	 * @return the problem's <i>Python</i> code as a String
	 */
	public String getCode(){
		return getCode(name);
	}
	
	/**
	 * <h1>getHTMLCode</h1>
	 * Generates a <i>Python</i> function that describes the problem from its statements. The function generated
	 * is the same to the one generated by the <code>getCode</code> function, but it can be displayed by an HTML
	 * viewer.
	 * @return the problem's <i>Python</i> code as an String that can be displayed by an HTML viewer
	 */
	public String getHTMLCode(){
		String body = "";
		String inputs = "";
		String outputs = "";
		String init = "";
		int outnum = 0;
		
		for(Statement s : statements){
			if(s.isInput()){
				if(!inputs.isEmpty())
					inputs += ", ";
				inputs += s.getVariables(", ");
			}
			else if(s.isOutput()){
				if(!outputs.isEmpty())
					outputs += ", ";
				outputs += s.getVariables(", ");
				outnum += s.variables.size();
			}
			else if(s.isIterator()){
				init += "&nbsp&nbsp&nbsp&nbsp "+s.toHTML()+"<br/>";
			}
			else{
				body += "&nbsp&nbsp&nbsp&nbsp "+s.toHTML().replace("<br/>", "<br/>&nbsp&nbsp&nbsp&nbsp ")+"<br/>";
			}
		}
		
		String str = "<b>def</b> "+name+"("+inputs+")<b>:</b><br/>"+"<i>"+init+"</i>"
		            + body.replace("for ", "<b>for</b> ").replace("while(", "<b>while</b>(")
	                      .replace("elif(", "<b>elif</b>(").replace("else", "<b>else</b>")
		                  .replace("if(", "<b>if</b>(").replace("solve:", "<b>solve</b>:")
		                  .replace(":", "<b>:</b>") .replace(" in ", " <b>in</b> ");
		if(outnum==1)
			str += "&nbsp&nbsp&nbsp&nbsp <b>return</b> "+outputs;
		if(outnum>1)
			str += "&nbsp&nbsp&nbsp&nbsp <b>return</b> ("+outputs+")";
		return str;
	}
	
	/**
	 * <h1>isSolved</h1>
	 * This function checks weather the problem's generated code (see <code>getCode</code> and <code>getHTMLCode</code>)
	 * is close to being actual <i>Python</i> code. It actually checks weather all the problem's statements are trivial
	 * statements (see <code>Statement.isTrivial</code>. However, this <b>does not cover the case of circular variable
	 * assignment</b>. If the problem was generated automatically, circular variable assignment can be caused by
	 * conflicts of multiple implementations.
	 * @return <code>true</code> if the problem is a solved one
	 */
	public boolean isSolved(){
		for(Statement s : statements)
			if(!s.isTrivial())
				return false;
		return true;
	}
	
	/**
	 * <h1>clearLog</h1>
	 * This function clears the log record.
	 */
	synchronized public static void clearLog(){
		logRecord = "";
	}
	/**
	 * <h1>setLogLevel</h1>
	 * This function sets the maximum log level (lower log levels are more important).
	 * @param level : the new maximum log level
	 */
	synchronized public static void setLogLevel(int level){
		logLevel = level;
	}
	/**
	 * <h1>getLogLevel</h1>
	 * This function returns the maximum log level (lower log levels are more important).
	 * @return the maximum log level
	 */
	synchronized public static int getLogLevel(){
		return logLevel;
	}
	/**
	 * <h1>log</h1>
	 * This function adds contents to the log record in HTML form. The contents have different formatting
	 * according to the importance level. The importance level ranges from 0 to 3, where 0 is of most importance
	 * and 3 is of least importance message.<br/>
	 * On importance of 0, the label <code>Main.log</code> is also updated if it has been generated.<br/>
	 * As a special input, the level -1 directly adds the text to the log record's HTML body.
	 * @param str : the contents to be added to the log record
	 * @param level : the importance of the comments to be added to the log record (0=most important, 3=least important)
	 */
	synchronized public static void log(String str, int level){
		if(level>logLevel)
			return;
		//System.out.println(str);
		if(level==0)
			logRecord += "<br/><font color=\"red\"><b>"+str+"</b></font><br/>";
		else if(level==1)
			logRecord += "<font color=\"blue\"><b>"+str+"</b></font><br/>";
		else if(level==2)
			logRecord += "&nbsp&nbsp&nbsp<b>"+str.replace("<br/>", "<br/>&nbsp&nbsp&nbsp ")+"</b><br/>";
		else if(level>0)
			logRecord += "&nbsp&nbsp&nbsp&nbsp&nbsp&nbsp "+str.replace("<br/>", "<br/>&nbsp&nbsp&nbsp&nbsp&nbsp&nbsp ")+"<br/>";
		else
			logRecord += str;
		if(level<=0 && Main.log!=null)
			Main.log.setText(getLog());
	}
	/**
	 * <h1>getLog</h1>
	 * @return returns the log record (generated by the <code>solve</code>function) in HTML form
	 */
	public static String getLog(){
		return "<html>"+logRecord+"</html>";
	}
	
	/**
	 * <h1>solve</h1>
	 * Attempts to solve a given problem by using the data in a knowledge pool. All generated statements
	 * towards the solution of the given problem are stored inside this problem (<b>this problem should
	 * be empty</b>). Calling this function only progresses <b>a single step</b> towards the problem's
	 * solution. For a complete solution, this function should be called until the problem no longer
	 * changes.
	 * @param p : the given problem
	 * @param problems : a problem list (it should be generated with
	 * <code>Knowledge.getRationalizedProblems</code>)
	 * @param unknownMessage : a message that will precede all non-implementable statements
	 * @param concervative : a value between 0 and 1 that balances merge selection
	 * @param threads: a number of threads to split the search for the best problem to merge
	 * (0 makes new information most important, 1 makes removing old information most important)
	 */
	public void solve(final Problem p, final ArrayList<Problem> problems, String unknownMessage, final float concervative, final int threads){
		/**
		 * 1. PRE-PROCESSING
		 * 1.1 Detect input and outputs and generate a single input and a single output statement
		 * 1.2 Create a copy of the given problem's original statements (we will need to alter the copied statements)
		 */
		if(breakdownStatements)
			p.expand("", breakdownAlsoCode);
		//1.1a find inputs, outputs and iterators
		String inputs = "";
		String outputs = "";
		String iters = "";
		statements.clear();
		for(Statement s : p.statements){
			if(s.isInput()){
				inputs = unionVariables(inputs, s.getVariables(), " ");
			}
			else if(s.isOutput()){
				outputs = unionVariables(outputs, s.getVariables(), " ");
			}
			else if(s.isIterator()){
				iters = unionVariables(iters, s.getVariables(), " ");
			}
		}
		//1.1b generate single input statement and single output statement
		if(!inputs.isEmpty())
			statements.add(new Statement(inputs, "input"));
		if(!outputs.isEmpty())
			statements.add(new Statement(outputs, "output"));
		//1.2 create a copy of the given problem's original statements
		ArrayList<Statement> pendingStatements = new ArrayList<Statement>();
		for(Statement s : p.statements)
			pendingStatements.add(new Statement(s.getVariables(), s.getExpression()));

		/**
		 * 2. COMPARING PROBLEMS
		 * For all problems kp in the rationalized copy of the knowledge pool that have a valid result:
		 * 2.1 Detect common statements between p and kp (if no common statements, proceed to next kp)
		 * 2.2 Remove common expression that are common between p and kp (those expressions will be
		 *     replaced with their equivalents from the result of kp).
		 * Perform the next steps only for the kp which has the maximum common information.
		 * 2.3 Create statements that synchronize (i.e. assign to one another) the variables between
		 *     expressions of p and kp. (e.g. if p has the statement a=b^2+1 and kp has the statement
		 *     _kp_a=_kp_b^2+1, then the following statements will be also created: a=_kp_a, b=_kp_b)
		 * 2.4 Add all expressions in the result of kp that affect the variables in kp that were
		 *     synchronized in the previous step (those are equivalent to the expressions removed in 2.2)
		 *     
		 * Note: All operations affect the statements inside pendingStatements, which are originally
		 *       a copy of p's statements but change during iterations.
		 */
		int prevLogLevel = logLevel;
		if(threads>1 && logLevel>=3){
			setLogLevel(2);
			log("Some details are ignored in multi-threaded mode.", 0);
		}
		if(threads>1)
			log("Selecting problem with most information ("+threads+" threads)", 1);
		ArrayList<Problem> select = new ArrayList<Problem>();
		float maxInformation = Float.MIN_VALUE;
		float selectionImplemented = Float.MAX_VALUE;
		final ArrayList<ArrayList<Problem>> threadSelection = new ArrayList<ArrayList<Problem>>();
		for(int i=0;i<threads;i++)
			threadSelection.add(new ArrayList<Problem>());
		final Float [] threadMaxInformation = new Float[threads];
		final Float [] threadSelectionImplemented = new Float[threads];
		final int threadSize = problems.size()/threads;
		Thread [] th = new Thread[threads];
		final Problem thisProblem = this;
		///create threads
		for(int i=0;i<threads;i++){
			final int threadId = i;
			threadMaxInformation[i] = maxInformation;
			threadSelectionImplemented[i] = selectionImplemented;
			threadSelection.get(i).clear();
			th[i] = new Thread(){
			@Override
			public void run(){
			ArrayList<Problem> prob = new ArrayList<Problem>();
			prob.addAll(problems.subList(threadId*threadSize, threadId<threads-1?(threadId+1)*threadSize: problems.size()));
			for(Problem kp : prob){
				if(kp!=thisProblem && kp.getResult()!=thisProblem && kp.getResult()!=null){
					//2.1 detect common statements (if no common statements call continue)
					String lib = kp.getLibrary();
					if(!lib.isEmpty())
						lib = " (from library <i>"+lib+"</i>)";
					if(logLevel>=3)
						log("Detecting common non-trivial expressions between <i>"+p.getName()+"</i> and <i>"+kp.getName()+"</i>", 1);
					ArrayList<Statement> comm = commonNonTrivial(p.statements, kp.statements);
					if(comm.size()==0){
						if(logLevel>=3)
							log("Nothing found", 3);
						else
							log("Comparing <i>"+p.getName()+"</i> and <i>"+kp.getName()+"</i>"+lib+": 0 bits (nothing found)", 2);
						continue;
					}
					//2.3 synchronize variables of common statements
					if(logLevel>=3)
						log("Synchronizing variables between <i>"+p.getName()+"</i> and <i>"+kp.getName()+"</i>", 2);
					ArrayList<Statement> sync = commonVariableSync(p.statements, kp.statements);
					//2.4a Find the names of the variables in kp that were synchronized
					if(logLevel>=3)
						log("Merging into <i>"+thisProblem.getName()+"</i> equivalent expressions from <i>"+kp.getResult().getName()+"</i>", 2);
					String commVar = "";
					for(Statement c : sync){
						if(!commVar.isEmpty())
							commVar += " ";
						/*if(c.isAssignment())
							commVar += diffVariables(c.getVariables(), c.getTrivialVariable(), " ");
						else*/
							commVar += c.getVariables();
					}
					//2.4b&c found things to merge
					ArrayList<Statement> ins = getAffectedFromVariables(kp.getResult().statements, commVar);
					if(logLevel>=3){
						log("Expressions to merge into <i>"+thisProblem.getName()+"</i> from <i>"+kp.getResult().getName()+"</i>", 2);
						for(Statement s : ins)
							log(s.getExpression(), 3);
					}
					//count information information gained
					float informationConcervative = 0;
					float informationNew = 0;
					for(Statement st : comm){
						float info = st.getInformation()+1;
						//get only common information if complex predicate rearrangement
						float maxVal = 1;
						if(!st.isTransitional()){
							maxVal = 0;
							for(Statement s : kp.statements)
								if(!s.isTrivial() && s.isSame(st)){
									float val = Statement.predicateSimilarity(s.getExpression(), s.matchExpression(st), s.getVariables());
									if(val>maxVal)
										maxVal = val;
								}
						}
						informationConcervative += info*maxVal;
					}
					for(Statement st : ins)
						informationNew += st.getInformation()+1;
					float information = informationConcervative*concervative+informationNew*(1-concervative);
					if(information>threadMaxInformation[threadId]){
						threadMaxInformation[threadId] = information;
						threadSelection.get(threadId).clear();
						threadSelection.get(threadId).add(kp);
						threadSelectionImplemented[threadId] = informationNew;
					}
					else if(information==threadMaxInformation[threadId] && informationNew<threadSelectionImplemented[threadId]){
						threadMaxInformation[threadId] = information;
						threadSelection.get(threadId).clear();
						threadSelection.get(threadId).add(kp);
						threadSelectionImplemented[threadId] = informationNew;
					}
					else if(information==threadMaxInformation[threadId])// && informationNew==threadSelectionImplemented[threadId] && kp!=null)
						threadSelection.get(threadId).add(kp);
					if(concervative<=1)
						log("Comparing <i>"+p.getName()+"</i> and <i>"+kp.getName()+"</i>"+lib+": "+Math.round(information*10)/10.0+" bits ("+Math.round(concervative*100)+"% * "+Math.round(informationConcervative*10)/10.0+" bits to remove + "+Math.round(100-concervative*100)+"% * "+Math.round(informationNew*10)/10.0+" bits to add)", 2);
					else
						log("Comparing <i>"+p.getName()+"</i> and <i>"+kp.getName()+"</i>"+lib+": "+Math.round(information*10)/10.0+" bits ("+Math.round(concervative*100)+"% * "+Math.round(informationConcervative*10)/10.0+" bits to remove - "+Math.abs(Math.round(100-concervative*100))+"% * "+Math.round(informationNew*10)/10.0+" bits to add)", 2);					
				}
			}
			}
			};
		}
		for(int i=0;i<threads;i++)
			th[i].start();
		//join threads
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
		
		///merge results to global max
		for(int i=0;i<threads;i++){
			if(threadMaxInformation[i]>maxInformation){
				select.clear();
				select.addAll(threadSelection.get(i));
				maxInformation = threadMaxInformation[i];
				selectionImplemented = threadSelectionImplemented[i];
			}
			else if(threadMaxInformation[i]==maxInformation && threadSelectionImplemented[i]<selectionImplemented){
				select.clear();
				select.addAll(threadSelection.get(i));
				maxInformation = threadMaxInformation[i];
				selectionImplemented = threadSelectionImplemented[i];
			}
			else if(threadMaxInformation[i]==maxInformation && threadSelectionImplemented[i]==selectionImplemented){
				select.addAll(threadSelection.get(i));
			}
		}
		
		Problem selection = Main.chooseBetweenProblems(select);

		setLogLevel(prevLogLevel);
		if(selection!=null)
		{
			log("Selected <i>"+selection.getName()+"</i> to merge ("+Math.round(maxInformation*10)/10.0+" bits of information)", 0);
			Problem kp = selection;
			ArrayList<Statement> comm = commonNonTrivial(p.statements, kp.statements);
			//2.2 remove common expressions between p and kp
			if(logLevel>=3)
				log("Removing common expressions between <i>"+p.getName()+"</i> and <i>"+kp.getName()+"</i>", 2);
			pendingStatements = diff(pendingStatements, comm);
			//2.3 synchronize variables of common statements
			if(logLevel>=3)
				log("Synchronizing variables between <i>"+p.getName()+"</i> and <i>"+kp.getName()+"</i>", 2);
			ArrayList<Statement> sync = commonVariableSync(p.statements, kp.statements);
			if(logLevel>=3)
				log("Merging synchronized variables into <i>"+getName()+"</i>", 2);
			pendingStatements = union(pendingStatements, sync);
			//2.4a Find the names of the variables in kp that were synchronized
			if(logLevel>=3)
				log("Merging into <i>"+getName()+"</i> equivalent expressions from <i>"+kp.getResult().getName()+"</i>", 2);
			String commVar = "";
			for(Statement c : sync){
				if(!commVar.isEmpty())
					commVar += " ";
				/*if(c.isAssignment())
					commVar += diffVariables(c.getVariables(), c.getTrivialVariable(), " ");
				*/
				else
					commVar += c.getVariables();
			}
			//2.4b&c Add all expressions in the result of kp that affect those variables
			if(!commVar.isEmpty()){
				if(logLevel>=3)
					log("Important variables: "+commVar, 3);
				pendingStatements = union(pendingStatements, getAffectedFromVariables(kp.getResult().statements, commVar));
			}
		}
		else{
			log("Nothing to merge", 0);
			//solving non-assignments that affect outputs
			if(logLevel>=3)
				log("Find <i>"+getName()+"</i> expressions with non-output assignment that need to be solved", 1);
			/*for(int i=0;i<pendingStatements.size();i++){
				if(!pendingStatements.get(i).isInput()
					&& !pendingStatements.get(i).isOutput()
					&& !pendingStatements.get(i).isIterator()
					&& pendingStatements.get(i).isAssignment("", inputs)
					&& !pendingStatements.get(i).isSourceCode()){
					log("Expression <i>"+pendingStatements.get(i).getExpression()+"</i> must be solved", 3);
					pendingStatements.set(i, new Statement(pendingStatements.get(i).getVariables(), unknownMessage+pendingStatements.get(i).getExpression()));
				}
			}*/
			for(Statement s : pendingStatements)
				if(!s.isInput() && !s.isOutput() && !s.isIterator()){
					s = new Statement(s.getVariables(), s.getExpression());
					s.removeUnusedVariables();
					s.removeDublicateVariables();
					statements.add(s);
				}
			if(breakdownStatements)
				collapse(breakdownAlsoCode, "");
			if(isSolved())
				log("Solved", 2);
			else
				log("Incomplete solution", 2);
			return;
		}
		/**
		 * 3. POST-PROCESSING
		 * 3.1 Remove iterators (this causes loss of causality, but code is not implementable otherwise)
		 * 3.2 Eliminate synchronizations by replacing variables with their equal
		 *     (Note for debugging: the order on which eliminations are performed can cause inputs and outputs
		 *      to be somehow missing)
		 *     During this elimination, dublicate assignments are also eliminated. 
		 * 3.3 Remove statements that don't affect the output variables
		 */
		//3.0 copy statements in order to prevent data loss
		for(int i=0;i<pendingStatements.size();i++)
			pendingStatements.set(i, new Statement(pendingStatements.get(i).getVariables(),
					                               pendingStatements.get(i).getExpression()));

		//3.0 add synchronizations to shorten variable names
		if(logLevel>=3)
			log("Renaming variables in <i>"+getName()+"</i>", 1);
		String variables = "";
		for(Statement st : pendingStatements)
			if(st.variables.size()!=0){
				if(!variables.isEmpty())
					variables += " ";
				variables += st.getVariables();
			}
		for(String var : variables.split(" ")){
			boolean found = false;
			for(Statement s : pendingStatements){
				if(s.isAssignment() && s.isTransitional() && s.hasVariable(var)){
					found = true;
					break;
				}
			} 
			if(found)
				continue;
			String nextVar = var;
			while(nextVar.startsWith("_")){
				nextVar = nextVar.substring(1);
				int next = nextVar.indexOf("_");
				nextVar = nextVar.substring(next+1);
			}
			if(nextVar.compareTo(var)!=0){
				int inc = 0;
				String holdVar = nextVar;
				while((" "+variables+" ").contains(" "+nextVar+" ")){
					nextVar = holdVar+inc;
					inc++;
				}
				Statement st = new Statement(nextVar+" "+var, nextVar+" = "+var);
				log("Added <i>"+st.getExpression()+"</i>", 3);
				pendingStatements.add(st);
			}
		}
		
		//3.1 remove iterators
		if(logLevel>=3)
			log("Removing <i>"+getName()+"</i> iterators", 1);
		for(int i=0;i<pendingStatements.size();i++)
			if(pendingStatements.get(i).isIterator()){
				for(String var : pendingStatements.get(i).variables){
					pendingStatements.add(0, new Statement(var, var+" = 0"));
					i++;
				}
				log("Removed <i>"+pendingStatements.get(i).getExpression()+"</i> (WARNING: causality may be lost)", 3);
				pendingStatements.remove(i);
				i--;
			}
		//3.2a eliminate synchronizations
		if(logLevel>=3)
			log("Code prototype for <i>"+getName()+"</i>", 1);
		for(Statement s : pendingStatements)
			log("<i>"+s.toHTML()+"</i>", 3);
		/*//removing non-trivial statements
		for(int i=0;i<pendingStatements.size();i++)
			if(!pendingStatements.get(i).isTrivial()){
				pendingStatements.remove(i);
				i--;
			}*/
		if(logLevel>=3)
			log("Peforming eliminations on generated code for <i>"+getName()+"</i>", 1);
		for(int i=0;i<pendingStatements.size();i++){
			//remove tautology
			if(pendingStatements.get(i).isAssignment() && pendingStatements.get(i).variables.size()==2){
				if(pendingStatements.get(i).variables.get(0).compareTo(pendingStatements.get(i).variables.get(1))==0){
					log("Eliminated tautology <i>"+pendingStatements.get(i).getExpression()+"</i>", 3);
					pendingStatements.remove(i);
					i--;
					continue;
				}
			}
			
			//3.2b eliminate duplicate
			boolean eliminate = false;
			if(pendingStatements.get(i).isTransitional())
				for(Statement s : pendingStatements){
					if(s!=pendingStatements.get(i) && pendingStatements.get(i).isIdentical(s)){
						eliminate = true;
						break;
					}
				}
			if(eliminate){
				log("Eliminated dublicate <i>"+pendingStatements.get(i).getExpression()+"</i>", 3);
				pendingStatements.remove(i);
				i--;
				continue;
			}
			if(pendingStatements.get(i).isTransitional() && pendingStatements.get(i).isAssignment(outputs, inputs)){
				log("Inverting assignment <i>"+pendingStatements.get(i).getExpression()+"</i>", 3);
				pendingStatements.get(i).inverse();
			}
			
			//substitute
			if(pendingStatements.get(i).isTransitional() && pendingStatements.get(i).isAssignment() && !pendingStatements.get(i).isAssignment(inputs+" "+outputs, outputs)){
				boolean found = false;
				for(Statement s : pendingStatements){
					if(s!=pendingStatements.get(i)){
						if(s.replace(pendingStatements.get(i).variables.get(1), pendingStatements.get(i).variables.get(0))){
							found = true;
							//break;//DEBUGGING: UNSURE WEATHER THIS break IS CORRECT OR NOT
						}
					}
				}
				if(found){
					log("Substituted <i>"+pendingStatements.get(i).getExpression()+"</i>", 3);
					pendingStatements.remove(i);
					i = 0;//do them from scratch
				}
			}
		}
		
		//3.3 remove statements that donnot affect the output
		if(logLevel>=3)
			log("Removing statements from <i>"+getName()+"</i> not affecting its outputs: <i>"+outputs+"</i>", 1);
		pendingStatements = getAffectedFromVariables(pendingStatements, outputs);
		//3.4 remove statements unaffected from inputs
		/*if(logLevel>=3)
			log("Removing statements from <i>"+getName()+"</i> with variables that appear only once", 1);
		Statement tempStatement = new Statement(inputs+" "+outputs, "any");
		pendingStatements.add(tempStatement);
		pendingStatements = removeFloatingStatements(pendingStatements);
		pendingStatements.remove(tempStatement);*/
	
		//solving non-assignments that affect outputs
		if(logLevel>=3)
			log("Find <i>"+getName()+"</i> expressions with non-output assignment that need to be solved", 1);
		for(int i=0;i<pendingStatements.size();i++){
			if(   !pendingStatements.get(i).isAssignment("", outputs)
			   && !pendingStatements.get(i).isSourceCode()){
				log("Expression <i>"+pendingStatements.get(i).getExpression()+"</i> must be solved", 3);
				pendingStatements.set(i, new Statement(pendingStatements.get(i).getVariables(), unknownMessage+pendingStatements.get(i).getExpression()));
			}
		}
		
		//add pending statements to statements according to assignment order
		if(logLevel>=3)
			log("Rearranging <i>"+getName()+"</i> statements", 1);
		while(!pendingStatements.isEmpty()){
			Statement s = pendingStatements.get(0);
			int startSearch = 1;
			while(startSearch<pendingStatements.size()){
				int minFound = pendingStatements.size();
				for(int i=startSearch;i<pendingStatements.size();i++){
					for(String variable : s.variables){
						if(!s.containPerfectAssignmentTo(variable) && pendingStatements.get(i).containsAssignmentTo(variable))
							if(i<minFound)
								minFound = i;
					}
				}
				if(minFound<pendingStatements.size()){
					if(logLevel>=3)
						log("<b>Statement</b>", 2);
					log("<i>"+s.toHTML()+"</i>", 3);
					s = pendingStatements.get(minFound);
					log("<b>Must be preceeded by</b>", 3);
					log("<i>"+s.toHTML()+"</i>", 3);
					startSearch = minFound+1;
				}
				else
					startSearch = pendingStatements.size();
			}
			statements.add(new Statement(s.getVariables(), s.getExpression()));
			pendingStatements.remove(s);
		}

		prevLogLevel = logLevel;
		logLevel = 3;
		log("Final code for <i>"+getName()+"</i>", 1);
		for(Statement s : statements)
			log("<i>"+s.toHTML()+"</i>", 3);
		logLevel = prevLogLevel;
		
		
		if(isSolved())
			log("Solved", 0);
		else
			log("Failed", 0);
	}
	
	/**
	 * <h1>commonNonTrivial</h1>
	 * This functions detects all common non-trivial statements between two sets of statements. To check
	 * weather two statements are the same, the function <code>Statement.isSame</code> is used. Only statements
	 * for which <code>Statement.isTrivial</code> returns false are checked. This function reports to the
	 * log record.
	 * @param v1 : the first set of statements
	 * @param v2 : the second set of statements
	 * @return an ArrayList that is the set of common non-trivial statements
	 */
	public static ArrayList<Statement> commonNonTrivial(ArrayList<Statement> v1, ArrayList<Statement> v2){
		ArrayList<Statement> ret = new ArrayList<Statement>();
		for(Statement s2 : v2){
			if(!s2.isTrivial())
			for(Statement s1 : v1)
				if(s1.isSame(s2)){
					//ret.add(new Statement(s1.getVariables(), s2.matchExpression(s1)));
					ret.add(s1);
					log("Found common non-trivial expressions <i>"+s1.toHTML()+"</i> and <i>"+s2.toHTML()+"</i>", 3);
					break;
				}
				//else
					//log("NOT <i>"+s1+"<i> and <i>"+s2+"<i>", 3);
		}
		return ret;
	}

	/**
	 * <h1>common</h1>
	 * This functions detects all common statements between two sets of statements. To check weather two
	 * statements are the same, the function <code>Statement.isSame</code> is used. This function reports
	 * to the log record.
	 * @param v1 : the first set of statements
	 * @param v2 : the second set of statements
	 * @return v1 intersect v2
	 */
	public static ArrayList<Statement> common(ArrayList<Statement> v1, ArrayList<Statement> v2){
		ArrayList<Statement> ret = new ArrayList<Statement>();
		for(Statement s2 : v2){
			for(Statement s1 : v1)
				if(s1.isSame(s2)){
					//ret.add(new Statement(s1.getVariables(), s2.matchExpression(s1)));
					ret.add(s1);
					log("Found common expressions <i>"+s1.toHTML()+"</i> and <i>"+s2.toHTML()+"</i>", 3);
					break;
				}
		}
		return ret;
	}
	
	/**
	 * <h1>commonVariableSync</h1>
	 * This functions detects all common non-trivial statements between two sets of statements and synchronizes
	 * their variables. To check weather two statements are the same, the function <code>Statement.isSame</code>
	 * is used. This function generates a list of statements that equate one for one the variables between 
	 * identical statements. Results are reported to the log record.
	 * @param v1 : the first set of statements
	 * @param v2 : the second set of statements
	 * @return an ArrayList of assignment statements that synchronize variables from the first set of statements
	 * with variables from the second set of statements
	 */
	public ArrayList<Statement> commonVariableSync(ArrayList<Statement> v1, ArrayList<Statement> v2){
		ArrayList<Statement> ret = new ArrayList<Statement>();
		for(Statement s2 : v2){
			if(!s2.isTrivial())
			for(Statement s1 : v1)
				if(s1.isSame(s2)){
					for(int i=0;i<s2.variables.size();i++)
						if(s1.variables.get(i).compareTo(s2.variables.get(i))!=0){
							Statement exp = new Statement(s1.variables.get(i)+" "+s2.variables.get(i), s1.variables.get(i)+" = "+s2.variables.get(i));
							boolean exists = false;
							for(Statement r : ret)
								if(r.getExpression().compareTo(exp.getExpression())==0)
									exists = true;
							if(!exists){
								ret.add(exp);
								log("Expression to sync <i>"+ret.get(ret.size()-1)+"</i>", 3);
							}
						}
					break;
				}
		}
		return ret;
	}
	/**
	 * <h1>diff</h1>
	 * This functions removes all common statements between two sets of statements from the first statement.
	 * To check weather two statements are the same, the function <code>Statement.isSame</code> is used.
	 * This function reports to the log record.<br/>
	 * This function <b>uses the <code>isIdentical</code> method for comparison</b> (which is stricter
	 * than <code>isSame</code> in case of transitional operators (i.e. &lt,&gt,&lt=,&gt=).
	 * @param v1 : the first set of statements
	 * @param v2 : the second set of statements
	 * @return v1-v2
	 * @deprecated this function also performs a check for variables to be exactly the same
	 */
	public static ArrayList<Statement> diff(ArrayList<Statement> v1, ArrayList<Statement> v2){
		ArrayList<Statement> ret = new ArrayList<Statement>();
		for(Statement s1 : v1){
			boolean exists = false;
			for(Statement s2 : v2)
				if(s2.isIdentical(s1) && s2.getVariables().compareTo(s1.getVariables())==0){
					exists = true;
					break;
				}
			if(!exists)
				ret.add(s1);
			else if(!s1.isTrivial())
				log("Removed expression: <i>"+s1.toHTML()+"</i>", 3);
		}
		return ret;
	}
	
	/**
	 * <h1>unionVariables</h1>
	 * This function adds to the first set of variables all variables from the second set that
	 * did not previously exist in the first set.
	 * @param v1 : the first set of variables as a String
	 * @param v2 : the second set of variables as a String
	 * @param seperator : the separator between variables (must be the same for v1, v2 and will be applied to the result)
	 * @return the union of variables from v1 and variables from v2
	 */
	public static String unionVariables(String v1, String v2, String separator){
		ArrayList<String> list1 = new ArrayList<String>(Arrays.asList(v1.split("\\s*"+separator+"\\s*")));
		ArrayList<String> list2 = new ArrayList<String>(Arrays.asList(v2.split("\\s*"+separator+"\\s*")));
		String ret = v1;
		for(String var : list2){
			boolean found = false;
			for(String check : list1)
				if(check.compareTo(var)==0){
					found = true;
					break;
				}
			if(found)
				continue;
			list1.add(var);
			if(!ret.isEmpty())
				ret += separator;
			ret += var;
		}
		return ret;
	}
	
	/**
	 * <h1>diffVariables</h1>
	 * This function removes from the first set all variables in the second set.
	 * @param v1 : the first set of variables as a String
	 * @param v2 : the second set of variables as a String
	 * @param seperator : the separator between variables (must be the same for v1, v2 and will be applied to the result)
	 * @return v1-v2
	 */
	public static String diffVariables(String v1, String v2, String separator){
		ArrayList<String> list1 = new ArrayList<String>(Arrays.asList(v1.split("\\s*"+separator+"\\s*")));
		ArrayList<String> list2 = new ArrayList<String>(Arrays.asList(v2.split("\\s*"+separator+"\\s*")));
		String ret = "";
		for(String var : list1){
			boolean found = false;
			for(String check : list2)
				if(check.compareTo(var)==0){
					found = true;
					break;
				}
			if(found)
				continue;
			if(!ret.isEmpty())
				ret += separator;
			ret += var;
		}
		return ret;
	}
	
	/**
	 * <h1>union</h1>
	 * This functions creates a set that contains all statements from two list of statements, but without
	 * allowing for the same statement twice. To check weather two statements are the same, the function
	 * <code>Statement.isSame</code> is used. This function reports to the log record all statements from
	 * the second set of statements that were added to the result.
	 * @param v1 : the first set of statements
	 * @param v2 : the second set of statements
	 * @return v1 union v2
	 * @deprecated this function also performs a check for variables to be exactly the same
	 */
	public static ArrayList<Statement> union(ArrayList<Statement> v1, ArrayList<Statement> v2){
		ArrayList<Statement> ret = new ArrayList<Statement>(v1);
		for(Statement s2 : v2){
			boolean exists = false;
			for(Statement s1 : v1)
				if(s1.isSame(s2) && s1.getVariables().compareTo(s2.getVariables())==0){
					exists = true;
					break;
				}
			if(!exists){
				ret.add(s2);
				log("Added expression: <i>"+s2.toHTML()+"</i>", 3);
			}
		}
		return ret;
	}
	
	/**
	 * <h1>getAffectedFromVariables</h1>
	 * This functions creates a set of statements that affect, all of whose members contain at least one of the given
	 * variables. This function also checks for indirect variable dependencies by performing a variation of Kruskal's
	 * algorithm for detecting forests in graphs. In particular, this function only detects the tree of statements that
	 * is connected with the given variables.
	 * @param candidates : a list of statements to check with
	 * @param vars : a String of variable names separated by space
	 * @return a set of statements that affect the given variables
	 */
	public static ArrayList<Statement> getAffectedFromVariables(ArrayList<Statement> candidates, String vars){
		//create initial pending statements and their states
		ArrayList<Statement> pendingStatements = new ArrayList<Statement>(candidates);
		ArrayList<Integer> states = new ArrayList<Integer>();
		for(int i=0;i<pendingStatements.size();i++)
			if(pendingStatements.get(i).isOutput())
				states.add(1);
			else
				states.add(0);
		//remove statements that donnot affect output
		Statement contribute = new Statement(vars, "output");
		int newVars = contribute.variables.size();
		while(newVars>0){
			newVars = 0;
			//set 1 to things contributing to current output
			for(int i=0;i<pendingStatements.size();i++){
				boolean contributesToOutput = false;
				for(String var : pendingStatements.get(i).variables)
					if(contribute.hasVariable(var)){
						contributesToOutput = true;
						break;
					}
				if(contributesToOutput)
					for(String var : pendingStatements.get(i).variables)
						if(!(" "+vars+" ").contains(var)){
							if(!vars.isEmpty())
								vars += " ";
							vars += var;
							newVars++;
						}
				if(contributesToOutput)
					states.set(i, 1);
				contribute.setVariables(vars);
			}

			//create new output
			contribute = new Statement(vars, "output");
		}
		ArrayList<Statement> ret = new ArrayList<Statement>();
		for(int i=0;i<pendingStatements.size();i++)
			if(!pendingStatements.get(i).isInput() && !pendingStatements.get(i).isOutput() && states.get(i)==1)
				ret.add(pendingStatements.get(i));
			else if(!pendingStatements.get(i).isInput() && !pendingStatements.get(i).isOutput())
				log("Removed expression: <i>"+pendingStatements.get(i).toHTML()+"</i>", 3);
		return ret;
	}
	
	/**
	 * <h1>getAffectingVariables</h1>
	 * This functions creates a set of statements that result to the given variables having
	 * a particular assignment (a search for '<code>&lt variable name&gt = </code>' is performed.
	 * @param candidates : a list of statements to check with
	 * @param vars : a String of variable names separated by space
	 * @return a set of statements that affect the given variables
	 */
	public static ArrayList<Statement> getAffectingVariables(ArrayList<Statement> candidates, String vars){
		//create initial pending statements and their states
		ArrayList<Statement> pendingStatements = new ArrayList<Statement>(candidates);
		ArrayList<Integer> states = new ArrayList<Integer>();
		for(int i=0;i<pendingStatements.size();i++)
			if(pendingStatements.get(i).isOutput())
				states.add(1);
			else
				states.add(0);
		//remove statements that donnot affect output
		Statement contribute = new Statement(vars, "input");
		int newVars = contribute.variables.size();
		while(newVars>0){
			newVars = 0;
			//set 1 to things contributing to current output
			for(int i=0;i<pendingStatements.size();i++){
				boolean contributesToInput = false;
				for(String var : pendingStatements.get(i).variables)
					if(contribute.hasVariable(var) && contribute.getExpression().contains(var+" = ")){
						contributesToInput = true;
						break;
					}
				if(contributesToInput)
					for(String var : pendingStatements.get(i).variables)
						if(!contribute.hasVariable(var)){
							if(!vars.isEmpty())
								vars += " ";
							vars += var;
							newVars++;
						}
				if(contributesToInput)
					states.set(i, 1);
				contribute.setVariables(vars);
			}

			//create new output
			contribute = new Statement(vars, "input");
		}
		ArrayList<Statement> ret = new ArrayList<Statement>();
		for(int i=0;i<pendingStatements.size();i++)
			if(!pendingStatements.get(i).isInput() && !pendingStatements.get(i).isOutput() && (states.get(i)==1 || pendingStatements.get(i).hasVariable(vars)))
				ret.add(pendingStatements.get(i));
		return ret;
	}
	
	/**
	 * <h1>removeFloatingStatements</h1>
	 * This function detects and removes statements with floating variables. Floating variables don't connect
	 * statements together have been created due to necessary over-training.
	 * @param candidates : the set of statements
	 * @return a subset of the given set of statements whose statements' variables appear at least 2 times
	 */
	public static ArrayList<Statement> removeFloatingStatements(ArrayList<Statement> candidates){
		//create initial pending statements and their states
		ArrayList<Statement> pendingStatements = new ArrayList<Statement>(candidates);
		HashMap<String, Integer> variableCount = new HashMap<String, Integer>();
		for(int i=0;i<pendingStatements.size();i++){
			for(String var : pendingStatements.get(i).variables){
				if(variableCount.get(var)==null)
					variableCount.put(var, 1);
				else
					variableCount.put(var, variableCount.get(var)+1);
			}
		}
		//remove statements which have at least one floating variable (floating variables are found exactly one time)
		ArrayList<Statement> ret = new ArrayList<Statement>();
		for(int i=0;i<pendingStatements.size();i++){
			boolean hasFloating = false;
			for(String var : pendingStatements.get(i).variables){
				if(variableCount.get(var)==1){
					hasFloating = true;
					break;
				}
			}
			if(hasFloating)
				log("Removed expression: <i>"+pendingStatements.get(i).toHTML()+"</i>", 3);
			else
				ret.add(pendingStatements.get(i));
		}
		return ret;
	}
	
	/**
	 * <h1>save</h1>
	 * Saves the problem to a designated XML element of an XML document.
	 * @param e : the XML element
	 * @param doc : the XML document (must contain the ZML element)
	 */
	void save(Element e, Document doc){
		Element nameElement = doc.createElement("name");
		nameElement.appendChild(doc.createTextNode(name));
		e.appendChild(nameElement);
		Element libraryElement = doc.createElement("library");
		libraryElement.appendChild(doc.createTextNode(library));
		e.appendChild(libraryElement);
		Element thisElement = doc.createElement("functionMemberVariable");
		thisElement.appendChild(doc.createTextNode(functionMemberVariable));
		e.appendChild(thisElement);
		for(Statement s : statements){
			Element state = doc.createElement("statement");
			s.save(state, doc);
			e.appendChild(state);
		}
	}
	
	/**
	 * <h1>load</h1>
	 * Loads the problem from an XML element.
	 * @param e : an XML element
	 */
	void load(Element e){
		statements = new ArrayList<Statement>();
		statements.clear();
		name = e.getElementsByTagName("name").item(0).getTextContent();
		library = e.getElementsByTagName("library").item(0).getTextContent();
		functionMemberVariable = e.getElementsByTagName("functionMemberVariable").item(0).getTextContent();
		NodeList elementList = e.getElementsByTagName("statement");
		for(int i=0;i<elementList.getLength();i++)
			statements.add(new Statement((Element)elementList.item(i)));
	}
	
	/**
	 * <h1>getOutput</h1>
	 * @return a String of all output variable names separated by space
	 */
	public String getOutput(String functionMemberVariable){
		String output = "";
		for(Statement s : statements)
			if(s.isOutput()){
				if(output.length()>0)
					output += " ";
				output += s.getVariables();
			}
			else if(!functionMemberVariable.isEmpty()){
				if(s.isInput() && s.hasVariable(functionMemberVariable)){
					if(output.length()>0)
						output += " ";
					output += functionMemberVariable;
				}
			}
		return output;
	}
	
	/**
	 * <h1>getInput</h1>
	 * @return a String of all input variable names separated by space
	 */
	public String getInput(){
		String input = "";
		for(Statement s : statements)
			if(s.isInput()){
				if(input.length()>0)
					input += " ";
				input += s.getVariables();
			}
		return input;
	}

	/**
	 * <h1>getIrerator</h1>
	 * @return a String of all iterator variable names separated by space
	 */
	public String getIterator(){
		String iter = "";
		for(Statement s : statements)
			if(s.isIterator()){
				if(iter.length()>0)
					iter += " ";
				iter += s.getVariables();
			}
		return iter;
	}
	
	/**
	 * <h1>getVariables</h1>
	 * @return a String of all variable names separated by space
	 */
	public String getVariables(){
		String vars = "";
		for(Statement s : statements)
			vars = unionVariables(vars, s.getVariables(" "), " ");
		return vars;
	}
	
	/**
	 * <h1>affectsOutput</h1>
	 * This function uses the static function <code>getAffectedFromVariables</code> to check
	 * weather a given statement affects (even indirectly) the problem's outputs variables.
	 * @param s : the given statement
	 * @return <code>true</code> if the given statement affect the problem's output variables
	 */
	public boolean affectsOutput(Statement s) {
		if(s.isInput() || s.isOutput())
			return true;
		ArrayList<Statement> list = getAffectedFromVariables(statements, getOutput(functionMemberVariable));
		for(Statement sl : list)
			if(sl==s)
				return true;
		return false;
	}

	/**
	 * <h1>isSame</h1>
	 * @param query : the problem to check with
	 * @return true if both problems produce exactly the same code
	 */
	public boolean isSame(Problem query) {
		return query.getCode().compareTo(getCode())==0;
	}
	

	/**
	 * <h1>getNonClassProblem</h1>
	 * This function generates a copy of the problem without any <code>this.</code>
	 * statements or <code>this</code> variables in its expression. If the copy
	 * would be the same as the original problem <code>null</code> is returned.<br/>
	 * This function also generates the appropriate resulting subproblems.
	 * @return a copy of the problem without class references, null if no class references
	 */
	public Problem getNonClassProblem(){
		if(functionMemberVariable.isEmpty())
			return null;
		boolean isClassMember = false;
		for(Statement s : statements)
			if(s.isInput() && s.hasVariable(functionMemberVariable))
				isClassMember = true;
		if(!isClassMember)
			return null;
		Problem p = new Problem(getName()+"_declass");
		for(Statement s : statements)
			p.statements.add(new Statement(
					diffVariables(s.getVariables(), functionMemberVariable, " "),
					s.getExpression().replace(functionMemberVariable+".", ""))  );
		if(getResult()!=null){
			p.setResult(getResult().getNonClassProblem());
			log("Created de-classed instance for <i>"+getName()+"</i>",2);
		}
		p.setLibrary(getLibrary());
		return p;
	}
	
	/**
	 * <h1>getSuproblem</h1>
	 * This function performs a variation of <i>Kruskal's</i> algorithm in order to
	 * generate a list of all the problem's subproblems. Subproblems should not
	 * have depending variables between them (iterators and inputs are considered
	 * dependent on nothing).<br/>
	 * This function also generates the appropriate resulting subproblems.
	 * @return a list of all the problem's subproblems
	 */
	public ArrayList<Problem> getSubproblems(){
		ArrayList<Problem> subproblems = new ArrayList<Problem>();
		HashMap<Statement, Integer> group = new HashMap<Statement, Integer>();
		HashMap<Statement, Boolean> inThis = new HashMap<Statement, Boolean>();
		ArrayList<Statement> allStatements = new ArrayList<Statement>();
		allStatements.addAll(this.statements);
		if(getResult()!=null)
			allStatements.addAll(getResult().statements);
		int changes = 0;
		for(Statement st : allStatements){
			group.put(st, changes);
			changes++;
		}
		for(Statement st : statements)
			inThis.put(st, true);
		if(getResult()!=null)
			for(Statement st : getResult().statements)
				inThis.put(st, false);
		
		String ignoreVariables = unionVariables(getInput(), getIterator(), " ");
		//System.out.println("Ignoring: "+ignoreVariables);
		while(changes!=0){
			changes = 0;
			for(Statement st : allStatements)
			if(!st.isInput() && !st.isOutput())
				for(Statement st_test : allStatements)
				if(!st_test.isInput() && !st_test.isOutput())
					if((int)group.get(st)!=(int)group.get(st_test)
					    && (  st_test.hasVariable(diffVariables(st.getVariables(), ignoreVariables, " "))
					    	|| diffVariables(st.getVariables(), getInput(), " ").isEmpty()
					       )
					  )
					{
						int prev = group.get(st_test);
						for(Statement temp : allStatements)
							if(group.get(temp)==prev){
								group.put(temp, group.get(st));
								//System.out.println("Merged groups of "+st.getExpression()+" and "+st_test.getExpression());
							}
						changes++;
					}
			//System.out.println("Changes: "+changes);
		}
		for(int i=0;i<allStatements.size();i++){
			ArrayList<Statement> groupStatements = new ArrayList<Statement>();
			for(Statement st : allStatements)
				if(group.get(st)==i && !st.isInput() && !st.isOutput() && !st.isIterator())
					groupStatements.add(st);
			if(!groupStatements.isEmpty()){
				Problem p = new Problem(getName()+"__"+subproblems.size());//use __ instead of _ in order to be able to remove things later
				p.statements.add(new Statement(getOutput(""), "output"));
				p.statements.add(new Statement(getInput(), "input"));
				for(Statement st : groupStatements)
					if(inThis.get(st))
						p.statements.add(st);
				if(getResult()!=null){
					String subproblemVars = "";
					for(Statement st : groupStatements){
						if(!subproblemVars.isEmpty())
							subproblemVars += " ";
						subproblemVars += st.getVariables();
					}
					//System.out.println("Subproblem variables: "+subproblemVars);
					subproblemVars = diffVariables(subproblemVars, ignoreVariables, " ");
					Problem res = new Problem(getResult().getName()+"_"+subproblems.size());
					res.statements.add(new Statement(getOutput(""), "output"));
					res.statements.add(new Statement(getInput(), "input"));
					for(Statement st : getAffectingVariables(getResult().statements, subproblemVars))
						if(!st.isInput() && !st.isOutput()){
							res.statements.add(st);
						}
					p.setResult(res);
					//System.out.println(p.getComments("", ""));
					//System.out.println(res.getComments("", ""));
				}
				p.setLibrary(getLibrary());
				subproblems.add(p);
			}
		}
		log("Created "+subproblems.size()+" subproblem(s) for <i>"+getName()+"</i>",2);
		return subproblems;
	}

	public void setName(String name) {
		this.name = name;
	}

	/**
	 * <h1>refresh</h1>
	 * This resets each statement's variables and expression, in order for changes
	 * in things like <code>Statement.separators</code> to take place.<br/>
	 * It also detects statements that should be split into smaller ones due
	 * to logical 'and's.
	 */
	public void refresh() {
		//remove unused variables
		for(Statement s : statements){
			if(s.isInput() || s.isOutput() || s.isIterator())
				continue;
			s.setExpression(s.getExpression());
			s.removeUnusedVariables();
		}
		
		//split comment statements
		for(int i=0;i<statements.size();i++){
			Statement st = statements.get(i);
			String expr = st.getExpression();
			if(!st.isSourceCode())
			for(String spl : Statement.languageAnd){
				if(expr.contains(" "+spl+" ")){
					String vars = st.getVariables();
					String[] expressions = expr.split("\\s+"+spl+"\\s+");
					statements.remove(i);
					String prevVariables = "";
					for(String e : expressions){
						Statement s = new Statement(vars, e);
						s.removeUnusedVariables();
						String addVar = "";
						for(String newVar : prevVariables.split(" "))
							if(!s.hasVariable(newVar)){
								if(!addVar.isEmpty())
									addVar += " ";
								addVar += newVar;
							}
						s.setVariables((addVar+" "+s.getVariables()).trim());
						s.setExpression((addVar+" "+s.getExpression()).trim());
						prevVariables = unionVariables(prevVariables, s.getVariables(), " ");
						statements.add(i, s);
						i++;
					}
					break;
				}
			}
		}
	}
	
	/**
	 * <h1>expand</h1>
	 * This function splits statements by replacing non-variable strings in parenthesis and brackets
	 * with a temporary variable. A prefix for those new variables can also be given.
	 * @param prefix : a prefix to add to all temporary variables
	 * @param allowSourceCodeExpansion : false to <i>disable</i> interaction with source code statements
	 */
	public void expand(String prefix, boolean allowSourceCodeExpansion){
		//replace constants with variables
		if(constantsAreVariables)
		{
			Pattern pattern = Pattern.compile("[\\(,\\s,\\n\\[,=,\\>,\\<,\\,][0-9]+[\\),\\s,\\n\\],=,\\>,\\<,\\,]"); 
			for(Statement s : statements){
				boolean found  = false;
				Matcher matcher = pattern.matcher(" "+s.getExpression()+" ");
				while(matcher.find()){
					String number = matcher.group();
					number = number.substring(1, number.length()-1);
					if(!s.variables.contains(number))
						s.variables.add(number);
				}
				if(found)
					s.setExpression(s.getExpression());
			}
		}
		//remove variables from statements
		String splitExpression = "";
		for(int s=0;s<Statement.separators.length;s++){
			String sep = Statement.separators[s];
			if(!splitExpression.isEmpty())
				splitExpression += "|";
			if(!sep.isEmpty()){
				splitExpression += "("+Pattern.quote(sep)+")";
			}
		}
		splitExpression = "("+splitExpression+")";
		//System.out.println(splitExpression);
		int i = 0;
		int varCount = 0;
		String varsAdded = "";
		while(i<statements.size()){
			int added = 0;
			if(allowSourceCodeExpansion || !statements.get(i).isSourceCode()){
				//System.out.println(""+i+" / "+statements.get(i));
				String expr = statements.get(i).getExpression();
				String vars = statements.get(i).getVariables();
				int first = expr.length();
				int fi = expr.indexOf('(');
				if(fi!=-1 && fi<first)
					first = fi;
				fi = expr.indexOf('[');
				if(fi!=-1 && fi<first)
					first = fi;
				if(first==expr.length())
					first = -1;
				if(first>=0){
					int level = 0;
					int last = -1;
					for(int l=first;l<expr.length();l++){
						if(expr.charAt(l)=='(' || expr.charAt(l)=='['){
							level++;
							if(level==1)
								first = l;
						}
						if(expr.charAt(l)==',')
							if(level==1){
								last = l;
								if(varsAdded.contains(" "+expr.substring(first+1, last).trim()+" ")){
									first = last+1;
									last = -1;
								}
								else
									break;
							}
						if(expr.charAt(l)==')' || expr.charAt(l)==']'){
							level--;
							if(level==0){
								last = l;
								if(expr.substring(first+1, last).startsWith(prefix+"_temp")){
									first = last;
									last = -1;
								}
								else
									break;
							}
						}
					}
					//TO-DO: fix this so that (x+y)+(w+z) does not yield "x+y)+(w+z"
					if(first>=expr.length()){
						i++;
						continue;
					}
					if(last==-1)
						last = expr.length();
					String nextVars = "";
					String exp = expr;
					//Statement prev = statements.get(i);
					String prevVariables = "";
					for(Statement st : statements)
						prevVariables += st.getVariables("\n")+"\n";
					String splits [];
					if(statements.get(i).isSourceCode()){
						//splits = new String[1];
						//splits[0] = exp.substring(exp.indexOf(':')+1);
						splits = exp.substring(exp.indexOf(':')+1).split("\n");
					}
					else
						splits = exp.substring(first+1, last).split(splitExpression);
					for(String repl : splits)
						if(!repl.trim().isEmpty() && !prevVariables.contains(repl.trim())){//!prev.hasVariable(repl.trim())){
							repl = repl.trim();
							//System.out.println("Replace: "+repl);
							String nextVar = prefix+"_temp"+varCount;
							while(prevVariables.contains(nextVar)){
								varCount++;
								nextVar = prefix+"_temp"+varCount;
							}
							nextVars += " "+nextVar;
							varCount++;
							String remove = "[\\s]";
							String maintain = "[\\(\\)\\[\\]\\,^$]";

							Pattern pattern = Pattern.compile("( (?<="+maintain+")|"+remove+")*(?<!\\.)"+Pattern.quote(repl)+"(?!\\.)"+"( (?="+maintain+")|"+remove+")*");
							Matcher matcher = pattern.matcher(" "+expr+" ");
							varsAdded += " "+nextVar+" ";
							StringBuffer buffer = new StringBuffer();
							while (matcher.find()) {
								matcher.appendReplacement(buffer, "");
								buffer.append(" "+nextVar);
							}
							matcher.appendTail(buffer);
							expr = buffer.toString().trim();
							expr = expr.replace("\\s*\\(\\s*"+Pattern.quote(nextVar)+"\\s*\\)", " "+nextVar+" ");
							Statement st = new Statement(nextVar+" "+vars, nextVar+" = "+repl);
							st.removeUnusedVariables();
							statements.add(i, st);
							added++;
						}
					Statement st = new Statement(nextVars+" "+vars, expr);
					st.removeUnusedVariables();
					statements.set(i+added, st);
				}
			}
			i += 1-added;
		}
		if(result!=null)
			result.expand("", breakdownAlsoCode);
	}

	/**
	 * <h1>collapse</h1>
	 * This function performs the directly inverse operation to <code>expand</code>.
	 * @param allowSourceCodeCollapse : the corresponding value given to <code>expand</code>.
	 * @param contentFilter : only variables containing this string can be eliminated
	 */
	public void collapse(boolean allowSourceCodeCollapse, String contentFilter){
		//remove constant variables
		for(Statement s : statements){
			for(String var : new ArrayList<String>(s.variables))
				if(var.matches("[0-9]+"))
					s.variables.remove(var);
		}
		
		
		String outputs = getOutput("");
		String inputs = getInput();
		//substitute
		int lastChange = 0;
		for(int i=0;i<statements.size();i++)
			if(statements.get(i).isAssignment() && statements.get(i).getTrivialVariable().contains(contentFilter) && !(" "+outputs+" "+inputs+" ").contains(" "+statements.get(i).getTrivialVariable()+" ")){
				boolean toRemove = true;
				boolean later = false;
				//System.out.println(statements.get(i).getExpression());
				for(Statement s : statements){
					if(!s.isInput() && !s.isOutput() && s!=statements.get(i) && (allowSourceCodeCollapse || !s.isSourceCode())){
						s.setVariables(Problem.unionVariables(s.getVariables(), statements.get(i).getVariables(), " "));
						String expr = s.getExpression();
						String repl = statements.get(i).getTrivialExpression();
						if(repl.endsWith(";")){
							if(!s.isSourceCode()){
								toRemove = false;
								//later = true;
								s.removeUnusedVariables();
								continue;
							}
							else
								repl = "\n"+"   "+repl.substring(0, repl.length());
						}
						Pattern pattern = Pattern.compile("[\\s\\+\\-\\*\\/\\^\\(\\)\\[\\]\\,\\{\\}\\.\\<\\>\\;\\:\n^]"+Pattern.quote(statements.get(i).getTrivialVariable())+"[\\s\\+\\-\\*\\/\\^\\(\\)\\[\\]\\,\\{\\}\\.\\<\\>\\;\\:\n$]");
						Matcher matcher = pattern.matcher(" "+expr+" ");
						StringBuffer buffer = new StringBuffer();
						while (matcher.find()) {
							String str = matcher.group();
							matcher.appendReplacement(buffer, "");
							boolean parenthesis = true;
							switch(str.charAt(0)){
								case '(': parenthesis = false; break;
							}
							switch(str.charAt(str.length()-1)){
								case ')': parenthesis = false; break;
							}
							if((" "+s.getVariables()+" ").contains(str))
								parenthesis = false;
							if(parenthesis)
								repl = "("+repl+")";
							buffer.append(str.charAt(0)+repl+str.charAt(str.length()-1));
						}
						matcher.appendTail(buffer);
						expr = buffer.toString().trim();
						//expr = expr.replace(statements.get(i).getTrivialVariable(), repl);
						/*String pref = "";
						if(statements.get(i).isSourceCode())
							pref = "\n";
						if(s.isSourceCode())
							expr = expr.replace(statements.get(i).getTrivialVariable(), repl+"\n");
						else
							expr = expr.replace(statements.get(i).getTrivialVariable(), pref+"("+repl+")");
						expr = expr.replace("(("+repl+"))", "("+repl+")");*/
						s.setExpression(expr);
						s.removeUnusedVariables();
					}
					else
						if(s!=statements.get(i) &&  ((!allowSourceCodeCollapse && s.isSourceCode() && (" "+s.getVariables()+" ").contains(statements.get(i).getTrivialVariable())) || s.isAssignment(statements.get(i).getTrivialVariable(), ""))){
							toRemove = false;
						}
				}
				if(toRemove || later){
					Statement s = statements.get(i);
					statements.remove(i);
					i = 0;//do them from scratch
					if(later){
						statements.add(s);
						lastChange++;
					}
					else
						lastChange = 0;
					if(lastChange>=statements.size())
						break;
				}
			}
		if(result!=null)
			result.collapse(allowSourceCodeCollapse, contentFilter);
	}

	
	/**
	 * <h1>Sort</h1>
	 * This function sorts statements according to assignment dependency.
	 */
	public void sort() {
		ArrayList<Statement> pendingStatements = new ArrayList<Statement>(statements);
		statements.clear();
		while(!pendingStatements.isEmpty()){
			Statement s = pendingStatements.get(0);
			int startSearch = 1;
			while(startSearch<pendingStatements.size()){
				int minFound = pendingStatements.size();
				for(int i=startSearch;i<pendingStatements.size();i++){
					for(String variable : s.variables){
						if(!s.containPerfectAssignmentTo(variable) && pendingStatements.get(i).containsAssignmentTo(variable))
							if(i<minFound)
								minFound = i;
					}
				}
				if(minFound<pendingStatements.size()){
					if(logLevel>=3)
						log("<b>Statement</b>", 2);
					log("<i>"+s.toHTML()+"</i>", 3);
					s = pendingStatements.get(minFound);
					log("<b>Must be preceeded by</b>", 3);
					log("<i>"+s.toHTML()+"</i>", 3);
					startSearch = minFound+1;
				}
				else
					startSearch = pendingStatements.size();
			}
			statements.add(new Statement(s.getVariables(), s.getExpression()));
			pendingStatements.remove(s);
		}
	}
}
