package org.robotstation.components;

import javax.swing.*;

/**
 * Created by eli on 4/10/17.
 */
public class PersonPanel extends JPanel {
    public void findPerson (int x, int y, int size_x, int size_y) {
        this.setBounds(x,y,size_x,size_y);
    }
}
