#version 150

uniform sampler2D SourceTexture;

uniform vec2 SourceSize;
uniform float Time;

uniform vec3 fontColor;
uniform vec3 backgroundColor;
uniform float chromaColor;
uniform float staticNoise;
uniform float horizontalSyncStrength;
uniform float horizontalSyncFrequency;
uniform vec2 jitter;
uniform float glowingLine;
uniform float flickering;
uniform float ambientLight;
uniform float pixelHeight;
uniform int pixelization;
uniform float rbgSplit;
uniform float scanlineStrength;
uniform float phosphorGlow;
uniform float glitchStrength;
uniform float glitchFrequency;
uniform float glitchBandHeight;
uniform float rollingInterference;
uniform float noiseFrameRate;
uniform float glitchFrameRate;
uniform float UiScale;

in vec2 uv;
out vec4 FragColor;

float sum2(vec2 v) {
    return v.x + v.y;
}

float rgb2grey(vec3 v) {
    float dp = dot(v, vec3(0.21, 0.72, 0.04));
    return dp == 0.0 ? 0.00001 : dp;
}

float hash11(float n) {
    return fract(sin(n * 17.231) * 43758.5453123);
}

float hash21(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453123);
}

float quantizedTime(float frameRate) {
    float safeFrameRate = max(1.0, frameRate);
    return floor(Time * safeFrameRate) / safeFrameRate;
}

float valueNoise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    vec2 u = f * f * (3.0 - 2.0 * f);
    float a = hash21(i);
    float b = hash21(i + vec2(1.0, 0.0));
    float c = hash21(i + vec2(0.0, 1.0));
    float d = hash21(i + vec2(1.0, 1.0));
    return mix(mix(a, b, u.x), mix(c, d, u.x), u.y);
}

vec4 noiseTexel(vec2 coords) {
    float noiseTime = quantizedTime(noiseFrameRate);
    vec2 p = coords * vec2(512.0, 512.0);
    float coarse = valueNoise(p * 0.33 + noiseTime * vec2(17.0, 11.0));
    float fine = hash21(floor(p + noiseTime * vec2(197.0, 131.0)));
    float alt = valueNoise(p * 0.71 + vec2(noiseTime * 23.0, -noiseTime * 19.0));
    float grain = mix(coarse, fine, 0.72);
    return vec4(grain, alt, fine, coarse);
}

vec4 sampleSafe(sampler2D source, vec2 coords) {
    if (coords.x < 0.0 || coords.x > 1.0 || coords.y < 0.0 || coords.y > 1.0) {
        return vec4(0.0);
    }
    return texture(source, coords);
}

float randomPass(vec2 coords) {
    float noiseTime = quantizedTime(noiseFrameRate);
    return fract(smoothstep(0.0, -120.0, coords.y - (SourceSize.y + 120.0) * fract(-noiseTime * 0.15)));
}

float pulseWindow(float localTime) {
    return smoothstep(0.0, 0.08, localTime) * (1.0 - smoothstep(0.10, 0.42, localTime));
}

float glitchPulse(float frequency) {
    float glitchTime = quantizedTime(glitchFrameRate);
    float safeFrequency = max(0.001, frequency);
    float slot = floor(glitchTime * safeFrequency);
    float localTime = fract(glitchTime * safeFrequency);
    float chance = step(0.72, hash11(slot + 3.0));
    return chance * pulseWindow(localTime);
}

float glitchBand(vec2 coords, float slot, float index, float bandHeight) {
    float center = hash11(slot * 7.0 + index * 19.0);
    float height = max(0.001, bandHeight) * mix(0.55, 1.55, hash11(slot + index * 11.0));
    return 1.0 - smoothstep(0.0, height, abs(coords.y - center));
}

float getScanlineIntensity(vec2 coords) {
    float result = 1.0;
    float scale = UiScale <= 0.0 ? 1.0 : max(0.01, UiScale);
    float scaledPixelHeight = pixelHeight / sqrt(scale);
    float strength = clamp(scanlineStrength, 0.0, 1.0);
    float val = 0.0;
    vec2 rasterizationCoords = fract(coords * SourceSize * 0.0365 * scaledPixelHeight);
    val += smoothstep(0.0, 0.5, rasterizationCoords.y);
    val -= smoothstep(0.5, 1.0, rasterizationCoords.y);
    result *= mix(1.0 - 0.55 * strength, 1.0, val);

    if (pixelization != 0) {
        val = 0.0;
        val += smoothstep(0.0, 0.5, rasterizationCoords.x);
        val -= smoothstep(0.5, 1.0, rasterizationCoords.x);
        result *= mix(1.0 - 0.35 * strength, 1.0, val);
    }

    return result;
}

