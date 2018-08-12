package processing.ar;

import processing.core.PApplet;
import processing.core.PMatrix3D;
import processing.core.PSurface;
import processing.opengl.PGL;
import processing.opengl.PGLES;
import processing.opengl.PGraphics3D;
import processing.opengl.PGraphicsOpenGL;

import static processing.ar.PSurfaceAR.mainPose;
import static processing.ar.PSurfaceAR.session;

public class PGraphicsAR extends PGraphics3D {

    PMatrix3D modelViewMatrix;
    PMatrix3D projectionMatrix;
    PMatrix3D projModelMatrix;

    public PGraphicsAR() {
    }

    @Override
    protected PGL createPGL(PGraphicsOpenGL pGraphicsOpenGL) {
        PGraphicsAR.showWarning("Graphics: Creation");
        return new PGLES(pGraphicsOpenGL);
    }

    @Override
    public void beginDraw() {
        super.beginDraw();
        updateInferences();
        PGraphicsAR.showWarning("Graphics: BeginDraw()");
    }

    @Override
    protected void backgroundImpl() {
        if (session != null) {
            PSurfaceAR.performRendering();
        }
        PGraphicsAR.showWarning("Graphics: background()");
    }

    @Override
    public void surfaceChanged() {
        PGraphicsAR.showWarning("Graphics: surfaceChanged()");
    }

    public void updateInferences(){
        setAR();
    }

    protected void setAR() {
        if (PSurfaceAR.projmtx != null && PSurfaceAR.viewmtx != null && PSurfaceAR.anchorMatrix != null) {
//        if(PSurfaceAR.viewmtx != null) {
            float[] prj = PSurfaceAR.projmtx;
            float[] view = PSurfaceAR.viewmtx;
            float[] anchor = PSurfaceAR.anchorMatrix;

            PApplet.println("Applying AR transformations");

            // ARCore are column-major, so the following indexing is correct:

            // Fist, set all matrices to identity
            resetProjection();
            resetMatrix();

            // Apply the projection matrix
            applyProjection(prj[0], prj[4], prj[8], prj[12],
                            prj[1], prj[5], prj[9], prj[13],
                            prj[2], prj[6], prj[10], prj[14],
                            prj[3], prj[7], prj[11], prj[15]);

            // make modelview = view
            applyMatrix(view[0], view[4], view[8], view[12],
                        view[1], view[5], view[9], view[13],
                        view[2], view[6], view[10], view[14],
                        view[3], view[7], view[11], view[15]);

            // now, modelview = view * anchor
            applyMatrix(anchor[0], anchor[4], anchor[8], anchor[12],
                        anchor[1], anchor[5], anchor[9], anchor[13],
                        anchor[2], anchor[6], anchor[10], anchor[14],
                        anchor[3], anchor[7], anchor[11], anchor[15]);




            /*
            // Testing row-major order, just in case...
            applyProjection(prj[0], prj[1], prj[2], prj[3],
                            prj[4], prj[5], prj[6], prj[7],
                            prj[8], prj[9], prj[10], prj[11],
                            prj[12], prj[13], prj[14], prj[15]);
            applyMatrix(view[0], view[1], view[2], view[3],
                        view[4], view[5], view[6], view[7],
                        view[8], view[9], view[10], view[11],
                        view[12], view[13], view[14], view[15]);
            applyMatrix(anchor[0], anchor[1], anchor[2], anchor[3],
                        anchor[4], anchor[5], anchor[6], anchor[7],
                        anchor[8], anchor[9], anchor[10], anchor[11],
                        anchor[12], anchor[13], anchor[14], anchor[15]);
*/

            PApplet.println("Anchor matrix: ", anchor[0], anchor[4], anchor[8], anchor[12],
                anchor[1], anchor[5], anchor[9], anchor[13],
                anchor[2], anchor[6], anchor[10], anchor[14],
                anchor[3], anchor[7], anchor[11], anchor[15]);




            /*
            modelview.set(PSurfaceAR.viewmtx[0], PSurfaceAR.viewmtx[4], PSurfaceAR.viewmtx[8], PSurfaceAR.viewmtx[12],
                    PSurfaceAR.viewmtx[1], PSurfaceAR.viewmtx[5], PSurfaceAR.viewmtx[9], PSurfaceAR.viewmtx[13],
                    PSurfaceAR.viewmtx[2], PSurfaceAR.viewmtx[6], PSurfaceAR.viewmtx[10], PSurfaceAR.viewmtx[14],
                    PSurfaceAR.viewmtx[3], PSurfaceAR.viewmtx[7], PSurfaceAR.viewmtx[11], PSurfaceAR.viewmtx[15]);

            modelViewMatrix = modelview.get();
            projModelMatrix = projmodelview.get();
            projectionMatrix = projection.get();

            float tx = -defCameraX + mainPose.tx();
            float ty = -defCameraY + mainPose.ty();
            float tz = -defCameraZ + mainPose.tz();
            modelview.translate(tx, ty, tz);

            camera.set(modelViewMatrix);
            updateProjmodelview();
*/


           /*
           virtualObject.updateModelMatrix(anchorMatrix, scaleFactor);
           updateModelMatrix(float[] modelMatrix, float scaleFactor)

        float[] scaleMatrix = new float[16];
        Matrix.setIdentityM(scaleMatrix, 0);
        scaleMatrix[0] = scaleFactor;
        scaleMatrix[5] = scaleFactor;
        scaleMatrix[10] = scaleFactor;
        Matrix.multiplyMM(this.modelMatrix, 0, modelMatrix, 0, scaleMatrix, 0);

            */

/*
            virtualObject.draw(viewmtx, projmtx, lightIntensity);
            draw(float[] cameraView, float[] cameraPerspective, float lightIntensity)
        Matrix.multiplyMM(modelViewMatrix, 0, cameraView, 0, modelMatrix, 0);
        Matrix.multiplyMM(modelViewProjectionMatrix, 0, cameraPerspective, 0, modelViewMatrix, 0);


  */

        }
//        PGraphicsAR.showWarning("Graphics: ARCamera()");
//        PGraphicsAR.showWarning("MV === "+modelViewMatrix+"\nPMM === "+projModelMatrix+"\nPM === "+projectionMatrix+"\n");
//        PGraphicsAR.showWarning("+++ "+PSurfaceAR.viewmtx);
    }
}
