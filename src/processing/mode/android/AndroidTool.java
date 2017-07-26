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

package processing.mode.android;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;

import processing.app.Base;
import processing.app.Messages;
import processing.app.Util;
import processing.app.contrib.ContributionType;
import processing.app.contrib.IgnorableException;
import processing.app.contrib.LocalContribution;
import processing.app.contrib.ToolContribution;
import processing.app.tools.Tool;

public class AndroidTool extends LocalContribution implements Tool, Comparable<ToolContribution> {
  private AndroidSDK sdk;
  private Tool tool;
  
  AndroidTool(File toolFolder, AndroidSDK sdk) throws Throwable {
    super(toolFolder);
    this.sdk = sdk;
    
    String className = initLoader(null);
    if (className != null) {
      Class<?> toolClass = loader.loadClass(className);
      tool = (Tool) toolClass.newInstance();
    }
  }  
  
  public void init(Base base) {
    tool.init(base);
  }


  public void run() {
    tool.run();
  }


  public String getMenuTitle() {
    return tool.getMenuTitle();
  }
  
  public String initLoader(String className) throws Exception {
    File toolDir = new File(folder, "tool");
    if (toolDir.exists()) {
      Messages.log("checking mode folder regarding " + className);
      // If no class name specified, search the main <modename>.jar for the
      // full name package and mode name.
      if (className == null) {
        String shortName = folder.getName();
        File mainJar = new File(toolDir, shortName + ".jar");
        if (mainJar.exists()) {
          className = findClassInZipFile(shortName, mainJar);
        } else {
          throw new IgnorableException(mainJar.getAbsolutePath() + " does not exist.");
        }

        if (className == null) {
          throw new IgnorableException("Could not find " + shortName +
                                       " class inside " + mainJar.getAbsolutePath());
        }
      }
      
      // Add .jar and .zip files from the "tool" and the SDK/tools/lib 
      // folder into the classpath
      File libDir = new File(sdk.getToolsFolder(), "lib");
      File[] toolArchives = Util.listJarFiles(toolDir);      
      File[] libArchives = Util.listJarFiles(libDir);
      
      if (toolArchives != null && toolArchives.length > 0 &&
          libArchives != null && libArchives.length > 0) {
        URL[] urlList = new URL[toolArchives.length + libArchives.length];
        
        int j;
        for (j = 0; j < toolArchives.length; j++) {
          Messages.log("Found archive " + toolArchives[j] + " for " + getName());
          urlList[j] = toolArchives[j].toURI().toURL();
        }
        for (int k = 0; k < libArchives.length; k++, j++) {
          Messages.log("Found archive " + libArchives[k] + " for " + getName());
          urlList[j] = libArchives[k].toURI().toURL();
        }
        
        loader = new URLClassLoader(urlList);
        Messages.log("loading above JARs with loader " + loader);
      }
    }

    // If no archives were found, just use the regular ClassLoader
    if (loader == null) {
      loader = Thread.currentThread().getContextClassLoader();
    }
    return className;
  }

  
  @Override
  public int compareTo(ToolContribution o) {
    return getMenuTitle().compareTo(o.getMenuTitle());
  }

  
  @Override
  public ContributionType getType() {
    return ContributionType.TOOL;
  }  
}
