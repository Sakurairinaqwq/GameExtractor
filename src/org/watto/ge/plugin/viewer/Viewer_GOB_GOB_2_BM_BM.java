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
import org.watto.component.PreviewPanel;
import org.watto.component.PreviewPanel_Image;
import org.watto.datatype.Archive;
import org.watto.datatype.ImageResource;
import org.watto.ge.helper.FieldValidator;
import org.watto.ge.helper.ImageFormatReader;
import org.watto.ge.plugin.AllFilesPlugin;
import org.watto.ge.plugin.ArchivePlugin;
import org.watto.ge.plugin.ViewerPlugin;
import org.watto.ge.plugin.archive.Plugin_GOB_GOB_2;
import org.watto.io.FileManipulator;
import org.watto.io.buffer.ByteBuffer;

/**
**********************************************************************************************

**********************************************************************************************
**/
public class Viewer_GOB_GOB_2_BM_BM extends ViewerPlugin {

  /**
  **********************************************************************************************
  
  **********************************************************************************************
  **/
  public Viewer_GOB_GOB_2_BM_BM() {
    super("GOB_GOB_2_BM_BM", "GOB_GOB_2_BM_BM Image");
    setExtensions("bm");

    setGames("Star Wars Jedi Knight: Dark Forces 2");
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
      if (plugin instanceof Plugin_GOB_GOB_2) {
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

      // 4 - Header
      if (fm.readString(4).equals("BM  ")) {
        rating += 50;
      }
      else {
        rating = 0;
      }

      if (fm.readInt() == 70) {
        rating += 5;
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

      // 4 - Header ("BM  ")
      // 4 - Unknown (70)
      // 4 - null
      // 4 - Unknown (0/1/2)
      fm.skip(16);

      // 4 - Image Format (1=Paletted, 3=RGB)
      int imageFormat = fm.readInt();

      // 16 - null
      // 4 - Unknown (8)
      // 88 - null (to offset 128)
      fm.skip(108);

      // 4 - Image Width
      int width = fm.readInt();
      FieldValidator.checkWidth(width);

      // 4 - Image Height
      int height = fm.readInt();
      FieldValidator.checkHeight(height);

      ImageResource imageResource = null;

      if (imageFormat == 3) {
        // RGB
        imageResource = ImageFormatReader.readRGB(fm, width, height);
      }
      else if (imageFormat == 1) {
        // Paletted

        // X - Pixels
        int numPixels = width * height;
        byte[] pixels = fm.readBytes(numPixels);

        // X - Color Palette
        int[] palette = ImageFormatReader.readPaletteRGB(fm, 256);

        FileManipulator pixelFM = new FileManipulator(new ByteBuffer(pixels));
        imageResource = ImageFormatReader.read8BitPaletted(pixelFM, width, height, palette);
        pixelFM.close();
      }
      else {
        ErrorLogger.log("[Viewer_GOB_GOB_2_BM_BM] Unknown image format: " + imageFormat);
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

}