package com.discnet.redblen;

import android.content.Context;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;
import android.view.MotionEvent;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/** Real-time ES2 shaded/wireframe viewport with ray-mesh and screen-space vertex selection. */
public final class View3D extends GLSurfaceView {
    public interface Listener {
        void onSelectionChanged();
        void onDocumentChanged();
        void onAnchorChanged();
    }
    private final Scene scene;
    private final Engine engine;
    private final Listener listener;
    private final float density, touchSlop;
    private float downX,downY,lastX,lastY,lastMidX,lastMidY,lastSpan;
    private boolean didDrag,hadMultiTouch,historySaved,anchorPick;

    public View3D(Context c,Scene scene,Listener listener){
        super(c);
        this.scene=scene;
        this.listener=listener;
        density=getResources().getDisplayMetrics().density;
        touchSlop=android.view.ViewConfiguration.get(c).getScaledTouchSlop();
        setEGLContextClientVersion(2);
        setEGLConfigChooser(8,8,8,8,16,0);
        setPreserveEGLContextOnPause(true);
        engine=new Engine(scene);
        setRenderer(engine);
        setRenderMode(RENDERMODE_WHEN_DIRTY);
    }
    public void refresh(){requestRender();}
    public float[] anchor(){return engine.target.clone();}
    public boolean isPickingAnchor(){return anchorPick;}
    public void beginPickAnchor(){anchorPick=true;listener.onAnchorChanged();}
    public void cancelPickAnchor(){anchorPick=false;listener.onAnchorChanged();}
    public void setAnchor(float x,float y,float z){
        if(!Float.isFinite(x)||!Float.isFinite(y)||!Float.isFinite(z))return;
        engine.target=new float[]{x,y,z};anchorPick=false;
        listener.onAnchorChanged();refresh();
    }
    public void resetCamera(){
        engine.yaw=0.65f;engine.pitch=0.34f;engine.distance=8.5f;
        setAnchor(0,0,0);
    }
    public boolean focus() {
        Scene.Part p=scene.current();
        if(p==null)return false;
        setAnchor(ModelTransforms.worldCenter(p));
        float[] m=ModelTransforms.pivot(p.mesh);
        float radius=0.5f;
        for(int i=0;i<p.mesh.vertexCount();i++){
            float dx=(p.mesh.vertices[3*i]-m[0])*p.scale[0];
            float dy=(p.mesh.vertices[3*i+1]-m[1])*p.scale[1];
            float dz=(p.mesh.vertices[3*i+2]-m[2])*p.scale[2];
            radius=Math.max(radius,(float)Math.sqrt(dx*dx+dy*dy+dz*dz));
        }
        engine.distance=clamp(radius*3.5f,1.2f,130f);refresh();
        return true;
    }
    private void setAnchor(float[] xyz){setAnchor(xyz[0],xyz[1],xyz[2]);}
    public boolean anchorSelection(){
        Scene.Part p=scene.current();
        if(p==null)return false;
        if(scene.mode==Scene.Mode.VERTEX&&scene.selectedVertex>=0&&scene.selectedVertex<p.mesh.vertexCount()) {
            int id=scene.selectedVertex;
            setAnchor(ModelTransforms.worldPoint(p,new float[]{p.mesh.vertices[3*id],p.mesh.vertices[3*id+1],p.mesh.vertices[3*id+2]}));
        }else if(scene.mode==Scene.Mode.FACE&&scene.selectedFace>=0&&scene.selectedFace<p.mesh.faces.size()){
            int[] face=p.mesh.faces.get(scene.selectedFace);float[] avg=new float[3];
            for(int id:face)for(int k=0;k<3;k++)avg[k]+=p.mesh.vertices[3*id+k]/face.length;
            setAnchor(ModelTransforms.worldPoint(p,avg));
        }else setAnchor(ModelTransforms.worldCenter(p));
        return true;
    }
    public void dolly(float factor){engine.distance=clamp(engine.distance*factor,0.45f,160f);refresh();}
    public void pan(float horizontal,float vertical){engine.panPixels(horizontal*density,vertical*density);refresh();}
    @Override public boolean onTouchEvent(MotionEvent e){
        switch(e.getActionMasked()){
            case MotionEvent.ACTION_DOWN:
                downX=lastX=e.getX();downY=lastY=e.getY();
                didDrag=false;hadMultiTouch=false;historySaved=false;lastSpan=0f;
                return true;
            case MotionEvent.ACTION_POINTER_DOWN:
                hadMultiTouch=true;
                if(e.getPointerCount()>=2){
                    lastSpan=span(e);
                    lastMidX=(e.getX(0)+e.getX(1))*.5f;
                    lastMidY=(e.getY(0)+e.getY(1))*.5f;
                }
                return true;
            case MotionEvent.ACTION_MOVE:
                if(e.getPointerCount()>=2){
                    float newSpan=span(e);
                    float mx=(e.getX(0)+e.getX(1))*.5f;
                    float my=(e.getY(0)+e.getY(1))*.5f;
                    if(lastSpan>1f&&newSpan>1f)engine.distance=clamp(engine.distance*lastSpan/newSpan,0.45f,160f);
                    engine.panPixels(mx-lastMidX,my-lastMidY);
                    lastSpan=newSpan;lastMidX=mx;lastMidY=my;
                    hadMultiTouch=true;didDrag=true;refresh();
                    return true;
                }
                float x=e.getX(),y=e.getY();
                float dx=x-lastX,dy=y-lastY;
                lastX=x;lastY=y;
                if(hadMultiTouch)return true;
                if(!didDrag&&Math.hypot(x-downX,y-downY)<=touchSlop)return true;
                if(!didDrag){didDrag=true;dx=0;dy=0;}
                if(dx==0&&dy==0)return true;
                if(anchorPick)return true;
                Scene.Part part=scene.current();
                boolean edit=scene.mode!=Scene.Mode.OBJECT;
                boolean selectedElement=(scene.mode==Scene.Mode.VERTEX&&scene.selectedVertex>=0)||
                    (scene.mode==Scene.Mode.FACE&&scene.selectedFace>=0);
                boolean manipulate=part!=null&&!anchorPick &&
                    (scene.tool==Scene.Tool.MOVE||scene.tool==Scene.Tool.ROTATE||scene.tool==Scene.Tool.SCALE) &&
                    (!edit||(selectedElement&&scene.tool==Scene.Tool.MOVE));
                if(manipulate){
                    if(!historySaved){scene.checkpoint();historySaved=true;}
                    float[] delta=engine.screenDeltaWorld(dx,dy);
                    if(edit){
                        float[] local=ModelTransforms.worldDeltaToLocal(part,delta);
                        if(scene.mode==Scene.Mode.VERTEX){
                            part.mesh.moveVertex(scene.selectedVertex,local[0],local[1],local[2]);
                        }else if(scene.selectedFace<part.mesh.faces.size()){
                            int[] face=part.mesh.faces.get(scene.selectedFace);
                            boolean[] visited=new boolean[part.mesh.vertexCount()];
                            for(int id:face)if(!visited[id]){
                                part.mesh.moveVertex(id,local[0],local[1],local[2]);
                                visited[id]=true;
                            }
                        }
                    }else{
                        switch(scene.tool){
                            case MOVE:
                                for(int k=0;k<3;k++)part.position[k]+=delta[k];
                                break;
                            case ROTATE:
                                part.rotation[1]+=dx*0.45f;
                                part.rotation[0]+=dy*0.45f;
                                break;
                            case SCALE:
                                float amount=scene.scaleAxis==Scene.ScaleAxis.X||scene.scaleAxis==Scene.ScaleAxis.Z?dx:-dy;
                                float multiplier=(float)Math.exp(amount*0.009f);
                                if(scene.scaleAxis==Scene.ScaleAxis.ALL){
                                    for(int k=0;k<3;k++)part.scale[k]=clamp(part.scale[k]*multiplier,0.025f,100);
                                }else {
                                    int axis=scene.scaleAxis==Scene.ScaleAxis.X?0:scene.scaleAxis==Scene.ScaleAxis.Y?1:2;
                                    part.scale[axis]=clamp(part.scale[axis]*multiplier,0.025f,100);
                                }
                                break;
                            default:break;
                        }
                    }
                    listener.onDocumentChanged();
                }else{
                    switch(scene.tool){
                        case PAN:engine.panPixels(dx,dy);break;
                        case LOOK:engine.lookPixels(dx,dy);break;
                        default:engine.orbitPixels(dx,dy);break;
                    }
                }
                refresh();
                return true;
            case MotionEvent.ACTION_POINTER_UP:
                hadMultiTouch=true;
                // Retain the remaining pointer to prevent camera jumps when fingers are lifted.
                int remain=e.getActionIndex()==0?1:0;
                if(remain<e.getPointerCount()){
                    lastX=e.getX(remain);lastY=e.getY(remain);
                }
                lastSpan=0f;return true;
            case MotionEvent.ACTION_UP:
                if(!hadMultiTouch&&!didDrag){
                    float tapX=e.getX(),tapY=e.getY();
                    if(anchorPick){
                        float[] hit=engine.pickAnchor(tapX,tapY);
                        if(hit!=null)setAnchor(hit);
                        else cancelPickAnchor();
                    }else if(scene.mode==Scene.Mode.VERTEX){
                        scene.selectedVertex=engine.pickVertex(tapX,tapY,26*density);
                        listener.onSelectionChanged();refresh();
                    }else{
                        int[] hit=engine.pickFace(tapX,tapY);
                        if(hit==null){
                            scene.selected=-1;scene.selectedFace=-1;scene.selectedVertex=-1;
                        }else{
                            scene.selected=hit[0];scene.selectedFace=hit[1];scene.selectedVertex=-1;
                        }
                        listener.onSelectionChanged();refresh();
                    }
                }
                return true;
            case MotionEvent.ACTION_CANCEL:
                return true;
            default:return true;
        }
    }
    private static float clamp(float a,float lo,float hi){return Math.max(lo,Math.min(hi,a));}
    private static float span(MotionEvent e){
        float dx=e.getX(0)-e.getX(1),dy=e.getY(0)-e.getY(1);
        return (float)Math.hypot(dx,dy);
    }

