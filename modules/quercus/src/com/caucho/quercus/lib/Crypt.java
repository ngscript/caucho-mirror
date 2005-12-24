/*
 * This implementation of the unix crypt function is based on
 * Eric Young's DES implementation from OpenSSL.
 */

package com.caucho.quercus.lib;

/**
 * Crypt.
 */
public class Crypt {
  public static String crypt(String keyString, String salt)
  {
    int len = keyString.length();
    
    if (8 < len)
      len = 8;

    int swap0 = toSalt(salt.charAt(0)) << 2;
    int swap1 = toSalt(salt.charAt(1)) << 6;

    char result[] = new char[12];
    byte key[] = new byte[8];

    int i;
    for (i = 0; i < len; i++) {
      char ch = keyString.charAt(i);

      key[i] = (byte) (ch << 1);
    }

    for (; i < 8; i++) {
      key[i] = 0;
    }

    int []keySchedule = new int[32];

    setKeySchedule(key, keySchedule);

    long value = encrypt(keySchedule, swap0, swap1);

    return resultToString(salt, value);
  }

  private static int toSalt(char ch)
  {
    if (ch < 0x3a)
      return ch - 0x2e;
    else if (ch < 0x5b)
      return ch - 0x3a + 0x05;
    else
      return ch - 0x5b + 0x20;
  }
  
  private static void setKeySchedule(byte []key, int []schedule)
  {
    int c = ((key[0] & 0xff) |
	     ((key[1] & 0xff) << 8) |
	     ((key[2] & 0xff) << 16) |
	     ((key[3] & 0xff) << 24));
    
    int d = ((key[4] & 0xff) |
	     ((key[5] & 0xff) << 8) |
	     ((key[6] & 0xff) << 16) |
	     ((key[7] & 0xff) << 24));


    // PERM_OP (d,c,t,4,0x0f0f0f0fL);
    int temp = ((d >>> 4) ^ c) & 0x0f0f0f0f;
    c ^= temp;
    d ^= temp << 4;

    // HPERM_OP(c,t,-2,0xcccc0000L);
    temp = ((c << 18) ^ c) & 0xcccc0000;
    c ^= temp ^ (temp >>> 18);
    
    // HPERM_OP(d,t,-2,0xcccc0000L);
    temp = ((d << 18) ^ d) & 0xcccc0000;
    d ^= temp ^ (temp >>> 18);
    
    // PERM_OP (d,c,t,1,0x55555555L);
    temp = ((d >>> 1) ^ c) & 0x55555555;
    c ^= temp;
    d ^= temp << 1;
    
    // PERM_OP (c,d,t,8,0x00ff00ffL);
    temp = ((c >>> 8) ^ d) & 0x00ff00ff;
    d ^= temp;
    c ^= temp << 8;
    
    // PERM_OP (d,c,t,1,0x55555555L);
    temp = ((d >>> 1) ^ c) & 0x55555555;
    c ^= temp;
    d ^= temp << 1;
    
    d =	(((d & 0x000000ff) << 16) |
	 (d & 0x0000ff00) |
	 ((d & 0x00ff0000) >>> 16) |
	 ((c & 0xf0000000) >>> 4));
    
    c &= 0x0fffffffL;

    int k = 0;
    for (int i = 0; i < 16; i++) {
      if (KEY_SHIFTS[i]) {
	c = (c >> 2) | (c << 26);
	d = (d >> 2) | (d << 26);
      }
      else {
	c = (c >> 1) | (c << 27);
	d = (d >> 1) | (d << 27);
      }
	
      c &= 0x0fffffff;
      d &= 0x0fffffff;
	
      int s = (skb_0[ (c      ) & 0x3f] |
	       skb_1[((c >> 6) & 0x03) |
		     ((c >> 7) & 0x3c)] |
	       skb_2[((c >> 13) & 0x0f) |
		     ((c >> 14) & 0x30)] |
	       skb_3[((c >> 20) & 0x01) |
		     ((c >> 21) & 0x06) |
		     ((c >> 22) & 0x38)]);
	
      int t = (skb_4[ (d    ) & 0x3f] |
	       skb_5[((d >> 7) & 0x03) |
		     ((d >> 8) & 0x3c)] |
	       skb_6[ (d >> 15) & 0x3f] |
	       skb_7[((d >> 21) & 0x0f) |
		     ((d >> 22) & 0x30)]);

      int t2 = (t << 16) | (s & 0x0000ffff);
	
      schedule[k++] = rotate(t2, 30);

      t2 = (s >>> 16) | (t & 0xffff0000);
	
      schedule[k++] = rotate(t2, 26);
    }
  }
  
