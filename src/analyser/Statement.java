package analyser;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

/**
 * <h1>Statement</h1>
 * This class represents a flattened (i.e. with max depth 2) second-order logic statement.
 * With the exception of the comparison operators (=, <, >, <=, >=) other mathematical operators
 * are treated as predicates too. Generally, as predicates may be of unknown origin or in
 * some pseudo-language, this class keeps track of the expression and its variables.<br/>
 * Variables are required to be manually given, but this class handles their arrangement by
 * sorting them according to the order they appear inside the expression.
 * @author Manios Krasanakis
 */
public class Statement {
	//an ArrayList that contains the variable names (including the trivialVariable bellow)
	public ArrayList<String> variables;
	//a String that contains the expression
	private String expression;
	//the name of the variable if the expression is a comparison with a single variable in one side ("" otherwise)
	private String trivialVariable;
	//the comparison (<,>,<=,>= or =) that separates the two sides of the expression ("" if no trivialVariable)
	private String trivialSeparator;
	//custom comment type
	private String commentType;
	//
	public static String[] separators = {">=", "<=", ">", "<", "="};
	public static String[] inverses   = {"<=", ">=", "<", ">", "="};
	//a threshold which if the predicateSimilarity function gets over, statements are considered similar
	public static float predicateSimilarityThreshold = 0.3f;
	//endings for statements
	public static String[] endings = {"ed", "ing", "es", "e", "s"};
	//statements to be ignored
	public static String[] ignored = {};
	//list of logical ands for expressions (used by problem.refresh())
	public static String [] languageAnd = {"and"};
	//list of custom-comment predicates (if found only in one of the two compared statements, the similarity returns as 0)
	public static String [] customComments = {"@param","@always"};
	
	/**
	 * <h1>Statement</h1>
	 * The default constructor for the Statement class. It initializes all its member with empty values.
	 */
	public Statement(){
		variables = new ArrayList<String>();
		commentType = "";
		expression = "";
		trivialVariable = "";
		trivialSeparator = "";
	}
	
	/**
	 * <h1>Statement</h1>
	 * A constructor for the Statement class. It initializes the class completely.
	 * @param variables : a String that contains all variable names (with space between them)
	 * @param expression : the expression text
	 */
	public Statement(String variables, String expression){
		commentType = "";
		trivialVariable = "";
		trivialSeparator = "";
		setVariables(variables);
		setExpression(expression);
	}

	/**
	 * <h1>Statement</h1>
	 * A constructor for the Statement class. It initializes the class completely.
	 * @param variables : an ArrayList that contains all variable names
	 * @param expression : the statement's text (a.k.a. expression)
	 */
	public Statement(ArrayList<String> variables, String expression){
		commentType = "";
		trivialVariable = "";
		trivialSeparator = "";
		setVariables(variables);
		setExpression(expression);
	}
	
	/**
	 * <h1>Statement</h1>
	 * A constructor for the Statement class that just calls its <code>load</code> function,
	 * thus loading the statement from an XML element.
	 * @param e : XML element from which to load the statement
	 */
	public Statement(Element e){
		load(e);
	}
	
	/**
	 * <h1>hasVariable</h1>
	 * Searches the statement for a variable with the given name.
	 * @param var : the name of the variable to search for
	 * @return <code>true</code> if the statement contains a variable with such a name
	 */
	public boolean hasVariable(String var){
		if(!var.matches("([A-Za-z\\_][A-Za-z0-9\\_]*\\s*)+"))
			return false;
		ArrayList<String> list = new ArrayList<String>(Arrays.asList(var.split("\\s+")));
		for(String ext : list)
			for(String str : variables)
				if(str.compareTo(ext)==0)
					return true;
		return false;
	}
	
	/**
	 * <h1>isAssignment</h1>
	 * This function confirms weather the statement is a comparison between an input and an
	 * output from those that can be found in its arguments. An output must be on the left hand side.
	 * This function is vastly different to its much simpler version with no arguments.
	 * @param input : a String that contains possible input variable names (names separated by space)
	 * @param output : a String that contains possible output variable names (names separated by space)
	 * @return <code>true</code> if the statement is an assignment from an input to an output variable
	 * (exists <code>i</code> such that <code>output[i]=f(input)</code>)
	 */
	public boolean isAssignment(String input, String output){
		//if(!isAssignment())
		//	return false;
		if(trivialVariable.isEmpty())
			return false;
		ArrayList<String> in = new ArrayList<String>(Arrays.asList(input.split("\\s\\s*")));
		ArrayList<String> out = new ArrayList<String>(Arrays.asList(output.split("\\s\\s*")));
		boolean has = false;
		if(input.length()!=0){
			for(String str : in){
				for(String var : variables)
					if(var.compareTo(trivialVariable)!=0 && var.compareTo(str)==0){
						has = true;
						break;
					}
				if(has)
					break;
			}
			if(!has)
				return false;
			has = output.length()==0;
		}
		for(String str : out)
			if(trivialVariable.compareTo(str)==0){
				has = true;
				break;
			}
		return has;
	}
	
