/* -*- mode: java; c-basic-offset: 2; indent-tabs-mode: nil -*- */

/*
 Part of the Processing project - http://processing.org

 Copyright (c) 2012-17 The Processing Foundation
 Copyright (c) 2009-12 Ben Fry and Casey Reas

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

import processing.app.*
import processing.app.ui.EditorState
import processing.app.ui.EditorToolbar
import processing.app.ui.Toolkit
import processing.mode.android.AndroidMode.Companion.getTextString
import processing.mode.java.JavaEditor
import processing.mode.java.debug.LineID
import processing.mode.java.preproc.PdePreprocessor

import java.io.File
import java.io.IOException
import java.util.*

import javax.swing.JCheckBoxMenuItem
import javax.swing.JMenu
import javax.swing.JMenuItem
import javax.swing.event.MenuEvent
import javax.swing.event.MenuListener

internal class AndroidEditor (base: Base?, path: String?, state: EditorState?,
                                          mode: Mode?) : JavaEditor(base, path, state, mode) {
    private var androidMenu: JMenu? = null
    var appComponent = 0
    private val androiddebugger: AndroidDebugger
    private var settings: Settings? = null
    private val androidMode: AndroidMode?
    private val androidTools: List<AndroidTool>
    private var fragmentItem: JCheckBoxMenuItem? = null
    private var wallpaperItem: JCheckBoxMenuItem? = null
    private var watchfaceItem: JCheckBoxMenuItem? = null
    private var vrItem: JCheckBoxMenuItem? = null
    private var arItem: JCheckBoxMenuItem? = null

    override fun createPreprocessor(sketchName: String): PdePreprocessor {
        return AndroidPreprocessor(sketchName)
    }

    override fun createToolbar(): EditorToolbar {
        return AndroidToolbar(this, base)
    }

    /*
  // Not for now, it is unclear if the package name should be reset after save
  // as, i.e.: sketch_1 -> sketch_2 ... 
  @Override
  public boolean handleSaveAs() {
    boolean saved = super.handleSaveAs();
    if (saved) {
      // Reset the manifest so package name and versions are blank 
      androidMode.resetManifest(sketch, appComponent);
    }
    return saved;
  }
  */

    // . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .

    override fun buildFileMenu(): JMenu {
        val exportPkgTitle = AndroidToolbar.getTitle(AndroidToolbar.EXPORT, false)
        val exportPackage = Toolkit.newJMenuItem(exportPkgTitle, 'E'.toInt())
        exportPackage.addActionListener { handleExportPackage() }
        val exportProjectTitle = AndroidToolbar.getTitle(AndroidToolbar.EXPORT, true)
        val exportProject = Toolkit.newJMenuItemShift(exportProjectTitle, 'E'.toInt())
        exportProject.addActionListener { handleExportProject() }
        return buildFileMenu(arrayOf(exportPackage, exportProject))
    }

    override fun buildSketchMenu(): JMenu {
        val runItem = Toolkit.newJMenuItem(AndroidToolbar.getTitle(AndroidToolbar.RUN, false), 'R'.toInt())
        runItem.addActionListener { handleRunDevice() }

        val presentItem = Toolkit.newJMenuItemShift(AndroidToolbar.getTitle(AndroidToolbar.RUN, true), 'R'.toInt())
        presentItem.addActionListener { handleRunEmulator() }

        val stopItem = JMenuItem(AndroidToolbar.getTitle(AndroidToolbar.STOP, false))
        stopItem.addActionListener { handleStop() }

        return buildSketchMenu(arrayOf(buildDebugMenu(), runItem, presentItem, stopItem))
    }

    override fun buildModeMenu(): JMenu {
        androidMenu = JMenu(getTextString("menu.android"))
        val item: JMenuItem
        item = JMenuItem(getTextString("menu.android.sketch_permissions"))
        item.addActionListener { Permissions(sketch, appComponent, androidMode!!.folder) }

        androidMenu!!.add(item)
        androidMenu!!.addSeparator()
        fragmentItem = JCheckBoxMenuItem(getTextString("menu.android.app"))
        wallpaperItem = JCheckBoxMenuItem(getTextString("menu.android.wallpaper"))
        watchfaceItem = JCheckBoxMenuItem(getTextString("menu.android.watch_face"))
        vrItem = JCheckBoxMenuItem(getTextString("menu.android.vr"))
        arItem = JCheckBoxMenuItem(getTextString("menu.android.ar"))

        fragmentItem!!.addActionListener {
            fragmentItem!!.state = true
            wallpaperItem!!.state = false
            watchfaceItem!!.isSelected = false
            vrItem!!.isSelected = false
            arItem!!.isSelected = false

            setappComponent(AndroidBuild.APP)
        }

        wallpaperItem!!.addActionListener {
            fragmentItem!!.state = false
            wallpaperItem!!.state = true
            watchfaceItem!!.isSelected = false
            vrItem!!.isSelected = false
            arItem!!.isSelected = false
            setappComponent(AndroidBuild.WALLPAPER)
        }

        watchfaceItem!!.addActionListener {
            fragmentItem!!.state = false
            wallpaperItem!!.state = false
            watchfaceItem!!.isSelected = true
            vrItem!!.isSelected = false
            arItem!!.isSelected = false
            setappComponent(AndroidBuild.WATCHFACE)
        }

        vrItem!!.addActionListener {
            fragmentItem!!.state = false
            wallpaperItem!!.state = false
            watchfaceItem!!.isSelected = false
            vrItem!!.isSelected = true
            arItem!!.isSelected = false
            setappComponent(AndroidBuild.VR)
        }

        arItem!!.addActionListener {
            fragmentItem!!.state = false
            wallpaperItem!!.state = false
            watchfaceItem!!.isSelected = false
            vrItem!!.isSelected = false
            arItem!!.isSelected = true
            setappComponent(AndroidBuild.AR)
        }

        fragmentItem!!.state = false
        wallpaperItem!!.state = false
        watchfaceItem!!.isSelected = false
        vrItem!!.isSelected = false
        arItem!!.isSelected = false

        androidMenu!!.add(fragmentItem)
        androidMenu!!.add(wallpaperItem)
        androidMenu!!.add(watchfaceItem)
        androidMenu!!.add(vrItem)
        androidMenu!!.add(arItem)

        androidMenu!!.addSeparator()

        val devicesMenu = JMenu(getTextString("menu.android.devices"))
        val noDevicesItem = JMenuItem(getTextString("menu.android.devices.no_connected_devices"))

        noDevicesItem.isEnabled = false
        devicesMenu.add(noDevicesItem)

        androidMenu!!.add(devicesMenu)

        // Update the device list only when the Android menu is selected.
        androidMenu!!.addMenuListener(object : MenuListener {
            var task: UpdateDeviceListTask? = null
            var timer: Timer? = null

            override fun menuSelected(e: MenuEvent) {
                task = UpdateDeviceListTask(devicesMenu)
                timer = Timer()
                timer!!.schedule(task, 400, 3000)
            }

            override fun menuDeselected(e: MenuEvent) {
                timer!!.cancel()
            }

            override fun menuCanceled(e: MenuEvent) {
                timer!!.cancel()
            }
        })
        androidMenu!!.addSeparator()
        return androidMenu as JMenu
    }

    private fun setappComponent(comp: Int) {
        if (appComponent != comp) {
            appComponent = comp
            if (appComponent == AndroidBuild.APP) {
                settings!!["component"] = "app"
            } else if (appComponent == AndroidBuild.WALLPAPER) {
                settings!!["component"] = "wallpaper"
            } else if (appComponent == AndroidBuild.WATCHFACE) {
                settings!!["component"] = "watchface"
            } else if (appComponent == AndroidBuild.VR) {
                settings!!["component"] = "vr"
            } else if (appComponent == AndroidBuild.AR) {
                settings!!["component"] = "ar"
            }

            settings!!.save()
            androidMode!!.resetManifest(sketch, appComponent)
            androidMode.showSelectComponentMessage(comp)

        }
    }

    /**
     * Uses the main help menu, and adds a few extra options. If/when there's
     * Android-specific documentation, we'll switch to that.
     */
    override fun buildHelpMenu(): JMenu {
        val menu = super.buildHelpMenu()
        var item: JMenuItem
        menu.addSeparator()

        item = JMenuItem(getTextString("menu.help.processing_for_android_site"))
        item.addActionListener { Platform.openURL("http://android.processing.org/") }

        menu.add(item)

        item = JMenuItem(getTextString("menu.help.android_developer_site"))
        item.addActionListener { Platform.openURL("http://developer.android.com/") }
        menu.add(item)
        return menu
    }

    /** override the standard grab reference to just show the java reference  */
    override fun showReference(filename: String) {
        val javaReferenceFolder = Platform.getContentFile("modes/java/reference")
        val file = File(javaReferenceFolder, filename)
        Platform.openURL(file.toURI().toString())
    }

    override fun statusError(what: String) {
        super.statusError(what)
        toolbar.deactivateRun()
    }

    fun sketchStopped() {
        deactivateRun()
        statusEmpty()
    }

    /**
     * Build the sketch and run it inside an emulator with the debugger.
     */
    fun handleRunEmulator() {
        object : Thread() {
            override fun run() {
                toolbar.activateRun()

                startIndeterminate()
                prepareRun()

                try {
                    androidMode!!.handleRunEmulator(sketch, this@AndroidEditor, this@AndroidEditor)
                } catch (e: SketchException) {
                    statusError(e)
                } catch (e: IOException) {
                    statusError(e)
                }
                stopIndeterminate()
            }
        }.start()
    }

    /**
     * Build the sketch and run it on a device with the debugger connected.
     */
    fun handleRunDevice() {
        object : Thread() {
            override fun run() {

                toolbar.activateRun()
                startIndeterminate()
                prepareRun()

                try {
                    androidMode!!.handleRunDevice(sketch, this@AndroidEditor, this@AndroidEditor)
                } catch (e: SketchException) {
                    statusError(e)
                } catch (e: IOException) {
                    statusError(e)
                }

                stopIndeterminate()

            }
        }.start()
    }

    override fun handleStop() {

        if (androiddebugger.isStarted()) {
            androiddebugger.stopDebug()
        } else {
            toolbar.activateStop()
            androidMode!!.handleStop(this)
            toolbar.deactivateStop()
            toolbar.deactivateRun()

            // focus the PDE again after quitting presentation mode [toxi 030903]
            toFront()

        }
    }

    override fun getDebugger(): AndroidDebugger {
        return androiddebugger
    }

    public override fun deactivateDebug() {
        super.deactivateDebug()
    }

    public override fun activateContinue() {
        (toolbar as AndroidToolbar).activateContinue()
    }

    public override fun deactivateContinue() {
        (toolbar as AndroidToolbar).deactivateContinue()
    }

    public override fun activateStep() {
        (toolbar as AndroidToolbar).activateStep()
    }

    public override fun deactivateStep() {
        (toolbar as AndroidToolbar).deactivateStep()
    }

    override fun toggleDebug() {
        super.toggleDebug()
        androiddebugger.toggleDebug()
    }

    override fun toggleBreakpoint(lineIndex: Int) {
        androiddebugger.toggleBreakpoint(lineIndex)
    }

    public override fun getCurrentLineID(): LineID {
        return super.getCurrentLineID()
    }

    /**
     * Create a release build of the sketch and have its apk files ready.
     * If users want a debug build, they can do that from the command line.
     */
    fun handleExportProject() {

        if (handleExportCheckModified()) {
            object : Thread() {
                override fun run() {

                    (toolbar as AndroidToolbar).activateExport()
                    startIndeterminate()
                    statusNotice(getTextString("android_editor.status.exporting_project"))
                    val build = AndroidBuild(sketch, androidMode!!, appComponent)

                    try {
                        val exportFolder = build.exportProject()
                        if (exportFolder != null) {
                            Platform.openFolder(exportFolder)
                            statusNotice(getTextString("android_editor.status.project_export_completed"))
                        } else {
                            statusError(getTextString("android_editor.status.project_export_failed")!!)
                        }
                    } catch (e: IOException) {
                        statusError(e)
                    } catch (e: SketchException) {
                        statusError(e)
                    }

                    stopIndeterminate()

                    (toolbar as AndroidToolbar).deactivateExport()
                }
            }.start()
        }
    }

    /**
     * Create a release build of the sketch and install its apk files on the
     * attached device.
     */
    fun handleExportPackage() {
        if (androidMode!!.checkPackageName(sketch, appComponent) &&
                androidMode.checkAppIcons(sketch, appComponent) && handleExportCheckModified()) {
            KeyStoreManager(this)
        }
    }

    fun startExportPackage(keyStorePassword: String?) {
        object : Thread() {
            override fun run() {

                startIndeterminate()
                statusNotice(getTextString("android_editor.status.exporting_package"))

                val build = AndroidBuild(sketch, androidMode!!, appComponent)

                try {
                    val projectFolder = build.exportPackage(keyStorePassword!!)
                    if (projectFolder != null) {
                        statusNotice(getTextString("android_editor.status.package_export_completed"))
                        Platform.openFolder(projectFolder)
                    } else {
                        statusError(getTextString("android_editor.status.package_export_failed")!!)
                    }
                } catch (e: IOException) {
                    statusError(e)
                } catch (e: SketchException) {
                    statusError(e)
                } catch (e: InterruptedException) {
                    e.printStackTrace()
                } catch (e: Exception) {
                    e.printStackTrace()
                }

                stopIndeterminate()

            }
        }.start()
    }

