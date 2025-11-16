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
import org.watto.datatype.Resource;
import org.watto.ge.helper.FieldValidator;
import org.watto.ge.plugin.ArchivePlugin;
import org.watto.io.FileManipulator;
import org.watto.io.converter.ByteConverter;
import org.watto.task.TaskProgressManager;

/**
**********************************************************************************************

**********************************************************************************************
**/
public class Plugin_PAK_EEPAK_3 extends ArchivePlugin {

  /**
  **********************************************************************************************
  
  **********************************************************************************************
  **/
  public Plugin_PAK_EEPAK_3() {

    super("PAK_EEPAK_3", "PAK_EEPAK_3");

    //         read write replace rename
    setProperties(true, false, false, false);

    setGames("Fear for Sale: City of the Past",
        "Fear for Sale: Endless Voyage");
    setExtensions("pak"); // MUST BE LOWER CASE
    setPlatforms("PC");

    // MUST BE LOWER CASE !!!
    //setFileTypes(new FileType("txt", "Text Document", FileType.TYPE_DOCUMENT),
    //             new FileType("bmp", "Bitmap Image", FileType.TYPE_IMAGE)
    //             );

    //setTextPreviewExtensions("colours", "rat", "screen", "styles"); // LOWER CASE

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

      long arcSize = fm.getLength();

      // Archive Size
      if (FieldValidator.checkEquals(fm.readInt(), arcSize)) {
        rating += 5;
      }

      // Header
      if (fm.readString(5).equals("EEPAK")) {
        rating += 50;
      }

      // 4 - Version (201)
      if (fm.readInt() == 201) {
        rating += 5;
      }

      // File Data Offset
      if (FieldValidator.checkOffset(fm.readInt(), arcSize)) {
        rating += 5;
      }

      // Number Of Files
      if (FieldValidator.checkNumFiles(fm.readInt())) {
        rating += 5;
      }

      return rating;

    }
    catch (Throwable t) {
      return 0;
    }
  }

  int realNumFiles = 0;

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

      // 4 - Archive Length
      // 5 - Header ("EEPAK")
      // 4 - Version (201)
      // 4 - File Data Offset
      fm.skip(17);

      int numFiles = Archive.getMaxFiles();

      Resource[] resources = new Resource[numFiles];
      TaskProgressManager.setMaximum(arcSize);

      realNumFiles = 0;

      readDirectory(path, fm, resources, "", arcSize);

      fm.close();

      resources = resizeResources(resources, realNumFiles);

      return resources;

    }
    catch (Throwable t) {
      logError(t);
      return null;
    }
  }

  /**
   **********************************************************************************************
   * 
   **********************************************************************************************
   **/
  public void readDirectory(File path, FileManipulator fm, Resource[] resources, String dirName, long arcSize) {
    try {

      //System.out.println("Reading directory " + dirName + " at " + fm.getOffset());

      // 4 - Number of Files in this Folder
      int numFilesInFolder = fm.readInt();
      FieldValidator.checkNumFiles(numFilesInFolder + 1); // +1 to allow no files

      for (int i = 0; i < numFilesInFolder; i++) {
        // 1 - Filename Length (including null terminator)
        int filenameLength = ByteConverter.unsign(fm.readByte());

        // X - Filename
        // 1 - null Filename Terminator
        String filename = dirName + fm.readNullString(filenameLength);

        // 4 - File Offset
        int offset = fm.readInt();
        FieldValidator.checkOffset(offset, arcSize);

        // 4 - File Length
        int length = fm.readInt();
        FieldValidator.checkLength(length, arcSize);

        /*
        if (filename.endsWith("dds")) {
          offset += 128;
          length -= 128;
        }
        */

        //path,name,offset,length,decompLength,exporter
        resources[realNumFiles] = new Resource(path, filename, offset, length);
        realNumFiles++;

        TaskProgressManager.setValue(offset);
      }

      // 4 - Number of Sub-Folders in this Folder
      int numFolders = fm.readInt();
      FieldValidator.checkNumFiles(numFolders + 1); // +1 to allow no folders

      //System.out.println("    " + numFolders + " sub-directories at " + fm.getOffset());

      for (int i = 0; i < numFolders; i++) {
        // 1 - Folder Name Length (including null terminator)
        int folderNameLength = ByteConverter.unsign(fm.readByte());

        // X - Folder Name
        // 1 - null Folder Name Terminator
        String folderName = dirName + fm.readNullString(folderNameLength) + "\\";

        readDirectory(path, fm, resources, folderName, arcSize);
      }

    }
    catch (Throwable t) {
      logError(t);
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
