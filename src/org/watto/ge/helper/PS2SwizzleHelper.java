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

/**
**********************************************************************************************
Ref: https://github.com/bartlomiejduda/ReverseBox/blob/main/reversebox/image/swizzling/swizzle_ps2_4bit.py
NONE OF THIS IS TESTED
**********************************************************************************************
**/
public class PS2SwizzleHelper {

  static int PSMT8_PAGE_WIDTH = 128;
  static int PSMT8_PAGE_HEIGHT = 64;
  static int PSMCT32_PAGE_WIDTH = 64;
  static int PSMCT32_PAGE_HEIGHT = 32;
  static int PSMT4_PAGE_WIDTH = 128;
  static int PSMT4_PAGE_HEIGHT = 128;
  static int PSMT4_BLOCK_WIDTH = 32;
  static int PSMT4_BLOCK_HEIGHT = 16;
  static int PSMT8_BLOCK_WIDTH = 16;
  static int PSMT8_BLOCK_HEIGHT = 16;
  static int PSMCT32_BLOCK_WIDTH = 8;
  static int PSMCT32_BLOCK_HEIGHT = 8;

  public static byte[] _unswizzle4_convert_block(byte[] input_block_data) {

    if (input_block_data.length != 256) {
      return null;
    }

    byte[] unswizzle_lut_table = new byte[] {
        0, 8, 16, 24, 32, 40, 48, 56,
        2, 10, 18, 26, 34, 42, 50, 58,
        4, 12, 20, 28, 36, 44, 52, 60,
        6, 14, 22, 30, 38, 46, 54, 62,
        64, 72, 80, 88, 96, 104, 112, 120,
        66, 74, 82, 90, 98, 106, 114, 122,
        68, 76, 84, 92, 100, 108, 116, 124,
        70, 78, 86, 94, 102, 110, 118, 126,
        33, 41, 49, 57, 1, 9, 17, 25,
        35, 43, 51, 59, 3, 11, 19, 27,
        37, 45, 53, 61, 5, 13, 21, 29,
        39, 47, 55, 63, 7, 15, 23, 31,
        97, 105, 113, 121, 65, 73, 81, 89,
        99, 107, 115, 123, 67, 75, 83, 91,
        101, 109, 117, 125, 69, 77, 85, 93,
        103, 111, 119, 127, 71, 79, 87, 95,
        32, 40, 48, 56, 0, 8, 16, 24,
        34, 42, 50, 58, 2, 10, 18, 26,
        36, 44, 52, 60, 4, 12, 20, 28,
        38, 46, 54, 62, 6, 14, 22, 30,
        96, 104, 112, 120, 64, 72, 80, 88,
        98, 106, 114, 122, 66, 74, 82, 90,
        100, 108, 116, 124, 68, 76, 84, 92,
        102, 110, 118, 126, 70, 78, 86, 94,
        1, 9, 17, 25, 33, 41, 49, 57,
        3, 11, 19, 27, 35, 43, 51, 59,
        5, 13, 21, 29, 37, 45, 53, 61,
        7, 15, 23, 31, 39, 47, 55, 63,
        65, 73, 81, 89, 97, 105, 113, 121,
        67, 75, 83, 91, 99, 107, 115, 123,
        69, 77, 85, 93, 101, 109, 117, 125,
        71, 79, 87, 95, 103, 111, 119, 127
    };

    byte[] output_block_data = new byte[(16 * 16)];
    int index1 = 0;
    int p_in = 0;
    for (int k = 0; k < 4; k++) {
      int index0 = (k % 2) * 128;

      for (int i = 0; i < 16; i++) {
        for (int j = 0; j < 4; j++) {
          int c_out = 0x00;
          int i0 = unswizzle_lut_table[index0];
          index0 += 1;
          int i1 = i0 / 2;
          int i2 = (i0 & 0x1) * 4;
          int c_in = (input_block_data[p_in + i1] & (0x0F << i2)) >> i2;
          c_out = c_out | c_in;

          i0 = unswizzle_lut_table[index0];
          index0 += 1;
          i1 = i0 / 2;
          i2 = (i0 & 0x1) * 4;
          c_in = (input_block_data[p_in + i1] & (0x0F << i2)) >> i2;
          c_out = c_out | (c_in << 4) & 0xF0;

          output_block_data[index1] = (byte) c_out;
          index1 += 1;
        }
      }
      p_in += 64;
    }

    if (output_block_data.length != 256) {
      return null;
    }
    return output_block_data;
  }

