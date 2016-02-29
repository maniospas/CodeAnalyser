package solver;

import java.util.ArrayList;

import analyser.Knowledge;
import analyser.Problem;

/**
 * <h1>Solver</h1>
 * This class has the appropriate static members that perform problem solving.
 * @author Manios Krasanakis
 */
public class Solver {
	/**
	 * <h1>solve</h1>
	 * Performs the solution algorithm by performing consecutive steps.
	 * In each step <code>Problem.solve</code> is called for the given problem
	 * and then the algorithm stops if either no changes were performed or if
	 * the problem is solved.
	 * @param pr : an empty problem into which to put the solution
	 * @param p : the given problem
	 * @param knowledge : the knowledge pool
	 * @param ignoredLibraries : libraries to ignore from the knowledge pool
	 * @param maxIterations : maximum number of steps to perform
	 * @param importanceBalance : importance of <i>not</i> adding new information
	 * (>100% for Occam's Razor - i.e. to select the simplest implementation)
	 * @param logLevel : the desired log level. Must be either 2 or 3.
	 * @param threads : the number of threads to use
	 */
	public static void solve(Problem pr, Problem p, Knowledge knowledge, ArrayList<String> ignoredLibraries, int maxIterations, float importanceBalance, int logLevel, int threads){
		Problem.setLogLevel(3);
		Problem.clearLog();
		p.setResult(null);
		Problem res = p;
		Problem prevRes = null;
		int iteration = 0;
		if(Problem.breakdownStatements)
			p.expand("", Problem.breakdownAlsoCode);
		//create a rationalized copy of the knowledge pool
		Problem.log("Creating rationalized copy of knowledge pool ("+threads+" threads)", 1);
		ArrayList<Problem> problems = knowledge.getRationalized(p, ignoredLibraries, threads);
		Problem.log("Number of rationalized problems: "+problems.size(), 2);
		Problem.setLogLevel(logLevel);
		//iterate until solution or until max depth reached
		do{
			prevRes = res;
			iteration++;
			Problem.log("Iteration #"+iteration, 0);
			Problem.log("--------------------------------------------------------------------------------------", 1);
			res = new Problem(prevRes.getName());
			res.solve(prevRes, problems, "", importanceBalance, threads);
		}while(!res.isSolved() && !prevRes.isSame(res) && (iteration<maxIterations));
		res.setLibrary(p.getLibrary());
		Problem.log("Final pass (confirmation and detect statements that need solving)", 0);
		Problem.log("--------------------------------------------------------------------------------------", 1);
		pr.solve(res, problems, "solve: ", importanceBalance, threads);
		//rearranging statements (done in solve)
		/*for(int i=0;i<pr.statements.size();i++){
			Statement s = pr.statements.get(i);
			for(int j=i+1;j<pr.statements.size();j++)
				if(   pr.statements.get(j).isAssignment()
				   && s.hasVariable(pr.statements.get(j).getTrivialVariable())){
					Statement temp = pr.statements.get(j);
					pr.statements.remove(j);
					pr.statements.add(i, temp);
				}
		}*/
		if(Problem.breakdownStatements)
			p.collapse(Problem.breakdownAlsoCode, "");
		Problem.log("Total passes: "+iteration+" + final pass", 0);
		p.setResult(pr);
		Problem.setLogLevel(3);
	}
}
