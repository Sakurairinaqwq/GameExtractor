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
import org.watto.datatype.Resource;
import org.watto.ge.helper.FieldValidator;
import org.watto.ge.helper.ImageFormatReader;
import org.watto.ge.plugin.AllFilesPlugin;
import org.watto.ge.plugin.ArchivePlugin;
import org.watto.ge.plugin.ViewerPlugin;
import org.watto.ge.plugin.archive.Plugin_BIN_43;
import org.watto.io.FileManipulator;
import org.watto.io.buffer.ByteBuffer;

/**
**********************************************************************************************

**********************************************************************************************
**/
public class Viewer_BIN_43_TEX extends ViewerPlugin {

  /**
  **********************************************************************************************
  
  **********************************************************************************************
  **/
  public Viewer_BIN_43_TEX() {
    super("BIN_43_TEX", "BIN_43_TEX Image");
    setExtensions("tex");

    setGames("Prince of Persia: Revelations");
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
      if (plugin instanceof Plugin_BIN_43) {
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

      fm.skip(4);

      if (fm.readInt() == -1) {
        rating += 5;
      }

      if (fm.readInt() == 1074397187) {
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

      // 4 - Unknown
      // 4 - Unknown (-1)
      // 4 - Unknown (1074397187)
      fm.skip(12);

      // 2 - Image Width
      int width = fm.readShort();
      FieldValidator.checkWidth(width);

      // 2 - Image Height
      int height = fm.readShort();
      FieldValidator.checkHeight(height);

      // 4 - null
      // 4 - Unknown
      // 4 - Unknown
      // 2 - Unknown (255)
      // 2 - Unknown (255)
      // 4 - Unknown
      // 4 - Unknown (4)
      fm.skip(24);

      // 4 - Image Type (0=TGA, 1=Paletted, 7=DXT5)
      int imageFormat = fm.readInt();

      // 4 - Image Width
      // 4 - Image Height
      // 4 - null
      // 4 - Unknown
      fm.skip(16);

      // X - Pixels
      ImageResource imageResource = null;
      if (imageFormat == 7) {
        // DXT5
        imageResource = ImageFormatReader.readDXT5(fm, width, height);
      }
      else if (imageFormat == 1) {
        // Paletted

        // 4 - Palette File ID
        int paletteID = fm.readInt();

        int[] palette = extractPalette(paletteID);
        if (palette != null) {
          imageResource = ImageFormatReader.read8BitPaletted(fm, width, height, palette);
        }

        //imageResource.setPixels(ImageFormatReader.unswizzlePSP(imageResource.getImagePixels(), width, height));
      }
      else if (imageFormat == 0) {
        ErrorLogger.log("[Viewer_BIN_43_TEX] TGA format is unsupported: " + imageFormat);
        return null;
      }
      else {
        ErrorLogger.log("[Viewer_BIN_43_TEX] Unknown Image Format: " + imageFormat);
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

  /**
  **********************************************************************************************
  
  **********************************************************************************************
  **/
  public int[] extractPalette(int paletteFileID) {
    try {

      Resource[] resources = Archive.getResources();
      int numResources = resources.length;

      Resource paletteFile = null;

      for (int i = 0; i < numResources; i++) {
        Resource currentResource = (Resource) resources[i];
        int fileID = Integer.parseInt(currentResource.getProperty("FileID"));

        if (fileID == paletteFileID) {
          // found the color palette file
          paletteFile = currentResource;
          break;
        }
      }

      if (paletteFile == null) {
        return null;
      }

      int paltLength = (int) paletteFile.getLength();

      ByteBuffer buffer = new ByteBuffer(paltLength);
      FileManipulator fm = new FileManipulator(buffer);
      paletteFile.extract(fm);

      fm.seek(4); // palette starts at offset 4

      int[] palette = ImageFormatReader.readPaletteBGRA(fm, 256);

      fm.close();

      return palette;
    }
    catch (Throwable t) {
      logError(t);
      return new int[0];
    }
  }

}