//    fun getAppComponent(): Int {
//        return appComponent
//    }

    private fun loadModeSettings() {
        val sketchProps = File(sketch.codeFolder, "sketch.properties")
        try {
            settings = Settings(sketchProps)
            var save = false
            var component: String?
            if (!sketchProps.exists()) {
                component = DEFAULT_COMPONENT
                settings!!["component"] = component
                save = true
            } else {
                component = settings!!["component"]
                if (component == null) {
                    component = DEFAULT_COMPONENT
                    settings!!["component"] = component
                    save = true
                }
            }
            if (save) settings!!.save()
            if (component == "app") {
                appComponent = AndroidBuild.APP
                fragmentItem!!.state = true
            } else if (component == "wallpaper") {
                appComponent = AndroidBuild.WALLPAPER
                wallpaperItem!!.state = true
            } else if (component == "watchface") {
                appComponent = AndroidBuild.WATCHFACE
                watchfaceItem!!.state = true
            } else if (component == "vr") {
                appComponent = AndroidBuild.VR
                vrItem!!.state = true
            } else if (component == "ar") {
                appComponent = AndroidBuild.AR
                arItem!!.state = true
            }
            androidMode!!.initManifest(sketch, appComponent)
        } catch (e: IOException) {
            System.err.println(getTextString("android_editor.error.cannot_create_sketch_properties", sketchProps, e.message))
        }
    }

    private fun loadAndroidTools(): List<AndroidTool> {
        // This gets called before assigning mode to androidMode...
        val outgoing = ArrayList<AndroidTool>()
        val toolPath = File(androidMode!!.folder, "tools/SDKUpdater")
        var tool: AndroidTool? = null
        try {
            tool = AndroidTool(toolPath, androidMode.getSdk()!!)
            tool.init(base)
            outgoing.add(tool)
        } catch (e: Throwable) {
            e.printStackTrace()
        }
        outgoing.sort()
        return outgoing
    }

    private fun addToolsToMenu() {
        var item: JMenuItem
        for (tool in androidTools) {
            item = JMenuItem(getTextString(tool.menuTitle))
            item.addActionListener { tool.run() }
            androidMenu!!.add(item)
        }

//    item = new JMenuItem("AVD Manager");
//    item.addActionListener(new ActionListener() {
//      public void actionPerformed(ActionEvent e) {
//        File file = androidMode.getSDK().getAndroidTool();
//        PApplet.exec(new String[] { file.getAbsolutePath(), "avd" });
//      }
//    });
//    menu.add(item);
        item = JMenuItem(getTextString("menu.android.reset_adb"))
        item.addActionListener { //        editor.statusNotice("Resetting the Android Debug Bridge server.");
            val devices = Devices.getInstance()
            devices.killAdbServer()
            devices.startAdbServer()
        }
        androidMenu!!.add(item)
    }

    internal inner class UpdateDeviceListTask(private val deviceMenu: JMenu) : TimerTask() {
        private fun selectFirstDevice(deviceList: List<Device>): Device? {
            return if (0 < deviceList.size) deviceList[0] else null
        }

        override fun run() {
            if (androidMode == null || androidMode.getSdk() == null) return
            val devices = Devices.getInstance()
            if (appComponent == AndroidBuild.WATCHFACE) {
                devices.enableBluetoothDebugging()
            }
            val deviceList = devices.findMultiple(false)
            var selectedDevice = devices.selectedDevice
            if (deviceList.size == 0) {
                if (0 < deviceMenu.itemCount) {
                    deviceMenu.removeAll()
                    val noDevicesItem = JMenuItem(getTextString("menu.android.devices.no_connected_devices"))
                    noDevicesItem.isEnabled = false
                    deviceMenu.add(noDevicesItem)
                }
                devices.selectedDevice = null
            } else {
                deviceMenu.removeAll()
                if (selectedDevice == null) {
                    selectedDevice = selectFirstDevice(deviceList)
                    devices.selectedDevice = selectedDevice
                } else {
                    // check if selected device is still connected
                    var found = false
                    for (device in deviceList) {
                        if (device == selectedDevice) {
                            found = true
                            break
                        }
                    }
                    if (!found) {
                        selectedDevice = selectFirstDevice(deviceList)
                        devices.selectedDevice = selectedDevice
                    }
                }
                for (device in deviceList) {
                    val deviceItem = JCheckBoxMenuItem(device.name)
                    deviceItem.isEnabled = true
                    if (device == selectedDevice) deviceItem.state = true

                    // prevent checkboxmenuitem automatic state changing onclick
                    deviceItem.addChangeListener { if (device == devices.selectedDevice) deviceItem.state = true else deviceItem.state = false }
                    deviceItem.addActionListener {
                        devices.selectedDevice = device
                        for (i in 0 until deviceMenu.itemCount) {
                            (deviceMenu.getItem(i) as JCheckBoxMenuItem).state = false
                        }
                        deviceItem.state = true
                    }
                    deviceMenu.add(deviceItem)
                }
            }
        }

    }

    companion object {
        // Component selected by default
        const val DEFAULT_COMPONENT = "app"
    }

    // constructor or initializer block
    init {
        androidMode = mode as AndroidMode?
        androidMode!!.resetUserSelection()
        androidMode.checkSDK(this)

        androiddebugger = AndroidDebugger(this, androidMode)
        // Set saved breakpoints when sketch is opened for the first time
        for (lineID in stripBreakpointComments()) {
            androiddebugger.setBreakpoint(lineID)
        }

        super.debugger = androiddebugger
        androidTools = loadAndroidTools()
        addToolsToMenu()
        loadModeSettings()
    }
}