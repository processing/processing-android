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

import processing.app.Base
import processing.app.Messages
import processing.app.Util
import processing.app.contrib.ContributionType
import processing.app.contrib.IgnorableException
import processing.app.contrib.LocalContribution
import processing.app.tools.Tool

import java.io.File
import java.net.URL
import java.net.URLClassLoader

/**
 * Specialized local contribution for Android tools. Cannot use ToolContribution
 * from processing-app since Android tools may need SDK jars in the classpath.
 */
internal class AndroidTool internal constructor(toolFolder: File?, private val sdk: AndroidSDK) : LocalContribution(toolFolder), Tool, Comparable<AndroidTool> {
    private var tool: Tool? = null

    override fun init(base: Base) {
        tool!!.init(base)
    }

    override fun run() {
        tool!!.run()
    }

    override fun getMenuTitle(): String {
        return tool!!.menuTitle
    }

    @Throws(Exception::class)
    override fun initLoader(className: String?): String {
        var className = className
        val toolDir = File(folder, "tool")

        if (toolDir.exists()) {
            Messages.log("checking mode folder regarding $className")
            // If no class name specified, search the main <modename>.jar for the
            // full name package and mode name.
            if (className == null) {
                val shortName = folder.name
                val mainJar = File(toolDir, "$shortName.jar")

                className = if (mainJar.exists()) {
                    findClassInZipFile(shortName, mainJar)
                } else {
                    throw IgnorableException(mainJar.absolutePath + " does not exist.")
                }
                if (className == null) {
                    throw IgnorableException("Could not find " + shortName +
                            " class inside " + mainJar.absolutePath)
                }
            }

            // Add .jar and .zip files from the "tool" and the SDK/tools/lib 
            // folder into the classpath
            val libDir = File(sdk.toolsFolder, "lib")
            val toolArchives = Util.listJarFiles(toolDir)
            val libArchives = Util.listJarFiles(libDir)

            if (toolArchives != null && toolArchives.isNotEmpty() && libArchives != null && libArchives.size > 0) {
                val urlList = arrayOfNulls<URL>(toolArchives.size + libArchives.size)
                var j: Int
                j = 0
                while (j < toolArchives.size) {
                    Messages.log("Found archive " + toolArchives[j] + " for " + getName())

                    urlList[j] = toolArchives[j].toURI().toURL()
                    j++
                }
                var k = 0

                while (k < libArchives.size) {
                    Messages.log("Found archive " + libArchives[k] + " for " + getName())

                    urlList[j] = libArchives[k].toURI().toURL()
                    k++
                    j++
                }

                loader = URLClassLoader(urlList)
                Messages.log("loading above JARs with loader $loader")
            }
        }

        // If no archives were found, just use the regular ClassLoader
        if (loader == null) {
            loader = Thread.currentThread().contextClassLoader
        }
        return className!!
    }

    override fun compareTo(o: AndroidTool): Int {
        return menuTitle.compareTo(o.menuTitle)
    }

    override fun getType(): ContributionType {
        return ContributionType.TOOL
    }

    init {
        val className = initLoader(null)

        if (className != null) {
            val toolClass = loader.loadClass(className)
            tool = toolClass.newInstance() as Tool
        }
    }
}