  private static long encrypt(int []key, int swap0, int swap1)
  {
    int l = 0;
    int r = 0;
    int temp;

    for (int j = 0; j < 25; j++) {
      for (int i = 0; i < 32; i += 8) {
	l = encrypt(l, r, key[i + 0], key[i + 1], swap0, swap1);
	r = encrypt(r, l, key[i + 2], key[i + 3], swap0, swap1);
	l = encrypt(l, r, key[i + 4], key[i + 5], swap0, swap1);
	r = encrypt(r, l, key[i + 6], key[i + 7], swap0, swap1);
      }

      temp = l;
      l = r;
      r = temp;
    }

    // l=ROTATE(l,3)&0xffffffffL;
    l = rotate(l, 3);
    // r=ROTATE(r,3)&0xffffffffL;
    r = rotate(r, 3);

    // PERM_OP(l,r,t, 1,0x55555555L);
    temp = ((l >>> 1) ^ r) & 0x55555555;
    r ^= temp;
    l ^= temp << 1;
    
    // PERM_OP(r,l,t, 8,0x00ff00ffL);
    temp = ((r >>> 8) ^ l) & 0x00ff00ff;
    l ^= temp;
    r ^= temp << 8;
    
    // PERM_OP(l,r,t, 2,0x33333333L);
    temp = ((l >>> 2) ^ r) & 0x33333333;
    r ^= temp;
    l ^= temp << 2;
    
    // PERM_OP(r,l,t,16,0x0000ffffL);
    temp = ((r >>> 16) ^ l) & 0x0000ffff;
    l ^= temp;
    r ^= temp << 16;
    
    // PERM_OP(l,r,t, 4,0x0f0f0f0fL);
    temp = ((l >>> 4) ^ r) & 0x0f0f0f0f;
    r ^= temp;
    l ^= temp << 4;

    return ((long) l << 32L) + ((long) r & 0xffffffffL);
  }

  private static int encrypt(int l, int r,
			     int key0, int key1,
			     int swap0, int swap1)
  {
    int t = r ^ (r >>> 16);
    int x = t & swap0;
    int y = t & swap1;

    x ^= r ^ key0 ^ (x << 16);
    y ^= r ^ key1 ^ (y << 16);

    y = rotate(y, 4);

    l ^= (des_0[(x >>  2) & 0x3f] ^
	  des_2[(x >> 10) & 0x3f] ^
	  des_4[(x >> 18) & 0x3f] ^
	  des_6[(x >> 26) & 0x3f] ^
	  des_1[(y >>  2) & 0x3f] ^
	  des_3[(y >> 10) & 0x3f] ^
	  des_5[(y >> 18) & 0x3f] ^
	  des_7[(y >> 26) & 0x3f]);

    return l;
  }
  
  private static int rotate(int v, int n)
  {
    return (v >>> n) + (v << (32 - n));
  }

  private static String resultToString(String salt, long v)
  {
    StringBuilder sb = new StringBuilder();

    sb.append(salt);

    v = (((v & 0x00000000000000ffL) << 56) |
	 ((v & 0x000000000000ff00L) << 40) |
	 ((v & 0x0000000000ff0000L) << 24) |
	 ((v & 0x00000000ff000000L) << 8) |
	 ((v & 0x000000ff00000000L) >>> 8) |
	 ((v & 0x0000ff0000000000L) >>> 24) |
	 ((v & 0x00ff000000000000L) >>> 40) |
	 ((v & 0xff00000000000000L) >>> 56));
    
    sb.append(resultToChar(v >> 58));
    sb.append(resultToChar(v >> 52));
    sb.append(resultToChar(v >> 46));
    sb.append(resultToChar(v >> 40));
    sb.append(resultToChar(v >> 34));
    sb.append(resultToChar(v >> 28));
    sb.append(resultToChar(v >> 22));
    sb.append(resultToChar(v >> 16));
    sb.append(resultToChar(v >> 10));
    sb.append(resultToChar(v >> 4));
    sb.append(resultToChar(v << 2));
    
    return sb.toString();
  }

