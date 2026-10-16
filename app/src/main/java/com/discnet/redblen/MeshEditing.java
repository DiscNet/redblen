package com.discnet.redblen;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Polygon topology and symmetric editing. Geometry is intentionally CPU-side for ES2 devices. */
public final class MeshEditing {
    private MeshEditing() {}
    public static final int MAX_VERTICES=6500;
    private static final float MATCH_EPS=.014f;

    /** Insert a connected vertex inside a polygon; replace it with a triangle fan. */
    public static int insertOnFace(Mesh mesh,int faceIndex,float[] point) {
        if(faceIndex<0||faceIndex>=mesh.faces.size()||point==null||point.length<3)return -1;
        if(mesh.vertexCount()+1>MAX_VERTICES)return -1;
        int[] face=mesh.faces.get(faceIndex);
        if(face.length<3)return -1;
        for(int id:face){
            float dx=mesh.vertices[id*3]-point[0],dy=mesh.vertices[id*3+1]-point[1],dz=mesh.vertices[id*3+2]-point[2];
            if(dx*dx+dy*dy+dz*dz<.0001f)return -1;
        }
        int index=mesh.vertexCount();
        mesh.vertices=Arrays.copyOf(mesh.vertices,mesh.vertices.length+3);
        System.arraycopy(point,0,mesh.vertices,index*3,3);
        mesh.faces.set(faceIndex,new int[]{face[0],face[1],index});
        for(int j=1;j<face.length;j++){
            mesh.faces.add(new int[]{face[j],face[(j+1)%face.length],index});
        }
        mesh.revision++;
        return index;
    }

    private static long edge(int a,int b){
        int min=Math.min(a,b),max=Math.max(a,b);
        return (((long)min)<<32)|(max&0xffffffffL);
    }
    /** Linear subdivision: one face-center, shared edge-midpoints, four-sided daughter polygons.
        Geometry stays on the existing surface; no rounding/shrinking or broken shared edges. */
    public static boolean subdivide(Mesh mesh){
        int oldCount=mesh.vertexCount();
        long edgeCount=0;
        for(int[] f:mesh.faces)edgeCount+=f.length;
        if(oldCount+edgeCount+mesh.faces.size()>MAX_VERTICES||edgeCount>24000)return false;
        ArrayList<Float> coords=new ArrayList<>(mesh.vertices.length+(int)edgeCount*3);
        for(float v:mesh.vertices)coords.add(v);
        Map<Long,Integer> edges=new HashMap<>();
        ArrayList<int[]> updated=new ArrayList<>();
        for(int[] f:mesh.faces){
            int n=f.length;if(n<3)continue;
            int[] mids=new int[n];
            for(int i=0;i<n;i++){
                int a=f[i],b=f[(i+1)%n];
                long key=edge(a,b);
                Integer prior=edges.get(key);
                if(prior!=null){mids[i]=prior;continue;}
                int id=coords.size()/3;
                for(int k=0;k<3;k++)coords.add((mesh.vertices[a*3+k]+mesh.vertices[b*3+k])*.5f);
                edges.put(key,id);mids[i]=id;
            }
            int center=coords.size()/3;
            for(int k=0;k<3;k++){
                float value=0;
                for(int id:f)value+=mesh.vertices[id*3+k]/n;
                coords.add(value);
            }
            for(int i=0;i<n;i++)updated.add(new int[]{f[i],mids[i],center,mids[(i+n-1)%n]});
        }
        if(updated.isEmpty()||coords.size()/3>MAX_VERTICES)return false;
        float[] all=new float[coords.size()];
        for(int i=0;i<all.length;i++)all[i]=coords.get(i);
        mesh.vertices=all;mesh.faces.clear();mesh.faces.addAll(updated);mesh.revision++;
        return true;
    }

    private static String key(float x,float y,float z){
        return Math.round(x/MATCH_EPS)+":"+Math.round(y/MATCH_EPS)+":"+Math.round(z/MATCH_EPS);
    }
    private static String key(float[] v,int id){return key(v[id*3],v[id*3+1],v[id*3+2]);}

