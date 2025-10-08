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
import org.watto.datatype.Resource;
import org.watto.ge.helper.FieldValidator;
import org.watto.ge.plugin.ArchivePlugin;
import org.watto.ge.plugin.ExporterPlugin;
import org.watto.ge.plugin.exporter.Exporter_Default;
import org.watto.ge.plugin.exporter.Exporter_ZLib;
import org.watto.io.FileManipulator;
import org.watto.io.buffer.ByteBuffer;
import org.watto.task.TaskProgressManager;

/**
**********************************************************************************************

**********************************************************************************************
**/
public class Plugin_NoExt_RDFZ extends ArchivePlugin {

  /**
  **********************************************************************************************
  
  **********************************************************************************************
  **/
  public Plugin_NoExt_RDFZ() {

    super("NoExt_RDFZ", "NoExt_RDFZ");

    //         read write replace rename
    setProperties(true, false, false, false);

    setGames("The Treasures of Montezuma 3",
        "Vacation Mogul",
        "Dark Strokes: Sins of the Fathers",
        "Dark Strokes: The Legend of the Snow Kingdom");
    setExtensions(""); // MUST BE LOWER CASE
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

      // Header
      if (fm.readString(4).equals("RDFZ")) {
        rating += 50;
      }

      if (fm.readInt() == 4) {
        rating += 5;
      }

      if (fm.readString(4).equals("Zlib")) {
        rating += 5;
      }

      long arcSize = fm.getLength();

      // Directory Length
      if (FieldValidator.checkLength(fm.readInt(), arcSize)) {
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

      // 4 - Header (RDFZ)
      // 4 - Unknown (4)
      // 4 - Compression Header (Zlib)
      fm.skip(12);

      // 4 - Compressed Directory Length [-4]
      int compDirLength = fm.readInt() - 4;
      FieldValidator.checkLength(compDirLength, arcSize);

      // 4 - Decompressed Directory Length
      int decompDirLength = fm.readInt();
      FieldValidator.checkLength(decompDirLength);

      long endOfDirOffset = fm.getOffset() + compDirLength;

      // X - Compressed Directory
      byte[] dirBytes = new byte[decompDirLength];
      int decompWritePos = 0;
      Exporter_ZLib dirExporter = Exporter_ZLib.getInstance();
      dirExporter.open(fm, compDirLength, decompDirLength);

      for (int b = 0; b < decompDirLength; b++) {
        if (dirExporter.available()) { // make sure we read the next bit of data, if required
          dirBytes[decompWritePos++] = (byte) dirExporter.read();
        }
      }

      //FileManipulator tempFM = new FileManipulator(new File("C:\\temp.txt"), true);
      //tempFM.writeBytes(dirBytes);
      //tempFM.close();

      // read all the names
      FileManipulator nameFM = new FileManipulator(new ByteBuffer(dirBytes));

      // 4 - Number of Compression Types (2)
      int numCompressions = nameFM.readInt();
      FieldValidator.checkRange(numCompressions, 1, 10);//guess max

      ExporterPlugin[] exporters = new ExporterPlugin[numCompressions];
      for (int i = 0; i < numCompressions; i++) {
        // 4 - Compression Name Length (can be null, meaning no compression)
        int nameLength = nameFM.readInt();
        FieldValidator.checkFilenameLength(nameLength + 1); // +1 to allow nulls, meaning "no compression"

        // X - Compression Name (eg Zlib)
        String name = nameFM.readString(nameLength);

        if (name.equals("")) {
          exporters[i] = Exporter_Default.getInstance();
        }
        else if (name.equalsIgnoreCase("Zlib")) {
          exporters[i] = Exporter_ZLib.getInstance();
        }
        else {
          ErrorLogger.log("[NoExt_RDFZ] Unknown compression type: " + name);
          exporters[i] = Exporter_Default.getInstance();
        }
      }

      // 4 - Number of Folders
      int numFolders = nameFM.readInt();
      FieldValidator.checkNumFiles(numFolders);

      String[] folders = new String[numFolders];
      for (int i = 0; i < numFolders; i++) {
        // 4 - Folder Name Length
        int nameLength = nameFM.readInt();
        FieldValidator.checkFilenameLength(nameLength + 1); // +1 to allow nulls, meaning "no folder"

        // X - Folder Name
        String name = nameFM.readString(nameLength);

        folders[i] = name + "\\";
      }

      // 4 - Number of Filenames
      int numFilenames = nameFM.readInt();
      FieldValidator.checkNumFiles(numFilenames);

      String[] filenames = new String[numFilenames];
      for (int i = 0; i < numFilenames; i++) {
        // 4 - Filename Length
        int nameLength = nameFM.readInt();
        FieldValidator.checkFilenameLength(nameLength + 1); // allow nulls

        // X - Filename
        String name = nameFM.readString(nameLength);
        //System.out.println(nameFM.getOffset() + name);

        filenames[i] = name;
      }

      nameFM.close();

      // just in case...
      fm.seek(endOfDirOffset);

      // 4 - Number Of Files
      int numFiles = fm.readInt();
      FieldValidator.checkNumFiles(numFiles);

      Resource[] resources = new Resource[numFiles];
      TaskProgressManager.setMaximum(numFiles);

      // Loop through directory
      for (int i = 0; i < numFiles; i++) {
        // 4 - File Offset
        int offset = fm.readInt();
        FieldValidator.checkOffset(offset, arcSize);

        // 4 - Compressed File Length (including the 4-byte header, if the file is compressed)
        int length = fm.readInt();
        FieldValidator.checkLength(length, arcSize);

        // 4 - Folder Name ID
        int folderID = fm.readInt();
        FieldValidator.checkRange(folderID, 0, numFolders);

        // 4 - File Name ID
        int filenameID = fm.readInt();
        FieldValidator.checkRange(filenameID, 0, numFilenames);

        // 4 - Compression Type ID
        int compressionID = fm.readInt();
        FieldValidator.checkRange(compressionID, 0, numCompressions);

        String filename = folders[folderID] + filenames[filenameID];

        ExporterPlugin exporter = exporters[compressionID];

        //path,name,offset,length,decompLength,exporter
        resources[i] = new Resource(path, filename, offset, length, length, exporter);

        TaskProgressManager.setValue(i);
      }

      // for all compressed files, need to go to those offsets, to get the decompressed length
      fm.seek(0);
      fm.getBuffer().setBufferSize(4);

      for (int i = 0; i < numFiles; i++) {
        Resource resource = resources[i];
        if (!(resource.getExporter() instanceof Exporter_Default)) {
          long offset = resource.getOffset();
          fm.seek(offset);

          // 4 - Decompressed Length
          int decompLength = fm.readInt();
          FieldValidator.checkLength(decompLength);

          // X - File Data (ZLib Compression)
          resource.setOffset(offset + 4);
          resource.setLength(resource.getLength() - 4);
          resource.setDecompressedLength(decompLength);
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

    String name = resource.getName();
    if (name.startsWith("font")) {
      return "font";
    }
    else if (name.startsWith("webm")) {
      return "webm";
    }
    else if (name.startsWith("ptc")) {
      return "ptc";
    }
    else if (name.startsWith("jimg_texture") || headerInt2 == -520103681) {
      resource.setOffset(resource.getOffset() + 4);
      int length = (int) resource.getLength() - 4;
      resource.setLength(length);
      resource.setDecompressedLength(length);
      return "jpg";
    }
    else if (name.startsWith("fx") || name.startsWith("script")) {
      return "txt";
    }
    else if (headerShort1 == -17425) {
      return "xml";
    }
    /*
    if (headerInt1 == 2037149520) {
      return "js";
    }
    */

    return null;
  }

}
