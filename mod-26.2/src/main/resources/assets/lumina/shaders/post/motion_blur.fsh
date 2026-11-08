#version 330

// Frame blending: mixes the current frame with the accumulated previous frames.

uniform sampler2D InSampler;
uniform sampler2D PrevSampler;

in vec2 texCoord;

layout(std140) uniform MotionBlurConfig {
    float BlendFactor;
};

out vec4 fragColor;

void main() {
    vec3 current = texture(InSampler, texCoord).rgb;
    vec3 previous = texture(PrevSampler, texCoord).rgb;
    fragColor = vec4(mix(current, previous, BlendFactor), 1.0);
}