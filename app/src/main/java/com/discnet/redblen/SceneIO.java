package com.discnet.redblen;

import android.opengl.Matrix;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Locale;

public final class SceneIO {
    private SceneIO(){}
    public static String encode(Scene scene) {
        try {
            JSONObject root=new JSONObject().put("format","redblen-v1").put("selected",scene.selected);
            JSONArray arr=new JSONArray();
            for(Scene.Part p:scene.objects) {
                JSONObject o=new JSONObject().put("name",p.name).put("color",p.color).put("visible",p.visible)
                  .put("position",vec(p.position)).put("rotation",vec(p.rotation)).put("scale",vec(p.scale));
                JSONArray verts=new JSONArray(),faces=new JSONArray();
                for(float v:p.mesh.vertices)verts.put((double)v);
                for(int[] f:p.mesh.faces){JSONArray a=new JSONArray();for(int id:f)a.put(id);faces.put(a);}
                o.put("vertices",verts).put("faces",faces);arr.put(o);
            }
            return root.put("objects",arr).toString();
        } catch(JSONException e){throw new IllegalStateException(e);}
    }
    private static JSONArray vec(float[] values) throws JSONException {JSONArray a=new JSONArray();for(float v:values)a.put((double)v);return a;}
    private static float[] floats(JSONArray array,int expected)throws JSONException{
        if(array.length()!=expected)throw new JSONException("Invalid vector size");
        float[] out=new float[expected];
        for(int i=0;i<expected;i++){out[i]=(float)array.getDouble(i);if(!Float.isFinite(out[i]))throw new JSONException("Invalid number");}
        return out;
    }
    public static void decodeInto(Scene scene,String json)throws JSONException{
        JSONObject root=new JSONObject(json);
        if(!"redblen-v1".equals(root.optString("format")))throw new JSONException("Unsupported project");
        JSONArray arr=root.getJSONArray("objects");
        if(arr.length()>Scene.MAX_OBJECTS)throw new JSONException("Too many objects");
        ArrayList<Scene.Part> loaded=new ArrayList<>();
        for(int i=0;i<arr.length();i++){
            JSONObject o=arr.getJSONObject(i);
            JSONArray vs=o.getJSONArray("vertices"),fs=o.getJSONArray("faces");
            if(vs.length()%3!=0||vs.length()>150000||fs.length()>100000)throw new JSONException("Mesh too large");
            float[] verts=new float[vs.length()];
            for(int j=0;j<verts.length;j++){verts[j]=(float)vs.getDouble(j);if(!Float.isFinite(verts[j]))throw new JSONException("Invalid vertex");}
            ArrayList<int[]> faces=new ArrayList<>();
            for(int j=0;j<fs.length();j++){
                JSONArray f=fs.getJSONArray(j);
                if(f.length()<3||f.length()>256)throw new JSONException("Invalid polygon");
                int[] polygon=new int[f.length()];
                for(int k=0;k<polygon.length;k++){polygon[k]=f.getInt(k);if(polygon[k]<0||polygon[k]>=verts.length/3)throw new JSONException("Invalid index");}
                faces.add(polygon);
            }
            Scene.Part p=new Scene.Part(o.optString("name","Objeto "+(i+1)),new Mesh(verts,faces));
            p.position=floats(o.getJSONArray("position"),3);
            p.rotation=floats(o.getJSONArray("rotation"),3);
            p.scale=floats(o.getJSONArray("scale"),3);
            p.color=o.optInt("color",0xFF91AEC3);
            p.visible=o.optBoolean("visible",true);loaded.add(p);
        }
        scene.objects.clear();scene.objects.addAll(loaded);
        scene.selected=Math.min(arr.length()-1,Math.max(-1,root.optInt("selected",-1)));
        scene.selectedFace=-1;scene.selectedVertex=-1;
    }

    public static void writeObj(Scene scene,Writer output)throws IOException{
        BufferedWriter w=output instanceof BufferedWriter?(BufferedWriter)output:new BufferedWriter(output);
        w.write("# Redblen OBJ export\n");
        int base=1;
        for(Scene.Part p:scene.objects){
            if(!p.visible)continue;
            w.write("o "+p.name.replaceAll("[^a-zA-Z0-9_-]","_")+"\n");
            float[] m=ModelTransforms.matrix(p),v=new float[4],transformed=new float[4];
            for(int i=0;i<p.mesh.vertexCount();i++){
                v[0]=p.mesh.vertices[3*i];v[1]=p.mesh.vertices[3*i+1];v[2]=p.mesh.vertices[3*i+2];v[3]=1;
                Matrix.multiplyMV(transformed,0,m,0,v,0);
                w.write(String.format(Locale.US,"v %.6f %.6f %.6f\n",transformed[0],transformed[1],transformed[2]));
            }
            for(int[] f:p.mesh.faces){
                w.write("f");for(int id:f)w.write(" "+(base+id));w.write("\n");
            }
            base+=p.mesh.vertexCount();
        }
        w.flush();
    }

    public static Mesh readObj(Reader input)throws IOException{
        BufferedReader r=new BufferedReader(input);
        ArrayList<Float> vertices=new ArrayList<>();ArrayList<int[]> faces=new ArrayList<>();
        String line;int lineCount=0;
        while((line=r.readLine())!=null){
            if(++lineCount>200000)throw new IOException("Arquivo OBJ muito grande");
            String s=line.trim();if(s.isEmpty()||s.startsWith("#"))continue;
            String[] tok=s.split("\\s+");
            if(tok[0].equals("v")&&tok.length>=4){
                if(vertices.size()>=150000)throw new IOException("Limite de vértices excedido");
                try {for(int i=1;i<4;i++){float v=Float.parseFloat(tok[i]);if(!Float.isFinite(v))throw new NumberFormatException();vertices.add(v);}}
                catch(NumberFormatException e){throw new IOException("Vértice inválido",e);}
            } else if(tok[0].equals("f")&&tok.length>=4){
                if(faces.size()>=100000||tok.length>257)throw new IOException("OBJ muito complexo");
                int[] face=new int[tok.length-1];
                try {
                    for(int j=1;j<tok.length;j++){
                        int id=Integer.parseInt(tok[j].split("/")[0]);
                        face[j-1]=id>0?id-1:vertices.size()/3+id;
                    }
                } catch(Exception e){throw new IOException("Face OBJ inválida",e);}
                faces.add(face);
            }
        }
        float[] v=new float[vertices.size()];for(int i=0;i<v.length;i++)v[i]=vertices.get(i);
        for(int[] f:faces)for(int id:f)if(id<0||id>=v.length/3)throw new IOException("Índice OBJ inválido");
        if(faces.isEmpty())throw new IOException("Nenhuma face encontrada");
        return new Mesh(v,faces);
    }
}
