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
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.watto.ErrorLogger;
import org.watto.Language;
import org.watto.component.WSPluginException;
import org.watto.datatype.Archive;
import org.watto.datatype.Resource;
import org.watto.ge.helper.FieldValidator;
import org.watto.ge.plugin.ArchivePlugin;
import org.watto.ge.plugin.ExporterPlugin;
import org.watto.ge.plugin.exporter.Exporter_Deflate_XOR;
import org.watto.io.FileManipulator;
import org.watto.io.buffer.XORBufferWrapper;
import org.watto.io.converter.ByteArrayConverter;
import org.watto.io.converter.IntConverter;
import org.watto.io.stream.ManipulatorOutputStream;
import org.watto.task.TaskProgressManager;

/**
**********************************************************************************************

**********************************************************************************************
**/
public class Plugin_DAT_115 extends ArchivePlugin {

  /**
  **********************************************************************************************
  
  **********************************************************************************************
  **/
  public Plugin_DAT_115() {

    super("DAT_115", "DAT_115");

    //         read write replace rename
    setProperties(true, true, false, false);

    setGames("El Matador");
    setExtensions("dat"); // MUST BE LOWER CASE
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
      if (fm.readInt() == -1700932146) { // PK header, XOR with (byte)158
        rating += 50;
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
   Reads an [archive] File into the Resources
   CODE COPIED FROM ZIP_PK.readManually() and then modified to use the XOR key
   Also uses the central directory for locating the files, rather than reading through manually 
   **********************************************************************************************
   **/
  @Override
  public Resource[] read(File path) {
    try {

      // NOTE - Compressed files MUST know their DECOMPRESSED LENGTH
      //      - Uncompressed files MUST know their LENGTH

      addFileTypes();

      ExporterPlugin exporter = new Exporter_Deflate_XOR(158);

      // RESETTING GLOBAL VARIABLES

      FileManipulator fm = new FileManipulator(path, false);
      fm.setBuffer(new XORBufferWrapper(fm.getBuffer(), 158)); // Set the XOR encryption

      long arcSize = fm.getLength();

      int numFiles = Archive.getMaxFiles();
      Resource[] resources = new Resource[numFiles];

      TaskProgressManager.setMaximum(arcSize);

      int realNumFiles = 0;

      // skip to the footer, to find the location of the central directory
      fm.seek(arcSize - 6);
      long dirOffset = IntConverter.unsign(fm.readInt());
      FieldValidator.checkOffset(dirOffset);
      fm.seek(dirOffset);

      while (fm.getOffset() < arcSize) {

        // 2 - Header (PK)
        fm.skip(2);
        /*
        // do a look here, for a few bytes, to see if we can find the PK header,
        // either at this offset, or maybe padded off by a few bytes.
        for (int p = 0; p < 256; p++) {
          byte nextByte = fm.readByte();
          boolean found = false;
          while (nextByte == 80) {
            nextByte = fm.readByte();
            found = true;
          }
          if (found && nextByte == 75) {
            // found the header
            break;
          }
        }
        System.out.println(fm.getOffset() - 2);
        */

        // 4 - Entry Type (1311747 = File Entry)
        int entryType = fm.readInt();
        if (entryType == 1311747) {
          // File Entry

          // 2 - Unknown (2)
          fm.skip(2);

          // 2 - Compression Method
          short compType = fm.readShort();

          // 8 - Checksum?
          fm.skip(8);

          // 4 - Compressed File Size
          int length = fm.readInt();
          FieldValidator.checkLength(length, arcSize);

          // 4 - Decompressed File Size
          int decompLength = fm.readInt();
          FieldValidator.checkLength(decompLength);

          // 2 - Filename Length
          int filenameLength = fm.readShort();
          FieldValidator.checkFilenameLength(filenameLength);

          // 2 - Extra Data Length
          int extraLength = fm.readShort();
          FieldValidator.checkLength(extraLength, arcSize);

          // X - Filename
          String filename = fm.readString(filenameLength);

          // X - Extra Data
          fm.skip(extraLength);

          // X - File Data
          long offset = fm.getOffset();
          fm.skip(length);

          if (length != 0) {
            if (compType == 0) {
              // uncompressed

              //path,name,offset,length,decompLength,exporter
              resources[realNumFiles] = new Resource(path, filename, offset, length);
            }
            else {
              // compressed - probably Deflate

              //path,name,offset,length,decompLength,exporter
              resources[realNumFiles] = new Resource(path, filename, offset, length, decompLength, exporter);
            }
            realNumFiles++;
          }

          TaskProgressManager.setValue(offset);

        }
        else if (entryType == 513 || entryType == 1311233 || entryType == 2753025 || entryType == 2949633) {
          // Directory Entry

          // 2 - Unknown (20)
          fm.skip(2);

          // 2 - Unknown (2)
          fm.skip(2);

          // 2 - Compression Method
          short compType = fm.readShort();

          // 8 - Checksum?
          fm.skip(8);

          // 4 - Compressed File Size
          int length = fm.readInt();
          FieldValidator.checkLength(length, arcSize);

          // 4 - Decompressed File Size
          int decompLength = fm.readInt();
          FieldValidator.checkLength(decompLength);

          // 2 - Filename Length
          int filenameLength = fm.readShort();
          FieldValidator.checkFilenameLength(filenameLength);

          // 2 - Extra Data Length
          int extraLength = fm.readShort();
          FieldValidator.checkLength(extraLength, arcSize);

          // 10 - null
          fm.skip(10);

          // 4 - File Offset (points to PK for this file in the directory)
          long offset = IntConverter.unsign(fm.readInt()) + 30 + filenameLength;
          FieldValidator.checkOffset(offset, arcSize);

          // X - Filename
          String filename = fm.readString(filenameLength);

          // X - Extra Data
          fm.skip(extraLength);

          if (length != 0) {
            if (compType == 0) {
              // uncompressed

              //path,name,offset,length,decompLength,exporter
              resources[realNumFiles] = new Resource(path, filename, offset, length);
            }
            else {
              // compressed - probably Deflate

              //path,name,offset,length,decompLength,exporter
              resources[realNumFiles] = new Resource(path, filename, offset, length, decompLength, exporter);
            }
            realNumFiles++;
          }

          TaskProgressManager.setValue(offset);

        }
        else if (entryType == 656387) {
          // Directory Entry (Short) (or sometimes a file)

          // 2 - Unknown (20)
          fm.skip(2);

          // 2 - Unknown (2)
          short compType = fm.readShort();

          // 8 - Checksum?
          fm.skip(8);

          // 4 - Compressed File Size
          int length = fm.readInt();
          FieldValidator.checkLength(length, arcSize);

          // 4 - Decompressed File Size
          int decompLength = fm.readInt();
          FieldValidator.checkLength(decompLength);

          // 4 - Filename Length
          int filenameLength = fm.readShort();
          fm.skip(2);
          FieldValidator.checkFilenameLength(filenameLength);

          // X - Filename
          String filename = fm.readString(filenameLength);

          // X - File Data
          if (length != 0) {
            long offset = fm.getOffset();
            fm.skip(length);

            if (compType == 0) {
              // uncompressed

              //path,name,offset,length,decompLength,exporter
              resources[realNumFiles] = new Resource(path, filename, offset, length);
            }
            else {
              // compressed - probably Deflate

              //path,name,offset,length,decompLength,exporter
              resources[realNumFiles] = new Resource(path, filename, offset, length, decompLength, exporter);
            }
            realNumFiles++;

            TaskProgressManager.setValue(offset);
          }

        }
        else if (entryType == 1541) {
          // EOF Entry

          // 2 - null
          // 8 - Checksum?
          // 4 - Length Of File Data (archive size excluding the directory)
          // 2 - null
          fm.skip(16);
        }

        /*// don't want this, as we try Crypto before we try Manual
        else if (entryType == 51643395 || entryType == 2032129) {
          // Encrypted File Entry
          fm.close();
          return readZipCrypto(path);
        }
        */
        else {
          byte[] intBytes = ByteArrayConverter.convertLittle(entryType);
          if (intBytes[0] == 7 && intBytes[1] == 8) {
            // Not sure
            // 10 - Unknown
            fm.skip(10);
          }
          else {
            // bad header
            String errorMessage = "[DAT_115]: Manual read: Unknown entry type " + entryType + " at offset " + (fm.getOffset() - 6);
            if (realNumFiles >= 5) {
              // we found a number of files, so lets just return them, it might be a "prematurely-short" archive.
              ErrorLogger.log(errorMessage);
              break;
            }
            else {
              throw new WSPluginException(errorMessage);
            }
          }
        }

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

    /*
    if (headerInt1 == 2037149520) {
      return "js";
    }
    */

    return null;
  }

  /**
   **********************************************************************************************
   Copied from ZIP_PK.write() with XOR buffers added to it
   **********************************************************************************************
   **/
  @Override
  public void write(Resource[] resources, File path) {
    try {

      // 2.0 - this doesn't actually work???
      //if (path.exists()){
      //  path = FileBuffer.checkFilename(path);
      //  FileBuffer.makeDirectory(path.getAbsolutePath());
      //  }

      FileManipulator fm = new FileManipulator(path, true, 1048576); // 1MB buffer for writing
      fm.setBuffer(new XORBufferWrapper(fm.getBuffer(), 158)); // Set the XOR encryption

      //ZipOutputStream outputStream = new ZipOutputStream(new FileOutputStream(path));
      ZipOutputStream outputStream = new ZipOutputStream(new ManipulatorOutputStream(fm));

      int numFiles = resources.length;
      TaskProgressManager.setMaximum(numFiles);

      TaskProgressManager.setMessage(Language.get("Progress_WritingFiles"));
      for (int i = 0; i < numFiles; i++) {

        ZipEntry cpZipEntry = new ZipEntry(resources[i].getName());

        outputStream.putNextEntry(cpZipEntry);
        resources[i].extract(outputStream);
        outputStream.closeEntry();

        TaskProgressManager.setValue(i);
      }

      outputStream.finish();
      outputStream.close();

    }
    catch (Throwable t) {
      logError(t);
    }
  }

}