  private static char resultToChar(long result)
  {
    int v = (int) (result & 0x3f);

    if (v < 0x0c)
      return (char) (v + 0x2e);
    else if (v < 0x26)
      return (char) (v + 0x41 - 0x0c);
    else
      return (char) (v + 0x61 - 0x26);
  }

  private final static boolean []KEY_SHIFTS = new boolean[] {
    false, false, true, true, true, true, true, true,
    false, true,  true, true, true, true, true, false
  };

  private final static int []REVERSE_BITS = new int[] {
    0x00, 0x20, 0x10, 0x30, 0x08, 0x28, 0x18, 0x38,
    0x04, 0x24, 0x14, 0x34, 0x0c, 0x2c, 0x1c, 0x3c,
    0x02, 0x22, 0x12, 0x32, 0x0a, 0x2a, 0x1a, 0x3a,
    0x06, 0x26, 0x16, 0x36, 0x0e, 0x2e, 0x1e, 0x3e,
    
    0x01, 0x21, 0x11, 0x31, 0x09, 0x29, 0x19, 0x39,
    0x05, 0x25, 0x15, 0x35, 0x0d, 0x2d, 0x1d, 0x3d,
    0x03, 0x23, 0x13, 0x33, 0x0b, 0x2b, 0x1b, 0x3b,
    0x07, 0x27, 0x17, 0x37, 0x0f, 0x2f, 0x1f, 0x3f,
  };

  private static final int []des_0 = new int[] {
    0x02080800, 0x00080000, 0x02000002, 0x02080802,
    0x02000000, 0x00080802, 0x00080002, 0x02000002,
    0x00080802, 0x02080800, 0x02080000, 0x00000802,
    0x02000802, 0x02000000, 0x00000000, 0x00080002,
    0x00080000, 0x00000002, 0x02000800, 0x00080800,
    0x02080802, 0x02080000, 0x00000802, 0x02000800,
    0x00000002, 0x00000800, 0x00080800, 0x02080002,
    0x00000800, 0x02000802, 0x02080002, 0x00000000,
    0x00000000, 0x02080802, 0x02000800, 0x00080002,
    0x02080800, 0x00080000, 0x00000802, 0x02000800,
    0x02080002, 0x00000800, 0x00080800, 0x02000002,
    0x00080802, 0x00000002, 0x02000002, 0x02080000,
    0x02080802, 0x00080800, 0x02080000, 0x02000802,
    0x02000000, 0x00000802, 0x00080002, 0x00000000,
    0x00080000, 0x02000000, 0x02000802, 0x02080800,
    0x00000002, 0x02080002, 0x00000800, 0x00080802
  };
  
  private static final int []des_1 = new int[] {
    0x40108010, 0x00000000, 0x00108000, 0x40100000,
    0x40000010, 0x00008010, 0x40008000, 0x00108000,
    0x00008000, 0x40100010, 0x00000010, 0x40008000,
    0x00100010, 0x40108000, 0x40100000, 0x00000010,
    0x00100000, 0x40008010, 0x40100010, 0x00008000,
    0x00108010, 0x40000000, 0x00000000, 0x00100010,
    0x40008010, 0x00108010, 0x40108000, 0x40000010,
    0x40000000, 0x00100000, 0x00008010, 0x40108010,
    0x00100010, 0x40108000, 0x40008000, 0x00108010,
    0x40108010, 0x00100010, 0x40000010, 0x00000000,
    0x40000000, 0x00008010, 0x00100000, 0x40100010,
    0x00008000, 0x40000000, 0x00108010, 0x40008010,
    0x40108000, 0x00008000, 0x00000000, 0x40000010,
    0x00000010, 0x40108010, 0x00108000, 0x40100000,
    0x40100010, 0x00100000, 0x00008010, 0x40008000,
    0x40008010, 0x00000010, 0x40100000, 0x00108000,
  };

