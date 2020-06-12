/* -*- mode: java; c-basic-offset: 2; indent-tabs-mode: nil -*- */ /*
 Part of the Processing project - http://processing.org

 Copyright (c) 2014-17 The Processing Foundation

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
package processing.mode.android

import processing.app.Language
import processing.app.Messages
import processing.app.Platform
import processing.app.ui.Toolkit
import java.awt.Color
import java.awt.Component
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.event.ActionListener
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.io.File
import java.util.*
import javax.swing.*
import javax.swing.border.EmptyBorder
import javax.swing.border.MatteBorder
import javax.swing.border.TitledBorder

class KeyStoreManager(var editor: AndroidEditor) : JFrame("Android keystore manager") {
    var keyStore: File? = null
    var passwordField: JPasswordField? = null
    var repeatPasswordField: JPasswordField? = null
    var commonName: JTextField? = null
    var organizationalUnit: JTextField? = null
    var organizationName: JTextField? = null
    var localityName: JTextField? = null
    var country: JTextField? = null
    var stateName: JTextField? = null
    private fun createLayout() {
        val outer = contentPane
        outer.removeAll()
        val vbox = Box.createVerticalBox()
        vbox.border = EmptyBorder(BOX_BORDER, BOX_BORDER, BOX_BORDER, BOX_BORDER)
        outer.add(vbox)
        keyStore = AndroidKeyStore.getKeyStore()
        if (keyStore != null) {
            showKeystorePasswordLayout(vbox)
        } else {
            showKeystoreCredentialsLayout(vbox)
        }
        vbox.add(Box.createVerticalStrut(GAP))

        // buttons
        val buttons = JPanel()
        buttons.alignmentX = Component.LEFT_ALIGNMENT
        val okButton = JButton(Language.text("prompt.ok"))
        var dim = Dimension(Toolkit.getButtonWidth(),
                okButton.preferredSize.height)
        okButton.preferredSize = dim
        okButton.addActionListener {
            if (checkRequiredFields()) {
                if (keyStore == null) {
                    try {
                        AndroidKeyStore.generateKeyStore(String(passwordField!!.password),
                                commonName!!.text, organizationalUnit!!.text, organizationName!!.text,
                                localityName!!.text, stateName!!.text, country!!.text)
                        isVisible = false
                        editor.startExportPackage(String(passwordField!!.password))
                    } catch (e1: Exception) {
                        e1.printStackTrace()
                    }
                } else {
                    isVisible = false
                    editor.startExportPackage(String(passwordField!!.password))
                }
            }
        }
        okButton.isEnabled = true
        val cancelButton = JButton(Language.text("prompt.cancel"))
        cancelButton.preferredSize = dim
        cancelButton.addActionListener { isVisible = false }
        cancelButton.isEnabled = true
        val resetKeystoreButton = JButton(AndroidMode.getTextString("keystore_manager.reset_password"))
        dim = Dimension(Toolkit.getButtonWidth() * 2,
                resetKeystoreButton.preferredSize.height)
        resetKeystoreButton.preferredSize = dim
        resetKeystoreButton.addActionListener {
            isVisible = false
            val result = Messages.showYesNoQuestion(editor, AndroidMode.getTextString("keystore_manager.dialog.reset_keyboard_title"),
                    AndroidMode.getTextString("keystore_manager.dialog.reset_keyboard_body_part1"),
                    AndroidMode.getTextString("keystore_manager.dialog.reset_keyboard_body_part2"))
            if (result == JOptionPane.NO_OPTION) {
                isVisible = true
            } else {
                if (!AndroidKeyStore.resetKeyStore()) {
                    Messages.showWarning(AndroidMode.getTextString("keystore_manager.warn.cannot_remove_keystore_title"),
                            AndroidMode.getTextString("keystore_manager.warn.cannot_remove_keystore_body"))
                    isVisible = true
                } else {
                    keyStore = null
                    createLayout()
                }
            }
        }
        resetKeystoreButton.isEnabled = true

        // think different, biznatchios!
        if (Platform.isMacOS()) {
            buttons.add(cancelButton)
            if (keyStore != null) buttons.add(resetKeystoreButton)
            //      buttons.add(Box.createHorizontalStrut(8));
            buttons.add(okButton)
        } else {
            buttons.add(okButton)
            if (keyStore != null) buttons.add(resetKeystoreButton)
            //      buttons.add(Box.createHorizontalStrut(8));
            buttons.add(cancelButton)
        }
        //    buttons.setMaximumSize(new Dimension(300, buttons.getPreferredSize().height));
        vbox.add(buttons)
        val root = getRootPane()
        root.defaultButton = okButton
        val disposer = ActionListener { isVisible = false }
        Toolkit.registerWindowCloseKeys(root, disposer)
        Toolkit.setIcon(this)
        pack()
        /*
    Dimension screen = Toolkit.getScreenSize();
    Dimension windowSize = getSize();
    setLocation((screen.width - windowSize.width) / 2,
        (screen.height - windowSize.height) / 2);
     */setLocationRelativeTo(null)
        isVisible = true
    }

    private fun showKeystorePasswordLayout(pain: Box) {
        passwordField = JPasswordField(15)
        val passwordLabel = JLabel("<html><body><b>" + AndroidMode.getTextString("keystore_manager.password_label") + " </b></body></html>")
        passwordLabel.labelFor = passwordField
        val textPane = JPanel(FlowLayout(FlowLayout.TRAILING))
        textPane.add(passwordLabel)
        textPane.add(passwordField)
        textPane.alignmentX = Component.LEFT_ALIGNMENT
        pain.add(textPane)
    }

    private fun checkRequiredFields(): Boolean {
        return if (passwordField!!.password.size > 5) {
            if (keyStore != null) return true
            if (Arrays.equals(passwordField!!.password, repeatPasswordField!!.password)) {
                true
            } else {
                Messages.showWarning(AndroidMode.getTextString("keystore_manager.warn.password_missmatch_title"),
                        AndroidMode.getTextString("keystore_manager.warn.password_missmatch_body"))
                false
            }
        } else {
            Messages.showWarning(AndroidMode.getTextString("keystore_manager.warn.short_password_title"),
                    AndroidMode.getTextString("keystore_manager.warn.short_password_body"))
            false
        }
    }

    private fun showKeystoreCredentialsLayout(box: Box) {
        val labelText = AndroidMode.getTextString("keystore_manager.top_label")
        val textarea = JLabel(labelText)
        textarea.preferredSize = Dimension(LABEL_WIDTH, LABEL_HEIGHT)
        textarea.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                Platform.openURL(GUIDE_URL)
            }
        })
        textarea.alignmentX = Component.LEFT_ALIGNMENT
        box.add(textarea)

        // password field
        passwordField = JPasswordField(15)
        val passwordLabel = JLabel("<html><body><b>" + AndroidMode.getTextString("keystore_manager.password_label") + " </b></body></html>")
        passwordLabel.labelFor = passwordField
        var textPane = JPanel(FlowLayout(FlowLayout.TRAILING))
        textPane.add(passwordLabel)
        textPane.add(passwordField)
        textPane.alignmentX = Component.LEFT_ALIGNMENT
        box.add(textPane)

        // repeat password field
        repeatPasswordField = JPasswordField(15)
        val repeatPasswordLabel = JLabel("<html><body><b>" + AndroidMode.getTextString("keystore_manager.repeat_password_label") + " </b></body></html>")
        repeatPasswordLabel.labelFor = passwordField
        textPane = JPanel(FlowLayout(FlowLayout.TRAILING))
        textPane.add(repeatPasswordLabel)
        textPane.add(repeatPasswordField)
        textPane.alignmentX = Component.LEFT_ALIGNMENT
        textPane.border = EmptyBorder(0, 0, PASS_BORDER, 0)
        box.add(textPane)
        val mb = MatteBorder(1, 0, 0, 0, Color.LIGHT_GRAY)
        val tb = TitledBorder(mb, AndroidMode.getTextString("keystore_manager.issuer_credentials_header"), TitledBorder.LEFT, TitledBorder.DEFAULT_POSITION)
        val separatorPanel = JPanel()
        separatorPanel.border = tb
        box.add(separatorPanel)

        // common name (CN)
        commonName = JTextField(15)
        val commonNameLabel = JLabel(AndroidMode.getTextString("keystore_manager.common_name_label"))
        commonNameLabel.labelFor = commonName
        textPane = JPanel(FlowLayout(FlowLayout.TRAILING))
        textPane.add(commonNameLabel)
        textPane.add(commonName)
        textPane.alignmentX = Component.LEFT_ALIGNMENT
        box.add(textPane)

        // organizational unit (OU)
        organizationalUnit = JTextField(15)
        val organizationalUnitLabel = JLabel(AndroidMode.getTextString("keystore_manager.organizational_unitl_label"))
        organizationalUnitLabel.labelFor = organizationalUnit
        textPane = JPanel(FlowLayout(FlowLayout.TRAILING))
        textPane.add(organizationalUnitLabel)
        textPane.add(organizationalUnit)
        textPane.alignmentX = Component.LEFT_ALIGNMENT
        box.add(textPane)

        // organization name (O)
        organizationName = JTextField(15)
        val organizationNameLabel = JLabel(AndroidMode.getTextString("keystore_manager.organization_name_label"))
        organizationNameLabel.labelFor = organizationName
        textPane = JPanel(FlowLayout(FlowLayout.TRAILING))
        textPane.add(organizationNameLabel)
        textPane.add(organizationName)
        textPane.alignmentX = Component.LEFT_ALIGNMENT
        box.add(textPane)

        // locality name (L)
        localityName = JTextField(15)
        val localityNameLabel = JLabel(AndroidMode.getTextString("keystore_manager.city_name_label"))
        localityNameLabel.labelFor = localityName
        textPane = JPanel(FlowLayout(FlowLayout.TRAILING))
        textPane.add(localityNameLabel)
        textPane.add(localityName)
        textPane.alignmentX = Component.LEFT_ALIGNMENT
        box.add(textPane)

        // state name (S)
        stateName = JTextField(15)
        val stateNameLabel = JLabel(AndroidMode.getTextString("keystore_manager.state_name_label"))
        stateNameLabel.labelFor = stateName
        textPane = JPanel(FlowLayout(FlowLayout.TRAILING))
        textPane.add(stateNameLabel)
        textPane.add(stateName)
        textPane.alignmentX = Component.LEFT_ALIGNMENT
        box.add(textPane)

        // country (C)
        country = JTextField(15)
        val countryLabel = JLabel(AndroidMode.getTextString("keystore_manager.country_code_label"))
        countryLabel.labelFor = country
        textPane = JPanel(FlowLayout(FlowLayout.TRAILING))
        textPane.add(countryLabel)
        textPane.add(country)
        textPane.alignmentX = Component.LEFT_ALIGNMENT
        box.add(textPane)
    }

    companion object {
        private val BOX_BORDER = Toolkit.zoom(13)
        private val PASS_BORDER = Toolkit.zoom(15)
        private val LABEL_WIDTH = Toolkit.zoom(400)
        private val LABEL_HEIGHT = Toolkit.zoom(100)
        private val GAP = Toolkit.zoom(13)
        const val GUIDE_URL = "https://developer.android.com/studio/publish/app-signing.html"
    }

    init {
        createLayout()
    }
}