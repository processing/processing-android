/* -*- mode: java; c-basic-offset: 2; indent-tabs-mode: nil -*- */

/*
 Part of the Processing project - http://processing.org

 Copyright (c) 2014-21 The Processing Foundation
 
 This program is free software; you can redistribute it and/or modify
 it under the terms of the GNU General Public License version 2
 as published by the Free Software Foundation.

 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with this program; if not, write to the Free Software Foundation,
 Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
*/

package processing.mode.android;

import processing.app.Language;
import processing.app.Messages;
import processing.app.Platform;
import processing.app.ui.Toolkit;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;
import javax.swing.border.TitledBorder;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.util.Arrays;


@SuppressWarnings("serial")
public class KeyStoreManager extends JFrame {
  final static protected int PACKAGE = 0;
  final static protected int BUNDLE  = 1;

  final static private int BOX_BORDER = Toolkit.zoom(13);
  final static private int PASS_BORDER = Toolkit.zoom(15);
  final static private int LABEL_WIDTH = Toolkit.zoom(400);
  final static private int LABEL_HEIGHT = Toolkit.zoom(100);
  final static private int GAP = Toolkit.zoom(13);
  
  static final String GUIDE_URL =
      "https://developer.android.com/studio/publish/app-signing.html";
  
  File keyStore;
  AndroidEditor editor;

  JPasswordField passwordField;
  JPasswordField repeatPasswordField;

  JTextField commonName;
  JTextField organizationalUnit;
  JTextField organizationName;
  JTextField localityName;
  JTextField country;
  JTextField stateName;

  public KeyStoreManager(final AndroidEditor editor, final int kind) {
    super("Android keystore manager");
    this.editor = editor;

    createLayout(kind);
  }

  private void createLayout(int kind) {
    Container outer = getContentPane();
    outer.removeAll();

    Box vbox = Box.createVerticalBox();
    vbox.setBorder(new EmptyBorder(BOX_BORDER, BOX_BORDER, BOX_BORDER, BOX_BORDER));
    outer.add(vbox);

    keyStore = AndroidKeyStore.getKeyStore();
    if (keyStore != null) {
      showKeystorePasswordLayout(vbox);
    } else {
      showKeystoreCredentialsLayout(vbox);
    }

    vbox.add(Box.createVerticalStrut(GAP));
    
    // buttons
    JPanel buttons = new JPanel();
    buttons.setAlignmentX(LEFT_ALIGNMENT);
    JButton okButton = new JButton(Language.text("prompt.ok"));
    Dimension dim = new Dimension(Toolkit.getButtonWidth(),
                                  okButton.getPreferredSize().height);
    okButton.setPreferredSize(dim);
    okButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        if (checkRequiredFields()) {
          if (keyStore == null) {
            try {
              AndroidKeyStore.generateKeyStore(new String(passwordField.getPassword()),
                  commonName.getText(), organizationalUnit.getText(), organizationName.getText(),
                  localityName.getText(), stateName.getText(), country.getText());

              setVisible(false);
              if (kind == KeyStoreManager.BUNDLE) {
                editor.startExportBundle(new String(passwordField.getPassword()));
              } else {
                editor.startExportPackage(new String(passwordField.getPassword()));
              }              
            } catch (Exception e1) {
              e1.printStackTrace();
            }
          } else {
            setVisible(false);
            if (kind == KeyStoreManager.BUNDLE) {
              editor.startExportBundle(new String(passwordField.getPassword()));
            } else {
              editor.startExportPackage(new String(passwordField.getPassword()));
            }
          }
        }
      }
    });
    okButton.setEnabled(true);

    JButton cancelButton = new JButton(Language.text("prompt.cancel"));
    cancelButton.setPreferredSize(dim);
    cancelButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        setVisible(false);
      }
    });
    cancelButton.setEnabled(true);

    JButton resetKeystoreButton = new JButton(AndroidMode.getTextString("keystore_manager.reset_password"));
    dim = new Dimension(Toolkit.getButtonWidth()*2,
                        resetKeystoreButton.getPreferredSize().height);
    resetKeystoreButton.setPreferredSize(dim);
    resetKeystoreButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        setVisible(false);
        int result = Messages.showYesNoQuestion(editor, AndroidMode.getTextString("keystore_manager.dialog.reset_keyboard_title"),
                                                        AndroidMode.getTextString("keystore_manager.dialog.reset_keyboard_body_part1"), 
                                                        AndroidMode.getTextString("keystore_manager.dialog.reset_keyboard_body_part2"));

        if (result == JOptionPane.NO_OPTION) {
          setVisible(true);
        } else {
          if (!AndroidKeyStore.resetKeyStore()) {
            Messages.showWarning(AndroidMode.getTextString("keystore_manager.warn.cannot_remove_keystore_title"), 
                                 AndroidMode.getTextString("keystore_manager.warn.cannot_remove_keystore_body"));
            setVisible(true);
          } else {
            keyStore = null;
            createLayout(kind);
          }
        }
      }
    });
    resetKeystoreButton.setEnabled(true);

    // think different, biznatchios!
    if (Platform.isMacOS()) {
      buttons.add(cancelButton);

      if (keyStore != null) buttons.add(resetKeystoreButton);
//      buttons.add(Box.createHorizontalStrut(8));
      buttons.add(okButton);
    } else {
      buttons.add(okButton);

      if (keyStore != null) buttons.add(resetKeystoreButton);
//      buttons.add(Box.createHorizontalStrut(8));
      buttons.add(cancelButton);
    }
