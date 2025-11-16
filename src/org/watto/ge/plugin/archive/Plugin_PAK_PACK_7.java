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

import org.watto.Language;
import org.watto.datatype.Resource;
import org.watto.ge.helper.FieldValidator;
import org.watto.ge.plugin.ArchivePlugin;
import org.watto.ge.plugin.ExporterPlugin;
import org.watto.ge.plugin.exporter.Exporter_Subtract;
import org.watto.io.FileManipulator;
import org.watto.task.TaskProgressManager;

/**
**********************************************************************************************

**********************************************************************************************
**/
public class Plugin_PAK_PACK_7 extends ArchivePlugin {

  /**
  **********************************************************************************************
  
  **********************************************************************************************
  **/
  public Plugin_PAK_PACK_7() {

    super("PAK_PACK_7", "PAK_PACK_7");

    //         read write replace rename
    setProperties(true, true, false, false);

    setGames("Thanos Cardgames");
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
      if (fm.readString(4).equals("PACK")) {
        rating += 50;
      }

      if (fm.readInt() == 16) {
        rating += 5;
      }

      long arcSize = fm.getLength();

      // Directory Length
      if (FieldValidator.checkLength(fm.readInt(), arcSize)) {
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

      ExporterPlugin exporter = new Exporter_Subtract(1);

      // RESETTING GLOBAL VARIABLES

      FileManipulator fm = new FileManipulator(path, false);

      long arcSize = fm.getLength();

      // 4 - Header (PACK)
      // 4 - Directory Offset (16)
      // 4 - Directory Length
      fm.skip(12);

      // 4 - Number of Files
      int numFiles = fm.readInt();
      FieldValidator.checkNumFiles(numFiles);

      Resource[] resources = new Resource[numFiles];
      TaskProgressManager.setMaximum(numFiles);

      // Loop through directory
      int xCount = 0;
      for (int i = 0; i < numFiles; i++) {
        // 56 - Filename (terminated with (byte)32, filled with (byte)32)
        String filename = fm.readString(56).trim();
        FieldValidator.checkFilename(filename);

        // 4 - File Offset
        int offset = fm.readInt();
        FieldValidator.checkOffset(offset, arcSize);

        // 4 - File Length
        int length = fm.readInt();
        FieldValidator.checkLength(length, arcSize);

        //path,name,offset,length,decompLength,exporter
        resources[i] = new Resource(path, filename, offset, length);

        if (filename.charAt(0) == 'X') {
          xCount++;
        }

        TaskProgressManager.setValue(i);
      }

      // Note that the first file offset is actually the last byte of the directory, so you
      // can only store dummy data in there, as the first byte of real data will be discarded!
      //
      // We correct that here (as in, we make sure that the offset is correct for the first file, and the length is 1 less than it should be
      Resource firstResource = resources[0];
      if (firstResource.getOffset() == fm.getOffset() - 1) {
        firstResource.setOffset(fm.getOffset());

        int correctedLength = (int) firstResource.getLength() - 1;

        firstResource.setLength(correctedLength);
        firstResource.setDecompressedLength(correctedLength);
      }

      // If all the filenames start with an X (except the first and last Dummy files), then all the files need to be read by subtracting 1 from each byte
      if (xCount == numFiles - 2) {
        for (int i = 0; i < numFiles; i++) {
          resources[i].setExporter(exporter);
        }
      }

      fm.close();

      return resources;

    }
    catch (

    Throwable t) {
      logError(t);
      return null;
    }
  }

  /**
   **********************************************************************************************
   * Writes an [archive] File with the contents of the Resources
   **********************************************************************************************
   **/
  @Override
  public void write(Resource[] resources, File path) {
    try {

      FileManipulator fm = new FileManipulator(path, true);
      int numFiles = resources.length;
      TaskProgressManager.setMaximum(numFiles);

      // Write Header Data

      // 4 - Header (PACK)
      fm.writeString("PACK");

      // 4 - Directory Offset (16)
      fm.writeInt(16);

      // 4 - Directory Length
      fm.writeInt(numFiles * 64);

      // 4 - Number of Files
      fm.writeInt(numFiles);

      // Write Directory
      TaskProgressManager.setMessage(Language.get("Progress_WritingDirectory"));
      long offset = 16 + (numFiles * 64);
      int xCount = 0;
      for (int i = 0; i < numFiles; i++) {
        Resource resource = resources[i];
        long decompLength = resource.getDecompressedLength();

        // 56 - Filename (terminated with (byte)32, filled with (byte)32)
        String filename = resource.getName();
        if (filename.length() > 56) {
          filename = filename.substring(0, 56);
        }
        fm.writeString(filename);

        int paddingLength = 56 - filename.length();
        for (int p = 0; p < paddingLength; p++) {
          fm.writeByte(32);
        }

        // Note that the first file offset is actually the last byte of the directory, so you
        // can only store dummy data in there, as the first byte of real data will be discarded!
        //
        // We correct that here (as in, we set the offset to 1 less than it should be, and we make the 
        // length 1 byte longer, so that we can store the file at the correct position without losing anything).
        // ie we trick the offset/length so that the file stays correct in the end.
        if (i == 0) {
          // 4 - File Offset
          fm.writeInt((int) offset - 1);

          // 4 - File Length
          fm.writeInt((int) decompLength + 1);
        }
        else {
          // 4 - File Offset
          fm.writeInt((int) offset);

          // 4 - File Length
          fm.writeInt((int) decompLength);
        }

        offset += decompLength;

        if (filename.charAt(0) == 'X') {
          xCount++;
        }
      }

      // Write Files
      TaskProgressManager.setMessage(Language.get("Progress_WritingFiles"));

      // If all the filenames start with an X (except the first and last Dummy files), then all the files need to be read by subtracting 1 from each byte
      if (xCount == numFiles - 2) {
        ExporterPlugin subtractExporter = new Exporter_Subtract(1);
        write(subtractExporter, resources, fm);

        /*for (int i = 0; i < numFiles; i++) {
          Resource resource = resources[i];
        
          ExporterPlugin originalExporter = resource.getExporter();
        
          resource.setExporter(subtractExporter);
          write(resource, fm);
        
          resource.setExporter(originalExporter);
        
          TaskProgressManager.setValue(i);
        }
        */
      }
      else {
        // write the files normally
        write(resources, fm);
      }

      // FOOTER
      // 1 - null
      fm.writeByte(0);

      fm.close();

    }
    catch (Throwable t) {
      logError(t);
    }
  }

}