  public static byte[] _unswizzle4_convert_page(int width, int height, byte[] input_page_data) {
    int[] block_table4 = new int[] {
        0, 2, 8, 10, 1, 3, 9, 11, 4, 6, 12, 14, 5, 7, 13, 15,
        16, 18, 24, 26, 17, 19, 25, 27, 20, 22, 28, 30, 21, 23, 29, 31
    };

    int[] block_table32 = new int[] {
        0, 1, 4, 5, 16, 17, 20, 21, 2, 3, 6, 7, 18, 19, 22, 23,
        8, 9, 12, 13, 24, 25, 28, 29, 10, 11, 14, 15, 26, 27, 30, 31
    };

    byte[] output_page_data = new byte[(PSMCT32_PAGE_WIDTH * 4 * PSMCT32_PAGE_HEIGHT)];

    int[] index32_h_arr = new int[32];
    int[] index32_v_arr = new int[32];

    int index0 = 0;
    for (int i = 0; i < 4; i++) {
      for (int j = 0; j < 8; j++) {
        int index1 = block_table32[index0];
        index32_h_arr[index1] = j;
        index32_v_arr[index1] = i;
        index0 += 1;
      }
    }

    int n_width = width / 32;
    int n_height = height / 16;
    int input_page_line_size = 256;
    int output_page_line_size = 128 / 2;

    for (int i = 0; i < n_height; i++) {
      for (int j = 0; j < n_width; j++) {
        int in_block_nb = block_table4[i * n_width + j];
        byte[] po0 = new byte[(16 * 16)];
        int po1_offset = 8 * index32_v_arr[in_block_nb] * input_page_line_size + index32_h_arr[in_block_nb] * 32;

        //po1 = input_page_data[po1_offset:];
        byte[] po1 = new byte[input_page_data.length - po1_offset];
        System.arraycopy(input_page_data, po1_offset, po1, 0, (input_page_data.length - po1_offset));

        for (int k = 0; k < PSMCT32_BLOCK_HEIGHT; k++) {
          //po0[k*32:(k+1)*32] = po1[k*input_page_line_size:k*input_page_line_size + 32];  
          System.arraycopy(po1, k * input_page_line_size, po0, k * 32, ((k * input_page_line_size + 32) - k * input_page_line_size));

        }

        byte[] output_block = _unswizzle4_convert_block(po0);

        for (int k = 0; k < PSMT4_BLOCK_HEIGHT; k++) {
          int start = (16 * i * output_page_line_size) + j * 16 + k * output_page_line_size;
          //output_page_data[start:start + 16] = output_block[k*16:k*16 + 16];
          System.arraycopy(output_block, k * 16, output_page_data, start, ((k * 16 + 16) - (k * 16)));
        }
      }
    }

    return output_page_data;
  }

  public static byte[] _ps2_unswizzle4(byte[] input_data, int img_width, int img_height) {
    byte[] output_data = new byte[input_data.length];
    int n_page_w = (img_width - 1) / PSMT4_PAGE_WIDTH + 1;
    int n_page_h = (img_height - 1) / PSMT4_PAGE_HEIGHT + 1;
    int n_page4_width_byte = PSMT4_PAGE_WIDTH / 2;
    int n_page32_width_byte = PSMCT32_PAGE_WIDTH * 4;

    int n_input_width_byte = 0;
    int n_output_height = 0;
    if (n_page_h == 1) {
      n_input_width_byte = img_height * 2;
      n_output_height = img_height;
    }
    else {
      n_input_width_byte = n_page32_width_byte;
      n_output_height = PSMT4_PAGE_HEIGHT;
    }

    int n_input_height = 0;
    int n_output_width_byte = 0;
    if (n_page_w == 1) {
      n_input_height = img_width / 4;
      n_output_width_byte = img_width / 2;
    }
    else {
      n_input_height = PSMCT32_PAGE_HEIGHT;
      n_output_width_byte = n_page4_width_byte;
    }

    for (int i = 0; i < n_page_h; i++) {
      for (int j = 0; j < n_page_w; j++) {
        int po0_offset = (n_input_width_byte * n_input_height) * n_page_w * i + n_input_width_byte * j;
        //po0 = input_data[po0_offset:];  
        byte[] po0 = new byte[input_data.length - po0_offset];
        System.arraycopy(input_data, po0_offset, po0, 0, (input_data.length - po0_offset));

        byte[] input_page = new byte[(PSMT4_PAGE_WIDTH / 2 * PSMT4_PAGE_HEIGHT)];

        for (int k = 0; k < n_input_height; k++) {
          int src_offset = k * n_input_width_byte * n_page_w;
          int dst_offset = k * n_page32_width_byte;
          //input_page[dst_offset:dst_offset + n_input_width_byte] = po0[src_offset:src_offset + n_input_width_byte];
          System.arraycopy(po0, src_offset, input_page, dst_offset, ((src_offset + n_input_width_byte) - src_offset));
        }

        byte[] output_page = _unswizzle4_convert_page(PSMT4_PAGE_WIDTH, PSMT4_PAGE_HEIGHT, input_page);

        int pi0_offset = (n_output_width_byte * n_output_height) * n_page_w * i + n_output_width_byte * j;
        for (int k = 0; k < n_output_height; k++) {
          int src_offset = k * n_page4_width_byte;
          int dst_offset = pi0_offset + k * n_output_width_byte * n_page_w;
          //output_data[dst_offset:dst_offset + n_output_width_byte] = output_page[src_offset:src_offset + n_output_width_byte];
          System.arraycopy(output_page, src_offset, output_data, dst_offset, ((src_offset + n_output_width_byte) - src_offset));
        }
      }
    }

    return output_data;
  }