    private static int find(HashMap<String,ArrayList<Integer>> lookup,float[] vertices,float[] pos){
        int ix=Math.round(pos[0]/MATCH_EPS),iy=Math.round(pos[1]/MATCH_EPS),iz=Math.round(pos[2]/MATCH_EPS);
        float best=MATCH_EPS*MATCH_EPS*3;
        int found=-1;
        for(int i=-1;i<=1;i++)for(int j=-1;j<=1;j++)for(int k=-1;k<=1;k++){
            ArrayList<Integer> ids=lookup.get((ix+i)+":"+(iy+j)+":"+(iz+k));
            if(ids==null)continue;
            for(int id:ids){
                float dx=vertices[id*3]-pos[0],dy=vertices[id*3+1]-pos[1],dz=vertices[id*3+2]-pos[2];
                float d=dx*dx+dy*dy+dz*dz;
                if(d<best){best=d;found=id;}
            }
        }
        return found;
    }
    private static float[] transformed(float[] position,int mask,int radial,int radialCount){
        float[] p=position.clone();
        if((mask&1)!=0)p[0]=-p[0];
        if((mask&2)!=0)p[1]=-p[1];
        if((mask&4)!=0)p[2]=-p[2];
        if(radialCount>1&&radial>0){
            double a=2*Math.PI*radial/radialCount;
            float x=p[0],z=p[2];
            p[0]=(float)(Math.cos(a)*x+Math.sin(a)*z);
            p[2]=(float)(-Math.sin(a)*x+Math.cos(a)*z);
        }
        return p;
    }
    private static int[] masks(int enabled) {
        int count=0;for(int mask=0;mask<8;mask++)if((mask&~enabled)==0)count++;
        int[] out=new int[count];int index=0;
        for(int mask=0;mask<8;mask++)if((mask&~enabled)==0)out[index++]=mask;
        return out;
    }

