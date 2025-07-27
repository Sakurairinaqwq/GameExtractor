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

package org.watto.ge.helper;

import org.watto.io.converter.ByteArrayConverter;
import org.watto.io.converter.ByteConverter;
import org.watto.io.converter.FloatConverter;

/**
**********************************************************************************************
BC6H/BC6F Decoder
Ref: https://github.com/iOrange/bcdec/blob/main/bcdec.h
// NOT WORKING YET
**********************************************************************************************
**/
public class BC6Reader {

  int[] bstream = new int[0];
  int bstreamByte = 0;
  int bstreamBitsRead = 0;

  /**
  **********************************************************************************************
  
  **********************************************************************************************
  **/
  int bcdec__bitstream_read_bits(int numBits) {

    //System.out.println("Reading bits: " + numBits + " from byte " + bstreamByte + " at pos " + bstreamBitsRead);

    int output = 0;
    int outputPos = 0;

    int bstreamBitsRemaining = 8 - bstreamBitsRead;
    while (numBits > bstreamBitsRemaining) {
      int mask = ((1 << bstreamBitsRemaining) - 1);
      output |= (bstream[bstreamByte] & mask) << outputPos;

      // we've read bstreamBitsRemaining bits, so make sure we account for it.
      outputPos += bstreamBitsRemaining;
      numBits -= bstreamBitsRemaining;

      // move to the next byte in the stream
      bstreamByte++;
      bstreamBitsRead = 0;

      // prepare for the next loop
      bstreamBitsRemaining = 8 - bstreamBitsRead;
    }

    // now we have at most 8 bits to return
    bstreamBitsRemaining = numBits;

    // read as above
    int mask = ((1 << bstreamBitsRemaining) - 1);
    output |= (bstream[bstreamByte] & mask) << outputPos;

    // we still have remaining data in this byte, but just keep track of where we're up to, and move the value accordingly
    bstream[bstreamByte] >>= numBits;
    bstreamBitsRead += numBits;

    // shouldn't get here, but just in case
    while (bstreamBitsRead >= 8) {
      bstreamBitsRead -= 8;
      bstreamByte++;
    }

    return output;
  }

  /**
  **********************************************************************************************
  
  **********************************************************************************************
  **/
  int bcdec__bitstream_read_bit() {
    return bcdec__bitstream_read_bits(1);
  }

  /**
  **********************************************************************************************
  
  **********************************************************************************************
  **/
  int bcdec__bitstream_read_bits_r(int numBits) {
    int bits = bcdec__bitstream_read_bits(numBits);
    /* Reverse the bits. */
    int result = 0;
    while (numBits-- > 0) {
      result <<= 1;
      result |= (bits & 1);
      bits >>= 1;
    }
    return result;
  }

  /**
  **********************************************************************************************
  
  **********************************************************************************************
  **/
  int bcdec__extend_sign(int val, int bits) {
    return (val << (32 - bits)) >> (32 - bits);
  }

  /**
  **********************************************************************************************
  
  **********************************************************************************************
  **/
  int bcdec__transform_inverse(int val, int a0, int bits, boolean isSigned) {
    /* If the precision of A0 is "p" bits, then the transform algorithm is:
       B0 = (B0 + A0) & ((1 << p) - 1) */
    val = (val + a0) & ((1 << bits) - 1);
    if (isSigned) {
      val = bcdec__extend_sign(val, bits);
    }
    return val;
  }

  /**
  **********************************************************************************************
  
  **********************************************************************************************
  **/
  int bcdec__unquantize(int val, int bits, boolean isSigned) {
    int unq, s = 0;

    if (!isSigned) {
      if (bits >= 15) {
        unq = val;
      }
      else if (val == 0) {
        unq = 0;
      }
      else if (val == ((1 << bits) - 1)) {
        unq = 0xFFFF;
      }
      else {
        unq = ((val << 16) + 0x8000) >> bits;
      }
    }
    else {
      if (bits >= 16) {
        unq = val;
      }
      else {
        if (val < 0) {
          s = 1;
          val = -val;
        }

        if (val == 0) {
          unq = 0;
        }
        else if (val >= ((1 << (bits - 1)) - 1)) {
          unq = 0x7FFF;
        }
        else {
          unq = ((val << 15) + 0x4000) >> (bits - 1);
        }

        if (s != 0) {
          unq = -unq;
        }
      }
    }
    return unq;
  }

  /**
  **********************************************************************************************
  
  **********************************************************************************************
  **/
  int bcdec__interpolate(int a, int b, int[] weights, int index) {
    return (a * (64 - weights[index]) + b * weights[index] + 32) >> 6;
  }

  /**
  **********************************************************************************************
  
  **********************************************************************************************
  **/
  int bcdec__finish_unquantize(int val, boolean isSigned) {
    int s;

    if (!isSigned) {
      return /*(unsigned short)*/((val * 31) >> 6); /* scale the magnitude by 31 / 64 */
    }
    else {
      val = (val < 0) ? -(((-val) * 31) >> 5) : (val * 31) >> 5; /* scale the magnitude by 31 / 32 */
      s = 0;
      if (val < 0) {
        s = 0x8000;
        val = -val;
      }
      return /*(unsigned short)*/(s | val);
    }
  }