//    buttons.setMaximumSize(new Dimension(300, buttons.getPreferredSize().height));
    vbox.add(buttons);

    JRootPane root = getRootPane();
    root.setDefaultButton(okButton);
    ActionListener disposer = new ActionListener() {
      public void actionPerformed(ActionEvent actionEvent) {
        setVisible(false);
      }
    };
    Toolkit.registerWindowCloseKeys(root, disposer);
    Toolkit.setIcon(this);

    pack();
    /*
    Dimension screen = Toolkit.getScreenSize();
    Dimension windowSize = getSize();
    setLocation((screen.width - windowSize.width) / 2,
        (screen.height - windowSize.height) / 2);
     */
    setLocationRelativeTo(null);
    setVisible(true);
  }

  private void showKeystorePasswordLayout(Box pain) {
    passwordField = new JPasswordField(15);
    JLabel passwordLabel = new JLabel("<html><body><b>" + AndroidMode.getTextString("keystore_manager.password_label") + " </b></body></html>");
    passwordLabel.setLabelFor(passwordField);

    JPanel textPane = new JPanel(new FlowLayout(FlowLayout.TRAILING));
    textPane.add(passwordLabel);
    textPane.add(passwordField);
    textPane.setAlignmentX(LEFT_ALIGNMENT);
    pain.add(textPane);
  }

  private boolean checkRequiredFields() {
    if (passwordField.getPassword().length > 5) {
      if (keyStore != null) return true;

      if (Arrays.equals(passwordField.getPassword(), repeatPasswordField.getPassword())) {
        return true;
      } else {
        Messages.showWarning(AndroidMode.getTextString("keystore_manager.warn.password_missmatch_title"), 
                             AndroidMode.getTextString("keystore_manager.warn.password_missmatch_body"));
        return false;
      }
    } else {
      Messages.showWarning(AndroidMode.getTextString("keystore_manager.warn.short_password_title"), 
                           AndroidMode.getTextString("keystore_manager.warn.short_password_body"));
      return false;
    }
  }

  private void showKeystoreCredentialsLayout(Box box) {
    String labelText = AndroidMode.getTextString("keystore_manager.top_label");
    JLabel textarea = new JLabel(labelText);
    textarea.setPreferredSize(new Dimension(LABEL_WIDTH, LABEL_HEIGHT));
    textarea.addMouseListener(new MouseAdapter() {
      public void mouseClicked(MouseEvent e) {
        Platform.openURL(GUIDE_URL);
      }
    });
    textarea.setAlignmentX(LEFT_ALIGNMENT);
    box.add(textarea);

    // password field
    passwordField = new JPasswordField(15);
    JLabel passwordLabel = new JLabel("<html><body><b>" + AndroidMode.getTextString("keystore_manager.password_label") + " </b></body></html>");
    passwordLabel.setLabelFor(passwordField);

    JPanel textPane = new JPanel(new FlowLayout(FlowLayout.TRAILING));
    textPane.add(passwordLabel);
    textPane.add(passwordField);
    textPane.setAlignmentX(LEFT_ALIGNMENT);
    box.add(textPane);

    // repeat password field
    repeatPasswordField = new JPasswordField(15);
    JLabel repeatPasswordLabel = new JLabel("<html><body><b>" + AndroidMode.getTextString("keystore_manager.repeat_password_label") + " </b></body></html>");
    repeatPasswordLabel.setLabelFor(passwordField);

    textPane = new JPanel(new FlowLayout(FlowLayout.TRAILING));
    textPane.add(repeatPasswordLabel);
    textPane.add(repeatPasswordField);
    textPane.setAlignmentX(LEFT_ALIGNMENT);
    textPane.setBorder(new EmptyBorder(0, 0, PASS_BORDER, 0));
    box.add(textPane);

    MatteBorder mb = new MatteBorder(1, 0, 0, 0, Color.LIGHT_GRAY);
    TitledBorder tb = new TitledBorder(mb, AndroidMode.getTextString("keystore_manager.issuer_credentials_header"), TitledBorder.LEFT, TitledBorder.DEFAULT_POSITION);
    JPanel separatorPanel = new JPanel();
    separatorPanel.setBorder(tb);
    box.add(separatorPanel);

    // common name (CN)
    commonName = new JTextField(15);
    JLabel commonNameLabel = new JLabel(AndroidMode.getTextString("keystore_manager.common_name_label"));
    commonNameLabel.setLabelFor(commonName);

    textPane = new JPanel(new FlowLayout(FlowLayout.TRAILING));
    textPane.add(commonNameLabel);
    textPane.add(commonName);
    textPane.setAlignmentX(LEFT_ALIGNMENT);
    box.add(textPane);

    // organizational unit (OU)
    organizationalUnit = new JTextField(15);
    JLabel organizationalUnitLabel = new JLabel(AndroidMode.getTextString("keystore_manager.organizational_unitl_label"));
    organizationalUnitLabel.setLabelFor(organizationalUnit);

    textPane = new JPanel(new FlowLayout(FlowLayout.TRAILING));
    textPane.add(organizationalUnitLabel);
    textPane.add(organizationalUnit);
    textPane.setAlignmentX(LEFT_ALIGNMENT);
    box.add(textPane);

    // organization name (O)
    organizationName = new JTextField(15);
    JLabel organizationNameLabel = new JLabel(AndroidMode.getTextString("keystore_manager.organization_name_label"));
    organizationNameLabel.setLabelFor(organizationName);

    textPane = new JPanel(new FlowLayout(FlowLayout.TRAILING));
    textPane.add(organizationNameLabel);
    textPane.add(organizationName);
    textPane.setAlignmentX(LEFT_ALIGNMENT);
    box.add(textPane);

    // locality name (L)
    localityName = new JTextField(15);
    JLabel localityNameLabel = new JLabel(AndroidMode.getTextString("keystore_manager.city_name_label"));
    localityNameLabel.setLabelFor(localityName);

    textPane = new JPanel(new FlowLayout(FlowLayout.TRAILING));
    textPane.add(localityNameLabel);
    textPane.add(localityName);
    textPane.setAlignmentX(LEFT_ALIGNMENT);
    box.add(textPane);

    // state name (S)
    stateName = new JTextField(15);
    JLabel stateNameLabel = new JLabel(AndroidMode.getTextString("keystore_manager.state_name_label"));
    stateNameLabel.setLabelFor(stateName);

    textPane = new JPanel(new FlowLayout(FlowLayout.TRAILING));
    textPane.add(stateNameLabel);
    textPane.add(stateName);
    textPane.setAlignmentX(LEFT_ALIGNMENT);
    box.add(textPane);

    // country (C)
    country = new JTextField(15);
    JLabel countryLabel = new JLabel(AndroidMode.getTextString("keystore_manager.country_code_label"));
    countryLabel.setLabelFor(country);

    textPane = new JPanel(new FlowLayout(FlowLayout.TRAILING));
    textPane.add(countryLabel);
    textPane.add(country);
    textPane.setAlignmentX(LEFT_ALIGNMENT);
    box.add(textPane);
  }
}
