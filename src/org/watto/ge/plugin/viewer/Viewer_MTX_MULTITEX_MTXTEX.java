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
import org.watto.SingletonManager;
import org.watto.component.PreviewPanel;
import org.watto.component.PreviewPanel_Image;
import org.watto.datatype.Archive;
import org.watto.datatype.ImageResource;
import org.watto.datatype.Resource;
import org.watto.ge.helper.FieldValidator;
import org.watto.ge.helper.ImageFormatReader;
import org.watto.ge.helper.ImageFormatWriter;
import org.watto.ge.helper.ImageManipulator;
import org.watto.ge.helper.ImageSwizzler;
import org.watto.ge.plugin.AllFilesPlugin;
import org.watto.ge.plugin.ArchivePlugin;
import org.watto.ge.plugin.ViewerPlugin;
import org.watto.ge.plugin.archive.Plugin_MTX_MULTITEX;
import org.watto.io.FileManipulator;

/**
**********************************************************************************************

**********************************************************************************************
**/
public class Viewer_MTX_MULTITEX_MTXTEX extends ViewerPlugin {

  /**
  **********************************************************************************************
  
  **********************************************************************************************
  **/
  public Viewer_MTX_MULTITEX_MTXTEX() {
    super("MTX_MULTITEX_MTXTEX", "MTX_MULTITEX MTX_TEX Image");
    setExtensions("mtx_tex");

    setGames("Indiana Jones and the Emperors Tomb");
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
  public int getMatchRating(FileManipulator fm) {
    try {

      int rating = 0;

      ArchivePlugin plugin = Archive.getReadPlugin();
      if (plugin instanceof Plugin_MTX_MULTITEX) {
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
      int mipmapCount = 10;
      String imageFormat = null;

      // get the width/height from the properties of the image resource, which were read by the ArchivePlugin
      Object resourceObject = SingletonManager.get("CurrentResource");
      if (resourceObject == null || !(resourceObject instanceof Resource)) {
        return null;
      }
      Resource resource = (Resource) resourceObject;

      try {
        height = Integer.parseInt(resource.getProperty("Height"));
        width = Integer.parseInt(resource.getProperty("Width"));
        mipmapCount = Integer.parseInt(resource.getProperty("MipmapCount"));
        imageFormat = resource.getProperty("ImageFormat");
      }
      catch (Throwable t) {
        //
      }

      if (height == 0 || width == 0 || imageFormat == null) {
        return null;
      }

      if (mipmapCount != 1) {
        // 4 - Mipmap Data Length
        fm.skip(4);
      }

      // X - Pixels
      ImageResource imageResource = null;
      if (imageFormat.equals("RGBA")) {
        imageResource = ImageFormatReader.readRGBA(fm, width, height);
      }
      else if (imageFormat.equals("DXT1")) {
        imageResource = ImageFormatReader.readDXT1(fm, width, height);
      }
      else if (imageFormat.equals("DXT3")) {
        imageResource = ImageFormatReader.readDXT3(fm, width, height);
      }
      else if (imageFormat.equals("DXT5")) {
        imageResource = ImageFormatReader.readDXT5(fm, width, height);
      }
      else if (imageFormat.equals("11")) {
        // swizzled ps2 paletted image
        fm.relativeSeek(0);

        // COLOR PALETTE
        int[] palette = ImageFormatReader.readPaletteRGBA(fm, 256);
        palette = ImageSwizzler.unstripePalettePS2(palette);
        palette = ImageFormatReader.doubleAlpha(palette);

        // IMAGE DATA
        // X - Pixels
        imageResource = ImageFormatReader.read8BitPaletted(fm, width, height, palette);
        imageResource.setPixels(ImageSwizzler.unswizzlePS2(imageResource.getImagePixels(), width, height));
      }
      else if (imageFormat.equals("10")) {
        // unswizzled ps2 paletted image
        fm.relativeSeek(0);

        // COLOR PALETTE
        int[] palette = ImageFormatReader.readPaletteRGBA(fm, 256);
        palette = ImageSwizzler.unstripePalettePS2(palette);
        palette = ImageFormatReader.doubleAlpha(palette);

        // IMAGE DATA
        // X - Pixels
        imageResource = ImageFormatReader.read8BitPaletted(fm, width, height, palette);
      }
      else {
        ErrorLogger.log("[Viewer_MTX_MULTITEX_MTXTEX] Unknown Image Format: " + imageFormat);
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
  We can't WRITE these files from scratch, but we can REPLACE some of the images with new content  
  **********************************************************************************************
  **/
  public void replace(Resource resourceBeingReplaced, PreviewPanel preview, FileManipulator fm) {
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

      // want to exclude the 2 smallest mipmaps (work backwards to find anything 4x4 or larger)
      for (int i = mipmapCount - 1; i > 0; i--) {
        ImageResource mipmap = mipmaps[i];
        if (mipmap.getHeight() >= 4 && mipmap.getWidth() >= 4) {
          // found the smallest one we want
          mipmapCount = i + 1;
          break;
        }
      }

      String imageFormat = resourceBeingReplaced.getProperty("ImageFormat");

      // X - Mipmaps
      for (int i = 0; i < mipmapCount; i++) {
        ImageResource mipmap = mipmaps[i];

        int mipmapHeight = mipmap.getHeight();
        int mipmapWidth = mipmap.getWidth();

        int pixelCount = mipmapWidth * mipmapHeight;

        if (imageFormat.equals("RGBA")) {
          // 4 - Mipmap Data Length
          fm.writeInt(pixelCount * 4);

          // X - Mipmap Data
          ImageFormatWriter.writeRGBA(fm, mipmap);
        }
        else if (imageFormat.equals("DXT1")) {
          // 4 - Mipmap Data Length
          fm.writeInt(pixelCount / 2);

          // X - Mipmap Data
          ImageFormatWriter.writeDXT1(fm, mipmap);
        }
        else if (imageFormat.equals("DXT3")) {
          // 4 - Mipmap Data Length
          fm.writeInt(pixelCount);

          // X - Mipmap Data
          ImageFormatWriter.writeDXT3(fm, mipmap);
        }
        else if (imageFormat.equals("DXT5")) {
          // 4 - Mipmap Data Length
          fm.writeInt(pixelCount);

          // X - Mipmap Data
          ImageFormatWriter.writeDXT5(fm, mipmap);
        }
        else {
          // unknown image format
          return;
        }

      }

      // Now that we've written the image, set the properties on the Resource
      resourceBeingReplaced.setProperty("MipmapCount", "" + mipmapCount);
      resourceBeingReplaced.setProperty("Width", "" + imageWidth);
      resourceBeingReplaced.setProperty("Height", "" + imageHeight);

      fm.close();

    }
    catch (Throwable t) {
      logError(t);
    }
  }

}