package com.discnet.redblen;

import android.opengl.Matrix;

/** Identical pivot-centered object transforms for display, picking and OBJ export. */
public final class ModelTransforms {
    private ModelTransforms() {}

    public static float[] pivot(Mesh mesh) {
        if(mesh.vertexCount()==0)return new float[]{0,0,0};
        float[] min={Float.POSITIVE_INFINITY,Float.POSITIVE_INFINITY,Float.POSITIVE_INFINITY};
        float[] max={Float.NEGATIVE_INFINITY,Float.NEGATIVE_INFINITY,Float.NEGATIVE_INFINITY};
        for(int i=0;i<mesh.vertices.length;i+=3){
            for(int k=0;k<3;k++){float value=mesh.vertices[i+k];min[k]=Math.min(min[k],value);max[k]=Math.max(max[k],value);}
        }
        return new float[]{(min[0]+max[0])*.5f,(min[1]+max[1])*.5f,(min[2]+max[2])*.5f};
    }
    public static float[] matrix(Scene.Part p) {
        float[] center=pivot(p.mesh),m=new float[16];
        Matrix.setIdentityM(m,0);
        Matrix.translateM(m,0,p.position[0],p.position[1],p.position[2]);
        Matrix.rotateM(m,0,p.rotation[2],0,0,1);
        Matrix.rotateM(m,0,p.rotation[1],0,1,0);
        Matrix.rotateM(m,0,p.rotation[0],1,0,0);
        Matrix.translateM(m,0,center[0],center[1],center[2]);
        Matrix.scaleM(m,0,p.scale[0],p.scale[1],p.scale[2]);
        Matrix.translateM(m,0,-center[0],-center[1],-center[2]);
        return m;
    }
    public static float[] worldPoint(Scene.Part p,float[] local){
        float[] in={local[0],local[1],local[2],1},out=new float[4];
        Matrix.multiplyMV(out,0,matrix(p),0,in,0);
        return new float[]{out[0],out[1],out[2]};
    }
    public static float[] worldCenter(Scene.Part p){return worldPoint(p,pivot(p.mesh));}
    /** Transform a translation in world space into mesh-local space (w = 0). */
    public static float[] worldDeltaToLocal(Scene.Part p,float[] world) {
        float[] inverse=new float[16],input={world[0],world[1],world[2],0},output=new float[4];
        if(!Matrix.invertM(inverse,0,matrix(p),0))return new float[]{0,0,0};
        Matrix.multiplyMV(output,0,inverse,0,input,0);
        return new float[]{output[0],output[1],output[2]};
    }
}
