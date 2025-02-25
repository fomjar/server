package com.ski.vcg.wa.ae.psn;

import net.sf.json.JSONObject;

import org.openqa.selenium.By;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.WebDriver;

import com.ski.vcg.common.CommonDefinition;
import com.ski.vcg.wa.AE;

public class VerifyAccount implements AE {

    private int     code = CommonDefinition.CODE.CODE_SYS_UNKNOWN_ERROR;
    private String  desc = null;

    @Override
    public void execute(WebDriver driver, JSONObject args) {
        AE login = new Login();
        login.execute(driver, args);
        if (CommonDefinition.CODE.CODE_SYS_SUCCESS != login.code()) {
            code = login.code();
            desc = login.desc();
            return;
        }
        try {
            driver.findElement(By.className("lastDeviceName")); // 存在设备绑定
            code = CommonDefinition.CODE.CODE_SYS_SUCCESS;
            desc = "psn account is binded";
        } catch (NoSuchElementException e) { // 不存在设备绑定
            code = CommonDefinition.CODE.CODE_SYS_SUCCESS;
            desc = "psn account is unbinded";
        }
    }

    @Override
    public int code() {
        return code;
    }

    @Override
    public String desc() {
        return desc;
    }

}
