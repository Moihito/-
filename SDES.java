/**
 * SDES.java
 * S-DES 算法核心实现（密钥扩展、加密、解密）
 *
 * Java 8
 * 全部注释为中文，按题目给定的置换表、S-box、扩展/置换实现
 */
public class SDES {
    // 使用题目中的置换表（以 1-based 形式写出，调用时减 1）
    private static final int[] P10 = {3,5,2,7,4,10,1,9,8,6};
    private static final int[] P8  = {6,3,7,4,8,5,10,9};
    private static final int[] IP  = {2,6,3,1,4,8,5,7};
    private static final int[] IP_INV = {4,1,3,5,7,2,8,6};
    private static final int[] EP = {4,1,2,3,2,3,4,1}; // 扩展置换 E/P
    private static final int[] P4 = {2,4,3,1}; // SPBox / P4

    // S盒（按题目给定）
    private static final int[][] SBOX1 = {
            {1,0,3,2},
            {3,2,1,0},
            {0,2,1,3},
            {3,1,0,2}
    };
    private static final int[][] SBOX2 = {
            {0,1,2,3},
            {2,3,1,0},
            {3,0,1,2},
            {2,1,0,3}
    };

    // ---------- 公共接口 ----------
    /**
     * 生成子密钥 k1、k2（每个为 8-bit 的二进制字符串）
     * @param key10 10-bit 字符串 "1010010010"
     * @return 长度为 2 的字符串数组 {k1, k2}
     */
    public static String[] generateSubKeys(String key10) {
        if (key10 == null || key10.length() != 10 || !key10.matches("[01]{10}"))
            throw new IllegalArgumentException("密钥必须为10位二进制字符串");

        String p10 = permute(key10, P10);
        String left = p10.substring(0,5);
        String right = p10.substring(5);

        // 左循环移位 1
        left = leftShift(left, 1);
        right = leftShift(right, 1);
        String k1 = permute(left + right, P8);

        // 在第一次左移的基础上再左移 2
        left = leftShift(left, 2);
        right = leftShift(right, 2);
        String k2 = permute(left + right, P8);

        return new String[] { k1, k2 };
    }

    /**
     * 加密 8-bit 分组（字符串形式），返回 8-bit 字符串
     */
    public static String encryptBlock(String plain8, String key10) {
        String[] keys = generateSubKeys(key10);
        return sdesEncrypt(plain8, keys[0], keys[1]);
    }

    /**
     * 解密 8-bit 分组（字符串形式），返回 8-bit 字符串
     */
    public static String decryptBlock(String cipher8, String key10) {
        String[] keys = generateSubKeys(key10);
        // 解密：子密钥顺序反过来
        return sdesEncrypt(cipher8, keys[1], keys[0]);
    }

    // ---------- 内部实现 ----------

    // 完整的两轮 S-DES 流程，参数为两个子密钥（k1, k2）
    private static String sdesEncrypt(String input8, String k1, String k2) {
        if (input8 == null || input8.length() != 8 || !input8.matches("[01]{8}"))
            throw new IllegalArgumentException("输入分组必须为8位二进制字符串");

        // 初始置换 IP
        String ip = permute(input8, IP);
        String L = ip.substring(0,4);
        String R = ip.substring(4);

        // 轮 1
        String f1 = fk(L, R, k1); // 返回 8 位 (L'|R)
        // 交换 SW
        String swapped = f1.substring(4) + f1.substring(0,4);
        // 轮 2
        String f2 = fk(swapped.substring(0,4), swapped.substring(4), k2);
        // 最后逆置换
        String preOutput = f2;
        String output = permute(preOutput, IP_INV);
        return output;
    }

    // 轮函数 fk：输入 L(4) R(4) 和子密钥（8），返回新的 8 位 (L'|R)
    private static String fk(String L, String R, String subkey) {
        // E/P 扩展 R -> 8 位
        String ep = permute(R, EP);
        // 与子密钥异或
        String xor = xorBits(ep, subkey);
        // 分为左右 4 位，输入到两个 S-box
        String left4 = xor.substring(0,4);
        String right4 = xor.substring(4);
        String s1out = sboxLookup(left4, SBOX1);
        String s2out = sboxLookup(right4, SBOX2);
        String combined = s1out + s2out; // 4 位
        String p4 = permute(combined, P4);
        String leftXor = xorBits(L, p4); // 4 位
        // 返回 L' | R (注意：这里返回 L' then original R)
        return leftXor + R;
    }

    // S-box 查表，输入 4-bit，输出 2-bit 字符串
    private static String sboxLookup(String in4, int[][] sbox) {
        // 行由 b0 b3 构成，列由 b1 b2 构成
        int row = Integer.parseInt("" + in4.charAt(0) + in4.charAt(3), 2);
        int col = Integer.parseInt(in4.substring(1,3), 2);
        int val = sbox[row][col]; // 0..3
        return String.format("%2s", Integer.toBinaryString(val)).replace(' ', '0');
    }

    // 二进制字符串异或（长度相同）
    private static String xorBits(String a, String b) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < a.length(); ++i) {
            sb.append(a.charAt(i) == b.charAt(i) ? '0' : '1');
        }
        return sb.toString();
    }

    // 置换函数（table 中为题目给出的 1-based 索引）
    private static String permute(String bits, int[] table) {
        StringBuilder sb = new StringBuilder();
        for (int idx : table) {
            sb.append(bits.charAt(idx - 1));
        }
        return sb.toString();
    }

    // 循环左移
    private static String leftShift(String s, int n) {
        return s.substring(n) + s.substring(0, n);
    }

    // ---------- 辅助：将字节值转 8-bit 二进制（用于 ASCII 模式） ----------
    /**
     * 将 0..255 的整数转为 8 位二进制字符串
     */
    public static String byteTo8Bits(int v) {
        v = v & 0xFF;
        String s = Integer.toBinaryString(0x100 | v).substring(1);
        return s;
    }
}
