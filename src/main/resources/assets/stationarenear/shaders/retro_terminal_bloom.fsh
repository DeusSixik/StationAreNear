#version 150

uniform sampler2D SourceTexture;
uniform vec2 SourceSize;
uniform float bloomStrength;
uniform float bloomRadius;
uniform float threshold;
uniform vec3 bloomTint;

in vec2 uv;
out vec4 FragColor;

vec4 sampleSafe(vec2 coords) {
    if (coords.x < 0.0 || coords.x > 1.0 || coords.y < 0.0 || coords.y > 1.0) {
        return vec4(0.0);
    }
    return texture(SourceTexture, coords);
}

float luminance(vec3 color) {
    return dot(color, vec3(0.2126, 0.7152, 0.0722));
}

vec3 brightPart(vec3 color) {
    float mask = smoothstep(threshold, threshold + 0.20, luminance(color));
    return color * mask;
}

void main() {
    vec2 coords = uv * 0.5 + 0.5;
    vec4 base = sampleSafe(coords);
    vec2 texel = 1.0 / max(SourceSize, vec2(1.0));
    float radius = max(0.0, bloomRadius);

    vec3 glow = vec3(0.0);
    glow += brightPart(sampleSafe(coords).rgb) * 0.26;
    glow += brightPart(sampleSafe(coords + texel * vec2( radius,  0.0)).rgb) * 0.10;
    glow += brightPart(sampleSafe(coords + texel * vec2(-radius,  0.0)).rgb) * 0.10;
    glow += brightPart(sampleSafe(coords + texel * vec2( 0.0,  radius)).rgb) * 0.08;
    glow += brightPart(sampleSafe(coords + texel * vec2( 0.0, -radius)).rgb) * 0.08;
    glow += brightPart(sampleSafe(coords + texel * vec2( radius * 2.0,  0.0)).rgb) * 0.08;
    glow += brightPart(sampleSafe(coords + texel * vec2(-radius * 2.0,  0.0)).rgb) * 0.08;
    glow += brightPart(sampleSafe(coords + texel * vec2( radius,  radius)).rgb) * 0.055;
    glow += brightPart(sampleSafe(coords + texel * vec2(-radius,  radius)).rgb) * 0.055;
    glow += brightPart(sampleSafe(coords + texel * vec2( radius, -radius)).rgb) * 0.055;
    glow += brightPart(sampleSafe(coords + texel * vec2(-radius, -radius)).rgb) * 0.055;
    glow += brightPart(sampleSafe(coords + texel * vec2( radius * 3.5,  0.0)).rgb) * 0.035;
    glow += brightPart(sampleSafe(coords + texel * vec2(-radius * 3.5,  0.0)).rgb) * 0.035;

    vec3 color = base.rgb + glow * bloomTint * bloomStrength;
    FragColor = vec4(color, base.a);
}
