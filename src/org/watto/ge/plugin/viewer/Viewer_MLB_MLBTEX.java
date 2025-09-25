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

import org.watto.ErrorLogger;
import org.watto.Language;
import org.watto.SingletonManager;
import org.watto.TemporarySettings;
import org.watto.component.PreviewPanel;
import org.watto.component.PreviewPanel_Image;
import org.watto.component.WSPopup;
import org.watto.datatype.Archive;
import org.watto.datatype.ImageResource;
import org.watto.datatype.Palette;
import org.watto.datatype.Resource;
import org.watto.ge.helper.FieldValidator;
import org.watto.ge.helper.ImageFormatReader;
import org.watto.ge.helper.PaletteManager;
import org.watto.ge.plugin.AllFilesPlugin;
import org.watto.ge.plugin.ArchivePlugin;
import org.watto.ge.plugin.ViewerPlugin;
import org.watto.ge.plugin.archive.Plugin_MLB;
import org.watto.io.FileManipulator;
import org.watto.io.converter.ByteConverter;

/**
**********************************************************************************************

**********************************************************************************************
**/
public class Viewer_MLB_MLBTEX extends ViewerPlugin {

  /**
  **********************************************************************************************
  
  **********************************************************************************************
  **/
  public Viewer_MLB_MLBTEX() {
    super("MLB_MLBTEX", "MLB_MLBTEX Image");
    setExtensions("mlb_tex");

    setGames("Screamer");
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
      if (plugin instanceof Plugin_MLB) {
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

      // get the width/height from the properties of the image resource, which were read by the ArchivePlugin
      Object resourceObject = SingletonManager.get("CurrentResource");
      if (resourceObject == null || !(resourceObject instanceof Resource)) {
        return null;
      }
      Resource resource = (Resource) resourceObject;

      try {
        height = Integer.parseInt(resource.getProperty("Height"));
        width = Integer.parseInt(resource.getProperty("Width"));
      }
      catch (Throwable t) {
        //
      }

      // first need to read a block of 256 width, and then shrink it down to the right size
      //int realWidth = width;
      //width = 256;
      //height = 256;

      if (!PaletteManager.hasPalettes()) {
        // load the COL file
        try {
          File paletteFile = ArchivePlugin.getDirectoryFile(Archive.getBasePath(), "COL");
          FileManipulator palFM = new FileManipulator(paletteFile, false);

          int[] palette = new int[256];
          for (int i = 0; i < 256; i++) {
            // INPUT = RGB
            int r = ByteConverter.unsign(palFM.readByte());
            int g = ByteConverter.unsign(palFM.readByte());
            int b = ByteConverter.unsign(palFM.readByte());
            int a = 255;

            // Double the color values
            r *= 2;
            g *= 2;
            b *= 2;

            // Make sure it's within 0-255
            if (r > 255) {
              r = 255;
            }
            if (g > 255) {
              g = 255;
            }
            if (b > 255) {
              b = 255;
            }

            // OUTPUT = ARGB
            palette[i] = (r << 16) | (g << 8) | b | (a << 24);
          }

          PaletteManager.addPalette(new Palette(palette));
          palFM.close();
        }
        catch (Throwable t) {
          ErrorLogger.log("[Viewer_MLB_MLBTEX] Palette file couldn't be found or read.");
        }
      }

      if (PaletteManager.getNumPalettes() <= 0) {
        // tell the user extract the COL file too
        String property = "Viewer_MLB_MLBTEX_PromptForOtherFile";

        if (!TemporarySettings.has(property)) {
          Language.set("WSLabel_" + property + "_Text", "The COL file needs to be extracted to the same directory before the image can be viewed.");
          WSPopup.showMessage(property);
          TemporarySettings.set(property, false);
        }
        ErrorLogger.log("[Viewer_MLB_MLBTEX] Missing color palette file");
        return null;
      }

      // X - Pixels
      ImageResource imageResource = ImageFormatReader.read8BitPaletted(fm, width, height, true);

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