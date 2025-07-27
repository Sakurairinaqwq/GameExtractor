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
import org.watto.io.buffer.ByteBuffer;
import org.watto.task.TaskProgressManager;

/**
**********************************************************************************************

**********************************************************************************************
**/
public class Plugin_VPPPC_4 extends ArchivePlugin {

  /**
  **********************************************************************************************
  
  **********************************************************************************************
  **/
  public Plugin_VPPPC_4() {

    super("VPPPC_4", "VPPPC_4");

    //         read write replace rename
    setProperties(true, false, false, false);

    setGames("Saints Row 2");
    setExtensions("vpp_pc"); // MUST BE LOWER CASE
    setPlatforms("PC");

    // MUST BE LOWER CASE !!!
    //setFileTypes(new FileType("txt", "Text Document", FileType.TYPE_DOCUMENT),
    //             new FileType("bmp", "Bitmap Image", FileType.TYPE_IMAGE)
    //             );

    setTextPreviewExtensions("vpkg", "vint_proj"); // LOWER CASE

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

      if (fm.readLong() == 1367935694) {
        rating += 25;
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

      // 8 - Unknown (1367935694)
      // 324 - null
      // 8 - Unknown (or null)
      fm.skip(340);

      // 4 - Number of Files
      int numFiles = fm.readInt();
      FieldValidator.checkNumFiles(numFiles);

      // 8 - Archive Length
      fm.skip(8);

      // 4 - Name Directory Length
      int nameDirLength = fm.readInt();
      FieldValidator.checkLength(nameDirLength, arcSize);

      // 4 - Unknown (9/12)
      // 8 - File Data Length?
      // 8 - Unknown
      // 8 - Unknown 
      // X - null Padding to a multiple of 2048 bytes

      // work out where the filename directory is
      int nameDirOffset = 2048 + (numFiles * 28);
      nameDirOffset += calculatePadding(nameDirOffset, 2048);

      // work out where the types directory is
      int typesDirOffset = nameDirOffset + nameDirLength;
      typesDirOffset += calculatePadding(typesDirOffset, 2048);

      // work out where the file data
      int relativeOffset = typesDirOffset + 2048;

      fm.seek(typesDirOffset);
      byte[] typeBytes = fm.readBytes(2048);
      FileManipulator typeFM = new FileManipulator(new ByteBuffer(typeBytes));

      fm.seek(nameDirOffset);
      byte[] nameBytes = fm.readBytes(nameDirLength);
      FileManipulator nameFM = new FileManipulator(new ByteBuffer(nameBytes));

      fm.seek(2048);

      Resource[] resources = new Resource[numFiles];
      TaskProgressManager.setMaximum(numFiles);

      // Loop through directory
      for (int i = 0; i < numFiles; i++) {
        // 4 - Filename Offset (relative to the start of the names directory)
        int filenameOffset = fm.readInt();
        FieldValidator.checkOffset(filenameOffset, nameDirLength);

        // 4 - File Type Offset (relative to the start of the types directory)
        int typeOffset = fm.readInt();
        FieldValidator.checkOffset(typeOffset, 2048);

        // 4 - null
        fm.skip(4);

        nameFM.seek(filenameOffset);
        String filename = nameFM.readNullString();
        typeFM.seek(typeOffset);
        filename += "." + typeFM.readNullString();

        // 4 - File Data Offset (relative to the start of the File Data)
        int offset = fm.readInt() + relativeOffset;
        FieldValidator.checkOffset(offset, arcSize + 1); // to allow for files that are at the length of the archive

        // 4 - Decompressed File Length
        int length = fm.readInt();
        FieldValidator.checkLength(length, arcSize);

        // 4 - Compressed File Length (or -1 if not compressed)
        // 4 - null
        fm.skip(8);

        //path,name,offset,length,decompLength,exporter
        resources[i] = new Resource(path, filename, offset, length);

        TaskProgressManager.setValue(i);
      }

      nameFM.close();
      typeFM.close();
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
