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

package org.watto.ge.plugin.exporter;

import org.watto.datatype.Resource;
import org.watto.ge.plugin.ExporterPlugin;
import org.watto.io.FileManipulator;
import org.watto.io.converter.ByteConverter;

/**
**********************************************************************************************
Exports a file, where each byte has a number subtracted from it
**********************************************************************************************
**/
public class Exporter_Subtract extends ExporterPlugin {

  static Exporter_Subtract instance = new Exporter_Subtract();

  static FileManipulator readSource;

  static long readLength = 0;

  /**
  **********************************************************************************************
  
  **********************************************************************************************
  **/
  public static Exporter_Subtract getInstance() {
    return instance;
  }

  int subtractValue = 0;

  /**
  **********************************************************************************************
  
  **********************************************************************************************
  **/
  public Exporter_Subtract() {
    setName("XOR Encrypted File");
  }

  /**
  **********************************************************************************************
  
  **********************************************************************************************
  **/
  public Exporter_Subtract(int subtractValue) {
    super();
    setName("XOR Encrypted File");
    this.subtractValue = subtractValue;
  }

  /**
  **********************************************************************************************
  
  **********************************************************************************************
  **/
  @Override
  public boolean available() {
    return readLength > 0;
  }

  /**
  **********************************************************************************************
  
  **********************************************************************************************
  **/
  @Override
  public void close() {
    try {
      readSource.close();
      readSource = null;
    }
    catch (Throwable t) {
      readSource = null;
    }
  }

  /**
  **********************************************************************************************
  
  **********************************************************************************************
  **/
  @Override
  public void open(Resource source) {
    try {
      readLength = source.getLength();

      // try to get the whole file in a single go, if it isn't too large (set to 200KB otherwise)
      int bufferSize = (int) readLength;
      if (bufferSize > 204800) {
        bufferSize = 204800;
      }

      readSource = new FileManipulator(source.getSource(), false, bufferSize);
      readSource.seek(source.getOffset());

    }
    catch (Throwable t) {
    }
  }

  /**
  **********************************************************************************************
  If read() is a subtract, then pack() is an addition
  **********************************************************************************************
  **/
  @Override
  public void pack(Resource source, FileManipulator destination) {
    try {

      ExporterPlugin exporter = source.getExporter();
      exporter.open(source);

      while (exporter.available()) {

        int currentByte = exporter.read();
        currentByte += subtractValue;
        if (currentByte > 255) {
          currentByte -= 255;
        }

        destination.writeByte(currentByte);
      }

      exporter.close();

      //destination.forceWrite();

    }
    catch (Throwable t) {
      logError(t);
    }
  }

  /**
  **********************************************************************************************
  
  **********************************************************************************************
  **/
  @Override
  public int read() {
    try {
      readLength--;

      int currentByte = ByteConverter.unsign(readSource.readByte());
      currentByte -= subtractValue;
      if (currentByte < 0) {
        currentByte = 256 + currentByte;
      }

      return (byte) currentByte;

    }
    catch (Throwable t) {
      return 0;
    }
  }

}