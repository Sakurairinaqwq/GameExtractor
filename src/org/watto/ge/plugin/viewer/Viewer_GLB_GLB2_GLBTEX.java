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
import org.watto.Language;
import org.watto.TemporarySettings;
import org.watto.component.PreviewPanel;
import org.watto.component.PreviewPanel_Image;
import org.watto.component.WSPopup;
import org.watto.datatype.Archive;
import org.watto.datatype.ImageResource;
import org.watto.datatype.Palette;
import org.watto.datatype.PalettedImageResource;
import org.watto.datatype.Resource;
import org.watto.ge.helper.FieldValidator;
import org.watto.ge.helper.ImageFormatReader;
import org.watto.ge.helper.PaletteManager;
import org.watto.ge.plugin.AllFilesPlugin;
import org.watto.ge.plugin.ArchivePlugin;
import org.watto.ge.plugin.ViewerPlugin;
import org.watto.ge.plugin.archive.Plugin_GLB_GLB2;
import org.watto.io.FileManipulator;
import org.watto.io.buffer.ByteBuffer;
import org.watto.io.converter.ByteConverter;

/**
**********************************************************************************************

**********************************************************************************************
**/
public class Viewer_GLB_GLB2_GLBTEX extends ViewerPlugin {

  /**
  **********************************************************************************************
  
  **********************************************************************************************
  **/
  public Viewer_GLB_GLB2_GLBTEX() {
    super("GLB_GLB2_GLBTEX", "GLB_GLB2_GLBTEX Image");
    setExtensions("glb_tex");

    setGames("DemonStar");
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
      if (plugin instanceof Plugin_GLB_GLB2) {
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

      // 4 - Image Width
      if (FieldValidator.checkWidth(fm.readInt())) {
        rating += 5;
      }

      // 4 - Image Height
      if (FieldValidator.checkHeight(fm.readInt())) {
        rating += 5;
      }

      int imageFormat = fm.readInt();
      if (imageFormat == 0 || imageFormat == 1) {
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

      // 4 - Image Width
      int width = fm.readInt();
      FieldValidator.checkWidth(width);

      // 4 - Image Height
      int height = fm.readInt();
      FieldValidator.checkHeight(height);

      // 4 - Image Format (1=raw data, 0=compressed)
      int imageFormat = fm.readInt();

      // get the Palette
      if (PaletteManager.getNumPalettes() <= 0) {
        Resource[] resources = Archive.getResources();
        int numResources = resources.length;
        for (int i = 0; i < numResources; i++) {
          Resource currentResource = resources[i];
          if (currentResource.getExtension().equals("pal")) {
            // found the color palette file - need to extract it and read the colors

            // so the thumbnails generate properly, lets grab the rest of this image data first, as opening the palette file will break the thumbnails otherwise
            fm.seek(0);
            byte[] imageBytes = fm.readBytes((int) arcSize);
            fm.close();
            fm = new FileManipulator(new ByteBuffer(imageBytes));
            fm.seek(12);

            // now read the palette
            int[] palette = extractPalette(currentResource);
            PaletteManager.addPalette(new Palette(palette));
            break;
          }
        }

        if (PaletteManager.getNumPalettes() <= 0) {
          // tell the user to load the main archive first
          String property = "Viewer_GLB_GLB2_GLBTEX_PromptForOtherArchive";

          if (!TemporarySettings.has(property)) {
            Language.set("WSLabel_" + property + "_Text", "The color palette needs to be loaded from the main archive.\nPlease load the main archive first, and preview an image in it, to load the palette.");
            WSPopup.showMessage(property);
            TemporarySettings.set(property, false);
          }
          ErrorLogger.log("[Viewer_GLB_GLB2_GLBTEX] Missing color palette file");
          return null;
        }
      }

      ImageResource imageResource = null;
      if (imageFormat == 1) {
        // raw
        imageResource = ImageFormatReader.read8BitPaletted(fm, width, height, true);
      }
      else if (imageFormat == 0) {
        // compressed

        int numPixels = width * height;
        int[] pixels = new int[numPixels];

        int[] lineOffsets = new int[height + 1];
        for (int h = 0; h < height; h++) {
          // 4 - Line Data Offset (0 if the line is empty)
          int offset = fm.readInt();
          FieldValidator.checkOffset(offset, arcSize);
          lineOffsets[h] = offset;
        }
        lineOffsets[height] = (int) arcSize;

        for (int h = 0; h < height; h++) {
          int offset = lineOffsets[h];
          if (offset == 0) {
            continue; // empty line
          }
          else {
            fm.relativeSeek(offset); // should be at the right place anyway

            int nextOffset = lineOffsets[h + 1];
            while (fm.getOffset() < nextOffset) {
              // 4 - Pixel Start Offset (in the line)
              int startOffset = (h * width) + fm.readInt();

              // 4 - Line Number
              fm.skip(4);

              // 4 - Pixel Data Length
              int dataLength = fm.readInt();
              if (dataLength == -1 || dataLength == 0) {
                break; // EOF
              }

              FieldValidator.checkOffset(startOffset, numPixels);
              FieldValidator.checkLength(dataLength, width);

              // X - Pixel Data
              for (int w = 0; w < dataLength; w++) {
                pixels[startOffset] = ByteConverter.unsign(fm.readByte());
                startOffset++;
              }
            }
          }
        }

        imageResource = new PalettedImageResource(pixels, width, height, PaletteManager.getCurrentPalette().getPalette());

      }
      else {
        ErrorLogger.log("[Viewer_GLB_GLB2_GLBTEX] Unknown image format: " + imageFormat);
        return null;
      }

      fm.close();

      imageResource = ImageFormatReader.doubleAlpha(imageResource);

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
  Extracts a PALT resource and then gets the Palette from it
  **********************************************************************************************
  **/
  public int[] extractPalette(Resource paltResource) {
    try {
      ByteBuffer buffer = new ByteBuffer((int) paltResource.getLength());
      FileManipulator fm = new FileManipulator(buffer);
      paltResource.extract(fm);

      fm.seek(0); // back to the beginning of the byte array

      int colorCount = 256;
      int[] palette = new int[colorCount];

      for (int i = 0; i < colorCount; i++) {
        // INPUT = RGB
        // OUTPUT = ARGB

        int r = ByteConverter.unsign(fm.readByte());
        int g = ByteConverter.unsign(fm.readByte());
        int b = ByteConverter.unsign(fm.readByte());

        // double the values, because it's so darn dark in this game!
        r *= 2;
        g *= 2;
        b *= 2;

        if (r > 255) {
          r = 255;
        }
        if (g > 255) {
          g = 255;
        }
        if (b > 255) {
          b = 255;
        }

        palette[i] = ((r << 16) | (g << 8) | b | (255 << 24));
      }

      fm.close();

      return palette;
    }
    catch (Throwable t) {
      logError(t);
      return new int[0];
    }
  }

}