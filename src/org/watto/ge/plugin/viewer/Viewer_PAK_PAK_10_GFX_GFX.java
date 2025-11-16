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
import org.watto.ge.plugin.archive.Plugin_PAK_PAK_10;
import org.watto.io.FileManipulator;

/**
**********************************************************************************************

**********************************************************************************************
**/
public class Viewer_PAK_PAK_10_GFX_GFX extends ViewerPlugin {

  /**
  **********************************************************************************************
  
  **********************************************************************************************
  **/
  public Viewer_PAK_PAK_10_GFX_GFX() {
    super("PAK_PAK_10_GFX_GFX", "PAK_PAK_10_GFX_GFX Image");
    setExtensions("gfx");

    setGames("Hot Farm Africa");
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
      if (plugin instanceof Plugin_PAK_PAK_10) {
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

      // 3 - Header
      if (fm.readString(3).equals("GFX")) {
        rating += 50;
      }
      else {
        rating = 0;
      }

      int type = fm.readShort();
      if (type == 0 || type == 512 || type == 768) {
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

      // 3 - Header (GFX)
      fm.skip(3);

      // 2 - Type (0/512/768)
      int type = fm.readShort();

      int width = 0;
      int height = 0;
      int imageFormat = 0;

      if (type == 0) {
        // 1 - null
        fm.skip(1);

        // 4 - Image Width
        width = fm.readInt();
        FieldValidator.checkWidth(width);

        // 4 - Image Height
        height = fm.readInt();
        FieldValidator.checkHeight(height);

        // 1 - Unknown (2)
        fm.skip(1);

        // 1 - Image Format? (12=BGRA4444)
        imageFormat = fm.readByte();

        // 2 - Number of Mipmaps
        fm.skip(2);
      }
      else if (type == 512) {
        // 1 - null
        fm.skip(1);

        // 4 - Image Width
        width = fm.readInt();
        FieldValidator.checkWidth(width);

        // 4 - Image Height
        height = fm.readInt();
        FieldValidator.checkHeight(height);

        // 4 - Unknown (1)
        // 1 - Unknown (4)
        fm.skip(5);

        // 2 - Image Format (1=BGRA, 6=Paletted, 10=DXT5)
        imageFormat = fm.readShort();

        // 1 - Number of Mipmaps
        fm.skip(1);
      }
      else if (type == 768) {
        // 4 - Image Width
        width = fm.readInt();
        FieldValidator.checkWidth(width);

        // 4 - Image Height
        height = fm.readInt();
        FieldValidator.checkHeight(height);

        // 4 - Unknown (1)
        fm.skip(4);

        // 2 - Image Format (1=BGRA, 6=Paletted, 10=DXT5)
        imageFormat = fm.readShort();

        // 1 - Number of Mipmaps
        fm.skip(1);
      }
      else {
        ErrorLogger.log("[Viewer_PAK_PAK_10_GFX_GFX] Unknown Type: " + type);
        return null;
      }

      // X - Pixels
      ImageResource imageResource = null;
      if (imageFormat == 1) {
        imageResource = ImageFormatReader.readBGRA(fm, width, height);
      }
      else if (imageFormat == 6 || imageFormat == 7 || imageFormat == 9) {
        imageResource = ImageFormatReader.read8BitPaletted(fm, width, height);
      }
      else if (imageFormat == 10) {
        imageResource = ImageFormatReader.readDXT5(fm, width, height);
      }
      else if (imageFormat == 12) {
        imageResource = ImageFormatReader.readBGRA4444(fm, width, height); // guess that it's BGRA4444
      }
      else {
        ErrorLogger.log("[Viewer_PAK_PAK_10_GFX_GFX] Unknown Image Format: " + imageFormat);
        return null;
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