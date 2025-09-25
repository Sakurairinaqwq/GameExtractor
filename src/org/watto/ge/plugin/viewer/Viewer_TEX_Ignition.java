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

import java.io.File;

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
import org.watto.io.buffer.ByteBuffer;

/**
**********************************************************************************************

**********************************************************************************************
**/
public class Viewer_TEX_Ignition extends ViewerPlugin {

  /**
  **********************************************************************************************
  
  **********************************************************************************************
  **/
  public Viewer_TEX_Ignition() {
    super("TEX_Ignition", "Ignition TEX Image");
    setExtensions("tex");

    setGames("Ignition");
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

      if ((fm.getLength() % 64) == 0) { // even multiple of 64 bytes
        rating += 5;
      }
      else {
        return 0;
      }

      // look for a SYS.COL file in this directory or a few parents
      boolean found = false;
      File parent = fm.getFile().getParentFile();
      while (parent != null && parent.isDirectory()) {
        File colorFile = new File(parent.getAbsolutePath() + File.separatorChar + "SYS.COL");
        if (colorFile.exists()) {
          rating += 5;
          found = true;
          break;
        }
        else {
          parent = parent.getParentFile();
        }
      }

      if (!found) {
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

      long arcSize = fm.getLength();

      int width = 256;
      int height = (int) arcSize / width;

      // find and load the SYS.COL color palette
      File colorFile = null;
      File parent = fm.getFile().getParentFile();
      while (parent != null && parent.isDirectory()) {
        colorFile = new File(parent.getAbsolutePath() + File.separatorChar + "SYS.COL");
        if (colorFile.exists()) {
          break;
        }
        else {
          colorFile = null;
          parent = parent.getParentFile();
        }
      }

      if (colorFile == null) {
        ErrorLogger.log("[Viewer_TEX_Ignition] Couldn't find the SYS.COL color palette file");
        return null;
      }

      byte[] pixelBytes = fm.readBytes((int) arcSize);

      FileManipulator colorFM = new FileManipulator(colorFile, false);
      colorFM.skip(8);
      int[] palette = ImageFormatReader.readPaletteRGB(colorFM, 256);
      colorFM.close();

      fm.close();
      fm = new FileManipulator(new ByteBuffer(pixelBytes));

      // X - Pixels
      ImageResource imageResource = ImageFormatReader.read8BitPaletted(fm, width, height, palette);

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