  public static byte[] _swizzle4_convert_block(byte[] input_block_data) {

    if (input_block_data.length != 256) {
      return null;
    }

    byte[] swizzle4_lut_table = new byte[] { 0, 68, 8, 76, 16, 84, 24, 92, 1, 69, 9, 77, 17, 85, 25, 93, 2, 70, 10, 78, 18, 86, 26, 94, 3, 71, 11, 79, 19, 87, 27, 95, 4, 64, 12, 72, 20, 80, 28, 88, 5, 65, 13, 73, 21, 81, 29, 89, 6, 66, 14, 74, 22, 82, 30, 90, 7, 67, 15, 75, 23, 83, 31, 91, 32, 100, 40, 108, 48, 116, 56, 124, 33, 101, 41, 109, 49, 117, 57, 125, 34, 102, 42, 110, 50, 118, 58, 126, 35, 103, 43, 111, 51, 119, 59, 127, 36, 96, 44, 104, 52, 112, 60, 120, 37, 97, 45, 105, 53, 113, 61, 121, 38, 98, 46, 106, 54, 114, 62, 122, 39, 99, 47, 107, 55, 115, 63, 123, 4, 64, 12, 72, 20, 80, 28, 88, 5, 65, 13, 73, 21, 81, 29, 89, 6, 66, 14, 74, 22, 82, 30, 90, 7, 67, 15, 75, 23, 83, 31, 91, 0, 68, 8, 76, 16, 84, 24, 92, 1, 69, 9, 77, 17, 85, 25, 93, 2, 70, 10, 78, 18, 86, 26, 94, 3, 71, 11, 79, 19, 87, 27, 95, 36, 96, 44, 104, 52, 112, 60, 120, 37, 97, 45, 105, 53, 113, 61, 121, 38, 98, 46, 106, 54, 114, 62, 122, 39, 99, 47, 107, 55, 115, 63, 123, 32, 100, 40, 108, 48, 116, 56, 124,
        33, 101, 41, 109, 49, 117, 57, 125, 34, 102, 42, 110, 50, 118, 58, 126, 35, 103, 43, 111, 51, 119, 59, 127 };

    byte[] output_block_data = new byte[(16 * 16)];

    int index1 = 0;
    int p_in = 0;
    for (int k = 0; k < 4; k++) {
      int index0 = (k % 2) * 128;
      for (int i = 0; i < 16; i++) {
        for (int j = 0; j < 4; j++) {
          int c_out = 0x00;
          for (int step = 0; step < 2; step++) {
            int i0 = swizzle4_lut_table[index0];
            index0 += 1;
            int i1 = i0 / 2;
            int i2 = (i0 & 0x1) * 4;
            int c_in = (input_block_data[p_in + i1] & (0x0f << i2)) >> i2;
            if (step == 0) {
              c_out |= c_in;
            }
            else {
              c_out |= (c_in << 4) & 0xf0;
            }
          }
          output_block_data[index1] = (byte) c_out;
          index1 += 1;
        }
      }
      p_in += 64;
    }

    if (output_block_data.length != 256) {
      return null;
    }

    return output_block_data;
  }

