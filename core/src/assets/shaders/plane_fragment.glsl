precision highp float;
uniform sampler2D u_Texture;
uniform vec4 u_dotColor;
uniform vec4 u_lineColor;
uniform vec4 u_gridControl;
varying vec3 v_TexCoordAlpha;

void main() {
  vec4 control = texture2D(u_Texture, v_TexCoordAlpha.xy);
  float dotScale = v_TexCoordAlpha.z;
  float lineFade = max(0.0, u_gridControl.z * v_TexCoordAlpha.z - (u_gridControl.z - 1.0));
  vec3 color = (control.r * dotScale > u_gridControl.x) ? u_dotColor.rgb
             : (control.g > u_gridControl.y)            ? u_lineColor.rgb * lineFade
                                                        : (u_lineColor.rgb * 0.25 * lineFade) ;
  gl_FragColor = vec4(color, v_TexCoordAlpha.z * u_gridControl.w);
}
