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
package org.watto.ge.plugin.archive;

import java.io.File;

import org.watto.ErrorLogger;
import org.watto.Language;
import org.watto.Settings;
import org.watto.component.PreviewPanel;
import org.watto.datatype.FileType;
import org.watto.datatype.Resource;
import org.watto.ge.helper.FieldValidator;
import org.watto.ge.plugin.ArchivePlugin;
import org.watto.ge.plugin.viewer.Viewer_XPR_XPR1_DXT;
import org.watto.io.FileManipulator;
import org.watto.io.converter.ByteConverter;
import org.watto.task.TaskProgressManager;

/**
**********************************************************************************************

**********************************************************************************************
**/
public class Plugin_XPR_XPR0 extends ArchivePlugin {

  /**
  **********************************************************************************************
  
  **********************************************************************************************
  **/
  public Plugin_XPR_XPR0() {

    super("XPR_XPR0", "XPR_XPR0");

    //         read write replace rename
    setProperties(true, false, true, false);

    setGames("Far Cry");
    setExtensions("xpr");
    setPlatforms("XBox");

    setFileTypes(new FileType("dxt", "DXT Image", FileType.TYPE_IMAGE));

    setCanConvertOnReplace(true);

  }

  /**
  **********************************************************************************************
  
  **********************************************************************************************
  **/
  @Override
  public int getMatchRating(FileManipulator fm) {
    try {

      int rating = 0;

      if (FieldValidator.checkExtension(fm, extensions)) {
        rating += 25;
      }

      // Header
      if (fm.readString(4).equals("XPR0")) {
        rating += 50;
      }

      long arcSize = fm.getLength();

      // Archive Size
      if (FieldValidator.checkEquals(fm.readInt(), arcSize)) {
        rating += 5;
      }

      // First File Offset
      if (FieldValidator.checkOffset(fm.readInt(), arcSize)) {
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
   * Reads an [archive] File into the Resources
   **********************************************************************************************
   **/
  @Override
  public Resource[] read(File path) {
    try {

      // NOTE - Compressed files MUST know their DECOMPRESSED LENGTH
      //      - Uncompressed files MUST know their LENGTH

      addFileTypes();

      //ExporterPlugin exporter = Exporter_ZLib.getInstance();

      // RESETTING GLOBAL VARIABLES

      FileManipulator fm = new FileManipulator(path, false);

      long arcSize = fm.getLength();

      // 4 - Header (XPR0)
      // 4 - Archive Size
      fm.skip(8);

      // 4 - First File Offset (2048)
      int firstFileOffset = fm.readInt();
      FieldValidator.checkOffset(firstFileOffset, arcSize);

      int numFiles = firstFileOffset / 20; /// approximate
      int realNumFiles = 0;

      Resource[] resources = new Resource[numFiles];
      TaskProgressManager.setMaximum(numFiles);

      // Loop through directory
      while (fm.getOffset() < firstFileOffset) {
        // 2 - Unknown (1)
        // 2 - Unknown (4)
        int hasMore = fm.readInt();
        if (hasMore == -1) {
          break;
        }

        // 8 - Offset [+firstFileOffset]
        long offset = fm.readLong() + firstFileOffset;
        FieldValidator.checkOffset(offset, arcSize);

        // 4 - Flags
        //   4 bits - dma
        //   4 bits - dimensions // Is this a 1D, 2D, or 3D texture
        //   8 bits - format     // The format of the texture data (see the table below)
        //   4 bits - levels     // Number of mipmap levels
        //   4 bits - width      // Width of the texture in the following format: actual_width = (1 << width)
        //   4 bits - height     // Height of the texture in the following format: actual_height = (1 << height)
        //   4 bits - depth      // Depth of the texture (for 3D textures) in the following format: actual_depth = (1 << depth)
        fm.skip(1);

        int imageFormat = ByteConverter.unsign(fm.readByte());

        int byte3 = ByteConverter.unsign(fm.readByte());
        int width = 1 << ((byte3 >> 4) & 15);
        int numMipmaps = (byte3 & 15);

        int byte4 = ByteConverter.unsign(fm.readByte());
        int height = 1 << (byte4 & 15);

        // 4 - Alternate Size (if the image isn't a power of 2)
        fm.skip(4);

        String filename = Resource.generateFilename(realNumFiles) + ".dxt";

        // NV097_SET_TEXTURE_FORMAT_COLOR_SZ_A1R5G5B5       0x02  2
        // NV097_SET_TEXTURE_FORMAT_COLOR_SZ_X1R5G5B5       0x03  3
        // NV097_SET_TEXTURE_FORMAT_COLOR_SZ_A4R4G4B4       0x04  4
        // NV097_SET_TEXTURE_FORMAT_COLOR_SZ_R5G6B5         0x05  5
        // NV097_SET_TEXTURE_FORMAT_COLOR_SZ_A8R8G8B8       0x06  6
        // NV097_SET_TEXTURE_FORMAT_COLOR_SZ_X8R8G8B8       0x07  7
        // NV097_SET_TEXTURE_FORMAT_COLOR_SZ_I8_A8R8G8B8    0x0B  11
        // NV097_SET_TEXTURE_FORMAT_COLOR_L_DXT1_A1R5G5B5   0x0C  12
        // NV097_SET_TEXTURE_FORMAT_COLOR_L_DXT23_A8R8G8B8  0x0E  14
        // NV097_SET_TEXTURE_FORMAT_COLOR_L_DXT45_A8R8G8B8  0x0F  15
        // NV097_SET_TEXTURE_FORMAT_COLOR_LU_IMAGE_R5G6B5   0x11  17
        // NV097_SET_TEXTURE_FORMAT_COLOR_LU_IMAGE_A8R8G8B8 0x12  18
        // NV097_SET_TEXTURE_FORMAT_COLOR_SZ_A8             0x19  25
        // NV097_SET_TEXTURE_FORMAT_COLOR_LU_IMAGE_X8R8G8B8 0x1E  30
        // NV097_SET_TEXTURE_FORMAT_COLOR_LU_IMAGE_DEPTH_Y16_FIXED 0x30  48
        // NV097_SET_TEXTURE_FORMAT_COLOR_SZ_A8B8G8R8       0x3A  58
        // NV097_SET_TEXTURE_FORMAT_COLOR_LU_IMAGE_A8B8G8R8 0x3F  63
        String imageFormatString = null;
        if (imageFormat == 2) {
          imageFormatString = "ARGB1555";
        }
        else if (imageFormat == 4) {
          imageFormatString = "ARGB4444";
        }
        else if (imageFormat == 5) {
          imageFormatString = "RGB565";
        }
        else if (imageFormat == 6) {
          imageFormatString = "ARGB";
        }
        else if (imageFormat == 12) {
          imageFormatString = "DXT1";
        }
        else if (imageFormat == 14) {
          imageFormatString = "DXT3";
        }
        else if (imageFormat == 15) {
          imageFormatString = "DXT5";
        }
        else {
          imageFormatString = "" + imageFormat;
        }

        //path,name,offset,length,decompLength,exporter
        //resources[realNumFiles] = new Resource(path, filename, offset);
        Resource resource = new Resource(path, filename, offset);
        resource.addProperty("Width", width);
        resource.addProperty("Height", height);
        resource.addProperty("ImageFormat", imageFormatString);
        resource.addProperty("MipmapCount", numMipmaps);
        resources[realNumFiles] = resource;

        TaskProgressManager.setValue(realNumFiles);
        realNumFiles++;
      }

      resources = resizeResources(resources, realNumFiles);
      calculateFileSizes(resources, arcSize);

      fm.close();

      return resources;

    }
    catch (Throwable t) {
      logError(t);
      return null;
    }
  }

  /**
   **********************************************************************************************
   * Writes an [archive] File with the contents of the Resources. The archive is written using
   * data from the initial archive - it isn't written from scratch.
   **********************************************************************************************
   **/
  @Override
  public void replace(Resource[] resources, File path) {
    try {

      FileManipulator fm = new FileManipulator(path, true);
      FileManipulator src = new FileManipulator(new File(Settings.getString("CurrentArchive")), false);

      int numFiles = resources.length;
      TaskProgressManager.setMaximum(numFiles);

      // Calculations
      TaskProgressManager.setMessage(Language.get("Progress_PerformingCalculations"));

      long headerSize = 12 + (numFiles * 20) + 4;
      int paddingSize = calculatePadding(headerSize, 2048);
      headerSize += paddingSize;

      long archiveSize = headerSize;
      for (int i = 0; i < numFiles; i++) {
        archiveSize += resources[i].getDecompressedLength();
      }

      // Write Header Data

      // 4 - Header (XPR0)
      fm.writeBytes(src.readBytes(4));

      // 4 - Archive Length
      src.skip(4);
      fm.writeInt(archiveSize);

      // 4 - First File Offset (2048)
      fm.writeBytes(src.readBytes(4));

      // Write Directory
      TaskProgressManager.setMessage(Language.get("Progress_WritingDirectory"));
      long offset = 0;
      for (int i = 0; i < numFiles; i++) {
        Resource resource = resources[i];
        long length = resource.getDecompressedLength();

        // 2 - Unknown (1)
        // 2 - Resource Type (4=Texture)
        fm.writeBytes(src.readBytes(4));

        // 4 - File Offset [+firstFileOffset]
        src.skip(4);
        fm.writeInt(offset);

        // 4 - Lock (null)
        fm.writeBytes(src.readBytes(4));

        if (resource.isReplaced()) {
          String imageFormatString = resource.getProperty("ImageFormat");
          if (imageFormatString == null || imageFormatString.equals("")) {
            imageFormatString = "DXT3";
          }

          // NV097_SET_TEXTURE_FORMAT_COLOR_SZ_A1R5G5B5       0x02  2
          // NV097_SET_TEXTURE_FORMAT_COLOR_SZ_X1R5G5B5       0x03  3
          // NV097_SET_TEXTURE_FORMAT_COLOR_SZ_A4R4G4B4       0x04  4
          // NV097_SET_TEXTURE_FORMAT_COLOR_SZ_R5G6B5         0x05  5
          // NV097_SET_TEXTURE_FORMAT_COLOR_SZ_A8R8G8B8       0x06  6
          // NV097_SET_TEXTURE_FORMAT_COLOR_SZ_X8R8G8B8       0x07  7
          // NV097_SET_TEXTURE_FORMAT_COLOR_SZ_I8_A8R8G8B8    0x0B  11
          // NV097_SET_TEXTURE_FORMAT_COLOR_L_DXT1_A1R5G5B5   0x0C  12
          // NV097_SET_TEXTURE_FORMAT_COLOR_L_DXT23_A8R8G8B8  0x0E  14
          // NV097_SET_TEXTURE_FORMAT_COLOR_L_DXT45_A8R8G8B8  0x0F  15
          // NV097_SET_TEXTURE_FORMAT_COLOR_LU_IMAGE_R5G6B5   0x11  17
          // NV097_SET_TEXTURE_FORMAT_COLOR_LU_IMAGE_A8R8G8B8 0x12  18
          // NV097_SET_TEXTURE_FORMAT_COLOR_SZ_A8             0x19  25
          // NV097_SET_TEXTURE_FORMAT_COLOR_LU_IMAGE_X8R8G8B8 0x1E  30
          // NV097_SET_TEXTURE_FORMAT_COLOR_LU_IMAGE_DEPTH_Y16_FIXED 0x30  48
          // NV097_SET_TEXTURE_FORMAT_COLOR_SZ_A8B8G8R8       0x3A  58
          // NV097_SET_TEXTURE_FORMAT_COLOR_LU_IMAGE_A8B8G8R8 0x3F  63
          int imageFormat = 14;

          if (imageFormatString.equals("ARGB1555")) {
            imageFormat = 2;
          }
          else if (imageFormatString.equals("ARGB4444")) {
            imageFormat = 4;
          }
          else if (imageFormatString.equals("RGB565")) {
            imageFormat = 5;
          }
          else if (imageFormatString.equals("ARGB")) {
            imageFormat = 6;
          }
          else if (imageFormatString.equals("DXT1")) {
            imageFormat = 12;
          }
          else if (imageFormatString.equals("DXT3")) {
            imageFormat = 14;
          }
          else if (imageFormatString.equals("DXT5")) {
            imageFormat = 15;
          }

          int height = 0;
          int width = 0;
          int numMipmaps = 1;

          try {
            width = Integer.parseInt(resource.getProperty("Width"));
            height = Integer.parseInt(resource.getProperty("Height"));
            numMipmaps = Integer.parseInt(resource.getProperty("MipmapCount"));
          }
          catch (Throwable t) {
            //
          }

          // work out the power value of the height and width
          if (height != 0) {
            int power = 0;
            while ((height & 1) == 0 && height != 0) {
              power++;
              height >>= 1;
            }
            height = power;
          }

          if (width != 0) {
            int power = 0;
            while ((width & 1) == 0 && width != 0) {
              power++;
              width >>= 1;
            }
            width = power;
          }

          // 4 - Flags
          //   4 bits - dma
          //   4 bits - dimensions // Is this a 1D, 2D, or 3D texture
          //   8 bits - format     // The format of the texture data (see the table below)
          //   4 bits - levels     // Number of mipmap levels
          //   4 bits - width      // Width of the texture in the following format: actual_width = (1 << width)
          //   4 bits - height     // Height of the texture in the following format: actual_height = (1 << height)
          //   4 bits - depth      // Depth of the texture (for 3D textures) in the following format: actual_depth = (1 << depth)
          fm.writeBytes(src.readBytes(1));

          src.skip(1);
          fm.writeByte(imageFormat);

          //int byte3 = ByteConverter.unsign(fm.readByte());
          src.skip(1);
          int byte3 = ((width & 15) << 4) | (numMipmaps & 15);
          fm.writeByte(byte3);

          int byte4 = ByteConverter.unsign(src.readByte());
          byte4 = (byte4 & 240) | (height & 15);
          fm.writeByte(byte4);

          // 4 - Alternate Size
          fm.writeInt(0);
          src.skip(4);
        }
        else {
          // 4 - Flags
          // 4 - Alternate Size
          fm.writeBytes(src.readBytes(8));
        }

        offset += length;
      }

      // 4 - End of Directory Marker (all 255's)
      fm.writeInt(-1);

      // X - Padding to first file offset with (byte)173
      for (int p = 0; p < paddingSize; p++) {
        fm.writeByte(173);
      }

      // Write Files
      TaskProgressManager.setMessage(Language.get("Progress_WritingFiles"));
      write(resources, fm);

      src.close();
      fm.close();

    }
    catch (Throwable t) {
      logError(t);
    }
  }

  /**
   **********************************************************************************************
   When replacing files, if the file is of a certain type, it will be converted before replace
   @param resourceBeingReplaced the Resource in the archive that is being replaced
   @param fileToReplaceWith the file on your PC that will replace the Resource. This file is the
          one that will be converted into a different format, if applicable.
   @return the converted file, if conversion was applicable/successful, else the original fileToReplaceWith
   **********************************************************************************************
   **/
  @Override
  public File convertOnReplace(Resource resourceBeingReplaced, File fileToReplaceWith) {
    try {

      PreviewPanel imagePreviewPanel = loadFileForConversion(resourceBeingReplaced, fileToReplaceWith, "dxt");
      if (imagePreviewPanel == null) {
        // no conversion needed, or wasn't able to be converted
        return fileToReplaceWith;
      }

      // The plugin that will do the conversion
      Viewer_XPR_XPR1_DXT converterPlugin = new Viewer_XPR_XPR1_DXT();

      String beingReplacedExtension = resourceBeingReplaced.getExtension();
      File destination = new File(fileToReplaceWith.getAbsolutePath() + "." + beingReplacedExtension);
      if (destination.exists()) {
        destination.delete();
      }

      FileManipulator fmOut = new FileManipulator(destination, true);
      converterPlugin.replace(resourceBeingReplaced, imagePreviewPanel, fmOut);
      fmOut.close();

      return destination;

    }
    catch (Throwable t) {
      ErrorLogger.log(t);
      return fileToReplaceWith;
    }
  }

}
