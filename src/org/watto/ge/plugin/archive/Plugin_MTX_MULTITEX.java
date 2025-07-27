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
import org.watto.ge.plugin.viewer.Viewer_MTX_MULTITEX_MTXTEX;
import org.watto.io.FileManipulator;
import org.watto.task.TaskProgressManager;

/**
**********************************************************************************************

**********************************************************************************************
**/
public class Plugin_MTX_MULTITEX extends ArchivePlugin {

  /**
  **********************************************************************************************
  
  **********************************************************************************************
  **/
  public Plugin_MTX_MULTITEX() {

    super("MTX_MULTITEX", "MTX_MULTITEX");

    //         read write replace rename
    setProperties(true, false, true, false);

    setGames("Indiana Jones and the Emperors Tomb");
    setExtensions("mtx"); // MUST BE LOWER CASE
    setPlatforms("PC");

    setFileTypes(new FileType("mtx_tex", "Texture Image", FileType.TYPE_IMAGE));

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
      if (fm.readString(8).equals("MULTITEX")) {
        rating += 50;
      }

      fm.skip(12);

      // Number Of Files
      if (FieldValidator.checkNumFiles(fm.readInt())) {
        rating += 5;
      }

      long arcSize = fm.getLength();

      // Directory Length
      if (FieldValidator.checkLength(fm.readInt(), arcSize)) {
        rating += 5;
      }

      fm.skip(4);

      // Directory Entry Length
      if (FieldValidator.checkLength(fm.readInt(), arcSize)) {
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

      // 8 - Header (MULTITEX)
      // 2 - Unknown (4/5)
      // 2 - Unknown (2)
      // 4 - Unknown (1)
      // 4 - null
      fm.skip(20);

      // 4 - Number of Files
      int numFiles = fm.readInt();
      FieldValidator.checkNumFiles(numFiles);

      // 4 - Details Directory Length
      // 4 - Unknown (24)
      fm.skip(8);

      // 4 - Directory Entry Length (120/128)
      int dirEntryLength = fm.readInt();
      FieldValidator.checkLength(dirEntryLength, arcSize);

      Resource[] resources = new Resource[numFiles];
      TaskProgressManager.setMaximum(numFiles);

      // Loop through directory
      for (int i = 0; i < numFiles; i++) {

        if (dirEntryLength == 80) {
          // 4 - Unknown
          // 4 - Unknown
          // 4 - Unknown
          fm.skip(12);

          // 32 - Filename (null terminated, padded with junk)
          String filename = fm.readNullString(32);
          FieldValidator.checkFilename(filename);
          filename += ".mtx_tex";

          // 4 - Image Width
          int width = fm.readInt();
          FieldValidator.checkWidth(width);

          // 4 - Image Height
          int height = fm.readInt();
          FieldValidator.checkHeight(height);

          // 4 - Unknown (32)
          // 4 - Unknown
          fm.skip(8);

          // 4 - Number of Mipmaps
          int mipmapCount = fm.readInt();

          // 4 - Unknown
          fm.skip(4);

          // 4 - Image Format (0=RGBA, 1=DXT1, 3=DXT3, 5=DXT5)
          int imageFormat = fm.readInt();

          String imageFormatString = null;
          if (imageFormat == 0) {
            imageFormatString = "RGBA";
          }
          else if (imageFormat == 1) {
            imageFormatString = "DXT1";
          }
          else if (imageFormat == 3) {
            imageFormatString = "DXT3";
          }
          else if (imageFormat == 5) {
            imageFormatString = "DXT5";
          }
          else {
            ErrorLogger.log("[MTX_MULTITEX] Unknown image format: " + imageFormat);
            imageFormatString = "" + imageFormat;
          }

          // 4 - File Offset
          long offset = fm.readInt();
          FieldValidator.checkOffset(offset, arcSize);

          // 4 - File Length
          long length = fm.readInt();
          FieldValidator.checkLength(length, arcSize);

          //path,name,offset,length,decompLength,exporter
          Resource resource = new Resource(path, filename, offset, length);
          resource.addProperty("Width", width);
          resource.addProperty("Height", height);
          resource.addProperty("ImageFormat", imageFormatString);
          resource.addProperty("MipmapCount", mipmapCount);
          resources[i] = resource;

          TaskProgressManager.setValue(i);
        }
        else {
          // 4 - File ID
          // 4 - Unknown (-1)
          fm.skip(8);

          if (dirEntryLength == 128) {
            // 4 - Unknown (-1)
            // 4 - Unknown
            fm.skip(8);
          }

          // 4 - Unknown
          // 4 - Unknown
          // 32 - Description (null terminated, padded with junk)
          fm.skip(40);

          // 32 - Filename (null terminated, padded with junk)
          String filename = fm.readNullString(32);
          FieldValidator.checkFilename(filename);
          filename += ".mtx_tex";

          // 4 - Image Width
          int width = fm.readInt();
          FieldValidator.checkWidth(width);

          // 4 - Image Height
          int height = fm.readInt();
          FieldValidator.checkHeight(height);

          // 4 - null
          // 4 - Unknown (32)
          // 4 - Unknown
          fm.skip(12);

          // 4 - Number of Mipmaps
          int mipmapCount = fm.readInt();

          // 4 - Unknown
          fm.skip(4);

          // 4 - Image Format (0=RGBA, 1=DXT1, 3=DXT3, 5=DXT5)
          int imageFormat = fm.readInt();

          String imageFormatString = null;
          if (imageFormat == 0) {
            imageFormatString = "RGBA";
          }
          else if (imageFormat == 1) {
            imageFormatString = "DXT1";
          }
          else if (imageFormat == 3) {
            imageFormatString = "DXT3";
          }
          else if (imageFormat == 5) {
            imageFormatString = "DXT5";
          }
          else {
            ErrorLogger.log("[MTX_MULTITEX] Unknown image format: " + imageFormat);
            imageFormatString = "" + imageFormat;
          }

          // 4 - File Offset
          long offset = fm.readInt();
          FieldValidator.checkOffset(offset, arcSize);

          // 4 - File Length
          long length = fm.readInt();
          FieldValidator.checkLength(length, arcSize);

          //path,name,offset,length,decompLength,exporter
          Resource resource = new Resource(path, filename, offset, length);
          resource.addProperty("Width", width);
          resource.addProperty("Height", height);
          resource.addProperty("ImageFormat", imageFormatString);
          resource.addProperty("MipmapCount", mipmapCount);
          resources[i] = resource;

          TaskProgressManager.setValue(i);
        }
      }

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

      // Write Header Data

      // 8 - Header (MULTITEX)
      // 2 - Unknown (4/5)
      // 2 - Unknown (2)
      // 4 - Unknown (1)
      // 4 - null
      // 4 - Number of Files
      // 4 - Details Directory Length
      // 4 - Unknown (24)
      fm.writeBytes(src.readBytes(32));

      // 4 - Directory Entry Length (120/128)
      int dirEntryLength = src.readInt();
      fm.writeInt(dirEntryLength);

      // Write Directory
      TaskProgressManager.setMessage(Language.get("Progress_WritingDirectory"));
      long offset = 36 + (dirEntryLength * numFiles);
      for (int i = 0; i < numFiles; i++) {
        Resource resource = resources[i];
        long length = resource.getDecompressedLength();

        int height = 0;
        int width = 0;
        int mipmapCount = 0;
        try {
          height = Integer.parseInt(resource.getProperty("Height"));
          width = Integer.parseInt(resource.getProperty("Width"));
          mipmapCount = Integer.parseInt(resource.getProperty("MipmapCount"));
        }
        catch (Throwable t) {
          //
        }

        if (dirEntryLength == 80) {
          // 4 - Unknown
          // 4 - Unknown
          // 4 - Unknown
          // 32 - Filename (null terminated, padded with junk)
          fm.writeBytes(src.readBytes(44));

          // 4 - Image Width
          fm.writeInt(width);
          src.skip(4);

          // 4 - Image Height
          fm.writeInt(height);
          src.skip(4);

          // 4 - Unknown (32)
          // 4 - Unknown
          fm.writeBytes(src.readBytes(8));

          // 4 - Number of Mipmaps
          fm.writeInt(mipmapCount);
          src.skip(4);

          // 4 - Unknown
          // 4 - Image Format (0=RGBA, 1=DXT1, 3=DXT3, 5=DXT5)
          fm.writeBytes(src.readBytes(8));

          // 4 - File Offset
          fm.writeInt(offset);
          src.skip(4);

          // 4 - File Length
          fm.writeInt(length);
          src.skip(4);

          offset += length;
        }
        else {
          // 4 - File ID
          // 4 - Unknown (-1)
          fm.writeBytes(src.readBytes(8));

          if (dirEntryLength == 128) {
            fm.writeBytes(src.readBytes(8));
          }

          // 4 - Unknown
          // 4 - Unknown
          // 32 - Description (null terminated, padded with junk)
          // 32 - Filename (null terminated, padded with junk)
          fm.writeBytes(src.readBytes(72));

          // 4 - Image Width
          fm.writeInt(width);
          src.skip(4);

          // 4 - Image Height
          fm.writeInt(height);
          src.skip(4);

          // 4 - null
          // 4 - Unknown (32)
          // 4 - Unknown
          fm.writeBytes(src.readBytes(12));

          // 4 - Number of Mipmaps
          fm.writeInt(mipmapCount);
          src.skip(4);

          // 4 - Unknown
          // 4 - Image Format (0=RGBA, 1=DXT1, 3=DXT3, 5=DXT5)
          fm.writeBytes(src.readBytes(8));

          // 4 - File Offset
          fm.writeInt(offset);
          src.skip(4);

          // 4 - File Length
          fm.writeInt(length);
          src.skip(4);

          offset += length;
        }
      }

      // Write Files
      TaskProgressManager.setMessage(Language.get("Progress_WritingFiles"));
      write(resources, fm);

      //ExporterPlugin exporter = new Exporter_ZLib();
      //long[] compressedLengths = write(exporter,resources,fm);

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

      PreviewPanel imagePreviewPanel = loadFileForConversion(resourceBeingReplaced, fileToReplaceWith, "mtx_tex");
      if (imagePreviewPanel == null) {
        // no conversion needed, or wasn't able to be converted
        return fileToReplaceWith;
      }

      // The plugin that will do the conversion
      Viewer_MTX_MULTITEX_MTXTEX converterPlugin = new Viewer_MTX_MULTITEX_MTXTEX();

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
