package display;

import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.Image;
import java.awt.KeyEventDispatcher;
import java.awt.KeyboardFocusManager;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.LineNumberReader;
import java.util.ArrayList;
import java.util.Arrays;

import javax.swing.*;
import javax.swing.event.*;

import parser.CodeParser;
import parser.JythonExecution;

import solver.Solver;

import analyser.Knowledge;
import analyser.Problem;
import analyser.Statement;

public class Main extends JFrame{
	private static final long serialVersionUID = 1L;
	private static final int version = 14;
	private static final String credits = "Programmer: Manios Krasanakis";
	
	//the knowledge pool
	private Knowledge knowledge;
	//a tabbed pane for viewing and editing problems
	private JTabbedPane tabbedPane;
	//a dialog that shows information about the solution progress
	private static JDialog solveDialog = null;
	//the text that appears in the solveDialog (it is updated by Problem.log while Problem.solve is running)
	public static JTextPane log = null;
	//set to true to exit without saving
	private boolean hardExit = false;
	
	/**
	 * <h1>getScaledIcon</h1>
	 * Creates an icon that can be used for buttons.
	 * @param path : path of the icon's image file
	 * @param size : size of the icon
	 * @return an ImageIcon
	 */
	private static ImageIcon getScaledIcon(String path, int size){
		ImageIcon icon = new ImageIcon(path);
		Image img = icon.getImage();  
		Image newimg = img.getScaledInstance( size, size*img.getHeight(null)/img.getWidth(null),  java.awt.Image.SCALE_SMOOTH ) ;  
		icon = new ImageIcon( newimg );
		return icon;
	}
	
	public void requestSolveProblem(final Problem pr, final Problem p){
		final JDialog dialog = new JDialog(this);
		dialog.setUndecorated(true);
		dialog.setLayout(null);
		dialog.getRootPane().setBorder( BorderFactory.createLineBorder(Color.BLACK) );
		dialog.setLocation(getWidth()/2, getHeight()/2);
		dialog.setSize(200, 290);
		dialog.setDefaultCloseOperation(DISPOSE_ON_CLOSE);
		JButton cancel = new JButton(getScaledIcon("data/images/undo.png", 30));
		cancel.setBounds(170, 0, 30, 30);
		dialog.add(cancel);
		cancel.setToolTipText("Cancel");
		JButton run = new JButton("Run...");
		run.setBounds(0, 0, 170, 30);
		dialog.add(run);
		final JSlider maxIterations = new JSlider(JSlider.HORIZONTAL, 1, knowledge.getSolvedProblems().size()*10, 1+knowledge.getSolvedProblems().size());
		maxIterations.setBounds(100, 30, 100, 25);
		dialog.add(maxIterations);
		final JLabel iterationShow = new JLabel(" Max iterations: "+maxIterations.getValue());
		iterationShow.setBounds(0, 30, 100, 25);
		dialog.add(iterationShow);
		maxIterations.addChangeListener(new ChangeListener(){
			@Override
			public void stateChanged(ChangeEvent arg0) {
				iterationShow.setText(" Max iterations: "+maxIterations.getValue());
			}
		});
		final JSlider importanceBalance = new JSlider(JSlider.HORIZONTAL, 0, 15, 5);
		importanceBalance.setBounds(100, 55, 100, 25);
		dialog.add(importanceBalance);
		final JLabel importanceBalanceShow = new JLabel(" Conserve: "+importanceBalance.getValue()*10+"%");
		importanceBalanceShow.setBounds(0, 55, 100, 25);
		dialog.add(importanceBalanceShow);
		importanceBalance.addChangeListener(new ChangeListener(){
			@Override
			public void stateChanged(ChangeEvent arg0) {
				importanceBalanceShow.setText(" Conserve: "+importanceBalance.getValue()*10+"%");
			}
		});
		final JSlider predicateSimilarityThreshold = new JSlider(JSlider.HORIZONTAL, 1, 10, (int)Math.round(Statement.predicateSimilarityThreshold*10));
		predicateSimilarityThreshold.setBounds(100, 80, 100, 25);
		dialog.add(predicateSimilarityThreshold);
		final JLabel predicateSimilarityThresholdShow = new JLabel(" Similarity: "+predicateSimilarityThreshold.getValue()*10+"%");
		predicateSimilarityThresholdShow.setBounds(0, 80, 100, 25);
		dialog.add(predicateSimilarityThresholdShow);
		predicateSimilarityThreshold.addChangeListener(new ChangeListener(){
			@Override
			public void stateChanged(ChangeEvent arg0) {
				predicateSimilarityThresholdShow.setText(" Similarity: "+predicateSimilarityThreshold.getValue()*10+"%");
			}
		});
		final JCheckBox logLevel = new JCheckBox("Log details", false);
		logLevel.setBounds(0, 105, 100, 25);
		dialog.add(logLevel);
		final JCheckBox threads = new JCheckBox("Multi-threaded", true);
		threads.setBounds(100, 105, 100, 25);
		dialog.add(threads);
		final CheckBoxList list = new CheckBoxList();
		JScrollPane scroller = new JScrollPane(list);
		scroller.setBounds(0, 130, 200, 160);
		dialog.add(scroller);
		dialog.setVisible(true);
		list.addCheckbox(new JCheckBox("Problems without library", false));
		for(String str : knowledge.getLibraryList()){
			list.addCheckbox(new JCheckBox(str, true));
		}
		cancel.addActionListener(new ActionListener(){
			@Override
			public void actionPerformed(ActionEvent arg0) {
				dialog.setVisible(false);
			}
		});
		run.addActionListener(new ActionListener(){
			@Override
			public void actionPerformed(ActionEvent arg0) {
				ArrayList<String> ignoredLibraries = new ArrayList<String>();
				if(!((JCheckBox)list.getModel().getElementAt(0)).isSelected())
					ignoredLibraries.add("");
				for(int i=1;i<list.getModel().getSize();i++)
					if(!((JCheckBox)list.getModel().getElementAt(i)).isSelected())
						ignoredLibraries.add(((JCheckBox)list.getModel().getElementAt(i)).getText());
				dialog.setVisible(false);
				Statement.predicateSimilarityThreshold = predicateSimilarityThreshold.getValue()/10.0f;
				solveProblem(pr, p, ignoredLibraries, maxIterations.getValue(), importanceBalance.getValue()/10.0f, logLevel.isSelected()?3:2, threads.isSelected()?Runtime.getRuntime().availableProcessors():1);
			}
		});
	}
	
	/**
	 * <h1>solveProblem</h1>
	 * This function creates the solveDialog dialog and then creates a thread that calls the <code>solve</code>
	 * function for an empty problem with argument the problem that needs to be solved. That thread also calls
	 * the <code>addProblem</code> function when finished, thus adding the solution to the knowledge pool.
	 * @param pr : the empty problem in which the solution will be placed
	 * @param p : the problem that needs to be solved
	 * @param ignoredLibraries : a set of library names that will be ignored by the solve function
	 */
	public void solveProblem(final Problem pr, final Problem p, final ArrayList<String> ignoredLibraries, final int maxIterations, final float importanceBalance, final int logLevel, final int threads){
		if(solveDialog!=null)
			solveDialog.setVisible(false);
		solveDialog = new JDialog();
		log = new JTextPane();
		log.setEditable(false);
		JScrollPane scroller = new JScrollPane(log);
		log.setContentType("text/html");
		log.setDoubleBuffered(true);
		scroller.setBounds(10, 10, 200, 400);
		solveDialog.add(scroller);
		solveDialog.setSize(600, 400);
		solveDialog.getRootPane().setBorder( BorderFactory.createLineBorder(Color.BLACK) );
		solveDialog.setVisible(true);
		(new Thread(){
			@Override
			public void run(){
				Solver.solve(pr, p, knowledge, ignoredLibraries, maxIterations, importanceBalance, logLevel, threads);
				addProblem(pr);
			}
		}).start();
	}
	
	/**
	 * <h1>ProblemPanel</h1>
	 * This class extends JPanel and is used by the tabbed pane to display information about individual problems.
	 * It generates an environment, when problems can be freely edited. I also allows for calling the solve function
	 * of problems.
	 */
	private class ProblemPanel extends JPanel{
		private static final long serialVersionUID = 1L;
		private Problem p;
		private Knowledge knowledge;
		private JList<Statement> list;
		private DefaultListModel<Statement> listModel;
		private Statement editing = null;
		private JScrollPane expressionScroller;
		private JTextArea expression;
		private JTextField variables;
		private JTextField library;
		private JTextPane code;
		private JButton libraryButton;
		private JButton applyButton;
		private JButton deleteButton;
		private JButton cancelButton;
		private JComboBox<Problem> problems;
		private JButton export;
		private JButton simplifyButton;
		private JButton implementationButton;
		private JButton deleteEntryButton;
		
