package com.seu.kse.controller;

import com.seu.kse.util.Constant;
import com.seu.kse.bean.User;
import com.seu.kse.service.IUserService;
import com.seu.kse.service.impl.UserFieldService;
import com.seu.kse.bean.userFieldsKey;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import javax.transaction.Transactional;
import java.util.Map;

/**
 * Created by yaosheng on 2017/5/23.
 */

@Controller
@RequestMapping("/login")
public class loginController {
    @Resource
    private IUserService userService;
    @Resource
    private UserFieldService utService;

    @RequestMapping("/login")
    public String login(HttpServletRequest request,HttpSession session, Model model){
        String userEmail=request.getParameter("email");
        String psw=request.getParameter("password");
//        String utypestr=request.getParameter("utype");
        if(userEmail!=null && psw !=null){
            User user=userService.verification(userEmail,psw);
            if(user==null){
                model.addAttribute("result","用户输入信息错误");
                return "/login/login";
            }
            session.setAttribute(Constant.CURRENT_USER,user);
        }
        return "redirect:/search";
    }

    @RequestMapping("/logout")
    public String logout(HttpServletRequest request,HttpSession session, Model model){
        session.removeAttribute(Constant.CURRENT_USER);
        return "redirect:/login/login.jsp";
    }

    @Transactional
    @RequestMapping("/register")
    public String register(HttpServletRequest request, HttpSession session, Model model){
        String uname=request.getParameter("name");
        String upsw=request.getParameter("password");
        String email=request.getParameter("email");
        int utype=Integer.parseInt(request.getParameter("utype"));
        //判断email是否已经注册
        if(userService.isRegisterEmail(email)){
            model.addAttribute("result","邮箱已经注册，请更换邮箱重试");
            return "/login/register";
        }else{
            User user=new User(uname,upsw,email,utype);
            int lines = userService.insertUser(user);
            if(lines<=0){
                model.addAttribute("result","数据库注册失败，请联系管理员");
                return "/login/register";
            }
            model.addAttribute("id",user.getId());
            String[] area_ids = {"00010","00020","00030","00040","00050","00060","00070","00080","00090","00100","00110","00120"};
            for(String area_id:area_ids){
                String userfield = request.getParameter(area_id);
                if(userfield!=null && userfield.equals("true")){
                    userFieldsKey ufkey = new userFieldsKey();
                    ufkey.setUid(user.getId());
                    ufkey.setFid(area_id);
                    utService.insertRecord(ufkey);
                }
            }
            session.setAttribute(Constant.CURRENT_USER,user);
        }
        return "redirect:/search";
    }

    /**
     * 记录用户的相关领域
     * @param request
     * @param model
     * @return
     */
    @RequestMapping("/insertUserField")
    public String insertUserField(HttpServletRequest request, Model model){
        Map<String, String[]> tags = request.getParameterMap();
        String[] tagList=tags.get("tags");
        String uid=request.getParameter("uid");

        userFieldsKey user_fields;
        for(String tag : tagList){
            user_fields = new userFieldsKey();
            user_fields.setFid(tag);
            user_fields.setUid(uid);
            utService.insertRecord(user_fields);

        }

        return  "/login/insertUserField";
    }
}
