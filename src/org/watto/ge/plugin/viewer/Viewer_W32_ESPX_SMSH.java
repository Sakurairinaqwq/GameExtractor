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

import java.io.File;

import org.watto.ErrorLogger;
import org.watto.SingletonManager;
import org.watto.component.PreviewPanel;
import org.watto.component.PreviewPanel_3DModel;
import org.watto.datatype.Archive;
import org.watto.datatype.ImageResource;
import org.watto.datatype.Resource;
import org.watto.ge.helper.FieldValidator;
import org.watto.ge.plugin.ArchivePlugin;
import org.watto.ge.plugin.ViewerPlugin;
import org.watto.ge.plugin.archive.Plugin_W32_ESPX;
import org.watto.io.FileManipulator;
import org.watto.io.converter.ShortConverter;

import javafx.geometry.Point3D;
import javafx.scene.shape.MeshView;
import javafx.scene.shape.TriangleMesh;

/**
**********************************************************************************************

**********************************************************************************************
**/
public class Viewer_W32_ESPX_SMSH extends ViewerPlugin {

  /**
  **********************************************************************************************
  
  **********************************************************************************************
  **/
  public Viewer_W32_ESPX_SMSH() {
    super("W32_ESPX_SMSH", "W32_ESPX_SMSH Model");
    setExtensions("smsh");

    setGames("Psi-Ops: The Mindgate Conspiracy");
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

      ArchivePlugin readPlugin = Archive.getReadPlugin();
      if (readPlugin instanceof Plugin_W32_ESPX) {
        rating += 50;
      }

      if (FieldValidator.checkExtension(fm, extensions)) {
        rating += 25;
      }
      else {
        return 0;
      }

      fm.skip(28);

      // 4 - Number of Parts
      if (FieldValidator.checkRange(fm.readInt(), 1, 255)) {// guess
        rating += 5;
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

      // Get the offset of the current resource in the Archive, as the offsets are relative to the archive, not to this specific file.
      Object resourceObject = SingletonManager.get("CurrentResource");
      if (resourceObject == null || !(resourceObject instanceof Resource)) {
        return null;
      }
      Resource resource = (Resource) resourceObject;

      int resourceOffset = (int) resource.getOffset();

      // Read in the model

      // Set up the mesh
      //TriangleMesh triangleMesh = new TriangleMesh();
      MeshView[] meshView = null; // we're using MeshView, as we have multiple parts

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

      // 24 - Bounding Box
      // 4 - Material Directory Offset
      fm.skip(28);

      // 4 - Number of Parts
      int numGroups = fm.readInt();
      FieldValidator.checkRange(numGroups, 1, 255);//guess

      // 4 - Part Directory Offset
      int groupsOffset = fm.readInt() - resourceOffset;
      FieldValidator.checkOffset(groupsOffset, arcSize);

      // 4 - Skeleton Offset
      // 4 - Unknown
      fm.seek(groupsOffset);

      int[] entryLengthsArray = new int[numGroups];
      int[] numFacesArray = new int[numGroups];
      int[] numVerticesArray = new int[numGroups];
      int[] verticesOffsetArray = new int[numGroups];
      int[] facesOffsetArray = new int[numGroups];
      for (int g = 0; g < numGroups; g++) {
        // 4 - null
        fm.skip(4);

        // 4 - Vertex Entry Length (56)
        int entryLength = fm.readInt();
        FieldValidator.checkRange(entryLength, 12, 100);//guess
        entryLengthsArray[g] = entryLength;

        if (entryLength < 56) {
          return null;
        }

        // 4 - Material Index
        // 4 - Unknown
        fm.skip(8);

        // 4 - Face Index Count
        int numFaces = fm.readInt() - 2; // this is a triangle strip, so the faces start from index 2
        FieldValidator.checkNumFaces(numFaces);
        numFacesArray[g] = numFaces;

        // 4 - Vertex Buffer Size (numEntries = ThisField/VertexEntryLength)
        int numVertices = fm.readInt() / entryLength;
        FieldValidator.checkNumVertices(numVertices);
        numVerticesArray[g] = numVertices;

        // 4 - Vertex Buffer Directory Offset (for this entry)
        int vertexOffset = fm.readInt() - resourceOffset;
        FieldValidator.checkOffset(vertexOffset, arcSize);
        verticesOffsetArray[g] = vertexOffset;

        // 4 - Face Index Offset
        int facesOffset = fm.readInt() - resourceOffset;
        FieldValidator.checkOffset(facesOffset, arcSize);
        facesOffsetArray[g] = facesOffset;

        // 4 - Unknown
        // 4 - Part Bone Index
        // 4 - Part Bone Index Offset
        fm.skip(12);
      }

      File archiveFile = Archive.getBasePath();
      long archiveFileLength = archiveFile.length();

      // get the offset of the Vertex file in the Archive
      for (int g = 0; g < numGroups; g++) {
        fm.seek(verticesOffsetArray[g]);
        // 4 - Unknown
        // 4 - Unknown
        // 4 - Unknown
        fm.skip(12);

        // 4 - Vertex Buffer Offset
        int vertexOffset = fm.readInt(); // THIS OFFSET POINTS TO A DIFFERENT FILE IN THE ARCHIVE, NOT IN THIS SPECIFIC FILE
        FieldValidator.checkOffset(vertexOffset, archiveFileLength);
        verticesOffsetArray[g] = vertexOffset;
      }

      // Open the Archive File and use that for reading the vertex data
      FileManipulator archiveFM = new FileManipulator(archiveFile, false);

      // Prepare the MeshView to hold all the parts
      meshView = new MeshView[numGroups];

      for (int g = 0; g < numGroups; g++) {
        //
        //
        // VERTICES (IN THE ARCHIVE, NOT IN THIS SPECIFIC FILE)
        //
        //
        archiveFM.seek(verticesOffsetArray[g]);

        int numVertices = numVerticesArray[g];
        int vertexEntryLength = entryLengthsArray[g];

        int numVertices3 = numVertices * 3;
        points = new float[numVertices3];
        normals = new float[numVertices3];

        int numPoints2 = numVertices * 2;
        texCoords = new float[numPoints2];

        int extraSize = vertexEntryLength - 56;
        FieldValidator.checkPositive(extraSize);

        for (int i = 0, j = 0, k = 0; i < numVertices; i++, j += 3, k += 2) {
          // 4 - Vertex X
          // 4 - Vertex Y
          // 4 - Vertex Z
          float xPoint = archiveFM.readFloat();
          float yPoint = archiveFM.readFloat();
          float zPoint = archiveFM.readFloat();

          points[j] = xPoint;
          points[j + 1] = yPoint;
          points[j + 2] = zPoint;

          // 4 - Normal X
          // 4 - Normal Y
          // 4 - Normal Z
          float xNormal = archiveFM.readFloat();
          float yNormal = archiveFM.readFloat();
          float zNormal = archiveFM.readFloat();

          normals[j] = xNormal;
          normals[j + 1] = yNormal;
          normals[j + 2] = zNormal;

          // 4 - Color Buffer Offset 1
          // 4 - Color Buffer Offset 2
          // 4 - Color Buffer Offset 3
          // 4 - Color Buffer Offset 4
          // 4 - Unknown
          // 4 - Unknown
          archiveFM.skip(24);

          // 4 - Texture U (float)
          // 4 - Texture V (float)
          float xTexture = archiveFM.readFloat();
          float yTexture = archiveFM.readFloat();

          texCoords[k] = xTexture;
          texCoords[k + 1] = yTexture;

          // X - Extra data
          archiveFM.skip(extraSize);

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

        //
        //
        // FACES (IN THIS FILE)
        //
        //

        fm.seek(facesOffsetArray[g]);

        int numFaces = numFacesArray[g];

        int numFaces3 = numFaces * 3;
        FieldValidator.checkNumFaces(numFaces3);

        int numFaces6 = numFaces3 * 2;
        faces = new int[numFaces6]; // need to store front and back faces

        int firstPoint = (ShortConverter.unsign(fm.readShort()));
        int secondPoint = (ShortConverter.unsign(fm.readShort()));

        boolean swap = true; // in a triangle strip, every second triangle needs to swap points 2 and 3
        for (int i = 0, j = 0; i < numFaces; i++, j += 6) {
          // 2 - Point Index 1
          // 2 - Point Index 2
          // 2 - Point Index 3
          int facePoint1 = firstPoint;
          int facePoint2 = secondPoint;
          int facePoint3 = (ShortConverter.unsign(fm.readShort()));

          if (swap) {
            // reverse face first (so the light shines properly, for this model specifically)
            faces[j] = facePoint3;
            faces[j + 1] = facePoint2;
            faces[j + 2] = facePoint1;

            // forward face second
            faces[j + 3] = facePoint1;
            faces[j + 4] = facePoint2;
            faces[j + 5] = facePoint3;
          }
          else {
            // reverse face first (so the light shines properly, for this model specifically)
            faces[j] = facePoint2;
            faces[j + 1] = facePoint3;
            faces[j + 2] = facePoint1;

            // forward face second
            faces[j + 3] = facePoint1;
            faces[j + 4] = facePoint3;
            faces[j + 5] = facePoint2;
          }

          swap = !swap;

          // remember the last 2 points, as they form the first 2 points of the next triangle
          firstPoint = secondPoint;
          secondPoint = facePoint3;
        }

        // add the part to the model
        if (faces != null && points != null && normals != null && texCoords != null) {
          // we have a full mesh for a single object - add it to the model
          TriangleMesh triangleMesh = new TriangleMesh();
          triangleMesh.getTexCoords().addAll(texCoords);

          triangleMesh.getPoints().addAll(points);
          triangleMesh.getFaces().addAll(faces);
          triangleMesh.getNormals().addAll(normals);

          faces = null;
          points = null;
          normals = null;
          texCoords = null;

          // Create the MeshView
          MeshView view = new MeshView(triangleMesh);

          meshView[g] = view;
        }

      } // now loop back and read the next part

      archiveFM.close();

      // calculate the sizes and centers
      float diffX = (maxX - minX);
      float diffY = (maxY - minY);
      float diffZ = (maxZ - minZ);

      float centerX = minX + (diffX / 2);
      float centerY = minY + (diffY / 2);
      float centerZ = minZ + (diffZ / 2);

      Point3D sizes = new Point3D(diffX, diffY, diffZ);
      Point3D center = new Point3D(centerX, centerY, centerZ);

      PreviewPanel_3DModel preview = new PreviewPanel_3DModel(meshView, sizes, center);

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

      java.awt.Image image = preview3D.getImage();
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