package com.adc.common;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 极简 CSV 生成器。
 * <p>
 * 为什么不是 PDF：报表里的数字是给人再加工的（总部要拿去核账、算激励），
 * CSV 能被 Excel 直接打开、能被再导入别的系统；PDF 只能看。
 * 中文用 UTF-8 BOM 开头，否则 Windows 版 Excel 打开是乱码——这是最常见的坑。
 * <p>
 * 转义按 RFC 4180：字段里有逗号、引号、换行时整体加引号，内部引号翻倍。
 */
public final class Csv {

    /** UTF-8 BOM：Excel 识别编码的依据，少这三个字节中文就乱码。 */
    private static final byte[] BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};

    private Csv() {
    }

    public static byte[] bytes(List<List<Object>> rows) {
        StringBuilder sb = new StringBuilder();
        for (List<Object> row : rows) {
            for (int i = 0; i < row.size(); i++) {
                if (i > 0) {
                    sb.append(',');
                }
                sb.append(cell(row.get(i)));
            }
            sb.append("\r\n");
        }
        byte[] body = sb.toString().getBytes(StandardCharsets.UTF_8);
        byte[] out = new byte[BOM.length + body.length];
        System.arraycopy(BOM, 0, out, 0, BOM.length);
        System.arraycopy(body, 0, out, BOM.length, body.length);
        return out;
    }

    private static String cell(Object v) {
        if (v == null) {
            return "";
        }
        String s = String.valueOf(v);
        boolean needQuote = s.indexOf(',') >= 0 || s.indexOf('"') >= 0
                || s.indexOf('\n') >= 0 || s.indexOf('\r') >= 0;
        if (!needQuote) {
            return s;
        }
        return '"' + s.replace("\"", "\"\"") + '"';
    }
}