	/**
	 * <h1>setExpression</h1>
	 * Sets the statement's expression. This function must be called <b>after <code>setVariables</code></b>.
	 * This function also performs extensive analysis on its input on the following stages:
	 * <h2>1. Create uniform text</h2>
	 * Transform the text into a string with properties that help editing (such as space separators
	 * between operations). This step uses heavily regular expressions.
	 * <h2>2. Sort variables</h2>
	 * Sorts the variables according to the order of their first occurrence.Since a small number of variables
	 * is expected for each statement, a simple 'bubble' sorting is performed. The search for variables uses
	 * patterns and regular expressions.
	 * <h2>3. Trivial statement recognition</h2>
	 * In this final step, a search for a trivialVariable and trivialSeparator are performed. This step only
	 * recognizes the comparison separators (=,<,>,<=,>=). If the expression text contains the <code>:</code>
	 * symbol, this step is omitted, as it is assumed that in such a case the expression is actually a block
	 * of <i>Python</i> code.<br/>
	 * <i>A better (more general) implementation may be possible for the third step, but the one proposed here
	 * works too.</i><br/>
	 * @param expression : an expression text
	 */
	public void setExpression(String expression){
		//uniformize
		if(!expression.contains(":"))
			expression = expression.trim();
		expression = expression.replaceAll("\\s*\\,\\s*", ", ");
		expression = expression.replaceAll("\\s*\\[\\s*", "[");
		expression = expression.replaceAll("\\s*\\]\\s*", "] ");
		expression = expression.replaceAll("\\s*\\(\\s*", "(");
		expression = expression.replaceAll("\\s*\\)\\s*", ") ");
		expression = expression.replaceAll("\\s*\\{\\s*", "{");
		expression = expression.replaceAll("\\s*\\}\\s*", "} ");
		expression = expression.replaceAll("\\s*\\.\\s*", ".");
		expression = expression.replaceAll("\\s*\\:", ":");
		expression = expression.replaceAll("\\s*\\=\\s*", " = ");
		expression = expression.replaceAll("\\s*\\*\\s*", " * ");
		expression = expression.replaceAll("\\s*\\+\\s*", " + ");
		expression = expression.replaceAll("\\s*\\-\\s*", " - ");
		expression = expression.replaceAll("\\s*\\/\\s*", " / ");
		expression = expression.replaceAll("\\s*\\>\\s*", " > ");
		expression = expression.replaceAll("\\s*\\<\\s*", " < ");
		expression = expression.replaceAll("\\s*\\%\\s*", " % ");
		expression = expression.replaceAll("\\s*\\^\\s*", " ^ ");
		expression = expression.replaceAll("\\s*\\|\\s*", " | ");
		expression = expression.replaceAll("\\s*\\&\\s*", " & ");
		expression = expression.replaceAll("\\s*\\~\\s*", " ~ ");
		expression = expression.replaceAll("\\s*\\>\\s\\=\\s*", " >= ");
		expression = expression.replaceAll("\\s*\\<\\s\\=\\s*", " <= ");
		expression = expression.replaceAll("\\s*\\+\\s\\=\\s*", " += ");
		expression = expression.replaceAll("\\s*\\-\\s\\=\\s*", " -= ");
		expression = expression.replaceAll("\\s*\\*\\s\\=\\s*", " *= ");
		expression = expression.replaceAll("\\s*\\/\\s\\=\\s*", " /= ");
		expression = expression.replaceAll("\\s*\\%\\s\\=\\s*", " % ");
		expression = expression.replaceAll("\\s*\\=\\s+\\=\\s*", " == ");
		expression = expression.replaceAll("\\s*\\!\\s+\\=\\s*", " != ");
		expression = expression.replaceAll("\\s*\\&\\s+\\&\\s*", " && ");
		expression = expression.replaceAll("\\s*\\|\\s+\\|\\s*", " || ");
		expression = expression.replaceAll("\\s*\\*\\s+\\*\\s*", " ** ");
		expression = expression.replaceAll("\\s*\\+\\s+\\+\\s*", " ++ ");
		expression = expression.replaceAll("\\s*\\-\\s+\\-\\s*", " -- ");
		for(String str : languageAnd)
			expression = expression.replaceAll("\\s+"+Pattern.quote(str)+"\\s+", " "+str+" ");
		expression = expression.replaceAll("\\s+"+Pattern.quote("or")+"\\s+", " or ");
		expression = expression.replaceAll("\\s+"+Pattern.quote("is")+"\\s+", " is ");
		expression = expression.replaceAll("\\s+"+Pattern.quote("not")+"\\s+", " not ");
		expression = expression.replaceAll("\\s+"+Pattern.quote("in")+"\\s+", " in ");
		if(!expression.contains(":"))
			expression = expression.replaceAll("\\s+", " ");
		
		commentType = "";
		for(String comment : customComments){
			if(expression.startsWith(comment)){
				commentType = comment;
				expression = expression.substring(comment.length()).trim();
				break;
			}
		}

		//find first occurrence of variables
		ArrayList<Integer> valFirstFind = new ArrayList<Integer>();
		for(int i=0;i<variables.size();i++){
			Pattern pattern = Pattern.compile("[\\s\\+\\-\\*\\/\\^\\(\\)\\[\\]\\,\\{\\}\\.\\;\\:\n]"+Pattern.quote(variables.get(i))+"[\\s\\+\\-\\*\\/\\^\\(\\)\\[\\]\\,\\{\\}\\.\\;\\:\n]");
			Matcher matcher = pattern.matcher(" "+expression+" ");
			int pos = -1;
			if(matcher.find())
				pos = matcher.start();
			valFirstFind.add(pos);
		}
		//sort variables according to occurrence (use bubblesort since small numbers)
		for(int i=0;i<variables.size();i++)
			for(int j=i+1;j<variables.size();j++){
				if(valFirstFind.get(i)>valFirstFind.get(j)){
					int tmp = valFirstFind.get(i);
					valFirstFind.set(i, valFirstFind.get(j));
					valFirstFind.set(j, tmp);
					String tmps = variables.get(i);
					variables.set(i, variables.get(j));
					variables.set(j, tmps);
				}
			}
		
		//
		trivialVariable = "";
		trivialSeparator = "";

		if(expression.contains(":") && !expression.contains(" = ")){
			this.expression = expression;
			return;
		}
		
		String testExpr = expression;
		for(String sep : separators)
			testExpr = testExpr.replaceFirst(Pattern.quote(sep), "=");
		/*Pattern p = Pattern.compile("\\s*(.*)\\s*[=]\\s*(.*)(\n*;*\\s*)*");
		Pattern p = Pattern.compile("(.*)[=](.*)");
		Matcher m = p.matcher(" "+testExpr+" ");
		if(m.matches() && m.groupCount()>=2){*/
		if(testExpr.contains("=")){
		    int idx = testExpr.indexOf("=");
		    //this.expression = m.group(2).trim();
			this.expression = expression.substring(idx + 1).trim();
			if(isInput() || isOutput() || isIterator())
				return;
		    //trivialVariable = m.group(1).trim();
			trivialVariable = expression.substring(0, idx).trim();
			//trivialSeparator = "=";
		    if(!hasVariable(trivialVariable)){
				if(hasVariable(this.expression)){
					for(String sep : separators){
						trivialSeparator = sep;
						if(getExpression().compareTo(expression)==0){
							setExpression(this.expression + " " + opposite(trivialSeparator) + " " +  trivialVariable);
							return;
						}
					}
					trivialVariable = "";
					trivialSeparator = "";
					this.expression = expression;
					return;
				}
				else
					this.expression = "";
		    }
		    else{
			   for(String sep : separators){
				   trivialSeparator = sep;
				   if(getExpression().compareTo(expression)==0){
					   boolean hasOtherWay = false;
					   String oposite = sep;
					   for(int i=0;i<separators.length;i++)
						   if(separators[i].compareTo(sep)==0 && separators[i].compareTo(inverses[i])!=0){
							   oposite = inverses[i];
							   break;
						   }
					   if(oposite.compareTo(sep)==0)
						   hasOtherWay = true;
					   else
						   for(int i=0;i<separators.length;i++)
							   if(separators[i].compareTo(oposite)==0 && separators[i].compareTo(inverses[i])!=0){
								   hasOtherWay = true;
								   break;
							   }
					   if(!hasOtherWay)
						   inverse();
					   return;
				   }
			   }
		    }
		}
		trivialVariable = "";
		trivialSeparator = "";
		this.expression = expression;
	}
	
