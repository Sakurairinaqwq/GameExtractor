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
public class Plugin_FORGE extends ArchivePlugin {
  /**
  **********************************************************************************************
  
  **********************************************************************************************
  **/
  public Plugin_FORGE() {

    super("FORGE", "FORGE");

    //         read write replace rename
    setProperties(true, false, false, false);

    setGames("For Honor");
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

      //
      //
      // THIS PLUGIN JUST READS THROUGH THE ARCHIVE (which probably has an encrypted directory), FINDS THE
      // INDIVIDUAL COMPRESSED FILES, AND RETURNS THEM. THIS SHOULD ONLY BE USED IF THE PROPER PLUGIN FAILS.
      //
      //

      ExporterPlugin exporterDefault = Exporter_Default.getInstance();
      ExporterPlugin exporterLZO1X = Exporter_LZO_SingleBlock.getInstance();
      ExporterPlugin exporterLZO1C = new Exporter_QuickBMS_DLL("LZO1C");
      ExporterPlugin exporterLZO2A = new Exporter_QuickBMS_DLL("LZO2A");
      ExporterPlugin exporterZSTD = Exporter_ZStd.getInstance();
      ExporterPlugin exporterOodle = Exporter_Oodle.getInstance();

      // RESETTING GLOBAL VARIABLES

      FileManipulator fm = new FileManipulator(path, false);

      long arcSize = fm.getLength();

      int numFiles = Archive.getMaxFiles();

      Resource[] resources = new Resource[numFiles];
      TaskProgressManager.setMaximum(arcSize);

      int realNumFiles = 0;
      while (fm.getOffset() < arcSize) {
        int currentByte = fm.readByte();
        if (currentByte == 51 || currentByte == 52) {
          if (fm.readByte() == -86) {
            if (fm.readByte() == -5) {
              if (fm.readByte() == 87) {
                // found the compression header

                // 8 - Compression Header (already read 4 bytes above)
                fm.skip(4);

                int maxChunks = Archive.getMaxFiles();
                long[] chunkOffsets = new long[maxChunks];
                long[] chunkLengths = new long[maxChunks];
                long[] chunkDecompLengths = new long[maxChunks];
                ExporterPlugin[] chunkExporters = new ExporterPlugin[maxChunks];
                int currentChunk = 0;

                // there can be multiple compressed blocks in each file, and each compressed block is made up of smaller compressed chunks
                int chunkFieldLength = 0;

                int totalDecompLength = 0;
                int totalCompLength = 0;

                long offset = 0; // just need this outside the loop below, the value doesn't matter yet.

                try {
                  // BLOCKS COME IN PAIRS, WITH THE DIRECTORY FIRST, AND THE DATA SECOND. SO, LOOP TWICE FOR EACH FILE
                  for (int i = 0; i < 2; i++) {
                    if (i != 0) { // we only need to do this for the second piece, as we've already done it for the first piece.
                      // 8 - Compression Header
                      fm.skip(8);
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
                      if (numChunks > 65536 || numChunks < 0) {
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

                    offset = fm.getOffset();
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
                        totalCompLength += length;
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
                        totalCompLength += length;
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

                    String filename = Resource.generateFilename(realNumFiles) + ".forge_dat";

                    resources[realNumFiles] = new Resource(path, filename, offset, totalCompLength, totalDecompLength, blockExporter);
                    realNumFiles++;

                  }

                }
                catch (Throwable t) {
                  // Ignore errors with compression determination
                  ErrorLogger.log(t);
                }

              }
            }
          }
        }

        TaskProgressManager.setValue(fm.getOffset());
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

}
