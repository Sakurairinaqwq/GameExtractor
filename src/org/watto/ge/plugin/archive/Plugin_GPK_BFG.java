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
import org.watto.ge.plugin.exporter.Exporter_Default;
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
public class Plugin_GPK_BFG extends ArchivePlugin {

  /**
  **********************************************************************************************
  
  **********************************************************************************************
  **/
  public Plugin_GPK_BFG() {

    super("GPK_BFG", "GPK_BFG");

    //         read write replace rename
    setProperties(true, false, false, false);

    setGames("Atlantis Sky Patrol");
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
      if (header == -2123930014 || header == -2107152798) { // "bfg" + (byte)129 OR "bfg" + (byte)130
        rating += 50;
      }

      if (fm.readInt() == 0 && fm.readInt() == 0 && fm.readInt() == 0) {
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

      FileManipulator fm = new FileManipulator(path, false, 144); // small quick reads

      long arcSize = fm.getLength();

      // 3 - Header ("bfg")
      fm.skip(3);

      // 1 - Version (129=unencrypted file data, 130 = encrypted file data)
      int version = ByteConverter.unsign(fm.readByte());

      // 12 - null Padding to a multiple of 16 bytes
      fm.skip(12);

      int numFiles = Archive.getMaxFiles();

      Resource[] resources = new Resource[numFiles];
      TaskProgressManager.setMaximum(arcSize);

      ExporterPlugin exporter = Exporter_Default.getInstance();
      if (version == 130) {
        exporter = new Exporter_XOR(143);
      }

      // Loop through directory
      int realNumFiles = 0;
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
        for (int j = 0, k = 100; j < 28; j++, k++) {
          if (fileHeaderBytes[k] == 0) {
            // found the terminator
            lengthStringBytes = j;
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

        // X - File Data
        long offset = fm.getOffset();

        //path,name,offset,length,decompLength,exporter
        resources[realNumFiles] = new Resource(path, filename, offset, length, length, exporter);
        realNumFiles++;

        offset += length;

        // X - null Padding to a multiple of 16 bytes
        offset += calculatePadding(offset, 16);
        fm.seek(offset);

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
      if (character >= 48) {
        break;
      }
    }

    for (; j < inLength; j++) {
      int character = ByteConverter.unsign(lengthBytes[j]);
      if (character < 48) {
        break;
      }

      character -= 48;
      length *= 8;
      length += character;
    }

    return length;
  }

  /**
  **********************************************************************************************
  
  **********************************************************************************************
  **/
  /*public int decodeLength(String encodedString) {
    int length = 0;
  
    int stringLength = encodedString.length();
  
    int j = 0;
    for (; j < stringLength; j++) {
      char T = encodedString.charAt(j);
      if (T > 48) {
        break;
      }
    }
  
    for (; j < stringLength; j++) {
      char T = encodedString.charAt(j);
      if (T > 48) {
        break;
      }
      T -= 48;
      length *= 8;
      length += T;
    }
  
    return length;
  }*/

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
