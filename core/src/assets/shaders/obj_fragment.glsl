precision mediump float;

uniform sampler2D u_Texture;

uniform vec4 u_LightingParameters;
uniform vec4 u_MaterialParameters;

varying vec3 v_ViewPosition;
varying vec3 v_ViewNormal;
varying vec2 v_TexCoord;

void main() {
    const float kGamma = 0.4545454;
    const float kInverseGamma = 2.2;
    vec3 viewLightDirection = u_LightingParameters.xyz;
    float lightIntensity = u_LightingParameters.w;
    float materialAmbient = u_MaterialParameters.x;
    float materialDiffuse = u_MaterialParameters.y;
    float materialSpecular = u_MaterialParameters.z;
    float materialSpecularPower = u_MaterialParameters.w;
    vec3 viewFragmentDirection = normalize(v_ViewPosition);
    vec3 viewNormal = normalize(v_ViewNormal);
    vec4 objectColor = texture2D(u_Texture, vec2(v_TexCoord.x, 1.0 - v_TexCoord.y));
    objectColor.rgb = pow(objectColor.rgb, vec3(kInverseGamma));
    float ambient = materialAmbient;
    float diffuse = lightIntensity * materialDiffuse *
            0.5 * (dot(viewNormal, viewLightDirection) + 1.0);
    vec3 reflectedLightDirection = reflect(viewLightDirection, viewNormal);
    float specularStrength = max(0.0, dot(viewFragmentDirection, reflectedLightDirection));
    float specular = lightIntensity * materialSpecular *
            pow(specularStrength, materialSpecularPower);
    gl_FragColor.a = objectColor.a;
    gl_FragColor.rgb = pow(objectColor.rgb * (ambient + diffuse) + specular, vec3(kGamma));
}
