package dev.sixik.stationarenear.navigation;

import dev.sixik.unigui.api.core.UIScaleProvider;
import dev.sixik.unigui.api.posteffect.UiPostEffectBlendMode;
import dev.sixik.unigui.api.posteffect.UiPostEffectChain;
import dev.sixik.unigui.api.posteffect.UiPostEffectPass;
import dev.sixik.unigui.api.render.shaders.ShaderHandle;

import java.util.List;

final class SolarNavigationPostEffects {
    private static final ShaderHandle TERMINAL_CONTENT_SHADER = ShaderHandle.fragmentSource(
            "stationarenear:solar_navigation_terminal_content",
            """
                    #version 150

                    in vec2 uv;
                    out vec4 FragColor;

                    uniform sampler2D SourceTexture;
                    uniform vec2 SourceSize;
                    uniform float SourceFlipY;
                    uniform float Time;
                    uniform float curvature;
                    uniform float chromaAmount;
                    uniform float scanlineAmount;
                    uniform float vignetteAmount;
                    uniform float glassGlow;
                    uniform float noiseAmount;
                    uniform float sweepAmount;

                    float hash(vec2 p) {
                        p = fract(p * vec2(123.34, 456.21));
                        p += dot(p, p + 45.32);
                        return fract(p.x * p.y);
                    }

                    vec2 normalizedSourceUv() {
                        vec2 sourceUv = uv * 0.5 + 0.5;
                        if (SourceFlipY > 0.5) {
                            sourceUv.y = 1.0 - sourceUv.y;
                        }
                        return clamp(sourceUv, vec2(0.0), vec2(1.0));
                    }

                    vec2 textureUv(vec2 sourceUv) {
                        vec2 result = clamp(sourceUv, vec2(0.0), vec2(1.0));
                        if (SourceFlipY > 0.5) {
                            result.y = 1.0 - result.y;
                        }
                        return result;
                    }

                    vec4 sampleSource(vec2 sourceUv) {
                        return texture(SourceTexture, textureUv(sourceUv));
                    }

                    void main() {
                        vec2 sourceUv = normalizedSourceUv();
                        vec2 centered = sourceUv * 2.0 - 1.0;
                        centered.x *= SourceSize.x / max(SourceSize.y, 1.0);

                        float radiusSq = dot(centered, centered);
                        vec2 distorted = sourceUv + (sourceUv - 0.5) * radiusSq * curvature;
                        if (distorted.x < 0.0 || distorted.x > 1.0 || distorted.y < 0.0 || distorted.y > 1.0) {
                            FragColor = vec4(0.0, 0.0, 0.0, 1.0);
                            return;
                        }

                        vec4 base = sampleSource(distorted);
                        vec3 color = base.rgb;

                        vec2 chroma = vec2(chromaAmount * (0.35 + radiusSq), 0.0);
                        vec3 chromatic = vec3(
                                sampleSource(distorted + chroma).r,
                                color.g,
                                sampleSource(distorted - chroma).b);
                        color = mix(color, chromatic, 0.12);

                        float scan = 0.5 + 0.5 * sin(distorted.y * SourceSize.y * 3.14159);
                        color *= 1.0 - scanlineAmount * smoothstep(0.48, 1.0, scan);

                        float grille = 0.92 + 0.08 * sin(distorted.x * SourceSize.x * 3.14159);
                        color *= grille;

                        float phase = fract(Time * 0.13);
                        float active = 1.0 - smoothstep(0.34, 0.48, phase);
                        float sweepY = phase / 0.34;
                        float sweepCore = active * smoothstep(0.018, 0.0, abs(distorted.y - sweepY));
                        float sweepGlow = active * smoothstep(0.115, 0.0, abs(distorted.y - sweepY)) * 0.22;
                        color += vec3(0.025, 0.105, 0.160) * (sweepCore + sweepGlow) * sweepAmount;
                        color *= 1.0 + sweepCore * 0.08 * sweepAmount;

                        float vignette = smoothstep(1.18, 0.18, length(centered * vec2(0.80, 1.02)));
                        color *= mix(1.0 - vignetteAmount, 1.0 + glassGlow, vignette);

                        float edgeHighlight = smoothstep(0.42, 1.18, radiusSq) * smoothstep(1.46, 0.78, radiusSq);
                        color += vec3(0.035, 0.125, 0.190) * edgeHighlight * glassGlow;

                        float noise = hash(distorted * SourceSize.xy + Time * 34.0) - 0.5;
                        color += noise * noiseAmount;

                        FragColor = vec4(max(color, vec3(0.0)), 1.0);
                    }
                    """);

