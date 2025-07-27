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
import org.watto.ge.helper.ImageFormatReader;
import org.watto.ge.helper.ImageSwizzler;
import org.watto.ge.plugin.AllFilesPlugin;
import org.watto.ge.plugin.ArchivePlugin;
import org.watto.ge.plugin.ViewerPlugin;
import org.watto.ge.plugin.archive.Plugin_SGP2;
import org.watto.ge.plugin.archive.Plugin_TEX2;
import org.watto.io.FileManipulator;
import org.watto.io.buffer.ByteBuffer;

/**
**********************************************************************************************

**********************************************************************************************
**/
public class Viewer_TEX2_TEX extends ViewerPlugin {

  /**
  **********************************************************************************************
  
  **********************************************************************************************
  **/
  public Viewer_TEX2_TEX() {
    super("TEX2_TEX", "TEX2_TEX Image");
    setExtensions("tex");

    setGames("Fantastic 4", "Sopranos: Road to Respect");
    setPlatforms("PS2", "PC");
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
      if (plugin instanceof Plugin_TEX2 || plugin instanceof Plugin_SGP2) {
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

      // 4 - Type? (101)
      if (fm.readInt() == 101) {
        rating += 5;
      }

      // 4 - File Length
      if (fm.readInt() == fm.getLength()) {
        rating += 5;
      }

      fm.skip(12);

      // 2 - Image Width
      if (FieldValidator.checkWidth(fm.readShort())) {
        rating += 5;
      }

      // 2 - Image Height
      if (FieldValidator.checkHeight(fm.readShort())) {
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

      // 4 - Type? (101)
      // 4 - Data Length (including all these headers)
      // 4 - Filename Offset (relative to the start of this file data) (44)
      // 4 - Hash?
      // 4 - null
      fm.skip(20);

      // 2 - Image Width
      short width = fm.readShort();
      FieldValidator.checkWidth(width);

      // 2 - Image Height
      short height = fm.readShort();
      FieldValidator.checkHeight(height);

      // 4 - Unknown
      // 8 - Unknown (257)
      fm.skip(12);

      // 4 - File Data Offset (relative to the start of this file data) (128)
      int dataOffset = fm.readInt();
      FieldValidator.checkOffset(dataOffset, arcSize);

      // 4 - Palette Offset
      int paletteOffset = fm.readInt();
      FieldValidator.checkOffset(dataOffset, arcSize);

      // 84 - Filename (null terminated, filled with nulls)
      fm.relativeSeek(dataOffset);

      // X - Pixels
      ImageResource imageResource = null;
      if (paletteOffset == 0) {
        imageResource = ImageFormatReader.readRGBA(fm, width, height);
      }
      else {
        int pixelCount = width * height;
        byte[] imageBytes = fm.readBytes(pixelCount);

        int[] palette = ImageFormatReader.readPaletteRGBA(fm, 256); // even though we don't always have 256 colors, read this many anyway
        palette = ImageSwizzler.stripePalettePS2(palette); // PS2 striped palettes

        fm.close();
        fm = new FileManipulator(new ByteBuffer(imageBytes));

        imageResource = ImageFormatReader.read8BitPaletted(fm, width, height, palette);
        imageResource = ImageFormatReader.flipVertically(imageResource); // flip paletted images
      }

      imageResource = ImageFormatReader.doubleAlpha(imageResource); // these are PS2 images, the alpha needs doubling

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