  /**
  **********************************************************************************************
  
  **********************************************************************************************
  **/
  public int[] bcdec_bc6h_half(byte[] compressedBlock, boolean isSigned) {

    int[] decompressedBlock = new int[16];

    int[][] actual_bits_count = new int[][] { // 4,14
        { 10, 7, 11, 11, 11, 9, 8, 8, 8, 6, 10, 11, 12, 16 }, /*  W */
        { 5, 6, 5, 4, 4, 5, 6, 5, 5, 6, 10, 9, 8, 4 }, /* dR */
        { 5, 6, 4, 5, 4, 5, 5, 6, 5, 6, 10, 9, 8, 4 }, /* dG */
        { 5, 6, 4, 4, 5, 5, 5, 5, 6, 6, 10, 9, 8, 4 } /* dB */
    };

    /* There are 32 possible partition sets for a two-region tile.
       Each 4x4 block represents a single shape.
       Here also every fix-up index has MSB bit set. */
    int[][][] partition_sets = new int[][][] { // 32,4,4
        { { 128, 0, 1, 1 }, { 0, 0, 1, 1 }, { 0, 0, 1, 1 }, { 0, 0, 1, 129 } }, /*  0 */
        { { 128, 0, 0, 1 }, { 0, 0, 0, 1 }, { 0, 0, 0, 1 }, { 0, 0, 0, 129 } }, /*  1 */
        { { 128, 1, 1, 1 }, { 0, 1, 1, 1 }, { 0, 1, 1, 1 }, { 0, 1, 1, 129 } }, /*  2 */
        { { 128, 0, 0, 1 }, { 0, 0, 1, 1 }, { 0, 0, 1, 1 }, { 0, 1, 1, 129 } }, /*  3 */
        { { 128, 0, 0, 0 }, { 0, 0, 0, 1 }, { 0, 0, 0, 1 }, { 0, 0, 1, 129 } }, /*  4 */
        { { 128, 0, 1, 1 }, { 0, 1, 1, 1 }, { 0, 1, 1, 1 }, { 1, 1, 1, 129 } }, /*  5 */
        { { 128, 0, 0, 1 }, { 0, 0, 1, 1 }, { 0, 1, 1, 1 }, { 1, 1, 1, 129 } }, /*  6 */
        { { 128, 0, 0, 0 }, { 0, 0, 0, 1 }, { 0, 0, 1, 1 }, { 0, 1, 1, 129 } }, /*  7 */
        { { 128, 0, 0, 0 }, { 0, 0, 0, 0 }, { 0, 0, 0, 1 }, { 0, 0, 1, 129 } }, /*  8 */
        { { 128, 0, 1, 1 }, { 0, 1, 1, 1 }, { 1, 1, 1, 1 }, { 1, 1, 1, 129 } }, /*  9 */
        { { 128, 0, 0, 0 }, { 0, 0, 0, 1 }, { 0, 1, 1, 1 }, { 1, 1, 1, 129 } }, /* 10 */
        { { 128, 0, 0, 0 }, { 0, 0, 0, 0 }, { 0, 0, 0, 1 }, { 0, 1, 1, 129 } }, /* 11 */
        { { 128, 0, 0, 1 }, { 0, 1, 1, 1 }, { 1, 1, 1, 1 }, { 1, 1, 1, 129 } }, /* 12 */
        { { 128, 0, 0, 0 }, { 0, 0, 0, 0 }, { 1, 1, 1, 1 }, { 1, 1, 1, 129 } }, /* 13 */
        { { 128, 0, 0, 0 }, { 1, 1, 1, 1 }, { 1, 1, 1, 1 }, { 1, 1, 1, 129 } }, /* 14 */
        { { 128, 0, 0, 0 }, { 0, 0, 0, 0 }, { 0, 0, 0, 0 }, { 1, 1, 1, 129 } }, /* 15 */
        { { 128, 0, 0, 0 }, { 1, 0, 0, 0 }, { 1, 1, 1, 0 }, { 1, 1, 1, 129 } }, /* 16 */
        { { 128, 1, 129, 1 }, { 0, 0, 0, 1 }, { 0, 0, 0, 0 }, { 0, 0, 0, 0 } }, /* 17 */
        { { 128, 0, 0, 0 }, { 0, 0, 0, 0 }, { 129, 0, 0, 0 }, { 1, 1, 1, 0 } }, /* 18 */
        { { 128, 1, 129, 1 }, { 0, 0, 1, 1 }, { 0, 0, 0, 1 }, { 0, 0, 0, 0 } }, /* 19 */
        { { 128, 0, 129, 1 }, { 0, 0, 0, 1 }, { 0, 0, 0, 0 }, { 0, 0, 0, 0 } }, /* 20 */
        { { 128, 0, 0, 0 }, { 1, 0, 0, 0 }, { 129, 1, 0, 0 }, { 1, 1, 1, 0 } }, /* 21 */
        { { 128, 0, 0, 0 }, { 0, 0, 0, 0 }, { 129, 0, 0, 0 }, { 1, 1, 0, 0 } }, /* 22 */
        { { 128, 1, 1, 1 }, { 0, 0, 1, 1 }, { 0, 0, 1, 1 }, { 0, 0, 0, 129 } }, /* 23 */
        { { 128, 0, 129, 1 }, { 0, 0, 0, 1 }, { 0, 0, 0, 1 }, { 0, 0, 0, 0 } }, /* 24 */
        { { 128, 0, 0, 0 }, { 1, 0, 0, 0 }, { 129, 0, 0, 0 }, { 1, 1, 0, 0 } }, /* 25 */
        { { 128, 1, 129, 0 }, { 0, 1, 1, 0 }, { 0, 1, 1, 0 }, { 0, 1, 1, 0 } }, /* 26 */
        { { 128, 0, 129, 1 }, { 0, 1, 1, 0 }, { 0, 1, 1, 0 }, { 1, 1, 0, 0 } }, /* 27 */
        { { 128, 0, 0, 1 }, { 0, 1, 1, 1 }, { 129, 1, 1, 0 }, { 1, 0, 0, 0 } }, /* 28 */
        { { 128, 0, 0, 0 }, { 1, 1, 1, 1 }, { 129, 1, 1, 1 }, { 0, 0, 0, 0 } }, /* 29 */
        { { 128, 1, 129, 1 }, { 0, 0, 0, 1 }, { 1, 0, 0, 0 }, { 1, 1, 1, 0 } }, /* 30 */
        { { 128, 0, 129, 1 }, { 1, 0, 0, 1 }, { 1, 0, 0, 1 }, { 1, 1, 0, 0 } } /* 31 */
    };

    int[] aWeight3 = new int[] { 0, 9, 18, 27, 37, 46, 55, 64 };
    int[] aWeight4 = new int[] { 0, 4, 9, 13, 17, 21, 26, 30, 34, 38, 43, 47, 51, 55, 60, 64 };

    int mode, partition, numPartitions, i, j, partitionSet, indexBits, index, ep_i, actualBits0Mode;
    int[] r = new int[4];
    int[] g = new int[4];
    int[] b = new int[4]; /* wxyz */
    int[] weights;

    //long bstreamLow = LongConverter.convertLittle(new byte[] {(byte)compressedBlock[0],(byte)compressedBlock[1],(byte)compressedBlock[2],(byte)compressedBlock[3],(byte)compressedBlock[4],(byte)compressedBlock[5],(byte)compressedBlock[6],(byte)compressedBlock[7]});//bstream.low = ((unsigned long long*)compressedBlock)[0];
    //long bstreamHigh = LongConverter.convertLittle(new byte[] {(byte)compressedBlock[8],(byte)compressedBlock[9],(byte)compressedBlock[10],(byte)compressedBlock[11],(byte)compressedBlock[12],(byte)compressedBlock[13],(byte)compressedBlock[14],(byte)compressedBlock[15]});//bstream.high = ((unsigned long long*)compressedBlock)[1];

    bstream = new int[16];
    /*
    bstream[0] = ByteConverter.unsign(compressedBlock[7]);
    bstream[1] = ByteConverter.unsign(compressedBlock[6]);
    bstream[2] = ByteConverter.unsign(compressedBlock[5]);
    bstream[3] = ByteConverter.unsign(compressedBlock[4]);
    bstream[4] = ByteConverter.unsign(compressedBlock[3]);
    bstream[5] = ByteConverter.unsign(compressedBlock[2]);
    bstream[6] = ByteConverter.unsign(compressedBlock[1]);
    bstream[7] = ByteConverter.unsign(compressedBlock[0]);
    
    bstream[8] = ByteConverter.unsign(compressedBlock[15]);
    bstream[9] = ByteConverter.unsign(compressedBlock[14]);
    bstream[10] = ByteConverter.unsign(compressedBlock[13]);
    bstream[11] = ByteConverter.unsign(compressedBlock[12]);
    bstream[12] = ByteConverter.unsign(compressedBlock[11]);
    bstream[13] = ByteConverter.unsign(compressedBlock[10]);
    bstream[14] = ByteConverter.unsign(compressedBlock[9]);
    bstream[15] = ByteConverter.unsign(compressedBlock[8]);
    */

    ///*
    for (int pos = 0; pos < 16; pos++) {
      bstream[pos] = ByteConverter.unsign(compressedBlock[pos]);
    }
    //*/

    /*
    for (int pos = 0; pos < 16; pos++) {
      bstream[pos] = ByteConverter.unsign(compressedBlock[15 - pos]);
    }
    */

    bstreamByte = 0;
    bstreamBitsRead = 0;

    r[0] = r[1] = r[2] = r[3] = 0;
    g[0] = g[1] = g[2] = g[3] = 0;
    b[0] = b[1] = b[2] = b[3] = 0;

    mode = bcdec__bitstream_read_bits(2);
    if (mode > 1) {
      mode |= (bcdec__bitstream_read_bits(3) << 2);
    }

    /* modes >= 11 (10 in my code) are using 0 one, others will read it from the bitstream */
    partition = 0;

    switch (mode) {
    /* mode 1 */
    case 0b00:
    {
      /* Partitition indices: 46 bits
         Partition: 5 bits
         Color Endpoints: 75 bits (10.555, 10.555, 10.555) */
      g[2] |= bcdec__bitstream_read_bit() << 4; /* gy[4]   */
      b[2] |= bcdec__bitstream_read_bit() << 4; /* by[4]   */
      b[3] |= bcdec__bitstream_read_bit() << 4; /* bz[4]   */
      r[0] |= bcdec__bitstream_read_bits(10); /* rw[9:0] */
      g[0] |= bcdec__bitstream_read_bits(10); /* gw[9:0] */
      b[0] |= bcdec__bitstream_read_bits(10); /* bw[9:0] */
      r[1] |= bcdec__bitstream_read_bits(5); /* rx[4:0] */
      g[3] |= bcdec__bitstream_read_bit() << 4; /* gz[4]   */
      g[2] |= bcdec__bitstream_read_bits(4); /* gy[3:0] */
      g[1] |= bcdec__bitstream_read_bits(5); /* gx[4:0] */
      b[3] |= bcdec__bitstream_read_bit(); /* bz[0]   */
      g[3] |= bcdec__bitstream_read_bits(4); /* gz[3:0] */
      b[1] |= bcdec__bitstream_read_bits(5); /* bx[4:0] */
      b[3] |= bcdec__bitstream_read_bit() << 1; /* bz[1]   */
      b[2] |= bcdec__bitstream_read_bits(4); /* by[3:0] */
      r[2] |= bcdec__bitstream_read_bits(5); /* ry[4:0] */
      b[3] |= bcdec__bitstream_read_bit() << 2; /* bz[2]   */
      r[3] |= bcdec__bitstream_read_bits(5); /* rz[4:0] */
      b[3] |= bcdec__bitstream_read_bit() << 3; /* bz[3]   */
      partition = bcdec__bitstream_read_bits(5); /* d[4:0]  */
      mode = 0;
    }
      break;

    /* mode 2 */
    case 0b01:
    {
      /* Partitition indices: 46 bits
         Partition: 5 bits
         Color Endpoints: 75 bits (7666, 7666, 7666) */
      g[2] |= bcdec__bitstream_read_bit() << 5; /* gy[5]   */
      g[3] |= bcdec__bitstream_read_bit() << 4; /* gz[4]   */
      g[3] |= bcdec__bitstream_read_bit() << 5; /* gz[5]   */
      r[0] |= bcdec__bitstream_read_bits(7); /* rw[6:0] */
      b[3] |= bcdec__bitstream_read_bit(); /* bz[0]   */
      b[3] |= bcdec__bitstream_read_bit() << 1; /* bz[1]   */
      b[2] |= bcdec__bitstream_read_bit() << 4; /* by[4]   */
      g[0] |= bcdec__bitstream_read_bits(7); /* gw[6:0] */
      b[2] |= bcdec__bitstream_read_bit() << 5; /* by[5]   */
      b[3] |= bcdec__bitstream_read_bit() << 2; /* bz[2]   */
      g[2] |= bcdec__bitstream_read_bit() << 4; /* gy[4]   */
      b[0] |= bcdec__bitstream_read_bits(7); /* bw[6:0] */
      b[3] |= bcdec__bitstream_read_bit() << 3; /* bz[3]   */
      b[3] |= bcdec__bitstream_read_bit() << 5; /* bz[5]   */
      b[3] |= bcdec__bitstream_read_bit() << 4; /* bz[4]   */
      r[1] |= bcdec__bitstream_read_bits(6); /* rx[5:0] */
      g[2] |= bcdec__bitstream_read_bits(4); /* gy[3:0] */
      g[1] |= bcdec__bitstream_read_bits(6); /* gx[5:0] */
      g[3] |= bcdec__bitstream_read_bits(4); /* gz[3:0] */
      b[1] |= bcdec__bitstream_read_bits(6); /* bx[5:0] */
      b[2] |= bcdec__bitstream_read_bits(4); /* by[3:0] */
      r[2] |= bcdec__bitstream_read_bits(6); /* ry[5:0] */
      r[3] |= bcdec__bitstream_read_bits(6); /* rz[5:0] */
      partition = bcdec__bitstream_read_bits(5); /* d[4:0]  */
      mode = 1;
    }
      break;

    /* mode 3 */
    case 0b00010:
    {
      /* Partitition indices: 46 bits
         Partition: 5 bits
         Color Endpoints: 72 bits (11.555, 11.444, 11.444) */
      r[0] |= bcdec__bitstream_read_bits(10); /* rw[9:0] */
      g[0] |= bcdec__bitstream_read_bits(10); /* gw[9:0] */
      b[0] |= bcdec__bitstream_read_bits(10); /* bw[9:0] */
      r[1] |= bcdec__bitstream_read_bits(5); /* rx[4:0] */
      r[0] |= bcdec__bitstream_read_bit() << 10; /* rw[10]  */
      g[2] |= bcdec__bitstream_read_bits(4); /* gy[3:0] */
      g[1] |= bcdec__bitstream_read_bits(4); /* gx[3:0] */
      g[0] |= bcdec__bitstream_read_bit() << 10; /* gw[10]  */
      b[3] |= bcdec__bitstream_read_bit(); /* bz[0]   */
      g[3] |= bcdec__bitstream_read_bits(4); /* gz[3:0] */
      b[1] |= bcdec__bitstream_read_bits(4); /* bx[3:0] */
      b[0] |= bcdec__bitstream_read_bit() << 10; /* bw[10]  */
      b[3] |= bcdec__bitstream_read_bit() << 1; /* bz[1]   */
      b[2] |= bcdec__bitstream_read_bits(4); /* by[3:0] */
      r[2] |= bcdec__bitstream_read_bits(5); /* ry[4:0] */
      b[3] |= bcdec__bitstream_read_bit() << 2; /* bz[2]   */
      r[3] |= bcdec__bitstream_read_bits(5); /* rz[4:0] */
      b[3] |= bcdec__bitstream_read_bit() << 3; /* bz[3]   */
      partition = bcdec__bitstream_read_bits(5); /* d[4:0]  */
      mode = 2;
    }
      break;

    /* mode 4 */
    case 0b00110:
    {
      /* Partitition indices: 46 bits
         Partition: 5 bits
         Color Endpoints: 72 bits (11.444, 11.555, 11.444) */
      r[0] |= bcdec__bitstream_read_bits(10); /* rw[9:0] */
      g[0] |= bcdec__bitstream_read_bits(10); /* gw[9:0] */
      b[0] |= bcdec__bitstream_read_bits(10); /* bw[9:0] */
      r[1] |= bcdec__bitstream_read_bits(4); /* rx[3:0] */
      r[0] |= bcdec__bitstream_read_bit() << 10; /* rw[10]  */
      g[3] |= bcdec__bitstream_read_bit() << 4; /* gz[4]   */
      g[2] |= bcdec__bitstream_read_bits(4); /* gy[3:0] */
      g[1] |= bcdec__bitstream_read_bits(5); /* gx[4:0] */
      g[0] |= bcdec__bitstream_read_bit() << 10; /* gw[10]  */
      g[3] |= bcdec__bitstream_read_bits(4); /* gz[3:0] */
      b[1] |= bcdec__bitstream_read_bits(4); /* bx[3:0] */
      b[0] |= bcdec__bitstream_read_bit() << 10; /* bw[10]  */
      b[3] |= bcdec__bitstream_read_bit() << 1; /* bz[1]   */
      b[2] |= bcdec__bitstream_read_bits(4); /* by[3:0] */
      r[2] |= bcdec__bitstream_read_bits(4); /* ry[3:0] */
      b[3] |= bcdec__bitstream_read_bit(); /* bz[0]   */
      b[3] |= bcdec__bitstream_read_bit() << 2; /* bz[2]   */
      r[3] |= bcdec__bitstream_read_bits(4); /* rz[3:0] */
      g[2] |= bcdec__bitstream_read_bit() << 4; /* gy[4]   */
      b[3] |= bcdec__bitstream_read_bit() << 3; /* bz[3]   */
      partition = bcdec__bitstream_read_bits(5); /* d[4:0]  */
      mode = 3;
    }
      break;

    /* mode 5 */
    case 0b01010:
    {
      /* Partitition indices: 46 bits
         Partition: 5 bits
         Color Endpoints: 72 bits (11.444, 11.444, 11.555) */
      r[0] |= bcdec__bitstream_read_bits(10); /* rw[9:0] */
      g[0] |= bcdec__bitstream_read_bits(10); /* gw[9:0] */
      b[0] |= bcdec__bitstream_read_bits(10); /* bw[9:0] */
      r[1] |= bcdec__bitstream_read_bits(4); /* rx[3:0] */
      r[0] |= bcdec__bitstream_read_bit() << 10; /* rw[10]  */
      b[2] |= bcdec__bitstream_read_bit() << 4; /* by[4]   */
      g[2] |= bcdec__bitstream_read_bits(4); /* gy[3:0] */
      g[1] |= bcdec__bitstream_read_bits(4); /* gx[3:0] */
      g[0] |= bcdec__bitstream_read_bit() << 10; /* gw[10]  */
      b[3] |= bcdec__bitstream_read_bit(); /* bz[0]   */
      g[3] |= bcdec__bitstream_read_bits(4); /* gz[3:0] */
      b[1] |= bcdec__bitstream_read_bits(5); /* bx[4:0] */
      b[0] |= bcdec__bitstream_read_bit() << 10; /* bw[10]  */
      b[2] |= bcdec__bitstream_read_bits(4); /* by[3:0] */
      r[2] |= bcdec__bitstream_read_bits(4); /* ry[3:0] */
      b[3] |= bcdec__bitstream_read_bit() << 1; /* bz[1]   */
      b[3] |= bcdec__bitstream_read_bit() << 2; /* bz[2]   */
      r[3] |= bcdec__bitstream_read_bits(4); /* rz[3:0] */
      b[3] |= bcdec__bitstream_read_bit() << 4; /* bz[4]   */
      b[3] |= bcdec__bitstream_read_bit() << 3; /* bz[3]   */
      partition = bcdec__bitstream_read_bits(5); /* d[4:0]  */
      mode = 4;
    }
      break;

    /* mode 6 */
    case 0b01110:
    {
      /* Partitition indices: 46 bits
         Partition: 5 bits
         Color Endpoints: 72 bits (9555, 9555, 9555) */
      r[0] |= bcdec__bitstream_read_bits(9); /* rw[8:0] */
      b[2] |= bcdec__bitstream_read_bit() << 4; /* by[4]   */
      g[0] |= bcdec__bitstream_read_bits(9); /* gw[8:0] */
      g[2] |= bcdec__bitstream_read_bit() << 4; /* gy[4]   */
      b[0] |= bcdec__bitstream_read_bits(9); /* bw[8:0] */
      b[3] |= bcdec__bitstream_read_bit() << 4; /* bz[4]   */
      r[1] |= bcdec__bitstream_read_bits(5); /* rx[4:0] */
      g[3] |= bcdec__bitstream_read_bit() << 4; /* gz[4]   */
      g[2] |= bcdec__bitstream_read_bits(4); /* gy[3:0] */
      g[1] |= bcdec__bitstream_read_bits(5); /* gx[4:0] */
      b[3] |= bcdec__bitstream_read_bit(); /* bz[0]   */
      g[3] |= bcdec__bitstream_read_bits(4); /* gx[3:0] */
      b[1] |= bcdec__bitstream_read_bits(5); /* bx[4:0] */
      b[3] |= bcdec__bitstream_read_bit() << 1; /* bz[1]   */
      b[2] |= bcdec__bitstream_read_bits(4); /* by[3:0] */
      r[2] |= bcdec__bitstream_read_bits(5); /* ry[4:0] */
      b[3] |= bcdec__bitstream_read_bit() << 2; /* bz[2]   */
      r[3] |= bcdec__bitstream_read_bits(5); /* rz[4:0] */
      b[3] |= bcdec__bitstream_read_bit() << 3; /* bz[3]   */
      partition = bcdec__bitstream_read_bits(5); /* d[4:0]  */
      mode = 5;
    }
      break;

    /* mode 7 */
    case 0b10010:
    {
      /* Partitition indices: 46 bits
         Partition: 5 bits
         Color Endpoints: 72 bits (8666, 8555, 8555) */
      r[0] |= bcdec__bitstream_read_bits(8); /* rw[7:0] */
      g[3] |= bcdec__bitstream_read_bit() << 4; /* gz[4]   */
      b[2] |= bcdec__bitstream_read_bit() << 4; /* by[4]   */
      g[0] |= bcdec__bitstream_read_bits(8); /* gw[7:0] */
      b[3] |= bcdec__bitstream_read_bit() << 2; /* bz[2]   */
      g[2] |= bcdec__bitstream_read_bit() << 4; /* gy[4]   */
      b[0] |= bcdec__bitstream_read_bits(8); /* bw[7:0] */
      b[3] |= bcdec__bitstream_read_bit() << 3; /* bz[3]   */
      b[3] |= bcdec__bitstream_read_bit() << 4; /* bz[4]   */
      r[1] |= bcdec__bitstream_read_bits(6); /* rx[5:0] */
      g[2] |= bcdec__bitstream_read_bits(4); /* gy[3:0] */
      g[1] |= bcdec__bitstream_read_bits(5); /* gx[4:0] */
      b[3] |= bcdec__bitstream_read_bit(); /* bz[0]   */
      g[3] |= bcdec__bitstream_read_bits(4); /* gz[3:0] */
      b[1] |= bcdec__bitstream_read_bits(5); /* bx[4:0] */
      b[3] |= bcdec__bitstream_read_bit() << 1; /* bz[1]   */
      b[2] |= bcdec__bitstream_read_bits(4); /* by[3:0] */
      r[2] |= bcdec__bitstream_read_bits(6); /* ry[5:0] */
      r[3] |= bcdec__bitstream_read_bits(6); /* rz[5:0] */
      partition = bcdec__bitstream_read_bits(5); /* d[4:0]  */
      mode = 6;
    }
      break;

    /* mode 8 */
    case 0b10110:
    {
      /* Partitition indices: 46 bits
         Partition: 5 bits
         Color Endpoints: 72 bits (8555, 8666, 8555) */
      r[0] |= bcdec__bitstream_read_bits(8); /* rw[7:0] */
      b[3] |= bcdec__bitstream_read_bit(); /* bz[0]   */
      b[2] |= bcdec__bitstream_read_bit() << 4; /* by[4]   */
      g[0] |= bcdec__bitstream_read_bits(8); /* gw[7:0] */
      g[2] |= bcdec__bitstream_read_bit() << 5; /* gy[5]   */
      g[2] |= bcdec__bitstream_read_bit() << 4; /* gy[4]   */
      b[0] |= bcdec__bitstream_read_bits(8); /* bw[7:0] */
      g[3] |= bcdec__bitstream_read_bit() << 5; /* gz[5]   */
      b[3] |= bcdec__bitstream_read_bit() << 4; /* bz[4]   */
      r[1] |= bcdec__bitstream_read_bits(5); /* rx[4:0] */
      g[3] |= bcdec__bitstream_read_bit() << 4; /* gz[4]   */
      g[2] |= bcdec__bitstream_read_bits(4); /* gy[3:0] */
      g[1] |= bcdec__bitstream_read_bits(6); /* gx[5:0] */
      g[3] |= bcdec__bitstream_read_bits(4); /* zx[3:0] */
      b[1] |= bcdec__bitstream_read_bits(5); /* bx[4:0] */
      b[3] |= bcdec__bitstream_read_bit() << 1; /* bz[1]   */
      b[2] |= bcdec__bitstream_read_bits(4); /* by[3:0] */
      r[2] |= bcdec__bitstream_read_bits(5); /* ry[4:0] */
      b[3] |= bcdec__bitstream_read_bit() << 2; /* bz[2]   */
      r[3] |= bcdec__bitstream_read_bits(5); /* rz[4:0] */
      b[3] |= bcdec__bitstream_read_bit() << 3; /* bz[3]   */
      partition = bcdec__bitstream_read_bits(5); /* d[4:0]  */
      mode = 7;
    }
      break;

    /* mode 9 */
    case 0b11010:
    {
      /* Partitition indices: 46 bits
         Partition: 5 bits
         Color Endpoints: 72 bits (8555, 8555, 8666) */
      r[0] |= bcdec__bitstream_read_bits(8); /* rw[7:0] */
      b[3] |= bcdec__bitstream_read_bit() << 1; /* bz[1]   */
      b[2] |= bcdec__bitstream_read_bit() << 4; /* by[4]   */
      g[0] |= bcdec__bitstream_read_bits(8); /* gw[7:0] */
      b[2] |= bcdec__bitstream_read_bit() << 5; /* by[5]   */
      g[2] |= bcdec__bitstream_read_bit() << 4; /* gy[4]   */
      b[0] |= bcdec__bitstream_read_bits(8); /* bw[7:0] */
      b[3] |= bcdec__bitstream_read_bit() << 5; /* bz[5]   */
      b[3] |= bcdec__bitstream_read_bit() << 4; /* bz[4]   */
      r[1] |= bcdec__bitstream_read_bits(5); /* bw[4:0] */
      g[3] |= bcdec__bitstream_read_bit() << 4; /* gz[4]   */
      g[2] |= bcdec__bitstream_read_bits(4); /* gy[3:0] */
      g[1] |= bcdec__bitstream_read_bits(5); /* gx[4:0] */
      b[3] |= bcdec__bitstream_read_bit(); /* bz[0]   */
      g[3] |= bcdec__bitstream_read_bits(4); /* gz[3:0] */
      b[1] |= bcdec__bitstream_read_bits(6); /* bx[5:0] */
      b[2] |= bcdec__bitstream_read_bits(4); /* by[3:0] */
      r[2] |= bcdec__bitstream_read_bits(5); /* ry[4:0] */
      b[3] |= bcdec__bitstream_read_bit() << 2; /* bz[2]   */
      r[3] |= bcdec__bitstream_read_bits(5); /* rz[4:0] */
      b[3] |= bcdec__bitstream_read_bit() << 3; /* bz[3]   */
      partition = bcdec__bitstream_read_bits(5); /* d[4:0]  */
      mode = 8;
    }
      break;

    /* mode 10 */
    case 0b11110:
    {
      /* Partitition indices: 46 bits
         Partition: 5 bits
         Color Endpoints: 72 bits (6666, 6666, 6666) */
      r[0] |= bcdec__bitstream_read_bits(6); /* rw[5:0] */
      g[3] |= bcdec__bitstream_read_bit() << 4; /* gz[4]   */
      b[3] |= bcdec__bitstream_read_bit(); /* bz[0]   */
      b[3] |= bcdec__bitstream_read_bit() << 1; /* bz[1]   */
      b[2] |= bcdec__bitstream_read_bit() << 4; /* by[4]   */
      g[0] |= bcdec__bitstream_read_bits(6); /* gw[5:0] */
      g[2] |= bcdec__bitstream_read_bit() << 5; /* gy[5]   */
      b[2] |= bcdec__bitstream_read_bit() << 5; /* by[5]   */
      b[3] |= bcdec__bitstream_read_bit() << 2; /* bz[2]   */
      g[2] |= bcdec__bitstream_read_bit() << 4; /* gy[4]   */
      b[0] |= bcdec__bitstream_read_bits(6); /* bw[5:0] */
      g[3] |= bcdec__bitstream_read_bit() << 5; /* gz[5]   */
      b[3] |= bcdec__bitstream_read_bit() << 3; /* bz[3]   */
      b[3] |= bcdec__bitstream_read_bit() << 5; /* bz[5]   */
      b[3] |= bcdec__bitstream_read_bit() << 4; /* bz[4]   */
      r[1] |= bcdec__bitstream_read_bits(6); /* rx[5:0] */
      g[2] |= bcdec__bitstream_read_bits(4); /* gy[3:0] */
      g[1] |= bcdec__bitstream_read_bits(6); /* gx[5:0] */
      g[3] |= bcdec__bitstream_read_bits(4); /* gz[3:0] */
      b[1] |= bcdec__bitstream_read_bits(6); /* bx[5:0] */
      b[2] |= bcdec__bitstream_read_bits(4); /* by[3:0] */
      r[2] |= bcdec__bitstream_read_bits(6); /* ry[5:0] */
      r[3] |= bcdec__bitstream_read_bits(6); /* rz[5:0] */
      partition = bcdec__bitstream_read_bits(5); /* d[4:0]  */
      mode = 9;
    }
      break;

    /* mode 11 */
    case 0b00011:
    {
      /* Partitition indices: 63 bits
         Partition: 0 bits
         Color Endpoints: 60 bits (10.10, 10.10, 10.10) */
      r[0] |= bcdec__bitstream_read_bits(10); /* rw[9:0] */
      g[0] |= bcdec__bitstream_read_bits(10); /* gw[9:0] */
      b[0] |= bcdec__bitstream_read_bits(10); /* bw[9:0] */
      r[1] |= bcdec__bitstream_read_bits(10); /* rx[9:0] */
      g[1] |= bcdec__bitstream_read_bits(10); /* gx[9:0] */
      b[1] |= bcdec__bitstream_read_bits(10); /* bx[9:0] */
      mode = 10;
    }
      break;

    /* mode 12 */
    case 0b00111:
    {
      /* Partitition indices: 63 bits
         Partition: 0 bits
         Color Endpoints: 60 bits (11.9, 11.9, 11.9) */
      r[0] |= bcdec__bitstream_read_bits(10); /* rw[9:0] */
      g[0] |= bcdec__bitstream_read_bits(10); /* gw[9:0] */
      b[0] |= bcdec__bitstream_read_bits(10); /* bw[9:0] */
      r[1] |= bcdec__bitstream_read_bits(9); /* rx[8:0] */
      r[0] |= bcdec__bitstream_read_bit() << 10; /* rw[10]  */
      g[1] |= bcdec__bitstream_read_bits(9); /* gx[8:0] */
      g[0] |= bcdec__bitstream_read_bit() << 10; /* gw[10]  */
      b[1] |= bcdec__bitstream_read_bits(9); /* bx[8:0] */
      b[0] |= bcdec__bitstream_read_bit() << 10; /* bw[10]  */
      mode = 11;
    }
      break;

    /* mode 13 */
    case 0b01011:
    {
      /* Partitition indices: 63 bits
         Partition: 0 bits
         Color Endpoints: 60 bits (12.8, 12.8, 12.8) */
      r[0] |= bcdec__bitstream_read_bits(10); /* rw[9:0] */
      g[0] |= bcdec__bitstream_read_bits(10); /* gw[9:0] */
      b[0] |= bcdec__bitstream_read_bits(10); /* bw[9:0] */
      r[1] |= bcdec__bitstream_read_bits(8); /* rx[7:0] */
      r[0] |= bcdec__bitstream_read_bits_r(2) << 10;/* rx[10:11] */
      g[1] |= bcdec__bitstream_read_bits(8); /* gx[7:0] */
      g[0] |= bcdec__bitstream_read_bits_r(2) << 10;/* gx[10:11] */
      b[1] |= bcdec__bitstream_read_bits(8); /* bx[7:0] */
      b[0] |= bcdec__bitstream_read_bits_r(2) << 10;/* bx[10:11] */
      mode = 12;
    }
      break;

    /* mode 14 */
    case 0b01111:
    {
      /* Partitition indices: 63 bits
         Partition: 0 bits
         Color Endpoints: 60 bits (16.4, 16.4, 16.4) */
      r[0] |= bcdec__bitstream_read_bits(10); /* rw[9:0] */
      g[0] |= bcdec__bitstream_read_bits(10); /* gw[9:0] */
      b[0] |= bcdec__bitstream_read_bits(10); /* bw[9:0] */
      r[1] |= bcdec__bitstream_read_bits(4); /* rx[3:0] */
      r[0] |= bcdec__bitstream_read_bits_r(6) << 10;/* rw[10:15] */
      g[1] |= bcdec__bitstream_read_bits(4); /* gx[3:0] */
      g[0] |= bcdec__bitstream_read_bits_r(6) << 10;/* gw[10:15] */
      b[1] |= bcdec__bitstream_read_bits(4); /* bx[3:0] */
      b[0] |= bcdec__bitstream_read_bits_r(6) << 10;/* bw[10:15] */
      mode = 13;
    }
      break;

    default:
    {
      /* Modes 10011, 10111, 11011, and 11111 (not shown) are reserved.
         Do not use these in your encoder. If the hardware is passed blocks
         with one of these modes specified, the resulting decompressed block
         must contain all zeroes in all channels except for the alpha channel. */
      for (i = 0; i < 4; ++i) {
        for (j = 0; j < 4; ++j) {
          decompressedBlock[(i * 4) + j] = 0;
        }
      }

      return decompressedBlock;
    }
    }

    numPartitions = (mode >= 10) ? 0 : 1;

    actualBits0Mode = actual_bits_count[0][mode];
    if (isSigned) {
      r[0] = bcdec__extend_sign(r[0], actualBits0Mode);
      g[0] = bcdec__extend_sign(g[0], actualBits0Mode);
      b[0] = bcdec__extend_sign(b[0], actualBits0Mode);
    }

    /* Mode 11 (like Mode 10) does not use delta compression,
       and instead stores both color endpoints explicitly.  */
    if ((mode != 9 && mode != 10) || isSigned) {
      for (i = 1; i < (numPartitions + 1) * 2; ++i) {
        r[i] = bcdec__extend_sign(r[i], actual_bits_count[1][mode]);
        g[i] = bcdec__extend_sign(g[i], actual_bits_count[2][mode]);
        b[i] = bcdec__extend_sign(b[i], actual_bits_count[3][mode]);
      }
    }

    if (mode != 9 && mode != 10) {
      for (i = 1; i < (numPartitions + 1) * 2; ++i) {
        r[i] = bcdec__transform_inverse(r[i], r[0], actualBits0Mode, isSigned);
        g[i] = bcdec__transform_inverse(g[i], g[0], actualBits0Mode, isSigned);
        b[i] = bcdec__transform_inverse(b[i], b[0], actualBits0Mode, isSigned);
      }
    }

    for (i = 0; i < (numPartitions + 1) * 2; ++i) {
      r[i] = bcdec__unquantize(r[i], actualBits0Mode, isSigned);
      g[i] = bcdec__unquantize(g[i], actualBits0Mode, isSigned);
      b[i] = bcdec__unquantize(b[i], actualBits0Mode, isSigned);
    }

    weights = (mode >= 10) ? aWeight4 : aWeight3;
    for (i = 0; i < 4; ++i) {
      for (j = 0; j < 4; ++j) {
        partitionSet = (mode >= 10) ? ((i != 0 | j != 0) ? 0 : 128) : partition_sets[partition][i][j];

        indexBits = (mode >= 10) ? 4 : 3;
        /* fix-up index is specified with one less bit */
        /* The fix-up index for subset 0 is always index 0 */
        if ((partitionSet & 0x80) != 0) {
          indexBits--;
        }
        partitionSet &= 0x01;

        index = bcdec__bitstream_read_bits(indexBits);

        ep_i = partitionSet * 2;
        /*
        decompressed[j * 3 + 0] = bcdec__finish_unquantize(
            bcdec__interpolate(r[ep_i], r[ep_i + 1], weights, index), isSigned);
        decompressed[j * 3 + 1] = bcdec__finish_unquantize(
            bcdec__interpolate(g[ep_i], g[ep_i + 1], weights, index), isSigned);
        decompressed[j * 3 + 2] = bcdec__finish_unquantize(
            bcdec__interpolate(b[ep_i], b[ep_i + 1], weights, index), isSigned);
        */
        int rValue = bcdec__finish_unquantize(bcdec__interpolate(r[ep_i], r[ep_i + 1], weights, index), isSigned);
        int gValue = bcdec__finish_unquantize(bcdec__interpolate(g[ep_i], g[ep_i + 1], weights, index), isSigned);
        int bValue = bcdec__finish_unquantize(bcdec__interpolate(b[ep_i], b[ep_i + 1], weights, index), isSigned);
        int aValue = 255;

        // OUTPUT = ARGB
        decompressedBlock[(i * 4) + j] = aValue << 24 | rValue << 16 | gValue << 8 | bValue;
      }

    }

    return decompressedBlock;
  }

  /**
  **********************************************************************************************
  
  **********************************************************************************************
  **/
  public int[] bcdec_bc6h_float(byte[] compressedBlock, boolean isSigned) {

    int[] decompressedBlock = bcdec_bc6h_half(compressedBlock, isSigned);

    for (int i = 0; i < 16; i++) {
      int value = decompressedBlock[i];

      int rValue = (value >> 16) & 255;
      int gValue = (value >> 8) & 255;
      int bValue = (value) & 255;
      int aValue = 255;

      float rFloat = FloatConverter.convertLittle(ByteArrayConverter.convertLittle(rValue));
      float gFloat = FloatConverter.convertLittle(ByteArrayConverter.convertLittle(gValue));
      float bFloat = FloatConverter.convertLittle(ByteArrayConverter.convertLittle(bValue));

      rValue = (int) (rFloat * 255);
      gValue = (int) (gFloat * 255);
      bValue = (int) (bFloat * 255);

      // OUTPUT = ARGB
      decompressedBlock[i] = aValue << 24 | rValue << 16 | gValue << 8 | bValue;
    }

    return decompressedBlock;
  }

}