void main() {
    vec2 coords = uv * 0.5 + 0.5;
    vec2 originalCoords = coords;
    float noiseTime = quantizedTime(noiseFrameRate);
    float glitchTime = quantizedTime(glitchFrameRate);
    vec2 initialCoords = vec2(fract(noiseTime / 2.0), fract(noiseTime / 3.14159265));
    vec4 initialNoiseTexel = noiseTexel(initialCoords);
    float randval = initialNoiseTexel.r;
    float distortionScale = step(1.0 - horizontalSyncFrequency, randval) * randval * horizontalSyncStrength * 0.1;
    float distortionFreq = mix(4.0, 40.0, initialNoiseTexel.g);

    float syncWave = sin((coords.y + Time) * distortionFreq);
    coords.x += syncWave * distortionScale;

    float safeGlitchFrequency = max(0.001, glitchFrequency);
    float glitchSlot = floor(glitchTime * safeGlitchFrequency);
    float pulse = glitchPulse(safeGlitchFrequency);
    float bandMask = max(
            glitchBand(coords, glitchSlot, 0.0, glitchBandHeight),
            max(glitchBand(coords, glitchSlot, 1.0, glitchBandHeight * 0.65),
                glitchBand(coords, glitchSlot, 2.0, glitchBandHeight * 0.42)));
    float bandDirection = hash11(glitchSlot + 41.0) * 2.0 - 1.0;
    coords.x += bandDirection * glitchStrength * pulse * bandMask;

    vec2 noiseUv = SourceSize / (vec2(512.0, 512.0) * 0.75) * coords
            + vec2(fract(noiseTime * 1000.0 / 51.0), fract(noiseTime * 1000.0 / 237.0));
    vec4 noiseTexel = noiseTexel(noiseUv);
    coords += (vec2(noiseTexel.b, noiseTexel.a) - vec2(0.5)) * jitter * (1.0 + pulse * 2.0);

    float color = 0.0001;
    float distance = length(vec2(0.5) - originalCoords);
    float noise = staticNoise + distortionScale * 7.0 + pulse * bandMask * 0.20;
    color += noiseTexel.a * noise * (1.0 - distance * 1.3);
    color += randomPass(coords * SourceSize) * glowingLine * 0.2;

    float roll = fract(Time * 0.18);
    float rollLine = 1.0 - smoothstep(0.0, 0.045, abs(originalCoords.y - roll));
    color += rollLine * rollingInterference;

    vec3 txtColor = sampleSafe(SourceTexture, coords).rgb;
    float split = rbgSplit + pulse * 0.18;
    if (split != 0.0) {
        float splitOffset = 0.0025 + pulse * 0.006;
        vec3 rightColor = sampleSafe(SourceTexture, coords + vec2(splitOffset, -0.001)).rgb;
        vec3 leftColor = sampleSafe(SourceTexture, coords + vec2(-splitOffset, -0.001)).rgb;
        txtColor.r = rightColor.r * 0.6 * split + txtColor.r * (1.0 - 0.6 * split);
        txtColor.g = leftColor.g * 0.4 * split + txtColor.g * (1.0 - 0.4 * split);
        txtColor.b = leftColor.b * 0.2 * split + txtColor.b * (1.0 - 0.2 * split);
    }

    float greyscaleColor = rgb2grey(txtColor);
    float reflectionMask = sum2(step(vec2(0.0), coords) - step(vec2(1.0), coords));
    reflectionMask = clamp(reflectionMask, 0.0, 1.0);

    vec3 foregroundColor = mix(fontColor, txtColor * fontColor / greyscaleColor, chromaColor);
    vec3 finalColor = mix(backgroundColor, foregroundColor, greyscaleColor * reflectionMask);
    finalColor += fontColor.rgb * vec3(color);
    finalColor += fontColor.rgb * greyscaleColor * phosphorGlow;
    finalColor *= 1.0 + (initialNoiseTexel.g - 0.5) * flickering;
    finalColor += vec3(ambientLight) * (1.0 - distance) * (1.0 - distance);

    if (pixelHeight != 0.0) {
        finalColor *= getScanlineIntensity(coords);
    }

    FragColor = vec4(finalColor, 1.0);
}