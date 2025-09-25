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
import org.watto.ge.plugin.viewer.Viewer_VDF_GOTHIC_TEX_ZTEX;
import org.watto.io.FileManipulator;
import org.watto.task.TaskProgressManager;

/**
**********************************************************************************************

**********************************************************************************************
**/
public class Plugin_VDF_GOTHIC extends ArchivePlugin {

  /**
  **********************************************************************************************
  
  **********************************************************************************************
  **/
  public Plugin_VDF_GOTHIC() {

    super("VDF_GOTHIC", "VDF_GOTHIC");

    //         read write replace rename
    setProperties(true, false, true, false);

    setGames("Gothic");
    setExtensions("vdf"); // MUST BE LOWER CASE
    setPlatforms("PC");

    setFileTypes(new FileType("tex", "Texture Image", FileType.TYPE_IMAGE));

    setTextPreviewExtensions("mds"); // LOWER CASE

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
      if (fm.readString(6).equals("Gothic")) {
        rating += 25;
      }

      fm.skip(250);

      if (fm.readString(6).equals("PSVDSC")) {
        rating += 25;
      }

      fm.skip(2);

      if (fm.readString(4).equals("2.00")) {
        rating += 5;
      }
      fm.skip(4);

      // Number Of Files
      if (FieldValidator.checkNumFiles(fm.readInt())) {
        rating += 5;
      }

      // Number Of Files
      if (FieldValidator.checkNumFiles(fm.readInt())) {
        rating += 5;
      }

      fm.skip(4);

      long arcSize = fm.getLength();

      // Directory Length
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

      // 256 - Description ("Gothic" + more characters depending on the file + (byte)26 to fill)
      // 16 - Header ("PSVDSC_V2.00" + (bytes)13,10,13,10)
      fm.skip(272);

      // 4 - Number of Entries
      int numEntries = fm.readInt();
      FieldValidator.checkNumFiles(numEntries);

      // 4 - Number of Files
      int numFiles = fm.readInt();
      FieldValidator.checkNumFiles(numFiles);

      // 4 - Timestamp
      // 4 - File Data Length
      fm.skip(8);

      // 4 - Directory Offset (296)
      long dirOffset = fm.readInt();
      FieldValidator.checkOffset(dirOffset, arcSize);

      // 4 - Entry Length (80)
      fm.seek(dirOffset);

      int numDirs = numEntries - numFiles;

      Resource[] resources = new Resource[numFiles];
      TaskProgressManager.setMaximum(numFiles);

      // Loop through directory
      String[] dirNames = new String[numDirs];
      int currentDirs = 0;
      String dirName = "";
      int realNumFiles = 0;
      for (int e = 0; e < numEntries; e++) {

        // 64 - File or Folder Name (filled with (byte)32)
        String filename = fm.readString(64).trim();

        // 4 - File Offset
        int offset = fm.readInt();

        // 4 - File Length
        int length = fm.readInt();

        // 4 - Entry Type (0x80000000 = Directory, 0x40000000 = Last)
        int type = fm.readInt();

        // 4 - Flags
        fm.skip(4);

        if ((type & 0x80000000) == 0x80000000) {
          // directory
          dirNames[currentDirs] = filename;
          currentDirs++;

          dirName += filename + "\\";
        }
        else {
          // file

          filename = dirName + filename;

          FieldValidator.checkOffset(offset, arcSize);
          FieldValidator.checkLength(length, arcSize);

          //path,name,offset,length,decompLength,exporter
          resources[realNumFiles] = new Resource(path, filename, offset, length);

          TaskProgressManager.setValue(realNumFiles);

          realNumFiles++;

          if ((type & 0x40000000) == 0x40000000) {
            currentDirs--;

            dirName = "";
            for (int n = 0; n < currentDirs; n++) {
              dirName += dirNames[n] + "\\";
            }

          }

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

      // Calculations
      TaskProgressManager.setMessage(Language.get("Progress_PerformingCalculations"));

      long filesSize = 0;
      for (int i = 0; i < numFiles; i++) {
        filesSize += resources[i].getDecompressedLength();
      }

      // Write Header Data

      // 256 - Description ("Gothic" + more characters depending on the file + (byte)26 to fill)
      // 16 - Header ("PSVDSC_V2.00" + (bytes)13,10,13,10)
      fm.writeBytes(src.readBytes(272));

      // 4 - Number of Entries
      int srcEntries = src.readInt();
      fm.writeInt(srcEntries);

      // 4 - Number of Files
      // 4 - Timestamp
      fm.writeBytes(src.readBytes(8));

      // 4 - File Data Length
      fm.writeInt(filesSize);
      src.skip(4);

      // 4 - Directory Offset (296)
      // 4 - Entry Length (80)
      fm.writeBytes(src.readBytes(8));

      // Write Directory
      TaskProgressManager.setMessage(Language.get("Progress_WritingDirectory"));
      long offset = 296 + (80 * srcEntries);
      int realNumFiles = 0;
      for (int e = 0; e < srcEntries; e++) {
        // 64 - File or Folder Name (filled with (byte)32)
        fm.writeBytes(src.readBytes(64));

        // 4 - File Offset
        int srcOffset = src.readInt();

        // 4 - File Length
        int srcLength = src.readInt();

        // 4 - Entry Type (0x80000000 = Directory, 0x40000000 = Last)
        int srcType = src.readInt();

        // 4 - Flags
        int srcFlags = src.readInt();

        if ((srcType & 0x80000000) == 0x80000000) {
          // directory
          fm.writeInt(srcOffset);
          fm.writeInt(srcLength);
          fm.writeInt(srcType);
          fm.writeInt(srcFlags);
        }
        else {
          // file

          Resource resource = resources[realNumFiles];
          realNumFiles++;

          long length = resource.getDecompressedLength();

          fm.writeInt(offset);
          fm.writeInt(length);
          fm.writeInt(srcType);
          fm.writeInt(srcFlags);

          offset += length;
        }

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

      PreviewPanel imagePreviewPanel = loadFileForConversion(resourceBeingReplaced, fileToReplaceWith, "tex");
      if (imagePreviewPanel == null) {
        // no conversion needed, or wasn't able to be converted
        return fileToReplaceWith;
      }

      // The plugin that will do the conversion
      Viewer_VDF_GOTHIC_TEX_ZTEX converterPlugin = new Viewer_VDF_GOTHIC_TEX_ZTEX();

      String beingReplacedExtension = resourceBeingReplaced.getExtension();
      File destination = new File(fileToReplaceWith.getAbsolutePath() + "." + beingReplacedExtension);
      if (destination.exists()) {
        destination.delete();
      }

      FileManipulator fmOut = new FileManipulator(destination, true);
      converterPlugin.write(imagePreviewPanel, fmOut);
      fmOut.close();

      return destination;

    }
    catch (Throwable t) {
      ErrorLogger.log(t);
      return fileToReplaceWith;
    }
  }

}