    static final class Engine implements GLSurfaceView.Renderer {
        final Scene scene;
        volatile float yaw=0.65f,pitch=0.34f,distance=8.5f;
        volatile float[] target={0,0,0};
        volatile int width=1,height=1;
        private int program,aPos,aNormal,uMVP,uModel,uColor,uFlat,uSize;
        private final float[] proj=new float[16],view=new float[16],vp=new float[16],model=new float[16],mvp=new float[16];
        Engine(Scene scene){this.scene=scene;}

        @Override public void onSurfaceCreated(GL10 ignored,EGLConfig config){
            GLES20.glClearColor(0.062f,0.071f,0.083f,1f);
            GLES20.glEnable(GLES20.GL_DEPTH_TEST);
            GLES20.glDepthFunc(GLES20.GL_LEQUAL);
            String vs="attribute vec3 aPos; attribute vec3 aNormal; uniform mat4 uMVP; uniform mat4 uModel; uniform float uSize; varying vec3 vN; void main(){gl_Position=uMVP*vec4(aPos,1.0); vN=mat3(uModel)*aNormal; gl_PointSize=uSize;}";
            String fs="precision mediump float; varying vec3 vN; uniform vec4 uColor; uniform float uFlat; void main(){float diffuse=max(dot(normalize(vN),normalize(vec3(0.4,0.8,0.65))),0.0); vec3 c=uColor.rgb*(0.36+0.64*diffuse); gl_FragColor=vec4(mix(c,uColor.rgb,uFlat),uColor.a);}";
            int v=shader(GLES20.GL_VERTEX_SHADER,vs),f=shader(GLES20.GL_FRAGMENT_SHADER,fs);
            program=GLES20.glCreateProgram();GLES20.glAttachShader(program,v);GLES20.glAttachShader(program,f);GLES20.glLinkProgram(program);
            int[] status=new int[1];GLES20.glGetProgramiv(program,GLES20.GL_LINK_STATUS,status,0);
            if(status[0]==0)throw new IllegalStateException("OpenGL link: "+GLES20.glGetProgramInfoLog(program));
            GLES20.glDeleteShader(v);GLES20.glDeleteShader(f);
            aPos=GLES20.glGetAttribLocation(program,"aPos");aNormal=GLES20.glGetAttribLocation(program,"aNormal");
            uMVP=GLES20.glGetUniformLocation(program,"uMVP");uModel=GLES20.glGetUniformLocation(program,"uModel");
            uColor=GLES20.glGetUniformLocation(program,"uColor");uFlat=GLES20.glGetUniformLocation(program,"uFlat");
            uSize=GLES20.glGetUniformLocation(program,"uSize");
        }
        private static int shader(int type,String source){
            int id=GLES20.glCreateShader(type);GLES20.glShaderSource(id,source);GLES20.glCompileShader(id);
            int[] status=new int[1];GLES20.glGetShaderiv(id,GLES20.GL_COMPILE_STATUS,status,0);
            if(status[0]==0)throw new IllegalStateException("OpenGL shader: "+GLES20.glGetShaderInfoLog(id));
            return id;
        }
        @Override public void onSurfaceChanged(GL10 gl,int w,int h){
            width=Math.max(1,w);height=Math.max(1,h);GLES20.glViewport(0,0,width,height);
        }
        /** Stable orbital camera; PAN moves in camera plane and LOOK rotates in place. */
        void orbitPixels(float dx,float dy){
            yaw+=dx*0.007f;
            pitch=clamp(pitch+dy*0.007f,-1.48f,1.48f);
        }
        float[] eye(){
            float cp=(float)Math.cos(pitch);
            return new float[]{target[0]+distance*cp*(float)Math.sin(yaw),
                target[1]+distance*(float)Math.sin(pitch),
                target[2]+distance*cp*(float)Math.cos(yaw)};
        }
        float[] right(){return new float[]{(float)Math.cos(yaw),0,-(float)Math.sin(yaw)};}
        float[] up(){
            return new float[]{-(float)(Math.sin(yaw)*Math.sin(pitch)),
                (float)Math.cos(pitch),
                -(float)(Math.cos(yaw)*Math.sin(pitch))};
        }
        float[] screenDeltaWorld(float dx,float dy){
            float s=(float)(2*Math.tan(Math.toRadians(25))*distance/Math.max(1,height));
            float[] r=right(),u=up();
            return new float[]{(r[0]*dx-u[0]*dy)*s,(r[1]*dx-u[1]*dy)*s,(r[2]*dx-u[2]*dy)*s};
        }
        void panPixels(float dx,float dy){
            float[] shift=screenDeltaWorld(dx,dy),current=target;
            target=new float[]{current[0]-shift[0],current[1]-shift[1],current[2]-shift[2]};
        }
        void lookPixels(float dx,float dy){
            float[] location=eye();
            yaw-=dx*0.007f;
            pitch=clamp(pitch-dy*0.007f,-1.48f,1.48f);
            float cp=(float)Math.cos(pitch);
            target=new float[]{location[0]-distance*cp*(float)Math.sin(yaw),
                location[1]-distance*(float)Math.sin(pitch),
                location[2]-distance*cp*(float)Math.cos(yaw)};
        }
        private void camera() {
            Matrix.perspectiveM(proj,0,50f,(float)width/height,0.08f,350f);
            float[] location=eye(),point=target;
            Matrix.setLookAtM(view,0,location[0],location[1],location[2],
                point[0],point[1],point[2],0,1,0);
            Matrix.multiplyMM(vp,0,proj,0,view,0);
        }
        static float[] model(Scene.Part p){return ModelTransforms.matrix(p);}
        @Override public synchronized void onDrawFrame(GL10 gl){
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT|GLES20.GL_DEPTH_BUFFER_BIT);
            GLES20.glUseProgram(program);camera();drawGrid();
            synchronized(scene){
                for(int i=0;i<scene.objects.size();i++){
                    Scene.Part part=scene.objects.get(i);if(!part.visible)continue;
                    float[] transform=model(part);
                    drawPart(part,transform,i==scene.selected);
                }
            }
            // The orbit pivot stays visible even when it is outside the selected mesh.
            GLES20.glDisable(GLES20.GL_DEPTH_TEST);
            float[] p=target;float size=Math.max(.035f,distance*.010f);
            drawLines(new float[]{p[0]-size,p[1],p[2],p[0]+size,p[1],p[2],
                p[0],p[1]-size,p[2],p[0],p[1]+size,p[2],
                p[0],p[1],p[2]-size,p[0],p[1],p[2]+size},identity(),0xFFD9FB74,2f);
            GLES20.glEnable(GLES20.GL_DEPTH_TEST);
        }
        private void drawGrid(){
            ArrayList<Float> lines=new ArrayList<>(),axis=new ArrayList<>();
            for(int i=-12;i<=12;i++){
                if(i==0)continue;
                put(lines,i,0,-12,i,0,12);put(lines,-12,0,i,12,0,i);
            }
            drawLines(toArray(lines),identity(),0xFF343B45,1.0f);
            drawLines(new float[]{-12,0,0,12,0,0},identity(),0xFF8B5353,1.8f);
            drawLines(new float[]{0,0,-12,0,0,12},identity(),0xFF547D98,1.8f);
            drawLines(new float[]{0,-2,0,0,5,0},identity(),0xFF648A6F,1.8f);
        }
        private static void put(ArrayList<Float>a,float...vs){for(float f:vs)a.add(f);}
        private static float[] toArray(ArrayList<Float> a){float[] out=new float[a.size()];for(int i=0;i<out.length;i++)out[i]=a.get(i);return out;}
        private static float[] identity(){float[] m=new float[16];Matrix.setIdentityM(m,0);return m;}
        private static FloatBuffer buffer(float[] v){
            FloatBuffer b=ByteBuffer.allocateDirect(v.length*4).order(ByteOrder.nativeOrder()).asFloatBuffer();
            b.put(v).position(0);return b;
        }
        private void uniforms(float[] transform,int color,boolean flat,float pointSize){
            Matrix.multiplyMM(mvp,0,vp,0,transform,0);
            GLES20.glUniformMatrix4fv(uMVP,1,false,mvp,0);GLES20.glUniformMatrix4fv(uModel,1,false,transform,0);
            GLES20.glUniform4f(uColor,((color>>16)&255)/255f,((color>>8)&255)/255f,(color&255)/255f,((color>>>24)&255)/255f);
            GLES20.glUniform1f(uFlat,flat?1f:0f);GLES20.glUniform1f(uSize,pointSize);
        }
        private void drawLines(float[] points,float[] transform,int color,float thickness){
            if(points.length==0)return;
            uniforms(transform,color,true,2f);
            GLES20.glDisableVertexAttribArray(aNormal);GLES20.glVertexAttrib3f(aNormal,0,1,0);
            GLES20.glEnableVertexAttribArray(aPos);
            GLES20.glVertexAttribPointer(aPos,3,GLES20.GL_FLOAT,false,0,buffer(points));
            GLES20.glLineWidth(thickness);GLES20.glDrawArrays(GLES20.GL_LINES,0,points.length/3);
            GLES20.glDisableVertexAttribArray(aPos);
        }
        private void drawPart(Scene.Part p,float[] transform,boolean selected){
            Mesh mesh=p.mesh;
            ArrayList<Float> tris=new ArrayList<>(),normals=new ArrayList<>(),edges=new ArrayList<>();
            for(int[] face:mesh.faces){
                if(face.length<3)continue;
                float[] n=normal(mesh,face);
                for(int j=1;j<face.length-1;j++) {
                    for(int id:new int[]{face[0],face[j],face[j+1]}){
                        for(int k=0;k<3;k++){tris.add(mesh.vertices[3*id+k]);normals.add(n[k]);}
                    }
                }
                for(int j=0;j<face.length;j++){
                    int a=face[j],b=face[(j+1)%face.length];
                    for(int k=0;k<3;k++)edges.add(mesh.vertices[a*3+k]);
                    for(int k=0;k<3;k++)edges.add(mesh.vertices[b*3+k]);
                }
            }
            float[] v=toArray(tris),n=toArray(normals);
            if(v.length>0){
                uniforms(transform,p.color,false,1);
                GLES20.glEnableVertexAttribArray(aPos);GLES20.glEnableVertexAttribArray(aNormal);
                GLES20.glVertexAttribPointer(aPos,3,GLES20.GL_FLOAT,false,0,buffer(v));
                GLES20.glVertexAttribPointer(aNormal,3,GLES20.GL_FLOAT,false,0,buffer(n));
                GLES20.glDrawArrays(GLES20.GL_TRIANGLES,0,v.length/3);
                GLES20.glDisableVertexAttribArray(aPos);GLES20.glDisableVertexAttribArray(aNormal);
            }
            // Wire overlay makes polygonal topology apparent without requiring edit mode.
            drawLines(toArray(edges),transform,selected?0xFFD9FB74:0xFF647382,selected?2f:1f);
            if(selected&&scene.mode==Scene.Mode.FACE&&scene.selectedFace>=0&&scene.selectedFace<mesh.faces.size()){
                int[] f=mesh.faces.get(scene.selectedFace);ArrayList<Float> highlight=new ArrayList<>();
                for(int j=0;j<f.length;j++){
                    int a=f[j],b=f[(j+1)%f.length];
                    for(int k=0;k<3;k++)highlight.add(mesh.vertices[a*3+k]);
                    for(int k=0;k<3;k++)highlight.add(mesh.vertices[b*3+k]);
                }
                GLES20.glDisable(GLES20.GL_DEPTH_TEST);
                drawLines(toArray(highlight),transform,0xFFFFB878,4f);
                GLES20.glEnable(GLES20.GL_DEPTH_TEST);
            }
            if(selected&&scene.mode==Scene.Mode.VERTEX){
                uniforms(transform,0xFFE4E8ED,true,9f);
                GLES20.glDisableVertexAttribArray(aNormal);GLES20.glVertexAttrib3f(aNormal,0,1,0);
                GLES20.glEnableVertexAttribArray(aPos);
                GLES20.glVertexAttribPointer(aPos,3,GLES20.GL_FLOAT,false,0,buffer(mesh.vertices));
                GLES20.glDrawArrays(GLES20.GL_POINTS,0,mesh.vertexCount());
                if(scene.selectedVertex>=0&&scene.selectedVertex<mesh.vertexCount()){
                    int id=scene.selectedVertex;float[] point={mesh.vertices[3*id],mesh.vertices[3*id+1],mesh.vertices[3*id+2]};
                    uniforms(transform,0xFFFFB878,true,17f);
                    GLES20.glVertexAttribPointer(aPos,3,GLES20.GL_FLOAT,false,0,buffer(point));
                    GLES20.glDrawArrays(GLES20.GL_POINTS,0,1);
                }
                GLES20.glDisableVertexAttribArray(aPos);
            }
        }
        private static float[] normal(Mesh mesh,int[] f){
            float[] v=mesh.vertices;
            float ax=v[3*f[1]]-v[3*f[0]],ay=v[3*f[1]+1]-v[3*f[0]+1],az=v[3*f[1]+2]-v[3*f[0]+2];
            float bx=v[3*f[2]]-v[3*f[0]],by=v[3*f[2]+1]-v[3*f[0]+1],bz=v[3*f[2]+2]-v[3*f[0]+2];
            float x=ay*bz-az*by,y=az*bx-ax*bz,z=ax*by-ay*bx;
            float l=(float)Math.sqrt(x*x+y*y+z*z);
            return l<1e-6f?new float[]{0,1,0}:new float[]{x/l,y/l,z/l};
        }
        private float[] unproject(float x,float y,float depth){
            float[] inv=new float[16],in={(2*x/width)-1,1-(2*y/height),depth,1},out=new float[4];
            if(!Matrix.invertM(inv,0,vp,0))return null;
            Matrix.multiplyMV(out,0,inv,0,in,0);
            if(Math.abs(out[3])<1e-9)return null;
            return new float[]{out[0]/out[3],out[1]/out[3],out[2]/out[3]};
        }
        /** Pick an exact point on the visible polygon, or the floor when no mesh was hit. */
        public synchronized float[] pickAnchor(float x,float y){
            camera();float[] a=unproject(x,y,-1),b=unproject(x,y,1);
            if(a==null||b==null)return null;
            float[] dir=sub(b,a);float length=(float)Math.sqrt(dot(dir,dir));
            if(length<1e-7f)return null;
            for(int k=0;k<3;k++)dir[k]/=length;
            float best=Float.POSITIVE_INFINITY;
            synchronized(scene){
                for(Scene.Part part:scene.objects){
                    if(!part.visible)continue;
                    float[] m=model(part),vs=part.mesh.vertices;
                    for(int[] face:part.mesh.faces){
                        for(int j=1;j<face.length-1;j++){
                            float hit=intersect(a,dir,point(m,vs,face[0]),point(m,vs,face[j]),point(m,vs,face[j+1]));
                            if(hit>0&&hit<best)best=hit;
                        }
                    }
                }
            }
            if(best==Float.POSITIVE_INFINITY&&Math.abs(dir[1])>0.00001f){
                float floor=(0-a[1])/dir[1];
                if(floor>0)best=floor;
            }
            if(best==Float.POSITIVE_INFINITY)return unproject(x,y,0);
            return new float[]{a[0]+best*dir[0],a[1]+best*dir[1],a[2]+best*dir[2]};
        }
        /** [object index, face index] for closest intersecting triangle. */
        public synchronized int[] pickFace(float x,float y){
            camera();float[] a=unproject(x,y,-1),b=unproject(x,y,1);
            if(a==null||b==null)return null;
            float[] dir={b[0]-a[0],b[1]-a[1],b[2]-a[2]};
            float len=(float)Math.sqrt(dot(dir,dir));for(int k=0;k<3;k++)dir[k]/=len;
            int[] best=null;float nearest=Float.POSITIVE_INFINITY;
            synchronized(scene) {
                for(int oi=0;oi<scene.objects.size();oi++){
                    Scene.Part p=scene.objects.get(oi);if(!p.visible)continue;
                    float[] m=model(p);float[] vertices=p.mesh.vertices;
                    for(int fi=0;fi<p.mesh.faces.size();fi++){
                        int[] f=p.mesh.faces.get(fi);
                        for(int k=1;k<f.length-1;k++){
                            float hit=intersect(a,dir,point(m,vertices,f[0]),point(m,vertices,f[k]),point(m,vertices,f[k+1]));
                            if(hit>0.01f&&hit<nearest){nearest=hit;best=new int[]{oi,fi};}
                        }
                    }
                }
            }
            return best;
        }
        public synchronized int pickVertex(float x,float y,float radius){
            camera();Scene.Part p=scene.current();if(p==null||!p.visible)return -1;
            float[] m=model(p),combined=new float[16];Matrix.multiplyMM(combined,0,vp,0,m,0);
            int best=-1;float closest=radius*radius;float[] in=new float[4],out=new float[4];
            for(int i=0;i<p.mesh.vertexCount();i++){
                in[0]=p.mesh.vertices[3*i];in[1]=p.mesh.vertices[3*i+1];in[2]=p.mesh.vertices[3*i+2];in[3]=1;
                Matrix.multiplyMV(out,0,combined,0,in,0);
                if(out[3]<=0)continue;
                float sx=(out[0]/out[3]+1)*width/2,sy=(1-out[1]/out[3])*height/2;
                float d=(sx-x)*(sx-x)+(sy-y)*(sy-y);
                if(d<closest){closest=d;best=i;}
            }
            return best;
        }
        private static float[] point(float[] m,float[] v,int id){
            float[] in={v[3*id],v[3*id+1],v[3*id+2],1},out=new float[4];
            Matrix.multiplyMV(out,0,m,0,in,0);return new float[]{out[0],out[1],out[2]};
        }
        private static float dot(float[] a,float[] b){return a[0]*b[0]+a[1]*b[1]+a[2]*b[2];}
        private static float[] sub(float[] a,float[] b){return new float[]{a[0]-b[0],a[1]-b[1],a[2]-b[2]};}
        private static float[] cross(float[] a,float[] b){return new float[]{a[1]*b[2]-a[2]*b[1],a[2]*b[0]-a[0]*b[2],a[0]*b[1]-a[1]*b[0]};}
        private static float intersect(float[] o,float[] dir,float[] v0,float[] v1,float[] v2){
            float[] e1=sub(v1,v0),e2=sub(v2,v0),h=cross(dir,e2);
            float a=dot(e1,h);if(Math.abs(a)<1e-7f)return -1;
            float f=1/a;float[] s=sub(o,v0);float u=f*dot(s,h);if(u<0||u>1)return -1;
            float[] q=cross(s,e1);float v=f*dot(dir,q);if(v<0||u+v>1)return -1;
            return f*dot(e2,q);
        }
    }
}