  private static final int []des_2 = new int[] {
    0x04000001, 0x04040100, 0x00000100, 0x04000101,
    0x00040001, 0x04000000, 0x04000101, 0x00040100,
    0x04000100, 0x00040000, 0x04040000, 0x00000001,
    0x04040101, 0x00000101, 0x00000001, 0x04040001,
    0x00000000, 0x00040001, 0x04040100, 0x00000100,
    0x00000101, 0x04040101, 0x00040000, 0x04000001,
    0x04040001, 0x04000100, 0x00040101, 0x04040000,
    0x00040100, 0x00000000, 0x04000000, 0x00040101,
    0x04040100, 0x00000100, 0x00000001, 0x00040000,
    0x00000101, 0x00040001, 0x04040000, 0x04000101,
    0x00000000, 0x04040100, 0x00040100, 0x04040001,
    0x00040001, 0x04000000, 0x04040101, 0x00000001,
    0x00040101, 0x04000001, 0x04000000, 0x04040101,
    0x00040000, 0x04000100, 0x04000101, 0x00040100,
    0x04000100, 0x00000000, 0x04040001, 0x00000101,
    0x04000001, 0x00040101, 0x00000100, 0x04040000,
  };

  private static final int []des_3 = new int[] {
    0x00401008, 0x10001000, 0x00000008, 0x10401008,
    0x00000000, 0x10400000, 0x10001008, 0x00400008,
    0x10401000, 0x10000008, 0x10000000, 0x00001008,
    0x10000008, 0x00401008, 0x00400000, 0x10000000,
    0x10400008, 0x00401000, 0x00001000, 0x00000008,
    0x00401000, 0x10001008, 0x10400000, 0x00001000,
    0x00001008, 0x00000000, 0x00400008, 0x10401000,
    0x10001000, 0x10400008, 0x10401008, 0x00400000,
    0x10400008, 0x00001008, 0x00400000, 0x10000008,
    0x00401000, 0x10001000, 0x00000008, 0x10400000,
    0x10001008, 0x00000000, 0x00001000, 0x00400008,
    0x00000000, 0x10400008, 0x10401000, 0x00001000,
    0x10000000, 0x10401008, 0x00401008, 0x00400000,
    0x10401008, 0x00000008, 0x10001000, 0x00401008,
    0x00400008, 0x00401000, 0x10400000, 0x10001008,
    0x00001008, 0x10000000, 0x10000008, 0x10401000,
  };
  
  private static final int []des_4 = new int[] {
    0x08000000, 0x00010000, 0x00000400, 0x08010420,
    0x08010020, 0x08000400, 0x00010420, 0x08010000,
    0x00010000, 0x00000020, 0x08000020, 0x00010400,
    0x08000420, 0x08010020, 0x08010400, 0x00000000,
    0x00010400, 0x08000000, 0x00010020, 0x00000420,
    0x08000400, 0x00010420, 0x00000000, 0x08000020,
    0x00000020, 0x08000420, 0x08010420, 0x00010020,
    0x08010000, 0x00000400, 0x00000420, 0x08010400,
    0x08010400, 0x08000420, 0x00010020, 0x08010000,
    0x00010000, 0x00000020, 0x08000020, 0x08000400,
    0x08000000, 0x00010400, 0x08010420, 0x00000000,
    0x00010420, 0x08000000, 0x00000400, 0x00010020,
    0x08000420, 0x00000400, 0x00000000, 0x08010420,
    0x08010020, 0x08010400, 0x00000420, 0x00010000,
    0x00010400, 0x08010020, 0x08000400, 0x00000420,
    0x00000020, 0x00010420, 0x08010000, 0x08000020,
  };

