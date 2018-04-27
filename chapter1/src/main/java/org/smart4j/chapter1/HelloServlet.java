package org.smart4j.chapter1;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Created by Brave on 18/4/27.
 */
@WebServlet("/hello")//使用注解配置请求路径,对外发布Servlet服务
public class HelloServlet extends HttpServlet {//继承HttpSevlet,成为一个HttpSevlet类

    /**
     * 重写doGet方法,接收Get请求
     */
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {

        // 获取当前系统时间
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        String currentTime = dateFormat.format(new Date());
        // 添加到HttpServletRequest请求对象中
        req.setAttribute("currentTime", currentTime);
        // 转发至/WEB-INF/jsp/hello.jsp页面
        req.getRequestDispatcher("/WEB-INF/jsp/hello.jsp").forward(req, resp);
    }
}
