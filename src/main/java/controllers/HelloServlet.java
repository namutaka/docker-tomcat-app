package controllers;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.Properties;

@WebServlet(
        name = "MyServlet",
        urlPatterns = {"/hello"}
)
public class HelloServlet extends HttpServlet {
    private static Logger log = LoggerFactory.getLogger(HelloServlet.class);

    private static Properties prop;
    static {
        prop = new Properties();
        try {
            InputStream is = HelloServlet.class.getResourceAsStream("/config.properties");
            prop.load(is);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        log.info("Access: {}, params={}", req.getRequestURL(), req.getParameterMap());

        StringBuilder body = new StringBuilder();
        body.append("hello world ");
        body.append(",");
        body.append(java.net.InetAddress.getLocalHost().getHostName());
        body.append(",");
        body.append("prop key1=");
        body.append(prop.getProperty("KEY1"));
        body.append(",");
        body.append("params=");
        body.append(req.getParameterMap());

        ServletOutputStream out = resp.getOutputStream();
        out.write(body.toString().getBytes());
        out.flush();
        out.close();
    }
}
