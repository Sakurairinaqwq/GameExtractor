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
import org.watto.datatype.Archive;
import org.watto.datatype.FileType;
import org.watto.datatype.Resource;
import org.watto.ge.helper.FieldValidator;
import org.watto.ge.plugin.ArchivePlugin;
import org.watto.ge.plugin.ExporterPlugin;
import org.watto.ge.plugin.exporter.BlockVariableExporterWrapper;
import org.watto.ge.plugin.exporter.Exporter_Default;
import org.watto.ge.plugin.exporter.Exporter_LZO_SingleBlock;
import org.watto.ge.plugin.exporter.Exporter_Oodle;
import org.watto.ge.plugin.exporter.Exporter_QuickBMS_DLL;
import org.watto.ge.plugin.exporter.Exporter_ZStd;
import org.watto.io.FileManipulator;
import org.watto.io.converter.ShortConverter;
import org.watto.task.TaskProgressManager;

/**
**********************************************************************************************

**********************************************************************************************
**/
public class Plugin_FORGE_SCIMITAR extends ArchivePlugin {

  /**
  **********************************************************************************************
  
  **********************************************************************************************
  **/
  public Plugin_FORGE_SCIMITAR() {

    super("FORGE_SCIMITAR", "FORGE_SCIMITAR");

    //         read write replace rename
    setProperties(true, false, false, false);

    setGames("Assassins Creed: Syndicate",
        "For Honor",
        "Steep",
        "Tom Clancy's Ghost Recon Wildlands");
    setExtensions("forge"); // MUST BE LOWER CASE
    setPlatforms("PC");

    // MUST BE LOWER CASE !!!
    setFileTypes(new FileType("forge_dat", "DAT Archive", FileType.TYPE_ARCHIVE));

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
      if (fm.readString(8).equals("scimitar")) {
        rating += 50;
      }

      fm.skip(5);

      long arcSize = fm.getLength();

      // 4 - Details Directory Offset
      if (FieldValidator.checkOffset(fm.readInt(), arcSize)) {
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

      ExporterPlugin exporterDefault = Exporter_Default.getInstance();
      ExporterPlugin exporterLZO1X = Exporter_LZO_SingleBlock.getInstance();
      ExporterPlugin exporterLZO1C = new Exporter_QuickBMS_DLL("LZO1C");
      ExporterPlugin exporterLZO2A = new Exporter_QuickBMS_DLL("LZO2A");
      ExporterPlugin exporterZSTD = Exporter_ZStd.getInstance();
      ExporterPlugin exporterOodle = Exporter_Oodle.getInstance();

      // RESETTING GLOBAL VARIABLES

      FileManipulator fm = new FileManipulator(path, false);

      long arcSize = fm.getLength();

      // 8 - Header (scimitar)
      // 1 - null
      // 4 - Unknown (27)
      fm.skip(13);

      // 4 - Details Directory Offset
      int dirOffset = fm.readInt();
      FieldValidator.checkOffset(dirOffset, arcSize);

      fm.seek(dirOffset);

      // 4 - Number Of Files
      int numFiles = fm.readInt();
      FieldValidator.checkNumFiles(numFiles);

      // 4 - Unknown (1)
      // 12 - null
      // 8 - Unknown (-1)
      // 4 - Number of Files (including the blank entry)
      // 4 - Unknown (1)
      // 4 - This Offset [-8]
      // 4 - null
      // 4 - Number of Files
      // 4 - Unknown (1)
      // 8 - Offset to the Start of the File Entries Loop
      // 8 - Unknown (-1)
      // 4 - null
      // 4 - Number of Files
      fm.skip(72);

      // 8 - Filename Directory Offset
      long filenameDirOffset = fm.readLong();
      FieldValidator.checkOffset(filenameDirOffset, arcSize);

      // 8 - Offset to the End of the Filename Directory (including the blank entry)
      long filenameEndDirOffset = fm.readLong() + 1;

      Resource[] resources = new Resource[numFiles];
      TaskProgressManager.setMaximum(numFiles);

      // Loop through directory
      int realNumFiles = 0;
      boolean useRelativeOffsets = false;
      long relativeOffset = 0;
      for (int i = 0; i < numFiles; i++) {
        // 8 - File Offset
        long offset = fm.readLong();
        //FieldValidator.checkOffset(offset, arcSize);

        // 4 - Hash?
        // 4 - null
        fm.skip(8);

        // 4 - File Length
        int length = fm.readInt();
        FieldValidator.checkLength(length, arcSize);

        if (offset > arcSize && i == 0) {
          // this is probably a decrypted file... so we need to calculate the offsets relative to the filenameEndDirOffset
          useRelativeOffsets = true;
          relativeOffset = filenameEndDirOffset;
          offset = filenameEndDirOffset;
        }

        if (useRelativeOffsets) {
          offset = relativeOffset;
        }

        String filename = Resource.generateFilename(realNumFiles) + ".forge_dat";

        //path,name,offset,length,decompLength,exporter
        resources[realNumFiles] = new Resource(path, filename, offset, length);
        realNumFiles++;

        relativeOffset += length;

        TaskProgressManager.setValue(i);
      }

      if (realNumFiles != numFiles) {
        resources = resizeResources(resources, realNumFiles);
        numFiles = realNumFiles;
      }

      fm.seek(filenameDirOffset);

      try {
        // Loop through filename directory
        String[] names = new String[numFiles];
        for (int i = 0; i < numFiles; i++) {
          // 4 - File Length
          // 8 - Hash?
          // 16 - null
          // 4 - File ID (Incremental from 1)
          // 4 - File ID (Incremental from -1)
          // 4 - null
          // 2 - File Type ID?
          // 2 - Unknown
          fm.skip(44);

          // 128 - Filename (null terminated, filled with nulls)
          String filename = fm.readNullString(128);
          FieldValidator.checkFilename(filename);
          names[i] = filename + ".forge_dat";

          // 4 - Unknown (4)
          // 8 - null
          // 4 - Unknown (4)
          // 4 - null
          fm.skip(20);

          TaskProgressManager.setValue(i);
        }

        // Loop through filename directory
        for (int i = 0; i < numFiles; i++) {
          String filename = names[i];

          //path,name,offset,length,decompLength,exporter
          Resource resource = resources[i];
          resource.setName(filename);
          resource.setOriginalName(filename);

          TaskProgressManager.setValue(i);
        }
      }
      catch (Throwable t) {
        // Newer archives have 320 bytes per entry, not 192. Also, the 320-byte ones are encrypted.
        // So, if this happens, just leave as default filenames.
      }

      // go to each file and check for compression
      fm.getBuffer().setBufferSize(128);

      for (int i = 0; i < numFiles; i++) {
        try {
          Resource resource = resources[i];

          long resourceOffset = resource.getOffset();
          fm.seek(resourceOffset);
          long endOffset = resourceOffset + resource.getLength();

          int maxChunks = Archive.getMaxFiles();
          long[] chunkOffsets = new long[maxChunks];
          long[] chunkLengths = new long[maxChunks];
          long[] chunkDecompLengths = new long[maxChunks];
          ExporterPlugin[] chunkExporters = new ExporterPlugin[maxChunks];
          int currentChunk = 0;

          long totalDecompLength = 0;

          // there can be multiple compressed blocks in each file, and each compressed block is made up of smaller compressed chunks
          int chunkFieldLength = 0;
          while (fm.getOffset() < endOffset) {
            // 8 - Compression Header
            //long compressionHeader = fm.readLong();
            //if (compressionHeader != 1154322941026740787l) {
            int compressionHeader = fm.readInt();
            fm.skip(4);
            if (compressionHeader != 1476110899 && compressionHeader != 1476110900) {
              // not compressed
              if (currentChunk == 0) {
                System.out.println("File at offset " + resourceOffset + " is not compressed.");
              }
              else {
                System.out.println("Couldn't find the compression header part-way through the file at offset " + resourceOffset);
              }
              break;
            }

            // 2 - Version (1)
            int version = fm.readShort();

            // 1 - Compression Type (0/1=LZO1X, 2=LZO2A, 3=ZSTD, 4=OODLE, 5=LZO1C, 7/8=OODLE)
            int compressionType = fm.readByte();
            ExporterPlugin chunkExporter = exporterDefault;
            if (compressionType == 0 || compressionType == 1) {
              chunkExporter = exporterLZO1X;
            }
            else if (compressionType == 2) {
              chunkExporter = exporterLZO2A;
            }
            else if (compressionType == 3) {
              chunkExporter = exporterZSTD;
            }
            else if (compressionType == 5) {
              chunkExporter = exporterLZO1C;
            }
            else {
              chunkExporter = exporterOodle;
            }

            // 2 - Maximum Decompressed Chunk Size
            // 2 - Maximum Compressed Chunk Size
            fm.skip(4);

            // 2/4 - Number of Chunks
            long chunkCheckOffset = fm.getOffset();
            int numChunks = 0;
            if (chunkFieldLength == 0) {
              // check for the size
              numChunks = fm.readInt();
              if (numChunks > 65536) {
                // probably just a short field
                fm.relativeSeek(chunkCheckOffset);
                numChunks = fm.readShort();
                chunkFieldLength = 2;
              }
              else {
                chunkFieldLength = 4;
              }
            }
            else {
              // use the size we've found previously

              if (chunkFieldLength == 2) {
                numChunks = fm.readShort();
              }
              else if (chunkFieldLength == 4) {
                numChunks = fm.readInt();
              }
            }

            FieldValidator.checkNumFiles(numChunks);

            long offset = fm.getOffset();
            if (version <= 1) {
              offset += (numChunks * 4);
            }
            else {
              offset += (numChunks * 8);
            }
            for (int c = 0; c < numChunks; c++) {
              if (version <= 1) {
                // 2 - Decompressed Chunk Length
                int decompLength = ShortConverter.unsign(fm.readShort());
                chunkDecompLengths[currentChunk] = decompLength;

                // 2 - Compressed Chunk Length
                int length = ShortConverter.unsign(fm.readShort());
                chunkLengths[currentChunk] = length;

                offset += 4; // skip the CRC on the compressed chunk
                chunkOffsets[currentChunk] = offset;

                if (length == decompLength) {
                  // raw
                  chunkExporters[currentChunk] = exporterDefault;
                }
                else {
                  // compressed
                  chunkExporters[currentChunk] = chunkExporter;
                }

                totalDecompLength += decompLength;
                offset += length; // ready for the next chunk
                currentChunk++;
              }
              else {
                // 4 - Decompressed Chunk Length
                int decompLength = fm.readInt();
                chunkDecompLengths[currentChunk] = decompLength;

                // 4 - Compressed Chunk Length
                int length = fm.readInt();
                chunkLengths[currentChunk] = length;

                offset += 4; // skip the CRC on the compressed chunk
                chunkOffsets[currentChunk] = offset;

                if (length == decompLength) {
                  // raw
                  chunkExporters[currentChunk] = exporterDefault;
                }
                else {
                  // compressed
                  chunkExporters[currentChunk] = chunkExporter;
                }

                totalDecompLength += decompLength;
                offset += length; // ready for the next chunk
                currentChunk++;
              }

            }

            // move to the end of the compressed chunks, in case there's more compressed chunks to process afterwards
            fm.seek(offset);

          }

          if (currentChunk > 0) {
            // shrink the 3 arrays to the correct number of chunks
            long[] cutChunkOffsets = new long[currentChunk];
            System.arraycopy(chunkOffsets, 0, cutChunkOffsets, 0, currentChunk);
            long[] cutChunkLengths = new long[currentChunk];
            System.arraycopy(chunkLengths, 0, cutChunkLengths, 0, currentChunk);
            long[] cutChunkDecompLengths = new long[currentChunk];
            System.arraycopy(chunkDecompLengths, 0, cutChunkDecompLengths, 0, currentChunk);
            ExporterPlugin[] cutChunkExporters = new ExporterPlugin[currentChunk];
            System.arraycopy(chunkExporters, 0, cutChunkExporters, 0, currentChunk);

            BlockVariableExporterWrapper blockExporter = new BlockVariableExporterWrapper(cutChunkExporters, cutChunkOffsets, cutChunkLengths, cutChunkDecompLengths);
            resource.setDecompressedLength(totalDecompLength);
            resource.setExporter(blockExporter);
          }

          TaskProgressManager.setValue(i);
        }
        catch (Throwable t) {
          // Ignore errors with compression determination
          ErrorLogger.log(t);
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

}