    /** Existing symmetrical face partners, computed before topology is changed. */
    public static int[] symmetricFaces(Mesh mesh,int sourceFace,int mirrorMask,int radialCount){
        if(sourceFace<0||sourceFace>=mesh.faces.size())return new int[0];
        float[][] centers=new float[mesh.faces.size()][3];
        for(int i=0;i<mesh.faces.size();i++){
            int[] f=mesh.faces.get(i);
            for(int id:f)for(int k=0;k<3;k++)centers[i][k]+=mesh.vertices[3*id+k]/f.length;
        }
        java.util.LinkedHashSet<Integer> found=new java.util.LinkedHashSet<>();
        found.add(sourceFace);float[] center=centers[sourceFace];
        int[] subset=masks(mirrorMask);
        int count=Math.max(1,Math.min(8,radialCount));
        for(int angle=0;angle<count;angle++)for(int mask:subset){
            float[] wanted=transformed(center,mask,angle,count);
            float best=MATCH_EPS*MATCH_EPS*9;int candidate=-1;
            for(int i=0;i<centers.length;i++){
                float dx=wanted[0]-centers[i][0],dy=wanted[1]-centers[i][1],dz=wanted[2]-centers[i][2];
                float dist=dx*dx+dy*dy+dz*dz;
                if(dist<best){best=dist;candidate=i;}
            }
            if(candidate>=0)found.add(candidate);
        }
        int[] faces=new int[found.size()];int cursor=0;for(int i:found)faces[cursor++]=i;
        return faces;
    }
    /** Add one connected vertex for each existing face partner in the symmetry group. */
    public static int[] insertSymmetric(Mesh mesh,int face,float[] hit,int mask,int radial){
        int[] counterpartFaces=symmetricFaces(mesh,face,mask,radial);
        if(mesh.vertexCount()+counterpartFaces.length>MAX_VERTICES)return new int[0];
        ArrayList<Integer> ids=new ArrayList<>();
        // The original touched polygon is first, so the touched vertex remains the active one.
        int inserted=insertOnFace(mesh,face,hit);
        if(inserted<0)return new int[0];
        ids.add(inserted);
        if(counterpartFaces.length==1)return new int[]{inserted};
        // Source original face has been split; mirrored target indices are unchanged.
        for(int i=1;i<counterpartFaces.length;i++){
            int targetFace=counterpartFaces[i];
            if(targetFace==face)continue;
            float[] center=new float[3];int[] f=mesh.faces.get(targetFace);
            for(int id:f)for(int k=0;k<3;k++)center[k]+=mesh.vertices[id*3+k]/f.length;
            float best=Float.POSITIVE_INFINITY;float[] bestHit=null;
            int count=Math.max(1,Math.min(8,radial));
            for(int turn=0;turn<count;turn++)for(int subset:masks(mask)){
                float[] v=transformed(hit,subset,turn,count);
                // Candidate should belong to target face plane / neighborhood.
                float dx=v[0]-center[0],dy=v[1]-center[1],dz=v[2]-center[2];
                float distance=dx*dx+dy*dy+dz*dz;
                if(distance<best){best=distance;bestHit=v;}
            }
            int newId=insertOnFace(mesh,targetFace,bestHit);
            if(newId>=0)ids.add(newId);
        }
        int[] result=new int[ids.size()];for(int i=0;i<ids.size();i++)result[i]=ids.get(i);
        return result;
    }
    public static int editMirroredFaces(Mesh mesh,int face,String operation,int mirrorMask,int radialCount){
        int[] targets=symmetricFaces(mesh,face,mirrorMask,radialCount);
        if(targets.length==0)return 0;
        int changed=0;
        for(int target:targets){
            if(operation.equals("extrude")){
                if(mesh.vertexCount()+mesh.faces.get(target).length>MAX_VERTICES)break;
                if(mesh.extrudeFace(target,.45f)>=0)changed++;
            }else if(operation.equals("inset")){
                if(mesh.vertexCount()+mesh.faces.get(target).length>MAX_VERTICES)break;
                if(mesh.insetFace(target,.22f)>=0)changed++;
            }else if(operation.equals("flip")){mesh.flipFace(target);changed++;}
        }
        return changed;
    }

    /** Move selected vertices and existing mirrored/rotational counterparts in one atomic edit. */
    public static int moveSelection(Mesh mesh,Set<Integer> selection,float[] delta,int mirrorMask,int radialCount){
        if(selection.isEmpty())return 0;
        float[] original=mesh.vertices.clone();
        HashMap<String,ArrayList<Integer>> lookup=new HashMap<>();
        for(int i=0;i<mesh.vertexCount();i++)lookup.computeIfAbsent(key(original,i),k->new ArrayList<>()).add(i);
        LinkedHashMap<Integer,float[]> deltas=new LinkedHashMap<>();
        int[] copies=masks(mirrorMask);
        int rotations=Math.max(1,Math.min(radialCount,8));
        for(int id:selection){
            if(id<0||id>=mesh.vertexCount())continue;
            float[] old={original[3*id],original[3*id+1],original[3*id+2]};
            float[] safe=delta.clone();
            for(int axis=0;axis<3;axis++){
                if((mirrorMask&(1<<axis))!=0&&Math.abs(old[axis])<MATCH_EPS)safe[axis]=0f;
            }
            for(int radial=0;radial<rotations;radial++)for(int mask:copies){
                float[] expected=transformed(old,mask,radial,rotations);
                int counterpart=find(lookup,original,expected);
                if(counterpart<0)continue;
                float[] amount=transformed(safe,mask,radial,rotations);
                if(!deltas.containsKey(counterpart))deltas.put(counterpart,amount);
            }
        }
        for(Map.Entry<Integer,float[]> entry:deltas.entrySet()){
            int id=entry.getKey();float[] value=entry.getValue();
            for(int k=0;k<3;k++)mesh.vertices[3*id+k]=original[3*id+k]+value[k];
        }
        if(!deltas.isEmpty())mesh.revision++;
        return deltas.size();
    }

