/* -*- mode: java; c-basic-offset: 2; indent-tabs-mode: nil -*- */

/*
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

import org.w3c.dom.Element
import org.w3c.dom.NodeList
import org.xml.sax.SAXException

import processing.app.Base
import processing.app.Platform
import processing.app.Preferences
import processing.app.ui.Toolkit

import processing.mode.android.AndroidMode.Companion.getTextString
import processing.mode.android.AndroidSDK.Companion.load
import processing.mode.android.AndroidUtil.createSubFolder
import processing.mode.android.AndroidUtil.extractFolder

import java.awt.Component
import java.awt.Dimension
import java.awt.Frame
import java.awt.event.ActionListener

import java.beans.PropertyChangeEvent
import java.beans.PropertyChangeListener

import java.io.File
import java.io.FileOutputStream
import java.io.IOException

import java.net.MalformedURLException
import java.net.URL

import javax.swing.*
import javax.swing.border.EmptyBorder
import javax.xml.parsers.DocumentBuilderFactory

import javax.xml.parsers.ParserConfigurationException
import javax.xml.xpath.XPathConstants
import javax.xml.xpath.XPathException
import javax.xml.xpath.XPathExpression
import javax.xml.xpath.XPathFactory

/**
 * @author Aditya Rana
 */
internal class SDKDownloader(private val editor: Frame) : JDialog(editor, getTextString("sdk_downloader.download_title"), true), PropertyChangeListener {

    private var progressBar: JProgressBar? = null
    private var downloadedTextArea: JLabel? = null
    private var downloadTask: SDKDownloadTask? = null
    var sDK: AndroidSDK? = null

    private var cancelled = false

    private var totalSize = 0

    internal inner class SDKUrlHolder {
        var platformVersion: String? = null
        var buildToolsVersion: String? = null
        var platformToolsUrl: String? = null
        var buildToolsUrl: String? = null
        var platformUrl: String? = null
        var toolsUrl: String? = null
        var emulatorUrl: String? = null
        var platformToolsFilename: String? = null
        var buildToolsFilename: String? = null
        var platformFilename: String? = null
        var toolsFilename: String? = null
        var emulatorFilename: String? = null
        var usbDriverUrl: String? = null
        var usbDriverFilename: String? = null
        var haxmFilename: String? = null
        var haxmUrl: String? = null
        var totalSize = 0
    }

