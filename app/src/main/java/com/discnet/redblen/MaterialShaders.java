package com.discnet.redblen;

/** Studio-light material shader: approximate IBL, not ray-traced scene reflections. */
public final class MaterialShaders {
    private MaterialShaders(){}
    public static final String VERTEX=
        "attribute vec3 aPos;\n" +
        "attribute vec3 aNormal;\n" +
        "uniform mat4 uMVP;\n" +
        "uniform mat4 uModel;\n" +
        "uniform mat3 uNormalMat;\n" +
        "uniform float uSize;\n" +
        "varying vec3 vN;\n" +
        "varying vec3 vLocalN;\n" +
        "varying vec3 vLocalP;\n" +
        "varying vec3 vWorldP;\n" +
        "void main(){\n" +
        "    vec4 world=uModel*vec4(aPos,1.0);\n" +
        "    vWorldP=world.xyz;\n" +
        "    vN=normalize(uNormalMat*aNormal);\n" +
        "    vLocalN=aNormal;\n" +
        "    vLocalP=aPos;\n" +
        "    gl_Position=uMVP*vec4(aPos,1.0);\n" +
        "    gl_PointSize=uSize;\n" +
        "}\n";
    public static final String FRAGMENT=
        "precision mediump float;\n" +
        "varying vec3 vN;\n" +
        "varying vec3 vLocalN;\n" +
        "varying vec3 vLocalP;\n" +
        "varying vec3 vWorldP;\n" +
        "uniform vec4 uColor;\n" +
        "uniform float uFlat,uMetallic,uRoughness,uReflectivity,uEmission,uOpacity;\n" +
        "uniform float uTexMix,uRepeat,uTexRotation,uUseTex;\n" +
        "uniform vec3 uEye;\n" +
        "uniform sampler2D uTex;\n" +
        "vec2 rotated(vec2 uv){\n" +
        "    float a=uTexRotation;\n" +
        "    vec2 centered=uv*uRepeat;\n" +
        "    return fract(vec2(cos(a)*centered.x-sin(a)*centered.y,\n" +
        "        sin(a)*centered.x+cos(a)*centered.y));\n" +
        "}\n" +
        "vec4 triplanar(){\n" +
        "    vec3 blend=pow(abs(normalize(vLocalN))+vec3(0.0001),vec3(4.0));\n" +
        "    blend=blend/(blend.x+blend.y+blend.z);\n" +
        "    vec4 sx=texture2D(uTex,rotated(vLocalP.zy*0.5));\n" +
        "    vec4 sy=texture2D(uTex,rotated(vLocalP.xz*0.5));\n" +
        "    vec4 sz=texture2D(uTex,rotated(vLocalP.xy*0.5));\n" +
        "    return sx*blend.x+sy*blend.y+sz*blend.z;\n" +
        "}\n" +
        "void main(){\n" +
        "    if(uFlat>0.5){gl_FragColor=uColor;return;}\n" +
        "    vec4 image=vec4(1.0);\n" +
        "    if(uUseTex>0.5)image=triplanar();\n" +
        "    vec3 albedo=uColor.rgb*mix(vec3(1.0),image.rgb,uTexMix*uUseTex);\n" +
        "    float alpha=uColor.a*uOpacity*mix(1.0,image.a,uTexMix*uUseTex);\n" +
        "    if(alpha<0.02)discard;\n" +
        "    vec3 N=normalize(vN);\n" +
        "    vec3 V=normalize(uEye-vWorldP);\n" +
        "    vec3 L=normalize(vec3(0.45,0.85,0.62));\n" +
        "    vec3 H=normalize(L+V);\n" +
        "    float ndl=max(dot(N,L),0.0);\n" +
        "    float rough=clamp(uRoughness,0.03,1.0);\n" +
        "    float metal=clamp(uMetallic,0.0,1.0);\n" +
        "    float strength=clamp(uReflectivity,0.0,1.0);\n" +
        "    vec3 F0=mix(vec3(0.04),albedo,metal);\n" +
        "    float ndv=clamp(dot(N,V),0.0,1.0);\n" +
        "    vec3 fresnel=F0+(vec3(1.0)-F0)*pow(1.0-ndv,5.0);\n" +
        "    vec3 reflected=reflect(-V,N);\n" +
        "    float sky=clamp(reflected.y*0.5+0.5,0.0,1.0);\n" +
        "    vec3 environment=mix(vec3(0.08,0.09,0.11),vec3(0.65,0.73,0.82),sky);\n" +
        "    float studio=pow(max(reflected.x*0.8+reflected.y*0.4,0.0),mix(3.0,22.0,1.0-rough));\n" +
        "    environment+=vec3(0.45,0.52,0.62)*studio;\n" +
        "    vec3 diffuse=albedo*(0.23+0.86*ndl)*(1.0-metal);\n" +
        "    float shininess=mix(4.0,136.0,pow(1.0-rough,2.0));\n" +
        "    vec3 specular=fresnel*pow(max(dot(N,H),0.0),shininess)*ndl*\n" +
        "        (0.3+2.4*(1.0-rough))*strength;\n" +
        "    vec3 reflection=environment*fresnel*(1.0-rough*0.82)*strength;\n" +
        "    vec3 result=diffuse+specular+reflection+albedo*uEmission;\n" +
        "    gl_FragColor=vec4(clamp(result,0.0,1.0),alpha);\n" +
        "}\n";
}
