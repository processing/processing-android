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

package processing.mode.android;

import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.util.ArrayList;
import java.util.List;

import processing.app.Base;
import processing.app.ui.Editor;
import processing.app.ui.EditorButton;
import processing.app.ui.EditorToolbar;
import processing.app.Language;


@SuppressWarnings("serial")
public class AndroidToolbar extends EditorToolbar {
  static protected final int RUN    = 0;
  static protected final int STOP   = 1;

  static protected final int NEW    = 2;
  static protected final int OPEN   = 3;
  static protected final int SAVE   = 4;
  static protected final int EXPORT = 5;


  public AndroidToolbar(Editor editor, Base base) {
    super(editor);
  }


  // TODO:
  // Buttons are initialized in createButtons, see code of EditorToolbar.rebuild()
//  public void init() {
//    Image[][] images = loadImages();
//    for (int i = 0; i < 6; i++) {
//      addButton(getTitle(i, false), getTitle(i, true), images[i], i == NEW);
//    }
//  }


  static public String getTitle(int index, boolean shift) {
    switch (index) {
    case RUN:    return !shift ? "Run on Device" : "Run in Emulator";
    case STOP:   return "Stop";
    case NEW:    return "New";
    case OPEN:   return "Open";
    case SAVE:   return "Save";
    case EXPORT: return !shift ? "Export Signed Package" : "Export Android Project";
    }
    return null;
  }

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

  @Override
  public List<EditorButton> createButtons() {
    ArrayList<EditorButton> toReturn = new ArrayList<EditorButton>();
    runButton = new EditorButton(this,
                                 "/lib/toolbar/run",
                                 "Run on device",
                                 "Run on emulator") {
      @Override
      public void actionPerformed(ActionEvent e) {
        handleRun(e.getModifiers());
      }
    };
    toReturn.add(runButton);

    stopButton = new EditorButton(this,
                                  "/lib/toolbar/stop",
                                  Language.text("toolbar.stop")) {
      @Override
      public void actionPerformed(ActionEvent e) {
        handleStop();
      }
    };
    toReturn.add(stopButton);
    return toReturn;
  }

  @Override
  public void handleRun(int modifiers) {
    AndroidEditor aEditor = (AndroidEditor) editor;
    boolean shift = (modifiers & InputEvent.SHIFT_MASK) != 0;
    if (!shift) {
      aEditor.handleRunDevice();
    } else {
      aEditor.handleRunEmulator();
    }
  }


  @Override
  public void handleStop() {
    // TODO Auto-generated method stub
    AndroidEditor aEditor = (AndroidEditor) editor;
    aEditor.handleStop();
  }


  public void activateExport() {
    // TODO added to match the new API in EditorToolbar (activateRun, etc).
  }


  public void deactivateExport() {
    // TODO added to match the new API in EditorToolbar (activateRun, etc).
  }
}