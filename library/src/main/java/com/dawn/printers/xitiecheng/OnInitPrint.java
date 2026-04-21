package com.dawn.printers.xitiecheng;

import com.printer.sdk.PrintMsg;

/**
 * @author hkxiong
 * @email love2008_vip@1163.com
 * @create_date 2025/11/18 17:18
 * @update_date 2025/11/18 17:18
 * @description
 */
public interface OnInitPrint {

    void onInitSuccess();

    void onInitFail(PrintMsg info);
}
