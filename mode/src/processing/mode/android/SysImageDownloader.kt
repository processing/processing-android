/* -*- mode: java; c-basic-offset: 2; indent-tabs-mode: nil -*- */

/*
 Part of the Processing project - http://processing.org

 Copyright (c) 2016 The Processing Foundation
 
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
import processing.app.exec.StreamPump
import processing.app.ui.Toolkit

import processing.core.PApplet

import processing.mode.android.AndroidMode.Companion.getTextString
import processing.mode.android.AndroidSDK.Companion.hAXMInstallerFolder
import processing.mode.android.AndroidUtil.extractFolder
import processing.mode.android.AndroidUtil.showMessage

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
import javax.swing.event.HyperlinkEvent
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.parsers.ParserConfigurationException
import javax.xml.xpath.XPathConstants
import javax.xml.xpath.XPathException
import javax.xml.xpath.XPathExpression
import javax.xml.xpath.XPathFactory

/**
 * @author Aditya Rana
 */
internal class SysImageDownloader(private val editor: Frame?, private val wear: Boolean, private val askABI: Boolean) : JDialog(editor, getTextString("sys_image_downloader.download_title"), true), PropertyChangeListener {
    private var progressBar: JProgressBar? = null
    private var downloadedTextArea: JLabel? = null
    private var downloadTask: DownloadTask? = null

    var result = false

    private var abi: String? = null

    private var cancelled = false

    private var totalSize = 0

    internal inner class UrlHolder {
        var platformVersion: String? = null
        var sysImgUrl: String? = null
        var sysImgTag: String? = null
        var sysImgWearUrl: String? = null
        var sysImgWearTag: String? = null
        var sysImgFilename: String? = null
        var sysImgWearFilename: String? = null
        var totalSize = 0
    }