	/**
	 * <h1>isSame</h1>
	 * Compares this statement with another statement. If it is a comparison between assignments,
	 * variable names must also match. Otherwise, substituting the variable names should yield
	 * the same statement String for the two statements to be considered the same. In order to
	 * perform the substitution check in the later case, the <code>matchExpression</code> function
	 * is called with the given statement as argument.
	 * @param s : a statement to compare with
	 * @return <code>true</code> if the statements are regarded as the same
	 */
	public boolean isSame(Statement s){
		if(isAssignment() && variables.size()==2 && s.variables.size()==2){
			return //s.isAssignment() &&
				(   ( variables.get(0).compareTo(s.variables.get(0))==0 && variables.get(1).compareTo(s.variables.get(1))==0)
				 || (variables.get(1).compareTo(s.variables.get(0))==0 && variables.get(0).compareTo(s.variables.get(1))==0));
		}
		if(isTransitional() && variables.size()==2 && s.variables.size()==2){
			return 
					(trivialSeparator.compareTo(s.trivialSeparator)==0)
				 ||	(trivialSeparator.compareTo(s.trivialSeparator)==0 && variables.get(0).compareTo(s.variables.get(0))==0 && variables.get(1).compareTo(s.variables.get(1))==0)
			     || (trivialSeparator.compareTo(Statement.opposite(s.trivialSeparator))==0 && variables.get(1).compareTo(s.variables.get(0))==0 && variables.get(0).compareTo(s.variables.get(1))==0);
			
		}
		if(s.variables.size()!=variables.size())
			return false;
		String repl = matchExpression(s);
		if(repl.compareTo(getExpression())==0)
			return true;
		//compare
		if(!isTrivial()){
			if(predicateSimilarity(repl, getExpression(), getVariables())>predicateSimilarityThreshold)
				return true;
		}
		return false;
	}
	