  public static byte[] _swizzle4_convert_page(int width, int height, byte[] input_page_data) {
    int[] block_table4 = new int[] {
        0, 2, 8, 10, 1, 3, 9, 11,
        4, 6, 12, 14, 5, 7, 13, 15,
        16, 18, 24, 26, 17, 19, 25, 27,
        20, 22, 28, 30, 21, 23, 29, 31
    };

    int[] block_table32 = new int[] {
        0, 1, 4, 5, 16, 17, 20, 21,
        2, 3, 6, 7, 18, 19, 22, 23,
        8, 9, 12, 13, 24, 25, 28, 29,
        10, 11, 14, 15, 26, 27, 30, 31
    };

    int[] index32_h = new int[32];
    int[] index32_v = new int[32];

    byte[] output_page_data = new byte[(PSMCT32_PAGE_WIDTH * 4 * PSMCT32_PAGE_HEIGHT)];

    int index0 = 0;
    for (int i = 0; i < 4; i++) {
      for (int j = 0; j < 8; j++) {
        int index1 = block_table32[index0];
        index32_h[index1] = j;
        index32_v[index1] = i;
        index0 += 1;
      }
    }

    int n_width = width / 32;
    int n_height = height / 16;
    int input_page_line_size = 128 / 2;
    int output_page_line_size = 256;

    byte[] input_block = new byte[(16 * 16)];

    for (int i = 0; i < n_height; i++) {
      for (int j = 0; j < n_width; j++) {
        byte[] pi0 = input_block;
        int pi1_idx = 16 * i * input_page_line_size + j * 16;

        int in_block_nb = block_table4[i * n_width + j];

        for (int k = 0; k < PSMT4_BLOCK_HEIGHT; k++) {
          int start = pi1_idx + k * input_page_line_size;
          //pi0[k * (PSMT4_BLOCK_WIDTH / 2):(k + 1) * (PSMT4_BLOCK_WIDTH / 2)] = input_page_data[start:start + (PSMT4_BLOCK_WIDTH / 2)];
          System.arraycopy(input_page_data, start, pi0, k * (PSMT4_BLOCK_WIDTH / 2), ((start + (PSMT4_BLOCK_WIDTH / 2)) - start));
        }

        byte[] output_block = _swizzle4_convert_block(input_block);

        int po0_idx = 8 * index32_v[in_block_nb] * output_page_line_size + index32_h[in_block_nb] * 32;

        for (int k = 0; k < PSMCT32_BLOCK_HEIGHT; k++) {
          int start = k * PSMCT32_BLOCK_WIDTH * 4;
          int end = start + PSMCT32_BLOCK_WIDTH * 4;
          int out_start = po0_idx + k * output_page_line_size;
          //output_page_data[out_start:out_start + PSMCT32_BLOCK_WIDTH * 4] = output_block[start:end];
          System.arraycopy(output_block, start, output_page_data, out_start, end - start);
        }
      }
    }

    return output_page_data;
  }

  public static byte[] _ps2_swizzle4(byte[] input_data, int width, int height) {
    byte[] output_data = new byte[input_data.length];
    int n_page_w = (width - 1) / PSMT4_PAGE_WIDTH + 1;
    int n_page_h = (height - 1) / PSMT4_PAGE_HEIGHT + 1;
    int n_page4_width_byte = PSMT4_PAGE_WIDTH / 2;
    int n_page32_width_byte = PSMCT32_PAGE_WIDTH * 4;

    int n_input_width_byte = 0;
    int n_output_height = 0;
    if (n_page_w == 1) {
      n_input_width_byte = width / 2;
      n_output_height = width / 4;
    }
    else {
      n_input_width_byte = n_page4_width_byte;
      n_output_height = PSMCT32_PAGE_HEIGHT;
    }

    int n_input_height = 0;
    int n_output_width_byte = 0;
    if (n_page_h == 1) {
      n_input_height = height;
      n_output_width_byte = height * 2;
    }
    else {
      n_input_height = PSMT4_PAGE_HEIGHT;
      n_output_width_byte = n_page32_width_byte;
    }

    byte[] input_page = new byte[(PSMT4_PAGE_WIDTH / 2 * PSMT4_PAGE_HEIGHT)];

    for (int i = 0; i < n_page_h; i++) {
      for (int j = 0; j < n_page_w; j++) {
        for (int k = 0; k < n_input_height; k++) {
          int src_idx = (n_input_width_byte * n_input_height) * n_page_w * i + n_input_width_byte * j + k * n_input_width_byte * n_page_w;
          int dst_idx = k * n_page4_width_byte;
          //input_page[dst_idx:dst_idx + n_input_width_byte] = input_data[src_idx:src_idx + n_input_width_byte];
          System.arraycopy(input_data, src_idx, input_page, dst_idx, ((src_idx + n_input_width_byte) - src_idx));
        }

        byte[] output_page = _swizzle4_convert_page(PSMT4_PAGE_WIDTH, PSMT4_PAGE_HEIGHT, input_page);

        for (int k = 0; k < n_output_height; k++) {
          int src_idx = k * n_page32_width_byte;
          int dst_idx = (n_output_width_byte * n_output_height) * n_page_w * i + n_output_width_byte * j + k * n_output_width_byte * n_page_w;
          //output_data[dst_idx:dst_idx + n_output_width_byte] = output_page[src_idx:src_idx + n_output_width_byte];
          System.arraycopy(output_page, src_idx, output_data, dst_idx, ((src_idx + n_output_width_byte) - src_idx));
        }
      }
    }

    return output_data;
  }

