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
import org.watto.ge.plugin.exporter.Exporter_Default;
import org.watto.ge.plugin.exporter.Exporter_LZWX;
import org.watto.io.FileManipulator;
import org.watto.task.TaskProgressManager;

/**
**********************************************************************************************

**********************************************************************************************
**/
public class Plugin_DAT_108 extends ArchivePlugin {

  /**
  **********************************************************************************************
  
  **********************************************************************************************
  **/
  public Plugin_DAT_108() {

    super("DAT_108", "DAT_108");

    //         read write replace rename
    setProperties(true, true, false, false);

    setGames("Screamer");
    setExtensions("dat"); // MUST BE LOWER CASE
    setPlatforms("PC");

    // MUST BE LOWER CASE !!!
    //setFileTypes(new FileType("txt", "Text Document", FileType.TYPE_DOCUMENT),
    //             new FileType("bmp", "Bitmap Image", FileType.TYPE_IMAGE)
    //             );

    setTextPreviewExtensions("dtr"); // LOWER CASE

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

      long arcSize = fm.getLength();

      long dirOffset = arcSize - 36;
      FieldValidator.checkOffset(dirOffset, arcSize);

      fm.seek(dirOffset);

      // 4 - Filename XOR Key (58)
      if (fm.readInt() == 58) {
        rating += 5;
      }

      // 28 - null
      if (fm.readLong() == 0 && fm.readLong() == 0 && fm.readLong() == 0 && fm.readInt() == 0) {
        rating += 5;
      }

      // 4 - Directory Length (not including this field)
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

      ExporterPlugin exporterLZWX = Exporter_LZWX.getInstance();

      // RESETTING GLOBAL VARIABLES

      FileManipulator fm = new FileManipulator(path, false);

      long arcSize = fm.getLength();

      long dirOffset = arcSize - 36;
      FieldValidator.checkOffset(dirOffset, arcSize);

      fm.seek(dirOffset);

      // 4 - Filename XOR Key (58)
      int xorKey = fm.readInt();
      FieldValidator.checkRange(xorKey, 0, 255);

      // 28 - null
      fm.skip(28);

      // 4 - Directory Length (not including this field)
      int dirLength = fm.readInt();
      dirOffset = arcSize - dirLength - 4;
      FieldValidator.checkOffset(dirOffset, arcSize);

      fm.seek(dirOffset);

      int numFiles = (dirLength / 32) - 1;
      FieldValidator.checkNumFiles(numFiles);

      Resource[] resources = new Resource[numFiles];
      TaskProgressManager.setMaximum(numFiles);

      // Loop through directory
      for (int i = 0; i < numFiles; i++) {

        // 16 - Filename (the filename + the first null are encrypted by the XOR key value) (null terminated, filled with nulls)
        byte[] filenameBytes = fm.readBytes(16);
        int filenameLength = 16;
        for (int b = 0; b < 16; b++) {
          if (filenameBytes[b] == xorKey) {
            // terminator
            filenameLength = b;
            break;
          }
          else {
            filenameBytes[b] ^= xorKey;
          }
        }
        String filename = new String(filenameBytes, 0, filenameLength);

        // 4 - File Offset
        int offset = fm.readInt();
        FieldValidator.checkOffset(offset, arcSize);

        // 4 - Compressed File Length
        int length = fm.readInt();
        FieldValidator.checkLength(length, arcSize);

        // 4 - Decompressed File Length
        int decompLength = fm.readInt();
        FieldValidator.checkLength(decompLength);

        // 4 - Compression Type (0=uncompressed, 3=unknown)
        int compression = fm.readInt();

        if (compression == 0) {
          // uncompressed

          //path,name,offset,length,decompLength,exporter
          resources[i] = new Resource(path, filename, offset, length, decompLength);
        }
        else if (compression == 3 || compression == 2) {
          // LZWX compression

          //path,name,offset,length,decompLength,exporter
          Resource resource = new Resource(path, filename, offset, length, decompLength, exporterLZWX);
          resource.addProperty("CompressionType", compression);
          resources[i] = resource;
        }
        else {
          ErrorLogger.log("[DAT_108] Unknown compression type: " + compression);

          //path,name,offset,length,decompLength,exporter
          resources[i] = new Resource(path, filename, offset, length, decompLength);
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
   * Writes an [archive] File with the contents of the Resources
   **********************************************************************************************
   **/
  @Override
  public void write(Resource[] resources, File path) {
    try {

      FileManipulator fm = new FileManipulator(path, true);
      int numFiles = resources.length;
      TaskProgressManager.setMaximum(numFiles);

      ExporterPlugin exporterDefault = Exporter_Default.getInstance();

      // Write Files
      TaskProgressManager.setMessage(Language.get("Progress_WritingFiles"));
      for (int i = 0; i < resources.length; i++) {
        Resource resource = resources[i];

        if (!resource.isReplaced()) {
          // original file from the archive - keep it compressed if it already is
          ExporterPlugin originalExporter = resource.getExporter();
          resource.setExporter(exporterDefault);
          write(resource, fm);
          resource.setExporter(originalExporter);
        }
        else {
          // write normally for any other file
          write(resource, fm);
        }

        TaskProgressManager.setValue(i);
      }

      int xorKey = 58;

      // Write Directory
      TaskProgressManager.setMessage(Language.get("Progress_WritingDirectory"));
      long offset = 0;
      for (int i = 0; i < numFiles; i++) {
        Resource resource = resources[i];

        long compLength = resource.getLength();
        long decompLength = resource.getDecompressedLength();

        // 16 - Filename (the filename + the first null are encrypted by the XOR key value) (null terminated, filled with nulls)
        String filename = resource.getName();
        if (filename.length() > 16) {
          filename = filename.substring(0, 16);
        }
        byte[] filenameBytes = filename.getBytes();
        int filenameLength = filenameBytes.length;
        for (int b = 0; b < filenameLength; b++) {
          fm.writeByte(filenameBytes[b] ^ xorKey);
        }
        if (filenameLength < 16) {
          fm.writeByte(xorKey);
          filenameLength++;
        }

        int padding = 16 - filenameLength;
        for (int p = 0; p < padding; p++) {
          fm.writeByte(0);
        }

        // 4 - File Offset
        fm.writeInt((int) offset);

        // 4 - Compressed File Length
        fm.writeInt((int) compLength);

        // 4 - Decompressed File Length
        fm.writeInt((int) decompLength);

        // 4 - Compression Type (0=uncompressed, 3=LZWX)
        if (compLength == decompLength) {
          fm.writeInt(0);
        }
        else {
          if (!resource.isReplaced()) {
            int compressionType = 3;
            try {
              compressionType = Integer.parseInt(resource.getProperty("CompressionType"));
            }
            catch (Throwable t) {
              // ignore
            }
            fm.writeInt(compressionType);
          }
          else {
            fm.writeInt(3);
          }
        }

        offset += compLength;
      }

      // 4 - Filename XOR Key (58)
      fm.writeInt(xorKey);

      // 28 - null
      int padding = 28;
      for (int p = 0; p < padding; p++) {
        fm.writeByte(0);
      }

      // 4 - Directory Length (not including this field)
      fm.writeInt((numFiles + 1) * 32);

      fm.close();

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