	/**
	 * <h1>isIdentical</h1>
	 * Compares this statement with another statement. If it is a comparison between assignments or transitionals,
	 * variable names must also match. Otherwise, substituting the variable names should yield
	 * the same statement String for the two statements to be considered the same. In order to
	 * perform the substitution check in the later case, the <code>matchExpression</code> function
	 * is called with the given statement as argument.
	 * @param s : a statement to compare with
	 * @return <code>true</code> if the statements are regarded as identical
	 */
	public boolean isIdentical(Statement s){
		if(isAssignment() && variables.size()==2 && s.variables.size()==2){
			return //s.isAssignment() &&
				(   ( variables.get(0).compareTo(s.variables.get(0))==0 && variables.get(1).compareTo(s.variables.get(1))==0)
				 || (variables.get(1).compareTo(s.variables.get(0))==0 && variables.get(0).compareTo(s.variables.get(1))==0));
		}
		if(isTransitional() && variables.size()==2 && s.variables.size()==2){
			return (trivialSeparator.compareTo(s.trivialSeparator)==0 && variables.get(0).compareTo(s.variables.get(0))==0 && variables.get(1).compareTo(s.variables.get(1))==0)
			    || (trivialSeparator.compareTo(Statement.opposite(s.trivialSeparator))==0 && variables.get(1).compareTo(s.variables.get(0))==0 && variables.get(0).compareTo(s.variables.get(1))==0);
			
		}
		if(s.variables.size()!=variables.size())
			return false;
		String repl = matchExpression(s);
		if(repl.compareTo(getExpression())==0)
			return true;
		return false;
	}
	
	/**
	 * <h1>matchExpression</h1>
	 * This static function substitutes variable names in an expression String. It is used by the
	 * non-static <code>matchExpression</code>, <code>replace</code> and <code>addVariablePrefix</code>
	 * functions.
	 * @param repl : an expression String in which the replacement rule takes place
	 * @param replacements : the replacement rule (a HashMap with keys the variables to be replaced
	 * and values the variables to take their place)
	 * @return the resulting expression String
	 */
	public static String matchExpression(String repl, HashMap<String,String> replacements){
		if(replacements.size()!=0){
			Pattern pattern = Pattern.compile("[A-Za-z\\_][A-Za-z0-9\\_]*");//compile("(\\*|\\-|\\=|\\+|\\-|\\s)(.)(\\*|\\-|\\=|\\+|\\-\\s)]");
			Matcher matcher = pattern.matcher(repl);
			StringBuffer buffer = new StringBuffer();
			while (matcher.find()) {
				String replacement = replacements.get(matcher.group(0));
				if (replacement != null){
					matcher.appendReplacement(buffer, "");
					buffer.append(replacement);
				}
			}
			matcher.appendTail(buffer);
			repl = buffer.toString();
			/*if(!s.isTrivial() && !s.isIterator()){
				System.out.println(replacements.toString());
				System.out.println(s.getExpression()+" to "+repl);
				System.out.println("Compare with "+getExpression());	
			}*/
		}
		return repl;
	}
	
	/**
	 * <h1>matchExpression</h1>
	 * This function firstly creates a HashMap between the given stament's variables and their
	 * corresponding ones in this statement and then returns its the implementation of
	 * <code>matchExpression</code> that takes a HashMap as an argument. This function may throw
	 * an out of bounds exception if the input statement has more variables and may not
	 * behave as intended by its caller if the input statement has less variables.<br/>
	 * <i>Checks for same number of variables are left to the function's caller.</i>
	 * @param s : a statement whose variables should replace the current variables
	 * @return an expression String that substitutes each variable of the input statement
	 * with the its corresponding one from this statement
	 */
	public String matchExpression(Statement s){
		HashMap<String,String> replacements = new HashMap<String,String>();
		for(int i=0;i<s.variables.size();i++){
			if(s.variables.get(i).compareTo(variables.get(i))!=0)
				replacements.put(s.variables.get(i), variables.get(i));
		}
		return matchExpression(s.getExpression(), replacements);
	}
	
	/**
	 * <h1>replace</h1>
	 * This function replaces a variable name with a new one. It works <b>only for single variables</b>. For
	 * multiple variable replacement, use the static function <code>matchExpression</code>.
	 * @param varOld : the variable whose name is to be changed
	 * @param varNew : the new variable name
	 * @return <code>true</code> if the expression was successfully altered
	 */
	public boolean replace(String varOld, String varNew){
		HashMap<String,String> replacements = new HashMap<String,String>();
		replacements.put(varOld, varNew);
		String repl = matchExpression(getExpression(), replacements);
		for(int i=0;i<variables.size();i++)
			if(variables.get(i).compareTo(varOld)==0)
				variables.set(i, varNew);
		String prev = getExpression();
		setExpression(repl);
		return getExpression().compareTo(prev)!=0;
	}
	
	/**
	 * <h1>addVariablePrefix</h1>
	 * This function uses the static function <code>matchExpression</code> to add a desired
	 * prefix to all variable names of the statement. This changes accordingly the statement's
	 * expression string too.
	 * @param pref : the desired prefix
	 */
	public void addVariablePrefix(String pref){
		HashMap<String,String> replacements = new HashMap<String,String>();
		for(String var : variables)
			replacements.put(var, pref+var);
		String repl = matchExpression(getExpression(), replacements);
		for(int i=0;i<variables.size();i++)
			variables.set(i, pref+variables.get(i));
		if(trivialVariable.length()>0)
			trivialVariable = pref+trivialVariable;
		setExpression(repl);
	}
	