		private String expressionText = "";
		private String variablesText = "";
		private String libraryText = "";
		
		/**
		 * <h1>getProblem</h1>
		 * @return the problem bound to this panel
		 */
		public Problem getProblem(){
			return p;
		}
		
		public ProblemPanel(Problem pr, Knowledge kn){
			super();
			setLayout(null);
			p = pr;
			knowledge = kn;
			listModel = new DefaultListModel<Statement>();
			list = new JList<Statement>(listModel);
			final ImageIcon trivialIcon    = getScaledIcon("data/images/trivial.png", 15); 
			final ImageIcon nonTrivialIcon = getScaledIcon("data/images/nonTrivial.png", 15);  
			final ImageIcon sourceCodeIcon = getScaledIcon("data/images/sourceCode.png", 15);   
			final ImageIcon invalidIcon = getScaledIcon("data/images/invalid.png", 15);  
			final ImageIcon nonAffectingIcon = getScaledIcon("data/images/nonAffecting.png", 15);
			list.setCellRenderer(new DefaultListCellRenderer() {
				private static final long serialVersionUID = 1L;
				@Override
	            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
	                JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
	                if(value instanceof Statement && ((Statement)value).getExpression().compareTo("...")!=0) {
	                	if(((Statement)value).isInvalid())
	                		label.setIcon(invalidIcon);
	                	else if(!p.affectsOutput((Statement)value))
	                		label.setIcon(nonAffectingIcon);
	                	else if(((Statement)value).isSourceCode())
	                		label.setIcon(sourceCodeIcon);
	                	else if(((Statement)value).isTrivial())
	                		label.setIcon(trivialIcon);
	                	else
	                		label.setIcon(nonTrivialIcon);
	                	String text = ((Statement)value).toHTML();
	                	label.setText("<html><div style=\"width:200px;\">"+text+"</div><html>");
	                }
	                return label;
	            }
	        });
			JScrollPane listScroller = new JScrollPane(list);
			listScroller.setBounds(10, 10, 300, 400);
	        add(listScroller);
	        
			list.addListSelectionListener(
					new ListSelectionListener() {
						@Override
						public void valueChanged(ListSelectionEvent e) {
							select(list.getSelectedIndex());
						}
					}
				);
			
			code = new JTextPane();
			code.setContentType("text/html");
			code.setEditable(false);
			JScrollPane codeScroller = new JScrollPane(code);
			codeScroller.setBounds(650, 90, 300, 320);
	        add(codeScroller);
			
			simplifyButton = new JButton("Create implementation");
			simplifyButton.setBounds(650, 50, 300, 30);
			add(simplifyButton);
			
			implementationButton = new JButton("Open implementation");
			implementationButton.setIcon(getScaledIcon("data/images/add.png", 15));
			implementationButton.setToolTipText("Opens implementation problem in new tab");
			implementationButton.setBounds(650, 50, 300, 30);
			implementationButton.setVisible(false);
			add(implementationButton);
			
			deleteEntryButton = new JButton("Delete problem");
			deleteEntryButton.setBounds(10, 510, 300, 30);
			deleteEntryButton.setToolTipText("Deletes this problem");
			add(deleteEntryButton);
			deleteEntryButton.setIcon(getScaledIcon("data/images/invalid.png", 15));

			JButton parseButton = new JButton("Parse from text");
			parseButton.setToolTipText("Creates problem statements from text");
			parseButton.setBounds(10, 450, 300, 30);
			add(parseButton);
			parseButton.setIcon(getScaledIcon("data/images/sourceCode.png", 15));
			

			JButton closeButton = new JButton("Close tab");
			closeButton.setBounds(10, 420, 300, 30);
			closeButton.setToolTipText("Closes this problem's tab");
			add(closeButton);
			closeButton.setIcon(getScaledIcon("data/images/undo.png", 15));
			
			parseButton.addActionListener(new ActionListener(){
				@Override
				public void actionPerformed(ActionEvent arg0) {
					parse();
				}
			});
			
			
			simplifyButton.addActionListener(new ActionListener(){
				@Override
				public void actionPerformed(ActionEvent arg0) {
					Problem pr = new Problem(p.getName()+"_implement");
					for(Problem p : knowledge.getProblems())
						if(pr.getName().compareTo(p.getName())==0){
							knowledge.remove(p);
							//problems.removeItem(p);
							removeProblem(p);
							break;
						}
					knowledge.add(pr);
					p.setResult(pr);
					requestSolveProblem(pr, p);
				}
			});
			
			implementationButton.addActionListener(new ActionListener(){
				@Override
				public void actionPerformed(ActionEvent arg0) {
					addProblem(p.getResult());
				}
			});
			
			deleteEntryButton.addActionListener(new ActionListener(){
				@Override
				public void actionPerformed(ActionEvent arg0) {
					int reply = JOptionPane.showConfirmDialog(getParent(), "Really delete problem "+p.getName()+"?", "Confirm", JOptionPane.YES_NO_OPTION);
			        if(reply==JOptionPane.YES_OPTION){
			        	knowledge.remove(p.getResult());
						removeProblem(p.getResult());
						knowledge.remove(p);
						removeProblem(p);
			        }
				}
			});
			
			closeButton.addActionListener(new ActionListener(){
				@Override
				public void actionPerformed(ActionEvent arg0) {
					removeProblem(p);
			    }
			});
			
			expression = new JTextArea();
			variables = new JTextField();
			variables.setFont(new Font("Verdana", Font.PLAIN, 12));
			expression.setFont(new Font("Verdana", Font.PLAIN, 12));
			list.setFont(new Font("Verdana", Font.PLAIN, 12));
			expressionScroller = new JScrollPane(expression);
			expressionScroller.setBounds(320, 50, 300, 130);
			variables.setBounds(320, 10, 170, 30);
			expression.setBorder(variables.getBorder());
			problems = new JComboBox<Problem>();
			problems.setBounds(650, 10, 300, 30);
			add(problems);
			problems.addActionListener(new ActionListener(){
				@Override
				public void actionPerformed(ActionEvent e) {
					if(problems.getSelectedItem()!=null && !updating){
						p.setResult((Problem)problems.getSelectedItem());
						update();
					}
				}
			});

			problems.setRenderer(new DefaultListCellRenderer() {
				private static final long serialVersionUID = 1L;
				@Override
	            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
	                JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
	                if(value instanceof Problem) {
	                	label.setText("<html><div style=\"width:200px;\">"+((Problem)value).getName()+"</div><html>");
	                	if(value==Knowledge.emptyProblem){
	                		label.setIcon(getScaledIcon("data/images/invalid.png", 15));
		                	label.setText("<html><div style=\"width:200px;\">Clear implementation (will NOT delete it)</div><html>");
	                	}
	                	else if(((Problem)value).isSolved())
	                		label.setIcon(trivialIcon);
	                	else
	                		label.setIcon(nonTrivialIcon);
	                }
	                return label;
	            }
	        });
			export = new JButton("Export code");
			export.setIcon(getScaledIcon("data/images/sourceCode.png", 15));
			export.setBounds(650, 10, 300, 30);
			add(export);
			export.setVisible(false);
			export.addActionListener(new ActionListener(){
				@Override
				public void actionPerformed(ActionEvent arg0) {
					JFileChooser j = new JFileChooser();
					j.setFileSelectionMode(JFileChooser.FILES_ONLY);
					Problem originalProblem = knowledge.getOriginalProblem(p);
					j.setSelectedFile(new File(originalProblem.getName()+".py"));
					Integer opt = j.showSaveDialog(tabbedPane);
					if(opt==0){
						File target = j.getSelectedFile();
						if(target!=null && (!target.exists() || JOptionPane.showConfirmDialog(tabbedPane, "File "+target.getPath()+" already exists. Do you want to replace it?")==0)){
							String str = originalProblem.getComments("\"\"\"\n","\"\"\"")+"\n"+p.getCode(originalProblem.getName());
							try(FileOutputStream fos = new FileOutputStream(target.getPath())){
					    		for(int i=0;i<str.length();i++)
					    			fos.write(str.charAt(i));
					    		JOptionPane.showMessageDialog(tabbedPane, "Succesfully exported comments and code to "+target.getPath());
							}
							catch(Exception e){
								System.out.println("failed at exporting code to "+target.getAbsolutePath());
							}
				         }
					}
				}
			});
			