    internal inner class SDKDownloadTask : SwingWorker<Any?, Any?>() {
        private var downloadedSize = 0
        private val BUFFER_SIZE = 4096

        @Throws(Exception::class)
        override fun doInBackground(): Any? {
            val sketchbookFolder = Base.getSketchbookFolder()
            val androidFolder = File(sketchbookFolder, "android")

            if (!androidFolder.exists()) androidFolder.mkdir()
            val sdkFolder = createSubFolder(androidFolder, "sdk")

            // creating sdk folders
            val platformsFolder = File(sdkFolder, "platforms")
            if (!platformsFolder.exists()) platformsFolder.mkdir()

            val buildToolsFolder = File(sdkFolder, "build-tools")
            if (!buildToolsFolder.exists()) buildToolsFolder.mkdir()

            val emulatorFolder = File(sdkFolder, "emulator")
            if (!emulatorFolder.exists()) emulatorFolder.mkdir()

            val extrasFolder = File(sdkFolder, "extras")
            if (!extrasFolder.exists()) extrasFolder.mkdir()

            val googleRepoFolder = File(extrasFolder, "google")
            if (!googleRepoFolder.exists()) googleRepoFolder.mkdir()

            val haxmFolder = File(extrasFolder, "intel/HAXM")
            if (!haxmFolder.exists()) haxmFolder.mkdirs()

            // creating temp folder for downloaded zip packages
            val tempFolder = File(androidFolder, "temp")
            if (!tempFolder.exists()) tempFolder.mkdir()

            try {
                val downloadUrls = SDKUrlHolder()
                val repositoryUrl = REPOSITORY_URL + REPOSITORY_LIST
                val addonUrl = REPOSITORY_URL + ADDON_LIST
                val haxmUrl = HAXM_URL + ADDON_LIST

                getMainDownloadUrls(downloadUrls, repositoryUrl, Platform.getName())
                getExtrasDownloadUrls(downloadUrls, addonUrl, Platform.getName())
                getHaxmDownloadUrl(downloadUrls, haxmUrl, Platform.getName())

                firePropertyChange(getTextString("download_property.change_event_total"), 0, downloadUrls.totalSize)

                // tools
                val downloadedTools = File(tempFolder, downloadUrls.toolsFilename)
                downloadAndUnpack(downloadUrls.toolsUrl, downloadedTools, sdkFolder, true)

                // platform-tools
                val downloadedPlatformTools = File(tempFolder, downloadUrls.platformToolsFilename)
                downloadAndUnpack(downloadUrls.platformToolsUrl, downloadedPlatformTools, sdkFolder, true)

                // build-tools
                val downloadedBuildTools = File(tempFolder, downloadUrls.buildToolsFilename)
                downloadAndUnpack(downloadUrls.buildToolsUrl, downloadedBuildTools, buildToolsFolder, true)

                // platform
                val downloadedPlatform = File(tempFolder, downloadUrls.platformFilename)
                downloadAndUnpack(downloadUrls.platformUrl, downloadedPlatform, platformsFolder, false)

                // emulator, unpacks directly to sdk folder 
                val downloadedEmulator = File(tempFolder, downloadUrls.emulatorFilename)
                downloadAndUnpack(downloadUrls.emulatorUrl, downloadedEmulator, sdkFolder, true)

                // usb driver
                if (Platform.isWindows()) {
                    val downloadedFolder = File(tempFolder, downloadUrls.usbDriverFilename)
                    downloadAndUnpack(downloadUrls.usbDriverUrl, downloadedFolder, googleRepoFolder, false)
                }

                // HAXM
                if (!Platform.isLinux()) {
                    val downloadedFolder = File(tempFolder, downloadUrls.haxmFilename)
                    downloadAndUnpack(downloadUrls.haxmUrl, downloadedFolder, haxmFolder, true)
                }

                if (Platform.isLinux() || Platform.isMacOS()) {
                    Runtime.getRuntime().exec("chmod -R 755 " + sdkFolder!!.absolutePath)
                }

                for (f in tempFolder.listFiles()) f.delete()

                tempFolder.delete()

                // Normalize built-tools and platform folders to android-<API LEVEL>
                var actualName = platformsFolder.listFiles()[0].name

                renameFolder(platformsFolder, "android-" + AndroidBuild.TARGET_SDK, actualName)
                actualName = buildToolsFolder.listFiles()[0].name
                renameFolder(buildToolsFolder, downloadUrls.buildToolsVersion, actualName)

                // Done, let's set the environment and load the new SDK!
                Platform.setenv("ANDROID_SDK", sdkFolder!!.absolutePath)
                Preferences.set("android.sdk.path", sdkFolder.absolutePath)

                sDK = load(false, null)
            } catch (e: ParserConfigurationException) {
                // TODO Handle exceptions here somehow (ie show error message)
                // and handle at least mkdir() results (above)
                e.printStackTrace()
            } catch (e: IOException) {
                e.printStackTrace()
            } catch (e: SAXException) {
                e.printStackTrace()
            }
            return null
        }

        override fun done() {
            super.done()
            isVisible = false
            dispose()
        }

        @Throws(IOException::class)
        private fun downloadAndUnpack(urlString: String?, saveTo: File,
                                      unpackTo: File?, setExec: Boolean) {
            var url: URL? = null
            url = try {
                URL(urlString)
            } catch (e: MalformedURLException) {
                //This is expected for API level 14 and more
                try {
                    URL(REPOSITORY_URL + urlString)
                } catch (e1: MalformedURLException) {
                    //This exception is not expected. Need to return.
                    e1.printStackTrace()
                    return
                }
            }
            val conn = url!!.openConnection()
            val inputStream = conn.getInputStream()
            val outputStream = FileOutputStream(saveTo)
            val b = ByteArray(BUFFER_SIZE)
            var count: Int

            while (inputStream.read(b).also { count = it } >= 0) {
                outputStream.write(b, 0, count)
                downloadedSize += count
                firePropertyChange(getTextString("download_property.change_event_downloaded"), 0, downloadedSize)
            }

            outputStream.flush()
            outputStream.close()
            inputStream.close()
            inputStream.close()
            outputStream.close()

            extractFolder(saveTo, unpackTo, setExec)
        }

        @Throws(ParserConfigurationException::class, IOException::class, SAXException::class, XPathException::class)
        private fun getMainDownloadUrls(urlHolder: SDKUrlHolder, repositoryUrl: String, requiredHostOs: String) {
            val dbf = DocumentBuilderFactory.newInstance()
            val db = dbf.newDocumentBuilder()
            val doc = db.parse(URL(repositoryUrl).openStream())
            val xPathfactory = XPathFactory.newInstance()
            val xpath = xPathfactory.newXPath()
            var expr: XPathExpression
            var remotePackages: NodeList
            var found: Boolean

            // -----------------------------------------------------------------------

            // platform

            expr = xpath.compile("//remotePackage[starts-with(@path, \"platforms;\")" +
                    "and contains(@path, '" + AndroidBuild.TARGET_SDK + "')]") // Skip latest platform; download only the targeted

            remotePackages = expr.evaluate(doc, XPathConstants.NODESET) as NodeList

            if (remotePackages != null) {
                val childNodes = remotePackages.item(0).childNodes
                val typeDetails = (childNodes as Element).getElementsByTagName("type-details")
                val apiLevel = (typeDetails.item(0) as Element).getElementsByTagName("api-level")
                urlHolder.platformVersion = apiLevel.item(0).textContent

                val archives = (childNodes as Element).getElementsByTagName("archive")
                val archive = archives.item(0).childNodes
                val complete = (archive as Element).getElementsByTagName("complete")
                val url = (complete.item(0) as Element).getElementsByTagName("url")
                val size = (complete.item(0) as Element).getElementsByTagName("size")

                urlHolder.platformFilename = url.item(0).textContent
                urlHolder.platformUrl = REPOSITORY_URL + urlHolder.platformFilename
                urlHolder.totalSize += size.item(0).textContent.toInt()
            } else {
                throw IOException(getTextString("sdk_downloader.error_cannot_find_platform_files"))
            }

            // Difference between platform tools, build tools, and SDK tools: 
            // http://stackoverflow.com/questions/19911762/what-is-android-sdk-build-tools-and-which-version-should-be-used
            // Always get the latest!

            // -----------------------------------------------------------------------

            // platform-tools

            expr = xpath.compile("//remotePackage[@path=\"platform-tools\"]")
            remotePackages = expr.evaluate(doc, XPathConstants.NODESET) as NodeList

            if (remotePackages != null) {
                parseAndSet(urlHolder, remotePackages, requiredHostOs, PLATFORM_TOOLS)
            } else {
                throw IOException(getTextString("sdk_downloader.error_cannot_find_platform_tools"))
            }

            // -----------------------------------------------------------------------

            // build-tools

            expr = xpath.compile("//remotePackage[starts-with(@path, \"build-tools;\")]")
            remotePackages = expr.evaluate(doc, XPathConstants.NODESET) as NodeList
            found = false

            if (remotePackages != null) {
                for (buildTool in 0 until remotePackages.length) {
                    val childNodes = remotePackages.item(buildTool).childNodes
                    val channel = (childNodes as Element).getElementsByTagName("channelRef")

                    if (channel.item(0).attributes.item(0).nodeValue != "channel-0") continue  //Stable channel only, skip others

                    val revision = (childNodes as Element).getElementsByTagName("revision")
                    val major = (revision.item(0) as Element).getElementsByTagName("major").item(0).textContent
                    val minor = (revision.item(0) as Element).getElementsByTagName("minor").item(0).textContent
                    val micro = (revision.item(0) as Element).getElementsByTagName("micro").item(0).textContent

                    if (major != AndroidBuild.TARGET_SDK) // Allows only the latest build tools for the target platform
                        continue
                    urlHolder.buildToolsVersion = "$major.$minor.$micro"

                    val archives = (childNodes as Element).getElementsByTagName("archive")

                    for (j in 0 until archives.length) {
                        val archive = archives.item(j).childNodes
                        val complete = (archive as Element).getElementsByTagName("complete")
                        val os = (archive as Element).getElementsByTagName("host-os")
                        val url = (complete.item(0) as Element).getElementsByTagName("url")
                        val size = (complete.item(0) as Element).getElementsByTagName("size")
                        if (os.item(0).textContent == requiredHostOs) {
                            urlHolder.buildToolsFilename = url.item(0).textContent
                            urlHolder.buildToolsUrl = REPOSITORY_URL + urlHolder.buildToolsFilename
                            urlHolder.totalSize += size.item(0).textContent.toInt()
                            found = true
                            break
                        }
                    }
                    if (found) break
                }
            }
            if (!found) {
                throw IOException(getTextString("sdk_downloader.error_cannot_find_build_tools"))
            }

            // -----------------------------------------------------------------------

            // tools

            expr = xpath.compile("//remotePackage[@path=\"tools\"]") // Matches two items according to xml file
            remotePackages = expr.evaluate(doc, XPathConstants.NODESET) as NodeList
            found = false

            if (remotePackages != null) {
                val childNodes = remotePackages.item(1).childNodes // Second item is the latest tools for now
                val archives = (childNodes as Element).getElementsByTagName("archive")

                for (i in 0 until archives.length) {
                    val archive = archives.item(i).childNodes
                    val complete = (archive as Element).getElementsByTagName("complete")
                    val os = (archive as Element).getElementsByTagName("host-os")
                    val url = (complete.item(0) as Element).getElementsByTagName("url")
                    val size = (complete.item(0) as Element).getElementsByTagName("size")

                    if (os.item(0).textContent == requiredHostOs) {
                        urlHolder.toolsFilename = url.item(0).textContent
                        urlHolder.toolsUrl = REPOSITORY_URL + urlHolder.toolsFilename
                        urlHolder.totalSize += size.item(0).textContent.toInt()
                        found = true
                        break
                    }
                }
            }
            if (!found) {
                throw IOException(getTextString("sdk_downloader.error_cannot_find_tools"))
            }

            // -----------------------------------------------------------------------

            // emulator

            expr = xpath.compile("//remotePackage[@path=\"emulator\"]") // Matches two items according to xml file
            remotePackages = expr.evaluate(doc, XPathConstants.NODESET) as NodeList
            found = false

            if (remotePackages != null) {
                for (i in 0 until remotePackages.length) {
                    val childNodes = remotePackages.item(i).childNodes
                    val channel = (childNodes as Element).getElementsByTagName("channelRef")
                    if (channel.item(0).attributes.item(0).nodeValue != "channel-0") continue  //Stable channel only, skip others
                    val archives = (childNodes as Element).getElementsByTagName("archive")

                    for (j in 0 until archives.length) {
                        val archive = archives.item(j).childNodes
                        val complete = (archive as Element).getElementsByTagName("complete")
                        val os = (archive as Element).getElementsByTagName("host-os")
                        val url = (complete.item(0) as Element).getElementsByTagName("url")
                        val size = (complete.item(0) as Element).getElementsByTagName("size")

                        if (os.item(0).textContent == requiredHostOs) {
                            urlHolder.emulatorFilename = url.item(0).textContent
                            urlHolder.emulatorUrl = REPOSITORY_URL + urlHolder.emulatorFilename
                            urlHolder.totalSize += size.item(0).textContent.toInt()
                            found = true
                            break
                        }
                    }
                    if (found) break
                }
            }
            if (!found) {
                throw IOException(getTextString("sdk_downloader.error_cannot_find_emulator"))
            }
        }
    }

