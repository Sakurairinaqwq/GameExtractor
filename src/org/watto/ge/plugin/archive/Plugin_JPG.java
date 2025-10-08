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
import org.watto.ge.plugin.exporter.Exporter_XOR_RepeatingKey;
import org.watto.io.FileManipulator;
import org.watto.io.buffer.XORRepeatingKeyBufferWrapper;
import org.watto.task.TaskProgressManager;

/**
**********************************************************************************************

**********************************************************************************************
**/
public class Plugin_JPG extends ArchivePlugin {

  /**
  **********************************************************************************************
  
  **********************************************************************************************
  **/
  public Plugin_JPG() {

    super("JPG", "JPG");

    //         read write replace rename
    setProperties(true, false, false, false);

    setGames("Machinarium");
    setExtensions("jpg"); // MUST BE LOWER CASE
    setPlatforms("PC");

    // MUST BE LOWER CASE !!!
    //setFileTypes(new FileType("txt", "Text Document", FileType.TYPE_DOCUMENT),
    //             new FileType("bmp", "Bitmap Image", FileType.TYPE_IMAGE)
    //             );

    //setTextPreviewExtensions("colours", "rat", "screen", "styles"); // LOWER CASE

    setCanScanForFileTypes(true);

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

      int headerInt = fm.readInt();
      if (headerInt == 0 || headerInt == -1552180169) {
        rating += 5;
      }
      else {
        rating = 0;
      }

      headerInt = fm.readInt();
      if (headerInt == 0 || headerInt == -1823130127) {
        rating += 5;
      }
      else {
        rating = 0;
      }

      headerInt = fm.readInt();
      if (headerInt == 0 || headerInt == 326211331) {
        rating += 5;
      }
      else {
        rating = 0;
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

      int numFiles = 49152 / 12;
      FieldValidator.checkNumFiles(numFiles);

      Resource[] resources = new Resource[numFiles];
      TaskProgressManager.setMaximum(numFiles);

      // see if the archive is encrypted or not.
      boolean encrypted = false;
      int[] xorKey = new int[] { 55, 156, 123, 163, 241, 61, 85, 147, 3, 151, 113, 19, 109, 158, 252, 113, 85, 204, 17, 99, 190, 86, 238, 85, 195, 213, 185, 247, 119, 156, 160, 233 };

      int headerCheck = fm.readInt();
      if (headerCheck == -1552180169) {
        // encrypted

        fm.setBuffer(new XORRepeatingKeyBufferWrapper(fm.getBuffer(), xorKey));
        encrypted = true;
      }
      fm.seek(0);

      // Loop through directory
      int realNumFiles = 0;
      for (int i = 0; i < numFiles; i++) {
        // 4 - Unknown
        fm.skip(4);

        // 4 - File Offset
        int offset = fm.readInt();
        FieldValidator.checkOffset(offset, arcSize);

        // 4 - File Length
        int length = fm.readInt();
        FieldValidator.checkLength(length, arcSize);

        if (offset == 0 && length == 0) {
          continue;
        }

        String filename = Resource.generateFilename(realNumFiles);

        if (encrypted) {
          int keyStart = offset % 32;
          ExporterPlugin exporter = new Exporter_XOR_RepeatingKey(xorKey, keyStart); // so the decryption starts at the right place

          //path,name,offset,length,decompLength,exporter
          resources[realNumFiles] = new Resource(path, filename, offset, length, length, exporter);
        }
        else {
          //path,name,offset,length,decompLength,exporter
          resources[realNumFiles] = new Resource(path, filename, offset, length);
        }
        realNumFiles++;

        TaskProgressManager.setValue(i);
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

    if (headerInt1 == 122902339) {
      return "swc";
    }
    else if (headerInt1 == 122902342) {
      return "swf";
    }
    else if (headerShort1 == -257) {
      return "txt";
    }
    else {
      // see if all ascii
      int headerLength = headerBytes.length;
      boolean ascii = true;
      for (int i = 0; i < headerLength; i++) {
        int headerByte = headerBytes[i];
        if (headerByte < 8) {
          ascii = false;
          break;
        }
      }
      if (ascii) {
        return "txt";
      }
    }

    return null;
  }

}