			deleteButton = new JButton(getScaledIcon("data/images/delete.png", 30));
			deleteButton.setToolTipText("Delete");
			deleteButton.setBounds(590, 10, 30, 30);
			deleteButton.addActionListener(new ActionListener(){
				@Override
				public void actionPerformed(ActionEvent arg0) {
					if(editing!=null){
						p.statements.remove(editing);
						select(-1);
						list.clearSelection();
						editing = null;
						variables.setVisible(editing!=null);
						expressionScroller.setVisible(editing!=null);
						applyButton.setVisible(editing!=null);
						deleteButton.setVisible(editing!=null);
						cancelButton.setVisible(editing!=null);
					}	
				}
			});
			cancelButton = new JButton(getScaledIcon("data/images/undo.png", 30));
			cancelButton.setToolTipText("Undo changes");
			cancelButton.setBounds(560, 10, 30, 30);
			cancelButton.addActionListener(new ActionListener(){
				@Override
				public void actionPerformed(ActionEvent arg0) {
					if(editing!=null){
						select(list.getSelectedIndex());
					}	
				}
			});
			library = new JTextField();
			library.setFont(new Font("Verdana", Font.PLAIN, 12));
			library.setBounds(10, 480, 300, 30);
			libraryText = p.getLibrary();
			add(library);
			libraryButton = new JButton(getScaledIcon("data/images/apply.png", 30));
			libraryButton.setBounds(310, 480, 30, 30);
			add(libraryButton);
			applyButton = new JButton(getScaledIcon("data/images/apply.png", 30));
			applyButton.setToolTipText("Apply changes");
			applyButton.setBounds(500, 10, 60, 30);
			applyButton.addActionListener(new ActionListener(){
				@Override
				public void actionPerformed(ActionEvent arg0) {
					if(editing!=null){
						boolean prevEmpty = editing.isEmpty();
						editing.setVariables(variablesText);
						editing.setExpression(expressionText);
						if(editing.isEmpty())
							p.statements.remove(editing);
						else if(prevEmpty)
							p.statements.add(editing);
						update();
					}	
				}
			});
			variables.setVisible(false);
			expressionScroller.setVisible(false);
			applyButton.setVisible(false);
			deleteButton.setVisible(false);
			cancelButton.setVisible(false);
			add(variables);
			add(expressionScroller);
			add(applyButton);
			add(deleteButton);
			add(cancelButton);
			update();
			
			variables.addFocusListener(new FocusListener(){
				@Override
	            public void focusGained(FocusEvent fe){
	                if(variablesText.isEmpty())
	                	variables.setText("");
	                variables.setForeground(Color.black);
	            }
				@Override
	            public void focusLost(FocusEvent fe){
					variablesText = variables.getText();
	                if(variablesText.isEmpty()){
	                	variables.setForeground(Color.gray);
	                    variables.setText("Variables");
	                }
	            }
	        });
			expression.addFocusListener(new FocusListener(){
				@Override
	            public void focusGained(FocusEvent fe){
	                if(expressionText.isEmpty())
	                	expression.setText("");
	                expression.setForeground(Color.black);
	            }
				@Override
	            public void focusLost(FocusEvent fe){
					expressionText = expression.getText();
	                if(expressionText.isEmpty()){
	                	expression.setForeground(Color.gray);
	                	expression.setText("Expression");
	                }
	            }
	        });
			
