package com.discnet.redblen;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Editable polygon mesh. Vertices are flat XYZ floats; faces may be triangles or quads. */
public final class Mesh {
    public float[] vertices;
    public final ArrayList<int[]> faces = new ArrayList<>();
    public int revision = 0;

    public Mesh(float[] vertices, List<int[]> faces) {
        this.vertices = vertices.clone();
        for (int[] f : faces) this.faces.add(f.clone());
    }

    public Mesh copy() { return new Mesh(vertices, faces); }
    public int vertexCount() { return vertices.length / 3; }
    public int triangleCount() { int n=0; for(int[] f:faces) n+=Math.max(0,f.length-2); return n; }

    public void moveVertex(int index, float x, float y, float z) {
        if (index<0 || index>=vertexCount()) return;
        vertices[3*index]+=x; vertices[3*index+1]+=y; vertices[3*index+2]+=z; revision++;
    }

    public float[] faceNormal(int faceIndex) {
        if(faceIndex<0 || faceIndex>=faces.size()) return new float[]{0,1,0};
        int[] f=faces.get(faceIndex);
        if(f.length<3)return new float[]{0,1,0};
        float ax=vertices[3*f[1]]-vertices[3*f[0]];
        float ay=vertices[3*f[1]+1]-vertices[3*f[0]+1];
        float az=vertices[3*f[1]+2]-vertices[3*f[0]+2];
        float bx=vertices[3*f[2]]-vertices[3*f[0]];
        float by=vertices[3*f[2]+1]-vertices[3*f[0]+1];
        float bz=vertices[3*f[2]+2]-vertices[3*f[0]+2];
        float nx=ay*bz-az*by,ny=az*bx-ax*bz,nz=ax*by-ay*bx;
        float len=(float)Math.sqrt(nx*nx+ny*ny+nz*nz);
        return len<1e-7f?new float[]{0,1,0}:new float[]{nx/len,ny/len,nz/len};
    }

    /** Replace one polygon by its translated cap and N lateral polygons. */
    public int extrudeFace(int faceIndex, float distance) {
        if(faceIndex<0 || faceIndex>=faces.size())return -1;
        int[] f=faces.get(faceIndex),cap=new int[f.length];
        float[] normal=faceNormal(faceIndex);
        int old=vertexCount();
        vertices=Arrays.copyOf(vertices,vertices.length+3*f.length);
        for(int j=0;j<f.length;j++){
            cap[j]=old+j;
            for(int k=0;k<3;k++) vertices[3*(old+j)+k]=vertices[3*f[j]+k]+normal[k]*distance;
        }
        faces.set(faceIndex,cap);
        for(int j=0;j<f.length;j++){
            int next=(j+1)%f.length;
            faces.add(new int[]{f[j],f[next],cap[next],cap[j]});
        }
        revision++;return faceIndex;
    }

    /** Insert an inner coplanar face and surrounding quads. */
    public int insetFace(int faceIndex, float fraction) {
        if(faceIndex<0 || faceIndex>=faces.size())return -1;
        int[] f=faces.get(faceIndex),inner=new int[f.length]; float[] center=new float[3];
        for(int id:f) for(int k=0;k<3;k++)center[k]+=vertices[3*id+k]/f.length;
        int old=vertexCount();
        vertices=Arrays.copyOf(vertices,vertices.length+3*f.length);
        float t=Math.max(0.02f,Math.min(0.95f,fraction));
        for(int j=0;j<f.length;j++){
            inner[j]=old+j;
            for(int k=0;k<3;k++)vertices[3*(old+j)+k]=vertices[3*f[j]+k]*(1-t)+center[k]*t;
        }
        faces.set(faceIndex,inner);
        for(int j=0;j<f.length;j++){
            int next=(j+1)%f.length;
            faces.add(new int[]{f[j],f[next],inner[next],inner[j]});
        }
        revision++;return faceIndex;
    }

    public void flipFace(int idx) {
        if(idx<0||idx>=faces.size())return;
        int[] f=faces.get(idx);for(int i=0;i<f.length/2;i++){int t=f[i];f[i]=f[f.length-1-i];f[f.length-1-i]=t;}revision++;
    }

    public static Mesh cube() {
        float[] v={-1,-1,-1, 1,-1,-1, 1,1,-1, -1,1,-1,
                   -1,-1, 1, 1,-1, 1, 1,1, 1, -1,1,1};
        ArrayList<int[]> f=new ArrayList<>();
        f.add(new int[]{4,5,6,7});f.add(new int[]{1,0,3,2});
        f.add(new int[]{0,4,7,3});f.add(new int[]{5,1,2,6});
        f.add(new int[]{3,7,6,2});f.add(new int[]{0,1,5,4});
        return new Mesh(v,f);
    }

    public static Mesh plane() {
        return new Mesh(new float[]{-1,0,-1,-1,0,1,1,0,1,1,0,-1},
            Arrays.asList(new int[]{0,1,2,3}));
    }

    public static Mesh sphere() {
        int rings=12,sides=20;
        float[] v=new float[(rings+1)*sides*3]; ArrayList<int[]> f=new ArrayList<>();
        for(int i=0;i<=rings;i++){
            double phi=Math.PI*i/rings;
            for(int j=0;j<sides;j++){
                double theta=2*Math.PI*j/sides;
                int b=(i*sides+j)*3;
                v[b]=(float)(Math.sin(phi)*Math.cos(theta));
                v[b+1]=(float)Math.cos(phi);
                v[b+2]=(float)(Math.sin(phi)*Math.sin(theta));
            }
        }
        for(int i=0;i<rings;i++)for(int j=0;j<sides;j++){
            int a=i*sides+j,b=i*sides+(j+1)%sides,c=(i+1)*sides+(j+1)%sides,d=(i+1)*sides+j;
            if(i==0)f.add(new int[]{a,c,d});
            else if(i==rings-1)f.add(new int[]{a,b,c});
            else f.add(new int[]{a,b,c,d});
        }
        return new Mesh(v,f);
    }

    public static Mesh cylinder(boolean cone) {
        int sides=24;float[] v=new float[sides*2*3];ArrayList<int[]> faces=new ArrayList<>();
        for(int i=0;i<sides;i++){
            double a=i*Math.PI*2/sides;
            float x=(float)Math.cos(a),z=(float)Math.sin(a);
            v[3*i]=x;v[3*i+1]=-1;v[3*i+2]=z;
            v[3*(sides+i)]=cone?0:x;v[3*(sides+i)+1]=1;v[3*(sides+i)+2]=cone?0:z;
        }
        for(int i=0;i<sides;i++){
            int j=(i+1)%sides;
            if(cone)faces.add(new int[]{i,j,sides+j});
            else faces.add(new int[]{i,j,sides+j,sides+i});
        }
        int[] bottom=new int[sides],top=new int[sides];
        for(int i=0;i<sides;i++){bottom[i]=sides-i-1;top[i]=sides+i;}
        faces.add(bottom);if(!cone)faces.add(top);
        return new Mesh(v,faces);
    }

    public static Mesh primitive(String kind) {
        switch(kind){
            case "Esfera":return sphere();
            case "Cilindro":return cylinder(false);
            case "Cone":return cylinder(true);
            case "Plano":return plane();
            default:return cube();
        }
    }
}
