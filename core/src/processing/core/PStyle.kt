/* -*- mode: java; c-basic-offset: 2; indent-tabs-mode: nil -*- */

/*
  Part of the Processing project - http://processing.org

  Copyright (c) 2008-10 Ben Fry and Casey Reas

  This library is free software; you can redistribute it and/or
  modify it under the terms of the GNU Lesser General Public
  License version 2.1 as published by the Free Software Foundation.

  This library is distributed in the hope that it will be useful,
  but WITHOUT ANY WARRANTY; without even the implied warranty of
  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
  Lesser General Public License for more details.

  You should have received a copy of the GNU Lesser General
  Public License along with this library; if not, write to the
  Free Software Foundation, Inc., 59 Temple Place, Suite 330,
  Boston, MA  02111-1307  USA
*/

package processing.core

open class PStyle : PConstants {
    @JvmField
    var imageMode = 0

    @JvmField
    var rectMode = 0

    @JvmField
    var ellipseMode = 0

    @JvmField
    var shapeMode = 0

    @JvmField
    var blendMode = 0

    @JvmField
    var colorMode = 0

    @JvmField
    var colorModeX = 0f

    @JvmField
    var colorModeY = 0f

    @JvmField
    var colorModeZ = 0f

    @JvmField
    var colorModeA = 0f

    @JvmField
    var tint = false

    @JvmField
    var tintColor = 0

    @JvmField
    var fill = false

    @JvmField
    var fillColor = 0

    @JvmField
    var stroke = false

    @JvmField
    var strokeColor = 0

    @JvmField
    var strokeWeight = 0f

    @JvmField
    var strokeCap = 0

    @JvmField
    var strokeJoin = 0

    // TODO these fellas are inconsistent, and may need to go elsewhere
    @JvmField
    var ambientR = 0f

    @JvmField
    var ambientG = 0f

    @JvmField
    var ambientB = 0f

    @JvmField
    var specularR = 0f

    @JvmField
    var specularG = 0f

    @JvmField
    var specularB = 0f

    @JvmField
    var emissiveR = 0f

    @JvmField
    var emissiveG = 0f

    @JvmField
    var emissiveB = 0f

    @JvmField
    var shininess = 0f

    @JvmField
    var textFont: PFont? = null

    @JvmField
    var textAlign = 0

    @JvmField
    var textAlignY = 0

    @JvmField
    var textMode = 0

    @JvmField
    var textSize = 0f

    @JvmField
    var textLeading = 0f
}