uniform mat4 transform;

attribute vec3 position;
attribute vec4 color;

varying vec4 vertColor;

void main() {
  gl_Position = transform * vec4(position, 1);

  //we avoid affecting the Z component by the transform
  //because it would mess up our depth testing
  gl_Position.z = position.z;

  vertColor = color.zyxw;
}
