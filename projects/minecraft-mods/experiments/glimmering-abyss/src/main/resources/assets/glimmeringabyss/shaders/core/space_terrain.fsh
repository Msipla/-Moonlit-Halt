#version 150
uniform sampler2D Sampler0;
uniform sampler2D Sampler2;
uniform vec4 ColorModulator;
uniform float FogStart;
uniform float FogEnd;
uniform vec4 FogColor;
uniform vec3 EnvColor;
uniform float EnvBase;
uniform vec4 EnvAnchor;
uniform float EnvRadiance;
uniform vec3 EnvDirection;
uniform vec3 EnvAirColor;
uniform vec2 EnvAirRange;
uniform float EnvAirMax;
uniform float AlphaCutoff;
in vec4 materialColor;
in vec2 texCoord;
in vec3 scenePosition;
in vec3 faceNormal;
in vec2 lightCoord;
in float viewDistance;
out vec4 fragColor;
void main() {
    vec4 surface=texture(Sampler0,texCoord)*materialColor*ColorModulator;
    if(surface.a<AlphaCutoff)discard;
    vec3 normal=normalize(faceNormal);
    vec3 toAnchor=EnvAnchor.xyz-scenePosition;
    float field=exp(-0.5*dot(toAnchor,toAnchor)/(EnvAnchor.w*EnvAnchor.w));
    float hemisphere=0.38+0.62*max(dot(normal,normalize(EnvDirection)),0.0);
    float facing=0.16+0.84*max(dot(normal,normalize(toAnchor+vec3(0.0001))),0.0);
    vec3 irradiance=EnvColor*(EnvBase*hemisphere+EnvRadiance*field*facing);
    vec3 nativeLight=texelFetch(Sampler2,ivec2(clamp(lightCoord,vec2(0),vec2(15))),0).rgb;
    float protection=1.0-smoothstep(0.3,1.0,clamp(lightCoord.x/15.0,0.0,1.0));
    vec3 illumination=mix(nativeLight,max(nativeLight,irradiance),protection);
    vec3 lit=surface.rgb*illumination;
    float air=smoothstep(EnvAirRange.x,EnvAirRange.y,viewDistance)*EnvAirMax*protection;
    vec3 airColor=EnvAirColor*(0.55+0.45*field);
    lit=mix(lit,airColor,air);
    // Keep the native visibility/status-effect boundary; the scene air term is separately bounded.
    float boundary=smoothstep(FogStart,FogEnd,viewDistance)*FogColor.a;
    fragColor=vec4(mix(lit,FogColor.rgb,boundary),surface.a);
}
