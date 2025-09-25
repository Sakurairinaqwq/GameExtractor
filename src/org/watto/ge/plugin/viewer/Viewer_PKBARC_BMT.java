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
import org.watto.ge.plugin.archive.Plugin_PKB;
import org.watto.ge.plugin.archive.Plugin_PKBARC;
import org.watto.io.FileManipulator;

/**
**********************************************************************************************

**********************************************************************************************
**/
public class Viewer_PKBARC_BMT extends ViewerPlugin {

  /**
  **********************************************************************************************
  
  **********************************************************************************************
  **/
  public Viewer_PKBARC_BMT() {
    super("PKBARC_BMT", "PKBARC_BMT Image");
    setExtensions("bmt", "bin", "tex");

    setGames("Shadow Hearts");
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
      if (plugin instanceof Plugin_PKBARC || plugin instanceof Plugin_PKB) {
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
      if (FieldValidator.checkWidth(fm.readShort() + 1)) { // +1 to allow nulls
        rating += 5;
      }

      // 2 - Image Height
      if (FieldValidator.checkHeight(fm.readShort() + 1)) { // +1 to allow nulls
        rating += 5;
      }

      // 2 - Image Width
      if (FieldValidator.checkWidth(fm.readShort())) {
        rating += 5;
      }

      // 2 - Image Height
      if (FieldValidator.checkHeight(fm.readShort())) {
        rating += 5;
      }

      // 2 - Image Format? (16=8bit Paletted)
      short imageFormat = fm.readShort();
      if (imageFormat == 0 || imageFormat == 1 || imageFormat == 2) {
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
      FieldValidator.checkWidth(width + 1); // +1 to allow nulls

      // 2 - Image Height
      int height = fm.readShort();
      FieldValidator.checkHeight(height + 1); // +1 to allow nulls

      if (width == 0 && height == 0) {
        // 2 - Image Width
        width = fm.readShort();
        FieldValidator.checkWidth(width);

        // 2 - Image Height
        height = fm.readShort();
        FieldValidator.checkHeight(height);
      }
      else {
        fm.skip(4);
      }

      // 2 - Image Format? (1=8bit Paletted, 2=4bit Paletted)
      int imageFormat = fm.readShort();

      // 2 - Unknown (1)
      fm.skip(2);

      // 2 - Number of Colors (48, 64, 128, 256, ...)
      int numColors = fm.readShort();
      if (numColors == 0) {
        numColors = 256;
      }
      FieldValidator.checkNumColors(numColors);

      // 2 - null
      fm.skip(2);

      ImageResource imageResource = null;

      if (imageFormat == 1) {
        // 8bit Paletted

        // PALETTE
        int[] palette = ImageFormatReader.readPaletteRGBA(fm, numColors);
        palette = ImageSwizzler.unstripePalettePS2(palette);

        // X - Pixels
        imageResource = ImageFormatReader.read8BitPaletted(fm, width, height, palette);
        imageResource = ImageFormatReader.doubleAlpha(imageResource);
      }
      else if (imageFormat == 2 || imageFormat == 0) {
        // 4bit Paletted

        // work out the number of colors, as it's unreliable to rely on the numColors field for the 4bit format
        numColors = ((int) fm.getLength() - ((width * height / 2) + 16)) / 4;

        // PALETTE
        int[] palette = ImageFormatReader.readPaletteRGBA(fm, numColors);
        if (imageFormat == 0) {
          palette = ImageSwizzler.unstripePalettePS2(palette);
        }

        // X - Pixels
        imageResource = ImageFormatReader.read4BitPaletted(fm, width, height, palette);
        imageResource = ImageFormatReader.doubleAlpha(imageResource);
      }
      else {
        ErrorLogger.log("[Viewer_PKBARC_BMT] Unknown Image Format: " + imageFormat);
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