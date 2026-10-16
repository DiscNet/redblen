package com.discnet.redblen;

import java.util.ArrayDeque;
import java.util.ArrayList;

public final class Scene {
    public static final int MAX_OBJECTS=60;
    public enum Mode { OBJECT, VERTEX, FACE }
    public enum Tool { ORBIT, MOVE, ROTATE, SCALE }
    public final ArrayList<Part> objects=new ArrayList<>();
    public int selected=-1,selectedFace=-1,selectedVertex=-1;
    public Mode mode=Mode.OBJECT;
    public Tool tool=Tool.ORBIT;
    private final ArrayDeque<String> undo=new ArrayDeque<>(),redo=new ArrayDeque<>();

    public static final class Part {
        public String name;
        public Mesh mesh;
        public float[] position={0,0,0},rotation={0,0,0},scale={1,1,1};
        public int color=0xFF91AEC3;
        public boolean visible=true;
        public Part(String name,Mesh mesh){this.name=name;this.mesh=mesh;}
        public Part copy(){
            Part p=new Part(name+" copy",mesh.copy());
            p.position=position.clone();p.rotation=rotation.clone();p.scale=scale.clone();
            p.position[0]+=0.4f;p.color=color;p.visible=visible;return p;
        }
    }

    public Scene(boolean withCube){if(withCube)add("Cubo",false);}
    public Part current(){return selected>=0&&selected<objects.size()?objects.get(selected):null;}
    public void add(String kind,boolean record) {
        if(objects.size()>=MAX_OBJECTS)return;
        if(record)checkpoint();
        Part p=new Part(kind+" "+(objects.size()+1),Mesh.primitive(kind));
        objects.add(p);selected=objects.size()-1;selectedFace=-1;selectedVertex=-1;
    }
    public void delete(){
        if(current()==null)return;checkpoint();
        objects.remove(selected);selected=Math.min(selected,objects.size()-1);
        selectedFace=-1;selectedVertex=-1;
    }
    public void duplicate() {
        if(current()==null||objects.size()>=MAX_OBJECTS)return;
        checkpoint();objects.add(current().copy());selected=objects.size()-1;selectedFace=-1;selectedVertex=-1;
    }
    public void checkpoint(){
        if(undo.size()>=35)undo.removeFirst();
        undo.addLast(SceneIO.encode(this));redo.clear();
    }
    public boolean undo(){
        if(undo.isEmpty())return false;
        redo.addLast(SceneIO.encode(this));
        String snapshot=undo.removeLast();
        try {SceneIO.decodeInto(this,snapshot);return true;}catch(Exception e){return false;}
    }
    public boolean redo(){
        if(redo.isEmpty())return false;
        undo.addLast(SceneIO.encode(this));
        String snapshot=redo.removeLast();
        try {SceneIO.decodeInto(this,snapshot);return true;}catch(Exception e){return false;}
    }
    public void clearHistory(){undo.clear();redo.clear();}
}
