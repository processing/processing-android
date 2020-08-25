/* -*- mode: java; c-basic-offset: 2; indent-tabs-mode: nil -*- */

/*
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
package processing.mode.android

import processing.app.*
import processing.app.exec.ProcessHelper
import processing.app.exec.ProcessResult
import processing.app.ui.Toolkit

import processing.core.PApplet

import processing.mode.android.AndroidMode.Companion.getDateStamp

import java.io.*
import java.util.*
import java.util.zip.ZipFile

import javax.swing.JEditorPane
import javax.swing.JLabel
import javax.swing.JOptionPane
import javax.swing.event.HyperlinkEvent

/**
 * @author Aditya Rana
 * Some utilities.
 */
internal object AndroidUtil {
    private val FONT_SIZE = Toolkit.zoom(11)
    private val TEXT_MARGIN = Toolkit.zoom(8)
    private val TEXT_WIDTH = Toolkit.zoom(300)

    // Creates a message dialog, where the text can contain clickable links.
    @JvmStatic
    fun showMessage(title: String?, text: String) {
        var title = title
        if (title == null) title = "Message"

        if (Base.isCommandLine()) {
            println("$title: $text")
        } else {

            val htmlString = "<html> " +
                    "<head> <style type=\"text/css\">" +
                    "p { font: " + FONT_SIZE + "pt \"Lucida Grande\"; " +
                    "margin: " + TEXT_MARGIN + "px; " +
                    "width: " + TEXT_WIDTH + "px }" +
                    "</style> </head>" +
                    "<body> <p>" + text + "</p> </body> </html>"

            val pane = JEditorPane("text/html", htmlString)

            pane.addHyperlinkListener { e ->
                if (e.eventType == HyperlinkEvent.EventType.ACTIVATED) {
                    Platform.openURL(e.url.toString())
                }
            }

            pane.isEditable = false
            val label = JLabel()

            pane.background = label.background

            JOptionPane.showMessageDialog(null, pane, title,
                    JOptionPane.INFORMATION_MESSAGE)
        }
    }

    @JvmStatic
    fun writeFile(file: File?, lines: Array<String?>) {
        val writer = PApplet.createWriter(file)
        for (line in lines) writer.println(line)
        writer.flush()
        writer.close()
    }

    @JvmStatic
    @Throws(SketchException::class)
    fun createPath(parent: File?, name: String?): File {
        val result = File(parent, name)
        if (!(result.exists() || result.mkdirs())) {
            throw SketchException("Could not create $result")
        }
        return result
    }

    @JvmStatic
    @JvmOverloads
    fun createFileFromTemplate(tmplFile: File?, destFile: File?,
                               replaceMap: HashMap<String?, String?>? = null) {
        val pw = PApplet.createWriter(destFile)
        val lines = PApplet.loadStrings(tmplFile)
        for (i in lines.indices) {
            if (lines[i].indexOf("@@") != -1 && replaceMap != null) {
                val sb = StringBuilder(lines[i])
                var index = 0
                for (key in replaceMap.keys) {
                    val `val` = replaceMap[key]
                    while (sb.indexOf(key).also { index = it } != -1) {
                        sb.replace(index, index + key!!.length, `val`)
                    }
                }
                lines[i] = sb.toString()
            }
            // explicit newlines to avoid Windows CRLF
            pw.print("""
    ${lines[i]}
    
    """.trimIndent())
        }
        pw.flush()
        pw.close()
    }

    @JvmStatic
    @Throws(IOException::class)
    fun createSubFolder(parent: File?, name: String): File? {
        val newFolder = File(parent, name)
        if (newFolder.exists()) {
            val stamp = getDateStamp(newFolder.lastModified())
            val dest = File(parent, "$name.$stamp")
            val result = newFolder.renameTo(dest)
            if (!result) {
                val mv: ProcessHelper
                val pr: ProcessResult

                try {
                    System.err.println("Cannot rename existing $name folder, resorting to mv/move instead.")
                    mv = ProcessHelper("mv", newFolder.absolutePath, dest.absolutePath)
                    pr = mv.execute()
                } catch (e: InterruptedException) {
                    e.printStackTrace()
                    return null
                }

                if (!pr.succeeded()) {
                    System.err.println(pr.stderr)
                    Messages.showWarning("Failed to rename", """Could not rename the old "$name" folder. Please delete, close,
|                                        or rename the folder ${newFolder.absolutePath}and try again.""".trimMargin(), null)
                    Platform.openFolder(newFolder)
                    return null
                }
            }
        } else {
            val result = newFolder.mkdirs()
            if (!result) {
                Messages.showWarning("Folders, folders, folders",
                        """
                            Could not create the necessary folders to build.
                            Perhaps you have some file permissions to sort out?
                            """.trimIndent(), null)
                return null
            }
        }
        return newFolder
    }

