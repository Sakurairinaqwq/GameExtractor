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
import org.watto.ge.plugin.ExporterPlugin;
import org.watto.ge.plugin.exporter.Exporter_XOR;
import org.watto.ge.plugin.exporter.Exporter_ZLib;
import org.watto.io.FileManipulator;
import org.watto.io.buffer.ByteBuffer;
import org.watto.task.TaskProgressManager;

/**
**********************************************************************************************

**********************************************************************************************
**/
public class Plugin_RESOURCES_DSRESOURCES extends ArchivePlugin {

  /**
  **********************************************************************************************
  
  **********************************************************************************************
  **/
  public Plugin_RESOURCES_DSRESOURCES() {

    super("RESOURCES_DSRESOURCES", "RESOURCES_DSRESOURCES");

    //         read write replace rename
    setProperties(true, false, false, false);

    setGames("City Style");
    setExtensions("resources"); // MUST BE LOWER CASE
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
      if (fm.readString(12).equals("dS.resources")) {
        rating += 50;
      }

      // Number Of Files
      if (FieldValidator.checkNumFiles(fm.readInt())) {
        rating += 5;
      }

      fm.skip(4);

      long arcSize = fm.getLength();

      // Directory Offset
      if (FieldValidator.checkOffset(fm.readInt(), arcSize)) {
        rating += 5;
      }

      // Directory Length
      if (FieldValidator.checkLength(fm.readInt(), arcSize)) {
        rating += 5;
      }

      // Directory Length
      if (FieldValidator.checkLength(fm.readInt())) {
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

      ExporterPlugin xorExporter = new Exporter_XOR(85);

      // RESETTING GLOBAL VARIABLES

      FileManipulator fm = new FileManipulator(path, false);

      long arcSize = fm.getLength();

      // 12 - Header (dS.resources)
      fm.skip(12);

      // 4 - Number of Files
      int numFiles = fm.readInt();
      FieldValidator.checkNumFiles(numFiles);

      // 4 - Unknown
      fm.skip(4);

      // 4 - Details Directory Offset
      int dirOffset = fm.readInt();
      FieldValidator.checkOffset(dirOffset, arcSize);

      // 4 - Compressed Filename Directory Length
      int compDirLength = fm.readInt();
      FieldValidator.checkLength(compDirLength, arcSize);

      // 4 - Decompressed Filename Directory Length
      int decompDirLength = fm.readInt();
      FieldValidator.checkLength(decompDirLength);

      // X - Filename Directory (ZLib Compression)
      byte[] dirBytes = new byte[decompDirLength];
      int decompWritePos = 0;
      Exporter_ZLib exporter = Exporter_ZLib.getInstance();
      exporter.open(fm, compDirLength, decompDirLength);

      for (int b = 0; b < decompDirLength; b++) {
        if (exporter.available()) { // make sure we read the next bit of data, if required
          dirBytes[decompWritePos++] = (byte) exporter.read();
        }
      }

      // open the decompressed data for processing
      String[] names = new String[numFiles];
      FileManipulator nameFM = new FileManipulator(new ByteBuffer(dirBytes));
      for (int i = 0; i < numFiles; i++) {
        // X - Filename
        // 1 - null Filename Terminator
        String filename = nameFM.readNullString();
        FieldValidator.checkFilename(filename);
        names[i] = filename;

        TaskProgressManager.setValue(i);
      }
      nameFM.close();

      fm.relativeSeek(dirOffset); // should be here anyway, but just in case...

      Resource[] resources = new Resource[numFiles];
      TaskProgressManager.setMaximum(numFiles);

      // Loop through directory
      for (int i = 0; i < numFiles; i++) {
        // 4 - File Length
        int length = fm.readInt();
        FieldValidator.checkLength(length, arcSize);

        // 4 - File Offset
        int offset = fm.readInt();
        FieldValidator.checkOffset(offset, arcSize);

        // 4 - Compression Flag? (0=compressed, #=uncompressed)
        fm.skip(4);

        // 4 - Decompressed File Length (=FileLength if the file isn't compressed)
        int decompLength = fm.readInt();
        FieldValidator.checkLength(decompLength);

        // 4 - Unknown
        // 4 - Unknown
        // 4 - Unknown
        // 4 - Unknown
        // 4 - Unknown
        // 4 - Unknown
        fm.skip(24);

        String filename = names[i];

        if (length == decompLength) {
          // uncompressed

          //path,name,offset,length,decompLength,exporter
          resources[i] = new Resource(path, filename, offset, length, decompLength, xorExporter);
        }
        else {
          // zlib

          //path,name,offset,length,decompLength,exporter
          resources[i] = new Resource(path, filename, offset, length, decompLength, exporter);
        }

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
