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
import org.watto.io.FileManipulator;

/**
**********************************************************************************************

**********************************************************************************************
**/
public class Viewer_GFX_1XFG extends ViewerPlugin {

  /**
  **********************************************************************************************
  
  **********************************************************************************************
  **/
  public Viewer_GFX_1XFG() {
    super("GFX_1XFG", "GFX_1XFG Image");
    setExtensions("gfx");

    setGames("Alice Greenfingers",
        "Alice Greenfingers 2");
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
      if (!(plugin instanceof AllFilesPlugin)) {
        return 0;
      }

      if (FieldValidator.checkExtension(fm, extensions)) {
        rating += 25;
      }
      else {
        return 0;
      }

      // 4 - Header
      if (fm.readString(4).equals("1XFG")) {
        rating += 25;
      }
      else {
        rating = 0;
      }

      // 4 - Header
      if (fm.readString(4).equals("CSED")) {
        rating += 25;
      }

      // 4 - Block Length
      if (fm.readInt() == 32) {
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

      long arcSize = fm.getLength();

      // 4 - Header
      fm.skip(4);

      int width = 0;
      int height = 0;
      int imageFormat = 0;
      ImageResource imageResource = null;

      while (fm.getOffset() < arcSize) {

        // 4 - Header
        String blockHeader = fm.readString(4);

        if (blockHeader.equals("OFNI")) {
          // INFO BLOCK

          // 4 - Block Length
          int blockLength = fm.readInt();
          FieldValidator.checkLength(blockLength, arcSize);

          // 4 - Image Width
          width = fm.readInt();
          FieldValidator.checkWidth(width);

          // 4 - Image Height
          height = fm.readInt();
          FieldValidator.checkHeight(height);

          // 4 - Bits Per Pixel (16=RGB565, 24=RGB, 32=RGBA)
          imageFormat = fm.readInt();

          // 4 - Unknown
          // 16 - null
          fm.skip(blockLength - 12);
        }
        else if (blockHeader.equals("EGMI")) {
          // IMAGE BLOCK

          // 4 - Block Length
          int blockLength = fm.readInt();
          FieldValidator.checkLength(blockLength, arcSize);

          // X - Pixel Data (RGB565)
          if (width == 0 || height == 0 || imageFormat == 0) {
            return null;
          }

          if (imageFormat == 16) {
            imageResource = ImageFormatReader.readRGB565(fm, width, height);
          }
          else if (imageFormat == 24) {
            imageResource = ImageFormatReader.readRGB(fm, width, height);
          }
          else if (imageFormat == 32) {
            imageResource = ImageFormatReader.readRGBA(fm, width, height);
          }
          else {
            ErrorLogger.log("[Viewer_GFX_1XFG] Unknown Image Format: " + imageFormat);
          }

          break; // found the image
        }
        else if (blockHeader.equals("GEPJ")) {
          // JPEG BLOCK

          // 4 - Block Length
          int blockLength = fm.readInt();
          FieldValidator.checkLength(blockLength, arcSize);

          // X - JPEG Data (without the header)
          ErrorLogger.log("[Viewer_GFX_1XFG] RAW JPEG data not supported");

          break; // found the image
        }
        else if (blockHeader.equals("CSED") || blockHeader.equals("LBTC") || blockHeader.equals("1PIM")) {
          // skip other known block types

          // 4 - Block Length
          int blockLength = fm.readInt();
          FieldValidator.checkLength(blockLength, arcSize);

          fm.skip(blockLength);
        }
        else {
          // skip 4 bytes (already read from the header)
          System.out.println(fm.getOffset() - 4);
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

}