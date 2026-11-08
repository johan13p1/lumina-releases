#version 330
#extension GL_ARB_separate_shader_objects : require

// Textured quad clipped to a rounded square / circle.
// vertexColor.rg = mask coordinate in [0, 1] across the quad, vertexColor.b = corner radius
// relative to the half size (1 = circle), vertexColor.a = opacity.

layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    mat4 TextureMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
};

uniform sampler2D Sampler0;

layout(location = 0) in vec2 texCoord0;
layout(location = 1) in vec4 vertexColor;

layout(location = 0) out vec4 fragColor;

void main() {
    vec2 p = vertexColor.rg * 2.0 - 1.0;
    float r = max(vertexColor.b, 0.001);
    vec2 q = abs(p) - vec2(1.0 - r);
    float dist = length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r;
    float aa = max(fwidth(dist), 1e-4);
    float mask = clamp(-dist / aa + 0.5, 0.0, 1.0);

    vec4 color = texture(Sampler0, texCoord0);
    color.a *= mask * vertexColor.a;
    if (color.a <= 0.0) {
        discard;
    }
    fragColor = color * ColorModulator;
}
