package processing.mode.android;

import processing.mode.java.Debugger;
import processing.mode.java.JavaEditor;

public class AndroidDebugger extends Debugger {
    /// editor window, acting as main view
    protected AndroidEditor editor;

    public AndroidDebugger(AndroidEditor editor) {
        super(editor);
        this.editor = editor;
    }

    @Override
    public AndroidEditor getEditor() {
        return editor;
    }

    @Override
    public synchronized void startDebug() {
        //stopDebug(); // stop any running sessions
        if (isStarted()) {
            return; // do nothing
        }

        // we are busy now
        editor.statusBusy();

        // clear console
        editor.clearConsole();

        // clear variable inspector (also resets expanded states)
        editor.variableInspector().reset();

        // load edits into sketch obj, etc...
        editor.prepareRun();

        //TODO : attach debugger here
    }
}
