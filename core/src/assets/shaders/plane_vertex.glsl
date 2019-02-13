uniform mat4 u_Model;
uniform mat4 u_ModelViewProjection;
uniform mat2 u_PlaneUvMatrix;

attribute vec3 a_XZPositionAlpha;

varying vec3 v_TexCoordAlpha;

void main() {
   vec4 position = vec4(a_XZPositionAlpha.x, 0.0, a_XZPositionAlpha.y, 1.0);
   v_TexCoordAlpha = vec3(u_PlaneUvMatrix * (u_Model * position).xz, a_XZPositionAlpha.z);
   gl_Position = u_ModelViewProjection * position;
}