	/**
	 * <h1>removeVariablePrefix</h1>
	 * This function uses the static function <code>matchExpression</code> to remove a desired
	 * prefix to all variable names of the statement. This changes accordingly the statement's
	 * expression string too.<br/>
	 * This function is the exact inverse of <code>addVariablePrefix</code>.
	 * @param pref : the desired prefix to remove
	 */
	public void removeVariablePrefix(String pref){
		HashMap<String,String> replacements = new HashMap<String,String>();
		for(String var : variables)
			if(var.startsWith(pref))
				replacements.put(var, var.substring(pref.length()));
			else
				replacements.put(var, var);
		String repl = matchExpression(getExpression(), replacements);
		for(int i=0;i<variables.size();i++)
			if(variables.get(i).startsWith(pref))
				variables.set(i, variables.get(i).substring(pref.length()));
		if(trivialVariable.startsWith(pref))
			trivialVariable = trivialVariable.substring(pref.length());
		setExpression(repl);
	}

	/**
	 * <h1>setVariables</h1>
	 * Creates the variable ArrayList from the given String. Variables are assumed to be
	 * separated using the space separator. This function must always be called
	 * <b>before the <code>setExpression</code> function</b>.
	 * @param variables : an ArrayList of variable names
	 */
	public void setVariables(String variables){
		setVariables(new ArrayList<String>(Arrays.asList(variables.split("\\s+"))));
	}
	
	/**
	 * <h1>setVariables</h1>
	 * Sets the given ArrayList as the variable ArrayList. This function must always be called
	 * <b>before the <code>setExpression</code> function</b>.
	 * @param variables : an ArrayList of variable names
	 */
	public void setVariables(ArrayList<String> variables){
		this.variables = variables;
	}
	
	/**
	 * <h1>getExpression</h1>
	 * Returns the statement's complete expression that can be parsed as an argument to
	 * <code>setExpression</code> to produce the same statement.
	 * @return the statement's complete expression
	 */
	public String getExpression(){
		String ret = expression;
		if(!trivialVariable.isEmpty())
			ret = trivialVariable + " " + trivialSeparator + " " + ret;
		if(!commentType.isEmpty())
			ret = commentType+" "+ret;
		return ret;
	}
	
	/**
	 * <h1>getTrivialVariable</h1>
	 * Returns the trivialVariable. The trivialVariable is a single variable which is connected with
	 * the rest of the expression through a single comparison (i.e. =,<,>,<=,>=). To get that comparison
	 * use the <code>getTrivialSeparator</code> function.
	 * @return the trivialVariable
	 */
	public String getTrivialVariable(){
		return trivialVariable;
	}
	
	/**
	 * <h1>getTrivialSeparator</h1>
	 * Returns the trivialSeparator. The trivialSeparator can only be a comparison (i.e. =,<,>,<=,>=).
	 * @return the trivialSeparator
	 */
	public String getTrivialSeparator(){
		return trivialSeparator;
	}
	
	/**
	 * <h1>getTrivialExpression</h1>
	 * Returns a String that together with <code>getTrivialVariable</code> and <code>getTrivialSeparator</code>
	 * can complete statement's expression. To get a complete expression, use <code>getExpression</code> instead.
	 * @return
	 */
	String getTrivialExpression(){
		return expression;
	}
	
	/**
	 * <h1>getVariables</h1>
	 * Returns all variables in a single string. The variables are separated using the space separator.
	 * The resulting String can be used by <code>seVariables</code>.
	 * @return a string containing all variables
	 */
	public String getVariables(){
		return getVariables(" ");
	}

	/**
	 * <h1>removeDublicateVariables</h1>
	 * Removes all second variable instances.
	 * (After this, the <code>isInvalid</code> function is possible to return the desired false result.)
	 * @return the number of variables removed
	 */
	public int removeDublicateVariables(){
		int count = 0;
		String vars = " ";
		for(int i=0;i<variables.size();i++)
			if(!vars.contains(" "+variables.get(i)+" "))
				vars += variables.get(i)+" ";
		setVariables(vars.trim());
		return count;
	}
	
	/**
	 * <h1>removeUnusedVariables</h1>
	 * Removes all variables that are not used by the statement.
	 * (After this, the <code>isInvalid</code> function is possible to return the desired false result.)
	 * @return the number of variables removed
	 */
	public int removeUnusedVariables(){
		int count = 0;
		String vars = "";
		for(String var : variables){
			Pattern pattern = Pattern.compile("[\\s\\+\\-\\*\\/\\^\\(\\)\\[\\]\\,\\{\\}\\.\\<\\>\\=\\;\\:\n]"+Pattern.quote(var)+"[\\s\\+\\-\\*\\/\\^\\(\\)\\[\\]\\,\\{\\}\\.\\<\\>\\=\\;\\:\n]");
			Matcher matcher = pattern.matcher(" "+getExpression()+" ");
			if(!matcher.find() && trivialVariable.compareTo(var)!=0)
				count++;
			else{
				if(!vars.isEmpty())
					vars += " ";
				vars += var;
			}
		}
		String expr = getExpression();
		setVariables(vars);
		setExpression(expr);
		return count;
	}
	
