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
import org.watto.ge.helper.PaletteManager;
import org.watto.ge.plugin.AllFilesPlugin;
import org.watto.ge.plugin.ArchivePlugin;
import org.watto.ge.plugin.ViewerPlugin;
import org.watto.ge.plugin.archive.Plugin_SHP_110;
import org.watto.io.FileManipulator;
import org.watto.io.converter.ByteConverter;

/**
**********************************************************************************************

**********************************************************************************************
**/
public class Viewer_SHP_110_SHPTEX extends ViewerPlugin {

  /**
  **********************************************************************************************
  
  **********************************************************************************************
  **/
  public Viewer_SHP_110_SHPTEX() {
    super("SHP_110_SHPTEX", "SHP_110_SHPTEX Image");
    setExtensions("shp_tex");

    setGames("Panzer General");
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
      if (plugin instanceof Plugin_SHP_110) {
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

      // 2 - Image Height
      if (FieldValidator.checkHeight(fm.readShort())) {
        rating += 5;
      }

      // 2 - Image Width
      if (FieldValidator.checkWidth(fm.readShort())) {
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

      // 2 - Image Height
      short height = fm.readShort();
      FieldValidator.checkHeight(height);

      // 2 - Image Width
      short width = fm.readShort();
      FieldValidator.checkWidth(width);

      // 2 - Center X
      // 2 - Center Y
      fm.skip(4);

      // 4 - X Start
      int x1 = fm.readInt();
      FieldValidator.checkRange(x1, 0, width);

      // 4 - Y Start
      int y1 = fm.readInt();
      FieldValidator.checkRange(y1, 0, height);

      // 4 - X End
      int x2 = fm.readInt();
      FieldValidator.checkRange(x2, 0, width);

      // 4 - Y End
      int y2 = fm.readInt();
      FieldValidator.checkRange(y2, 0, height);

      // Get the palette
      Object resourceObject = SingletonManager.get("CurrentResource");
      if (resourceObject == null || !(resourceObject instanceof Resource)) {
        return null;
      }
      Resource resource = (Resource) resourceObject;

      int[] palette = null;
      try {
        int paletteID = Integer.parseInt(resource.getProperty("PaletteID"));
        palette = PaletteManager.getPalette(paletteID).getPalette();
      }
      catch (Throwable t) {
      }

      if (palette == null || palette.length < 256) {
        return null;
      }

      // X - Pixels
      int numPixels = width * (height + 1); // add an extra line for overflows, which do occur in some files
      int[] pixels = new int[numPixels];
      int transparent = 0;

      // Read the RLE encoding
      int x = 0;
      int y = y1;
      while (y < y2) {
        int buf = ByteConverter.unsign(fm.readByte());
        int flag = buf % 2;
        int count = buf / 2;

        if (count == 0 && flag == 1) {
          // transparent
          count = ByteConverter.unsign(fm.readByte());
          for (int i = 0; i < count; i++) {
            int pixelIndex = (y * width) + (x1 + x);
            pixels[pixelIndex] = transparent;
            x++;
          }
        }
        else {
          if (count == 0) {
            // End of the current line
            //System.out.println("End of line " + y + " of " + y2);
            y++;
            x = 0;
          }
          else {
            if (flag == 0) {
              // repeating a color <count> times
              int pixel = palette[ByteConverter.unsign(fm.readByte())];
              for (int i = 0; i < count; i++) {
                int pixelIndex = (y * width) + (x1 + x);
                pixels[pixelIndex] = pixel;
                x++;
              }
            }
            else {
              // count != 0 && flag == 1 --> Read the next b bytes as color values

              for (int i = 0; i < count; i++) {
                int pixel = palette[ByteConverter.unsign(fm.readByte())];
                int pixelIndex = (y * width) + (x1 + x);
                pixels[pixelIndex] = pixel;
                x++;
              }
            }
          }
        }
      }

      fm.close();

      ImageResource imageResource = new ImageResource(pixels, width, height);

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