package parser;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import analyser.Knowledge;
import analyser.Problem;
import analyser.Statement;

public class CodeParser {
	public static boolean enableAutoOutToInMatching = true;
	/**
	 * <h1>parseExport</h1>
	 * This function detects all problems in a knowledge pool that belong to the
	 * given library and packs them into a <i>Python</i> library.<br/>
	 * The return value of this function can be used as file contents for generating
	 * that library.
	 * @param k : a knowledge pool
	 * @param library : the library to export form the knowledge pool
	 * @return a String representation for the generated <i>Python</i> library
	 */
	public static String parseExport(Knowledge k, String library){
		String ret = "";
		for(Problem problem : k.getProblems())
			if(problem.getLibrary().compareTo(library)==0
			   && problem.getResult()!=null){
				Problem p = problem.getNonClassProblem();
				if(p==null)
					p = problem;
				ret += p.getComments("\"\"\"\n","\"\"\"")+"\n"+p.getResult().getCode(problem.getName())+"\n\n";
		}
		return ret;
	}
	/**
	 * <h1>getNonLineComments</h1>
	 * @param text : a String that contains both comments and code from
	 * a <i>Python</i> library
	 * @return a String that contains all non in-line comments in the given text
	 */
	protected static String getNonLineComments(String text){
		ArrayList<String> lines = new ArrayList<String>(Arrays.asList(text.split("\n")));
		String ret = "";
		boolean openComments = false;
		for(String line : lines){
			int commentBlockPos = line.indexOf("\"\"\"");
			if(commentBlockPos!=-1){
				if(openComments)
					ret += line.substring(commentBlockPos+3)+"\n";
				else
					ret += line.substring(0, commentBlockPos)+"\n";
				openComments = !openComments;
			}
			else if(!line.trim().startsWith("#") && !openComments)
				ret += line+"\n";
		}
		return ret;
	}
	/**
	 * <h1>getLineComments</h1>
	 * @param text : a String that contains both comments and code from
	 * a <i>Python</i> library
	 * @return a String that contains non in-line comments in the given text
	 */
	protected static String getLineComments(String text){
		ArrayList<String> lines = new ArrayList<String>(Arrays.asList(text.split("\n")));
		String ret = "";
		boolean openComments = false;
		for(String line : lines){
			line = line.trim();
			int commentBlockPos = line.indexOf("\"\"\"");
			if(line.startsWith("#"))
				ret += line.substring(1)+"\n";
			else if(commentBlockPos!=-1){
				if(openComments)
					ret += line.substring(0, commentBlockPos).trim()+"\n";
				else
					ret += line.substring(commentBlockPos+3).trim()+"\n";
				openComments = !openComments;
			}
			else if(openComments)
				ret += line+"\n";
		}
		return ret;
	}
	/**
	 * <h1>parseImport</h1>
	 * This function aims to split a <i>Python</i> library into separate problems
	 * (each one matching a single function). Each problem contains comments for
	 * that function and has a resulting problem that has its code separated into
	 * statements.<br/>
	 * This function firstly detects all in-line and non in-line comments that
	 * correspond to a function. Non in-line comments are assumed to be the
	 * comment block directly over the function (all comments between functions
	 * are considered to belong to the function bellow). <b>Note that this leads
	 * to incorrect class comments recognition.</b><br/>
	 * Comments and code are firstly generated into problems by using the
	 * <code>LanguagePareser.parse</code> function, but afterwards the following
	 * passes are made to correctly detect variables:<br/>
	 * PASS 1: Add all code and comment variables, as well as all left-hand-sides 
	 * of assignments to a list of variables an append them to all statements.
	 * Then remove all unused variables from the those statements.
	 * PASS 2: Append all input and output variables to all statements without any
	 * variables and also append their names to their expressions.
	 * PASS 3: Add assignments between unused input and function outputs for the
	 * base problem (this correctly detects omit-to-avoid redundancy patterns in
	 * speech).
	 * 
	 * @param fileText : the file contents of a <i>Python</i> library
	 * @return a list of problems that have non-empty results
	 */
	public static ArrayList<Problem> parseImport(String fileText){
		fileText.replace("\t", "    ");
		ArrayList<Problem> ret = new ArrayList<Problem>();
		String[] lines = fileText.split("\n");
		String text = "";
		boolean inFunction = false;
		for(int li=0;li<=lines.length;li++){
			String line = "";
			if(li<lines.length){
				line = lines[li];
				boolean tryAgain = true;
				if(line.startsWith("def")){
					if(inFunction)
						tryAgain = false;
					else
						inFunction = true;
				}
				else if(inFunction && !line.startsWith(" ")){
					tryAgain = false;
					inFunction = false;
				}
				if(tryAgain){
					text += line+"\n";
					continue;
				}
			}
			Problem p = new Problem("");
			Problem r = new Problem("");
			String comments = getLineComments(text);
			String body = getNonLineComments(text);
			
			LanguageParser.parse(p, comments);
			LanguageParser.parse(r, body, p.getVariables());
			if(!r.statements.isEmpty()){
				if(r.getName().isEmpty())
					r.setName(p.getName());
				else
					p.setName(r.getName());
				//normalize statements (include all possible variables)
				String variables = Problem.unionVariables(p.getVariables(), r.getVariables(), " ");
				for(Statement statement : r.statements){
					if(statement.isAssignment())
						variables = Problem.unionVariables(variables, statement.getTrivialVariable(), " ");
					else if(statement.getExpression().contains("=")){
						//detect variables from direct assignments (exactly one variable should be in the assignment)
						String expr = statement.getExpression();
						String newVars = "";
						Pattern pattern = Pattern.compile("([A-Za-z\\_][A-Za-z0-9\\_]*)");//compile("(\\*|\\-|\\=|\\+|\\-|\\s)(.)(\\*|\\-|\\=|\\+|\\-\\s)]");
						Matcher matcher = pattern.matcher(expr);
						while(matcher.find()){
							String word = matcher.group();
							if(expr.contains(word+" =") && !expr.contains(word+" =="))
								newVars += word+" ";
						}
						variables = Problem.unionVariables(variables, newVars.trim(), " ");
					}
				}
				//System.out.println(variables);
				LanguageParser.parse(r, body, variables);
				r.setName(p.getName()+"_implement");
				p.setResult(r);
				for(Statement statement : r.statements){
					if(statement.isInput() || statement.isOutput() || statement.isIterator())
						continue;
					String expr = statement.getExpression();
					statement.setVariables(variables);
					statement.setExpression(expr);
					statement.removeUnusedVariables();
				}
				for(Statement statement : p.statements){
					if(statement.isInput() || statement.isOutput() || statement.isIterator())
						continue;
					String expr = statement.getExpression();
					statement.setVariables(variables);
					statement.setExpression(expr);
					statement.removeUnusedVariables();
				}
				//refresh to split statements accordingly
				p.refresh();
				r.refresh();
				//get inputs and outputs
				String inputs = Problem.unionVariables(p.getInput(), r.getInput(), " ");
				String outputs = Problem.unionVariables(p.getOutput(""), r.getOutput(""), " ");
				//remove inputs and outputs (to put later insert them as a single statement)
				for(int i=p.statements.size()-1;i>=0;i--)
					if(p.statements.get(i).isInput() || p.statements.get(i).isOutput())
						p.statements.remove(i);
				if(!inputs.trim().isEmpty())
					p.statements.add(0, new Statement(inputs, "input"));
				if(!outputs.trim().isEmpty())
					p.statements.add(0, new Statement(outputs, "output"));
				//add all inputs and outputs to statements without variables
				String vars = Problem.unionVariables(r.getOutput(""), r.getInput(), " ");
				for(Statement statement : p.statements){
					if(statement.getVariables().trim().isEmpty()){
						statement.setVariables(vars);
						statement.setExpression(statement.getExpression()+"\n"+vars);
					}
				}
				//all outputs that may not be present in the base problem are assigned to empty statements and, if not possible, to input values
				if(enableAutoOutToInMatching){
					p.refresh();
					int outId = 0;
					for(String out : p.getOutput("").split(" ")){
						boolean assigned = false;
						for(Statement st : p.statements)
							if(!st.isInput() && !st.isOutput() && !st.isIterator())
							if((st.isAssignment() && st.getTrivialVariable().compareTo(out)==0) || (!st.isAssignment() && st.hasVariable(out))){
								assigned = true;
								break;
							}
						if(!assigned){
							String [] inList = inputs.split(" ");
							if(outId<inList.length){
								r.statements.add(new Statement(inList[outId]+" "+out, inList[outId]+" = "+out));
							}
						}
						outId++;
					}
				}
				p.refresh();
			}
			if(!p.statements.isEmpty())
				ret.add(p);
			
			text = line+"\n";
		}
		return ret;
	}
}
