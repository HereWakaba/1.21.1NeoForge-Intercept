#version 150

uniform sampler2D DiffuseSampler;
uniform vec2 InSize;
uniform float Time;
uniform float EffectTime;
uniform float ChargeProgress;
uniform float InvertAmount;
uniform float TimeStopAmount;

in vec2 texCoord;
in vec2 oneTexel;

out vec4 fragColor;

float luma(vec3 c) {
    return dot(c, vec3(0.299, 0.587, 0.114));
}

void main() {
    float p = clamp(ChargeProgress, 0.0, 1.0);
    float iv = clamp(InvertAmount, 0.0, 1.0);
    float ts = clamp(TimeStopAmount, 0.0, 1.0);
    vec2 uv = texCoord;
    vec2 center = uv - 0.5;
    float dist = length(center);
    vec2 dir = center / (dist + 1e-5);
    float t = EffectTime * 60.0;

    vec2 warp = vec2(sin(uv.y * 28.0 + t) * 0.006, cos(uv.x * 24.0 + t * 1.3) * 0.006) * p
              + dir * (sin(dist * 42.0 - EffectTime * 3.0) * 0.004) * iv;
    vec2 wuv = uv + warp;

    float pk = max(max(p, iv), ts * 0.7);
    float ca = 0.008 * pk;
    float r = texture(DiffuseSampler, wuv + dir * ca).r;
    float g = texture(DiffuseSampler, wuv).g;
    float b = texture(DiffuseSampler, wuv - dir * ca).b;
    vec3 col = vec3(r, g, b);

    float c0 = luma(texture(DiffuseSampler, wuv).rgb);
    float cX = luma(texture(DiffuseSampler, wuv + vec2(oneTexel.x, 0.0)).rgb);
    float cY = luma(texture(DiffuseSampler, wuv + vec2(0.0, oneTexel.y)).rgb);
    float edge = smoothstep(0.06, 0.25, abs(c0 - cX) + abs(c0 - cY)) * p;
    col = mix(col, vec3(1.0), edge * 0.7);
    float gray = luma(col);
    col = mix(col, vec3(gray), p);

    if (iv > 0.001) {
        vec3 inv = vec3(1.0) - col;
        float ig = luma(inv);
        vec3 styled = mix(inv, vec3(ig), 0.25);
        float scan = 0.90 + 0.10 * sin(uv.y * InSize.y * 1.5 + EffectTime * 8.0);
        styled *= scan;
        float vigEdge = 0.34 + 0.06 * sin(EffectTime * 2.0);
        float vig = smoothstep(0.95, vigEdge, dist);
        styled *= mix(0.55, 1.0, vig);
        col = mix(col, styled, iv);
    }

    if (ts > 0.001) {
        float rows = 64.0;
        float rowIdx = floor(wuv.y * rows);
        float gseed = floor(EffectTime * 20.0);
        float n = fract(sin(rowIdx * 12.9898 + gseed * 78.233) * 43758.5453);
        float glitch  = step(0.52, n) * ts;
        float bigTear = step(0.94, n);
        float hshift  = (n - 0.5) * (0.03 + 0.14 * bigTear) * ts;
        vec2  guv     = vec2(clamp(wuv.x + hshift, 0.0, 1.0), wuv.y);

        float gca   = 0.006 * glitch;
        float pulse = 0.004 * ts * (0.5 + 0.5 * sin(EffectTime * 3.0));
        float off   = gca + pulse;
        float rr = texture(DiffuseSampler, guv + vec2(off, 0.0)).r;
        float gg = texture(DiffuseSampler, guv).g;
        float bb = texture(DiffuseSampler, guv - vec2(off, 0.0)).b;
        vec3  gcol = mix(col, vec3(rr, gg, bb), clamp(glitch * 1.2, 0.0, 1.0));
        gcol *= 1.0 - 0.40 * bigTear * ts;

        float gluma = luma(gcol);
        gcol = mix(gcol, vec3(gluma), 0.78 * ts);
        gcol += vec3(-0.03, 0.00, 0.07) * ts;

        float scan = 0.70 + 0.30 * sin(guv.y * InSize.y * 3.1415926 + EffectTime * 46.0);
        float drop = step(0.72, fract(sin((rowIdx + gseed) * 91.7) * 4375.11));
        gcol *= mix(1.0, scan, 0.9 * ts) * (1.0 - 0.5 * drop * ts);

        float band  = fract(EffectTime * 0.16);
        float sweep = smoothstep(0.03, 0.0, abs(guv.y - band));
        gcol += vec3(0.12, 0.16, 0.22) * sweep * ts;

        float vigEdge = 0.30 + 0.08 * sin(EffectTime * 2.5);
        float vig = smoothstep(1.05, vigEdge, dist);
        gcol *= mix(1.0, 0.30 + 0.70 * vig, ts);

        col = mix(col, gcol, ts);
    }

    fragColor = vec4(col, 1.0);
}
