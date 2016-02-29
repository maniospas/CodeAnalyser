package display;

import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.awt.event.*;

/**
 * <h1>CheckBoxList</h1>
 * A list that can have check boxes as its components.
 * @author Trevor Harmon (http://www.devx.com/tips/Tip/5342)
 */
public class CheckBoxList extends JList<Object>
{
	private static final long serialVersionUID = 1L;
	protected static Border noFocusBorder = new EmptyBorder(1, 1, 1, 1);

   public CheckBoxList()
   {
      setCellRenderer(new CellRenderer());

      addMouseListener(new MouseAdapter()
         {
            public void mousePressed(MouseEvent e)
            {
               int index = locationToIndex(e.getPoint());

               if (index != -1) {
                  JCheckBox checkbox = (JCheckBox)
                              getModel().getElementAt(index);
                  checkbox.setSelected(
                                     !checkbox.isSelected());
                  repaint();
               }
            }
         }
      );

      setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
   }

   protected class CellRenderer implements ListCellRenderer<Object>
   {
      public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                    boolean isSelected, boolean cellHasFocus)
      {
         JCheckBox checkbox = (JCheckBox) value;
         checkbox.setBackground(isSelected ?
                 getSelectionBackground() : getBackground());
         checkbox.setForeground(isSelected ?
                 getSelectionForeground() : getForeground());
         checkbox.setEnabled(isEnabled());
         checkbox.setFont(getFont());
         checkbox.setFocusPainted(false);
         checkbox.setBorderPainted(true);
         checkbox.setBorder(isSelected ?
          UIManager.getBorder(
           "List.focusCellHighlightBorder") : noFocusBorder);
         return checkbox;
      }
   }
   
   public void addCheckbox(JCheckBox checkBox) {
	    ListModel<Object> currentList = this.getModel();
	    JCheckBox[] newList = new JCheckBox[currentList.getSize() + 1];
	    for (int i = 0; i < currentList.getSize(); i++) {
	        newList[i] = (JCheckBox) currentList.getElementAt(i);
	    }
	    newList[newList.length - 1] = checkBox;
	    setListData(newList);
	}
}
