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

import org.watto.SingletonManager;
import org.watto.component.PreviewPanel;
import org.watto.component.PreviewPanel_Image;
import org.watto.datatype.Archive;
import org.watto.datatype.ImageResource;
import org.watto.datatype.Resource;
import org.watto.ge.helper.FieldValidator;
import org.watto.ge.helper.ImageFormatReader;
import org.watto.ge.helper.ImageSwizzler;
import org.watto.ge.plugin.AllFilesPlugin;
import org.watto.ge.plugin.ArchivePlugin;
import org.watto.ge.plugin.ViewerPlugin;
import org.watto.ge.plugin.archive.Plugin_TEX_XET;
import org.watto.io.FileManipulator;
import org.watto.io.buffer.ByteBuffer;

/**
**********************************************************************************************

**********************************************************************************************
**/
public class Viewer_TEX_XET_TEXTEX extends ViewerPlugin {

  /**
  **********************************************************************************************
  
  **********************************************************************************************
  **/
  public Viewer_TEX_XET_TEXTEX() {
    super("TEX_XET_TEXTEX", "TEX_XET_TEXTEX Image");
    setExtensions("tex_tex");

    setGames("Grand Theft Auto: Liberty City Stories");
    setPlatforms("PSP");
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
      if (plugin instanceof Plugin_TEX_XET) {
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

      int height = 0;
      int width = 0;
      int bpp = 0;

      // get the width/height from the properties of the image resource, which were read by the ArchivePlugin
      Object resourceObject = SingletonManager.get("CurrentResource");
      if (resourceObject == null || !(resourceObject instanceof Resource)) {
        return null;
      }
      Resource resource = (Resource) resourceObject;

      try {
        height = Integer.parseInt(resource.getProperty("Height"));
        width = Integer.parseInt(resource.getProperty("Width"));
        bpp = Integer.parseInt(resource.getProperty("BitsPerPixel"));
      }
      catch (Throwable t) {
        //
      }

      if (height == 0 || width == 0 || bpp == 0) {
        return null;
      }

      long arcSize = fm.getLength();

      /*
      // find where the palette is located
      // to do this, we need to calculate up the length of each mipmap
      int paletteOffset = 0;
      int mipmapWidth = width;
      int mipmapHeight = height;
      boolean rectangle = width != height;
      if (bpp == 8) {
        while (mipmapWidth >= 8 && mipmapHeight >= 8) {
          paletteOffset += mipmapWidth * mipmapHeight;
          mipmapWidth /= 2;
          mipmapHeight /= 2;
        }
        // add 4x4 and 2x2 (which are 64 bytes each) and 1x1 (32 bytes)
        if (rectangle) {
          paletteOffset += 32 + 32 + 32;
        }
        else {
          paletteOffset += 64 + 64 + 32;
        }
      }
      else if (bpp == 4) {
        while (mipmapWidth > 16 && mipmapHeight > 16) {
          paletteOffset += mipmapWidth * mipmapHeight / 2; // 1 byte stores 2 pixels
          mipmapWidth /= 2;
          mipmapHeight /= 2;
        }
        // add 16x16 (stored as 256) and 8x8 and 4x4 (stored as 128 each) and 2x2 (64 bytes) and 1x1 (32 bytes)
        if (rectangle) {
          int largestDimension = mipmapWidth;
          if (mipmapHeight > largestDimension) {
            largestDimension = mipmapHeight;
          }
          paletteOffset += (largestDimension * largestDimension / 2) + 256 + 64 + 64 + 32 + 32;
        }
        else {
          paletteOffset += 256 + 64 + 64 + 64 + 32;
        }
      }
      else if (bpp == 32) {
        // no palette
      }
      else {
        ErrorLogger.log("[Viewer_TEX_XET_TEXTEX] Unsupported bpp: " + bpp);
        return null;
      }
      */
      // find where the palette is located
      int paletteOffset = (int) arcSize;
      if (bpp == 4) {
        paletteOffset -= 64;
      }
      else if (bpp == 8) {
        paletteOffset -= 1024;
      }

      // load the image data into a byte buffer for use later (and so we can unswizzle it)
      int numBytes = width * height;
      if (bpp == 4) {
        numBytes /= 2;
      }
      else if (bpp == 32) {
        numBytes *= 4;
      }

      byte[] pixelData = fm.readBytes(numBytes);

      if (bpp == 8) {
        pixelData = ImageSwizzler.unswizzlePSP8Bit(pixelData, width, height);
      }
      else if (bpp == 4) {
        pixelData = ImageSwizzler.unswizzlePSP4Bit(pixelData, width, height);
      }
      else if (bpp == 32) {
        pixelData = ImageSwizzler.unswizzlePSP32Bit(pixelData, width, height);
      }

      // go to the palette
      fm.seek(paletteOffset);

      int[] palette = null;
      if (bpp == 8) {
        palette = ImageFormatReader.readPaletteRGBA(fm, 256);
      }
      else if (bpp == 4) {
        palette = ImageFormatReader.readPaletteRGBA(fm, 16);
      }

      // Now read the image
      fm.close();
      fm = new FileManipulator(new ByteBuffer(pixelData));

      // X - Pixels
      ImageResource imageResource = null;
      if (bpp == 8) {
        imageResource = ImageFormatReader.read8BitPaletted(fm, width, height, palette);
      }
      else if (bpp == 4) {
        imageResource = ImageFormatReader.read4BitPaletted(fm, width, height, palette);
      }
      else if (bpp == 32) {
        imageResource = ImageFormatReader.readRGBA(fm, width, height);
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