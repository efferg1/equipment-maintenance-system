package com.cuijian.ems.web;

import com.cuijian.ems.dao.EmsDao;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Map;

@Controller
public class AuthController {

    private final EmsDao dao;

    public AuthController(EmsDao dao) {
        this.dao = dao;
    }

    @GetMapping("/login")
    public String loginPage() {
        return "login";
    }

    @PostMapping("/login")
    public String doLogin(@RequestParam String username,
                          @RequestParam String password,
                          HttpSession session,
                          Model model) {
        Map<String, Object> user = dao.findUserByUsername(username.trim());
        if (user == null || !password.equals(String.valueOf(user.get("password")))) {
            model.addAttribute("err", "账号或密码错误");
            model.addAttribute("username", username);
            return "login";
        }
        session.setAttribute("loginUser", user);
        return "redirect:/";
    }

    @GetMapping("/logout")
    public String logout(HttpSession session) {
        session.invalidate();
        return "redirect:/login";
    }
}