	/**
	 * <h1>getVariables</h1>
	 * Returns all variables in a single string. The variables are separated using a given separator.
	 * @param separator : the separator you wish to use
	 * @return a string containing all variables separated by the given separator
	 */
	public String getVariables(String separator){
		String str = "";
		for(String s : variables){
			if(!str.isEmpty())
				str += separator;
			str += s;
		}
		return str;
	}
	
	/**
	 * <h1>toHTML</h1>
	 * Converts the statement's expression to HTML.
	 * The code is also colored according to the following conventions:
	 * <br/>- if <code>isTransitional</code> is true (statement can be inverted) then
	 * <code>&lt</code>,<code>&gt</code> and </code>=</code> symbols will be bold blue
	 * <br/>- if <code>isAssignment</code> is true then the </code>=</code> symbol will be bold red
	 * <br/>- the <code>is</code> predicate will become the <code>=</code> bold red symbol
	 * @return the converted expression
	 */
	public String toHTML(){
		if(isEmpty())
			return "...";
		if(isInput())
			return "Inputs: " + getVariables();
		if(isOutput())
			return "Outputs: " + getVariables();
		if(isIterator())
			return "For any: " + getVariables();
		String text = getExpression();
		if(isTransitional() || !trivialSeparator.isEmpty()){
    		text = text.replaceFirst(">=", " _&&gteq").replace("<=", " _&&lteq");
    		text = text.replaceFirst(">", " _&&gt").replace("<", "<b color='0000FF'>&lt</b>");
    		text = text.replaceFirst(" _&&gteq", "<b color='0000FF'>&ge</b>");
    		text = text.replaceFirst(" _&&lteq", "<b color='0000FF'>&le</b>");
    		text = text.replaceFirst(" _&&gt", "<b color='0000FF'>&gt</b>");
    		text = text.replaceFirst(" = ", "<b color='0000FF'> = </b>");
    	}
		else
			text = text.replace(">=", "&ge").replace("<=", "&le").replace(">", "&gt").replace("<", "&lt");
    	if(!isSourceCode()){
			String[] words = text.split(" ");
	    	text = "";
	    	int i = -1;
	    	for(String word : words){
	    		i++;
		    	if(i==0 && isSpecialComment()){
					text += " <span rsize='1.2' color='7777FF'>"+commentType+"</span> ";
					continue;
		    	}
	    		boolean toIgnore = false;
	    		boolean isVariable = false;
				for(String var : variables)
					if(word.compareTo(var)==0){
						isVariable = true;
						break;
					}
				if(!isVariable)
				for(String ign : ignored)
					if(isSamePredicate(word, ign)){
						toIgnore = true;
						break;
					}
				if(toIgnore)
					text += " <small color='555555'>"+word+"</small> ";
				else if(isVariable || word.startsWith("&"))
					text += " "+word+" ";
				else{
					boolean specialEnding = false;
					for(String end : endings)
						if(word.endsWith(end)){
							text += " "+word.substring(0,word.length()-end.length())+"<small color='555555'>"+end+"</small>";
							specialEnding = true;
							break;
						}
					if(!specialEnding)
						text += " "+word+" ";
				}
	    	}
    	}
		text = text.replace(" is ", "<b color='FF0000'> = </b>");
    	text = text.replace("\n", "<br/>").replace("  ", "&nbsp ");
		return text;
	}
	
	/**
	 * <h1>toString</h1>
	 * Overrides the <code>toString</code> method of objects, making return a description of this statement.
	 * @return a description of this statement
	 */
	@Override
	public String toString(){
		if(isEmpty())
			return "...";
		if(isInput())
			return "Inputs: " + getVariables();
		if(isOutput())
			return "Outputs: " + getVariables();
		if(isIterator())
			return "For any: " + getVariables();
		return getExpression();
	}
	
	/**
	 * <h1>isEmpty</h1>
	 * Checks weather the statement is empty.
	 * @return <code>true</code> is expression is empty (even if there are variables present)
	 */
	public boolean isEmpty(){
		return expression.isEmpty();
	}

	/**
	 * <h1>isInput</h1>
	 * Checks weather the statement is an input statement.
	 * @return <code>true</code> if expression is input statement
	 */
	public boolean isInput(){
		return expression.compareTo("input")==0;
	}

	/**
	 * <h1>isOutput</h1>
	 * Checks weather the statement is an output statement.
	 * @return <code>true</code> if expression is output statement
	 */
	public boolean isOutput(){
		return expression.compareTo("output")==0;
	}

	/**
	 * <h1>isIterator</h1>
	 * Checks weather the statement is an iterator statement.
	 * @return <code>true</code> if expression is iterator statement
	 */
	public boolean isIterator(){
		return expression.compareTo("any")==0;
	}
	