  private static final int []des_5 = new int[] {
    0x80000040, 0x00200040, 0x00000000, 0x80202000,
    0x00200040, 0x00002000, 0x80002040, 0x00200000,
    0x00002040, 0x80202040, 0x00202000, 0x80000000,
    0x80002000, 0x80000040, 0x80200000, 0x00202040,
    0x00200000, 0x80002040, 0x80200040, 0x00000000,
    0x00002000, 0x00000040, 0x80202000, 0x80200040,
    0x80202040, 0x80200000, 0x80000000, 0x00002040,
    0x00000040, 0x00202000, 0x00202040, 0x80002000,
    0x00002040, 0x80000000, 0x80002000, 0x00202040,
    0x80202000, 0x00200040, 0x00000000, 0x80002000,
    0x80000000, 0x00002000, 0x80200040, 0x00200000,
    0x00200040, 0x80202040, 0x00202000, 0x00000040,
    0x80202040, 0x00202000, 0x00200000, 0x80002040,
    0x80000040, 0x80200000, 0x00202040, 0x00000000,
    0x00002000, 0x80000040, 0x80002040, 0x80202000,
    0x80200000, 0x00002040, 0x00000040, 0x80200040,
  };
  
  private static final int []des_6 = new int[] {
    0x00004000, 0x00000200, 0x01000200, 0x01000004,
    0x01004204, 0x00004004, 0x00004200, 0x00000000,
    0x01000000, 0x01000204, 0x00000204, 0x01004000,
    0x00000004, 0x01004200, 0x01004000, 0x00000204,
    0x01000204, 0x00004000, 0x00004004, 0x01004204,
    0x00000000, 0x01000200, 0x01000004, 0x00004200,
    0x01004004, 0x00004204, 0x01004200, 0x00000004,
    0x00004204, 0x01004004, 0x00000200, 0x01000000,
    0x00004204, 0x01004000, 0x01004004, 0x00000204,
    0x00004000, 0x00000200, 0x01000000, 0x01004004,
    0x01000204, 0x00004204, 0x00004200, 0x00000000,
    0x00000200, 0x01000004, 0x00000004, 0x01000200,
    0x00000000, 0x01000204, 0x01000200, 0x00004200,
    0x00000204, 0x00004000, 0x01004204, 0x01000000,
    0x01004200, 0x00000004, 0x00004004, 0x01004204,
    0x01000004, 0x01004200, 0x01004000, 0x00004004,
  };
  
  private static final int []des_7 = new int[] {
    0x20800080, 0x20820000, 0x00020080, 0x00000000,
    0x20020000, 0x00800080, 0x20800000, 0x20820080,
    0x00000080, 0x20000000, 0x00820000, 0x00020080,
    0x00820080, 0x20020080, 0x20000080, 0x20800000,
    0x00020000, 0x00820080, 0x00800080, 0x20020000,
    0x20820080, 0x20000080, 0x00000000, 0x00820000,
    0x20000000, 0x00800000, 0x20020080, 0x20800080,
    0x00800000, 0x00020000, 0x20820000, 0x00000080,
    0x00800000, 0x00020000, 0x20000080, 0x20820080,
    0x00020080, 0x20000000, 0x00000000, 0x00820000,
    0x20800080, 0x20020080, 0x20020000, 0x00800080,
    0x20820000, 0x00000080, 0x00800080, 0x20020000,
    0x20820080, 0x00800000, 0x20800000, 0x20000080,
    0x00820000, 0x00020080, 0x20020080, 0x20800000,
    0x00000080, 0x20820000, 0x00820080, 0x00000000,
    0x20000000, 0x20800080, 0x00020000, 0x00820080,
  };

  private static final int []skb_0 = new int[] {
    0x00000000,0x00000010,0x20000000,0x20000010,
    0x00010000,0x00010010,0x20010000,0x20010010,
    0x00000800,0x00000810,0x20000800,0x20000810,
    0x00010800,0x00010810,0x20010800,0x20010810,
    0x00000020,0x00000030,0x20000020,0x20000030,
    0x00010020,0x00010030,0x20010020,0x20010030,
    0x00000820,0x00000830,0x20000820,0x20000830,
    0x00010820,0x00010830,0x20010820,0x20010830,
    0x00080000,0x00080010,0x20080000,0x20080010,
    0x00090000,0x00090010,0x20090000,0x20090010,
    0x00080800,0x00080810,0x20080800,0x20080810,
    0x00090800,0x00090810,0x20090800,0x20090810,
    0x00080020,0x00080030,0x20080020,0x20080030,
    0x00090020,0x00090030,0x20090020,0x20090030,
    0x00080820,0x00080830,0x20080820,0x20080830,
    0x00090820,0x00090830,0x20090820,0x20090830,
  };
  
