#version 330
#extension GL_ARB_separate_shader_objects : require

// Frame blending: mixes the current frame with the accumulated previous frames.

uniform sampler2D InSampler;
uniform sampler2D PrevSampler;

layout(location = 0) in vec2 texCoord;

layout(std140) uniform MotionBlurConfig {
    float BlendFactor;
};

layout(location = 0) out vec4 fragColor;

void main() {
    vec3 current = texture(InSampler, texCoord).rgb;
    vec3 previous = texture(PrevSampler, texCoord).rgb;
    fragColor = vec4(mix(current, previous, BlendFactor), 1.0);
}