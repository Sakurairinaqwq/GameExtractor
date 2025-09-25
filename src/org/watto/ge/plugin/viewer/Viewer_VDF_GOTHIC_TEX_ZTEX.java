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
import org.watto.ge.helper.ImageFormatWriter;
import org.watto.ge.helper.ImageManipulator;
import org.watto.ge.plugin.AllFilesPlugin;
import org.watto.ge.plugin.ArchivePlugin;
import org.watto.ge.plugin.ViewerPlugin;
import org.watto.ge.plugin.archive.Plugin_VDF_GOTHIC;
import org.watto.io.FileManipulator;

/**
**********************************************************************************************

**********************************************************************************************
**/
public class Viewer_VDF_GOTHIC_TEX_ZTEX extends ViewerPlugin {

  /**
  **********************************************************************************************
  
  **********************************************************************************************
  **/
  public Viewer_VDF_GOTHIC_TEX_ZTEX() {
    super("VDF_GOTHIC_TEX_ZTEX", "Gothic TEX(ZTEX) Image");
    setExtensions("tex");

    setGames("Gothic");
    setPlatforms("PC");
    setStandardFileFormat(false);
  }

  /**
  **********************************************************************************************
  
  **********************************************************************************************
  **/
  @Override
  public boolean canWrite(PreviewPanel panel) {
    if (panel instanceof PreviewPanel_Image) {
      return true;
    }
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
      if (plugin instanceof Plugin_VDF_GOTHIC) {
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
      if (fm.readString(4).equals("ZTEX")) {
        rating += 50;
      }
      else {
        rating = 0;
      }

      fm.skip(8);

      // 4 - Image Width
      if (FieldValidator.checkWidth(fm.readInt())) {
        rating += 5;
      }

      // 4 - Image Height
      if (FieldValidator.checkHeight(fm.readInt())) {
        rating += 5;
      }

      // 4 - Number of Mipmaps
      if (FieldValidator.checkRange(fm.readInt(), 1, 20)) {
        rating += 5;
      }

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

      long arcSize = fm.getLength();

      // 4 - Header (ZTEX)
      // 4 - null
      fm.skip(8);

      // 4 - Image Type? (10=DXT1)
      int imageFormat = fm.readInt();

      // 4 - Image Width
      int width = fm.readInt();
      FieldValidator.checkWidth(width);

      // 4 - Image Height
      int height = fm.readInt();
      FieldValidator.checkHeight(height);

      // 4 - Number Of Mipmaps
      int numMipmaps = fm.readInt();
      FieldValidator.checkRange(numMipmaps, 1, 20);

      // 4 - Image Width
      // 4 - Image Height
      // 4 - Unknown (208,208,208,255)
      fm.skip(12);

      int largestLength = width * height;
      if (imageFormat == 10) {
        // DXT1
        largestLength /= 2;
      }
      else if (imageFormat == 12) {
        // DXT3
        // no change
      }
      else if (imageFormat == 6 || imageFormat == 8) {
        // RGBA4444 or BGR565
        largestLength *= 2;
      }
      long offset = arcSize - largestLength;
      fm.relativeSeek(offset);

      // X - Pixels
      ImageResource imageResource = null;
      if (imageFormat == 10) {
        imageResource = ImageFormatReader.readDXT1(fm, width, height);
        imageResource.addProperty("ImageFormat", "DXT1");
      }
      else if (imageFormat == 12) {
        imageResource = ImageFormatReader.readDXT3(fm, width, height);
        imageResource.addProperty("ImageFormat", "DXT3");
      }
      else if (imageFormat == 6) {
        imageResource = ImageFormatReader.readRGBA4444(fm, width, height);
        imageResource.addProperty("ImageFormat", "RGBA4444");
      }
      else if (imageFormat == 8) {
        imageResource = ImageFormatReader.readBGR565(fm, width, height);
        imageResource.addProperty("ImageFormat", "BGR565");
      }
      else {
        ErrorLogger.log("[Viewer_VDF_GOTHIC_TEX_ZTEX] Unknown Image Format: " + imageFormat);
        return null;
      }

      fm.close();

      //ColorConverter.convertToPaletted(resource);

      imageResource.addProperty("MipmapCount", "" + numMipmaps);

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
    try {

      if (!(preview instanceof PreviewPanel_Image)) {
        return;
      }

      ImageManipulator im = new ImageManipulator((PreviewPanel_Image) preview);

      int imageWidth = im.getWidth();
      int imageHeight = im.getHeight();

      if (imageWidth == -1 || imageHeight == -1) {
        return;
      }

      // Generate all the mipmaps of the image
      ImageResource[] mipmaps = im.generateMipmaps();
      int mipmapCount = mipmaps.length;

      // smallest mipmap in this format = 8*8;
      for (int m = mipmapCount - 1; m > 0; m--) {
        ImageResource mipmap = mipmaps[m];
        if (mipmap.getHeight() >= 8 && mipmap.getWidth() >= 8) {
          mipmapCount = m + 1;
          break;
        }
      }

      // Now try to get the property values from the ImageResource, if they exist
      ImageResource imageResource = ((PreviewPanel_Image) preview).getImageResource();

      int imageFormat = 0;
      if (imageResource != null) {
        String imageFormatString = imageResource.getProperty("ImageFormat", "DXT3");
        if (imageFormatString.equals("DXT1")) {
          imageFormat = 10;
        }
        else if (imageFormatString.equals("DXT3")) {
          imageFormat = 12;
        }
        else if (imageFormatString.equals("RGBA4444")) {
          imageFormat = 6;
        }
        else if (imageFormatString.equals("BGR565")) {
          imageFormat = 8;
        }
      }

      // 4 - Header (ZTEX)
      fm.writeString("ZTEX");

      // 4 - null
      fm.writeInt(0);

      // 4 - Image Type? (10=DXT1)
      fm.writeInt(imageFormat);

      // 4 - Image Width
      fm.writeInt(imageWidth);

      // 4 - Image Height
      fm.writeInt(imageHeight);

      // 4 - Number of Mipmaps
      fm.writeInt(mipmapCount);

      // 4 - Image Width
      fm.writeInt(imageWidth);

      // 4 - Image Height
      fm.writeInt(imageHeight);

      // 4 - Unknown (208,208,208,255)
      fm.writeByte(208);
      fm.writeByte(208);
      fm.writeByte(208);
      fm.writeByte(255);

      // X - Mipmaps (smallest to largest){
      for (int i = mipmapCount - 1; i >= 0; i--) {
        ImageResource mipmap = mipmaps[i];
        if (imageFormat == 10) { // DXT1
          ImageFormatWriter.writeDXT1(fm, mipmap);
        }
        else if (imageFormat == 12) { // DXT3
          ImageFormatWriter.writeDXT3(fm, mipmap);
        }
        else if (imageFormat == 6) {
          ImageFormatWriter.writeRGBA4444(fm, mipmap);
        }
        else if (imageFormat == 8) {
          ImageFormatWriter.writeBGR565(fm, mipmap);
        }
      }

      fm.close();

    }
    catch (Throwable t) {
      logError(t);
    }
  }

}