	/**
	 * <h1>isInvalid</h1>
	 * Checks variable integrity. In particular, this function checks for variable repetions
	 * and unused variable declarations.
	 * @return <code>true</code> if the statement has variable errors
	 */
	public boolean isInvalid(){
		for(int i=0;i<variables.size();i++)
			for(int j=0;j<variables.size();j++)
				if(i!=j && variables.get(i).compareTo(variables.get(j))==0)
					return true;
		
		if(isInput() || isOutput() || isSourceCode() || isIterator())
			return false;
		
		for(int i=0;i<variables.size();i++){
			//valFirstFind.add(expression.indexOf(variables.get(i)));
			/*String allSeps = "";
			for(String str : separators)
				allSeps += "("+str+")";
			Pattern pattern = Pattern.compile("[\\s\\+\\-\\*\\/\\^\\(\\)\\[\\]\\,\\{\\}\\.\\<\\>\\="+allSeps+"]"+variables.get(i)+"[\\s\\+\\-\\*\\/\\^\\(\\)\\[\\]\\,\\{\\}\\.\\<\\>\\="+allSeps+"]");
			*/
			Pattern pattern = Pattern.compile("[\\s\\+\\-\\*\\/\\^\\(\\)\\[\\]\\,\\{\\}\\.\\<\\>\\=\\;\\:\n]"+Pattern.quote(variables.get(i))+"[\\s\\+\\-\\*\\/\\^\\(\\)\\[\\]\\,\\{\\}\\.\\<\\>\\=\\;\\:\n]");
			Matcher matcher = pattern.matcher(" "+expression+" ");
			if(!matcher.find() && trivialVariable.compareTo(variables.get(i))!=0)
				return true;
		}
		return false;
	}
	
	/**
	 * <h1>isSpecialComment</h1>
	 * Returns true if the statement is a special comment. A statement is 
	 * a special comment if its text starts with a special comment predicate.
	 * @return <code>true</code> if the expression is a special comment
	 */
	public boolean isSpecialComment(){
		return !commentType.isEmpty();
	}
	
	/**
	 * <h1>isSourceCode</h1>
	 * Returns true if the statement is assumed to be <i>Python</i> code.
	 * Checks for <i>Python</i> code are performed simplistically by asserting
	 * weather the <code>:</code> symbol is contained in the expression.
	 * @return <code>true</code> if the expression is <i>Python</i> code
	 */
	public boolean isSourceCode(){
		return expression.contains(":");
	}
	
	/**
	 * <h1>isAssignent</h1>
	 * Checks weather the statement is a direct assignment. This function checks <b>for equality
	 * (<code>=</code>) only</b>. To check for comparisons between exactly two variables (i.e. =, <,>,<=,>=)
	 * use the <code>isTransitional</code> function.
	 * @return <code>true</code> if the statement is a direct assignment
	 */
	public boolean isAssignment(){
		return !trivialVariable.isEmpty() && trivialSeparator.compareTo("=")==0;
	}
	
	/**
	 * <h1>isTrivial</h1>
	 * Checks weather the statement is a trivial one. Trivial statements are the only statements
	 * that can be directly implemented.
	 * @return <code>true</code> if the statement is assumed to be a trivial one
	 */
	public boolean isTrivial(){
		return isSourceCode() || ( !isIterator() && (isAssignment() || isInput() || isOutput()) );
	}
	
	/**
	 * <h1>isTransitional</h1>
	 * Checks weather the statement is transitional between two variables.
	 * @return <code>true</code> if the expression is in the form <code>variable1 * variable2</code>, where * can be
	 * any comparison (i.e. =, <, >, <=, >=.)
	 */
	public boolean isTransitional(){
		if(variables.size()==2 && getExpression().compareTo(variables.get(0)+" "+trivialSeparator+" "+variables.get(1))==0)
			return true;
		return false;
	}
	
	/**
	 * <h1>inverse</h1>
	 * This function inverses the left and right hand sides of the statement if the statement has a trivialVariable.
	 * If the statement does not have a trivialVariable, the function does nothing.
	 */
	public void inverse(){
		if(trivialVariable.isEmpty())
			return;
		setExpression(expression+" "+opposite(trivialSeparator)+" "+trivialVariable);
	}
	
	/**
	 * <h1>opposite</h1>
	 * This function inverses the &lt and &gt signs in a given String
	 * @param opp : a trivialSeperator to inverse
	 * @return the inverse of the input String
	 */
	private static String opposite(String opp){
		for(int i=0;i<separators.length;i++){
			opp = opp.replace(separators[i], " _"+i);
		}
		for(int i=0;i<separators.length;i++){
			opp = opp.replace(" _"+i, inverses[i]);
		}
		return opp;
	}
	
	/**
	 * <h1>getInformation</h1>
	 * This function returns a metric for the amount of information stored in the
	 * statement. The amount of information is dependent on the number of variables,
	 * as well as the number of their occurrences (a single symbol is considered as
	 * a predicate between any two variable occurrences). Also, each parenthesis
	 * block contributes to complexity and thus also increases it by one (either by
	 * being considered a function call or part of a condition).
	 * @return returns |variables|+|variable occurrences|/2-1+|parenthesis blocks|
	 * (inputs and outputs return 0 information)
	 */
	public int getInformation(){
		if(isInput() || isOutput())
			return 0;
		int info = variables.size();
		int count = 0;
		//find first occurrence of variables
		for(int i=0;i<variables.size();i++){
			Pattern pattern = Pattern.compile("[\\s\\+\\-\\*\\/\\^\\(\\)\\[\\]\\,\\{\\}\\.]"+Pattern.quote(variables.get(i))+"[\\s\\+\\-\\*\\/\\^\\(\\)\\[\\]\\,\\{\\}\\.]");
			Matcher matcher = pattern.matcher(" "+expression+" ");
			while(matcher.find())
			    count++;
		}
		if(!trivialVariable.isEmpty()){
			count++;
			info++;
		}
		info += count/2-1;
		info += expression.split("\\(").length;
		return info;
	}
	
