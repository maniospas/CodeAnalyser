package parser;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.JOptionPane;

import analyser.*;

/**
 * <h1>LanguageParser</h1>
 * This class can be used to convert series of string statements into problems,
 * including correct variable detections for all statements.
 * @author Manios Krasanakis
 */
public class LanguageParser {
	/**
	 * <h1>language</h1>
	 * Converts a given problem into a parsable expression by using the
	 * <code>Problem.getComments</code> function.
	 * @param p : the given problem
	 * @return an expression that when inputed into the <code>parse</code> function 
	 * will produce the exact same problem
	 */
	public static String language(Problem p){
		return p.getComments("","");
	}
	
	/**
	 * <h1>parse</h1>
	 * This function empties a given problem and adds to it statements according to the
	 * given String expression.<br/>
	 * The expression can contain the following:<br/>
	 * - <code>library:</code> followed by a library name for the problem
	 * - <code>inputs:</code> followed by a list of variables separated by commas (<code>,</code>) or spaces, which declares the problem's inputs<br/>
	 * - <code>outputs:</code> followed by a list of variables separated by commas (<code>,</code>) or spaces, which declares the problem's outputs<br/>
	 * - <code>for any:</code> followed by a list of variables separated by commas (<code>,</code>) or spaces, which declares any iterators
	 * - <code>variables:</code> followed by a list of variables separated by commas (<code>,</code>) or spaces, which declares any additional variables that may be missing from the inputs, outputs or itterators<br/>
	 * Each line is parsed into a different statement, with the exceptions of lines between
	 * <code>{</code> and <code>}</code> which are treated as a common statement. <i>Python</i>
	 * indentations for blocks of code are also parsed as a single statement.
	 * @param p : the given problem into which to create statements for the given expression
	 * @param expression : the given expression
	 */
	public static void parse(Problem p, String expression){
		parse(p, expression, "");
	}
	public static void parse(Problem p, String expression, String variables){
		expression = expression.replace("{", "\n{\n");
		expression = expression.replace("}", "\n}\n");
		ArrayList<String> lines = new ArrayList<String>(Arrays.asList(expression.split("\n")));
		String inputs = "";
		String outputs = "";
		ArrayList<String> extraLines = new ArrayList<String>();
		///1. Detect variables
		boolean insideFunction = false;
		for(String line : lines){
			line = line.trim();
			if(line.toLowerCase().startsWith("variables:")){
				if(!variables.isEmpty())
					variables += " ";
				String var =line.substring(("variables:").length()+1).replace(", ", " ");
				variables = Problem.unionVariables(variables, var, " ");
			}
			else if(line.toLowerCase().startsWith("inputs:")){
				if(!variables.isEmpty())
					variables += " ";
				String var =line.substring(("inputs:").length()+1).replace(", ", " ");
				variables = Problem.unionVariables(variables, var, ", ");
				inputs = Problem.unionVariables(outputs, var, " ");
			}
			else if(line.toLowerCase().startsWith("for any:")){
				String var =line.substring(("for any:").length()+1).replace(", ", " ");
				variables = Problem.unionVariables(variables, var, " ");
			}
			else if(line.toLowerCase().startsWith("outputs:")){
				if(!variables.isEmpty())
					variables += " ";
				String var = line.substring(("outputs:").length()+1).replace(", ", " ");
				variables = Problem.unionVariables(variables, var, " ");
				outputs = Problem.unionVariables(outputs, var, " ");
			}
			else if(line.toLowerCase().startsWith("library:")){
				p.setLibrary(line.replace("\\s+", "").substring(("library:").length()+1));
			}
			else if(line.toLowerCase().startsWith("function:")){
				p.setName(line.replace("\\s+", "").substring(("function:").length()+1));
			}
			else if(line.toLowerCase().startsWith("def ")&&line.toLowerCase().endsWith(":")){
				line = line.replace("\\s+", " ");
				line = line.substring(("def ").length(), line.length()-(":").length());
				int firstParenthesis = line.indexOf("(");
				p.setName(line.substring(0, firstParenthesis).trim());
				String vars = line.substring(firstParenthesis+1, line.length()-1).replace(",", " ").replace("\\s+", " ");
				inputs = Problem.unionVariables(inputs, vars, " ");
				variables = Problem.unionVariables(variables, vars, " ");
				insideFunction = true;
			}
			else if(line.toLowerCase().startsWith("return ")){
				line = line.replace("\\s+", " ").trim();
				line = line.substring(("return ").length()).trim();
				if(line.startsWith("("))
					line = line.substring(1, line.lastIndexOf(")"));
				else if(line.endsWith(";"))
					line = line.substring(0, line.length()-1);
				extraLines.add(line);
			}
		}
		//System.out.println(variables);
		p.statements.clear();
		boolean code = false;
		int blockStart = -1;
		int blockLevel = 0;
		int functionSpaces = 0;
		///3. generate expressions for each line
		for(String line : lines){
			if(insideFunction){
				if(functionSpaces==0)
					while(line.startsWith(" ")){
						functionSpaces++;
						line = line.substring(1);
					}
				else
					line = line.substring(functionSpaces);
			}
			line = line.replace(";", "");
			if(line.startsWith(" ")&&code){
				Statement statement = p.statements.get(p.statements.size()-1);
				statement.setVariables(variables);
				statement.setExpression(statement.getExpression()+"\n"+line);
				statement.removeUnusedVariables();
				continue;
			}
			else
				code = false;
			line = line.trim();
			if(line.compareTo("{")==0){
				if(blockLevel==0)
					blockStart = p.statements.size();
				blockLevel++;
				continue;
			}
			if(line.compareTo("}")==0){
				blockLevel--;
				if(blockLevel!=0)
					continue;
				String str = "";
				for(int st=blockStart+1;st<p.statements.size();st++){
					str += "\n"+p.statements.get(st).getExpression();
					p.statements.remove(st);
					st--;
				}
				if(!str.isEmpty()){
					p.statements.get(blockStart).setExpression(p.statements.get(blockStart).getExpression()+str);
					p.statements.get(blockStart).setVariables(variables);
					p.statements.get(blockStart).removeUnusedVariables();
				}
				blockStart = -1;
				continue;
			}
			if(   line.toLowerCase().startsWith("variables:")
			   || line.toLowerCase().startsWith("inputs:")
			   || line.toLowerCase().startsWith("outputs:")
			   || line.toLowerCase().startsWith("function:")
			   || line.toLowerCase().startsWith("library:")
			   || (line.toLowerCase().startsWith("def ") && line.toLowerCase().endsWith(":"))
			   || (line.toLowerCase().startsWith("return"))
			  )
				continue;
			if(line.isEmpty())
				continue;
			if(line.toLowerCase().startsWith("for any:")){
				p.statements.add(new Statement(line.substring(("for any:").length()+1), "any"));
				continue;
			}
			Statement statement = new Statement(variables, line);
			statement.removeUnusedVariables();
			p.statements.add(statement);
			if(statement.isSourceCode())
				code = true;
		}
		if(blockLevel!=0)
			JOptionPane.showMessageDialog(null, "Imbalanced brackets for blocks of code");
		///4. Add expressions from return statements
		variables = p.getVariables();
		for(String line : extraLines){
			line = line.replace(";", "");
			String [] statements = line.split(",");
			for(String st : statements){
				st = st.trim();
				Pattern pattern = Pattern.compile("[A-Za-z\\_][A-Za-z0-9\\_]*");
				Matcher matcher = pattern.matcher(st);
				if(matcher.find() && st.trim().compareTo(matcher.group(0))==0){
					variables = Problem.unionVariables(variables, st, " ");
					outputs = Problem.unionVariables(outputs, st, " ");
				}
				else{
					String nextVar = "ret0";
					int count = 0;
					while((" "+variables+" ").contains(" "+nextVar+" ")){
						count++;
						nextVar = "ret"+count;
					}
					variables = Problem.unionVariables(variables, nextVar, " ");
					//System.out.println("Added: "+nextVar+" = "+st.trim());
					Statement statement = new Statement(variables, nextVar+" = "+st.trim());
					statement.removeUnusedVariables();
					p.statements.add(statement);
					outputs = Problem.unionVariables(outputs, nextVar, " ");
				}
			}
		}
		
		///6. Generate input and output expressions
		if(!outputs.isEmpty())
			p.statements.add(0, new Statement(outputs, "output"));
		if(!inputs.isEmpty())
			p.statements.add(0, new Statement(inputs, "input"));
	}
}
