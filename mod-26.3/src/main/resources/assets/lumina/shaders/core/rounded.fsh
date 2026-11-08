#version 330
#extension GL_ARB_separate_shader_objects : require

// Anti-aliased circle segments for Lumina's UI.
//
// The texture coordinates carry two things:
//  * the local coordinate inside the shape, where length(uv) == 1 is the outer edge
//  * a mode code K packed into the integer part of u: u = local.x + 4 * K
//      K == 0            solid shape
//      K in [1, 1000]    ring, inner radius = K / 1000
//      K in [1001, 2000] soft shape (shadows), fully opaque up to (K - 1001) / 999

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

float coverage(float d, float edge, float aa) {
    return clamp((edge - d) / aa + 0.5, 0.0, 1.0);
}

void main() {
    float k = floor((texCoord0.x + 2.0) / 4.0);
    vec2 uv = vec2(texCoord0.x - 4.0 * k, texCoord0.y);
    float d = length(uv);
    float aa = max(fwidth(d), 1e-4);

    float alpha;
    if (k < 0.5) {
        alpha = coverage(d, 1.0, aa);
    } else if (k < 1000.5) {
        float inner = k / 1000.0;
        alpha = coverage(d, 1.0, aa) * (1.0 - coverage(d, inner, aa));
    } else {
        float inner = (k - 1001.0) / 999.0;
        alpha = 1.0 - smoothstep(inner, 1.0, d);
    }

    vec4 color = vertexColor * texture(Sampler0, vec2(0.5));
    color.a *= alpha;
    if (color.a <= 0.0) {
        discard;
    }
    fragColor = color * ColorModulator;
}