			libraryButton.addActionListener(new ActionListener(){
				@Override
				public void actionPerformed(ActionEvent e) {
					p.setLibrary(libraryText);
					library.setFocusable(false);
					library.setFocusable(true);
					update();
				}
			});
			library.addActionListener(libraryButton.getActionListeners()[0]);
			library.addFocusListener(new FocusListener(){
				@Override
	            public void focusGained(FocusEvent fe){
	                if(libraryText.isEmpty())
	                	library.setText("");
	                library.setForeground(Color.black);
	                libraryButton.setVisible(true);
	            }
				@Override
	            public void focusLost(FocusEvent fe){
					libraryText = library.getText();
	                if(libraryText.isEmpty()){
	                	library.setForeground(Color.gray);
	                	library.setText("Library");
	                }
	            }
	        });
		}
		
		public void subproblems(){
			Problem.setLogLevel(3);
			if(solveDialog!=null)
				solveDialog.setVisible(false);
			solveDialog = new JDialog();
			log = new JTextPane();
			log.setEditable(false);
			JScrollPane scroller = new JScrollPane(log);
			log.setContentType("text/html");
			log.setDoubleBuffered(true);
			scroller.setBounds(10, 10, 200, 400);
			solveDialog.add(scroller);
			solveDialog.setSize(600, 400);
			solveDialog.getRootPane().setBorder( BorderFactory.createLineBorder(Color.BLACK) );
			solveDialog.setVisible(true);
			Problem.clearLog();
			Problem.log("Analyzing problem <i>"+p.getName()+"</i>", 0);
			Problem p = new Problem(this.p, "");
			ArrayList<Problem> subproblems = p.getSubproblems();
			Problem dep = p.getNonClassProblem();
			ArrayList<Problem> subproblemsDep = (dep!=null)?dep.getSubproblems():null;
			Problem.log("Displaying detected subproblems", 0);
			Problem.log("<table>", -1);
			Problem.log("<tr><th align=\"left\">Problem</th><td></td><th align=\"left\">Implementation</th></tr>", -1);
			for(Problem subproblem : subproblems){
				Problem.log("<tr>", -1);
				int information = 0;
				for(Statement st : subproblem.statements)
					information += st.getInformation();
				Problem.log("<td align=\"left\" valign=\"top\">", -1);
				Problem.log(subproblem.getName()+" ("+information+" bits)", 1);
				Problem.log(subproblem.getHTMLCode(), -1);
				Problem.log("</td>", -1);
				Problem.log("<td align=\"left\" valign=\"top\">&nbsp&nbsp&gt&gt&gt&gt&gt&nbsp&nbsp</td>", -1);
				if(subproblem.getResult()!=null){
					information = 0;
					for(Statement st : subproblem.getResult().statements)
						information += st.getInformation();
					Problem.log("<td align=\"left\" valign=\"top\">", -1);
					Problem.log(subproblem.getResult().getName()+" ("+information+" bits)", 1);
					Problem.log(subproblem.getResult().getHTMLCode(), -1);
					Problem.log("</td>", -1);
				}
				else{
					Problem.log("<td align=\"left\" valign=\"top\">", -1);
					Problem.log("No implementation", 1);
					Problem.log("</td>", -1);
				}
				Problem.log("</tr>", -1);
			}
			if(subproblemsDep!=null){
				for(Problem subproblem : subproblemsDep){
					Problem.log("<tr>", -1);
					int information = 0;
					for(Statement st : subproblem.statements)
						information += st.getInformation();
					Problem.log("<td align=\"left\" valign=\"top\">", -1);
					Problem.log(subproblem.getName()+" ("+information+" bits)", 1);
					Problem.log(subproblem.getHTMLCode(), -1);
					Problem.log("</td>", -1);
					Problem.log("<td align=\"left\" valign=\"top\">&nbsp&nbsp&gt&gt&gt&gt&gt&nbsp&nbsp</td>", -1);
					if(subproblem.getResult()!=null){
						information = 0;
						for(Statement st : subproblem.getResult().statements)
							information += st.getInformation();
						Problem.log("<td align=\"left\" valign=\"top\">", -1);
						Problem.log(subproblem.getResult().getName()+" ("+information+" bits)", 1);
						Problem.log(subproblem.getResult().getHTMLCode(), -1);
						Problem.log("</td>", -1);
					}
					else{
						Problem.log("<td align=\"left\" valign=\"top\">", -1);
						Problem.log("No implementation", 1);
						Problem.log("</td>", -1);
					}
					Problem.log("</tr>", -1);
				}
			}
			Problem.log("</table>", -1);
			Problem.log("Finished analysis", 0);
			log.setText(Problem.getLog());
		}
		
		public void parse(){
			JTextArea parseText = new JTextArea(parser.LanguageParser.language(p), 20, 20);
			parseText.setFont(new Font("Verdana", Font.PLAIN, 12));
			
			int option = JOptionPane.showConfirmDialog(this,
					new JScrollPane(parseText),
                    "Text parser",
                    JOptionPane.OK_CANCEL_OPTION,
                    JOptionPane.PLAIN_MESSAGE,
                    null);

			if(option == JOptionPane.OK_OPTION) {
				String text = parseText.getText();
				parser.LanguageParser.parse(p, text);
				p.refresh();
				editing = null;
				update();
			}
		}

		private boolean updating = false;
		protected void update(){
			if(updating)
				return;
			updating = true;
			int prevSel = list.getSelectedIndex();
			listModel.clear();
			for(Statement s : p.statements)
				listModel.addElement(s);
			listModel.addElement(new Statement("", "..."));
			list.setSelectedIndex(prevSel);
			
			variables.setVisible(editing!=null);
			expressionScroller.setVisible(editing!=null);
			applyButton.setVisible(editing!=null);
			deleteButton.setVisible(editing!=null);
			cancelButton.setVisible(editing!=null);
			
			if(editing!=null){
				expressionText = editing.getExpression();
	            if(editing.isEmpty())
	            	expression.setText("Expression");
	            variablesText = editing.getVariables();
	            if(editing.variables.size()==0)
	                variables.setText("Variables");
            	variables.setForeground(Color.gray);
            	expression.setForeground(Color.gray);
			}
			libraryText = p.getLibrary();
			library.setText(p.getLibrary().isEmpty()?"Library":p.getLibrary());
	        expression.setForeground(Color.gray);
            libraryButton.setVisible(false);
            library.setForeground(Color.gray);
			
			problems.removeAllItems();
			for(Problem pr : knowledge.getProblems())
				if(pr!=p && pr.getResult()==null && pr.getLibrary().compareTo(p.getLibrary())==0)
					problems.addItem(pr);
			if(p.getResult()==null)
				problems.setSelectedIndex(-1);
			else{
				problems.setSelectedItem(p.getResult());
				p.setResult((Problem)problems.getSelectedItem());
			}
				
			if(p.getResult()==null)
				code.setText(p.getHTMLCode());
			else
				code.setText(p.getResult().getHTMLCode());
			
			simplifyButton.setVisible(p.getResult()==null);
			simplifyButton.setEnabled(!knowledge.isImplementation(p));
			if(simplifyButton.isEnabled()){
				simplifyButton.setText("Create implementation");
				simplifyButton.setToolTipText("Automatically generate implementation");
				library.setVisible(true);
				problems.setVisible(!p.isSolved());
				simplifyButton.setIcon(getScaledIcon("data/images/add.png", 15));
			}
			else{
				simplifyButton.setText("This is an implementation");
				simplifyButton.setToolTipText("This implements another problem");
				library.setVisible(false);
				libraryButton.setVisible(false);
				problems.setVisible(false);
				simplifyButton.setIcon(getScaledIcon("data/images/trivial.png", 15));
			}
			export.setVisible(true);

			implementationButton.setVisible(p.getResult()!=null);
			
			updating = false;
		}
		
		protected void select(int id){
			editing = null;
			if(id==p.statements.size())
				editing = new Statement();
			else if(id!=-1)
				editing = p.statements.get(id);
			if(editing!=null){
				expression.setText(editing.getExpression());
				variables.setText(editing.getVariables());
			}
			update();
		}

		public void expand() {
			p.expand("", Problem.breakdownAlsoCode);
			update();
		}
		public void collapse() {
			p.collapse(Problem.breakdownAlsoCode, "temp");
			update();
		}
	}
	
	/**
	 * <h1>Main</h1>
	 * The constructor for this frame. It initializes and loads the knowledge pool and creates the
	 * tabbed pane and its listeners.
	 */
	public Main(){
		super("Code Analyzer");
		setResizable(false);
		ToolTipManager.sharedInstance().setInitialDelay(0);
		try{
			UIManager.setLookAndFeel("com.sun.java.swing.plaf.windows.WindowsLookAndFeel");
		}
		catch(Exception e){
			e.printStackTrace();
		}
		//load endings
		try(LineNumberReader lnr = new LineNumberReader(new FileReader(new File("data/endings.txt")))){
			String str = "";
			String line;
		    while((line = lnr.readLine()) != null) {
		    	if(!str.isEmpty())
		    		str += "\n";
		    	str += line;
		    }
		    Statement.endings = str.split("\n");
    	}
		catch(Exception e){
		}
		//load separators
		try(LineNumberReader lnr = new LineNumberReader(new FileReader(new File("data/separators.txt")))){
			String str = "";
			String line;
		    while((line = lnr.readLine()) != null) {
		    	if(!str.isEmpty())
		    		str += "\n";
		    	str += line;
		    }
			ArrayList<String> lines = new ArrayList<String>(Arrays.asList(str.split("\n")));
			for(int i=lines.size()-1;i>=0;i--)
				if(lines.get(i).trim().isEmpty())
					lines.remove(i);
				else
					lines.set(i, lines.get(i).trim());
			Statement.separators = new String[lines.size()];
			Statement.inverses = new String[lines.size()];
			for(int i=0;i<lines.size();i++){
				if(lines.get(i).indexOf("@")==-1){
					Statement.separators[i] = lines.get(i);
					Statement.inverses[i] = lines.get(i);
					continue;
				}
				Statement.separators[i] = lines.get(i).substring(0, lines.get(i).indexOf("@")).trim();
				Statement.inverses[i] = lines.get(i).substring(lines.get(i).indexOf("@")+1, lines.get(i).length()).trim();
				if(Statement.inverses[i].isEmpty())
					Statement.inverses[i] = Statement.separators[i];
			}
		}
		catch(Exception e){
		}
		//load custom comments
		try(LineNumberReader lnr = new LineNumberReader(new FileReader(new File("data/comments.txt")))){
			String str = "";
			String line;
		    while((line = lnr.readLine()) != null) {
		    	if(!str.isEmpty())
		    		str += "\n";
		    	str += line;
		    }
		    Statement.customComments = str.split("\n");
    	}
		catch(Exception e){
		}
		//load ands
		try(LineNumberReader lnr = new LineNumberReader(new FileReader(new File("data/ands.txt")))){
			String str = "";
			String line;
		    while((line = lnr.readLine()) != null) {
		    	if(!str.isEmpty())
		    		str += "\n";
		    	str += line;
		    }
		    Statement.languageAnd = str.split("\n");
    	}
		catch(Exception e){
		}
		//load ignored
		try(LineNumberReader lnr = new LineNumberReader(new FileReader(new File("data/ignored.txt")))){
			String str = "";
			String line;
		    while((line = lnr.readLine()) != null) {
		    	if(!str.isEmpty())
		    		str += "\n";
		    	str += line;
		    }
		    Statement.ignored = str.split("\n");
    	}
		catch(Exception e){
		}
		//load knowledge
		knowledge = new Knowledge();
		knowledge.load("data/knowledge.xml");
		
		//
		setSize(1000, 630);
		tabbedPane = new JTabbedPane();
		tabbedPane.add("...", null);
		tabbedPane.setSelectedIndex(-1);
		tabbedPane.addChangeListener(new ChangeListener(){
			@Override
			public void stateChanged(ChangeEvent arg0) {
				if(!changing && tabbedPane.getSelectedIndex()==tabbedPane.getTabCount()-1){
					selectProblem();
				}
				else if(tabbedPane.getSelectedComponent()!=null)
					((ProblemPanel)tabbedPane.getSelectedComponent()).update();
			}
		});
		//create menu
		JMenuBar menu = new JMenuBar();
		setJMenuBar(menu);
		//file menu
		JMenu fileMenu = new JMenu("Problems");
		menu.add(fileMenu);
		//file menu - open
		JMenuItem problemManage = new JMenuItem("Open");
		problemManage.setIcon(getScaledIcon("data/images/open.png", 15));
		fileMenu.add(problemManage);
		problemManage.addActionListener(new ActionListener(){
			@Override
			public void actionPerformed(ActionEvent arg0) {
				selectProblem();
			}
		});
		//file menu - new
		JMenuItem problemNew = new JMenuItem("Create & Open");
		problemNew.setIcon(getScaledIcon("data/images/create.png", 15));
		fileMenu.add(problemNew);
		problemNew.addActionListener(new ActionListener(){
			@Override
			public void actionPerformed(ActionEvent arg0) {
				createProblem();
			}
		});
		
		//file menu - import
		fileMenu.addSeparator();
		JMenuItem fileImport = new JMenuItem("Import");
		fileImport.setIcon(getScaledIcon("data/images/import.png", 15));
		fileMenu.add(fileImport);
		fileImport.addActionListener(new ActionListener(){
			@Override
			public void actionPerformed(ActionEvent arg0) {
				JFileChooser j = new JFileChooser();
				j.setFileSelectionMode(JFileChooser.FILES_ONLY);
				j.setSelectedFile(new File(""));
				Integer opt = j.showOpenDialog(tabbedPane);
				if(opt==0){
					File target = j.getSelectedFile();
					if(target!=null && target.exists()){
						try(LineNumberReader lnr = new LineNumberReader(new FileReader(target))){
							String str = "";
							String line;
						    while((line = lnr.readLine()) != null) {
						    	str += line+"\n";
						    }
						    importString(str, target.getName().substring(0, target.getName().lastIndexOf(".")));
				    	}
						catch(Exception e){
							System.out.println("failed at importing code from "+target.getAbsolutePath());
							e.printStackTrace();
						}
			         }
				}
			}
		});
		//file menu - export
		JMenuItem fileExport = new JMenuItem("Export");
		fileExport.setIcon(getScaledIcon("data/images/export.png", 15));
		fileMenu.add(fileExport);
		fileExport.addActionListener(new ActionListener(){
			@Override
			public void actionPerformed(ActionEvent arg0) {
				JList<String> list = new JList<String>(new DefaultListModel<String>());
				list.setFont(new Font("Verdana", Font.PLAIN, 12));
				((DefaultListModel<String>)list.getModel()).addElement("Problems without library");
				for(String str : knowledge.getLibraryList())
					((DefaultListModel<String>)list.getModel()).addElement(str);
				list.setSelectedIndex(0);
				int option = JOptionPane.showConfirmDialog(tabbedPane,
						list,
	                    "Library to export",
	                    JOptionPane.OK_CANCEL_OPTION,
	                    JOptionPane.PLAIN_MESSAGE,
	                    null);
				if(option == JOptionPane.OK_OPTION){
					String libName = list.getSelectedValue();
					if(list.getSelectedIndex()==0)
						libName = "temp";
					JFileChooser j = new JFileChooser();
					j.setFileSelectionMode(JFileChooser.FILES_ONLY);
					j.setSelectedFile(new File(libName+".py"));
					if(list.getSelectedIndex()==0)
						libName = "";
					Integer opt = j.showSaveDialog(tabbedPane);
					if(opt==0){
						File target = j.getSelectedFile();
						if(target!=null && (!target.exists() || JOptionPane.showConfirmDialog(tabbedPane, "File "+target.getPath()+" already exists. Do you want to replace it?")==0)){
							String str = CodeParser.parseExport(knowledge, libName);
							try(FileOutputStream fos = new FileOutputStream(target.getPath())){
					    		for(int i=0;i<str.length();i++)
					    			fos.write(str.charAt(i));
					    		JOptionPane.showMessageDialog(tabbedPane, "Succesfully exported comments and code to "+target.getPath()+".");
					        }
							catch(Exception e){
								System.out.println("failed at exporting code to "+target.getAbsolutePath());
							}
				         }
					}
				}
			}
		});
		fileMenu.addSeparator();
		//file menu - exit
		JMenuItem fileExit = new JMenuItem("Save & Exit");
		fileExit.setIcon(getScaledIcon("data/images/exit.png", 15));
		fileMenu.add(fileExit);
		fileExit.addActionListener(new ActionListener(){
			@Override
			public void actionPerformed(ActionEvent arg0) {
				save();
				System.exit(0);
			}
		});
		//file menu - hard exit
		JMenuItem fileHardExit = new JMenuItem("Hard Exit");
		fileHardExit.setIcon(getScaledIcon("data/images/hardExit.png", 15));
		fileMenu.add(fileHardExit);
		fileHardExit.addActionListener(new ActionListener(){
			@Override
			public void actionPerformed(ActionEvent arg0) {
				hardExit = true;
				System.exit(0);
			}
		});
		//problem menu
		JMenu problemMenu = new JMenu("Problem");
		menu.add(problemMenu);
		//problem menu - subproblems
		JMenuItem subproblems = new JMenuItem("Subproblem Analysis");
		subproblems.setIcon(getScaledIcon("data/images/nonTrivial.png", 15));
		problemMenu.add(subproblems);
		subproblems.addActionListener(new ActionListener(){
			@Override
			public void actionPerformed(ActionEvent e) {
				if(tabbedPane.getSelectedComponent()!=null)
					((ProblemPanel)tabbedPane.getSelectedComponent()).subproblems();
			}
		});
		//problem menu - auto_correct
		JMenuItem auto_correct = new JMenuItem("Auto Correct");
		auto_correct.setIcon(getScaledIcon("data/images/nonTrivial.png", 15));
		problemMenu.add(auto_correct);
		auto_correct.addActionListener(new ActionListener(){
			@Override
			public void actionPerformed(ActionEvent e) {
				Problem p;
				if((p = ((ProblemPanel)tabbedPane.getSelectedComponent()).getProblem())!=null){
					String text = parser.LanguageParser.language(p);
					parser.LanguageParser.parse(p, text);
					p.sort();
					p.refresh();
					((ProblemPanel)tabbedPane.getSelectedComponent()).update();
				}
			}
		});
		//problem menu - expand
		JMenuItem expand = new JMenuItem("Expand");
		expand.setIcon(getScaledIcon("data/images/nonTrivial.png", 15));
		problemMenu.add(expand);
		expand.addActionListener(new ActionListener(){
			@Override
			public void actionPerformed(ActionEvent e) {
				if(tabbedPane.getSelectedComponent()!=null)
					((ProblemPanel)tabbedPane.getSelectedComponent()).expand();
			}
		});
		//problem menu - collapse
		JMenuItem collapse = new JMenuItem("Collapse");
		collapse.setIcon(getScaledIcon("data/images/nonTrivial.png", 15));
		problemMenu.add(collapse);
		collapse.addActionListener(new ActionListener(){
			@Override
			public void actionPerformed(ActionEvent e) {
				if(tabbedPane.getSelectedComponent()!=null)
					((ProblemPanel)tabbedPane.getSelectedComponent()).collapse();
			}
		});
		//problem menu - execute
		JMenuItem execute = new JMenuItem("Jython Execute");
		execute.setIcon(getScaledIcon("data/images/nonTrivial.png", 15));
		problemMenu.add(execute);
		execute.addActionListener(new ActionListener(){
			@Override
			public void actionPerformed(ActionEvent e) {
				if(tabbedPane.getSelectedComponent()!=null){
					Problem.setLogLevel(3);
					if(solveDialog!=null)
						solveDialog.setVisible(false);
					solveDialog = new JDialog();
					log = new JTextPane();
					log.setEditable(false);
					JScrollPane scroller = new JScrollPane(log);
					log.setContentType("text/html");
					log.setDoubleBuffered(true);
					scroller.setBounds(10, 10, 200, 400);
					solveDialog.add(scroller);
					solveDialog.setSize(600, 400);
					solveDialog.getRootPane().setBorder( BorderFactory.createLineBorder(Color.BLACK) );
					solveDialog.setVisible(true);
					JythonExecution.execute(((ProblemPanel)tabbedPane.getSelectedComponent()).getProblem());
				}
			}
		});
		//options menu
		JMenu optionsMenu = new JMenu("Language");
		menu.add(optionsMenu);
		//options menu - endings
		JMenuItem endings = new JMenuItem("Endings");
		endings.setIcon(getScaledIcon("data/images/endings.png", 15));
		optionsMenu.add(endings);
		endings.addActionListener(new ActionListener(){
			@Override
			public void actionPerformed(ActionEvent e) {
				editEndings();
			}
		});
		//options menu - ignored
		JMenuItem ignored = new JMenuItem("Ignored");
		ignored.setIcon(getScaledIcon("data/images/ignored.png", 15));
		optionsMenu.add(ignored);
		ignored.addActionListener(new ActionListener(){
			@Override
			public void actionPerformed(ActionEvent e) {
				editIgnored();
			}
		});
		//options menu - separators
		JMenuItem separators = new JMenuItem("Separators");
		separators.setIcon(getScaledIcon("data/images/separators.png", 15));
		optionsMenu.add(separators);
		separators.addActionListener(new ActionListener(){
			@Override
			public void actionPerformed(ActionEvent e) {
				editSeparators();
			}
		});
		//options menu - ands
		JMenuItem ands = new JMenuItem("Logical 'and's");
		ands.setIcon(getScaledIcon("data/images/ands.png", 15));
		optionsMenu.add(ands);
		ands.addActionListener(new ActionListener(){
			@Override
			public void actionPerformed(ActionEvent e) {
				editAnds();
			}
		});
		//options menu - custom comments
		JMenuItem customComments = new JMenuItem("Custom comments");
		customComments.setIcon(getScaledIcon("data/images/comments.png", 15));
		optionsMenu.add(customComments);
		customComments.addActionListener(new ActionListener(){
			@Override
			public void actionPerformed(ActionEvent e) {
				editComments();
			}
		});
		//feature menu
		JMenu featureMenu = new JMenu("Analysis");
		menu.add(featureMenu);
		//options menu - custom comments
		final JMenuItem breakdown = new JMenuItem("Expand for analysis");
		featureMenu.add(breakdown);
		breakdown.setSelected(Problem.breakdownStatements);
		if(Problem.breakdownStatements)
			breakdown.setIcon(getScaledIcon("data/images/ticked.png", 15));
		else
			breakdown.setIcon(getScaledIcon("data/images/unticked.png", 15));
		breakdown.addActionListener(new ActionListener(){
			@Override
			public void actionPerformed(ActionEvent e) {
				Problem.breakdownStatements = !Problem.breakdownStatements;
				if(Problem.breakdownStatements)
					breakdown.setIcon(getScaledIcon("data/images/ticked.png", 15));
				else
					breakdown.setIcon(getScaledIcon("data/images/unticked.png", 15));
			}
		});
		final JMenuItem constants = new JMenuItem("Constants as Variables");
		featureMenu.add(constants);
		constants.setSelected(Problem.constantsAreVariables);
		if(Problem.constantsAreVariables)
			constants.setIcon(getScaledIcon("data/images/ticked.png", 15));
		else
			constants.setIcon(getScaledIcon("data/images/unticked.png", 15));
		constants.addActionListener(new ActionListener(){
			@Override
			public void actionPerformed(ActionEvent e) {
				Problem.constantsAreVariables = !Problem.constantsAreVariables;
				if(Problem.constantsAreVariables)
					constants.setIcon(getScaledIcon("data/images/ticked.png", 15));
				else
					constants.setIcon(getScaledIcon("data/images/unticked.png", 15));
			}
		});
		final JMenuItem breakdownCode = new JMenuItem("Allow source code expansion");
		featureMenu.add(breakdownCode);
		if(Problem.breakdownAlsoCode)
			breakdownCode.setIcon(getScaledIcon("data/images/ticked.png", 15));
		else
			breakdownCode.setIcon(getScaledIcon("data/images/unticked.png", 15));
		breakdownCode.addActionListener(new ActionListener(){
			@Override
			public void actionPerformed(ActionEvent e) {
				Problem.breakdownAlsoCode = !Problem.breakdownAlsoCode;
				if(Problem.breakdownAlsoCode)
					breakdownCode.setIcon(getScaledIcon("data/images/ticked.png", 15));
				else
					breakdownCode.setIcon(getScaledIcon("data/images/unticked.png", 15));
			}
		});
		//help menu
		JMenu helpMenu = new JMenu("Help");
		menu.add(helpMenu);
		//help menu - about
		JMenuItem about = new JMenuItem("About");
		about.setIcon(getScaledIcon("data/images/about.png", 15));
		helpMenu.add(about);
		about.addActionListener(new ActionListener(){
			@Override
			public void actionPerformed(ActionEvent e) {
				JOptionPane.showMessageDialog(null, "<html><h1>Code Analyser</h1>Version number: "+version+"<br/>Computer threads: "+Runtime.getRuntime().availableProcessors()+"<br/>"+credits+"</html>", "About", -1);
			}
		});
		//help menu - documentation
		JMenuItem documentation = new JMenuItem("Documentation");
		documentation.setIcon(getScaledIcon("data/images/documentation.png", 15));
		helpMenu.add(documentation);
		documentation.addActionListener(new ActionListener(){
			@Override
			public void actionPerformed(ActionEvent e) {
				showDocumentation();
			}
		});
		//create tabs
		add(tabbedPane);
		//this.setDefaultCloseOperation(EXIT_ON_CLOSE);
		this.addWindowListener(new WindowAdapter() {
			@Override
			public void windowClosing(WindowEvent we){ 
				if(hardExit==false){
					save();
				}
				//exit
				System.exit(0);
			}
		});
	}

	/**
	 * <h1>save</h1>
	 * This function saves everything.
	 */
	public void save(){
		//save endings
		try(FileOutputStream fos = new FileOutputStream(new File("data/endings.txt"))){
			String str = "";
			for(String end : Statement.endings)
				str += end+"\n";
    		for(int i=0;i<str.length();i++)
    			fos.write(str.charAt(i));
    	}
		catch(Exception e){
			System.out.println("failed at saving endings in data/endings.txt");
		}
		//save separators
		try(FileOutputStream fos = new FileOutputStream(new File("data/separators.txt"))){
			String str = "";
			for(int i=0;i<Statement.separators.length;i++){
				str += Statement.separators[i];
				if(Statement.separators[i].compareTo(Statement.inverses[i])!=0)
					str += " @ "+Statement.inverses[i];
				str += "\n";
			}
    		for(int i=0;i<str.length();i++)
    			fos.write(str.charAt(i));
    	}
		catch(Exception e){
			System.out.println("failed at saving separators in data/separators.txt");
		}
		//save custom comments
		try(FileOutputStream fos = new FileOutputStream(new File("data/comments.txt"))){
			String str = "";
			for(String ign : Statement.customComments)
				str += ign+"\n";
    		for(int i=0;i<str.length();i++)
    			fos.write(str.charAt(i));
    	}
		catch(Exception e){
			System.out.println("failed at saving custom comments in data/comments.txt");
		}
		//save logical ands
		try(FileOutputStream fos = new FileOutputStream(new File("data/ands.txt"))){
			String str = "";
			for(String ign : Statement.languageAnd)
				str += ign+"\n";
    		for(int i=0;i<str.length();i++)
    			fos.write(str.charAt(i));
    	}
		catch(Exception e){
			System.out.println("failed at saving logical ands in data/ands.txt");
		}
		//save ignored
		try(FileOutputStream fos = new FileOutputStream(new File("data/ignored.txt"))){
			String str = "";
			for(String ign : Statement.ignored)
				str += ign+"\n";
    		for(int i=0;i<str.length();i++)
    			fos.write(str.charAt(i));
    	}
		catch(Exception e){
			System.out.println("failed at saving ignored in data/ignored.txt");
		}
		//save knowledge
		knowledge.save("data/knowledge.xml");
	}
	
	/**
	 * <h1>selectProblem</h1>
	 * Opens a dialog that allows the user to select a problem. The same dialog allows the user
	 * to create new problems.
	 */
	public void selectProblem(){
		final JDialog dialog = new JDialog(this);
		dialog.setLayout(null);
		dialog.getRootPane().setBorder( BorderFactory.createLineBorder(Color.BLACK) );
		final JComboBox<Problem> problems = new JComboBox<Problem>();
		for(Problem p : knowledge.getProblems())
			if(p!=Knowledge.emptyProblem && p.getLibrary().isEmpty() && !knowledge.isImplementation(p))
				problems.addItem(p);
		problems.setBounds(10, 10, 200, 30);
		dialog.add(problems);
		JButton selectProblem = new JButton(getScaledIcon("data/images/apply.png", 30));
		selectProblem.setBounds(220, 10, 30, 30);
		dialog.add(selectProblem);
		JButton addProblem = new JButton(getScaledIcon("data/images/add.png", 30));
		addProblem.setBounds(250, 10, 30, 30);
		dialog.add(addProblem);
		JButton close = new JButton(getScaledIcon("data/images/undo.png", 30));
		close.setBounds(280, 10, 30, 30);
		dialog.add(close);
		JButton delete = new JButton(getScaledIcon("data/images/delete.png", 30));
		delete.setBounds(310, 10, 30, 30);
		dialog.add(delete);

		addProblem.setToolTipText("Create (+)");
		selectProblem.setToolTipText("Sel (ENTER)");
		delete.setToolTipText("Delete (DEL)");
		close.setToolTipText("Cancel (ESC)");
		
		dialog.setResizable(false);
		dialog.setUndecorated(true);
		dialog.setSize(350, 80);
		dialog.setVisible(true);
		
		dialog.addKeyListener(new KeyListener(){
			@Override
			public void keyPressed(KeyEvent arg0){
			}
			@Override
			public void keyTyped(KeyEvent arg0){
			}
			@Override
			public void keyReleased(KeyEvent arg0){
				if(arg0.getKeyChar()=='+'){
					String str = JOptionPane.showInputDialog("Problem name:", "");
					if(str!=null){
						Problem p = new Problem(str);
						knowledge.add(p);
						problems.addItem(p);
						problems.setSelectedItem(p);
					}
				}
				else if(arg0.getKeyChar()==KeyEvent.VK_ESCAPE){
					if(tabbedPane.getSelectedComponent()!=null)
						((ProblemPanel)tabbedPane.getSelectedComponent()).update();
					dialog.setVisible(false);
					if(tabbedPane.getSelectedIndex()==tabbedPane.getTabCount()-1)
						tabbedPane.setSelectedIndex(-1);
				}
				else if(arg0.getKeyChar()==KeyEvent.VK_DELETE){
					if(problems.getSelectedItem()!=null){
						Problem p = (Problem)problems.getSelectedItem();
						int reply = JOptionPane.showConfirmDialog(dialog, "Really delete problem "+p.getName()+"? (This will completely remove the problem from the knowledge pool.)", "Confirm", JOptionPane.YES_NO_OPTION);
				        if(reply==JOptionPane.YES_OPTION) {
				        	knowledge.remove(p);
				        	problems.removeItem(p);
				        	removeProblem(p);
				        }
					}
				}
				else if(arg0.getKeyChar()==KeyEvent.VK_ENTER){
					if(problems.getSelectedItem()!=null){
						addProblem((Problem)problems.getSelectedItem());
						((ProblemPanel)tabbedPane.getSelectedComponent()).update();
						dialog.setVisible(false);
					}
				}
			}
		});
		
		final JCheckBox includeLibraryProblems = new JCheckBox("Show problems from libraries");
		includeLibraryProblems.setBounds(10,40,getWidth()-20,30);
		dialog.add(includeLibraryProblems);
		includeLibraryProblems.addActionListener(new ActionListener(){
			@Override
			public void actionPerformed(ActionEvent arg0) {	
				boolean allow = includeLibraryProblems.isSelected();
				problems.removeAllItems();
				for(Problem p : knowledge.getProblems())
					if(p!=Knowledge.emptyProblem && (p.getLibrary().isEmpty() || allow) && !knowledge.isImplementation(p))
						problems.addItem(p);
			}
		});
		
		KeyEventDispatcher myKeyEventDispatcher = new DefaultFocusManager();
		KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(myKeyEventDispatcher);
		problems.addKeyListener(dialog.getKeyListeners()[0]);
		
		selectProblem.addActionListener(new ActionListener(){
			@Override
			public void actionPerformed(ActionEvent arg0) {
				if(problems.getSelectedItem()!=null){
					tabbedPane.setSelectedIndex(-1);
					addProblem((Problem)problems.getSelectedItem());
					((ProblemPanel)tabbedPane.getSelectedComponent()).update();
					dialog.setVisible(false);
				}
			}
		});
		
		addProblem.addActionListener(new ActionListener(){
			@Override
			public void actionPerformed(ActionEvent arg0) {
				String str = JOptionPane.showInputDialog("Problem name:", "");
				if(str!=null){
					Problem p = new Problem(str);
					knowledge.add(p);
					problems.addItem(p);
					problems.setSelectedItem(p);
				}
			}
		});
		close.addActionListener(new ActionListener(){
			@Override
			public void actionPerformed(ActionEvent arg0) {
				if(tabbedPane.getSelectedComponent()!=null)
					((ProblemPanel)tabbedPane.getSelectedComponent()).update();
				if(tabbedPane.getSelectedIndex()==tabbedPane.getTabCount()-1)
					tabbedPane.setSelectedIndex(-1);
				dialog.setVisible(false);
			}
		});
		delete.addActionListener(new ActionListener(){
			@Override
			public void actionPerformed(ActionEvent arg0) {
				if(problems.getSelectedItem()!=null){
					Problem p = (Problem)problems.getSelectedItem();
					int reply = JOptionPane.showConfirmDialog(dialog, "Really delete problem "+p.getName()+"? (This will completely remove the problem from the knowledge pool.)", "Confirm", JOptionPane.YES_NO_OPTION);
			        if(reply==JOptionPane.YES_OPTION) {
			        	knowledge.remove(p);
			        	problems.removeItem(p);
			        	removeProblem(p);
			        }
				}
			}
			
		});

		dialog.setLocation(getWidth()/2-dialog.getWidth()/2, getHeight()/2-dialog.getHeight()/2);
	    dialog.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
	}
	
	/**
	 * <h1>createProblem</h1>
	 * Creates and opens a problem. This function is used to quickly create problems
	 * and immediately open them, without having to perform the more tiresome process
	 * of using the dialog created with <code>selectProblem</code>.
	 */
	public void createProblem(){
		String str = JOptionPane.showInputDialog("Problem name:", "");
		if(str!=null){
			Problem p = new Problem(str);
			knowledge.add(p);
			tabbedPane.setSelectedIndex(-1);
			addProblem((Problem)p);
			((ProblemPanel)tabbedPane.getSelectedComponent()).update();
		}
	}
	
	//a synchronizer so as to perform only the first call on a possible event stack
	private boolean changing = false;

	/**
	 * <h1>addProblem</h1>
	 * Creates a ProblemPanel in the tabbed pane with the given problem. If such a panel
	 * already exists, it is brought into focus instead.
	 * @param p : the given problem
	 */
	public void addProblem(Problem p){
		if(changing)
			return;
		changing = true;
		for(int i=0;i<tabbedPane.getTabCount()-1;i++)
			if(((ProblemPanel)tabbedPane.getComponent(i)).getProblem()==p){
				((ProblemPanel)tabbedPane.getComponent(i)).update();
				tabbedPane.setSelectedIndex(i);
				changing = false;
				return;
			}
		if(tabbedPane.getTabCount()>0){
			tabbedPane.remove(tabbedPane.getTabCount()-1);
			JPanel panel = new ProblemPanel(p, knowledge);
			tabbedPane.add(p.getName(), panel);
			tabbedPane.add("...", null);
			tabbedPane.setSelectedIndex(tabbedPane.getTabCount()-2);
			((ProblemPanel)panel).update();
		}
		changing = false;
	}
	
	/**
	 * <h1>removeProblem</h1>
	 * Closes any open ProblemPanel in the tabbed pane that matches the given problem.
	 * @param p : the given problem
	 */
	public void removeProblem(Problem p){
		if(changing)
			return;
		changing = true;
		for(int i=0;i<tabbedPane.getTabCount()-1;i++)
			if(((ProblemPanel)tabbedPane.getComponent(i)).getProblem()==p){
				if(tabbedPane.getSelectedIndex()==i)
					tabbedPane.setSelectedIndex(tabbedPane.getTabCount()-1);
				tabbedPane.remove(i);
			}
		if(tabbedPane.getTabCount()<=1)
			tabbedPane.setSelectedIndex(-1);
		else if(tabbedPane.getSelectedIndex()>0)
			tabbedPane.setSelectedIndex(tabbedPane.getSelectedIndex()-1);
		changing = false;
	}
	
	public void showDocumentation(){
		(new DocumentationDialog(this)).setVisible(true);
	}
	
	public void importString(String str, String defaultLibrary){
		final ArrayList<Problem> ret = CodeParser.parseImport(str);
		
		if(ret.isEmpty()){
			JOptionPane.showMessageDialog(tabbedPane, "Nothing was imported.");
			return;
		}
		
		final JDialog dialog = new JDialog(this);
		final CheckBoxList list = new CheckBoxList();
		JScrollPane scroller = new JScrollPane(list);
		scroller.setBounds(0, 30, 200, 170);
		dialog.add(scroller);
		for(Problem pr : ret){
			boolean found = false;
			for(Problem p : knowledge.getProblems())
				if(p.getName().compareTo(pr.getName())==0){
					found = true;
					break;
				}
			if(pr.getLibrary().isEmpty())
				pr.setLibrary(defaultLibrary);
			String text = pr.toString();
			list.addCheckbox(new JCheckBox(text, !found && pr.getResult()!=null));
		}
		dialog.setUndecorated(true);
		dialog.setLayout(null);
		dialog.getRootPane().setBorder( BorderFactory.createLineBorder(Color.BLACK) );
		dialog.setLocation(getWidth()/2, getHeight()/2);
		dialog.setSize(200, 200);
		dialog.add(scroller);
		JButton run = new JButton("Import...");
		run.setBounds(0, 0, 200, 30);
		dialog.add(run);
		
		run.addActionListener(new ActionListener(){
			@Override
			public void actionPerformed(ActionEvent arg0) {
				for(int i=0;i<list.getModel().getSize();i++){
					if(((JCheckBox)list.getModel().getElementAt(i)).isSelected()){
						for(Problem p : knowledge.getProblems())
							if(p.getName().compareTo(ret.get(i).getName())==0){
								knowledge.remove(p);
								removeProblem(p);
								break;
							}
						knowledge.add(ret.get(i));
						if(ret.get(i).getResult()!=null){
							for(Problem p : knowledge.getProblems())
								if(p.getName().compareTo(ret.get(i).getResult().getName())==0){
									knowledge.remove(p);
									removeProblem(p);
									break;
								}
							knowledge.add(ret.get(i).getResult());
							addProblem(ret.get(i));
							addProblem(ret.get(i).getResult());
						}
						else
							addProblem(ret.get(i));
					}
				}
				dialog.setVisible(false);
			}
		});
		dialog.setVisible(true);
	}
	
	public void editEndings(){
		String end = "";
		for(String str : Statement.endings)
			end += str+"\n";
		JTextArea endingText = new JTextArea(end, 20, 20);
		endingText.setFont(new Font("Verdana", Font.PLAIN, 12));
		int option = JOptionPane.showConfirmDialog(this,
				new JScrollPane(endingText),
                "Endings",
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE,
                null);
		if(option == JOptionPane.OK_OPTION) {
			String text = endingText.getText();
			text = text.replace(",", "\n");
			ArrayList<String> lines = new ArrayList<String>(Arrays.asList(text.split("\n")));
			for(int i=lines.size()-1;i>=0;i--)
				if(lines.get(i).trim().isEmpty())
					lines.remove(i);
			Statement.endings = new String[lines.size()];
			for(int i=0;i<lines.size();i++)
				Statement.endings[i] = lines.get(i).trim();
			if(tabbedPane.getSelectedComponent()!=null)
				((ProblemPanel)tabbedPane.getSelectedComponent()).update();
		}
	}
	
	public void editComments(){
		String end = "";
		for(String str : Statement.customComments)
			end += str+"\n";
		JTextArea endingText = new JTextArea(end, 20, 20);
		endingText.setFont(new Font("Verdana", Font.PLAIN, 12));
		int option = JOptionPane.showConfirmDialog(this,
				new JScrollPane(endingText),
                "Custom comment indicators",
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE,
                null);
		if(option == JOptionPane.OK_OPTION) {
			String text = endingText.getText();
			text = text.replace(",", "\n");
			ArrayList<String> lines = new ArrayList<String>(Arrays.asList(text.split("\n")));
			for(int i=lines.size()-1;i>=0;i--)
				if(lines.get(i).trim().isEmpty())
					lines.remove(i);
			Statement.customComments = new String[lines.size()];
			for(int i=0;i<lines.size();i++)
				Statement.customComments[i] = lines.get(i).trim();
			//REFRESH KNOWLEDGE
			knowledge.refresh();
			if(tabbedPane.getSelectedComponent()!=null)
				((ProblemPanel)tabbedPane.getSelectedComponent()).update();
		}
	}
	
	public void editAnds(){
		String end = "";
		for(String str : Statement.languageAnd)
			end += str+"\n";
		JTextArea endingText = new JTextArea(end, 20, 20);
		endingText.setFont(new Font("Verdana", Font.PLAIN, 12));
		int option = JOptionPane.showConfirmDialog(this,
				new JScrollPane(endingText),
                "Predicates meaning 'and'",
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE,
                null);
		if(option == JOptionPane.OK_OPTION) {
			String text = endingText.getText();
			text = text.replace(",", "\n");
			ArrayList<String> lines = new ArrayList<String>(Arrays.asList(text.split("\n")));
			for(int i=lines.size()-1;i>=0;i--)
				if(lines.get(i).trim().isEmpty())
					lines.remove(i);
			Statement.languageAnd = new String[lines.size()];
			for(int i=0;i<lines.size();i++)
				Statement.languageAnd[i] = lines.get(i).trim();
			//REFRESH KNOWLEDGE
			knowledge.refresh();
			if(tabbedPane.getSelectedComponent()!=null)
				((ProblemPanel)tabbedPane.getSelectedComponent()).update();
		}
	}
	
	public void editIgnored(){
		String end = "";
		for(String str : Statement.ignored)
			end += str+"\n";
		JTextArea endingText = new JTextArea(end, 20, 20);
		endingText.setFont(new Font("Verdana", Font.PLAIN, 12));
		int option = JOptionPane.showConfirmDialog(this,
				new JScrollPane(endingText),
                "Ignored Predicates",
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE,
                null);
		if(option == JOptionPane.OK_OPTION) {
			String text = endingText.getText();
			text = text.replace(",", "\n");
			ArrayList<String> lines = new ArrayList<String>(Arrays.asList(text.split("\n")));
			for(int i=lines.size()-1;i>=0;i--)
				if(lines.get(i).trim().isEmpty())
					lines.remove(i);
			Statement.ignored = new String[lines.size()];
			for(int i=0;i<lines.size();i++)
				Statement.ignored[i] = lines.get(i).trim();
			if(tabbedPane.getSelectedComponent()!=null)
				((ProblemPanel)tabbedPane.getSelectedComponent()).update();
		}
	}
	
	public void editSeparators(){
		String str = "";
		for(int i=0;i<Statement.separators.length;i++){
			str += Statement.separators[i];
			if(Statement.separators[i].compareTo(Statement.inverses[i])!=0)
				str += " @ "+Statement.inverses[i];
			str += "\n";
		}
			
		JTextArea endingText = new JTextArea(str, 20, 20);
		endingText.setFont(new Font("Verdana", Font.PLAIN, 12));
		int option = JOptionPane.showConfirmDialog(this,
				new JScrollPane(endingText),
                "Separators",
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE,
                null);
		if(option == JOptionPane.OK_OPTION) {
			String text = endingText.getText();
			text = text.replace(",", "\n");
			ArrayList<String> lines = new ArrayList<String>(Arrays.asList(text.split("\n")));
			for(int i=lines.size()-1;i>=0;i--)
				if(lines.get(i).trim().isEmpty())
					lines.remove(i);
				else
					lines.set(i, lines.get(i).trim());
			Statement.separators = new String[lines.size()];
			Statement.inverses = new String[lines.size()];
			for(int i=0;i<lines.size();i++){
				if(lines.get(i).indexOf("@")==-1){
					Statement.separators[i] = lines.get(i);
					Statement.inverses[i] = lines.get(i);
					continue;
				}
				Statement.separators[i] = lines.get(i).substring(0, lines.get(i).indexOf("@")).trim();
				Statement.inverses[i] = lines.get(i).substring(lines.get(i).indexOf("@")+1, lines.get(i).length()).trim();
				if(Statement.inverses[i].isEmpty())
					Statement.inverses[i] = Statement.separators[i];
			}
			///REFRESH KNOWLEDGE
			knowledge.refresh();
			if(tabbedPane.getSelectedComponent()!=null)
				((ProblemPanel)tabbedPane.getSelectedComponent()).update();
		}
	}
	
	public static void main(String[] args) {
		(new Main()).setVisible(true);
	}
	
	private static Problem res = null;
	/**
	 * <h1>chooseBetweenProblems</h1>
	 * This function creates a dialog that helps you choose between any number of problems.
	 * For those problems, their implementation's statements are displayed to help the user
	 * in their choice.
	 * @param pr : an ArrayList containing all the problems
	 * @return the problem selected by the user
	 */
	synchronized public static Problem chooseBetweenProblems(final ArrayList<Problem> pr){
		if(pr.size()==1)
			return pr.get(0);
		if(pr.size()==0)
			return null;
		final JDialog dialog = new JDialog();
		dialog.setModal(true);
		dialog.setSize(600, 400);
		JPanel panel = new JPanel();
		panel.setLayout(null);
		panel.setSize(pr.size()*300,400);
		JScrollPane panelScroll = new JScrollPane(panel);
		dialog.add(panelScroll);
		for(int i=0;i<pr.size();i++){
			final Problem boundProblem = pr.get(i);
			JTextPane text = new JTextPane();
			text.setContentType("text/html");
			String str = "";
			for(Statement st : boundProblem.getResult().statements){
				if(st.isInput() || st.isOutput() || st.isIterator())
					continue;
				Statement s = new Statement(st.getVariables(), st.getExpression());
				s.removeVariablePrefix("_"+boundProblem.getName()+"_");
				str += s.toHTML()+"<br/>";
			}
			text.setText(str);
			text.setEditable(false);
			JScrollPane scroller = new JScrollPane(text);
			scroller.setBounds(i*300, 30, 300, 370);
			panel.add(scroller);
			JButton button = new JButton("Select "+boundProblem.toString());
			button.setBounds(i*300, 0, 300, 30);
			panel.add(button);
			button.addActionListener(new ActionListener(){
				@Override
				public void actionPerformed(ActionEvent arg0) {
					res = boundProblem;
					dialog.setVisible(false);
				}
			});
		}
		dialog.setUndecorated(true);
		dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
		dialog.setVisible(true);
		return res;
	}

}
