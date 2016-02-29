package display;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;

import javax.swing.ImageIcon;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JScrollPane;
import javax.swing.JTextPane;

public class DocumentationDialog extends JDialog{
	private static final long serialVersionUID = 1L;
	JTextPane textField;

	public DocumentationDialog(JFrame parent){
		super(parent, "Documentation");
		ImageIcon icon = new ImageIcon("data/images/documentation.png");
	    setIconImage(icon.getImage());
		setSize(600,400);
		textField = new JTextPane();
		JScrollPane scroll = new JScrollPane(textField);
		add(scroll);
		textField.setContentType("text/html");
		textField.setEditable(false);
		
		open("data/help/documentation.html");
	}
	public void open(String path){
		if((new File(path)).exists()){
			BufferedReader reader;
			try{
				reader = new BufferedReader(new FileReader(path));
				String text = "";
				String line;
				while((line=reader.readLine())!=null)
					text += line;
				//text = text.replace("src=\"", "src=\""+(new File("")).getAbsolutePath()+"\\");
				//System.out.println(text);
				textField.setText(text);
			}
			catch(Exception e){
				e.printStackTrace();
			}
		}
	}
	
	
}
