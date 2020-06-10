/* -*- mode: java; c-basic-offset: 2; indent-tabs-mode: nil -*- */ /*
 Part of the Processing project - http://processing.org

 Copyright (c) 2017 The Processing Foundation

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
package processing.mode.android.tools

import com.android.repository.api.*
import com.android.repository.io.FileOpUtils
import com.android.repository.io.impl.FileSystemFileOp
import com.android.repository.util.InstallerUtil
import com.android.sdklib.repository.AndroidSdkHandler
import com.android.sdklib.repository.installer.SdkInstallerUtil
import com.android.sdklib.repository.legacy.LegacyDownloader
import com.android.sdklib.tool.sdkmanager.SdkManagerCli
import processing.app.Base
import processing.app.Preferences
import processing.app.tools.Tool
import processing.app.ui.Toolkit
import processing.mode.android.tools.SDKUpdater
import java.awt.Dimension
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import java.awt.event.ActionListener
import java.awt.event.KeyEvent
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.beans.PropertyChangeEvent
import java.beans.PropertyChangeListener
import java.io.File
import java.util.*
import java.util.concurrent.CancellationException
import java.util.concurrent.ExecutionException
import javax.swing.*
import javax.swing.border.EmptyBorder
import javax.swing.table.DefaultTableModel

class SDKUpdater : JFrame(), PropertyChangeListener, Tool {
    private val columns = Vector(Arrays.asList(
            "Package name", "Installed version", "Available update"))
    private var sdkFolder: File? = null
    private var queryTask: QueryTask? = null
    private var downloadTask: DownloadTask? = null
    private var downloadTaskRunning = false
    private var packageList: Vector<Vector<String>>? = null
    private var packageTable: DefaultTableModel? = null
    private var numUpdates = 0
    private var progressBar: JProgressBar? = null
    private var status: JLabel? = null
    private var actionButton: JButton? = null
    private var table: JTable? = null
    override fun init(base: Base) {
        createLayout(base.activeEditor == null)
    }

    override fun run() {
        isVisible = true
        val path = Preferences.get("android.sdk.path")
        sdkFolder = File(path)
        queryTask = QueryTask()
        queryTask!!.addPropertyChangeListener(this)
        queryTask!!.execute()
        status!!.text = "Querying packages..."
    }

    override fun getMenuTitle(): String {
        return "menu.android.sdk_updater"
    }

    override fun propertyChange(evt: PropertyChangeEvent) {
        when (evt.propertyName) {
            PROPERTY_CHANGE_QUERY -> {
                progressBar!!.isIndeterminate = false
                if (numUpdates == 0) {
                    actionButton!!.isEnabled = false
                    status!!.text = "No updates available"
                } else {
                    actionButton!!.isEnabled = true
                    if (numUpdates == 1) {
                        status!!.text = "1 update found!"
                    } else {
                        status!!.text = "$numUpdates updates found!"
                    }
                }
            }
        }
    }

    internal inner class QueryTask : SwingWorker<Any?, Any?>() {
        var progress: ProgressIndicator

        @Throws(Exception::class)
        override fun doInBackground(): Any? {
            numUpdates = 0
            packageList = Vector()

            /* Following code is from listPackages() of com.android.sdklib.tool.SdkManagerCli
               with some changes
       */
            val mHandler = AndroidSdkHandler.getInstance(sdkFolder)
            val fop = FileOpUtils.create() as FileSystemFileOp
            val mRepoManager = mHandler.getSdkManager(progress)
            mRepoManager.loadSynchronously(0, progress, LegacyDownloader(fop, object : SettingsController {
                override fun getForceHttp(): Boolean {
                    return false
                }

                override fun setForceHttp(b: Boolean) {}
                override fun getChannel(): Channel? {
                    return null
                }
            }), null)
            val packages = mRepoManager.packages
            val installed = HashMap<String, List<String>>()
            for (local in packages.localPackages.values) {
                val path = local.path
                var name = local.displayName
                val ver = local.version.toString()
                // Remove version from the display name
                val rev = name.indexOf(", rev")
                if (-1 < rev) {
                    name = name.substring(0, rev)
                }
                val maj = ver.indexOf(".")
                if (-1 < maj) {
                    val major = ver.substring(0, maj)
                    val pos = name.indexOf(major)
                    if (-1 < pos) {
                        name = name.substring(0, pos).trim { it <= ' ' }
                    }
                }
                installed[path] = Arrays.asList(name, ver)
            }
            val updated = HashMap<String, List<String>>()
            for (update in packages.updatedPkgs) {
                val path = update.path
                val loc = update.local.version.toString()
                val rem = update.remote.version.toString()
                updated[path] = Arrays.asList(loc, rem)
            }
            for (path in installed.keys) {
                val info = Vector<String>()
                val locInfo = installed[path]!!
                info.add(locInfo[0])
                info.add(locInfo[1])
                if (updated.containsKey(path)) {
                    val upVer = updated[path]!![1]
                    info.add(upVer)
                    numUpdates++
                } else {
                    info.add("")
                }
                packageList!!.add(info)
            }
            return null
        }

        override fun done() {
            super.done()
            try {
                get()
                firePropertyChange(PROPERTY_CHANGE_QUERY, "query", "SUCCESS")
                if (packageList != null) {
                    packageTable!!.setDataVector(packageList, columns)
                    packageTable!!.fireTableDataChanged()
                }
            } catch (e: InterruptedException) {
                cancel(false)
            } catch (e: CancellationException) {
                cancel(false)
            } catch (e: ExecutionException) {
                cancel(true)
                JOptionPane.showMessageDialog(null,
                        e.cause.toString(), "Error", JOptionPane.ERROR_MESSAGE)
                e.printStackTrace()
            }
        }

        init {
            progress = ConsoleProgressIndicator()
        }
    }

    internal inner class DownloadTask : SwingWorker<Any?, Any?>() {
        var progress: ProgressIndicator

        @Throws(Exception::class)
        override fun doInBackground(): Any? {
            downloadTaskRunning = true

            /* Following code is from installPackages() of com.android.sdklib.tool.SdkManagerCli
               with some changes
       */
            val mHandler = AndroidSdkHandler.getInstance(sdkFolder)
            val fop = FileOpUtils.create() as FileSystemFileOp
            val settings = CustomSettings()
            val downloader: Downloader = LegacyDownloader(fop, settings)
            val mRepoManager = mHandler.getSdkManager(progress)
            mRepoManager.loadSynchronously(0, progress, downloader, settings)
            var remotes: MutableList<RemotePackage?>? = ArrayList()
            for (path in settings.getPaths(mRepoManager)) {
                val p = mRepoManager.packages.remotePackages[path]
                if (p == null) {
                    progress.logWarning("Failed to find package $path")
                    throw SdkManagerCli.CommandFailedException()
                }
                remotes!!.add(p)
            }
            remotes = InstallerUtil.computeRequiredPackages(
                    remotes, mRepoManager.packages, progress)
            if (remotes != null) {
                for (p in remotes) {
                    val installer = SdkInstallerUtil.findBestInstallerFactory(p, mHandler)
                            .createInstaller(p, mRepoManager, downloader, mHandler.fileOp)
                    if (!(installer.prepare(progress) && installer.complete(progress))) {
                        // there was an error, abort.
                        throw SdkManagerCli.CommandFailedException()
                    }
                }
            } else {
                progress.logWarning("Unable to compute a complete list of dependencies.")
                throw SdkManagerCli.CommandFailedException()
            }
            return null
        }

        override fun done() {
            super.done()
            try {
                get()
                actionButton!!.isEnabled = false
                status!!.text = "Refreshing packages..."
                queryTask = QueryTask()
                queryTask!!.addPropertyChangeListener(this@SDKUpdater)
                queryTask!!.execute()
            } catch (e: InterruptedException) {
                cancel(true)
            } catch (e: CancellationException) {
                cancel(true)
            } catch (e: ExecutionException) {
                cancel(true)
                JOptionPane.showMessageDialog(null,
                        e.cause.toString(), "Error", JOptionPane.ERROR_MESSAGE)
                e.printStackTrace()
            } finally {
                downloadTaskRunning = false
                progressBar!!.isIndeterminate = false
            }
        }

        internal inner class CustomSettings : SettingsController {
            /* Dummy implementation with some necessary methods from the original
               implementation in com.android.sdklib.tool.SdkManagerCli
       */
            override fun getForceHttp(): Boolean {
                return false
            }

            override fun setForceHttp(b: Boolean) {}
            override fun getChannel(): Channel? {
                return null
            }

            fun getPaths(mgr: RepoManager): List<String> {
                val updates: MutableList<String> = ArrayList()
                for (upd in mgr.packages.updatedPkgs) {
                    if (!upd.remote.obsolete()) {
                        updates.add(upd.representative.path)
                    }
                }
                return updates
            }
        }

        init {
            progress = ConsoleProgressIndicator()
        }
    }

    private fun createLayout(standalone: Boolean) {
        title = menuTitle
        val outer = contentPane
        outer.removeAll()
        val verticalBox = Box.createVerticalBox()
        verticalBox.border = EmptyBorder(BORDER, BORDER, BORDER, BORDER)
        outer.add(verticalBox)

        /* Packages panel */
        val packagesPanel = JPanel()
        val boxLayout = BoxLayout(packagesPanel, BoxLayout.Y_AXIS)
        packagesPanel.layout = boxLayout

        // Packages table
        packageTable = object : DefaultTableModel(NUM_ROWS, columns.size) {
            override fun isCellEditable(row: Int, column: Int): Boolean {
                return false
            }

            override fun getColumnClass(columnIndex: Int): Class<*> {
                return String::class.java
            }
        }
        table = object : JTable(packageTable) {
            override fun getColumnName(column: Int): String {
                return columns[column]
            }
        }
        (table as JTable).fillsViewportHeight = true
        (table as JTable).autoResizeMode = JTable.AUTO_RESIZE_ALL_COLUMNS
        (table as JTable).rowHeight = Toolkit.zoom((table as JTable).rowHeight)
        val dim = Dimension((table as JTable).columnCount * COL_WIDTH,
                (table as JTable).rowHeight * NUM_ROWS)
        (table as JTable).preferredScrollableViewportSize = dim
        packagesPanel.add(JScrollPane(table))
        val controlPanel = JPanel()
        val gridBagLayout = GridBagLayout()
        controlPanel.layout = gridBagLayout
        val gbc = GridBagConstraints()
        gbc.insets = Insets(INSET, INSET, INSET, INSET)
        status = JLabel()
        status!!.text = "Starting up..."
        gbc.gridx = 0
        gbc.gridy = 0
        controlPanel.add(status, gbc)

        // Using an indeterminate progress bar from now until we learn
        // how to update the fraction of the query/download process:
        // https://github.com/processing/processing-android/issues/362
        progressBar = JProgressBar()
        progressBar!!.isIndeterminate = true
        gbc.gridx = 0
        gbc.gridy = 1
        gbc.weightx = 1.0
        gbc.fill = GridBagConstraints.HORIZONTAL
        controlPanel.add(progressBar, gbc)
        actionButton = JButton("Update") // handles Update/Cancel
        actionButton!!.addActionListener {
            if (downloadTaskRunning) { // i.e button state is Cancel
                cancelTasks()
            } else { // i.e button state is Update
                downloadTask = DownloadTask()
                progressBar!!.isIndeterminate = true
                downloadTask!!.execute()

                // getFraction() always returns 0.0, needs to be set somewhere (??)
//          Thread update = new Thread() {
//            @Override
//            public void run() {
//              while (downloadTaskRunning) {
//                try {
//                  Thread.sleep(100);
//                } catch (InterruptedException e) { }
//                System.out.println("Updating: " + downloadTask.progress.getFraction());
//              }
//            }
//          };
//          update.start();
                status!!.text = "Downloading available updates..."
                actionButton!!.text = "Cancel"
            }
        }
        actionButton!!.isEnabled = false
        actionButton!!.preferredSize = Dimension(BUTTON_WIDTH, BUTTON_HEIGHT)
        gbc.gridx = 1
        gbc.gridy = 0
        gbc.weightx = 0.0
        gbc.fill = GridBagConstraints.HORIZONTAL
        controlPanel.add(actionButton, gbc)
        val disposer = ActionListener {
            cancelTasks()
            if (standalone) {
                System.exit(0)
            } else {
                isVisible = false
            }
        }
        val closeButton = JButton("Close")
        closeButton.preferredSize = Dimension(BUTTON_WIDTH, BUTTON_HEIGHT)
        closeButton.addActionListener(disposer)
        closeButton.isEnabled = true
        gbc.gridx = 1
        gbc.gridy = 1
        gbc.weightx = 0.0
        gbc.fill = GridBagConstraints.HORIZONTAL
        controlPanel.add(closeButton, gbc)
        verticalBox.add(packagesPanel)
        verticalBox.add(Box.createVerticalStrut(GAP))
        verticalBox.add(controlPanel)
        pack()
        val root = getRootPane()
        root.defaultButton = closeButton
        Toolkit.registerWindowCloseKeys(root, disposer)
        Toolkit.setIcon(this)
        addWindowListener(object : WindowAdapter() {
            override fun windowClosing(e: WindowEvent) {
                cancelTasks()
                super.windowClosing(e)
            }
        })
        registerWindowCloseKeys(getRootPane(), disposer)
        setLocationRelativeTo(null)
        isResizable = false
        isVisible = false
    }

    fun cancelTasks() {
        queryTask!!.cancel(true)
        if (downloadTaskRunning) {
            downloadTask!!.cancel(true)
            status!!.text = "Download canceled"
            JOptionPane.showMessageDialog(null,
                    "Download canceled", "Warning", JOptionPane.WARNING_MESSAGE)
            actionButton!!.text = "Update"
        }
    }

    companion object {
        private const val NUM_ROWS = 10
        private val COL_WIDTH = Toolkit.zoom(220)
        private val BORDER = Toolkit.zoom(13)
        private val GAP = Toolkit.zoom(13)
        private val INSET = Toolkit.zoom(1)
        private val BUTTON_WIDTH = Toolkit.zoom(75)
        private val BUTTON_HEIGHT = Toolkit.zoom(25)
        private const val PROPERTY_CHANGE_QUERY = "query"

        /**
         * Registers key events for a Ctrl-W and ESC with an ActionListener
         * that will take care of disposing the window.
         */
        fun registerWindowCloseKeys(root: JRootPane,
                                    disposer: ActionListener?) {
            var stroke = KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0)
            root.registerKeyboardAction(disposer, stroke,
                    JComponent.WHEN_IN_FOCUSED_WINDOW)
            val modifiers = java.awt.Toolkit.getDefaultToolkit().menuShortcutKeyMask
            stroke = KeyStroke.getKeyStroke('W'.toInt(), modifiers)
            root.registerKeyboardAction(disposer, stroke,
                    JComponent.WHEN_IN_FOCUSED_WINDOW)
        }
    }
}