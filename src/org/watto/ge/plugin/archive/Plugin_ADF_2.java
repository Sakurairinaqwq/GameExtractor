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
import org.watto.Settings;
import org.watto.datatype.Archive;
import org.watto.datatype.FileType;
import org.watto.datatype.Resource;
import org.watto.ge.helper.FieldValidator;
import org.watto.ge.plugin.ArchivePlugin;
import org.watto.ge.plugin.ExporterPlugin;
import org.watto.ge.plugin.exporter.Exporter_Default;
import org.watto.ge.plugin.exporter.Exporter_ZLib;
import org.watto.io.FileManipulator;
import org.watto.task.TaskProgressManager;

/**
**********************************************************************************************

**********************************************************************************************
**/
public class Plugin_ADF_2 extends ArchivePlugin {

  /**
  **********************************************************************************************
  
  **********************************************************************************************
  **/
  public Plugin_ADF_2() {

    super("ADF_2", "ADF_2");

    //         read write replace rename
    setProperties(true, false, true, false);

    setGames("A.R.S.E.N.A.L Extended Power");
    setExtensions("adf");
    setPlatforms("PC");

    // MUST BE LOWER CASE !!!
    setFileTypes(new FileType("rgb", "RGB Image Archive", FileType.TYPE_ARCHIVE));

    setTextPreviewExtensions("acf", "adu", "nam", "tab"); // LOWER CASE

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

      int i = 0;
      while (i < 100) {
        if (fm.readByte() == 0) {
          i = 100;
        }
        i++;
      }
      fm.skip(4);

      long arcSize = fm.getLength();

      // First File Length
      if (FieldValidator.checkLength(fm.readInt(), arcSize)) {
        rating += 5;
      }

      // ZLib compressed file data
      if (fm.readString(1).equals("x")) {
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
  
  **********************************************************************************************
  **/
  @Override
  public Resource[] read(File path) {
    try {

      ExporterPlugin exporter = Exporter_ZLib.getInstance();
      addFileTypes();

      // RESETTING THE GLOBAL VARIABLES

      FileManipulator fm = new FileManipulator(path, false, 255); // small quick reads

      int numFiles = Archive.getMaxFiles(4);

      long arcSize = fm.getLength();

      Resource[] resources = new Resource[numFiles];
      TaskProgressManager.setMaximum(arcSize);

      // Loop through directory
      int realNumFiles = 0;
      while (fm.getOffset() < fm.getLength()) {
        // X - Encrypted Filename? (null)
        byte[] nameBytes = new byte[255]; // guess max

        int currentByte = fm.readByte();
        int numBytes = 0;
        while (currentByte != 0) {
          nameBytes[numBytes] = (byte) ((currentByte - 66) & 0xFF);
          numBytes++;
          currentByte = fm.readByte();
        }

        byte[] shortNameBytes = new byte[numBytes - 2]; // the filename has <> characters around it, so skip those when copied to the shorter array
        System.arraycopy(nameBytes, 1, shortNameBytes, 0, numBytes - 2);
        String filename = new String(shortNameBytes);

        // 4 - Decompressed File Size
        int decompLength = fm.readInt();

        // 4 - Compressed File Size
        long length = fm.readInt();
        FieldValidator.checkLength(length, arcSize);

        // X - File Data
        long offset = (int) fm.getOffset();
        fm.skip(length);

        //String filename = Resource.generateFilename(realNumFiles);

        //path,id,name,offset,length,decompLength,exporter
        resources[realNumFiles] = new Resource(path, filename, offset, length, decompLength, exporter);

        TaskProgressManager.setValue(offset);
        realNumFiles++;
      }

      fm.close();

      resources = resizeResources(resources, realNumFiles);

      return resources;

    }
    catch (Throwable t) {
      logError(t);
      return null;
    }
  }

  /**
   **********************************************************************************************
   * Writes an [archive] File with the contents of the Resources. The archive is written using
   * data from the initial archive - it isn't written from scratch.
   **********************************************************************************************
   **/
  @Override
  public void replace(Resource[] resources, File path) {
    try {

      FileManipulator fm = new FileManipulator(path, true);
      FileManipulator src = new FileManipulator(new File(Settings.getString("CurrentArchive")), false);

      int numFiles = resources.length;
      TaskProgressManager.setMaximum(numFiles);

      ExporterPlugin defaultExporter = Exporter_Default.getInstance();

      // Write Directory and Files
      TaskProgressManager.setMessage(Language.get("Progress_WritingFiles"));
      for (int i = 0; i < numFiles; i++) {
        Resource resource = resources[i];
        long length = resource.getDecompressedLength();

        // X - Encrypted Filename (subtract 66 from each byte)
        // 1 - null Filename Terminator
        int currentByte = src.readByte();
        while (currentByte != 0) {
          fm.writeByte(currentByte);
          currentByte = src.readByte();
        }
        fm.writeByte(0);

        if (resource.isReplaced()) {
          // REPLACED

          // 4 - Decompressed File Size
          fm.writeInt(length);
          src.skip(4);

          // 4 - Compressed File Size
          long offsetForLength = fm.getOffset();

          int srcCompLength = src.readInt();
          fm.writeInt(srcCompLength);

          // X - File Data
          ExporterPlugin exporter = new Exporter_ZLib();
          long compLength = write(exporter, resource, fm);

          long endPos = fm.getOffset();

          fm.seek(offsetForLength);
          fm.writeInt(compLength);
          fm.seek(endPos);

          src.skip(srcCompLength);
        }
        else {
          // ORIGINAL

          // 4 - Decompressed File Size
          fm.writeBytes(src.readBytes(4));

          // 4 - Compressed File Size
          int srcCompLength = src.readInt();
          fm.writeInt(srcCompLength);

          // X - File Data
          ExporterPlugin originalExporter = resource.getExporter();
          resource.setExporter(defaultExporter);
          write(resource, fm);
          resource.setExporter(originalExporter);

          src.skip(srcCompLength);
        }

      }

      //ExporterPlugin exporter = new Exporter_ZLib();
      //long[] compressedLengths = write(exporter,resources,fm);

      src.close();
      fm.close();

    }
    catch (Throwable t) {
      logError(t);
    }
  }

}