    @Throws(ParserConfigurationException::class, IOException::class, SAXException::class, XPathException::class)
    private fun getExtrasDownloadUrls(urlHolder: SDKUrlHolder, repositoryUrl: String, requiredHostOs: String) {
        val dbf = DocumentBuilderFactory.newInstance()
        val db = dbf.newDocumentBuilder()
        val doc = db.parse(URL(repositoryUrl).openStream())
        val xPathfactory = XPathFactory.newInstance()
        val xpath = xPathfactory.newXPath()
        var expr: XPathExpression
        var remotePackages: NodeList

        // ---------------------------------------------------------------------

        // Android Support repository

        expr = xpath.compile("//remotePackage[@path=\"extras;android;m2repository\"]")
        remotePackages = expr.evaluate(doc, XPathConstants.NODESET) as NodeList
        remotePackages?.let { parseAndSet(urlHolder, it, requiredHostOs, ANDROID_REPO) }

        // ---------------------------------------------------------------------

        // Google repository

        expr = xpath.compile("//remotePackage[@path=\"extras;google;m2repository\"]")
        remotePackages = expr.evaluate(doc, XPathConstants.NODESET) as NodeList
        remotePackages?.let { parseAndSet(urlHolder, it, requiredHostOs, GOOGLE_REPO) }

        // ---------------------------------------------------------------------

        // USB driver

        expr = xpath.compile("//remotePackage[@path=\"extras;google;usb_driver\"]")
        remotePackages = expr.evaluate(doc, XPathConstants.NODESET) as NodeList
        if (remotePackages != null && Platform.isWindows()) {
            parseAndSet(urlHolder, remotePackages, requiredHostOs, USB_DRIVER)
        }
    }

