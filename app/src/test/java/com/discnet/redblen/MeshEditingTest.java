package com.discnet.redblen;

import org.junit.Test;
import java.util.Collections;
import static org.junit.Assert.*;

public final class MeshEditingTest {
    @Test public void multiaxisMirrorMovesPairedVerticesInOppositeDirections() {
        Mesh mesh=Mesh.cube();
        int affected=MeshEditing.moveSelection(mesh,Collections.singleton(0),
            new float[]{.25f,.1f,0},1,1);
        assertEquals(2,affected);
        assertEquals(-.75f,mesh.vertices[0],1e-5f);
        assertEquals(.75f,mesh.vertices[3],1e-5f);
        assertEquals(-.9f,mesh.vertices[1],1e-5f);
        assertEquals(-.9f,mesh.vertices[4],1e-5f);
    }

    @Test public void mirroredPlaneSeamRemainsLocked() {
        Mesh mesh=Mesh.plane();
        // Add connected center in face, then test symmetry seam stays on X=0.
        int id=MeshEditing.insertOnFace(mesh,0,new float[]{0,0,0});
        assertTrue(id>=0);
        MeshEditing.moveSelection(mesh,Collections.singleton(id),new float[]{1f,.3f,0},1,1);
        assertEquals(0f,mesh.vertices[id*3],1e-5f);
        assertEquals(.3f,mesh.vertices[id*3+1],1e-5f);
    }

    @Test public void subdivisionSharesEdgesAndMaintainsClosedCube() {
        Mesh mesh=Mesh.cube();
        assertTrue(MeshEditing.subdivide(mesh));
        assertEquals(26,mesh.vertexCount());
        assertEquals(24,mesh.faces.size());
        assertTrue(MeshEditing.subdivide(mesh));
        assertEquals(98,mesh.vertexCount());
        assertEquals(96,mesh.faces.size());
    }

    @Test public void addingVertexSplitsPolygonWithoutLooseVertices() {
        Mesh mesh=Mesh.plane();
        int id=MeshEditing.insertOnFace(mesh,0,new float[]{.1f,0,.2f});
        assertEquals(4,id);assertEquals(5,mesh.vertexCount());
        assertEquals(4,mesh.faces.size());
        for(int[] face:mesh.faces){
            assertEquals(3,face.length);
            assertEquals(id,face[2]);
        }
    }

    @Test public void mirroredSculptPullsBothSides(){
        Mesh mesh=Mesh.cube();
        assertTrue(MeshEditing.subdivide(mesh));
        assertTrue(MeshEditing.subdivide(mesh));
        int positive=-1,negative=-1;
        for(int i=0;i<mesh.vertexCount();i++){
            float x=mesh.vertices[3*i],y=mesh.vertices[3*i+1],z=mesh.vertices[3*i+2];
            if(Math.abs(x)<1e-4f&&Math.abs(y)<1e-4f){
                if(Math.abs(z-1)<1e-4f)positive=i;
                if(Math.abs(z+1)<1e-4f)negative=i;
            }
        }
        assertTrue(positive>=0&&negative>=0);
        int count=MeshEditing.sculpt(mesh,new float[]{0,0,1},new float[]{0,0,1},
            .85f,.5f,Scene.SculptBrush.PULL,4,1);
        assertTrue(count>0);
        assertTrue(mesh.vertices[3*positive+2]>1f);
        assertTrue(mesh.vertices[3*negative+2]<-1f);
    }

    @Test public void symmetryInsertsVertexOnBothCorrespondingFaces(){
        Mesh mesh=Mesh.cube();
        int[] indices=MeshEditing.insertSymmetric(mesh,0,new float[]{.2f,.1f,1},4,1);
        assertEquals(2,indices.length);
        assertEquals(10,mesh.vertexCount());
        assertEquals(0f,mesh.vertices[3*indices[0]+1]-.1f,1e-5f);
        assertEquals(-1f,mesh.vertices[3*indices[1]+2],1e-5f);
    }
}
