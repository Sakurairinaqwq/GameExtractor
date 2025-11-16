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
import org.watto.ge.plugin.archive.Plugin_PAK_41;
import org.watto.io.FileManipulator;
import org.watto.io.converter.ByteConverter;

/**
**********************************************************************************************

**********************************************************************************************
**/
public class Viewer_PAK_41_BIT_BIT extends ViewerPlugin {

  /**
  **********************************************************************************************
  
  **********************************************************************************************
  **/
  public Viewer_PAK_41_BIT_BIT() {
    super("PAK_41_BIT_BIT", "PAK_41_BIT_BIT Image");
    setExtensions("bit");

    setGames("Farm Mania");
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
      if (plugin instanceof Plugin_PAK_41) {
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
      if (fm.readString(3).equals("BIT")) {
        rating += 50;
      }
      else {
        rating = 0;
      }

      // 4 - Image Width
      if (FieldValidator.checkWidth(fm.readInt())) {
        rating += 5;
      }

      // 4 - Image Height
      if (FieldValidator.checkHeight(fm.readInt())) {
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

      // 3 - Header (BIT)
      fm.skip(3);

      // 4 - Image Width
      int width = fm.readInt();
      FieldValidator.checkWidth(width);

      // 4 - Image Height
      int height = fm.readInt();
      FieldValidator.checkHeight(height);

      // 1 - Image Format? (4=8-bit Paletted with RGB Palette, 0=BGRA pixels)
      int imageFormat = fm.readByte();

      ImageResource imageResource = null;

      if (imageFormat == 0) {
        // RGBA Pixels
        imageResource = ImageFormatReader.readBGRA(fm, width, height);
      }
      else if (imageFormat == 4) {
        // 8-bit Paletted with RGB Palette

        // 1 - null
        fm.skip(1);

        // Color Palette RGB
        int[] palette = new int[256];

        // NOTE: No alpha in this palette!!
        for (int i = 0; i < 256; i++) {
          palette[i] = ((ByteConverter.unsign(fm.readByte()) << 16) | (ByteConverter.unsign(fm.readByte()) << 8) | ByteConverter.unsign(fm.readByte()));
        }

        // X - Pixels
        int numPixels = width * height;
        int[] pixels = new int[numPixels];

        for (int i = 0; i < numPixels; i++) {
          pixels[i] = palette[ByteConverter.unsign(fm.readByte())];
        }

        // 1 - Unknown (8)
        fm.skip(1);

        // X - Alpha Values

        for (int i = 0; i < numPixels; i++) {
          pixels[i] |= (ByteConverter.unsign(fm.readByte()) << 24);
        }

        imageResource = new ImageResource(pixels, width, height);
      }
      else {
        ErrorLogger.log("[Viewer_PAK_41_BIT_BIT] Unknown image format: " + imageFormat);
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