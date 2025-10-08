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
import org.watto.ge.plugin.exporter.Exporter_XOR_RepeatingKey;
import org.watto.io.FileManipulator;
import org.watto.io.buffer.XORRepeatingKeyBufferWrapper;
import org.watto.task.TaskProgressManager;

/**
**********************************************************************************************

**********************************************************************************************
**/
public class Plugin_ZCA_ZCA_2 extends ArchivePlugin {

  /**
  **********************************************************************************************
  
  **********************************************************************************************
  **/
  public Plugin_ZCA_ZCA_2() {

    super("ZCA_ZCA_2", "ZCA_ZCA_2");

    //         read write replace rename
    setProperties(true, false, false, false);

    setGames("Ballad of Solar");
    setExtensions("zca"); // MUST BE LOWER CASE
    setPlatforms("PC");

    // MUST BE LOWER CASE !!!
    //setFileTypes(new FileType("txt", "Text Document", FileType.TYPE_DOCUMENT),
    //             new FileType("bmp", "Bitmap Image", FileType.TYPE_IMAGE)
    //             );

    setTextPreviewExtensions("zfnt", "zscn"); // LOWER CASE

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
      if (fm.readString(3).equals("ZCA")) {
        rating += 50;
      }
      fm.skip(1);

      long arcSize = fm.getLength();

      // Directory Offset
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

      // RESETTING GLOBAL VARIABLES

      FileManipulator fm = new FileManipulator(path, false);

      long arcSize = fm.getLength();

      // 4 - Header ("ZCA" + null)
      fm.skip(4);

      // 4 - Directory Offset
      int dirOffset = fm.readInt();
      FieldValidator.checkOffset(dirOffset, arcSize);

      // 4 - Number of Files
      int numFiles = fm.readInt();
      FieldValidator.checkNumFiles(numFiles);

      // 4 - null
      fm.seek(dirOffset);

      Resource[] resources = new Resource[numFiles];
      TaskProgressManager.setMaximum(numFiles);

      // Set the XOR key
      int[] xorKey = new int[] { 115, 107, 84, 106, 50, 51, 52, 89, 105, 117, 116, 121, 115, 66, 106, 103, 104, 72, 115, 100, 54, 50, 85, 55, 56, 53, 104, 106, 107, 114, 121, 88, 55, 56, 50, 70, 51, 104, 103, 97, 102, 85, 97, 55, 115, 103, 116, 87, 50, 51, 117, 52, 79, 103, 101, 119, 102, 51, 102 };

      // 59 entries in the key, need to work out where to start based on the dir offset
      int keyOffset = dirOffset % 59;
      if (keyOffset >= 16) {
        keyOffset -= 16;
      }
      else {
        int overflow = 16 - keyOffset;
        keyOffset = 59 - overflow;
      }

      XORRepeatingKeyBufferWrapper xorBuffer = new XORRepeatingKeyBufferWrapper(fm.getBuffer(), xorKey);
      xorBuffer.setCurrentKeyPos(keyOffset);
      fm.setBuffer(xorBuffer);

      // Loop through directory
      for (int i = 0; i < numFiles; i++) {

        // 2 - Filename Length
        short filenameLength = fm.readShort();
        FieldValidator.checkFilenameLength(filenameLength);

        // X - Filename
        String filename = fm.readString(filenameLength);

        // 4 - File Offset
        int offset = fm.readInt();
        FieldValidator.checkOffset(offset, arcSize);

        // 4 - File Length
        int length = fm.readInt();
        FieldValidator.checkLength(length, arcSize);

        // 59 entries in the key, need to work out where to start based on the dir offset
        keyOffset = offset % 59;
        if (keyOffset >= 16) {
          keyOffset -= 16;
        }
        else {
          int overflow = 16 - keyOffset;
          keyOffset = 59 - overflow;
        }

        Exporter_XOR_RepeatingKey exporter = new Exporter_XOR_RepeatingKey(xorKey, keyOffset);

        /*
        exporter.setCurrentKeyPos(keyOffset);
        exporter.startAtCurrentKeyPos(true);
        */

        //System.out.println(filename + "\t" + keyOffset);

        //path,name,offset,length,decompLength,exporter
        resources[i] = new Resource(path, filename, offset, length, length, exporter);

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

}
