/*
 * Application:  Game Extractor
 * Author:       wattostudios
 * Website:      http://www.watto.org
 * Copyright:    Copyright (c) 2002-2020 wattostudios
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
import org.watto.Settings;
import org.watto.component.PreviewPanel;
import org.watto.component.PreviewPanel_Image;
import org.watto.datatype.Archive;
import org.watto.datatype.ImageResource;
import org.watto.ge.helper.FieldValidator;
import org.watto.ge.helper.ImageFormatReader;
import org.watto.ge.plugin.AllFilesPlugin;
import org.watto.ge.plugin.ArchivePlugin;
import org.watto.ge.plugin.ViewerPlugin;
import org.watto.ge.plugin.archive.Plugin_PCK_GDPC_2;
import org.watto.io.FileManipulator;

/**
**********************************************************************************************

**********************************************************************************************
**/
public class Viewer_PCK_GDPC_2_CTEX_GST2 extends ViewerPlugin {

  /**
  **********************************************************************************************
  
  **********************************************************************************************
  **/
  public Viewer_PCK_GDPC_2_CTEX_GST2() {
    super("PCK_GDPC_2_CTEX_GST2", "PCK_GDPC_2_CTEX_GST2 Image");
    setExtensions("ctex");

    setGames("Kamaeru");
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
  public int getMatchRating(FileManipulator fm) {
    try {

      int rating = 0;

      ArchivePlugin plugin = Archive.getReadPlugin();
      if (plugin instanceof Plugin_PCK_GDPC_2) {
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

      // 4 - Header
      if (fm.readString(4).equals("GST2")) {
        rating += 50;
      }

      fm.skip(4);

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

      // 4 - Unknown
      // 4 - Header (GST2)
      // 4 - Unknown (1)
      // 4 - Image Width
      // 4 - Image Height
      // 4 - Unknown
      // 4 - Unknown
      // 16 - null
      fm.skip(44);

      // 2 - Image Width
      int width = fm.readShort();
      FieldValidator.checkWidth(width);

      // 2 - Image Height
      int height = fm.readShort();
      FieldValidator.checkHeight(height);

      // 4 - Number Of Mipmaps
      fm.skip(4);

      // 4 - Image Format
      int imageFormat = fm.readInt();

      // X - Pixels
      ImageResource imageResource = null;
      if (imageFormat == 5) {
        // 4 - Image Data Length
        int length = fm.readInt();
        FieldValidator.checkLength(length, fm.getLength());

        // X - Image Data (WEBP)
        /*
        byte[] webPData = fm.readBytes(length);
        BufferedImage image = WebPCodec.decodeImage(webPData);
        
        int pixelCount = width * height;
        
        int[] pixels = new int[pixelCount];
        PixelGrabber pixelGrabber = new PixelGrabber(image, 0, 0, width, height, pixels, 0, width);
        pixelGrabber.grabPixels();
        
        return new ImageResource(pixels, width, height);
        */

        // dump the remaining image data out to disk, so it can be read by the webp converter
        File sourceFile = fm.getFile();
        String outputFilePath = Settings.getString("TempDirectory") + File.separatorChar + sourceFile.getName() + ".webp";
        File outputFile = new File(outputFilePath);
        if (outputFile.exists()) {
          // already dumped to disk
          sourceFile = outputFile;
        }
        else {
          FileManipulator outFM = new FileManipulator(outputFile, true);
          while (length > 0) {
            outFM.writeByte(fm.readByte());
            length--;
          }
          outFM.close();
          sourceFile = outputFile;
        }

        fm.close();
        fm = new FileManipulator(sourceFile, false);

        return new Viewer_WEBP_RIFF().readThumbnail(fm);

      }
      else if (imageFormat == 17) {
        imageResource = ImageFormatReader.readDXT1(fm, width, height);
      }
      else if (imageFormat == 18) {
        imageResource = ImageFormatReader.readDXT3(fm, width, height);
      }
      else if (imageFormat == 19 || imageFormat == 34) {
        imageResource = ImageFormatReader.readDXT5(fm, width, height);
      }
      else {
        ErrorLogger.log("[Viewer_PCK_GDPC_2_CTEX_GST2] Unknown Image Format: " + imageFormat);
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