    private SolarNavigationPostEffects() {
    }


    static UiPostEffectChain retroTerminal(UIScaleProvider scaleProvider) {
        return retroTerminal(scaleProvider, () -> 0.0D);
    }

    static UiPostEffectChain retroTerminal(UIScaleProvider scaleProvider, java.util.function.DoubleSupplier impactSupplier) {
        UiPostEffectPass terminal = UiPostEffectPass.shader("stationarenear:retro_terminal")
                .uniforms(uniforms -> uniforms
                        .vec3("fontColor", 0.46f, 0.82f, 1.00f)
                        .vec3("backgroundColor", 0.006f, 0.018f, 0.030f)
                        .floatValue("chromaColor", 0.42f)
                        .floatSupplier("staticNoise", () -> 0.035f + impact(impactSupplier) * 0.055f)
                        .floatSupplier("horizontalSyncStrength", () -> 0.095f + impact(impactSupplier) * 0.140f)
                        .floatValue("horizontalSyncFrequency", 0.070f)
                        .vec2("jitter", 0.0010f, 0.00028f)
                        .floatSupplier("glowingLine", () -> 0.10f + impact(impactSupplier) * 0.24f)
                        .floatSupplier("flickering", () -> 0.040f + impact(impactSupplier) * 0.140f)
                        .floatValue("ambientLight", 0.055f)
                        .floatValue("pixelHeight", 5.40f)
                        .boolValue("pixelization", false)
                        .floatSupplier("rbgSplit", () -> 0.035f + impact(impactSupplier) * 0.070f)
                        .floatValue("scanlineStrength", 0.55f)
                        .floatValue("phosphorGlow", 0.18f)
                        .floatSupplier("glitchStrength", () -> 0.016f + impact(impactSupplier) * 0.070f)
                        .floatValue("glitchFrequency", 1.15f)
                        .floatValue("glitchBandHeight", 0.038f)
                        .floatSupplier("rollingInterference", () -> 0.045f + impact(impactSupplier) * 0.120f)
                        .floatSupplier("impactDistortion", () -> impact(impactSupplier))
                        .floatValue("noiseFrameRate", 24.0f)
                        .floatValue("glitchFrameRate", 18.0f)
                        .floatSupplier("UiScale", () -> UIScaleProvider.sanitize(
                                scaleProvider == null ? 1.0f : scaleProvider.scale())));

        UiPostEffectPass bloom = UiPostEffectPass.shader("stationarenear:retro_terminal_bloom")
                .uniforms(uniforms -> uniforms
                        .floatSupplier("bloomStrength", () -> 0.72f + impact(impactSupplier) * 0.58f)
                        .floatValue("bloomRadius", 2.10f)
                        .floatValue("threshold", 0.28f)
                        .vec3("bloomTint", 0.46f, 0.78f, 1.00f));

        UiPostEffectPass frame = UiPostEffectPass.shader("stationarenear:retro_terminal_frame")
                .uniforms(uniforms -> uniforms
                        .floatValue("screenCurvature", 0.145f)
                        .vec3("frameColor", 0.11f, 0.16f, 0.22f));

        return UiPostEffectChain.of(List.of(terminal, bloom, frame));
    }

    private static float impact(java.util.function.DoubleSupplier impactSupplier) {
        if (impactSupplier == null) {
            return 0.0f;
        }
        return Math.max(0.0f, Math.min(1.0f, (float) impactSupplier.getAsDouble()));
    }

    static UiPostEffectChain terminalContent() {
        return UiPostEffectChain.of(List.of(UiPostEffectPass.shader(TERMINAL_CONTENT_SHADER)
                .uniforms(uniforms -> uniforms
                        .floatValue("curvature", 0.095f)
                        .floatValue("chromaAmount", 0.00115f)
                        .floatValue("scanlineAmount", 0.055f)
                        .floatValue("vignetteAmount", 0.34f)
                        .floatValue("glassGlow", 0.16f)
                        .floatValue("noiseAmount", 0.004f)
                        .floatValue("sweepAmount", 0.85f))
                .blendMode(UiPostEffectBlendMode.REPLACE)));
    }
}