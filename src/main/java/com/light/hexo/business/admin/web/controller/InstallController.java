package com.light.hexo.business.admin.web.controller;

import com.light.hexo.business.admin.component.InstallService;
import com.light.hexo.common.base.BaseRequest;
import com.light.hexo.common.model.InstallRequest;
import com.light.hexo.common.model.Result;
import com.light.hexo.common.model.UserRequest;
import com.light.hexo.common.util.BrowserUtil;
import com.light.hexo.common.util.IpUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.servlet.http.HttpServletRequest;
import java.util.Map;

/**
 * @Author MoonlightL
 * @ClassName: InstallController
 * @ProjectName hexo-boot
 * @Description: 安装控制器
 * @DateTime 2020/9/11 17:29
 */
@RequestMapping("/admin")
@Controller
public class InstallController {

    @Autowired
    private InstallService installService;

    /**
     * 安装页
     * @param resultMap
     * @return
     */
    @RequestMapping("/install.html")
    public String install(Map<String, Object> resultMap) {
        return "/admin/install";
    }

    /**
     * 安装系统
     * @param request
     * @return
     */
    @RequestMapping("/install.json")
    @ResponseBody
    public Result install(@Validated(BaseRequest.Save.class) InstallRequest request, HttpServletRequest httpServletRequest) throws Exception{
        this.installService.installApplication(
                request,
                BrowserUtil.getBrowserName(httpServletRequest),
                IpUtil.getIpAddr(httpServletRequest));
        return Result.success("/admin/login.html");
    }
}