    internal inner class DownloadTask : SwingWorker<Any?, Any?>() {
        private var downloadedSize = 0
        private val BUFFER_SIZE = 4096

        @Throws(Exception::class)
        override fun doInBackground(): Any? {
            result = false

            // The SDK should already be detected by the android mode
            val sdkPrefsPath = Preferences.get("android.sdk.path")
            val sketchbookFolder = Base.getSketchbookFolder()
            val androidFolder = File(sketchbookFolder, "android")

            if (!androidFolder.exists()) androidFolder.mkdir()
            val sdkFolder = File(sdkPrefsPath)

            if (!sdkFolder.exists()) {
                throw IOException("SDK folder does not exist " + sdkFolder.absolutePath)
            }

            // creating sdk folders
            val sysImgFolder = File(sdkFolder, "system-images")
            if (!sysImgFolder.exists()) sysImgFolder.mkdir()

            // creating temp folder for downloaded zip packages
            val tempFolder = File(androidFolder, "temp")
            if (!tempFolder.exists()) tempFolder.mkdir()

            try {
                val repo: String
                repo = if (wear) {
                    SYS_IMAGES_WEAR_URL + SYS_IMAGES_WEAR_LIST
                } else if (abi == "arm") {
                    // The ARM images using Google APIs are too slow, so use the 
                    // older Android (AOSP) images.
                    SYS_IMAGES_ARM_URL + SYS_IMAGES_PHONE_LIST
                } else {
                    SYS_IMAGES_PHONE_URL + SYS_IMAGES_PHONE_LIST
                }

                val downloadUrls = UrlHolder()

                getDownloadUrls(downloadUrls, repo, Platform.getName())
                firePropertyChange(getTextString("download_property.change_event_total"), 0, downloadUrls.totalSize)

                totalSize = downloadUrls.totalSize

                if (wear) {
                    // wear system images
                    val downloadedSysImgWear = File(tempFolder, downloadUrls.sysImgWearFilename)
                    val tmp = File(sysImgFolder, "android-" + AndroidBuild.TARGET_SDK)

                    if (!tmp.exists()) tmp.mkdir()

                    val sysImgWearFinalFolder = File(tmp, downloadUrls.sysImgWearTag)

                    if (!sysImgWearFinalFolder.exists()) sysImgWearFinalFolder.mkdir()

                    downloadAndUnpack(downloadUrls.sysImgWearUrl, downloadedSysImgWear, sysImgWearFinalFolder, false)

                    fixSourceProperties(sysImgWearFinalFolder)
                } else {
                    // mobile system images
                    val downloadedSysImg = File(tempFolder, downloadUrls.sysImgFilename)
                    val level = (if (abi == "arm") AVD.TARGET_SDK_ARM else AndroidBuild.TARGET_SDK)!!
                    val tmp = File(sysImgFolder, "android-$level")

                    if (!tmp.exists()) tmp.mkdir()

                    val sysImgFinalFolder = File(tmp, downloadUrls.sysImgTag)

                    if (!sysImgFinalFolder.exists()) sysImgFinalFolder.mkdir()

                    downloadAndUnpack(downloadUrls.sysImgUrl, downloadedSysImg, sysImgFinalFolder, false)

                    fixSourceProperties(sysImgFinalFolder)
                }

                if (Platform.isLinux() || Platform.isMacOS()) {
                    Runtime.getRuntime().exec("chmod -R 755 " + sysImgFolder.absolutePath)
                }

                for (f in tempFolder.listFiles()) f.delete()
                tempFolder.delete()

                if (Platform.isLinux() && Platform.getVariant() == "64") {
                    showMessage(getTextString("sys_image_downloader.dialog.ia32libs_title"), getTextString("sys_image_downloader.dialog.ia32libs_body")!!)
                }

                result = true

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
                                      unpackTo: File, setExec: Boolean) {
            var url: URL? = null
            url = try {
                URL(urlString)
            } catch (e: MalformedURLException) {
                e.printStackTrace()
                return
            }

            val conn = url.openConnection()
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

        // For some reason the source.properties file includes Addon entries, 
        // and this breaks the image...
        private fun fixSourceProperties(imageFolder: File) {
            for (d in imageFolder.listFiles()) {
                // Should iterate over the installed archs (x86, etc)
                if (d.isDirectory) {
                    for (f in d.listFiles()) {
                        if (PApplet.getExtension(f.name) == "properties") {
                            val linesIn = PApplet.loadStrings(f)
                            var concat = ""
                            for (l in linesIn) {
                                if (l.indexOf("Addon") == -1) concat += """
     $l
     
     """.trimIndent()
                            }
                            val linesOut = concat.split("\n").toTypedArray()
                            PApplet.saveStrings(f, linesOut)
                        }
                    }
                }
            }
        }

        @Throws(ParserConfigurationException::class, IOException::class, SAXException::class, XPathException::class)
        private fun getDownloadUrls(urlHolder: UrlHolder, repositoryUrl: String, requiredHostOs: String) {
            val dbf = DocumentBuilderFactory.newInstance()
            val db = dbf.newDocumentBuilder()
            val xPathfactory = XPathFactory.newInstance()
            val xpath = xPathfactory.newXPath()
            val expr: XPathExpression
            val remotePackages: NodeList

            expr = if (abi == "arm") xpath.compile("//remotePackage[contains(@path, '" + AVD.TARGET_SDK_ARM + "')" +
                    "and contains(@path, \"armeabi-v7a\")]") else xpath.compile("//remotePackage[contains(@path, '" + AndroidBuild.TARGET_SDK + "')" +
                    "and contains(@path, \"x86\")]")

            if (wear) {
                val docSysImgWear = db.parse(URL(repositoryUrl).openStream())
                remotePackages = expr.evaluate(docSysImgWear, XPathConstants.NODESET) as NodeList

                val childNodes = remotePackages.item(0).childNodes
                val typeDetails = (childNodes as Element).getElementsByTagName("type-details")
                val tag = (typeDetails.item(0) as Element).getElementsByTagName("tag")
                val id = (tag.item(0) as Element).getElementsByTagName("id")

                urlHolder.sysImgWearTag = id.item(0).textContent

                val archives = (childNodes as Element).getElementsByTagName("archive")
                val archive = archives.item(0).childNodes
                val complete = (archive as Element).getElementsByTagName("complete")
                val url = (complete.item(0) as Element).getElementsByTagName("url")
                val size = (complete.item(0) as Element).getElementsByTagName("size")

                urlHolder.sysImgWearFilename = url.item(0).textContent
                urlHolder.sysImgWearUrl = SYS_IMAGES_WEAR_URL + urlHolder.sysImgWearFilename
                urlHolder.totalSize += size.item(0).textContent.toInt()

            } else {
                val docSysImg = db.parse(URL(repositoryUrl).openStream())
                remotePackages = expr.evaluate(docSysImg, XPathConstants.NODESET) as NodeList

                val childNodes = remotePackages.item(0).childNodes // Index 1 contains x86_64
                val typeDetails = (childNodes as Element).getElementsByTagName("type-details")

                //NodeList abi = ((Element) typeDetails.item(0)).getElementsByTagName("abi");
                //NodeList api = ((Element) typeDetails.item(0)).getElementsByTagName("api-level");
                //System.out.println(api.item(0).getTextContent());          
                val tag = (typeDetails.item(0) as Element).getElementsByTagName("tag")
                val id = (tag.item(0) as Element).getElementsByTagName("id")

                urlHolder.sysImgTag = id.item(0).textContent

                val archives = (childNodes as Element).getElementsByTagName("archive")
                val archive = archives.item(0).childNodes
                val complete = (archive as Element).getElementsByTagName("complete")
                val url = (complete.item(0) as Element).getElementsByTagName("url")
                val size = (complete.item(0) as Element).getElementsByTagName("size")

                urlHolder.sysImgFilename = url.item(0).textContent

                val imgUrl = if (abi == "arm") SYS_IMAGES_ARM_URL else SYS_IMAGES_PHONE_URL
                urlHolder.sysImgUrl = imgUrl + urlHolder.sysImgFilename
                urlHolder.totalSize += size.item(0).textContent.toInt()
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
        abi = Preferences.get("android.emulator.image.abi")

        if (abi == null || askABI) {
            // Either there was no image architecture selected, or the default was set.
            // In this case, we give the user the option to choose between ARM and x86
            val result: Int
            // PROCESSOR_IDENTIFIER is only defined on Windows. For cross-platform CPU
            // info, in the future we could use OSHI: https://github.com/oshi/oshi
            val procId = System.getenv("PROCESSOR_IDENTIFIER")
            result = if (procId != null) {
                if (-1 < procId.indexOf("Intel")) {
                    // Intel CPU: we go for the x86 abi
                    JOptionPane.YES_OPTION
                } else {
                    // Another CPU, can only be AMD, so we go for ARM abi          
                    JOptionPane.NO_OPTION
                }
            } else if (Platform.isMacOS()) {
                // Macs only have Intel CPUs, so we also go for the x86 abi
                JOptionPane.YES_OPTION
            } else {
                showSysImageMessage()
            }
            if (result == JOptionPane.YES_OPTION || result == JOptionPane.CLOSED_OPTION) {
                abi = "x86"
                installHAXM()
            } else {
                abi = "arm"
            }
            Preferences.set("android.emulator.image.abi", abi)
        }

        downloadTask = DownloadTask()
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

        val pain = Box.createVerticalBox()
        pain.border = EmptyBorder(13, 13, 13, 13)
        outer.add(pain)

        val labelText = if (wear) getTextString("sys_image_downloader.download_watch_label") else getTextString("sys_image_downloader.download_phone_label")
        val textarea = JLabel(labelText)

        textarea.alignmentX = Component.LEFT_ALIGNMENT
        pain.add(textarea)

        progressBar = JProgressBar(0, 100)
        progressBar!!.value = 0
        progressBar!!.isStringPainted = true
        progressBar!!.isIndeterminate = true
        progressBar!!.border = EmptyBorder(10, 10, 10, 10)
        pain.add(progressBar)

        downloadedTextArea = JLabel("")
        downloadedTextArea!!.alignmentX = Component.LEFT_ALIGNMENT
        pain.add(downloadedTextArea)

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
                cancelButton.preferredSize.height)

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
        pain.add(buttons)

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
        private val FONT_SIZE = Toolkit.zoom(11)
        private val TEXT_MARGIN = Toolkit.zoom(8)
        private val TEXT_WIDTH = Toolkit.zoom(300)

        private const val SYS_IMAGES_ARM_URL = "https://dl.google.com/android/repository/sys-img/android/"
        private const val SYS_IMAGES_PHONE_URL = "https://dl.google.com/android/repository/sys-img/google_apis/"
        private const val SYS_IMAGES_PHONE_LIST = "sys-img2-1.xml"
        private const val SYS_IMAGES_WEAR_URL = "https://dl.google.com/android/repository/sys-img/android-wear/"
        private const val SYS_IMAGES_WEAR_LIST = "sys-img2-1.xml"
        private const val EMULATOR_GUIDE_URL = "https://developer.android.com/studio/run/emulator-acceleration.html"
        private const val KVM_LINUX_GUIDE_URL = "https://developer.android.com/studio/run/emulator-acceleration.html#vm-linux"

        // http://stackoverflow.com/questions/3758606/how-to-convert-byte-size-into-human-readable-format-in-java
        fun humanReadableByteCount(bytes: Long, si: Boolean): String {
            val unit = if (si) 1000 else 1024
            if (bytes < unit) return "$bytes B"
            val exp = (Math.log(bytes.toDouble()) / Math.log(unit.toDouble())).toInt()
            val pre = (if (si) "kMGTPE" else "KMGTPE")[exp - 1].toString() + if (si) "" else "i"
            return String.format("%.1f %sB", bytes / Math.pow(unit.toDouble(), exp.toDouble()), pre)
        }

        fun showSysImageMessage(): Int {
            var htmlString = "<html> " +
                    "<head> <style type=\"text/css\">" +
                    "p { font: " + FONT_SIZE + "pt \"Lucida Grande\"; " +
                    "margin: " + TEXT_MARGIN + "px; " +
                    "width: " + TEXT_WIDTH + "px }" +
                    "</style> </head>"

            htmlString += "<body> <p> " + getTextString("sys_image_downloader.dialog.select_image_body", EMULATOR_GUIDE_URL) + " </p> </body> </html>"

            val title = getTextString("sys_image_downloader.dialog.select_image_title")
            val pane = JEditorPane("text/html", htmlString)

            pane.addHyperlinkListener { e ->
                if (e.eventType == HyperlinkEvent.EventType.ACTIVATED) {
                    Platform.openURL(e.url.toString())
                }

            }

            pane.isEditable = false

            val label = JLabel()

            pane.background = label.background

            val options = arrayOf(
                    getTextString("sys_image_downloader.option.x86_image"),
                    getTextString("sys_image_downloader.option.arm_image")
            )

            val result = JOptionPane.showOptionDialog(null, pane, title,
                    JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE,
                    null, options, options[0])

            return if (result == JOptionPane.YES_OPTION) {
                JOptionPane.YES_OPTION
            } else if (result == JOptionPane.NO_OPTION) {
                JOptionPane.NO_OPTION
            } else {
                JOptionPane.CLOSED_OPTION
            }
        }

        fun installHAXM() {
            val haxmFolder = hAXMInstallerFolder

            if (Platform.isLinux()) {
                showMessage(getTextString("sys_image_downloader.dialog.accel_images_title"),
                        getTextString("sys_image_downloader.dialog.kvm_config_body", KVM_LINUX_GUIDE_URL))
            } else if (haxmFolder.exists()) {
                showMessage(getTextString("sys_image_downloader.dialog.accel_images_title"),
                        getTextString("sys_image_downloader.dialog.haxm_install_body")!!)
                val pb: ProcessBuilder

                pb = if (Platform.isWindows()) {
                    val exec = File(haxmFolder, "silent_install.bat")
                    ProcessBuilder(exec.absolutePath)
                } else {
                    val exec = File(haxmFolder, "HAXM installation")
                    ProcessBuilder(exec.absolutePath)
                }

                pb.directory(haxmFolder)
                pb.redirectErrorStream(true)

                var process: Process? = null

                try {
                    process = pb.start()
                } catch (e: IOException) {
                    e.printStackTrace()
                }

                if (process != null) {
                    try {
                        val output = StreamPump(process.inputStream, "HAXM: ")
                        output.addTarget { line -> println("HAXM: $line") }.start()
                        process.waitFor()
                    } catch (ie: InterruptedException) {
                        ie.printStackTrace()
                    } finally {
                        process.destroy()
                    }
                }
            }
        }
    }

    // constructor or initializer block
    // will be executed before any other operation in this class
    init {
        createLayout()
    }
}