  public static int[] _unswizzle4_convert_block(int[] input_block_data) {

    if (input_block_data.length != 256) {
      return null;
    }

    byte[] unswizzle_lut_table = new byte[] {
        0, 8, 16, 24, 32, 40, 48, 56,
        2, 10, 18, 26, 34, 42, 50, 58,
        4, 12, 20, 28, 36, 44, 52, 60,
        6, 14, 22, 30, 38, 46, 54, 62,
        64, 72, 80, 88, 96, 104, 112, 120,
        66, 74, 82, 90, 98, 106, 114, 122,
        68, 76, 84, 92, 100, 108, 116, 124,
        70, 78, 86, 94, 102, 110, 118, 126,
        33, 41, 49, 57, 1, 9, 17, 25,
        35, 43, 51, 59, 3, 11, 19, 27,
        37, 45, 53, 61, 5, 13, 21, 29,
        39, 47, 55, 63, 7, 15, 23, 31,
        97, 105, 113, 121, 65, 73, 81, 89,
        99, 107, 115, 123, 67, 75, 83, 91,
        101, 109, 117, 125, 69, 77, 85, 93,
        103, 111, 119, 127, 71, 79, 87, 95,
        32, 40, 48, 56, 0, 8, 16, 24,
        34, 42, 50, 58, 2, 10, 18, 26,
        36, 44, 52, 60, 4, 12, 20, 28,
        38, 46, 54, 62, 6, 14, 22, 30,
        96, 104, 112, 120, 64, 72, 80, 88,
        98, 106, 114, 122, 66, 74, 82, 90,
        100, 108, 116, 124, 68, 76, 84, 92,
        102, 110, 118, 126, 70, 78, 86, 94,
        1, 9, 17, 25, 33, 41, 49, 57,
        3, 11, 19, 27, 35, 43, 51, 59,
        5, 13, 21, 29, 37, 45, 53, 61,
        7, 15, 23, 31, 39, 47, 55, 63,
        65, 73, 81, 89, 97, 105, 113, 121,
        67, 75, 83, 91, 99, 107, 115, 123,
        69, 77, 85, 93, 101, 109, 117, 125,
        71, 79, 87, 95, 103, 111, 119, 127
    };

    int[] output_block_data = new int[(16 * 16)];
    int index1 = 0;
    int p_in = 0;
    for (int k = 0; k < 4; k++) {
      int index0 = (k % 2) * 128;

      for (int i = 0; i < 16; i++) {
        for (int j = 0; j < 4; j++) {
          int c_out = 0x00;
          int i0 = unswizzle_lut_table[index0];
          index0 += 1;
          int i1 = i0 / 2;
          int i2 = (i0 & 0x1) * 4;
          int c_in = (input_block_data[p_in + i1] & (0x0F << i2)) >> i2;
          c_out = c_out | c_in;

          i0 = unswizzle_lut_table[index0];
          index0 += 1;
          i1 = i0 / 2;
          i2 = (i0 & 0x1) * 4;
          c_in = (input_block_data[p_in + i1] & (0x0F << i2)) >> i2;
          c_out = c_out | (c_in << 4) & 0xF0;

          output_block_data[index1] = (byte) c_out;
          index1 += 1;
        }
      }
      p_in += 64;
    }

    if (output_block_data.length != 256) {
      return null;
    }
    return output_block_data;
  }

