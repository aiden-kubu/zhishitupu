package com.knowledgegraph.assistant.host.demo;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowledgegraph.common.ApiResponse;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.util.*;

/** Single-owner website session. Proxy headers never supply owner, scope or reader evidence. */
public final class AssistantWebsiteDemoAccess implements Filter {
    public static final String LOGIN="/api/assistant/demo/login",LOGOUT="/api/assistant/demo/logout";
    public static final String SESSION="__Host-AssistantDemo",CHALLENGE="__Host-AssistantDemoLogin";
    private static final String ORIGIN="https://tupu.gmule.cn";
    private final AssistantWebsiteDemoConfig config;
    private final Clock clock;
    private final SecureRandom random=new SecureRandom();
    private final Map<String,Instant> sessions=new LinkedHashMap<>(),challenges=new LinkedHashMap<>();
    private final ObjectMapper json=new ObjectMapper();
    private Instant attemptWindow=Instant.MIN;
    private int attempts;
    public AssistantWebsiteDemoAccess(AssistantWebsiteDemoConfig config){this(config,Clock.systemUTC());}
    public AssistantWebsiteDemoAccess(AssistantWebsiteDemoConfig config,Clock clock){this.config=config;this.clock=clock;}
    private String token(){byte[] b=new byte[32];random.nextBytes(b);return Base64.getUrlEncoder().withoutPadding().encodeToString(b);}
    private static String cookie(HttpServletRequest r,String name) {
        String value=null;if(r.getCookies()!=null)for(Cookie c:r.getCookies())if(c.getName().equals(name)){if(value!=null)return null;value=c.getValue();}return value;
    }
    private void cookie(HttpServletResponse r,String name,String token,int seconds) {
        r.addHeader("Set-Cookie",name+"="+token+"; Path=/; Max-Age="+seconds+"; Secure; HttpOnly; SameSite=Strict");
    }
    private boolean transport(HttpServletRequest r) {
        if(!"tupu.gmule.cn".equals(r.getServerName()) || r.getHeader("Forwarded")!=null)return false;
        String proto=r.getHeader("X-Forwarded-Proto");
        // Production TLS terminates at the local reverse proxy; only literal loopback and one https value qualify.
        if(proto!=null)return Collections.list(r.getHeaders("X-Forwarded-Proto")).size()==1 && "https".equals(proto)
                && Set.of("127.0.0.1","::1","0:0:0:0:0:0:0:1").contains(r.getRemoteAddr());
        return r.isSecure() && "https".equals(r.getScheme());
    }
    private boolean sameOrigin(HttpServletRequest r,boolean required) {
        String origin=r.getHeader("Origin"),fetch=r.getHeader("Sec-Fetch-Site");
        if(fetch!=null && !Set.of("same-origin","none").contains(fetch))return false;
        if(Collections.list(r.getHeaders("Origin")).size()>1)return false;
        return origin==null?!required:ORIGIN.equals(origin);
    }
    private synchronized boolean authorized(HttpServletRequest r) {
        Instant now=clock.instant();sessions.values().removeIf(t->!now.isBefore(t));
        String key=cookie(r,SESSION);return key!=null && sessions.containsKey(key);
    }
    private void error(HttpServletResponse r,int status,String code)throws IOException {
        r.setStatus(status);r.setContentType("application/json");r.setCharacterEncoding("UTF-8");json.writeValue(r.getOutputStream(),ApiResponse.error(code,code));
    }
    @Override public void doFilter(ServletRequest input,ServletResponse output,FilterChain chain)throws IOException,ServletException {
        HttpServletRequest r=(HttpServletRequest)input;HttpServletResponse s=(HttpServletResponse)output;
        s.setHeader("Cache-Control","no-store");s.setHeader("X-Content-Type-Options","nosniff");s.setHeader("Referrer-Policy","no-referrer");
        if(!transport(r)){error(s,401,"ACCESS_CONTEXT_UNAVAILABLE");return;}
        if(config==null){error(s,503,"CAPABILITY_UNAVAILABLE");return;}
        String path=r.getRequestURI().substring(r.getContextPath().length()),method=r.getMethod();
        boolean mutation=!Set.of("GET","HEAD").contains(method);
        if(!sameOrigin(r,mutation)){error(s,403,"DEMO_ORIGIN_REJECTED");return;}
        boolean forged=false;
        try {if(r.getQueryString()!=null)for(String part:r.getQueryString().split("&"))
            if(Set.of("owner","grant","verified","enabled","verificationSha256").contains(URLDecoder.decode(part.split("=",2)[0],StandardCharsets.UTF_8)))forged=true;
        }catch(IllegalArgumentException invalid){forged=true;}
        if(forged
                || r.getHeader("X-Assistant-Owner")!=null || r.getHeader("X-Assistant-Verified")!=null){error(s,400,"INVALID_ARGUMENT");return;}
        if("GET".equals(method) && (r.getContentLengthLong()>0 || r.getInputStream().read()!=-1)){error(s,400,"INVALID_ARGUMENT");return;}
        if(LOGIN.equals(path)) {
            if(r.getQueryString()!=null){error(s,400,"INVALID_ARGUMENT");return;}
            if("GET".equals(method)){loginPage(s);return;}
            if("POST".equals(method)){login(r,s);return;}
            error(s,405,"METHOD_NOT_ALLOWED");return;
        }
        if(!authorized(r)){error(s,401,"DEMO_AUTH_REQUIRED");return;}
        if(LOGOUT.equals(path)) {
            if(!"POST".equals(method)){error(s,405,"METHOD_NOT_ALLOWED");return;}
            synchronized(this){sessions.remove(cookie(r,SESSION));}cookie(s,SESSION,"",0);s.setStatus(204);return;
        }
        chain.doFilter(r,s);
    }
    private synchronized void loginPage(HttpServletResponse s)throws IOException {
        Instant now=clock.instant();challenges.values().removeIf(t->!now.isBefore(t));
        if(challenges.size()>=64){error(s,429,"DEMO_LOGIN_LIMIT");return;}
        String csrf=token();challenges.put(csrf,now.plusSeconds(300));cookie(s,CHALLENGE,csrf,300);
        s.setHeader("Content-Security-Policy","default-src 'none'; form-action 'self'; frame-ancestors 'none'; base-uri 'none'");
        s.setContentType("text/html");s.setCharacterEncoding("UTF-8");
        s.getWriter().write("<!doctype html><html lang=\"zh-CN\"><meta charset=\"UTF-8\"><title>助手演示登录</title>"
                +"<h1>助手演示登录</h1><form method=\"post\" action=\""+LOGIN+"\"><input type=\"hidden\" name=\"csrf\" value=\""+csrf+"\">"
                +"<label>访问口令 <input type=\"password\" name=\"password\" required maxlength=\"256\" autocomplete=\"off\"></label>"
                +"<button type=\"submit\">进入助手</button></form></html>");
    }
    private synchronized void login(HttpServletRequest r,HttpServletResponse s)throws IOException {
        Instant now=clock.instant();if(!now.isBefore(attemptWindow.plusSeconds(60))){attemptWindow=now;attempts=0;}
        if(++attempts>5){error(s,429,"DEMO_LOGIN_LIMIT");return;}
        if(r.getContentType()==null || !r.getContentType().split(";",2)[0].equals("application/x-www-form-urlencoded")){error(s,400,"INVALID_ARGUMENT");return;}
        // 256 UTF-16 characters can exceed 2 KiB after UTF-8 form percent encoding.
        byte[] body=r.getInputStream().readNBytes(4097);if(body.length>4096){error(s,413,"INVALID_ARGUMENT");return;}
        Map<String,String> form=new HashMap<>();
        try {for(String part:new String(body,StandardCharsets.UTF_8).split("&")) {
            String[] kv=part.split("=",2);if(kv.length!=2 || form.putIfAbsent(URLDecoder.decode(kv[0],StandardCharsets.UTF_8),URLDecoder.decode(kv[1],StandardCharsets.UTF_8))!=null)throw new IllegalArgumentException();
        }}catch(IllegalArgumentException invalid){error(s,400,"INVALID_ARGUMENT");return;}
        if(!form.keySet().equals(Set.of("csrf","password"))){error(s,400,"INVALID_ARGUMENT");return;}
        String csrf=form.get("csrf"),challenge=cookie(r,CHALLENGE);Instant expires=challenges.remove(csrf);
        cookie(s,CHALLENGE,"",0);
        if(challenge==null || !challenge.equals(csrf) || expires==null || !now.isBefore(expires)){error(s,403,"DEMO_CSRF_REJECTED");return;}
        if(!MessageDigest.isEqual(digest(form.get("password")),digest(config.accessPassword()))){error(s,401,"DEMO_AUTH_REQUIRED");return;}
        sessions.values().removeIf(t->!now.isBefore(t));if(sessions.size()>=32){error(s,429,"DEMO_LOGIN_LIMIT");return;}
        // Rotate any previous cookie; only an opaque random token is sent to the browser.
        sessions.remove(cookie(r,SESSION));String key=token();sessions.put(key,now.plusSeconds(config.sessionSeconds()));
        cookie(s,SESSION,key,config.sessionSeconds());s.setStatus(303);s.setHeader("Location","/assistant");
    }
    private static byte[] digest(String s) {
        try{return MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));}catch(NoSuchAlgorithmException e){throw new IllegalStateException();}
    }
}
