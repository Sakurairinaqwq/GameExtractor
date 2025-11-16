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

import org.watto.datatype.Resource;
import org.watto.ge.helper.FieldValidator;
import org.watto.ge.plugin.ArchivePlugin;
import org.watto.io.FileManipulator;
import org.watto.task.TaskProgressManager;

/**
**********************************************************************************************

**********************************************************************************************
**/
public class Plugin_PKG_SFG extends ArchivePlugin {

  /**
  **********************************************************************************************
  
  **********************************************************************************************
  **/
  public Plugin_PKG_SFG() {

    super("PKG_SFG", "PKG_SFG");

    //         read write replace rename
    setProperties(true, false, false, false);

    setGames("Farm Frenzy: Heave Ho");
    setExtensions("pkg"); // MUST BE LOWER CASE
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

      // Header
      if (fm.readInt() == 21448275) { // "SFG" + (byte)1
        rating += 50;
      }

      long arcSize = fm.getLength();

      // Directory Offset
      if (FieldValidator.checkOffset(fm.readInt(), arcSize)) {
        rating += 5;
      }

      // Directory Offset
      if (FieldValidator.checkOffset(fm.readInt(), arcSize)) {
        rating += 5;
      }

      fm.skip(2);

      // 2 - Maximum Name Length (88)
      if (fm.readShort() == 88) {
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

      // 4 - Header ("SFG" + (byte)1)
      fm.skip(4);

      // 4 - Metadata Directory Offset
      int metadataDirOffset = fm.readInt();
      FieldValidator.checkOffset(metadataDirOffset, arcSize);

      // 4 - File Data Offset
      int fileDataOffset = fm.readInt();
      FieldValidator.checkOffset(fileDataOffset, arcSize);

      // 2 - Archive Name Length
      // 2 - Maximum Name Length (88)
      // 4 - Unknown
      // 88 - Archive Name (nulls to fill)
      fm.skip(96);

      // 2 - Number of Files
      short numFiles = fm.readShort();
      FieldValidator.checkNumFiles(numFiles);

      // 2 - Unknown (1)
      // 2 - Empty Length (0)
      // 2 - Maximum Empty Length (88)
      // 4 - Unknown (1)
      // 88 - null
      // 2 - null
      fm.skip(98);

      Resource[] resources = new Resource[numFiles];
      TaskProgressManager.setMaximum(numFiles);

      // Loop through directory
      int[] metadataOffsets = new int[numFiles];
      int[] fileOffsets = new int[numFiles];
      for (int i = 0; i < numFiles; i++) {
        // 4 - Meta Data Offset (relative to the start of the Metadata Directory)
        int metadataOffset = fm.readInt() + metadataDirOffset;
        FieldValidator.checkOffset(metadataOffset, arcSize);
        metadataOffsets[i] = metadataOffset;

        // 4 - File Data Offset (relative to the start of the File Data)
        int offset = fm.readInt() + fileDataOffset;
        FieldValidator.checkOffset(offset, arcSize);
        fileOffsets[i] = offset;

        // 4 - Unknown (-1)
        fm.skip(4);
        TaskProgressManager.setValue(i);
      }

      String[] filenames = new String[numFiles];
      for (int i = 0; i < numFiles; i++) {
        fm.relativeSeek(metadataOffsets[i]);

        // 4 - Unknown
        fm.skip(4);

        // 2 - Short Name Length
        // 2 - Maximum Short Name Length (88)
        // 4 - Unknown
        // 88 - Short Name (nulls to fill)
        fm.skip(96);

        // 2 - File Type Length
        // 2 - Maximum File Type Length (88)
        // 4 - Unknown
        // 88 - File Type (nulls to fill)
        fm.skip(96);

        // 2 - Filename Length
        short filenameLength = fm.readShort();
        FieldValidator.checkFilenameLength(filenameLength);

        // 2 - Maximum Filename Length (88)
        // 4 - Unknown
        fm.skip(6);

        // 88 - Filename (nulls to fill)
        String filename = fm.readString(filenameLength);
        filenames[i] = filename;

        TaskProgressManager.setValue(i);
      }

      fm.getBuffer().setBufferSize(4); // small quick reads

      for (int i = 0; i < numFiles; i++) {
        fm.relativeSeek(fileOffsets[i]);

        // 4 - File Length
        int length = fm.readInt();
        FieldValidator.checkLength(length, arcSize);

        // X - File Data
        long offset = fm.getOffset();

        String filename = filenames[i];

        //path,name,offset,length,decompLength,exporter
        resources[i] = new Resource(path, filename, offset, length);

        TaskProgressManager.setValue(i);
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
