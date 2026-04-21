package com.dawn.printers.xitiecheng;

import com.printer.sdk.PrintMsg;

/**
 * @author hkxiong
 * @email love2008_vip@1163.com
 * @create_date 2025-11-19 09:51:20
 * @update_date 2025-11-19 09:51:20
 * @description
 */
public interface OnStartPrint {

    void onStarSuccess();

    void onStarFail(PrintMsg info);
}