	/**
	 * <h1>save</h1>
	 * Saves the statement to an element of an XML document.
	 * @param e : the XML element to save into
	 * @param doc : the XML document the element belongs to
	 */
	void save(Element e, Document doc){
		Element variablesElement = doc.createElement("variables");
		variablesElement.appendChild(doc.createTextNode(getVariables()));
		e.appendChild(variablesElement);
		Element expressionElement = doc.createElement("expression");
		expressionElement.appendChild(doc.createTextNode(getExpression()));
		e.appendChild(expressionElement);
	}
	
	/**
	 * <h1>load</h1>
	 * Loads the statement from an XML element.
	 * @param e : the XML element to load from
	 */
	void load(Element e){
		setVariables(e.getElementsByTagName("variables").item(0).getTextContent());
		setExpression(e.getElementsByTagName("expression").item(0).getTextContent());
	}
	
	/**
	 * <h1>predicateSimilarity</h1>
	 * This function compares predicates between the two strings. It uses
	 * the <code>isSamePredicate</code> function to compare predicates.
	 * @param str1 : the first string
	 * @param str2 : the second string
	 * @param variables : the variables in a string format
	 * @return <code>((predicates of str1 in str2)+(predicates of str2 in str1)-2*numberOfVariables)/((predicates in str1)+(predicates in str2)-2*numberOfVariables)</code>
	 * = 0 for completely dissimilar and 1 for the same string context
	 */
	public static float predicateSimilarity(String str1, String str2, String variables){
		int common = 0;
		int ignore = 0;
		ArrayList<String> predicates1 = new ArrayList<String>(Arrays.asList(str1.split(" ")));
		ArrayList<String> predicates2 = new ArrayList<String>(Arrays.asList(str2.split("\\s+")));
		ArrayList<String> vars = new ArrayList<String>(Arrays.asList(variables.split(" ")));
		
		if(!predicates1.isEmpty() && !predicates2.isEmpty()){
			boolean sameCommentType = true;
			for(String commentType : customComments){
				if(commentType.equals(predicates1.get(0)) || commentType.equals(predicates2.get(0))){
					sameCommentType = predicates1.get(0).equals(predicates2.get(1));
					break;
				}
			}
			if(!sameCommentType)
				return 0;
		}
		
		for(String pr1 : predicates1){
			boolean toIgnore = false;
			for(String var : vars)
				if(var.compareTo(pr1)==0){
					toIgnore = true;
					ignore++;
					break;
				}
			if(toIgnore)
				continue;
			for(String ign : ignored)
				if(isSamePredicate(pr1, ign)){
					toIgnore = true;
					ignore++;
					break;
				}
			if(toIgnore)
				continue;
			for(String pr2 : predicates2)
				if(isSamePredicate(pr1, pr2)){
					common++;
					break;
				}
		}
		for(String pr2 : predicates2){
			boolean toIgnore = false;
			for(String var : vars)
				if(var.compareTo(pr2)==0){
					toIgnore = true;
					ignore++;
					break;
				}
			if(toIgnore)
				continue;
			for(String ign : ignored)
				if(isSamePredicate(pr2, ign)){
					toIgnore = true;
					ignore++;
					break;
				}
			if(toIgnore)
				continue;
			for(String pr1 : predicates1)
				if(isSamePredicate(pr1, pr2)){
					common++;
					break;
				}
		}
		return (float)(common)/(float)(predicates1.size()+predicates2.size()-ignore);
	}
	
	/**
	 * <h1>isSamePredicate</h1>
	 * Checks weather two predicates are the same using synonym and ending comparison.
	 * The order of the predicates does not matter.
	 * @param predicate1 : the first predicate
	 * @param predicate2 : the second predicate
	 * @return true if the predicates are considered the same
	 */
	private static boolean isSamePredicate(String predicate1, String predicate2){
		for(String ending : endings){
			if(predicate1.endsWith(ending)){
				predicate1 = predicate1.substring(0, predicate1.length()-ending.length());
				break;
			}
		}for(String ending : endings){
			if(predicate2.endsWith(ending)){
				predicate2 = predicate2.substring(0, predicate2.length()-ending.length());
				break;
			}
		}
		return predicate1.compareTo(predicate2)==0;
	}

	public boolean containsAssignmentTo(String variable) {
		if(isInput() && variables.contains(variable))
			return true;
		String expr = (" "+getExpression()).replace("\n", " ");
		return expr.contains(" "+variable+" = ");// && (variables.size()==1 || !expr.matches(".*[\\s,\\(,\\[]\\s*("+Pattern.quote(variable)+")\\s+=.*[\\s,\\(,\\[]("+Pattern.quote(variable)+").*"));
	}
	
	public boolean containPerfectAssignmentTo(String variable){
		if(isInput() && variables.contains(variable))
			return true;
		String expr = (" "+getExpression()).replace("\n", " ");
		return expr.contains(" "+variable+" = ") && (variables.size()==1 || !expr.matches(".*[\\s,\\(,\\[]\\s*("+Pattern.quote(variable)+")\\s+=.*[\\s,\\(,\\[]("+Pattern.quote(variable)+").*"));
	
	}
}
