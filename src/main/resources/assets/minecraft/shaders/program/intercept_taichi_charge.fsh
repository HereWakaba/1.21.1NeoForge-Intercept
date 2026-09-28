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
    float p = clamp(ChargeProgress, 0.0, 1.0);   // 蓄力扭曲强度
    float iv = clamp(InvertAmount, 0.0, 1.0);     // 全反强度
    float ts = clamp(TimeStopAmount, 0.0, 1.0);   // 时停指示（扫描线/故障，不反色）
    vec2 uv = texCoord;
    vec2 center = uv - 0.5;
    float dist = length(center);
    vec2 dir = center / (dist + 1e-5);
    float t = EffectTime * 60.0;

    // 扭曲：蓄力正弦扭曲 + 全反径向波纹（各自权重，切换时叠加=交叉过渡）
    vec2 warp = vec2(sin(uv.y * 28.0 + t) * 0.006, cos(uv.x * 24.0 + t * 1.3) * 0.006) * p
              + dir * (sin(dist * 42.0 - EffectTime * 3.0) * 0.004) * iv;
    vec2 wuv = uv + warp;

    // 色散（三模式共享，取较强者）
    float pk = max(max(p, iv), ts * 0.7);
    float ca = 0.008 * pk;
    float r = texture(DiffuseSampler, wuv + dir * ca).r;
    float g = texture(DiffuseSampler, wuv).g;
    float b = texture(DiffuseSampler, wuv - dir * ca).b;
    vec3 col = vec3(r, g, b);

    // —— 蓄力模式：Sobel 描边 + 黑白 ——（p 驱动）
    float c0 = luma(texture(DiffuseSampler, wuv).rgb);
    float cX = luma(texture(DiffuseSampler, wuv + vec2(oneTexel.x, 0.0)).rgb);
    float cY = luma(texture(DiffuseSampler, wuv + vec2(0.0, oneTexel.y)).rgb);
    float edge = smoothstep(0.06, 0.25, abs(c0 - cX) + abs(c0 - cY)) * p;
    col = mix(col, vec3(1.0), edge * 0.7);
    float gray = luma(col);
    col = mix(col, vec3(gray), p);

    // —— 全反模式：反色 + 去饱和 + 扫描线 + 脉动暗角 ——（iv 驱动）
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

    // —— 时停指示（剧烈版）：整屏去色凝滞 + 冷调 + 高频故障行/大块撕裂 + RGB 撕裂 + 强扫描线/快门光带 + 脉动暗角 ——
    if (ts > 0.001) {
        float rows = 64.0;
        float rowIdx = floor(wuv.y * rows);
        float gseed = floor(EffectTime * 20.0);                 // 高频重掷，故障更抽搐
        float n = fract(sin(rowIdx * 12.9898 + gseed * 78.233) * 43758.5453);
        float glitch  = step(0.52, n) * ts;                     // 约近半行参与故障
        float bigTear = step(0.94, n);                          // 偶发整块大撕裂
        float hshift  = (n - 0.5) * (0.03 + 0.14 * bigTear) * ts;
        vec2  guv     = vec2(clamp(wuv.x + hshift, 0.0, 1.0), wuv.y);

        // RGB 撕裂 + 全局色散脉冲（不反相）
        float gca   = 0.006 * glitch;
        float pulse = 0.004 * ts * (0.5 + 0.5 * sin(EffectTime * 3.0));
        float off   = gca + pulse;
        float rr = texture(DiffuseSampler, guv + vec2(off, 0.0)).r;
        float gg = texture(DiffuseSampler, guv).g;
        float bb = texture(DiffuseSampler, guv - vec2(off, 0.0)).b;
        vec3  gcol = mix(col, vec3(rr, gg, bb), clamp(glitch * 1.2, 0.0, 1.0));
        gcol *= 1.0 - 0.40 * bigTear * ts;                      // 撕裂块内塌陷变暗

        // 去色 + 冷蓝基调（时间"冻结"感）
        float gluma = luma(gcol);
        gcol = mix(gcol, vec3(gluma), 0.78 * ts);
        gcol += vec3(-0.03, 0.00, 0.07) * ts;

        // 强扫描线 + 偶发整行暗线（信号丢失）
        float scan = 0.70 + 0.30 * sin(guv.y * InSize.y * 3.1415926 + EffectTime * 46.0);
        float drop = step(0.72, fract(sin((rowIdx + gseed) * 91.7) * 4375.11));
        gcol *= mix(1.0, scan, 0.9 * ts) * (1.0 - 0.5 * drop * ts);

        // 缓慢上扫的快门光带
        float band  = fract(EffectTime * 0.16);
        float sweep = smoothstep(0.03, 0.0, abs(guv.y - band));
        gcol += vec3(0.12, 0.16, 0.22) * sweep * ts;

        // 脉动暗角（更重）
        float vigEdge = 0.30 + 0.08 * sin(EffectTime * 2.5);
        float vig = smoothstep(1.05, vigEdge, dist);
        gcol *= mix(1.0, 0.30 + 0.70 * vig, ts);

        col = mix(col, gcol, ts);
    }

    fragColor = vec4(col, 1.0);
}
