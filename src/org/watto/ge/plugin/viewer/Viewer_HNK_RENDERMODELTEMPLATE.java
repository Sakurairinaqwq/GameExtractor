/*
 * Application:  Game Extractor
 * Author:       wattostudios
 * Website:      http://www.watto.org
 * Copyright:    Copyright (c) 2002-2025 wattostudios
 *
 * License Information:
 * This program is free software; you can redistribute it and/or modify it under the terms of the GNU General Public License
 * published by the Free Software Foundation; either version 2 of the License, or (at your option) any later versions. This
 * program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranties
 * of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License at http://www.gnu.org for more
 * details. For further information on this application, refer to the authors' website.
 */

package org.watto.ge.plugin.viewer;

import java.awt.Image;

import org.watto.ErrorLogger;
import org.watto.component.PreviewPanel;
import org.watto.component.PreviewPanel_3DModel;
import org.watto.datatype.Archive;
import org.watto.datatype.ImageResource;
import org.watto.ge.helper.FieldValidator;
import org.watto.ge.plugin.AllFilesPlugin;
import org.watto.ge.plugin.ArchivePlugin;
import org.watto.ge.plugin.ViewerPlugin;
import org.watto.ge.plugin.archive.Plugin_HNK;
import org.watto.io.FileManipulator;
import org.watto.io.converter.ShortConverter;

import javafx.geometry.Point3D;
import javafx.scene.shape.TriangleMesh;

/**
**********************************************************************************************

**********************************************************************************************
**/
public class Viewer_HNK_RENDERMODELTEMPLATE extends ViewerPlugin {

  /**
  **********************************************************************************************
  
  **********************************************************************************************
  **/
  public Viewer_HNK_RENDERMODELTEMPLATE() {
    super("HNK_RENDERMODELTEMPLATE", "HNK_RENDERMODELTEMPLATE Model");
    setExtensions("rendermodeltemplate");

    setGames("Scooby-Doo! First Frights");
    setPlatforms("PC");
    setStandardFileFormat(false);
  }

  /**
  **********************************************************************************************
  
  **********************************************************************************************
  **/
  @Override
  public boolean canWrite(PreviewPanel panel) {
    return false;
  }

  /**
  **********************************************************************************************
  
  **********************************************************************************************
  **/
  @Override
  public int getMatchRating(FileManipulator fm) {
    try {

      int rating = 0;

      ArchivePlugin plugin = Archive.getReadPlugin();
      if (plugin instanceof Plugin_HNK) {
        rating += 50;
      }
      else if (!(plugin instanceof AllFilesPlugin)) {
        return 0;
      }

      if (FieldValidator.checkExtension(fm, extensions)) {
        rating += 25;
      }
      else {
        return 0;
      }

      fm.skip(18);

      if (fm.readString(19).equals("RenderModelTemplate")) {
        rating += 50;
      }
      else {
        rating = 0;
      }

      return rating;

    }
    catch (

    Throwable t) {
      return 0;
    }
  }

  float minX = 20000f;

  float maxX = -20000f;

  float minY = 20000f;

  float maxY = -20000f;

  float minZ = 20000f;

  float maxZ = -20000f;