  public static int[] _unswizzle4_convert_page(int width, int height, int[] input_page_data) {
    int[] block_table4 = new int[] {
        0, 2, 8, 10, 1, 3, 9, 11, 4, 6, 12, 14, 5, 7, 13, 15,
        16, 18, 24, 26, 17, 19, 25, 27, 20, 22, 28, 30, 21, 23, 29, 31
    };

    int[] block_table32 = new int[] {
        0, 1, 4, 5, 16, 17, 20, 21, 2, 3, 6, 7, 18, 19, 22, 23,
        8, 9, 12, 13, 24, 25, 28, 29, 10, 11, 14, 15, 26, 27, 30, 31
    };

    int[] output_page_data = new int[(PSMCT32_PAGE_WIDTH * 4 * PSMCT32_PAGE_HEIGHT)];

    int[] index32_h_arr = new int[32];
    int[] index32_v_arr = new int[32];

    int index0 = 0;
    for (int i = 0; i < 4; i++) {
      for (int j = 0; j < 8; j++) {
        int index1 = block_table32[index0];
        index32_h_arr[index1] = j;
        index32_v_arr[index1] = i;
        index0 += 1;
      }
    }

    int n_width = width / 32;
    int n_height = height / 16;
    int input_page_line_size = 256;
    int output_page_line_size = 128 / 2;

    for (int i = 0; i < n_height; i++) {
      for (int j = 0; j < n_width; j++) {
        int in_block_nb = block_table4[i * n_width + j];
        byte[] po0 = new byte[(16 * 16)];
        int po1_offset = 8 * index32_v_arr[in_block_nb] * input_page_line_size + index32_h_arr[in_block_nb] * 32;

        //po1 = input_page_data[po1_offset:];
        byte[] po1 = new byte[input_page_data.length - po1_offset];
        System.arraycopy(input_page_data, po1_offset, po1, 0, (input_page_data.length - po1_offset));

        for (int k = 0; k < PSMCT32_BLOCK_HEIGHT; k++) {
          //po0[k*32:(k+1)*32] = po1[k*input_page_line_size:k*input_page_line_size + 32];  
          System.arraycopy(po1, k * input_page_line_size, po0, k * 32, ((k * input_page_line_size + 32) - k * input_page_line_size));

        }

        byte[] output_block = _unswizzle4_convert_block(po0);

        for (int k = 0; k < PSMT4_BLOCK_HEIGHT; k++) {
          int start = (16 * i * output_page_line_size) + j * 16 + k * output_page_line_size;
          //output_page_data[start:start + 16] = output_block[k*16:k*16 + 16];
          System.arraycopy(output_block, k * 16, output_page_data, start, ((k * 16 + 16) - (k * 16)));
        }
      }
    }

    return output_page_data;
  }

  public static int[] _ps2_unswizzle4(int[] input_data, int img_width, int img_height) {
    int[] output_data = new int[input_data.length];
    int n_page_w = (img_width - 1) / PSMT4_PAGE_WIDTH + 1;
    int n_page_h = (img_height - 1) / PSMT4_PAGE_HEIGHT + 1;
    int n_page4_width_byte = PSMT4_PAGE_WIDTH / 2;
    int n_page32_width_byte = PSMCT32_PAGE_WIDTH * 4;

    int n_input_width_byte = 0;
    int n_output_height = 0;
    if (n_page_h == 1) {
      n_input_width_byte = img_height * 2;
      n_output_height = img_height;
    }
    else {
      n_input_width_byte = n_page32_width_byte;
      n_output_height = PSMT4_PAGE_HEIGHT;
    }

    int n_input_height = 0;
    int n_output_width_byte = 0;
    if (n_page_w == 1) {
      n_input_height = img_width / 4;
      n_output_width_byte = img_width / 2;
    }
    else {
      n_input_height = PSMCT32_PAGE_HEIGHT;
      n_output_width_byte = n_page4_width_byte;
    }

    for (int i = 0; i < n_page_h; i++) {
      for (int j = 0; j < n_page_w; j++) {
        int po0_offset = (n_input_width_byte * n_input_height) * n_page_w * i + n_input_width_byte * j;
        //po0 = input_data[po0_offset:];  
        int[] po0 = new int[input_data.length - po0_offset];
        System.arraycopy(input_data, po0_offset, po0, 0, (input_data.length - po0_offset));

        int[] input_page = new int[(PSMT4_PAGE_WIDTH / 2 * PSMT4_PAGE_HEIGHT)];

        for (int k = 0; k < n_input_height; k++) {
          int src_offset = k * n_input_width_byte * n_page_w;
          int dst_offset = k * n_page32_width_byte;
          //input_page[dst_offset:dst_offset + n_input_width_byte] = po0[src_offset:src_offset + n_input_width_byte];
          System.arraycopy(po0, src_offset, input_page, dst_offset, ((src_offset + n_input_width_byte) - src_offset));
        }

        int[] output_page = _unswizzle4_convert_page(PSMT4_PAGE_WIDTH, PSMT4_PAGE_HEIGHT, input_page);

        int pi0_offset = (n_output_width_byte * n_output_height) * n_page_w * i + n_output_width_byte * j;
        for (int k = 0; k < n_output_height; k++) {
          int src_offset = k * n_page4_width_byte;
          int dst_offset = pi0_offset + k * n_output_width_byte * n_page_w;
          //output_data[dst_offset:dst_offset + n_output_width_byte] = output_page[src_offset:src_offset + n_output_width_byte];
          System.arraycopy(output_page, src_offset, output_data, dst_offset, ((src_offset + n_output_width_byte) - src_offset));
        }
      }
    }

    return output_data;
  }

