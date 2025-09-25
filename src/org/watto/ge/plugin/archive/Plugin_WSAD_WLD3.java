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
import java.util.Arrays;

import org.watto.ErrorLogger;
import org.watto.Language;
import org.watto.datatype.Resource;
import org.watto.ge.helper.FieldValidator;
import org.watto.ge.plugin.ArchivePlugin;
import org.watto.io.FileManipulator;
import org.watto.io.FilenameSplitter;
import org.watto.io.converter.ByteConverter;
import org.watto.io.converter.IntConverter;
import org.watto.io.converter.StringConverter;
import org.watto.task.TaskProgressManager;

/**
**********************************************************************************************
Ref: https://github.com/diamondman/WTExtractor/blob/master/pywttools/wtextract.py
**********************************************************************************************
**/
public class Plugin_WSAD_WLD3 extends ArchivePlugin {

  /**
  **********************************************************************************************
  
  **********************************************************************************************
  **/
  public Plugin_WSAD_WLD3() {

    super("WSAD_WLD3", "WildTangent");

    //         read write replace rename
    setProperties(true, false, false, false);

    setGames("Shrek 2: Ogre Bowler");
    setExtensions("wsad"); // MUST BE LOWER CASE
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
      if (fm.readString(4).equals("WLD3")) {
        rating += 50;
      }

      fm.skip(5);

      if (fm.readString(42).equals("WildTangent 3D 300 Compressed and Patented")) {
        rating += 5;
      }

      fm.skip(2);

      if (fm.readString(18).equals("Converted by XtoWT")) {
        rating += 5;
      }

      return rating;

    }
    catch (Throwable t) {
      return 0;
    }
  }

  int largestLength = 0;
  byte[][] fields = null;

  FileManipulator fm = null;

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

      fm = new FileManipulator(path, false);

      // decrypt the archive
      FileManipulator decompFM = decryptArchive(fm);
      if (decompFM != null) {
        fm.close();
        fm = decompFM;
        fm.seek(0);

        path = fm.getFile();
      }

      // Read the decrypted CAB archive

      boolean cabFile = fm.readString(4).equals("MSCF");

      fm.close();

      Resource[] resources = null;
      if (cabFile) {
        // a Microsoft CAB archive
        setCanScanForFileTypes(false);
        resources = new Plugin_CAB_MSCF().read(path);
      }
      else if (path.length() == 0) {
        return null;
      }
      else {
        // a plain file (like a plain JPG or something)
        setCanScanForFileTypes(true);

        // path,name,offset,length,decompLength,exporter
        Resource resource = new Resource(path, Resource.generateFilename(0), 0, path.length());
        resources = new Resource[] { resource };
      }

      if (resources != null) {
        int numFiles = resources.length;
        for (int i = 0; i < numFiles; i++) {
          resources[i].forceNotAdded(true);
        }
      }

      return resources;

    }
    catch (Throwable t) {
      logError(t);
      return null;
    }
  }

  /**
   **********************************************************************************************
   Decrypts the archive
   If the decompressed file already exists, we use that, we don't re-decrypt.
   **********************************************************************************************
   **/
  @SuppressWarnings("unused")
  public FileManipulator decryptArchive(FileManipulator fm) {
    try {

      // reset globals
      _index = 0;
      _last_crypt_byte = 0;
      largestLength = 0;
      fields = null;

      // Build a new "_ge_decompressed" archive file in the current directory
      File origFile = fm.getFile();

      String pathOnly = FilenameSplitter.getDirectory(origFile);
      String filenameOnly = FilenameSplitter.getFilename(origFile);
      String extensionOnly = FilenameSplitter.getExtension(origFile);

      File decompFile = new File(pathOnly + File.separatorChar + filenameOnly + "_ge_decrypted" + "." + extensionOnly);
      if (decompFile.exists() && decompFile.length() > 0) {
        // we've already decompressed this file before - open and return it
        return new FileManipulator(decompFile, false);
      }

      FileManipulator decompFM = new FileManipulator(decompFile, true);

      // Now decompress the block into the decompressed file
      TaskProgressManager.setMessage(Language.get("Progress_DecompressingArchive")); // progress bar
      TaskProgressManager.setMaximum(1); // progress bar
      TaskProgressManager.setIndeterminate(true);

      //
      // Do the decryption
      //

      // 4 - Header (WLD3)
      // 4 - Archive Type (eg ".tmp")
      fm.skip(5);
      String baseType = fm.readString(3);

      // 65 - Header
      fm.skip(65);

      // X - Timestamp
      // 1 - New Line (10)
      fm.readLine();

      // 2 - New Line (13,10)
      // 2 - New Line (13,10)
      // 1 - Full Stop
      // 5 - Start Header (START)
      // 1 - Unknown
      fm.skip(11);

      //System.out.println(fm.getOffset());

      // 1 - Number of Fields
      int numberOfFields = revOffset(ByteConverter.unsign(fm.readByte()), 197);
      FieldValidator.checkRange(numberOfFields, 8, 9);

      int[] fieldLengths = new int[numberOfFields];
      largestLength = 0; // used in the calculation of the hash tables
      for (int i = 0; i < numberOfFields; i++) {
        int fieldLength = revOffset(ByteConverter.unsign(fm.readByte()), 57 + (13 * i));
        if (fieldLength > largestLength) {
          largestLength = fieldLength;
        }
        fieldLengths[i] = fieldLength;
      }

      //System.out.println(fm.getOffset());

      fields = new byte[numberOfFields][]; // also used in hash calculations
      for (int i = 0; i < numberOfFields; i++) {
        fields[i] = fm.readBytes(fieldLengths[i]);
      }

      //System.out.println(fm.getOffset());

      // decode_urls()
      if (numberOfFields == 9 && IntConverter.convertLittle(fields[8]) < 300) {
        // doesn't need to be decoded
      }
      else {
        // decode the URLs

        int[] media_enc_table = calc_enc_key_table_TYPEMEDIA(-1);
        String[] urls = new String[0];
        int[] urlTypes = new int[0];
        while (true) {
          int urlType = decodeByte(media_enc_table);
          int url_len = _readintcrypt(media_enc_table);
          int always300_0 = _readintcrypt(media_enc_table);
          int always300_1 = _readintcrypt(media_enc_table);
          if (url_len == 0 && urlType == 0) {
            break;
          }

          String urlbody = _read_str_crypt(media_enc_table, url_len);
          // don't actually need these, at this point in time.
          //urls[i] = urlbody;
          //urlTypes[i] = urlType;
        }

      }

      //System.out.println(fm.getOffset());

      int archiveType = -1;
      if (baseType.equals("wt") || baseType.equals("pwt")) {
        archiveType = 0;
      }
      else if (baseType.equals("png") || baseType.equals("jpg") || baseType.equals("wav") || baseType.equals("mid") || baseType.equals("rmi") || baseType.equals("cab") || baseType.equals("tmp")) {
        archiveType = 1;
      }
      else if (baseType.equals("cfg") || baseType.equals("dat") || baseType.equals("ini") || baseType.equals("txt")) {
        archiveType = 2;
      }
      else {
        ErrorLogger.log("[WSAD_WLD3] Unknown archive type: " + baseType);
        return null;
      }

      int[] enc_key_table = null;
      if (archiveType == 0) {
        enc_key_table = calc_enc_key_table_TYPEMODEL(-1);
      }
      else if (archiveType == 1) {
        enc_key_table = calc_enc_key_table_TYPEMEDIA(-1);
      }
      else if (archiveType == 2) {
        enc_key_table = calc_enc_key_table_TYPEDATA(-1);
      }

      //decode_payload(table);

      int remainingLength = (int) fm.getRemainingLength();
      //byte[] decodedBytes = new byte[remainingLength];
      for (int b = 0; b < remainingLength; b++) {
        //decodedBytes[b] = (byte) decodeByte(enc_key_table, fm.readByte());
        decompFM.writeByte((byte) decodeByte(enc_key_table, fm.readByte()));
      }

      //FileManipulator tempFM = new FileManipulator(new File("C:\\out.tmp"), true);
      //tempFM.writeBytes(decodedBytes);
      //tempFM.close();

      // Force-write out the decrypted file to write it to disk, then change the buffer to read-only.
      decompFM.close();
      decompFM = new FileManipulator(decompFile, false);

      TaskProgressManager.setMessage(Language.get("Progress_ReadingArchive")); // progress bar
      TaskProgressManager.setIndeterminate(false);

      // Return the file pointer to the beginning, and return the decompressed file
      decompFM.seek(0);
      return decompFM;
    }
    catch (Throwable t) {
      ErrorLogger.log(t);
      return null;
    }
  }

  int _index = 0;
  int _last_crypt_byte = 0;

  /**
   **********************************************************************************************
   
   **********************************************************************************************
   **/
  public int decodeByte(int[] table) {
    return decodeByte(table, fm.readByte());
  }

  /**
   **********************************************************************************************
   
   **********************************************************************************************
   **/
  public int decodeByte(int[] table, int inbyte) {
    int res = inbyte ^ _last_crypt_byte ^ table[_index % table.length];

    _index += 1;
    _last_crypt_byte = inbyte;
    return res & 0xFF;
  }

  /**
   **********************************************************************************************
   
   **********************************************************************************************
   **/
  public int _readintcrypt(int[] table) {
    byte[] bytes = new byte[] { (byte) decodeByte(table), (byte) decodeByte(table), (byte) decodeByte(table), (byte) decodeByte(table) };
    return IntConverter.convertLittle(bytes);
  }

  /**
   **********************************************************************************************
   
   **********************************************************************************************
   **/
  public String _read_str_crypt(int[] table, int cnt) {
    byte[] bytes = new byte[cnt];
    for (int b = 0; b < cnt; b++) {
      bytes[b] = (byte) decodeByte(table);
    }
    return StringConverter.convertLittle(bytes);
  }

  /**
   **********************************************************************************************
   
   **********************************************************************************************
   **/
  public int revOffset(int num, int offset) {
    int tmp = num - offset;

    if (tmp < 0) {
      return 0x100 - tmp;
    }
    else {
      return tmp;
    }
  }

  /**
   **********************************************************************************************
   
   **********************************************************************************************
   **/
  public int[] calc_enc_key_table_TYPEDATA(int enc_byte) {
    if (enc_byte == -1) {
      enc_byte = calc_hash_byte_TYPEMEDIADATA();
    }

    int[] enc_key_table = new int[largestLength];
    Arrays.fill(enc_key_table, enc_byte);

    int enc_key_tableLength = enc_key_table.length;

    for (int i = 0; i < enc_key_tableLength; i++) {
      int key = enc_key_table[i];
      int chr_index = i + 7;
      for (int f = 0; f < fields.length; f++) {
        byte[] field = fields[f];

        if (field.length >= 1) {
          key ^= field[chr_index % field.length];
        }
        else {
          key ^= 0;
        }
        chr_index += 13;
      }
      enc_key_table[i] = key & 0xFF;
    }
    return enc_key_table;
  }

  /**
   **********************************************************************************************
   
   **********************************************************************************************
   **/
  public int[] calc_enc_key_table_TYPEMODEL(int enc_byte) {
    if (enc_byte == -1) {
      enc_byte = calc_hash_byte_TYPEMODEL();
    }

    int[] enc_key_table = new int[largestLength];
    Arrays.fill(enc_key_table, enc_byte);

    int enc_key_tableLength = enc_key_table.length;

    for (int i = 0; i < enc_key_tableLength; i++) {
      int key = enc_key_table[i];
      for (int f = 0; f < fields.length; f++) {
        byte[] field = fields[f];

        if (field.length >= 1) {
          key ^= field[i % field.length];
        }
        else {
          key ^= 0;
        }
      }

      enc_key_table[i] = key & 0xFF;
    }
    return enc_key_table;
  }

  /**
   **********************************************************************************************
   
   **********************************************************************************************
   **/
  public int[] calc_enc_key_table_TYPEMEDIA(int enc_byte) {

    if (enc_byte == -1) {
      enc_byte = calc_hash_byte_TYPEMEDIADATA();
    }

    int[] enc_key_table = new int[largestLength];
    Arrays.fill(enc_key_table, enc_byte);

    int enc_key_tableLength = enc_key_table.length;

    for (int i = 0; i < enc_key_tableLength; i++) {
      int key = enc_key_table[i];
      for (int f = 0; f < fields.length; f++) {
        byte[] field = fields[f];

        if (field.length >= 1) {
          key ^= field[(f + i) % field.length];
        }
        else {
          key ^= 0;
        }
      }

      enc_key_table[i] = key & 0xFF;
    }
    return enc_key_table;
  }

  /**
   **********************************************************************************************
   
   **********************************************************************************************
   **/
  public int calc_hash_byte_TYPEMEDIADATA() {
    int enc_byte = 0;
    for (int i = 0; i < fields.length; i++) {
      byte[] data = fields[i];

      if (largestLength > 0) {
        for (int j = 0; j < largestLength; j++) {
          int c;
          if (data.length >= 1) {
            c = data[j % data.length];
          }
          else {
            c = 0;
          }
          enc_byte ^= i + j + c * (j + 1);
        }
      }
    }
    return enc_byte & 0xFF;
  }

  /**
   **********************************************************************************************
   
   **********************************************************************************************
   **/
  public int calc_hash_byte_TYPEMODEL() {
    int enc_byte = 0;

    for (int i = 0; i < fields.length; i++) {
      byte[] data = fields[i];

      if (largestLength > 0) {
        for (int j = 0; j < largestLength; j++) {
          int c = 0;
          if (data.length >= 1) {
            c = data[j % data.length];
          }
          else {
            c = 0;
          }
          enc_byte ^= c;
        }
      }
    }
    return enc_byte & 0xFF;
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