  /**
  **********************************************************************************************
  
  **********************************************************************************************
  **/
  @Override
  public PreviewPanel read(FileManipulator fm) {

    try {

      long arcSize = fm.getLength();

      // Read in the model

      // Set up the mesh
      TriangleMesh triangleMesh = new TriangleMesh();

      float[] points = null;
      float[] normals = null;
      float[] texCoords = null;
      int[] faces = null;

      minX = 20000f;
      maxX = -20000f;
      minY = 20000f;
      maxY = -20000f;
      minZ = 20000f;
      maxZ = -20000f;

      int vertexEntryLength = 36;

      while (fm.getOffset() < arcSize) {
        // 4 - Block Length (not including these headers)
        int blockLength = fm.readInt();
        FieldValidator.checkLength(blockLength, arcSize);

        // 2 - Block Type A
        short blockTypeA = fm.readShort();

        // 2 - Block Type B
        short blockTypeB = fm.readShort();

        // X - Block Data
        //System.out.println("Block " + blockTypeA + ":" + blockTypeB + " at offset " + (fm.getOffset() - 8) + " for length " + blockLength);

        if (blockTypeA == 4176 && blockTypeB == 16) {
          // METADATA
          long startOffset = fm.getOffset();

          // 32 - Unknown
          fm.skip(32);

          // 4 - Offset to FacesBlockLength field (relative to the start of this Block Data)
          int metadataOffset = fm.readInt() - 36; // as we're going to skip it, and we've read 36 bytes already
          FieldValidator.checkOffset(metadataOffset, blockLength);

          // 36 - Unknown

          // for each (8)
          //   4 - Unknown Float

          // 76 - Unknown
          fm.skip(metadataOffset);

          // 4 - Faces Block Length
          // 4 - Vertices Block Length
          fm.skip(8);

          // 4 - Length of each Vertex Entry (36/40)
          vertexEntryLength = fm.readInt();
          FieldValidator.checkRange(vertexEntryLength, 36, 60); // guess

          // X - Unknown
          fm.skip(blockLength - (fm.getOffset() - startOffset));
        }
        else if (blockTypeA == 84 && blockTypeB == 4) {
          // VERTICES
          if (points != null) {
            // found another part - skip it for now
            //System.out.println("Model has multiple parts");
            fm.skip(blockLength);
            continue;
          }

          int numVertices = blockLength / vertexEntryLength;

          int skipLength = vertexEntryLength - 36;

          int numVertices3 = numVertices * 3;
          points = new float[numVertices3];
          normals = new float[numVertices3];

          int numPoints2 = numVertices * 2;
          texCoords = new float[numPoints2];

          for (int i = 0, j = 0, k = 0; i < numVertices; i++, j += 3, k += 2) {
            // 4 - Vertex X
            // 4 - Vertex Y
            // 4 - Vertex Z
            float xPoint = fm.readFloat();
            float yPoint = fm.readFloat();
            float zPoint = fm.readFloat();

            points[j] = xPoint;
            points[j + 1] = yPoint;
            points[j + 2] = zPoint;

            // 4 - Normal X
            // 4 - Normal Y
            // 4 - Normal Z
            float xNormal = fm.readFloat();
            float yNormal = fm.readFloat();
            float zNormal = fm.readFloat();

            normals[j] = xNormal;
            normals[j + 1] = yNormal;
            normals[j + 2] = zNormal;

            // 4 - Unknown (-1)
            fm.skip(4);

            // 4 - Texture U (float)
            // 4 - Texture V (float)
            float uTexture = fm.readFloat();
            float vTexture = fm.readFloat();

            texCoords[k] = uTexture;
            texCoords[k + 1] = vTexture;

            // X - Extra Data (varying length)
            fm.skip(skipLength);

            // Calculate the size of the object
            if (xPoint < minX) {
              minX = xPoint;
            }
            if (xPoint > maxX) {
              maxX = xPoint;
            }

            if (yPoint < minY) {
              minY = yPoint;
            }
            if (yPoint > maxY) {
              maxY = yPoint;
            }

            if (zPoint < minZ) {
              minZ = zPoint;
            }
            if (zPoint > maxZ) {
              maxZ = zPoint;
            }
          }

        }
        else if (blockTypeA == 85 && blockTypeB == 2) {
          // FACES
          if (faces != null) {
            // found another part - skip it for now
            //System.out.println("Model has multiple parts");
            fm.skip(blockLength);
            continue;
          }

          int numFaces = blockLength / 2;

          int numFaces3 = numFaces;
          FieldValidator.checkNumFaces(numFaces3);

          numFaces = numFaces3 / 3;
          int numFaces6 = numFaces3 * 2;

          faces = new int[numFaces6]; // need to store front and back faces

          for (int i = 0, j = 0; i < numFaces; i++, j += 6) {
            // 2 - Point Index 1
            // 2 - Point Index 2
            // 2 - Point Index 3
            int facePoint1 = (ShortConverter.unsign(fm.readShort()));
            int facePoint2 = (ShortConverter.unsign(fm.readShort()));
            int facePoint3 = (ShortConverter.unsign(fm.readShort()));

            // reverse face first (so the light shines properly, for this model specifically)
            faces[j] = facePoint3;
            faces[j + 1] = facePoint2;
            faces[j + 2] = facePoint1;

            // forward face second
            faces[j + 3] = facePoint1;
            faces[j + 4] = facePoint2;
            faces[j + 5] = facePoint3;

          }
        }
        else {
          // anything else
          fm.skip(blockLength);
        }
      }

      // add the part to the model
      if (faces != null && points != null && normals != null && texCoords != null) {
        // we have a full mesh for a single object - add it to the model
        triangleMesh.getTexCoords().addAll(texCoords);

        triangleMesh.getPoints().addAll(points);
        triangleMesh.getFaces().addAll(faces);
        triangleMesh.getNormals().addAll(normals);

        faces = null;
        points = null;
        normals = null;
        texCoords = null;
      }

      // calculate the sizes and centers
      float diffX = (maxX - minX);
      float diffY = (maxY - minY);
      float diffZ = (maxZ - minZ);

      float centerX = minX + (diffX / 2);
      float centerY = minY + (diffY / 2);
      float centerZ = minZ + (diffZ / 2);

      Point3D sizes = new Point3D(diffX, diffY, diffZ);
      Point3D center = new Point3D(centerX, centerY, centerZ);

      PreviewPanel_3DModel preview = new PreviewPanel_3DModel(triangleMesh, sizes, center);

      return preview;
    }
    catch (

    Throwable t) {
      ErrorLogger.log(t);
      return null;
    }

  }

  /**
  **********************************************************************************************
  
  **********************************************************************************************
  **/
  @Override
  public void write(PreviewPanel panel, FileManipulator destination) {
  }

  /**
  **********************************************************************************************
  
  **********************************************************************************************
  **/
  public ImageResource readThumbnail(FileManipulator source) {
    try {
      PreviewPanel preview = read(source);
      if (preview == null || !(preview instanceof PreviewPanel_3DModel)) {
        return null;
      }

      PreviewPanel_3DModel preview3D = (PreviewPanel_3DModel) preview;

      // generate a thumbnail-sized snapshot
      int thumbnailSize = 150; // bigger than ImageResource, so it is shrunk (and smoothed as a result)
      preview3D.generateSnapshot(thumbnailSize, thumbnailSize);

      Image image = preview3D.getImage();
      if (image != null) {
        ImageResource resource = new ImageResource(image, preview3D.getImageWidth(), preview3D.getImageHeight());
        preview3D.onCloseRequest(); // cleanup memory
        return resource;
      }

      preview3D.onCloseRequest(); // cleanup memory

      return null;
    }
    catch (Throwable t) {
      ErrorLogger.log(t);
      return null;
    }
  }

}