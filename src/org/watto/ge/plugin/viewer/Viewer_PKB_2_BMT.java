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
import org.watto.ge.helper.ImageSwizzler;
import org.watto.ge.plugin.AllFilesPlugin;
import org.watto.ge.plugin.ArchivePlugin;
import org.watto.ge.plugin.ViewerPlugin;
import org.watto.ge.plugin.archive.Plugin_PKBARC;
import org.watto.ge.plugin.archive.Plugin_PKB_2;
import org.watto.io.FileManipulator;
import org.watto.io.buffer.ByteBuffer;

/**
**********************************************************************************************

**********************************************************************************************
**/
public class Viewer_PKB_2_BMT extends ViewerPlugin {

  /**
  **********************************************************************************************
  
  **********************************************************************************************
  **/
  public Viewer_PKB_2_BMT() {
    super("PKB_2_BMT", "PKB_2_BMT Image");
    setExtensions("bmt", "tex");

    setGames("Shadow Hearts 2",
        "Shadow Hearts Covenant");
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
      if (plugin instanceof Plugin_PKBARC || plugin instanceof Plugin_PKB_2) {
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

      // 2 - Image Width
      if (FieldValidator.checkWidth(fm.readShort())) {
        rating += 5;
      }

      // 2 - Image Height
      if (FieldValidator.checkHeight(fm.readShort())) {
        rating += 5;
      }

      fm.skip(4);

      // 2 - Image Format? (16=8bit Paletted)
      if (fm.readShort() == 16) {
        rating += 5;
      }
      else {
        rating -= 10;
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

      // 2 - Image Width
      int width = fm.readShort();
      FieldValidator.checkWidth(width);

      // 2 - Image Height
      int height = fm.readShort();
      FieldValidator.checkHeight(height);

      // 2 - Unknown
      fm.skip(2);

      // 2 - Number of Colors (48, 64, 128, 256, ...)
      int numColors = fm.readShort();
      FieldValidator.checkNumColors(numColors);

      // 2 - Image Format? (16=8bit Paletted)
      int imageFormat = fm.readShort();

      // 2 - null
      fm.skip(2);

      // 4 - Palette Offset
      int paletteOffset = fm.readInt();

      ImageResource imageResource = null;

      if (imageFormat == 16) {
        int numPixels = width * height;

        if (numColors == 16 && (numPixels / 2 + 16) == paletteOffset) {
          // 4bit Paletted

          // X - Pixels
          numPixels /= 2;
          byte[] pixelBytes = fm.readBytes(numPixels);

          // PALETTE
          int[] palette = ImageFormatReader.readPaletteRGBA(fm, numColors);
          //palette = ImageSwizzler.unstripePalettePS2(palette);

          fm.close();
          fm = new FileManipulator(new ByteBuffer(pixelBytes));
          imageResource = ImageFormatReader.read4BitPaletted(fm, width, height, palette);
          imageResource = ImageFormatReader.doubleAlpha(imageResource);
        }
        else {
          // 8bit Paletted

          // X - Pixels
          byte[] pixelBytes = fm.readBytes(numPixels);

          // PALETTE
          int[] palette = ImageFormatReader.readPaletteRGBA(fm, numColors);
          palette = ImageSwizzler.unstripePalettePS2(palette);

          fm.close();
          fm = new FileManipulator(new ByteBuffer(pixelBytes));
          imageResource = ImageFormatReader.read8BitPaletted(fm, width, height, palette);
          imageResource = ImageFormatReader.doubleAlpha(imageResource);
        }

      }
      else {
        ErrorLogger.log("[Viewer_PKB_2_BMT] Unknown Image Format: " + imageFormat);
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