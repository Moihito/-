/**
 * SDES_GUI.java
 * Swing 中文界面：支持加密/解密/ASCII 模式/暴力破解/封闭测试
 *
 * 依赖 SDES.java（同一目录下）
 *
 * 编译与运行（Java 8）：
 *   javac SDES.java SDES_GUI.java Main.java
 *   java Main
 */
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;

public class SDES_GUI extends JFrame {
    // 界面控件（中文）
    private JTextField tfPlain;    // 明文或 ASCII 输入（取决于模式）
    private JTextField tfKey;      // 10-bit 密钥输入
    private JTextField tfCipher;   // 密文框（在 ASCII 模式下为连续 8-bit 块）
    private JCheckBox cbAsciiMode; // ASCII 模式勾选框
    private JButton btnEncrypt, btnDecrypt, btnAsciiEncrypt, btnAsciiDecrypt;
    private JButton btnBruteForce, btnClosedTest;
    private JTextArea taLog;
    private JTextField tfThreads;  // 暴力破解线程数输入

    public SDES_GUI() {
        setTitle("S-DES 加密系统");
        setSize(820, 620);               // 加大窗口防止按钮截断
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        initComponents();
    }

    private void initComponents() {
        // 顶部输入区
        JPanel pTop = new JPanel(new GridBagLayout());
        pTop.setBorder(BorderFactory.createTitledBorder("输入 / 设置"));
        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(6,6,6,6);
        g.fill = GridBagConstraints.HORIZONTAL;

        g.gridx = 0; g.gridy = 0; pTop.add(new JLabel("明文（8-bit 二进制或 ASCII 文本）:"), g);
        g.gridx = 1; g.gridy = 0; tfPlain = new JTextField(36); pTop.add(tfPlain, g);

        g.gridx = 0; g.gridy = 1; pTop.add(new JLabel("密钥（10-bit 二进制）:"), g);
        g.gridx = 1; g.gridy = 1; tfKey = new JTextField(20); pTop.add(tfKey, g);

        g.gridx = 0; g.gridy = 2; pTop.add(new JLabel("密文（8-bit 块串，ASCII 模式为连续块）:"), g);
        g.gridx = 1; g.gridy = 2; tfCipher = new JTextField(36); pTop.add(tfCipher, g);

        g.gridx = 0; g.gridy = 3; pTop.add(new JLabel("线程数（暴力破解用，可选）:"), g);
        g.gridx = 1; g.gridy = 3; tfThreads = new JTextField(String.valueOf(Runtime.getRuntime().availableProcessors()), 6); pTop.add(tfThreads, g);

        g.gridx = 0; g.gridy = 4; cbAsciiMode = new JCheckBox("ASCII 模式（逐字符为 8-bit 分组）"); pTop.add(cbAsciiMode, g);
        getContentPane().add(pTop, BorderLayout.NORTH);

        // 中间功能按钮区（使用 FlowLayout + 多行按钮避免挤压）
        JPanel pButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 12));
        pButtons.setBorder(BorderFactory.createTitledBorder("操作"));

        btnEncrypt = new JButton("加密（单 8-bit 分组）");
        btnDecrypt = new JButton("解密（单 8-bit 分组）");
        btnAsciiEncrypt = new JButton("加密（ASCII 模式）");
        btnAsciiDecrypt = new JButton("解密（ASCII 模式）");
        btnBruteForce = new JButton("暴力破解（找密钥）");
        btnClosedTest = new JButton("封闭测试（检验多密钥碰撞）");

        // 把按钮放入 panel
        pButtons.add(btnEncrypt); pButtons.add(btnDecrypt);
        pButtons.add(btnAsciiEncrypt); pButtons.add(btnAsciiDecrypt);
        pButtons.add(btnBruteForce); pButtons.add(btnClosedTest);

        getContentPane().add(pButtons, BorderLayout.CENTER);

        // 下方日志输出
        taLog = new JTextArea();
        taLog.setEditable(false);
        taLog.setLineWrap(true);
        JScrollPane sp = new JScrollPane(taLog);
        sp.setBorder(BorderFactory.createTitledBorder("输出 / 日志"));
        sp.setPreferredSize(new Dimension(780, 280));
        getContentPane().add(sp, BorderLayout.SOUTH);

        // 绑定事件
        bindEvents();
    }

    private void bindEvents() {
        // 单分组加密
        btnEncrypt.addActionListener(e -> {
            try {
                if (cbAsciiMode.isSelected()) {
                    log("提示：当前勾选了 ASCII 模式，但你点击的是单分组加密（将视为单 8-bit 二进制处理）。");
                }
                String plain = tfPlain.getText().trim();
                String key = tfKey.getText().trim();
                if (!validate8Bit(plain) || !validate10Bit(key)) return;
                String cipher = SDES.encryptBlock(plain, key);
                tfCipher.setText(cipher);
                log("加密（单组）: P=" + plain + "  K=" + key + "  -> C=" + cipher);
            } catch (Exception ex) {
                log("错误: " + ex.getMessage());
            }
        });

        // 单分组解密
        btnDecrypt.addActionListener(e -> {
            try {
                String cipher = tfCipher.getText().trim();
                String key = tfKey.getText().trim();
                if (!validate8Bit(cipher) || !validate10Bit(key)) return;
                String plain = SDES.decryptBlock(cipher, key);
                tfPlain.setText(plain);
                log("解密（单组）: C=" + cipher + "  K=" + key + "  -> P=" + plain);
            } catch (Exception ex) {
                log("错误: " + ex.getMessage());
            }
        });

        // ASCII 加密（逐字符）
        btnAsciiEncrypt.addActionListener(e -> {
            try {
                String text = tfPlain.getText();
                String key = tfKey.getText().trim();
                if (!validate10Bit(key)) return;
                StringBuilder sb = new StringBuilder();
                for (char ch : text.toCharArray()) {
                    int v = (int) ch & 0xFF;
                    String b8 = SDES.byteTo8Bits(v);
                    String c8 = SDES.encryptBlock(b8, key);
                    sb.append(c8); // 直接拼接 8-bit 块，不添加分隔
                }
                tfCipher.setText(sb.toString());
                log("ASCII 加密完成，原文长度 " + text.length() + "，密文字节总长度 " + sb.length());
            } catch (Exception ex) {
                log("错误: " + ex.getMessage());
            }
        });

        // ASCII 解密（逐8-bit 块）
        btnAsciiDecrypt.addActionListener(e -> {
            try {
                String cipherConcat = tfCipher.getText().trim();
                String key = tfKey.getText().trim();
                if (!validate10Bit(key)) return;
                if (cipherConcat.length() % 8 != 0) {
                    log("错误：密文长度不是 8 的倍数，无法按字节解密。");
                    return;
                }
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < cipherConcat.length(); i += 8) {
                    String blk = cipherConcat.substring(i, i+8);
                    String plain8 = SDES.decryptBlock(blk, key);
                    int val = Integer.parseInt(plain8, 2);
                    sb.append((char) val);
                }
                tfPlain.setText(sb.toString());
                log("ASCII 解密完成，恢复文本：" + sb.toString());
            } catch (Exception ex) {
                log("错误: " + ex.getMessage());
            }
        });

        // 暴力破解（多线程）
        btnBruteForce.addActionListener(e -> {
            try {
                boolean ascii = cbAsciiMode.isSelected();
                String plainInput = tfPlain.getText().trim();
                String cipherInput = tfCipher.getText().trim();
                int threads = parseThreadCount(tfThreads.getText().trim());
                if (threads <= 0) threads = Runtime.getRuntime().availableProcessors();

                // 解析输入
                final List<String> plainBlocks = new ArrayList<>();
                final List<String> cipherBlocks = new ArrayList<>();

                if (ascii) {
                    // ASCII 模式：plainInput 是原文字符串，cipherInput 是连续 8-bit 块
                    if (cipherInput.length() % 8 != 0) {
                        log("错误：ASCII 模式下，密文长度必须为 8 的倍数。");
                        return;
                    }
                    for (char ch : plainInput.toCharArray()) {
                        plainBlocks.add(SDES.byteTo8Bits((int) ch & 0xFF));
                    }
                    // 将 cipher 按 8-bit 划分
                    for (int i=0; i<cipherInput.length(); i+=8) cipherBlocks.add(cipherInput.substring(i, i+8));
                    if (plainBlocks.size() != cipherBlocks.size()) {
                        log("错误：明文字符数与密文字节数不匹配。");
                        return;
                    }
                } else {
                    // 非 ASCII：plain 和 cipher 都应为逗号分隔的 8-bit 块或单个 8-bit
                    if (plainInput.contains(",")) {
                        for (String s : plainInput.split(",")) plainBlocks.add(s.trim());
                    } else {
                        if (!plainInput.isEmpty()) plainBlocks.add(plainInput);
                    }
                    if (cipherInput.contains(",")) {
                        for (String s : cipherInput.split(",")) cipherBlocks.add(s.trim());
                    } else {
                        if (!cipherInput.isEmpty()) cipherBlocks.add(cipherInput);
                    }
                }

                // 基本检测
                if (plainBlocks.isEmpty() || cipherBlocks.isEmpty() || plainBlocks.size() != cipherBlocks.size()) {
                    log("错误：请提供等数量的明文与密文分组（ASCII 模式下明文为字符串，密文为连续 8-bit 块）。");
                    return;
                }
                for (String s : plainBlocks) if (!validate8BitSilent(s)) { log("错误：明文块格式错误：" + s); return; }
                for (String s : cipherBlocks) if (!validate8BitSilent(s)) { log("错误：密文块格式错误：" + s); return; }

                // 异步多线程搜索
                log("开始暴力破解（多线程）... 线程数=" + threads);
                long t0 = System.currentTimeMillis();
                ExecutorService ex = Executors.newFixedThreadPool(threads);
                List<Future<List<String>>> futures = new ArrayList<>();
                final int totalKeys = 1 << 10;
                final int per = (totalKeys + threads - 1) / threads;
                for (int i = 0; i < threads; ++i) {
                    final int start = i * per;
                    final int end = Math.min(totalKeys, start + per);
                    futures.add(ex.submit(() -> {
                        List<String> found = new ArrayList<>();
                        for (int k = start; k < end; ++k) {
                            String key = String.format("%10s", Integer.toBinaryString(k)).replace(' ', '0');
                            boolean ok = true;
                            for (int idx = 0; idx < plainBlocks.size(); ++idx) {
                                String enc = SDES.encryptBlock(plainBlocks.get(idx), key);
                                if (!enc.equals(cipherBlocks.get(idx))) { ok = false; break; }
                            }
                            if (ok) found.add(key);
                        }
                        return found;
                    }));
                }
                // 收集结果
                List<String> candidates = new ArrayList<>();
                for (Future<List<String>> f : futures) {
                    candidates.addAll(f.get());
                }
                ex.shutdownNow();
                long t1 = System.currentTimeMillis();
                if (candidates.isEmpty()) {
                    log("未找到匹配密钥（耗时 " + (t1 - t0) + " ms）。");
                } else {
                    log("找到 " + candidates.size() + " 个候选密钥（耗时 " + (t1 - t0) + " ms）：");
                    for (String k : candidates) log("  " + k);
                }
            } catch (Exception ex) {
                log("错误: " + ex.getMessage());
            }
        });

        // 封闭测试（扫描所有密钥，找出哪些密钥对某个明文产生相同密文）
        btnClosedTest.addActionListener(e -> {
            try {
                String plainInput = tfPlain.getText().trim();
                boolean ascii = cbAsciiMode.isSelected();
                String targetPlain8 = null;

                if (ascii) {
                    if (plainInput.length() != 1) {
                        log("提示：ASCII 模式下，封闭测试只支持单字符输入（用于测试该字符在 1024 个密钥下的密文分布）。");
                        return;
                    }
                    targetPlain8 = SDES.byteTo8Bits((int)plainInput.charAt(0) & 0xFF);
                } else {
                    if (!validate8BitSilent(plainInput)) { log("错误：请输入 8-bit 明文做封闭测试（或选择 ASCII 模式并输入单字符）。"); return; }
                    targetPlain8 = plainInput;
                }

                log("开始封闭测试：扫描所有 1024 个密钥，对明文 " + targetPlain8 + " 计算密文分布...");
                // Map from cipher -> list of keys producing it
                Map<String, List<String>> map = new HashMap<>();
                for (int k = 0; k < 1024; ++k) {
                    String key = String.format("%10s", Integer.toBinaryString(k)).replace(' ', '0');
                    String c = SDES.encryptBlock(targetPlain8, key);
                    map.computeIfAbsent(c, x -> new ArrayList<>()).add(key);
                }
                // 统计并输出
                int collisions = 0;
                for (Map.Entry<String, List<String>> en : map.entrySet()) {
                    if (en.getValue().size() > 1) collisions++;
                }
                log("扫描结束。不同密文数 = " + map.size() + "，出现多个不同密钥映射到同一密文的密文数量 = " + collisions);
                if (collisions > 0) {
                    log("下面列举部分碰撞（密文 -> 对应密钥列表）：");
                    int shown = 0;
                    for (Map.Entry<String, List<String>> en : map.entrySet()) {
                        if (en.getValue().size() > 1) {
                            log("  " + en.getKey() + "  ->  " + en.getValue());
                            shown++;
                            if (shown >= 10) break;
                        }
                    }
                } else {
                    log("未发现碰撞：该明文在所有 1024 个密钥下生成的密文均唯一。");
                }
            } catch (Exception ex) {
                log("错误: " + ex.getMessage());
            }
        });
    }

    // ---------- 辅助方法 ----------

    private void log(String s) {
        taLog.append(s + "\n");
        taLog.setCaretPosition(taLog.getDocument().getLength());
    }

    // 简单校验 8-bit 格式
    private boolean validate8Bit(String s) {
        if (s == null || s.length() != 8 || !s.matches("[01]{8}")) {
            log("错误：请输入 8 位二进制（例如 01010101）。");
            return false;
        }
        return true;
    }
    // 校验但不打印错误（用于内部判断）
    private boolean validate8BitSilent(String s) {
        return s != null && s.length() == 8 && s.matches("[01]{8}");
    }
    private boolean validate10Bit(String s) {
        if (s == null || s.length() != 10 || !s.matches("[01]{10}")) {
            log("错误：请输入 10 位二进制密钥（例如 1010000010）。");
            return false;
        }
        return true;
    }
    private int parseThreadCount(String s) {
        try {
            return Integer.parseInt(s);
        } catch (Exception e) {
            return Runtime.getRuntime().availableProcessors();
        }
    }
}