  public static int[] _swizzle4_convert_block(int[] input_block_data) {

    if (input_block_data.length != 256) {
      return null;
    }

    byte[] swizzle4_lut_table = new byte[] { 0, 68, 8, 76, 16, 84, 24, 92, 1, 69, 9, 77, 17, 85, 25, 93, 2, 70, 10, 78, 18, 86, 26, 94, 3, 71, 11, 79, 19, 87, 27, 95, 4, 64, 12, 72, 20, 80, 28, 88, 5, 65, 13, 73, 21, 81, 29, 89, 6, 66, 14, 74, 22, 82, 30, 90, 7, 67, 15, 75, 23, 83, 31, 91, 32, 100, 40, 108, 48, 116, 56, 124, 33, 101, 41, 109, 49, 117, 57, 125, 34, 102, 42, 110, 50, 118, 58, 126, 35, 103, 43, 111, 51, 119, 59, 127, 36, 96, 44, 104, 52, 112, 60, 120, 37, 97, 45, 105, 53, 113, 61, 121, 38, 98, 46, 106, 54, 114, 62, 122, 39, 99, 47, 107, 55, 115, 63, 123, 4, 64, 12, 72, 20, 80, 28, 88, 5, 65, 13, 73, 21, 81, 29, 89, 6, 66, 14, 74, 22, 82, 30, 90, 7, 67, 15, 75, 23, 83, 31, 91, 0, 68, 8, 76, 16, 84, 24, 92, 1, 69, 9, 77, 17, 85, 25, 93, 2, 70, 10, 78, 18, 86, 26, 94, 3, 71, 11, 79, 19, 87, 27, 95, 36, 96, 44, 104, 52, 112, 60, 120, 37, 97, 45, 105, 53, 113, 61, 121, 38, 98, 46, 106, 54, 114, 62, 122, 39, 99, 47, 107, 55, 115, 63, 123, 32, 100, 40, 108, 48, 116, 56, 124,
        33, 101, 41, 109, 49, 117, 57, 125, 34, 102, 42, 110, 50, 118, 58, 126, 35, 103, 43, 111, 51, 119, 59, 127 };

    int[] output_block_data = new int[(16 * 16)];

    int index1 = 0;
    int p_in = 0;
    for (int k = 0; k < 4; k++) {
      int index0 = (k % 2) * 128;
      for (int i = 0; i < 16; i++) {
        for (int j = 0; j < 4; j++) {
          int c_out = 0x00;
          for (int step = 0; step < 2; step++) {
            int i0 = swizzle4_lut_table[index0];
            index0 += 1;
            int i1 = i0 / 2;
            int i2 = (i0 & 0x1) * 4;
            int c_in = (input_block_data[p_in + i1] & (0x0f << i2)) >> i2;
            if (step == 0) {
              c_out |= c_in;
            }
            else {
              c_out |= (c_in << 4) & 0xf0;
            }
          }
          output_block_data[index1] = (byte) c_out;
          index1 += 1;
        }
      }
      p_in += 64;
    }

    if (output_block_data.length != 256) {
      return null;
    }

    return output_block_data;
  }

