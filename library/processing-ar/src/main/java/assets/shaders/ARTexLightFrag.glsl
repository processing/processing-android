/*
  Part of the Processing project - http://processing.org

  Copyright (c) 2012-16 The Processing Foundation
  Copyright (c) 2004-12 Ben Fry and Casey Reas
  Copyright (c) 2001-04 Massachusetts Institute of Technology

  This library is free software; you can redistribute it and/or
  modify it under the terms of the GNU Lesser General Public
  License as published by the Free Software Foundation, version 2.1.

  This library is distributed in the hope that it will be useful,
  but WITHOUT ANY WARRANTY; without even the implied warranty of
  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
  Lesser General Public License for more details.

  You should have received a copy of the GNU Lesser General
  Public License along with this library; if not, write to the
  Free Software Foundation, Inc., 59 Temple Place, Suite 330,
  Boston, MA  02111-1307  USA
*/

#ifdef GL_ES
precision mediump float;
precision mediump int;
#endif

uniform sampler2D texture;
uniform vec2 texOffset;
uniform vec4 colorCorrection;

varying vec4 vertColor;
varying vec4 backVertColor;
varying vec4 vertTexCoord;

// Approximate sRGB gamma parameters
const float kGamma = 0.4545454;
const float kInverseGamma = 2.2;
const float kMiddleGrayGamma = 0.466;

void main() {
  vec3 colorShift = colorCorrection.rgb;
  float averagePixelIntensity = colorCorrection.a;

  vec4 tex = texture2D(texture, vertTexCoord.st);
  vec4 gtex = vec4(pow(tex.rgb, vec3(kInverseGamma)), tex.a);

  vec4 color = gtex * (gl_FrontFacing ? vertColor : backVertColor);

  // Apply SRGB gamma before writing the fragment color.
  color.rgb = pow(color.rgb, vec3(kGamma));

  // Apply average pixel intensity and color shift
  color.rgb *= colorShift * (averagePixelIntensity / kMiddleGrayGamma);
  gl_FragColor = color;
}