  private static final int []skb_1 = new int[] {
    0x00000000,0x02000000,0x00002000,0x02002000,
    0x00200000,0x02200000,0x00202000,0x02202000,
    0x00000004,0x02000004,0x00002004,0x02002004,
    0x00200004,0x02200004,0x00202004,0x02202004,
    0x00000400,0x02000400,0x00002400,0x02002400,
    0x00200400,0x02200400,0x00202400,0x02202400,
    0x00000404,0x02000404,0x00002404,0x02002404,
    0x00200404,0x02200404,0x00202404,0x02202404,
    0x10000000,0x12000000,0x10002000,0x12002000,
    0x10200000,0x12200000,0x10202000,0x12202000,
    0x10000004,0x12000004,0x10002004,0x12002004,
    0x10200004,0x12200004,0x10202004,0x12202004,
    0x10000400,0x12000400,0x10002400,0x12002400,
    0x10200400,0x12200400,0x10202400,0x12202400,
    0x10000404,0x12000404,0x10002404,0x12002404,
    0x10200404,0x12200404,0x10202404,0x12202404,
  };
  
  private static final int []skb_2 = new int[] {
    0x00000000,0x00000001,0x00040000,0x00040001,
    0x01000000,0x01000001,0x01040000,0x01040001,
    0x00000002,0x00000003,0x00040002,0x00040003,
    0x01000002,0x01000003,0x01040002,0x01040003,
    0x00000200,0x00000201,0x00040200,0x00040201,
    0x01000200,0x01000201,0x01040200,0x01040201,
    0x00000202,0x00000203,0x00040202,0x00040203,
    0x01000202,0x01000203,0x01040202,0x01040203,
    0x08000000,0x08000001,0x08040000,0x08040001,
    0x09000000,0x09000001,0x09040000,0x09040001,
    0x08000002,0x08000003,0x08040002,0x08040003,
    0x09000002,0x09000003,0x09040002,0x09040003,
    0x08000200,0x08000201,0x08040200,0x08040201,
    0x09000200,0x09000201,0x09040200,0x09040201,
    0x08000202,0x08000203,0x08040202,0x08040203,
    0x09000202,0x09000203,0x09040202,0x09040203,
  };

  private static final int []skb_3 = new int[] {
    0x00000000,0x00100000,0x00000100,0x00100100,
    0x00000008,0x00100008,0x00000108,0x00100108,
    0x00001000,0x00101000,0x00001100,0x00101100,
    0x00001008,0x00101008,0x00001108,0x00101108,
    0x04000000,0x04100000,0x04000100,0x04100100,
    0x04000008,0x04100008,0x04000108,0x04100108,
    0x04001000,0x04101000,0x04001100,0x04101100,
    0x04001008,0x04101008,0x04001108,0x04101108,
    0x00020000,0x00120000,0x00020100,0x00120100,
    0x00020008,0x00120008,0x00020108,0x00120108,
    0x00021000,0x00121000,0x00021100,0x00121100,
    0x00021008,0x00121008,0x00021108,0x00121108,
    0x04020000,0x04120000,0x04020100,0x04120100,
    0x04020008,0x04120008,0x04020108,0x04120108,
    0x04021000,0x04121000,0x04021100,0x04121100,
    0x04021008,0x04121008,0x04021108,0x04121108,
  };

