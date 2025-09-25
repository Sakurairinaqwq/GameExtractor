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

import org.watto.SingletonManager;
import org.watto.component.PreviewPanel;
import org.watto.component.PreviewPanel_Image;
import org.watto.datatype.Archive;
import org.watto.datatype.ImageResource;
import org.watto.datatype.Resource;
import org.watto.ge.helper.ImageFormatReader;
import org.watto.ge.helper.ImageFormatWriter;
import org.watto.ge.helper.ImageManipulator;
import org.watto.ge.plugin.ArchivePlugin;
import org.watto.ge.plugin.ViewerPlugin;
import org.watto.ge.plugin.archive.Plugin_XPR_XPR0;
import org.watto.ge.plugin.archive.Plugin_XPR_XPR1;
import org.watto.io.FileManipulator;

/**
**********************************************************************************************

**********************************************************************************************
**/
public class Viewer_XPR_XPR1_DXT extends ViewerPlugin {

  /**
  **********************************************************************************************
  
  **********************************************************************************************
  **/
  public Viewer_XPR_XPR1_DXT() {
    super("XPR_XPR1_DXT", "Generic XBox Image");
    setExtensions("dxt");

    setGames("Generic XBox Archive");
    setPlatforms("XBox");
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
      if (plugin instanceof Plugin_XPR_XPR1 || plugin instanceof Plugin_XPR_XPR0) {
        rating += 50;
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
      String imageFormat = null;

      // get the height from the properties of the image resource, which were read by the ArchivePlugin
      Object resourceObject = SingletonManager.get("CurrentResource");
      if (resourceObject == null || !(resourceObject instanceof Resource)) {
        return null;
      }
      Resource resource = (Resource) resourceObject;

      try {
        height = Integer.parseInt(resource.getProperty("Height"));
        width = Integer.parseInt(resource.getProperty("Width"));
        imageFormat = resource.getProperty("ImageFormat");
      }
      catch (Throwable t) {
        //
      }

      if (height == 0 || width == 0) {
        return null;
      }

      if (imageFormat == null || imageFormat.equals("")) {
        imageFormat = "DXT3";
      }

      // X - Pixels
      ImageResource imageResource = null;

      if (imageFormat.equals("ARGB1555")) {
        imageResource = ImageFormatReader.readARGB1555(fm, width, height);
      }
      else if (imageFormat.equals("ARGB4444")) {
        imageResource = ImageFormatReader.readARGB4444(fm, width, height);
      }
      else if (imageFormat.equals("RGB565")) {
        imageResource = ImageFormatReader.readRGB565(fm, width, height);
      }
      else if (imageFormat.equals("ARGB")) {
        imageResource = ImageFormatReader.readARGB(fm, width, height);
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

      String imageFormat = resourceBeingReplaced.getProperty("ImageFormat");
      int numMipmaps = 1;
      try {
        numMipmaps = Integer.parseInt(resourceBeingReplaced.getProperty("MipmapCount"));
      }
      catch (Throwable t) {
        //
      }

      if (imageFormat == null || imageFormat.equals("")) {
        imageFormat = "DXT3";
      }

      // X - Pixels
      if (numMipmaps == 1) {
        if (imageFormat.equals("ARGB1555")) {
          ImageFormatWriter.writeARGB1555(fm, imageResource);
        }
        else if (imageFormat.equals("ARGB4444")) {
          ImageFormatWriter.writeARGB4444(fm, imageResource);
        }
        else if (imageFormat.equals("RGB565")) {
          ImageFormatWriter.writeRGB565(fm, imageResource);
        }
        else if (imageFormat.equals("ARGB")) {
          ImageFormatWriter.writeARGB(fm, imageResource);
        }
        else if (imageFormat.equals("DXT1")) {
          ImageFormatWriter.writeDXT1(fm, imageResource);
        }
        else if (imageFormat.equals("DXT3")) {
          ImageFormatWriter.writeDXT3(fm, imageResource);
        }
        else if (imageFormat.equals("DXT5")) {
          ImageFormatWriter.writeDXT5(fm, imageResource);
        }
      }
      else {
        ImageManipulator im = new ImageManipulator(imageResource);
        ImageResource[] mipmaps = im.generateMipmaps();
        for (int m = 0; m < numMipmaps; m++) {
          ImageResource mipmap = mipmaps[m];

          if (imageFormat.equals("ARGB1555")) {
            ImageFormatWriter.writeARGB1555(fm, mipmap);
          }
          else if (imageFormat.equals("ARGB4444")) {
            ImageFormatWriter.writeARGB4444(fm, mipmap);
          }
          else if (imageFormat.equals("RGB565")) {
            ImageFormatWriter.writeRGB565(fm, mipmap);
          }
          else if (imageFormat.equals("ARGB")) {
            ImageFormatWriter.writeARGB(fm, mipmap);
          }
          else if (imageFormat.equals("DXT1")) {
            ImageFormatWriter.writeDXT1(fm, mipmap);
          }
          else if (imageFormat.equals("DXT3")) {
            ImageFormatWriter.writeDXT3(fm, mipmap);
          }
          else if (imageFormat.equals("DXT5")) {
            ImageFormatWriter.writeDXT5(fm, mipmap);
          }
        }
      }

      fm.close();

      // Now update the properties on the Resource to match the new width/height
      resourceBeingReplaced.setProperty("Width", "" + width);
      resourceBeingReplaced.setProperty("Height", "" + height);

    }
    catch (Throwable t) {
      logError(t);
    }
  }

}