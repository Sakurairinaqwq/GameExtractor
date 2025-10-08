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
import org.watto.ge.plugin.archive.Plugin_P00_PKF;
import org.watto.ge.plugin.exporter.Exporter_ZLib;
import org.watto.io.FileManipulator;
import org.watto.io.buffer.ByteBuffer;

/**
**********************************************************************************************

**********************************************************************************************
**/
public class Viewer_P00_PKF_MPT_MPT extends ViewerPlugin {

  /**
  **********************************************************************************************
  
  **********************************************************************************************
  **/
  public Viewer_P00_PKF_MPT_MPT() {
    super("P00_PKF_MPT_MPT", "P00_PKF_MPT_MPT Image");
    setExtensions("mpt");

    setGames("Building the Great Wall of China");
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
      if (plugin instanceof Plugin_P00_PKF) {
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
      if (fm.readString(4).equals("MPT ")) {
        rating += 50;
      }
      else {
        rating = 0;
      }

      fm.skip(2);

      if (FieldValidator.checkRange(fm.readShort(), 1, 10)) { // guess max
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

      // 4 - Header ("MPT ")
      // 2 - Unknown
      fm.skip(6);

      // 2 - Number of Images
      int numImages = fm.readShort();
      FieldValidator.checkRange(numImages, 1, 10); // guess max

      ImageResource imageResource = null;

      for (int i = 0; i < numImages; i++) {
        // 4 - Image Format (Wi32=?, Pp32=?, P332=?, RGBA, RGBZ=RGB data that's ZLib-compressed)
        String imageFormat = fm.readString(4);

        // 4 - File Offset
        int offset = fm.readInt();
        FieldValidator.checkOffset(offset, arcSize);

        if (imageFormat.equals("RGBA")) {
          fm.relativeSeek(offset);

          // 4 - Image Width
          int width = fm.readInt();
          FieldValidator.checkWidth(width);

          // 4 - Image Height
          int height = fm.readInt();
          FieldValidator.checkHeight(height);

          imageResource = ImageFormatReader.readRGBA(fm, width, height);

          break;
        }
        else if (imageFormat.equals("RGBZ")) {
          fm.relativeSeek(offset);

          // 4 - Image Width
          int width = fm.readInt();
          FieldValidator.checkWidth(width);

          // 4 - Image Height
          int height = fm.readInt();
          FieldValidator.checkHeight(height);

          int decompLength = (width * height * 4);

          byte[] pixelBytes = new byte[decompLength];
          int decompWritePos = 0;
          Exporter_ZLib exporter = Exporter_ZLib.getInstance();
          exporter.open(fm, decompLength, decompLength);

          for (int b = 0; b < decompLength; b++) {
            if (exporter.available()) { // make sure we read the next bit of data, if required
              pixelBytes[decompWritePos++] = (byte) (exporter.read());
            }
          }

          // open the decompressed data for processing
          fm.close();

          //FileManipulator fmout = new FileManipulator(new File("C:\\temp.out"), true);
          //fmout.writeBytes(pixelBytes);
          //fmout.close();

          fm = new FileManipulator(new ByteBuffer(pixelBytes));

          imageResource = ImageFormatReader.readRGBA(fm, width, height);
          imageResource = ImageFormatReader.removeAlpha(imageResource);

          break;
        }
      }

      if (imageResource == null) {
        ErrorLogger.log("[Viewer_P00_PKF_MPT_MPT] No supported image formats found.");
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