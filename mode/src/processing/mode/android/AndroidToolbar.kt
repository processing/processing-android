/* -*- mode: java; c-basic-offset: 2; indent-tabs-mode: nil -*- */

/*
 Part of the Processing project - http://processing.org

 Copyright (c) 2012-16 The Processing Foundation
 Copyright (c) 2011-12 Ben Fry and Casey Reas

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

import processing.app.Base
import processing.app.Language
import processing.app.ui.Editor
import processing.app.ui.EditorButton
import processing.app.ui.EditorToolbar

import processing.mode.android.AndroidMode.Companion.getTextString

import java.awt.event.ActionEvent
import java.awt.event.InputEvent
import java.util.*
import javax.swing.Box
import javax.swing.JLabel

internal class AndroidToolbar(editor: Editor, base: Base?) : EditorToolbar(editor) {
    private val aEditor: AndroidEditor = editor as AndroidEditor
    var stepButton: EditorButton? = null
    var continueButton: EditorButton? = null

    /*
  public void handlePressed(MouseEvent e, int sel) {
    boolean shift = e.isShiftDown();
    AndroidEditor aeditor = (AndroidEditor) editor;

    switch (sel) {
    case RUN:
      if (!shift) {
        aeditor.handleRunDevice();
      } else {
        aeditor.handleRunEmulator();
      }
      break;

    case STOP:
      aeditor.handleStop();
      break;

    case OPEN:
      // TODO I think we need a longer chain of accessors here.
      JPopupMenu popup = editor.getMode().getToolbarMenu().getPopupMenu();
      popup.show(this, e.getX(), e.getY());
      break;

    case NEW:
//      if (shift) {
      base.handleNew();
//      } else {
//        base.handleNewReplace();
//      }
      break;

    case SAVE:
      aeditor.handleSave(false);
      break;

    case EXPORT:
      if (!shift) {
        aeditor.handleExportPackage();
      } else {
        aeditor.handleExportProject();
      }
      break;
    }
  }
*/
    override fun createButtons(): List<EditorButton> {
        // aEditor not ready yet because this is called by super()
        val debug = (editor as AndroidEditor).isDebuggerEnabled

        val toReturn = ArrayList<EditorButton>()

        val runText = if (debug) Language.text("toolbar.debug") else Language.text("Run on Device")

        runButton = object : EditorButton(this,
                "/lib/toolbar/run",
                runText,
                "Run on emulator") {
            override fun actionPerformed(e: ActionEvent) {
                handleRun(e.modifiers)
            }
        }

        toReturn.add(runButton)

        if (debug) {
            stepButton = object : EditorButton(this,
                    "/lib/toolbar/step",
                    Language.text("menu.debug.step"),
                    Language.text("menu.debug.step_into"),
                    Language.text("menu.debug.step_out")) {

                override fun actionPerformed(e: ActionEvent) {
                    val mask = ActionEvent.SHIFT_MASK or ActionEvent.ALT_MASK
                    aEditor.handleStep(e.modifiers and mask)
                }

            }

            toReturn.add(stepButton as EditorButton)

            continueButton = object : EditorButton(this,
                    "/lib/toolbar/continue",
                    Language.text("menu.debug.continue")) {
                override fun actionPerformed(e: ActionEvent) {
                    aEditor.handleContinue()
                }
            }

            toReturn.add(continueButton as EditorButton)

        }

        stopButton = object : EditorButton(this,
                "/lib/toolbar/stop",
                Language.text("toolbar.stop")) {
            override fun actionPerformed(e: ActionEvent) {
                handleStop()
            }
        }

        toReturn.add(stopButton)

        return toReturn
    }

    override fun addModeButtons(box: Box, label: JLabel) {
        val debugButton: EditorButton = object : EditorButton(this, "/lib/toolbar/debug",
                Language.text("toolbar.debug")) {

            override fun actionPerformed(e: ActionEvent) {
                aEditor.toggleDebug()
            }
        }

        if ((editor as AndroidEditor).isDebuggerEnabled) {
            debugButton.setSelected(true)
        }

        //    debugButton.setRolloverLabel(label);
        box.add(debugButton)

        addGap(box)
    }

    override fun handleRun(modifiers: Int) {
        val shift = modifiers and InputEvent.SHIFT_MASK != 0

        if (!shift) {
            aEditor.handleRunDevice()
        } else {
            aEditor.handleRunEmulator()
        }
    }

    override fun handleStop() {
        // TODO Auto-generated method stub
        aEditor.handleStop()
    }

    fun activateExport() {
        // TODO added to match the new API in EditorToolbar (activateRun, etc).
    }

    fun deactivateExport() {
        // TODO added to match the new API in EditorToolbar (activateRun, etc).
    }

    fun activateContinue() {
        continueButton!!.setSelected(true)
        repaint()
    }

    fun deactivateContinue() {
        continueButton!!.setSelected(false)
        repaint()
    }

    fun activateStep() {
        stepButton!!.setSelected(true)
        repaint()
    }

    fun deactivateStep() {
        stepButton!!.setSelected(false)
        repaint()
    }

    companion object {
         const val RUN = 0
         const val STOP = 1
         const val NEW = 2
         const val OPEN = 3
         const val SAVE = 4
         const val EXPORT = 5

        // TODO:
        // Buttons are initialized in createButtons, see code of EditorToolbar.rebuild()
        //  public void init() {
        //    Image[][] images = loadImages();
        //    for (int i = 0; i < 6; i++) {
        //      addButton(getTitle(i, false), getTitle(i, true), images[i], i == NEW);
        //    }
        //  }
        fun getTitle(index: Int, shift: Boolean): String? {

            when (index) {
                RUN -> return if (!shift) "Run on Device" else "Run in Emulator"
                STOP -> return "Stop"
                NEW -> return "New"
                OPEN -> return "Open"
                SAVE -> return "Save"
                EXPORT -> return if (!shift) getTextString("menu.file.export_signed_package") else getTextString("menu.file.export_android_project")
            }
            return null
        }

    }

}