    @Throws(ParserConfigurationException::class, IOException::class, SAXException::class, XPathException::class)
    private fun getHaxmDownloadUrl(urlHolder: SDKUrlHolder,
                                   repositoryUrl: String, requiredHostOs: String) {
        if (requiredHostOs == "linux") return
        val dbf = DocumentBuilderFactory.newInstance()
        val db = dbf.newDocumentBuilder()
        val doc = db.parse(URL(repositoryUrl).openStream())
        val xPathfactory = XPathFactory.newInstance()
        val xpath = xPathfactory.newXPath()
        val expr: XPathExpression
        val remotePackages: NodeList

        expr = xpath.compile("//remotePackage[@path=\"extras;intel;Hardware_Accelerated_Execution_Manager\"]")
        remotePackages = expr.evaluate(doc, XPathConstants.NODESET) as NodeList

        if (remotePackages != null) {
            for (i in 0 until remotePackages.length) {
                val childNodes = remotePackages.item(i).childNodes
                val archives = (childNodes as Element).getElementsByTagName("archive")
                val archive = archives.item(0).childNodes
                val os = (archive as Element).getElementsByTagName("host-os")
                if (os.item(0).textContent != requiredHostOs) continue
                val complete = (archive as Element).getElementsByTagName("complete")
                val url = (complete.item(0) as Element).getElementsByTagName("url")
                val size = (complete.item(0) as Element).getElementsByTagName("size")

                urlHolder.haxmFilename = url.item(0).textContent
                urlHolder.haxmUrl = HAXM_URL + urlHolder.haxmFilename
                urlHolder.totalSize += size.item(0).textContent.toInt()
                break
            }
        }
    }

