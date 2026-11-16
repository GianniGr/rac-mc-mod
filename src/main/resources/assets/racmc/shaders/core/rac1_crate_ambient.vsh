#version 150

#moj_import <light.glsl>
#moj_import <fog.glsl>

in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV1;
in ivec2 UV2;
in vec3 Normal;

uniform sampler2D Sampler1;
uniform sampler2D Sampler2;
uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform int FogShape;
uniform vec3 Light0_Direction;
uniform vec3 Light1_Direction;

out float vertexDistance;
out vec4 vertexColor;
out vec4 lightMapColor;
out vec4 overlayColor;
out vec2 texCoord0;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    vertexDistance = fog_distance(Position, FogShape);

    // Original fuse changes ambient, not the texture or directional light.
    // 255-normalization and MC directional/world lighting are an explicit adaptation,
    // not a claim to reproduce the unported PS2 light-word/GS blending pipeline.
    vec3 ambient = vec3(200.0, 64.0, 64.0) / 255.0;
    float diffuse = (max(0.0, dot(Light0_Direction, Normal))
                   + max(0.0, dot(Light1_Direction, Normal))) * MINECRAFT_LIGHT_POWER;
    // Stock MC already clips ambient + diffuse at 1. Keep only its visible diffuse
    // range before replacing ambient; otherwise up-facing normals can clip EVERY
    // channel to white and erase the global flash. Ordinary side lighting is unchanged.
    diffuse = min(diffuse, 1.0 - MINECRAFT_AMBIENT_LIGHT);
    vertexColor = vec4(Color.rgb * min(vec3(1.0), ambient + vec3(diffuse)), Color.a);

    // Retain ordinary MC lightmap, overlay, texture and fog via the vanilla fragment shader.
    lightMapColor = texelFetch(Sampler2, UV2 / 16, 0);
    overlayColor = texelFetch(Sampler1, UV1, 0);
    texCoord0 = UV0;
}