  public static int[] _swizzle4_convert_page(int width, int height, int[] input_page_data) {
    int[] block_table4 = new int[] {
        0, 2, 8, 10, 1, 3, 9, 11,
        4, 6, 12, 14, 5, 7, 13, 15,
        16, 18, 24, 26, 17, 19, 25, 27,
        20, 22, 28, 30, 21, 23, 29, 31
    };

    int[] block_table32 = new int[] {
        0, 1, 4, 5, 16, 17, 20, 21,
        2, 3, 6, 7, 18, 19, 22, 23,
        8, 9, 12, 13, 24, 25, 28, 29,
        10, 11, 14, 15, 26, 27, 30, 31
    };

    int[] index32_h = new int[32];
    int[] index32_v = new int[32];

    int[] output_page_data = new int[(PSMCT32_PAGE_WIDTH * 4 * PSMCT32_PAGE_HEIGHT)];

    int index0 = 0;
    for (int i = 0; i < 4; i++) {
      for (int j = 0; j < 8; j++) {
        int index1 = block_table32[index0];
        index32_h[index1] = j;
        index32_v[index1] = i;
        index0 += 1;
      }
    }

    int n_width = width / 32;
    int n_height = height / 16;
    int input_page_line_size = 128 / 2;
    int output_page_line_size = 256;

    int[] input_block = new int[(16 * 16)];

    for (int i = 0; i < n_height; i++) {
      for (int j = 0; j < n_width; j++) {
        int[] pi0 = input_block;
        int pi1_idx = 16 * i * input_page_line_size + j * 16;

        int in_block_nb = block_table4[i * n_width + j];

        for (int k = 0; k < PSMT4_BLOCK_HEIGHT; k++) {
          int start = pi1_idx + k * input_page_line_size;
          //pi0[k * (PSMT4_BLOCK_WIDTH / 2):(k + 1) * (PSMT4_BLOCK_WIDTH / 2)] = input_page_data[start:start + (PSMT4_BLOCK_WIDTH / 2)];
          System.arraycopy(input_page_data, start, pi0, k * (PSMT4_BLOCK_WIDTH / 2), ((start + (PSMT4_BLOCK_WIDTH / 2)) - start));
        }

        int[] output_block = _swizzle4_convert_block(input_block);

        int po0_idx = 8 * index32_v[in_block_nb] * output_page_line_size + index32_h[in_block_nb] * 32;

        for (int k = 0; k < PSMCT32_BLOCK_HEIGHT; k++) {
          int start = k * PSMCT32_BLOCK_WIDTH * 4;
          int end = start + PSMCT32_BLOCK_WIDTH * 4;
          int out_start = po0_idx + k * output_page_line_size;
          //output_page_data[out_start:out_start + PSMCT32_BLOCK_WIDTH * 4] = output_block[start:end];
          System.arraycopy(output_block, start, output_page_data, out_start, end - start);
        }
      }
    }

    return output_page_data;
  }

  public static int[] _ps2_swizzle4(int[] input_data, int width, int height) {
    int[] output_data = new int[input_data.length];
    int n_page_w = (width - 1) / PSMT4_PAGE_WIDTH + 1;
    int n_page_h = (height - 1) / PSMT4_PAGE_HEIGHT + 1;
    int n_page4_width_byte = PSMT4_PAGE_WIDTH / 2;
    int n_page32_width_byte = PSMCT32_PAGE_WIDTH * 4;

    int n_input_width_byte = 0;
    int n_output_height = 0;
    if (n_page_w == 1) {
      n_input_width_byte = width / 2;
      n_output_height = width / 4;
    }
    else {
      n_input_width_byte = n_page4_width_byte;
      n_output_height = PSMCT32_PAGE_HEIGHT;
    }

    int n_input_height = 0;
    int n_output_width_byte = 0;
    if (n_page_h == 1) {
      n_input_height = height;
      n_output_width_byte = height * 2;
    }
    else {
      n_input_height = PSMT4_PAGE_HEIGHT;
      n_output_width_byte = n_page32_width_byte;
    }

    int[] input_page = new int[(PSMT4_PAGE_WIDTH / 2 * PSMT4_PAGE_HEIGHT)];

    for (int i = 0; i < n_page_h; i++) {
      for (int j = 0; j < n_page_w; j++) {
        for (int k = 0; k < n_input_height; k++) {
          int src_idx = (n_input_width_byte * n_input_height) * n_page_w * i + n_input_width_byte * j + k * n_input_width_byte * n_page_w;
          int dst_idx = k * n_page4_width_byte;
          //input_page[dst_idx:dst_idx + n_input_width_byte] = input_data[src_idx:src_idx + n_input_width_byte];
          System.arraycopy(input_data, src_idx, input_page, dst_idx, ((src_idx + n_input_width_byte) - src_idx));
        }

        int[] output_page = _swizzle4_convert_page(PSMT4_PAGE_WIDTH, PSMT4_PAGE_HEIGHT, input_page);

        for (int k = 0; k < n_output_height; k++) {
          int src_idx = k * n_page32_width_byte;
          int dst_idx = (n_output_width_byte * n_output_height) * n_page_w * i + n_output_width_byte * j + k * n_output_width_byte * n_page_w;
          //output_data[dst_idx:dst_idx + n_output_width_byte] = output_page[src_idx:src_idx + n_output_width_byte];
          System.arraycopy(output_page, src_idx, output_data, dst_idx, ((src_idx + n_output_width_byte) - src_idx));
        }
      }
    }

    return output_data;
  }

}
