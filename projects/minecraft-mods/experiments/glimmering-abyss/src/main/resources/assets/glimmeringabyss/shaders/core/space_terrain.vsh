#version 150
in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV2;
in vec3 Normal;
uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform vec3 ChunkOffset;
uniform vec3 EnvCamera;
out vec4 materialColor;
out vec2 texCoord;
out vec3 scenePosition;
out vec3 faceNormal;
out vec2 lightCoord;
out float viewDistance;
void main() {
    vec3 relativePosition=Position+ChunkOffset;
    vec4 viewPosition=ModelViewMat*vec4(relativePosition,1.0);
    gl_Position=ProjMat*viewPosition;
    materialColor=Color;
    texCoord=UV0;
    scenePosition=relativePosition+EnvCamera;
    faceNormal=Normal;
    lightCoord=vec2(UV2)/16.0;
    viewDistance=length(viewPosition.xyz);
}
