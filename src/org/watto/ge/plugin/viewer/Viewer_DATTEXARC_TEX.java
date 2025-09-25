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

import java.awt.Image;

import org.watto.ErrorLogger;
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
import org.watto.ge.plugin.archive.Plugin_DATTEXARC;
import org.watto.io.FileManipulator;
import org.watto.io.buffer.ByteBuffer;

/**
**********************************************************************************************

**********************************************************************************************
**/
public class Viewer_DATTEXARC_TEX extends ViewerPlugin {

  /**
  **********************************************************************************************
  
  **********************************************************************************************
  **/
  public Viewer_DATTEXARC_TEX() {
    super("DATTEXARC_TEX", "DATTEXARC_TEX Image");
    setExtensions("dattexarc_tex");

    setGames("The Great Escape");
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
      if (plugin instanceof Plugin_DATTEXARC) {
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

      fm.skip(8);

      int imageFormat = fm.readInt();
      if (imageFormat == 4 || imageFormat == 8) {
        rating += 5;
      }

      if (FieldValidator.checkWidth(fm.readInt())) {
        rating += 5;
      }

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
      // 4 - Unknown
      fm.skip(8);

      // 4 - Image Format? (8=8bit Paletted, 4=4bit Paletted)
      int imageFormat = fm.readInt();

      // 4 - Image Width
      int width = fm.readInt();
      FieldValidator.checkWidth(width);

      // 4 - Image Height
      int height = fm.readInt();
      FieldValidator.checkHeight(height);

      // 2 - Unknown (1)
      // 2 - Number of Mipmaps? [+1]
      // 4 - Flags
      // 4 - Unknown (64/4)
      // 4 - Unknown (1)
      fm.skip(16);

      // X - Pixels
      ImageResource imageResource = null;
      if (imageFormat == 4) {
        int[] palette = ImageFormatReader.readPaletteRGBA(fm, 16);
        imageResource = ImageFormatReader.read4BitPaletted(fm, width, height, palette);
        imageResource = ImageFormatReader.doubleAlpha(imageResource);
      }
      else if (imageFormat == 8) {
        int[] palette = ImageFormatReader.readPaletteRGBA(fm, 256);
        palette = ImageSwizzler.stripePalettePS2(palette);
        imageResource = ImageFormatReader.read8BitPaletted(fm, width, height, palette);
        imageResource = ImageFormatReader.doubleAlpha(imageResource);
      }
      else {
        ErrorLogger.log("[Viewer_DATTEXARC_TEX] Unknown Image Format: " + imageFormat);
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

      PreviewPanel_Image ivp = (PreviewPanel_Image) preview;
      Image image = ivp.getImage();
      int width = ivp.getImageWidth();
      int height = ivp.getImageHeight();

      if (width == -1 || height == -1) {
        return;
      }

      // Try to get the existing ImageResource (if it was stored), otherwise build a new one
      ImageResource imageResource = ((PreviewPanel_Image) preview).getImageResource();
      if (imageResource == null) {
        imageResource = new ImageResource(image, width, height);
      }

      // Extract the original resource into a byte[] array, so we can reference it
      int srcLength = (int) resourceBeingReplaced.getDecompressedLength();
      if (srcLength > 62) {
        srcLength = 62; // allows enough reading for the header, but not much of the original image data
      }
      //byte[] srcBytes = new byte[(int) resourceBeingReplaced.getDecompressedLength()];
      byte[] srcBytes = new byte[srcLength];
      FileManipulator src = new FileManipulator(new ByteBuffer(srcBytes));
      resourceBeingReplaced.extract(src);
      src.seek(0);

      // Build the new file using the src[] and adding in the new image content

      // 4 - Unknown
      // 4 - Unknown
      fm.writeBytes(src.readBytes(8));

      // 4 - Image Format? (8=8bit Paletted, 4=4bit Paletted)
      src.skip(4);
      fm.writeInt(8); // force to 8-bit 

      // 4 - Image Width
      fm.writeInt(width);
      src.skip(4);

      // 4 - Image Height
      fm.writeInt(height);
      src.skip(4);

      // 2 - Unknown (1)
      fm.writeBytes(src.readBytes(2));

      // 2 - Number of Mipmaps? [+1]
      int numMipmaps = src.readShort();
      fm.writeShort(numMipmaps);

      numMipmaps++;

      // 4 - Flags
      // 4 - Unknown (64/4)
      // 4 - Unknown (1)
      fm.writeBytes(src.readBytes(12));

      // We've forced it to 8-bit for simplicity
      ImageManipulator im = new ImageManipulator(imageResource);
      im.changeColorCountRGBSingleAlpha(256);

      int[] palette = im.getPalette();
      palette = ImageSwizzler.stripePalettePS2(palette);
      palette = ImageFormatWriter.halveAlpha(palette);
      ImageFormatWriter.writePaletteRGBA(fm, palette);

      if (numMipmaps == 1) {
        // only 1 mipmap (the image itself)
        byte[] pixels = im.getPixelBytes();
        fm.writeBytes(pixels);
      }
      else {
        // multiple mipmaps
        ImageManipulator[] mipmaps = im.generatePalettedMipmaps();

        for (int i = 0; i < numMipmaps; i++) {
          byte[] pixels = mipmaps[i].getPixelBytes();
          fm.writeBytes(pixels);
        }

      }

      src.close();
      fm.close();

    }
    catch (Throwable t) {
      logError(t);
    }
  }

}