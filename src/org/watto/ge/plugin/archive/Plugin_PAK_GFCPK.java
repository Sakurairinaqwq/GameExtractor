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
import org.watto.datatype.Resource;
import org.watto.ge.helper.FieldValidator;
import org.watto.ge.plugin.ArchivePlugin;
import org.watto.ge.plugin.ExporterPlugin;
import org.watto.ge.plugin.exporter.Exporter_ZLib;
import org.watto.io.FileManipulator;
import org.watto.io.FilenameSplitter;
import org.watto.io.converter.ByteConverter;
import org.watto.task.TaskProgressManager;

/**
**********************************************************************************************

**********************************************************************************************
**/
public class Plugin_PAK_GFCPK extends ArchivePlugin {

  /**
  **********************************************************************************************
  
  **********************************************************************************************
  **/
  public Plugin_PAK_GFCPK() {

    super("PAK_GFCPK", "PAK_GFCPK");

    //         read write replace rename
    setProperties(true, false, false, false);

    setGames("Build It! Miami Beach Resort");
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

      // Header
      if (fm.readString(5).equals("GFCPK")) {
        rating += 50;
      }

      if (fm.readByte() == 2) {
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

      ExporterPlugin exporter = Exporter_ZLib.getInstance();

      // RESETTING GLOBAL VARIABLES

      FileManipulator fm = new FileManipulator(path, false);

      FileManipulator decompFM = decryptArchive(fm);
      if (decompFM != null && decompFM != fm) {
        fm.close(); // close the original archive
        fm = decompFM; // now we're going to read from the decrypted file instead

        path = fm.getFile(); // So the resources are stored against the decrypted file
      }

      fm.seek(0);

      long arcSize = fm.getLength();

      // 5 - Header ("GFCPK")
      // 1 - Version (2)
      // 4 - Encryption Key
      // 4 - Unknown
      fm.skip(14);

      // 4 - Number Of Files
      int numFiles = fm.readInt();
      FieldValidator.checkNumFiles(numFiles);

      Resource[] resources = new Resource[numFiles];
      TaskProgressManager.setMaximum(numFiles);

      // Loop through directory
      for (int i = 0; i < numFiles; i++) {
        // 4 - Filename Length
        int filenameLength = fm.readInt();
        FieldValidator.checkFilenameLength(filenameLength);

        // X - Filename
        String filename = fm.readString(filenameLength);

        // 4 - Compression Flag (0=uncompressed, 1=ZLib)
        int compressionFlag = fm.readInt();

        // 4 - Decompressed File Length
        int decompLength = fm.readInt();
        FieldValidator.checkLength(decompLength);

        // 4 - File Offset
        int offset = fm.readInt();
        FieldValidator.checkOffset(offset, arcSize);

        // 4 - Compressed File Length
        int length = fm.readInt();
        FieldValidator.checkLength(length, arcSize);

        if (compressionFlag == 0) {
          // uncompressed

          //path,name,offset,length,decompLength,exporter
          resources[i] = new Resource(path, filename, offset, length);
        }
        else if (compressionFlag == 1) {
          // zlib

          //path,name,offset,length,decompLength,exporter
          resources[i] = new Resource(path, filename, offset, length, decompLength, exporter);
        }
        else {
          ErrorLogger.log("[PAK_GFCPK] Unknown compression type: " + compressionFlag);

          //path,name,offset,length,decompLength,exporter
          resources[i] = new Resource(path, filename, offset, length);
        }

        resources[i].forceNotAdded(true);

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
   Decrypts an archive, where the whole archive is currently encrypted.
   If the decrypted file already exists, we use that, we don't re-decrypt.
   **********************************************************************************************
   **/
  public FileManipulator decryptArchive(FileManipulator fm) {
    try {
      // Build a new "_ge_decompressed" archive file in the current directory
      File origFile = fm.getFile();

      String pathOnly = FilenameSplitter.getDirectory(origFile);
      String filenameOnly = FilenameSplitter.getFilename(origFile);
      String extensionOnly = FilenameSplitter.getExtension(origFile);

      File decompFile = new File(pathOnly + File.separatorChar + filenameOnly + "_ge_decompressed" + "." + extensionOnly);
      if (decompFile.exists()) {
        // we've already decrypted this file before - open and return it
        return new FileManipulator(decompFile, false);
      }

      FileManipulator decompFM = new FileManipulator(decompFile, true);

      long currentOffset = fm.getOffset();
      long arcSize = fm.getLength();

      fm.seek(0); // to fill the buffer from the start of the file, for efficient reading

      // 5 - Header ("GFCPK")
      // 1 - Version (2)
      fm.skip(6);

      // 4 - Encryption Key
      int[] key = new int[] { ByteConverter.unsign(fm.readByte()), ByteConverter.unsign(fm.readByte()), ByteConverter.unsign(fm.readByte()), ByteConverter.unsign(fm.readByte()) };
      if (key[0] == 0 && key[1] == 0 && key[2] == 0 && key[3] == 0) {
        // already reading a decrypted archive
        return fm;
      }

      // 4 - Unknown
      fm.skip(4);

      // go back and copy all the 14 header bytes into the decrypted file
      fm.seek(0);

      // 5 - Header ("GFCPK")
      // 1 - Version (2)
      decompFM.writeBytes(fm.readBytes(6));
      // 4 - Encryption Key (now set to 0,0,0,0)
      decompFM.writeInt(0);
      fm.skip(4);
      // 4 - Unknown
      decompFM.writeBytes(fm.readBytes(4));

      // Now decrypt the block into the decrypted file
      TaskProgressManager.setMessage(Language.get("Progress_DecompressingArchive")); // progress bar
      TaskProgressManager.setMaximum(arcSize); // progress bar
      TaskProgressManager.setIndeterminate(true);

      //int keyPos = 0;
      //int keyLength = key.length;
      for (int i = 14; i < arcSize; i++) {
        int c = i;

        int keyPos = (i & 3);
        c ^= key[keyPos];

        int currentByte = ByteConverter.unsign(fm.readByte());
        currentByte ^= c;

        decompFM.writeByte(currentByte);
      }

      // Force-write out the decrypted file to write it to disk, then change the buffer to read-only.
      decompFM.close();
      decompFM = new FileManipulator(decompFile, false);

      TaskProgressManager.setMessage(Language.get("Progress_ReadingArchive")); // progress bar
      TaskProgressManager.setIndeterminate(false);

      // Return the file pointer to the beginning, and return the decompressed file
      decompFM.seek(currentOffset);
      return decompFM;
    }
    catch (Throwable t) {
      ErrorLogger.log(t);
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
