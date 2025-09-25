/*
 * Application:  Game Extractor
 * Author:       wattostudios
 * Website:      http://www.watto.org
 * Copyright:    Copyright (c) 2002-2020 wattostudios
 *
 * License Information:
 * This program is free software; you can redistribute it and/or modify it under the terms of the GNU General Public License
 * published by the Free Software Foundation; either version 2 of the License, or (at your option) any later versions. This
 * program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranties
 * of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License at http://www.gnu.org for more
 * details. For further information on this application, refer to the authors' website.
 */

package org.watto.ge.plugin.exporter;

import org.watto.ErrorLogger;
import org.watto.datatype.Resource;
import org.watto.ge.plugin.ExporterPlugin;
import org.watto.io.FileManipulator;
import org.watto.io.buffer.ByteBuffer;

public class Exporter_Custom_RFD_2 extends ExporterPlugin {

  static Exporter_Custom_RFD_2 instance = new Exporter_Custom_RFD_2();

  /**
  **********************************************************************************************
  Ref: https://gist.github.com/shinyquagsire23/20a86e65206ece28683a9b4c5597e172
  **********************************************************************************************
  **/
  public static Exporter_Custom_RFD_2 getInstance() {
    return instance;
  }

  int[] buffer = new int[0];
  int bufferLength = 0;
  int bufferPos = 0;

  /**
  **********************************************************************************************
  
  **********************************************************************************************
  **/
  public Exporter_Custom_RFD_2() {
    setName("Lego Loco Compression");
  }

  /**
  **********************************************************************************************
  
  **********************************************************************************************
  **/
  @Override
  public boolean available() {
    return bufferPos < bufferLength;
  }

  /**
  **********************************************************************************************
  
  **********************************************************************************************
  **/
  public void decompressData(byte[] compBytes) {
    try {

      FileManipulator fm = new FileManipulator(new ByteBuffer(compBytes));

      int decompLength = fm.readInt();
      int[] decompBytes = new int[decompLength];

      fm.seek(4);
      short node = fm.readShort();

      fm.seek(2056);

      int compLength = compBytes.length;

      int outPos = 0;
      for (int b = 2056; b < compLength; b++) {
        byte bits = compBytes[b];

        for (int i = 0; i < 8; i++) {
          int bit = bits & 1;
          bits = (byte) (bits >> 1);

          int nodePos = (node * 4) + (bit * 2) + 8;

          fm.seek(nodePos);
          node = fm.readShort();

          boolean isTerminal = (node & 0x100) == 0;
          if (isTerminal) {
            decompBytes[outPos] = node;
            outPos++;

            if (outPos >= decompLength) {
              break; // end of compression, don't read some of the stuff after it.
            }

            fm.seek(4);
            node = fm.readShort();

          }
        }

        if (outPos >= decompLength) {
          break; // end of compression, don't read some of the stuff after it.
        }
      }

      buffer = decompBytes;
      bufferLength = outPos;
      bufferPos = 0;

      //System.out.println(outPos + " vs " + (decompLength-1024));

    }
    catch (Throwable t) {
      ErrorLogger.log(t);

      bufferLength = 0;
      bufferPos = 0;
    }
  }

  /**
  **********************************************************************************************
  
  **********************************************************************************************
  **/
  @Override
  public void close() {
    buffer = null;
  }

  /**
  **********************************************************************************************
  Closes and Re-opens the resource from the beginning. Here in case we want to keep decompressed
  buffers for the next run instead of deleting them and re-decompressing every time, for example.
  Used mainly in ExporterByteBuffer to roll back to the beginning of the file.
  **********************************************************************************************
  **/
  public void closeAndReopen(Resource source) {
    bufferPos = 0;
  }

  /**
  **********************************************************************************************
  
  **********************************************************************************************
  **/
  @Override
  public void open(Resource source) {
    try {

      int readLength = (int) source.getLength();

      FileManipulator fm = new FileManipulator(source.getSource(), false);
      fm.seek(source.getOffset());

      byte[] compBytes = fm.readBytes((int) readLength);

      decompressData(compBytes);

    }
    catch (Throwable t) {
    }
  }

  /**
  **********************************************************************************************
  NOT IMPLEMENTED
  **********************************************************************************************
  **/
  @Override
  public void pack(Resource source, FileManipulator destination) {
    // NOT IMPLEMENTED
  }

  /**
  **********************************************************************************************
  
  **********************************************************************************************
  **/
  @Override
  public int read() {
    try {
      int readByte = buffer[bufferPos];
      bufferPos++;
      return readByte;
    }
    catch (Throwable t) {
      return 0;
    }
  }

}