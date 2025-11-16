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
import org.watto.ge.plugin.archive.Plugin_PAK_73;
import org.watto.io.FileManipulator;
import org.watto.io.buffer.ByteBuffer;

/**
**********************************************************************************************

**********************************************************************************************
**/
public class Viewer_PAK_73_MT2 extends ViewerPlugin {

  /**
  **********************************************************************************************
  
  **********************************************************************************************
  **/
  public Viewer_PAK_73_MT2() {
    super("PAK_73_MT2", "PAK_73_MT2 Image");
    setExtensions("mt2");

    setGames("Indiana Jones and the Emperors Tomb");
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
      if (plugin instanceof Plugin_PAK_73) {
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

      fm.skip(28);

      if (fm.readInt() == 0) {
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

      if (fm.readInt() == 0) {
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

      // 32 - Filename (null terminated, filled with nulls)
      fm.skip(32);

      // 4 - Image Width
      int width = fm.readInt();
      FieldValidator.checkWidth(width);

      // 4 - Image Height
      int height = fm.readInt();
      FieldValidator.checkHeight(height);

      // 4 - null
      // 4 - Unknown (0/1/4)
      // 4 - Length of Color Palette + Image Data
      // 4 - Unknown (8)
      fm.skip(16);

      // 4 - Image Format? (73=Paletted, 71=?)
      int imageFormat = fm.readInt();
      if (imageFormat != 71 && imageFormat != 72 && imageFormat != 73) {
        ErrorLogger.log("[Viewer_PAK_73_MT2] Unknown image format: " + imageFormat);
        return null;
      }

      // 4 - Unknown (1)
      fm.skip(4);

      ImageResource imageResource = null;

      if (imageFormat == 71) {
        // grayscale 4-bit
        int dataLength = (width * height) / 2;

        // X - 4-bit pixel values
        byte[] rawBytes = fm.readBytes(dataLength);
        byte[] bytes = ImageSwizzler.unswizzlePS24Bit(rawBytes, width, height);

        /*
        // This isn't right yet - still trying to work it out
        
        // convert to 8-bit pixels
        int numPixels = width * height;
        int[] pixels = new int[numPixels];
        
        for (int i = 0, r = 0; i < numPixels; i += 2, r++) { // r=read
          int pixel = ByteConverter.unsign(bytes[r]);
        
          int pixel2 = (pixel >> 4) & 15;
          int pixel1 = (pixel & 15);
        
          pixels[i] = pixel1;
          pixels[i + 1] = pixel2;
        }
        
        // X - Colors for each 4x8 block
        for (int y = 0; y < height; y += 4) {
          for (int x = 0; x < width; x += 8) {
            // read color 1 (RGBA5551)
            int byte1 = ByteConverter.unsign(fm.readByte());
            int byte2 = ByteConverter.unsign(fm.readByte());
        
            int color1r = ((byte2 >> 2) & 31) * 8;
            int color1g = (((byte2 & 3) << 3) | ((byte1 >> 5) & 7)) * 8;
            int color1b = (byte1 & 31) * 8;
            int color1a = (byte2 >> 7) * 255;
        
            // read color 2 (RGBA5551)
            byte1 = ByteConverter.unsign(fm.readByte());
            byte2 = ByteConverter.unsign(fm.readByte());
        
            int color2r = ((byte2 >> 2) & 31) * 8;
            int color2g = (((byte2 & 3) << 3) | ((byte1 >> 5) & 7)) * 8;
            int color2b = (byte1 & 31) * 8;
            int color2a = (byte2 >> 7) * 255;
        
            float stepR = (color1r - color2r) / 16;
            float stepG = (color1g - color2g) / 16;
            float stepB = (color1b - color2b) / 16;
            float stepA = (color1a - color2a) / 16;
        
            // generate the 16 colors between these 2
            int[] colors = new int[16];
            colors[0] = ((color1r << 16) | (color1g << 8) | color1b | (color1a << 24));
            colors[15] = ((color2r << 16) | (color2g << 8) | color2b | (color2a << 24));
            for (int s = 1; s < 15; s++) {
              colors[s] = (((int) (color1r + stepR * s) << 16) | ((int) (color1g + stepG * s) << 8) | (int) (color1b + stepB * s) | ((int) (color1a + stepA * s) << 24));
            }
        
            // OUTPUT = ARGB
            //int color = ((r << 16) | (g << 8) | b | (a << 24));
        
            for (int by = 0; by < 4; ++by) {
              for (int bx = 0; bx < 8; ++bx) {
        
                int pixelPos = (y + by) * width + x + bx;
                int intensity = pixels[pixelPos];
                //System.out.println(intensity);
        
                //int color = ((r << 16) | (g << 8) | b | (a << 24));
        
                pixels[pixelPos] = colors[intensity];
              }
            }
          }
        }
        
        imageResource = new ImageResource(pixels, width, height);
        */

        FileManipulator swizFM = new FileManipulator(new ByteBuffer(bytes));
        imageResource = ImageFormatReader.read4BitPaletted(swizFM, width, height);
        swizFM.close();

      }
      else {

        // COLOR PALETTE
        int[] palette = ImageFormatReader.readPaletteRGBA(fm, 256);
        palette = ImageSwizzler.unstripePalettePS2(palette);
        palette = ImageFormatReader.doubleAlpha(palette);

        // IMAGE DATA
        // X - Pixels

        imageResource = ImageFormatReader.read8BitPaletted(fm, width, height, palette);

        if (imageFormat == 72) {
          // no swizzle
        }
        else if (width > 16 && height > 16) {
          imageResource.setPixels(ImageSwizzler.unswizzlePS2(imageResource.getImagePixels(), width, height));
        }
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
      if (srcLength > 64) {
        srcLength = 64; // allows enough reading for the header and color palette, but not much of the original image data
      }
      byte[] srcBytes = new byte[(int) resourceBeingReplaced.getDecompressedLength()];
      FileManipulator src = new FileManipulator(new ByteBuffer(srcBytes));
      resourceBeingReplaced.extract(src);
      src.seek(0);

      // Build the new file using the src[] and adding in the new image content

      // 32 - Filename (null terminated, filled with nulls)
      fm.writeBytes(src.readBytes(32));

      // 4 - Image Width
      fm.writeInt(width);
      src.skip(4);

      // 4 - Image Height
      fm.writeInt(height);
      src.skip(4);

      // 4 - null
      // 4 - Unknown (0/1/4)
      fm.writeBytes(src.readBytes(8));

      // 4 - Length of Color Palette + Image Data
      src.skip(4);
      int newSize = 1024 + (width * height) + 2;
      fm.writeInt(newSize);

      // 4 - Unknown (8)
      fm.writeBytes(src.readBytes(4));

      // 4 - Image Format? (73=Paletted, 71=?)
      int imageFormat = src.readInt();
      fm.writeInt(imageFormat);
      if (imageFormat != 72 && imageFormat != 73) {
        ErrorLogger.log("[Viewer_PAK_73_MT2] Unknown image format: " + imageFormat);
        return;
      }

      // 4 - Unknown (1)
      fm.writeBytes(src.readBytes(4));

      // Apply swizzling (or not)
      if (imageFormat == 72) {
        // no swizzle
      }
      else if (width > 16 && height > 16) {
        // swizzle
        imageResource.setPixels(ImageSwizzler.swizzlePS2(imageResource.getImagePixels(), width, height));
      }

      // COLOR PALETTE
      ImageManipulator im = new ImageManipulator(imageResource);
      im.changeColorCount(256);

      int[] palette = im.getPalette();
      palette = ImageSwizzler.stripePalettePS2(palette);
      palette = ImageFormatReader.halveAlpha(palette);

      ImageFormatWriter.writePaletteRGBA(fm, palette);

      // IMAGE DATA
      // X - Pixels (we've already done swizzling, if needed)
      int[] pixels = imageResource.getPixels();
      int numPixels = pixels.length;
      for (int p = 0; p < numPixels; p++) {
        fm.writeByte(pixels[p]);
      }

      // 2 - null
      fm.writeShort(0);

      fm.close();

    }
    catch (Throwable t) {
      logError(t);
    }
  }

}