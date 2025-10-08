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
import org.watto.ge.plugin.exporter.Exporter_ZLib;
import org.watto.ge.plugin.exporter.Exporter_ZLib_CompressedSizeOnly;
import org.watto.ge.plugin.exporter.Exporter_ZLib_XOR_RepeatingKey;
import org.watto.io.FileManipulator;
import org.watto.io.buffer.ByteBuffer;
import org.watto.io.converter.ByteConverter;
import org.watto.task.TaskProgressManager;

/**
**********************************************************************************************

**********************************************************************************************
**/
public class Plugin_PAK_76 extends ArchivePlugin {

  /**
  **********************************************************************************************
  
  **********************************************************************************************
  **/
  public Plugin_PAK_76() {

    super("PAK_76", "PAK_76");

    //         read write replace rename
    setProperties(true, false, false, false);

    setGames("Echoes of the Past: Royal House of Stone",
        "Echoes of the Past: The Castle of Shadows",
        "Echoes of the Past: The Citadels of Time",
        "Echoes of the Past: The Kingdom of Despair",
        "Echoes of the Past: The Revenge of the Witch",
        "Echoes of the Past: Wolf Healer");
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

      // 2 - Unknown (463)
      if (fm.readShort() == 463) {
        rating += 5;
      }

      // 2 - Compressed Directory Length
      int compLength = fm.readShort();
      FieldValidator.checkLength(compLength);

      if (fm.readByte() == 120) {
        rating += 5;
      }
      else {
        rating = 0;
      }

      /*
      fm.skip(compLength - 1); // -1 because we've already read 1 byte from the compressed data for the header check above
      
      if (fm.readByte() == 120) {
        rating += 5;
      }
      else {
        rating = 0;
      }
      */

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

      ExporterPlugin exporterZLib = Exporter_ZLib.getInstance();

      // RESETTING GLOBAL VARIABLES

      FileManipulator fm = new FileManipulator(path, false);

      long arcSize = fm.getLength();

      // 2 - Unknown
      fm.skip(2);

      // 2 - Compressed Directory Length
      int compDirLength = fm.readShort();
      FieldValidator.checkLength(compDirLength);

      int decompDirLength = compDirLength * 10; // guess

      // X - Compressed Directory
      byte[] dirBytes = new byte[decompDirLength];
      int decompWritePos = 0;
      Exporter_ZLib_CompressedSizeOnly exporter = Exporter_ZLib_CompressedSizeOnly.getInstance();
      exporter.open(fm, compDirLength, decompDirLength);

      for (int b = 0; b < decompDirLength; b++) {
        if (exporter.available()) { // make sure we read the next bit of data, if required
          dirBytes[decompWritePos++] = (byte) exporter.read();
        }
        else {
          decompDirLength = b;
          break;
        }
      }

      // open the decompressed data for processing
      //fm.close();
      FileManipulator origFM = fm;
      fm = new FileManipulator(new ByteBuffer(dirBytes));

      // 1 - Unknown (1)
      fm.skip(1);

      // 2 - Number of Files
      int numFiles = fm.readShort();
      FieldValidator.checkNumFiles(numFiles);

      Resource[] resources = new Resource[numFiles];
      TaskProgressManager.setMaximum(numFiles);

      // Loop through directory
      long offset = 4 + compDirLength;
      for (int i = 0; i < numFiles; i++) {
        // 1 - Filename Length
        int filenameLength = ByteConverter.unsign(fm.readByte());
        if (filenameLength == 255 || filenameLength == 0) {
          break; // EOF
        }

        // X - Filename
        String filename = fm.readString(filenameLength);

        // 4 - Compressed File Length
        int length = fm.readInt();
        FieldValidator.checkLength(length, arcSize);

        // 4 - Decompressed File Length
        int decompLength = fm.readInt();
        FieldValidator.checkLength(decompLength);

        //path,name,offset,length,decompLength,exporter
        resources[i] = new Resource(path, filename, offset, length, decompLength);

        TaskProgressManager.setValue(i);

        offset += length;
      }

      // Now we need to detect what the compression is
      fm.close();
      fm = origFM;
      fm.seek(0);
      //fm.getBuffer().setBufferSize(1); // small quick reads

      boolean encryptionChecked = false;
      ExporterPlugin encryptionPlugin = null;
      // Note: the keys in the BMS scripts from https://aluigi.altervista.org/quickbms.htm were different to these. These keys have been validated by me.
      int[] key1 = new int[] { 101, 51, 52, 55, 54, 49 }; // The Citadels of Time (confirmed against download from BigFishGames)
      int[] key2 = new int[] { 101, 52, 51, 48, 55, 57 }; // The Revenge of the Witch (confirmed against download from BigFishGames)
      int[] key3 = new int[] { 101, 53, 51, 53, 50, 49 }; // The Kingdom of Despair (confirmed against download from BigFishGames)
      int[] key4 = new int[] { 101, 54, 52, 52, 51, 52 }; // Wolf Healer (confirmed against download from BigFishGames)

      int[][] keys = new int[][] { key1, key2, key3, key4 };

      int[] validKey = null;

      for (int i = 0; i < numFiles; i++) {
        Resource resource = resources[i];
        fm.seek(resource.getOffset());
        if (fm.readByte() == 120) {
          // ZLib
          resource.setExporter(exporterZLib);
        }
        else {
          ///*
          // try a few encryptions+decompressions and see if we can find one that works

          if (!encryptionChecked) {
            encryptionChecked = true;

            offset = (int) resource.getOffset();
            fm.seek(resource.getOffset());

            int length = (int) resource.getLength();
            if (length > 1000) {
              length = 1000;
            }
            int checkLength = length / 2;
            byte[] origBytes = fm.readBytes(length);

            int numKeys = keys.length;
            for (int k = 0; k < numKeys; k++) {
              if (validKey == null) {

                try {
                  // try each key
                  byte[] decryptedBytes = new byte[length];
                  int keyPos = 0;
                  for (int b = 0; b < length; b++) {
                    decryptedBytes[b] = (byte) (origBytes[b] ^ keys[k][keyPos]);
                    keyPos++;
                    if (keyPos >= 6) {
                      keyPos = 0;
                    }
                  }

                  FileManipulator checkFM = new FileManipulator(new ByteBuffer(decryptedBytes));

                  Exporter_ZLib exporterChecker = Exporter_ZLib.getInstance();
                  exporterChecker.open(checkFM, length, length);

                  int bytesDecompressed = 0;
                  for (int b = 0; b < checkLength; b++) {
                    if (exporterChecker.available()) {
                      exporterChecker.read(); // just discard it, we don't want it 
                      bytesDecompressed++;
                    }
                    else {
                      break;
                    }
                  }

                  checkFM.close();

                  if (bytesDecompressed > 10) {
                    // OK
                    validKey = keys[k];
                  }
                }
                catch (Throwable t) {
                  // Bad key
                  ErrorLogger.log(t);
                }
              }
            }

            /*
            if (validKey == null) {
              
              try {
                // try key 1
                byte[] decryptedBytes = new byte[length];
                int keyPos = 0;
                for (int b = 0; b < length; b++) {
                  decryptedBytes[b] = (byte) (origBytes[b] ^ key1[keyPos]);
                  keyPos++;
                  if (keyPos >= 6) {
                    keyPos = 0;
                  }
                }
            
                FileManipulator checkFM = new FileManipulator(new ByteBuffer(decryptedBytes));
            
                Exporter_ZLib exporterChecker = Exporter_ZLib.getInstance();
                exporterChecker.open(checkFM, length, length);
            
                int bytesDecompressed = 0;
                for (int b = 0; b < checkLength; b++) {
                  if (exporterChecker.available()) {
                    exporterChecker.read(); // just discard it, we don't want it 
                    bytesDecompressed++;
                  }
                  else {
                    break;
                  }
                }
            
                checkFM.close();
            
                if (bytesDecompressed > 10) {
                  // OK
                  validKey = key1;
                }
              }
              catch (Throwable t) {
                // Bad key
                ErrorLogger.log(t);
              }
            }
            
            if (validKey == null) {
              try {
                // try key 2
                byte[] decryptedBytes = new byte[length];
                int keyPos = 0;
                for (int b = 0; b < length; b++) {
                  decryptedBytes[b] = (byte) (origBytes[b] ^ key2[keyPos]);
                  keyPos++;
                  if (keyPos >= 6) {
                    keyPos = 0;
                  }
                }
            
                FileManipulator checkFM = new FileManipulator(new ByteBuffer(decryptedBytes));
            
                Exporter_ZLib exporterChecker = Exporter_ZLib.getInstance();
                exporterChecker.open(checkFM, length, length);
            
                int bytesDecompressed = 0;
                for (int b = 0; b < checkLength; b++) {
                  if (exporterChecker.available()) {
                    exporterChecker.read(); // just discard it, we don't want it 
                    bytesDecompressed++;
                  }
                  else {
                    break;
                  }
                }
            
                checkFM.close();
            
                if (bytesDecompressed > 10) {
                  // OK
                  validKey = key2;
                }
              }
              catch (Throwable t) {
                // Bad key
                ErrorLogger.log(t);
              }
            }
            
            if (validKey == null) {
              try {
                // try key 3
                byte[] decryptedBytes = new byte[length];
                int keyPos = 0;
                for (int b = 0; b < length; b++) {
                  decryptedBytes[b] = (byte) (origBytes[b] ^ key3[keyPos]);
                  keyPos++;
                  if (keyPos >= 6) {
                    keyPos = 0;
                  }
                }
            
                FileManipulator checkFM = new FileManipulator(new ByteBuffer(decryptedBytes));
            
                Exporter_ZLib exporterChecker = Exporter_ZLib.getInstance();
                exporterChecker.open(checkFM, length, length);
            
                int bytesDecompressed = 0;
                for (int b = 0; b < checkLength; b++) {
                  if (exporterChecker.available()) {
                    exporterChecker.read(); // just discard it, we don't want it 
                    bytesDecompressed++;
                  }
                  else {
                    break;
                  }
                }
            
                checkFM.close();
            
                if (bytesDecompressed > 10) {
                  // OK
                  validKey = key3;
                }
              }
              catch (Throwable t) {
                // Bad key
                ErrorLogger.log(t);
              }
            }
            
            if (validKey == null) {
              try {
                // try key 4
                byte[] decryptedBytes = new byte[length];
                int keyPos = 0;
                for (int b = 0; b < length; b++) {
                  decryptedBytes[b] = (byte) (origBytes[b] ^ key4[keyPos]);
                  keyPos++;
                  if (keyPos >= 6) {
                    keyPos = 0;
                  }
                }
            
                FileManipulator checkFM = new FileManipulator(new ByteBuffer(decryptedBytes));
            
                Exporter_ZLib exporterChecker = Exporter_ZLib.getInstance();
                exporterChecker.open(checkFM, length, length);
            
                int bytesDecompressed = 0;
                for (int b = 0; b < checkLength; b++) {
                  if (exporterChecker.available()) {
                    exporterChecker.read(); // just discard it, we don't want it 
                    bytesDecompressed++;
                  }
                  else {
                    break;
                  }
                }
            
                checkFM.close();
            
                if (bytesDecompressed > 10) {
                  // OK
                  validKey = key4;
                }
              }
              catch (Throwable t) {
                // Bad key
                ErrorLogger.log(t);
              }
            }
            */

            if (validKey != null) {
              // found a valid key, make an exporter for it
              encryptionPlugin = new Exporter_ZLib_XOR_RepeatingKey(validKey);
            }

          }

          if (encryptionPlugin != null) {
            resource.setExporter(encryptionPlugin);
          }
          //*/
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