  private static final int []skb_4 = new int[] {
    0x00000000,0x10000000,0x00010000,0x10010000,
    0x00000004,0x10000004,0x00010004,0x10010004,
    0x20000000,0x30000000,0x20010000,0x30010000,
    0x20000004,0x30000004,0x20010004,0x30010004,
    0x00100000,0x10100000,0x00110000,0x10110000,
    0x00100004,0x10100004,0x00110004,0x10110004,
    0x20100000,0x30100000,0x20110000,0x30110000,
    0x20100004,0x30100004,0x20110004,0x30110004,
    0x00001000,0x10001000,0x00011000,0x10011000,
    0x00001004,0x10001004,0x00011004,0x10011004,
    0x20001000,0x30001000,0x20011000,0x30011000,
    0x20001004,0x30001004,0x20011004,0x30011004,
    0x00101000,0x10101000,0x00111000,0x10111000,
    0x00101004,0x10101004,0x00111004,0x10111004,
    0x20101000,0x30101000,0x20111000,0x30111000,
    0x20101004,0x30101004,0x20111004,0x30111004,
  };

  private static final int []skb_5 = new int[] {
    0x00000000,0x08000000,0x00000008,0x08000008,
    0x00000400,0x08000400,0x00000408,0x08000408,
    0x00020000,0x08020000,0x00020008,0x08020008,
    0x00020400,0x08020400,0x00020408,0x08020408,
    0x00000001,0x08000001,0x00000009,0x08000009,
    0x00000401,0x08000401,0x00000409,0x08000409,
    0x00020001,0x08020001,0x00020009,0x08020009,
    0x00020401,0x08020401,0x00020409,0x08020409,
    0x02000000,0x0A000000,0x02000008,0x0A000008,
    0x02000400,0x0A000400,0x02000408,0x0A000408,
    0x02020000,0x0A020000,0x02020008,0x0A020008,
    0x02020400,0x0A020400,0x02020408,0x0A020408,
    0x02000001,0x0A000001,0x02000009,0x0A000009,
    0x02000401,0x0A000401,0x02000409,0x0A000409,
    0x02020001,0x0A020001,0x02020009,0x0A020009,
    0x02020401,0x0A020401,0x02020409,0x0A020409,
  };

  private static final int []skb_6 = new int[] {
    0x00000000,0x00000100,0x00080000,0x00080100,
    0x01000000,0x01000100,0x01080000,0x01080100,
    0x00000010,0x00000110,0x00080010,0x00080110,
    0x01000010,0x01000110,0x01080010,0x01080110,
    0x00200000,0x00200100,0x00280000,0x00280100,
    0x01200000,0x01200100,0x01280000,0x01280100,
    0x00200010,0x00200110,0x00280010,0x00280110,
    0x01200010,0x01200110,0x01280010,0x01280110,
    0x00000200,0x00000300,0x00080200,0x00080300,
    0x01000200,0x01000300,0x01080200,0x01080300,
    0x00000210,0x00000310,0x00080210,0x00080310,
    0x01000210,0x01000310,0x01080210,0x01080310,
    0x00200200,0x00200300,0x00280200,0x00280300,
    0x01200200,0x01200300,0x01280200,0x01280300,
    0x00200210,0x00200310,0x00280210,0x00280310,
    0x01200210,0x01200310,0x01280210,0x01280310,
  };

  private static final int []skb_7 = new int[] {
    0x00000000,0x04000000,0x00040000,0x04040000,
    0x00000002,0x04000002,0x00040002,0x04040002,
    0x00002000,0x04002000,0x00042000,0x04042000,
    0x00002002,0x04002002,0x00042002,0x04042002,
    0x00000020,0x04000020,0x00040020,0x04040020,
    0x00000022,0x04000022,0x00040022,0x04040022,
    0x00002020,0x04002020,0x00042020,0x04042020,
    0x00002022,0x04002022,0x00042022,0x04042022,
    0x00000800,0x04000800,0x00040800,0x04040800,
    0x00000802,0x04000802,0x00040802,0x04040802,
    0x00002800,0x04002800,0x00042800,0x04042800,
    0x00002802,0x04002802,0x00042802,0x04042802,
    0x00000820,0x04000820,0x00040820,0x04040820,
    0x00000822,0x04000822,0x00040822,0x04040822,
    0x00002820,0x04002820,0x00042820,0x04042820,
    0x00002822,0x04002822,0x00042822,0x04042822,
  };
}