    private fun parseAndSet(urlHolder: SDKUrlHolder, remotePackages: NodeList, requiredHostOs: String, packageN: Int) {
        val childNodes = remotePackages.item(0).childNodes
        val archives = (childNodes as Element).getElementsByTagName("archive")

        loop@ for (i in 0 until archives.length) {
            val archive = archives.item(i).childNodes
            val complete = (archive as Element).getElementsByTagName("complete")
            val url = (complete.item(0) as Element).getElementsByTagName("url")
            val size = (complete.item(0) as Element).getElementsByTagName("size")

            when (packageN) {
                PLATFORM_TOOLS -> {
                    val os = (archive as Element).getElementsByTagName("host-os")
                    if (os.item(0).textContent != requiredHostOs) continue@loop
                    urlHolder.platformToolsFilename = url.item(0).textContent
                    urlHolder.platformToolsUrl = REPOSITORY_URL + urlHolder.platformToolsFilename
                    urlHolder.totalSize += size.item(0).textContent.toInt()
                }

                USB_DRIVER -> {
                    urlHolder.usbDriverFilename = url.item(0).textContent
                    urlHolder.usbDriverUrl = REPOSITORY_URL + urlHolder.usbDriverFilename
                    urlHolder.totalSize += size.item(0).textContent.toInt()
                }
            }

            break
        }
    }

    @Throws(IOException::class)
    private fun renameFolder(baseFolder: File, expected: String?, actual: String) {
        val expectedPath = File(baseFolder, expected)
        val actualPath = File(baseFolder, actual)

        if (!expectedPath.exists()) {
            if (actualPath.exists()) {
                actualPath.renameTo(expectedPath)
            } else {
                throw IOException(getTextString("sdk_downloader.error.cannot_unpack_platform", actualPath.absolutePath))
            }
        }
    }

    override fun propertyChange(evt: PropertyChangeEvent) {
        if (evt.propertyName == getTextString("download_property.change_event_total")) {
            progressBar!!.isIndeterminate = false
            totalSize = evt.newValue as Int
            progressBar!!.maximum = totalSize
        } else if (evt.propertyName == getTextString("download_property.change_event_downloaded")) {
            downloadedTextArea!!.text = (humanReadableByteCount(evt.newValue as Long, si = true)
                    + " / " + humanReadableByteCount(totalSize.toLong(), true))
            progressBar!!.value = (evt.newValue as Int)
        }
    }

    fun run() {
        cancelled = false
        downloadTask = SDKDownloadTask()
        downloadTask!!.addPropertyChangeListener(this)
        downloadTask!!.execute()
        isAlwaysOnTop = true
        isVisible = true
    }

    fun cancelled(): Boolean {
        return cancelled
    }

