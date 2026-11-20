#version 150
uniform sampler2D Sampler0;
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
uniform vec4 EnvDepth;
uniform float AlphaCutoff;
uniform float EnvScoped;
uniform vec4 EnvBounds;
in vec4 materialColor;
in vec2 texCoord;
in vec3 scenePosition;
in vec3 faceNormal;
in vec2 lightCoord;
in vec3 originalLight;
in float boundaryDistance;
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
    vec3 irradiance=clamp(EnvColor*(EnvBase*hemisphere+EnvRadiance*field*facing),vec3(0),vec3(1));
    vec3 nativeLight=originalLight;
    float scope=EnvScoped<.5?1.0:smoothstep(0.0,5.0,min(min(scenePosition.x-EnvBounds.x,EnvBounds.y-scenePosition.x),min(scenePosition.z-EnvBounds.z,EnvBounds.w-scenePosition.z)))*(1.0-smoothstep(103.0,111.0,scenePosition.y));
    float protection=(1.0-smoothstep(0.3,1.0,clamp(lightCoord.x/15.0,0.0,1.0)))*scope;
    vec3 illumination=mix(nativeLight,max(nativeLight,irradiance),protection);
    vec3 lit=surface.rgb*illumination;
    float air=smoothstep(EnvAirRange.x,EnvAirRange.y,viewDistance)*EnvAirMax*protection;
    vec3 airColor=EnvAirColor*(0.55+0.45*field);
    lit=mix(lit,airColor,air);
    // Bounded height-dependent surface haze, not volumetric fog. Walking surfaces
    // and warm windows above y=12 receive none. Native block light remains protected.
    float depth=smoothstep(0.0,EnvDepth.x-EnvDepth.y,EnvDepth.x-scenePosition.y);
    float deepMix=depth*smoothstep(EnvDepth.w,EnvDepth.w+35.0,viewDistance)*EnvDepth.z*protection;
    lit=mix(lit,vec3(0.012,0.033,0.055),deepMix);
    // Keep the native visibility/status-effect boundary; the scene air term is separately bounded.
    float boundary=smoothstep(FogStart,FogEnd,boundaryDistance)*FogColor.a;
    fragColor=vec4(mix(lit,FogColor.rgb,boundary),surface.a);
}
