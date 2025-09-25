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

import org.watto.component.PreviewPanel;
import org.watto.component.PreviewPanel_Image;
import org.watto.datatype.Archive;
import org.watto.datatype.ImageResource;
import org.watto.ge.helper.FieldValidator;
import org.watto.ge.plugin.AllFilesPlugin;
import org.watto.ge.plugin.ArchivePlugin;
import org.watto.ge.plugin.ViewerPlugin;
import org.watto.io.FileManipulator;
import org.watto.io.converter.ByteConverter;

/**
**********************************************************************************************

**********************************************************************************************
**/
public class Viewer_PAL_RIFF extends ViewerPlugin {

  /**
  **********************************************************************************************
  
  **********************************************************************************************
  **/
  public Viewer_PAL_RIFF() {
    super("PAL_RIFF", "PAL Palette");
    setExtensions("pal");

    setGames("Super Bubsy");
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

      // 4 - Header
      if (fm.readString(4).equals("RIFF")) {
        rating += 15;
      }
      else {
        rating = 0;
      }

      // 4 - File Length
      if (fm.readInt() + 8 == fm.getLength()) {
        rating += 5;
      }

      // 8 - Header
      if (fm.readString(8).equals("PAL data")) {
        rating += 25;
      }
      else {
        rating = 0;
      }

      fm.skip(6);

      if (FieldValidator.checkNumColors(fm.readShort())) {
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

      // 4 - Header (RIFF)
      if (!fm.readString(4).equals("RIFF")) {
        return null;
      }

      // 4 - Palette Length [+8]
      FieldValidator.checkLength(fm.readInt(), arcSize);

      // 4 - Header ("PAL ")
      // 4 - Header (data)
      if (!fm.readString(8).equals("PAL data")) {
        return null;
      }

      // 4 - Palette Data Length
      FieldValidator.checkLength(fm.readInt(), arcSize);

      // 2 - Palette Version
      fm.skip(2);

      // 2 - Number of Colors
      int numColors = fm.readShort();
      FieldValidator.checkNumColors(numColors);

      // write each color as a 10x10 square, and the image width is 16 colors * 16 colors
      int numPixels = (16 * 10) * (16 * 10); // max 256 colors (16*16)
      int[] pixels = new int[numPixels];

      int[] singlePixels = new int[256];
      for (int i = 0; i < numColors; i++) {
        // INPUT = RGB + flag
        // OUTPUT = ARGB
        singlePixels[i] = ((ByteConverter.unsign(fm.readByte()) << 16) | (ByteConverter.unsign(fm.readByte()) << 8) | ByteConverter.unsign(fm.readByte()) | (255 << 24));
        fm.skip(1);
      }

      // now make it in to an image where each pixel is 10x10
      int height = 16 * 10;
      int width = 16 * 10;
      int pixelNumber = 0;
      for (int h = 0; h < height; h += 10) {
        for (int w = 0; w < width; w += 10) {
          int pixel = singlePixels[pixelNumber];
          pixelNumber++;

          for (int h2 = 0; h2 < 10; h2++) {
            for (int w2 = 0; w2 < 10; w2++) {
              int outPos = ((h + h2) * width) + (w + w2);
              pixels[outPos] = pixel;
            }
          }
        }
      }

      // X - Pixels
      ImageResource imageResource = new ImageResource(pixels, width, height);

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