    /** Sculpt using falloff in local units; mirrored and radial strokes use identical weights. */
    public static int sculpt(Mesh mesh,float[] center,float[] normal,float radius,float strength,
                             Scene.SculptBrush brush,int mirrorMask,int radialCount){
        if(mesh.vertexCount()==0||radius<=0||strength<=0)return 0;
        float[] source=mesh.vertices.clone();
        int n=mesh.vertexCount(),repeats=Math.max(1,Math.min(radialCount,8));
        // For each affected vertex, keep the strongest mirrored falloff rather than adding overlaps.
        float[][] centers=new float[masks(mirrorMask).length*repeats][];
        float[][] directions=new float[centers.length][];
        int index=0;
        for(int angle=0;angle<repeats;angle++)for(int mask:masks(mirrorMask)){
            centers[index]=transformed(center,mask,angle,repeats);
            directions[index]=transformed(normal,mask,angle,repeats);
            index++;
        }
        float[][] average=null;
        if(brush==Scene.SculptBrush.SMOOTH){
            @SuppressWarnings("unchecked")
            ArrayList<Integer>[] adjacent=new ArrayList[n];
            for(int i=0;i<n;i++)adjacent[i]=new ArrayList<>();
            for(int[] face:mesh.faces)for(int i=0;i<face.length;i++){
                int a=face[i],b=face[(i+1)%face.length];
                if(!adjacent[a].contains(b))adjacent[a].add(b);
                if(!adjacent[b].contains(a))adjacent[b].add(a);
            }
            average=new float[n][3];
            for(int i=0;i<n;i++) {
                int degree=adjacent[i].size();
                if(degree==0){for(int k=0;k<3;k++)average[i][k]=source[3*i+k];continue;}
                for(int adj:adjacent[i])for(int k=0;k<3;k++)average[i][k]+=source[3*adj+k]/degree;
            }
        }
        int affected=0;float radiusSq=radius*radius;
        for(int i=0;i<n;i++){
            float x=source[3*i],y=source[3*i+1],z=source[3*i+2];
            float best=0;int bestCenter=0;
            for(int c=0;c<centers.length;c++){
                float[] p=centers[c];
                float dx=x-p[0],dy=y-p[1],dz=z-p[2];
                float d2=dx*dx+dy*dy+dz*dz;
                if(d2>=radiusSq)continue;
                float t=1f-(float)Math.sqrt(d2)/radius;
                float falloff=t*t*(3f-2f*t);
                if(falloff>best){best=falloff;bestCenter=c;}
            }
            if(best<=0)continue;
            float weight=best*strength;
            if(brush==Scene.SculptBrush.SMOOTH){
                float blend=Math.min(.55f,weight*.65f);
                for(int k=0;k<3;k++)mesh.vertices[3*i+k]=source[3*i+k]*(1-blend)+average[i][k]*blend;
            }else{
                float[] direction=directions[bestCenter];
                float directionSign=brush==Scene.SculptBrush.PULL?1f:-1f;
                float size=(float)Math.sqrt(direction[0]*direction[0]+direction[1]*direction[1]+direction[2]*direction[2]);
                if(size<1e-6f)continue;
                // Move toward/away from the mirrored surface normal, not camera facing Z.
                for(int k=0;k<3;k++)mesh.vertices[3*i+k]=source[3*i+k]+directionSign*
                    weight*radius*.15f*direction[k]/size;
                // Center seam cannot drift away from any active mirror plane.
                for(int k=0;k<3;k++)if((mirrorMask&(1<<k))!=0&&Math.abs(source[3*i+k])<MATCH_EPS)
                    mesh.vertices[3*i+k]=0;
            }
            affected++;
        }
        if(affected>0)mesh.revision++;
        return affected;
    }
}
