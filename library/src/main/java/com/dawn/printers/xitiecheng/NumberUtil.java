package com.dawn.printers.xitiecheng;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author hkxiong
 * @email love2008_vip@1163.com
 * @create_date 2025/11/19 9:21
 * @update_date 2025/11/19 9:21
 * @description   数字处理工具类
 *
 */
public class NumberUtil {

    /**
     *  提取文本中的数字
     * @param str   输入的文本内容
     * @return
     */
    public static int extractNumbers(String str){
        Pattern pattern = Pattern.compile("\\d+"); // \\d+ 匹配一个或多个数字
        Matcher matcher = pattern.matcher(str);
        System.out.println(matcher); // 输出: 1234
        if (matcher.find()) {
            String numberStr = matcher.group();
            // 去除前导的零
            String result = numberStr.replaceFirst("^0+", "");
            // 如果去除后为空字符串，说明原始数字全是0，则返回"0"
            return result.isEmpty() ? 0 : Integer.parseInt(result);
        }
        return 0;
    }

}
