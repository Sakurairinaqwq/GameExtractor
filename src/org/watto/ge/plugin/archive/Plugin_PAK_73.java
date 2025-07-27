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

import org.watto.datatype.Archive;
import org.watto.datatype.FileType;
import org.watto.datatype.Resource;
import org.watto.ge.helper.FieldValidator;
import org.watto.ge.plugin.ArchivePlugin;
import org.watto.io.FileManipulator;
import org.watto.io.FilenameSplitter;
import org.watto.task.TaskProgressManager;

/**
**********************************************************************************************

**********************************************************************************************
**/
public class Plugin_PAK_73 extends ArchivePlugin {

  /**
  **********************************************************************************************
  
  **********************************************************************************************
  **/
  public Plugin_PAK_73() {

    super("PAK_73", "PAK_73");

    //         read write replace rename
    setProperties(true, false, false, false);

    setGames("Indiana Jones and the Emperors Tomb");
    setExtensions("pak"); // MUST BE LOWER CASE
    setPlatforms("PS2");

    // MUST BE LOWER CASE !!!
    setFileTypes(new FileType("vag", "VAG Audio", FileType.TYPE_AUDIO),
        new FileType("mt2", "MT2 Image", FileType.TYPE_IMAGE));

    setTextPreviewExtensions("csv", "tpr", "pin", "gin"); // LOWER CASE

    //setCanScanForFileTypes(true);

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
      if (fm.readInt() == 335913) {
        rating += 25;
      }

      long arcSize = fm.getLength();

      // Directory Length
      if (FieldValidator.checkLength(fm.readInt(), arcSize)) {
        rating += 5;
      }

      if (fm.readInt() == 0) {
        rating += 5;
      }

      // Directory Length
      if (FieldValidator.checkLength(fm.readInt(), arcSize)) {
        rating += 5;
      }

      if (fm.readInt() == 4096) {
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

      // 4 - Header (335913)
      // 4 - Names Directory Length
      // 4 - null
      fm.skip(12);

      // 4 - Names Directory Length
      int dirLength = fm.readInt();
      FieldValidator.checkLength(dirLength, arcSize);

      // 4 - Unknown (4096)
      fm.skip(4);

      // 4 - Number of Names
      int numNames = fm.readInt();
      FieldValidator.checkNumFiles(numNames);

      // NAME OFFSETS DIRECTORY
      fm.skip(numNames * 4);

      // NAMES DIRECTORY
      fm.skip(dirLength);

      // 4 - Unknown
      fm.skip(4);

      // 4 - Number of Entries
      int numEntries = fm.readInt();
      FieldValidator.checkNumFiles(numEntries);

      // UNKNOWN DIRECTORY 1
      fm.skip(numEntries * 2);

      // 4 - Unknown
      // 4 - Unknown
      fm.skip(8);

      // 4 - Number of directories (in the root)
      int numDirs = fm.readInt();
      FieldValidator.checkNumFiles(numDirs);

      // DIRECTORY NAMES DIRECTORY
      fm.skip(numDirs * 36);

      // 4 - File Type ID Number?
      int marker = fm.readInt();
      while (fm.getOffset() < arcSize && marker != -1) {
        FieldValidator.checkPositive(marker);

        // 4 - File Type Name Length
        int typeNameLength = fm.readInt();
        FieldValidator.checkFilenameLength(typeNameLength);

        // X - File Type Name (mt2, sl2, ms2,...)
        fm.skip(typeNameLength);
        //String fileType = fm.readString(typeNameLength);
        //System.out.println(fileType + " at " + (fm.getOffset() - 4 - typeNameLength));

        // 4 - Flags? (4/8)
        fm.skip(4);

        // 4 - Number of Sub-Folders
        int numSubFolders = fm.readInt();
        FieldValidator.checkNumFiles(numSubFolders);

        // for each sub-folder
        for (int s = 0; s < numSubFolders; s++) {
          // 4 - Sub-folder Name Length
          int subFolderNameLength = fm.readInt();
          FieldValidator.checkFilenameLength(subFolderNameLength);

          // X - Sub-folder Name
          fm.skip(subFolderNameLength);
        }

        // 4 - Flags? (64/32/256/512/0)
        fm.skip(4);

        // 4 - Number of Files in this sub-folder (can be null)
        int numFilesInSub = fm.readInt();
        FieldValidator.checkNumFiles(numFilesInSub + 1);//+1 to allow nulls

        // for each file in this sub-folder
        for (int f = 0; f < numFilesInSub; f++) {
          // 2 - Unknown
          // 2 - Unknown
          fm.skip(4);

          // 4 - Count
          int count = fm.readInt();
          FieldValidator.checkNumFiles(count);

          // for each count
          // 2 - Unknown
          // 4 - Unknown
          // 2 - Unknown
          // 4 - Unknown
          fm.skip(count * 12);
        }

        // 4 - File Type ID Number? (or end of file marker == -1)
        marker = fm.readInt();

      }

      //
      // FINALLY!!! Now we can start reading through the individual files 
      //

      // force buffer reload
      long currentPos = fm.getOffset();
      fm.getBuffer().setBufferSize(256); // small fast reads
      fm.seek(0);
      fm.seek(currentPos);

      int numFiles = Archive.getMaxFiles();

      Resource[] resources = new Resource[numFiles];
      TaskProgressManager.setMaximum(arcSize);

      // Loop through directory
      int realNumFiles = 0;
      while (fm.getOffset() < arcSize) {
        long startOffset = fm.getOffset();

        // 4 - Unknown (1)
        int entryIndicator = fm.readInt();

        // 4 - Offset of the Previous Field
        int previousOffset = fm.readInt();

        if (entryIndicator == 0 && previousOffset == 0) {
          // END OF FILE
          break;
        }

        // 4 - File Length (including all these headers)
        int lengthWithHeaders = fm.readInt();
        FieldValidator.checkLength(lengthWithHeaders, arcSize);

        // 4 - File Length (File Data only)
        int length = fm.readInt();
        FieldValidator.checkLength(length, arcSize);

        // 4 - null
        fm.skip(4);

        // 4 - Filename Length
        int filenameLength = fm.readInt();
        FieldValidator.checkFilenameLength(filenameLength);

        // X - Filename
        String filename = fm.readString(filenameLength);

        //System.out.println(filename + " at " + (fm.getOffset() - filenameLength - 24));

        // 4 - Name 2 Length (can be null)
        int name2Length = fm.readInt();
        FieldValidator.checkFilenameLength(name2Length + 1);//+1 to allow for nulls

        // X - Name 2
        if (name2Length != 0) {
          String filename2 = fm.readString(name2Length);

          String extension = FilenameSplitter.getExtension(filename);

          filename = filename.substring(0, filename.length() - extension.length() - 1) + "_" + filename2 + "." + extension;
        }

        // 4 - Name 3 Length (can be null)
        int name3Length = fm.readInt();
        FieldValidator.checkFilenameLength(name3Length + 1);//+1 to allow for nulls

        // X - Name 3
        fm.skip(name3Length);

        // X - File Data
        long offset = fm.getOffset();

        if (length == 0) {
          // work out the actual file length from the lengthWithHeaders field
          length = (int) (lengthWithHeaders - (offset - startOffset));
        }

        fm.skip(length);

        //path,name,offset,length,decompLength,exporter
        resources[realNumFiles] = new Resource(path, filename, offset, length);
        realNumFiles++;

        TaskProgressManager.setValue(offset);
      }

      resources = resizeResources(resources, realNumFiles);

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
  If an archive doesn't have filenames stored in it, the scanner can come here to try to work out
  what kind of file a Resource is. This method allows the plugin to provide additional plugin-specific
  extensions, which will be tried before any standard extensions.
  @return null if no extension can be determined, or the extension if one can be found
  **********************************************************************************************
  **/
  @Override
  public String guessFileExtension(Resource resource, byte[] headerBytes, int headerInt1, int headerInt2, int headerInt3, short headerShort1, short headerShort2, short headerShort3, short headerShort4, short headerShort5, short headerShort6) {

    /*
    if (headerInt1 == 2037149520) {
      return "js";
    }
    */

    return null;
  }

}
