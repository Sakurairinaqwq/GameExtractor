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

import org.watto.component.PreviewPanel;
import org.watto.component.PreviewPanel_Image;
import org.watto.datatype.Archive;
import org.watto.datatype.ImageResource;
import org.watto.ge.helper.FieldValidator;
import org.watto.ge.helper.PaletteGenerator;
import org.watto.ge.plugin.AllFilesPlugin;
import org.watto.ge.plugin.ArchivePlugin;
import org.watto.ge.plugin.ViewerPlugin;
import org.watto.ge.plugin.archive.Plugin_FST_4;
import org.watto.io.FileManipulator;
import org.watto.io.converter.ByteConverter;
import org.watto.io.converter.ShortConverter;

/**
**********************************************************************************************

**********************************************************************************************
**/
public class Viewer_FST_4_BMP extends ViewerPlugin {

  /**
  **********************************************************************************************
  
  **********************************************************************************************
  **/
  public Viewer_FST_4_BMP() {
    super("FST_4_BMP", "FST_4_BMP Image");
    setExtensions("bmp");

    setGames("Super Bubsy");
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
      if (plugin instanceof Plugin_FST_4) {
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

      // 2 - Image Width
      if (FieldValidator.checkWidth(fm.readShort())) {
        rating += 5;
      }

      // 2 - Image Height
      if (FieldValidator.checkHeight(fm.readShort())) {
        rating += 5;
      }

      // 2 - Compressed Length
      if (ShortConverter.unsign(fm.readShort()) + 8 == fm.getLength()) {
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

      // 2 - Image Width
      short width = fm.readShort();
      FieldValidator.checkWidth(width);

      // 2 - Image Height
      short height = fm.readShort();
      FieldValidator.checkHeight(height);

      // 2 - Compressed Length
      int length = ShortConverter.unsign(fm.readShort());
      FieldValidator.checkLength(length, arcSize);

      // 2 - RLE Type? (3/4)
      fm.skip(2);

      // X - RLE-Compressed Pixels
      int numPixels = width * height;
      int[] pixels = new int[numPixels];

      int[] palette = null;
      // see if we can find the "TITLE.PAL" file in a parent directory
      //String paletteName = "TITLE.PAL";
      //String paletteName = "BUBSY.BMP";
      String paletteName = "ENDMOVIE.PAL";
      File parentFile = Archive.getBasePath().getParentFile();
      File paletteFile = new File(parentFile.getAbsolutePath() + File.separatorChar + paletteName);
      if (!paletteFile.exists()) {
        parentFile = parentFile.getParentFile();
        paletteFile = new File(parentFile.getAbsolutePath() + File.separatorChar + paletteName);
      }

      if (paletteFile.exists() && paletteFile.isFile()) {// && paletteFile.length() == 1048) {
        // load the palette file
        FileManipulator paletteFM = new FileManipulator(paletteFile, false);
        paletteFM.seek(24);
        //paletteFM.seek(54);
        //paletteFM.seek(94); // works for bubsy only

        palette = new int[256];
        for (int i = 0; i < 256; i++) {
          // INPUT = RGB + flag
          // OUTPUT = ARGB
          palette[i] = ((ByteConverter.unsign(paletteFM.readByte()) << 16) | (ByteConverter.unsign(paletteFM.readByte()) << 8) | ByteConverter.unsign(paletteFM.readByte()) | (255 << 24));
          //palette[i] = ((ByteConverter.unsign(paletteFM.readByte())) | (ByteConverter.unsign(paletteFM.readByte()) << 8) | (ByteConverter.unsign(paletteFM.readByte()) << 16) | (255 << 24));
          paletteFM.skip(1);
        }

        paletteFM.close();
      }
      else {
        // can't find it in the current directory, or in the parent directory, so just use a default palette
        palette = PaletteGenerator.getMode13hPalette().getPalette();
      }

      int readData = 0;
      int outPos = 0;
      int rememberedCode = -1;
      int rememberedPixel = -1;
      while (readData < length) {
        // if we've completed a line of the output, we need to reset the remembered pixels
        if (outPos % width == 0) {
          //System.out.println("Next line");
          rememberedCode = -1;
          rememberedPixel = -1;
        }

        // 1 - Code
        int code = ByteConverter.unsign(fm.readByte());
        readData++;
        //System.out.println("Code = " + code);

        if (code == 0) {
          if (rememberedCode != -1) {
            // forget the remembered pixel
            rememberedCode = -1;
          }
          else {
            // 1 - Pixel
            int value = ByteConverter.unsign(fm.readByte());
            readData++;

            // Repeat that pixel 1 time only
            int pixel = palette[value];
            pixels[outPos] = pixel;
            outPos++;
          }
        }
        else if (code < 128) {
          // 1 - Pixel
          int value = ByteConverter.unsign(fm.readByte());
          readData++;

          // Repeat that pixel Code+1 times
          int pixel = palette[value];

          int repeat = code + 1;
          for (int r = 0; r < repeat; r++) {
            pixels[outPos] = pixel;
            outPos++;
          }
        }
        else if (code >= 128) {
          if (rememberedCode == -1) {
            // 1 - Pixel
            int value = ByteConverter.unsign(fm.readByte());
            readData++;

            // Repeat that pixel 1 time only
            int pixel = palette[value];
            pixels[outPos] = pixel;
            outPos++;

            // Remember this pixel
            rememberedCode = code;
            rememberedPixel = pixel;
          }
          else if (code == rememberedCode) {
            // Write the remembered pixel 1 time
            int pixel = rememberedPixel;
            pixels[outPos] = pixel;
            outPos++;
          }
          else if (code == 192) {
            // forget the remembered pixel
            rememberedCode = -1;
          }
          else {
            // the code is a pixel value, write it out once
            int pixel = palette[code];
            pixels[outPos] = pixel;
            outPos++;
          }
        }
      }

      // X - Pixels
      ImageResource imageResource = new ImageResource(pixels, width, height);

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