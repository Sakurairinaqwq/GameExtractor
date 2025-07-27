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

import org.watto.ErrorLogger;
import org.watto.SingletonManager;
import org.watto.component.PreviewPanel;
import org.watto.component.PreviewPanel_Image;
import org.watto.datatype.Archive;
import org.watto.datatype.ImageResource;
import org.watto.datatype.Resource;
import org.watto.ge.helper.FieldValidator;
import org.watto.ge.helper.ImageFormatReader;
import org.watto.ge.helper.ImageSwizzler;
import org.watto.ge.plugin.AllFilesPlugin;
import org.watto.ge.plugin.ArchivePlugin;
import org.watto.ge.plugin.ViewerPlugin;
import org.watto.ge.plugin.archive.Plugin_TEX_6;
import org.watto.io.FileManipulator;
import org.watto.io.buffer.ByteBuffer;

/**
**********************************************************************************************

**********************************************************************************************
**/
public class Viewer_TEX_6_DATA extends ViewerPlugin {

  /**
  **********************************************************************************************
  
  **********************************************************************************************
  **/
  public Viewer_TEX_6_DATA() {
    super("TEX_6_DATA", "TEX_6_DATA Image");
    setExtensions("data");

    setGames("Hitman: Blood Money");
    setPlatforms("PS2");
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
  public boolean canReplace(PreviewPanel panel) {
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
      if (plugin instanceof Plugin_TEX_6) {
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

      return rating;

    }
    catch (Throwable t) {
      return 0;
    }
  }

  /**
  **********************************************************************************************
  Reads a resource from the FileManipulator, and generates a PreviewPanel for it. The FileManipulator
  is an extracted temp file, not the original archive!
  **********************************************************************************************
  **/
  @Override
  public PreviewPanel read(FileManipulator fm) {
    try {

      ImageResource imageResource = readThumbnail(fm);

      if (imageResource == null) {
        return null;
      }

      PreviewPanel_Image preview = new PreviewPanel_Image(imageResource);

      return preview;

    }
    catch (Throwable t) {
      logError(t);
      return null;
    }
  }

  /**
  **********************************************************************************************
  Reads a resource from the FileManipulator, and generates a Thumbnail for it (generally, only
  an Image ViewerPlugin will do this, but others can do it if they want). The FileManipulator is
  an extracted temp file, not the original archive!
  **********************************************************************************************
  **/

  @Override
  public ImageResource readThumbnail(FileManipulator fm) {
    try {

      int height = 0;
      int width = 0;
      int paletteID = -1;

      // get the width/height from the properties of the image resource, which were read by the ArchivePlugin
      Object resourceObject = SingletonManager.get("CurrentResource");
      if (resourceObject == null || !(resourceObject instanceof Resource)) {
        return null;
      }
      Resource resource = (Resource) resourceObject;

      try {
        height = Integer.parseInt(resource.getProperty("Height"));
        width = Integer.parseInt(resource.getProperty("Width"));
        paletteID = Integer.parseInt(resource.getProperty("PaletteID"));
      }
      catch (Throwable t) {
        //
      }

      if (width == 0 || height == 0) {
        return null;
      }

      ImageResource imageResource = null;
      if (paletteID == -1) {
        // RGBA in this file
        imageResource = ImageFormatReader.readRGBA(fm, width, height);
      }
      else {
        // paletted, where the palette is in a separate file
        Resource paltResource = Archive.getResource(paletteID);

        // So the thumbnails play nice, need to grab all the pixel data first, before we try to open an external palette file
        byte[] pixelData = fm.readBytes((int) fm.getRemainingLength());
        fm.close();
        fm = new FileManipulator(new ByteBuffer(pixelData));

        // now we can get the palette
        int[] palette = extractPalette(paltResource);

        if (palette != null) {
          int numColors = palette.length;
          if (numColors == 256) {
            palette = ImageSwizzler.unstripePalettePS2(palette);
            imageResource = ImageFormatReader.read8BitPaletted(fm, width, height, palette);
            imageResource.setPixels(ImageSwizzler.unswizzlePS2(imageResource.getImagePixels(), width, height));
          }
          else if (numColors == 16) {

            int numBytes = width * height / 2;
            byte[] dataBytes = fm.readBytes(numBytes);// * 2);
            dataBytes = ImageSwizzler.unswizzlePS24BitSuba(dataBytes, width, height);

            int[] paletteBig = new int[256];
            System.arraycopy(palette, 0, paletteBig, 0, 16);

            FileManipulator dataFM = new FileManipulator(new ByteBuffer(dataBytes));
            imageResource = ImageFormatReader.read4BitPaletted(dataFM, width, height, paletteBig);
            dataFM.close();

          }
        }
      }

      fm.close();

      return imageResource;

    }
    catch (Throwable t) {
      logError(t);
      return null;
    }
  }

  /**
  **********************************************************************************************
  
  **********************************************************************************************
  **/
  @Override
  public void write(PreviewPanel preview, FileManipulator fm) {
  }

  /**
  **********************************************************************************************
  
  **********************************************************************************************
  **/
  public int[] extractPalette(Resource paltResource) {
    try {
      int paltLength = (int) paltResource.getLength();

      ByteBuffer buffer = new ByteBuffer(paltLength);
      FileManipulator fm = new FileManipulator(buffer);
      paltResource.extract(fm);

      fm.seek(0); // back to the beginning of the byte array

      int[] palette = null;
      if (paltLength == 64) {
        palette = ImageFormatReader.readPaletteRGBA(fm, 16);
      }
      else if (paltLength == 1024) {
        palette = ImageFormatReader.readPaletteRGBA(fm, 256);
      }
      else {
        ErrorLogger.log("[Viewer_TEX_6_DATA] Unknown Palette Format for length " + paltLength);
      }

      fm.close();

      return palette;
    }
    catch (Throwable t) {
      logError(t);
      return new int[0];
    }
  }

}