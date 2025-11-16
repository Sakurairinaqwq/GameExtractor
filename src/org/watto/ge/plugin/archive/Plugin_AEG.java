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
import org.watto.datatype.FileType;
import org.watto.datatype.Resource;
import org.watto.ge.helper.FieldValidator;
import org.watto.ge.plugin.ArchivePlugin;
import org.watto.ge.plugin.ExporterPlugin;
import org.watto.ge.plugin.exporter.Exporter_ZLib;
import org.watto.io.FileManipulator;
import org.watto.task.TaskProgressManager;

/**
**********************************************************************************************

**********************************************************************************************
**/
public class Plugin_AEG extends ArchivePlugin {

  /**
  **********************************************************************************************
  
  **********************************************************************************************
  **/
  public Plugin_AEG() {

    super("AEG", "AEG");

    //         read write replace rename
    setProperties(true, false, false, false);

    setGames("Fairy Maids");
    setExtensions("aeg"); // MUST BE LOWER CASE
    setPlatforms("PC");

    // MUST BE LOWER CASE !!!
    setFileTypes(new FileType("nct", "Compressed Texture", FileType.TYPE_IMAGE));

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

      if (fm.readByte() == 1) {
        rating += 5;
      }

      long arcSize = fm.getLength();

      // Directory Length
      if (FieldValidator.checkLength(fm.readInt(), arcSize)) {
        rating += 5;
      }

      // Number Of Folders
      if (FieldValidator.checkNumFiles(fm.readShort())) {
        rating += 5;
      }

      // Number Of Files
      if (FieldValidator.checkNumFiles(fm.readShort())) {
        rating += 5;
      }

      return rating;

    }
    catch (Throwable t) {
      return 0;
    }
  }

  int realNumFiles = 0;

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

      long arcSize = fm.getLength();

      // 1 - Version? (1)
      // 4 - Directory Length
      fm.skip(5);

      int numFiles = Archive.getMaxFiles();
      realNumFiles = 0;

      Resource[] resources = new Resource[numFiles];
      TaskProgressManager.setMaximum(arcSize);

      readDirectory(fm, path, resources, "", arcSize);

      resources = resizeResources(resources, realNumFiles);

      numFiles = realNumFiles;

      // now go through, find all the NCT files (which are compressed DDS images), and set the exporters
      fm.getBuffer().setBufferSize(8); // small quick reads
      for (int i = 0; i < numFiles; i++) {
        Resource resource = resources[i];
        if (resource.getExtension().toLowerCase().equals("nct")) {
          long offset = resource.getOffset();

          fm.seek(offset);

          // 4 - Decompressed Length
          int decompLength = fm.readInt();

          // X - Compressed File Data
          int compressionCheck = fm.readByte();

          if (compressionCheck == 120) {
            FieldValidator.checkLength(decompLength);

            resource.setOffset(offset + 4);
            resource.setDecompressedLength(decompLength);
            resource.setExporter(exporter);
          }

        }
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
  public void readDirectory(FileManipulator fm, File path, Resource[] resources, String dirName, long arcSize) {
    try {

      //System.out.println("Offset " + fm.getOffset() + " for directory " + dirName);

      long relativeDirOffset = fm.getOffset();

      // 2 - Number of Sub-Folders in this Folder
      short numFolders = fm.readShort();
      FieldValidator.checkNumFiles(numFolders + 1); // +1 to allow 0 sub-folders

      // 2 - Number of Files in this Folder
      short numFiles = fm.readShort();
      FieldValidator.checkNumFiles(numFiles + 1); // +1 to allow 0 files (only sub-folders)

      // Read the sub-folders
      long[] folderOffsets = new long[numFolders];
      String[] folderNames = new String[numFolders];
      for (int i = 0; i < numFolders; i++) {
        // 4 - Offset to File Entries for this Folder [+5]
        long offset = fm.readInt() + relativeDirOffset;
        FieldValidator.checkOffset(offset, arcSize);

        // X - Folder Name
        // 1 - null Folder Name Terminator
        String folderName = fm.readNullString();
        FieldValidator.checkFilename(folderName);

        folderOffsets[i] = offset;
        folderNames[i] = dirName + folderName + "\\";
      }

      // read the files
      for (int i = 0; i < numFiles; i++) {
        // 4 - File Offset [*2048]
        long offset = fm.readInt() * 2048;
        FieldValidator.checkOffset(offset, arcSize);

        // 4 - File Length
        int length = fm.readInt();
        FieldValidator.checkLength(length, arcSize);

        // X - Filename
        // 1 - null Filename Terminator
        String filename = fm.readNullString();
        FieldValidator.checkFilename(filename);

        filename = dirName + filename;

        //path,name,offset,length,decompLength,exporter
        resources[realNumFiles] = new Resource(path, filename, offset, length);
        realNumFiles++;

        TaskProgressManager.setValue(offset);
      }

      // process each sub-folder
      for (int i = 0; i < numFolders; i++) {
        fm.seek(folderOffsets[i]);

        readDirectory(fm, path, resources, folderNames[i], arcSize);
      }

    }
    catch (Throwable t) {
      logError(t);
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
