#version 150

uniform sampler2D Sampler0;
uniform vec2 ScreenSize;
uniform vec4 PanelRect;
uniform float CornerRadius;
uniform float PixelSize;
uniform float BlurRadius;
uniform float Opacity;
out vec4 fragColor;

void main() {
    // Both glass and its one-GUI-pixel rim use this single, pixel-centered circle.
    vec2 local = (floor((gl_FragCoord.xy - PanelRect.xy) / PixelSize) + 0.5) * PixelSize;
    vec2 point = local - PanelRect.zw * 0.5;
    float radius = min(CornerRadius, min(PanelRect.z, PanelRect.w) * 0.5);
    vec2 edge = abs(point) - PanelRect.zw * 0.5 + radius;
    float distance = length(max(edge, 0.0)) + min(max(edge.x, edge.y), 0.0) - radius;
    if (distance > 0.0) discard;
    if (distance > -PixelSize) {
        fragColor = vec4(1.0, 1.0, 1.0, Opacity);
        return;
    }
    vec3 blurred = vec3(0.0);
    float weightSum = 0.0;
    for (int y = -2; y <= 2; y++) {
        for (int x = -2; x <= 2; x++) {
            vec2 offset = vec2(float(x), float(y));
            float weight = exp(-dot(offset, offset) * 0.5);
            blurred += texture(Sampler0, (gl_FragCoord.xy + offset * BlurRadius * 0.5) / ScreenSize).rgb * weight;
            weightSum += weight;
        }
    }
    vec3 glass = mix(blurred / weightSum, vec3(18.0, 20.0, 24.0) / 255.0, 0.39);
    fragColor = vec4(glass, Opacity);
}
