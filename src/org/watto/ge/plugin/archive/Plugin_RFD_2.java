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
import org.watto.datatype.Archive;
import org.watto.datatype.FileType;
import org.watto.datatype.Resource;
import org.watto.ge.helper.FieldValidator;
import org.watto.ge.plugin.ArchivePlugin;
import org.watto.ge.plugin.ExporterPlugin;
import org.watto.ge.plugin.exporter.Exporter_Custom_RFD_2;
import org.watto.ge.plugin.exporter.Exporter_Default;
import org.watto.io.FileManipulator;
import org.watto.task.TaskProgressManager;

/**
**********************************************************************************************

**********************************************************************************************
**/
public class Plugin_RFD_2 extends ArchivePlugin {

  /**
  **********************************************************************************************
  
  **********************************************************************************************
  **/
  public Plugin_RFD_2() {

    super("RFD_2", "RFD_2");

    //         read write replace rename
    setProperties(true, true, false, true);

    setExtensions("rfd");
    setGames("LEGO Loco");
    setPlatforms("PC");

    // MUST BE LOWER CASE !!!
    setFileTypes(new FileType("but", "Button Image", FileType.TYPE_IMAGE));

    setTextPreviewExtensions("dat", "lay"); // LOWER CASE

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

      getDirectoryFile(fm.getFile(), "rfh");
      rating += 25;

      return rating;

    }
    catch (Throwable t) {
      return 0;
    }
  }

  /**
  **********************************************************************************************
  
  **********************************************************************************************
  **/
  @Override
  public Resource[] read(File path) {
    try {

      addFileTypes();

      long arcSize = (int) path.length();

      File sourcePath = getDirectoryFile(path, "rfh");

      FileManipulator fm = new FileManipulator(sourcePath, false);

      int numFiles = Archive.getMaxFiles(4);//guess

      Resource[] resources = new Resource[numFiles];
      TaskProgressManager.setMaximum(arcSize);

      ExporterPlugin exporter = Exporter_Custom_RFD_2.getInstance();

      int i = 0;
      long offset = 0;
      long[] offsets = new long[numFiles];
      while (fm.getOffset() < fm.getLength()) {
        // 4 - Filename Length (including the null)
        int filenameLength = fm.readInt() - 1;
        FieldValidator.checkFilenameLength(filenameLength);

        // X - Filename
        // 1 - null Filename Terminator
        String filename = fm.readString(filenameLength);
        fm.skip(1);
        FieldValidator.checkFilename(filename);

        // 4 - File Length
        long length = fm.readInt();
        FieldValidator.checkLength(length, arcSize);

        // 4 - Compression (0=uncompressed, 1=compressed)
        int compression = fm.readInt();

        //path,id,name,offset,length,decompLength,exporter
        Resource resource = new Resource(path, filename, offset, length);
        if (compression == 1) {
          resource.setExporter(exporter);
          offsets[i] = offset;
        }
        else {
          offsets[i] = -1;
        }
        resources[i] = resource;

        TaskProgressManager.setValue(offset);
        i++;
        offset += length;
      }

      resources = resizeResources(resources, i);

      // now go through and get/set the decompressed lengths
      fm.close();
      fm = new FileManipulator(path, false, 4); // small quick reads
      for (int f = 0; f < i; f++) {
        offset = offsets[f];
        if (offset != -1) {
          // a compressed file
          Resource resource = resources[f];
          fm.seek(offset);

          // 4 - Decomp Length
          int decompLength = fm.readInt();
          FieldValidator.checkLength(decompLength);
          resource.setDecompressedLength(decompLength);
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
   * Writes an [archive] File with the contents of the Resources
   **********************************************************************************************
   **/
  @Override
  public void write(Resource[] resources, File path) {
    try {

      int numFiles = resources.length;
      TaskProgressManager.setMaximum(numFiles);

      // WRITE THE FILE THAT CONTAINS THE DIRECTORY
      File dirPath = getDirectoryFile(path, "RFH", false);
      FileManipulator fm = new FileManipulator(dirPath, true);

      // Calculations
      TaskProgressManager.setMessage(Language.get("Progress_PerformingCalculations"));

      // Write Directory
      TaskProgressManager.setMessage(Language.get("Progress_WritingDirectory"));
      for (int i = 0; i < numFiles; i++) {
        Resource resource = resources[i];

        String name = resource.getName();
        int nameLength = name.length() + 1;

        // 4 - Filename Length (including the null)
        fm.writeInt(nameLength);

        // X - Filename
        fm.writeString(name);

        // 1 - null Filename Terminator
        fm.writeByte(0);

        if (resource.isReplaced()) {
          // replaced
          // 4 - File Length
          long decompLength = resource.getDecompressedLength();
          fm.writeInt((int) decompLength);

          // 4 - Compression Flag (0=uncompressed, 1=compressed)
          fm.writeInt(0);

        }
        else {
          // not replaced - copy from the source archive

          // 4 - File Length
          long length = resource.getLength();
          fm.writeInt((int) length);

          // 4 - Compression Flag (0=uncompressed, 1=compressed)
          if (resource.getExporter() instanceof Exporter_Custom_RFD_2) {
            fm.writeInt(1);
          }
          else {
            fm.writeInt(0);
          }
        }

      }

      fm.close();

      // WRITE THE FILE THAT CONTAINS THE DATA
      fm = new FileManipulator(path, true);

      ExporterPlugin exporterDefault = Exporter_Default.getInstance();

      // Write Files
      TaskProgressManager.setMessage(Language.get("Progress_WritingFiles"));
      for (int i = 0; i < resources.length; i++) {
        Resource resource = resources[i];

        // If the file hasn't been replaced, use the Default exporter to just copy in the compressed format.
        // If the file *has* been replaced, use the Default exporter as well, we will store uncompressed.
        ExporterPlugin originalExporter = resource.getExporter();
        resource.setExporter(exporterDefault);

        write(resource, fm);

        resource.setExporter(originalExporter);

        TaskProgressManager.setValue(i);
      }

      //ExporterPlugin exporter = Exporter_ZLib.getInstance();
      //long[] compressedLengths = write(exporter,resources,fm);

      fm.close();

    }
    catch (Throwable t) {
      logError(t);
    }
  }

}