    @JvmStatic
    @JvmOverloads
    @Throws(IOException::class)
    fun extractFolder(file: File?, newPath: File?, setExec: Boolean,
                      remRoot: Boolean = false) {
        val BUFFER = 2048
        val zip = ZipFile(file)
        val zipFileEntries = zip.entries()

        // Process each entry
        while (zipFileEntries.hasMoreElements()) {
            // grab a zip file entry
            val entry = zipFileEntries.nextElement()
            var currentEntry = entry.name

            if (remRoot) {
                // Remove root folder from path
                var idx = currentEntry.indexOf("/")
                if (idx == -1) {
                    // Let's try the system file separator
                    // https://stackoverflow.com/a/16485210
                    idx = currentEntry.indexOf(File.separator)
                }
                currentEntry = currentEntry.substring(idx + 1)
            }

            val destFile = File(newPath, currentEntry)
            //destFile = new File(newPath, destFile.getName());
            val destinationParent = destFile.parentFile

            // create the parent directory structure if needed
            destinationParent.mkdirs()

            val ext = PApplet.getExtension(currentEntry)

            if (setExec && ext == "unknown") {
                // On some OS X machines the android binaries lose their executable
                // attribute, rendering the mode unusable
                destFile.setExecutable(true)
            }

            if (!entry.isDirectory) {
                // should preserve permissions
                // https://bitbucket.org/atlassian/amps/pull-requests/21/amps-904-preserve-executable-file-status/diff
                val `is` = BufferedInputStream(zip.getInputStream(entry))
                var currentByte: Int

                // establish buffer for writing file
                val data = ByteArray(BUFFER)

                // write the current file to disk
                val fos = FileOutputStream(destFile)
                val dest = BufferedOutputStream(fos, BUFFER)

                // read and write until last byte is encountered
                while (`is`.read(data, 0, BUFFER).also { currentByte = it } != -1) {
                    dest.write(data, 0, currentByte)
                }

                dest.flush()
                dest.close()

                `is`.close()
            }
        }
        zip.close()
    }

    @JvmStatic
    @JvmOverloads
    @Throws(IOException::class)
    fun extractClassesJarFromAar(wearFile: File?, explodeDir: File?, jarFile: File?, removeDir: Boolean = true) {
        extractFolder(wearFile, explodeDir, false)

        val classFile = File(explodeDir, "classes.jar")

        Util.copyFile(classFile, jarFile)

        Util.removeDir(explodeDir)
    }

    @JvmStatic
    fun getFileList(folder: File?, names: Array<String?>): Array<File?> {
        return getFileList(folder, names, null)
    }

    @JvmStatic
    fun getFileList(folder: File?, names: Array<String?>, altNames: Array<String?>?): Array<File?> {
        val icons = arrayOfNulls<File>(names.size)
        for (i in names.indices) {
            var f = File(folder, names[i])
            if (!f.exists() && altNames != null) {
                f = File(folder, altNames[i])
            }
            icons[i] = f
        }
        return icons
    }

    @JvmStatic
    fun getFileList(mode: Mode, prefix: String?, names: Array<String?>?): Array<File?> {
        val icons = arrayOfNulls<File>(names!!.size)

        for (i in names!!.indices) {
            icons[i] = mode.getContentFile(prefix + names[i])
        }

        return icons
    }

    @JvmStatic
    fun allFilesExists(files: Array<File?>): Boolean {
        for (f in files) {
            if (!f!!.exists()) return false
        }
        return true
    }

    @JvmStatic
    fun noFileExists(files: Array<File?>): Boolean {
        for (f in files) {
            if (f!!.exists()) return false
        }
        return true
    }
}