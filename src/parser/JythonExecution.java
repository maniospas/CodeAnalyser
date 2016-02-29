package parser;

import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.HashMap;

import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JTextField;

import org.python.util.PythonInterpreter;

import analyser.Problem;
import analyser.Statement;

/**
 * <h1>JythonExecution</h1>
 * This class can be used to perform an algorithm execution according to 
 * Jython (a <i>Python</i> implementation for <i>Java</i>).
 * @author Manios Krasanakis
 */
public class JythonExecution {
	/**
	 * <h1>execute</h1>
	 * Creates a <i>Jython</i> interpreter for executing all problem statements
	 * one by one. Execution is <b>not</b> halted on errors. (This function uses
	 * the <code>Program.log</code> function to keep a track of its activities.)
	 * <br/>
	 * To interact with the user for variable inputs and outputs, the
	 * <code>inputVariables</code> and <code>showVariables</code> respectively.
	 * @param p : the problem for execution
	 * @return true if execution was successful
	 */
	public static boolean execute(Problem p){
		if(p.getResult()!=null)
			p = p.getResult();
		PythonInterpreter interpreter = null;
		try{
			interpreter = new PythonInterpreter();
			Object inputs [] = p.getInput().split(" ");
			String outputs [] = p.getOutput("").split(" ");
			HashMap<Object,String> evaluations = inputVariables(inputs, "Inputs");
			Problem.clearLog();
			for(Object input : inputs)
				if(evaluations.get(input)!=null){
					String str = input.toString()+" = "+evaluations.get(input);
					interpreter.exec(str);
					Problem.log(str, 2);
				}
			int errors = 0;
			for(Statement s : p.statements)
				if(!s.isInput() && !s.isOutput() && !s.isIterator()){
					Problem.log(s.toHTML(), 2);
					try{
						interpreter.exec(s.toString());
					}
					catch(Exception e){
						Problem.log(""+e, 0);
						errors++;
					}
				}
			evaluations.clear();
			for(String output : outputs)
			if(interpreter.get(output)!=null){
				Problem.log(output+" = "+interpreter.get(output).toString(), 2);
				evaluations.put(output, interpreter.get(output).toString());
			}
			Problem.log("Finished script execution with "+errors+" errors", 0);
			showVariables(outputs, evaluations, "Outputs");
		}
		catch(Exception e){
			e.printStackTrace();
			return false;
		}
		finally{
			if(interpreter!=null)
				interpreter.cleanup();
		}
		return true;
	}
	
	/**
	 * <h1>showVariables</h1>
	 * Creates a modal dialog (pauses execution) to show some variables
	 * and their corresponding values.
	 * @param variables : a list of variables t
	 * @param values : a hash map from variables to their values
	 * @param name : the dialog label
	 */
	protected static void showVariables(Object [] variables, HashMap<Object, String> values, String name){
		final JDialog frame = new JDialog();
		frame.setTitle(name);
		frame.setLayout(new GridLayout(0, 2));
		for(Object var : variables){
			frame.add(new JLabel(" "+var.toString()+" "));
			JTextField field = new JTextField(values.get(var));
			frame.add(field);
			field.setEnabled(false);
		}
		JButton apply = new JButton("Done");
		frame.add(apply);
		apply.addActionListener(new ActionListener(){
			public void actionPerformed(ActionEvent arg0) {
				frame.setVisible(false);
			}
		});
		frame.pack();
		frame.setModal(true);
		frame.setVisible(true);
	}
	/**
	 * <h1>inputVariables</h1>
	 * Creates a modal dialog (pauses execution) that allows the user to input
	 * string representation of values for a number of variables.
	 * @param variables : the list of variables
	 * @param name : the label of the dialog
	 * @return a hash map between the given variables and their inputed string values
	 */
	protected static HashMap<Object, String> inputVariables(final Object [] variables, String name){
		final HashMap<Object, String> values = new HashMap<Object, String>();
		final JDialog frame = new JDialog();
		frame.setTitle(name);
		frame.setLayout(new GridLayout(0, 2));
		final HashMap<Object, JTextField> fields = new HashMap<Object, JTextField>();
		for(Object var : variables){
			frame.add(new JLabel(" "+var.toString()+" "));
			JTextField field = new JTextField("");
			frame.add(field);
			fields.put(var, field);
		}
		JButton apply = new JButton("Done");
		frame.add(apply);
		apply.addActionListener(new ActionListener(){
			public void actionPerformed(ActionEvent arg0) {
				String err = "";
				for(Object var : variables){
					if(fields.get(var).getText().isEmpty())
						err += "- "+var.toString()+"\n";
					else
						values.put(var, fields.get(var).getText());
				}
				if(err.isEmpty())
					frame.setVisible(false);
				else
					JOptionPane.showMessageDialog(null, "Missing values for:\n"+err);
			}
		});
		frame.pack();
		frame.setModal(true);
		frame.setVisible(true);
		return values;
	}
}
