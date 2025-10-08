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
import org.watto.ge.plugin.ExporterPlugin;
import org.watto.ge.plugin.exporter.Exporter_XOR;
import org.watto.io.FileManipulator;
import org.watto.io.converter.ByteConverter;
import org.watto.io.converter.StringConverter;
import org.watto.task.TaskProgressManager;

/**
**********************************************************************************************
Thanks to Aluigi from ZenHax (Ref: https://aluigi.altervista.org/bms/bigfishgames81.bms) 
**********************************************************************************************
**/
public class Plugin_GPK_BFG_2 extends ArchivePlugin {

  /**
  **********************************************************************************************
  
  **********************************************************************************************
  **/
  public Plugin_GPK_BFG_2() {

    super("GPK_BFG_2", "GPK_BFG_2");

    //         read write replace rename
    setProperties(true, false, false, false);

    setGames("Azada: Ancient Magic");
    setExtensions("gpk"); // MUST BE LOWER CASE
    setPlatforms("PC");

    // MUST BE LOWER CASE !!!
    //setFileTypes(new FileType("txt", "Text Document", FileType.TYPE_DOCUMENT),
    //             new FileType("bmp", "Bitmap Image", FileType.TYPE_IMAGE)
    //             );

    setTextPreviewExtensions("fpt", "lng"); // LOWER CASE

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
      int header = fm.readInt();
      if (header == -2090375582) { // "bfg" + (byte)131
        rating += 50;
      }

      fm.skip(4);

      if (fm.readInt() == 0 && fm.readInt() == 0) {
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

      // 3 - Header ("bfg")
      // 1 - Version (131)
      // 4 - Unknown
      // 8 - null
      fm.skip(16);

      int numFiles = Archive.getMaxFiles();

      Resource[] resources = new Resource[numFiles];
      TaskProgressManager.setMaximum(arcSize);

      ExporterPlugin exporter = new Exporter_XOR(143);

      // Loop through directory
      int realNumFiles = 0;
      long offset = 0;
      while (fm.getOffset() < arcSize) {

        // 128 - Encrypted File Header
        byte[] fileHeaderBytes = fm.readBytes(128);
        fileHeaderBytes = decrypt(fileHeaderBytes);

        // 100 - Filename (null terminated, filled with nulls)
        int filenameLength = 100;
        for (int j = 0; j < 100; j++) {
          if (fileHeaderBytes[j] == 0) {
            // found the terminator
            filenameLength = j;
            break;
          }
        }
        byte[] filenameBytes = new byte[filenameLength];
        System.arraycopy(fileHeaderBytes, 0, filenameBytes, 0, filenameLength);
        String filename = StringConverter.convertLittle(filenameBytes);

        // X - Encrypted File Length String
        // 1 - null Encrypted File Length String Terminator
        int lengthStringBytes = 28;
        // read backwards until we find the last non-null
        for (int j = 127; j > 100; j--) {
          if (fileHeaderBytes[j] != 0) {
            // found the last non-null
            lengthStringBytes = j - 100 + 1;
            break;
          }
        }
        byte[] lengthBytes = new byte[lengthStringBytes];
        System.arraycopy(fileHeaderBytes, 100, lengthBytes, 0, lengthStringBytes);
        //String lengthString = StringConverter.convertLittle(lengthBytes);

        if (filenameLength == 0 && filenameLength == 0) {
          break; // early exit
        }

        int length = decodeLength(lengthBytes);
        FieldValidator.checkLength(length, arcSize);

        if (length == 0) {
          continue; // a directory entry, not a file
        }

        //path,name,offset,length,decompLength,exporter
        resources[realNumFiles] = new Resource(path, filename, offset, length, length, exporter);
        realNumFiles++;

        offset += length;

        // X - null Padding to a multiple of 16 bytes
        offset += calculatePadding(offset, 16);

        TaskProgressManager.setValue(offset);
      }

      resources = resizeResources(resources, realNumFiles);

      // now go through and set the file offsets appropriately
      long relativeOffset = fm.getOffset();
      for (int i = 0; i < realNumFiles; i++) {
        Resource resource = resources[i];
        resource.setOffset(resource.getOffset() + relativeOffset);
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
  
  **********************************************************************************************
  **/
  public byte[] decrypt(byte[] bytes) {
    int key = 188;

    int length = bytes.length;
    for (int j = 0; j < length; j++) {

      bytes[j] ^= key;

      key *= 9;
      key += j;
      key += 5;
    }

    return bytes;
  }

  /**
  **********************************************************************************************
  
  **********************************************************************************************
  **/
  public int decodeLength(byte[] lengthBytes) {
    int length = 0;

    int inLength = lengthBytes.length;

    int j = 0;
    for (; j < inLength; j++) {
      int character = ByteConverter.unsign(lengthBytes[j]);
      if (character != 0) {
        break;
      }
    }

    for (; j < inLength; j++) {
      int character = ByteConverter.unsign(lengthBytes[j]);

      character -= 48;
      length *= 8;
      length += character;
    }

    return length;
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