    private fun createLayout() {
        val outer = contentPane
        outer.removeAll()

        val vbox = Box.createVerticalBox()
        vbox.border = EmptyBorder(BOX_BORDER, BOX_BORDER, BOX_BORDER, BOX_BORDER)
        outer.add(vbox)

        val labelText = getTextString("sdk_downloader.download_sdk_label")
        val textarea = JLabel(labelText)
        textarea.alignmentX = Component.LEFT_ALIGNMENT
        vbox.add(textarea)

        // Needed to put the progressBar inside this panel so we can set its size
        val progressPanel = JPanel()
        val boxLayout = BoxLayout(progressPanel, BoxLayout.Y_AXIS)
        progressPanel.layout = boxLayout

        progressBar = JProgressBar(0, 100)
        progressBar!!.preferredSize = Dimension(BAR_WIDTH, BAR_HEIGHT)
        progressBar!!.value = 0
        progressBar!!.isStringPainted = true
        progressBar!!.isIndeterminate = true
        progressBar!!.border = EmptyBorder(BAR_BORDER, BAR_BORDER, BAR_BORDER, BAR_BORDER)

        progressPanel.add(progressBar)
        vbox.add(progressPanel)

        downloadedTextArea = JLabel("0 / 0 MB")
        downloadedTextArea!!.alignmentX = Component.LEFT_ALIGNMENT
        vbox.add(downloadedTextArea)
        vbox.add(Box.createVerticalStrut(GAP))

        // buttons
        val buttons = JPanel()

        //    buttons.setPreferredSize(new Dimension(400, 35));
//    JPanel buttons = new JPanel() {
//      public Dimension getPreferredSize() {
//        return new Dimension(400, 35);
//      }
//      public Dimension getMinimumSize() {
//        return new Dimension(400, 35);
//      }
//      public Dimension getMaximumSize() {
//        return new Dimension(400, 35);
//      }
//    };

//    Box buttons = Box.createHorizontalBox();

        buttons.alignmentX = Component.LEFT_ALIGNMENT
        val cancelButton = JButton(getTextString("download_prompt.cancel"))
        val dim = Dimension(Toolkit.getButtonWidth() * 2,
                Toolkit.zoom(cancelButton.preferredSize.height))

        cancelButton.preferredSize = dim
        cancelButton.addActionListener {
            if (downloadTask != null) {
                downloadTask!!.cancel(true)
            }
            isVisible = false
            cancelled = true
        }

        cancelButton.isEnabled = true
        buttons.add(cancelButton)

        //    buttons.setMaximumSize(new Dimension(300, buttons.getPreferredSize().height));
        vbox.add(buttons)

        val root = getRootPane()
        root.defaultButton = cancelButton
        val disposer = ActionListener { isVisible = false }

        Toolkit.registerWindowCloseKeys(root, disposer)
        Toolkit.setIcon(this)
        pack()
        isResizable = false
        setLocationRelativeTo(editor)
    }

    companion object {
        private val BOX_BORDER = Toolkit.zoom(13)
        private val BAR_BORDER = Toolkit.zoom(10)
        private val BAR_WIDTH = Toolkit.zoom(300)
        private val BAR_HEIGHT = Toolkit.zoom(30)
        private val GAP = Toolkit.zoom(13)

        private const val PLATFORM_TOOLS = 2
        private const val ANDROID_REPO = 4
        private const val GOOGLE_REPO = 5
        private const val USB_DRIVER = 6

        private const val REPOSITORY_URL = "https://dl.google.com/android/repository/"
        private const val HAXM_URL = "https://dl.google.com/android/repository/extras/intel/"
        private const val REPOSITORY_LIST = "repository2-1.xml"
        private const val ADDON_LIST = "addon2-1.xml"

        // http://stackoverflow.com/questions/3758606/how-to-convert-byte-size-into-human-readable-format-in-java
        fun humanReadableByteCount(bytes: Long, si: Boolean): String {
            val unit = if (si) 1000 else 1024
            if (bytes < unit) return "$bytes B"
            val exp = (Math.log(bytes.toDouble()) / Math.log(unit.toDouble())).toInt()
            val pre = (if (si) "kMGTPE" else "KMGTPE")[exp - 1].toString() + if (si) "" else "i"
            return String.format("%.1f %sB", bytes / Math.pow(unit.toDouble(), exp.toDouble()), pre)
        }
    }

    // constructor or initializer block
    // will be executed before any other operation
    init {
        createLayout()
    }
}